/*
 * Bruce - A PostgreSQL Database Replication System
 *
 * Portions Copyright (c) 2007, Connexus Corporation
 *
 * Permission to use, copy, modify, and distribute this software and its
 * documentation for any purpose, without fee, and without a written
 * agreement is hereby granted, provided that the above copyright notice and
 * this paragraph and the following two paragraphs appear in all copies.
 *
 * IN NO EVENT SHALL CONNEXUS CORPORATION BE LIABLE TO ANY PARTY FOR DIRECT,
 * INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST
 * PROFITS, ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION,
 * EVEN IF CONNEXUS CORPORATION HAS BEEN ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * CONNEXUS CORPORATION SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE. THE SOFTWARE PROVIDED HEREUNDER IS ON AN "AS IS"
 * BASIS, AND CONNEXUS CORPORATION HAS NO OBLIGATIONS TO PROVIDE MAINTENANCE,
 * SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
*/
package com.netblue.bruce.admin;

import com.netblue.bruce.cluster.Cluster;
import com.netblue.bruce.cluster.ClusterFactory;
import com.netblue.bruce.cluster.Node;
import com.netblue.bruce.cluster.RegExReplicationStrategy;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.log4j.*;
import org.dbunit.DatabaseUnitException;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseDataSourceConnection;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.xml.XmlDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Main class for slave configuration admin tool.
 *
 * @author lanceball
 * @version $Id: Main.java 72519 2007-06-27 14:24:08Z lball $
 */
public class Main
{
    public Main(final Options options) throws IllegalAccessException, InstantiationException
    {
        this.options = options;

        final String logConfig = System.getProperty("log4j.configuration");
        if (logConfig != null)
        {
            PropertyConfigurator.configure(logConfig);
        }

        LOGGER.setLevel(Level.INFO);
        LOGGER.info("Using database URL: \t" + options.getUrl());
        LOGGER.info("Using database user:\t" + options.getUsername());
        this.dataSource = DatabaseBuilder.makeDataSource(options.getUrl(), options.getUsername(), options.getPassword());
        // Cluster access uses hibernate.  We need to set some system properties
        // so that the hibernate configuration goes to the right DB
        System.setProperty("hibernate.connection.url", options.getUrl());
        System.setProperty("hibernate.connection.username", options.getUsername() == null ? "null" : options.getUsername());
        System.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
    }

    public void run() throws SQLException, IOException, DatabaseUnitException, IllegalAccessException, InstantiationException
    {
        // Create the database if necessary
        if (options.getLoadSchema())
        {
            LOGGER.info("Loading database schema");
            new ConfigurationDatabaseBuilder().buildDatabase(dataSource);
        }

        // Load any user data provided
        if (options.getDataFile() != null)
        {
            Set<Node> loadedNodes = loadDataFile();
            // install schema and triggers on all nodes/all clusters
            if (loadedNodes.size() > 0)
            {
                new NodeBuilder(loadedNodes, options.getInitSnapshots()).buildNodes();
            }
        }

        if (options.getList())
        {
            listClusters();
        }
        LOGGER.info("Complete");
    }

    public static void main(String[] args)
    {
        Appender startupAppender = new ConsoleAppender(new SimpleLayout());
        BasicConfigurator.configure(startupAppender);

        CmdLineParser parser = null;
        try
        {
            final Options options = new Options();
            parser = new CmdLineParser(options);
            parser.parseArgument(args);

            if (options.getUsage())
            {
                parser.printUsage(System.out);
                LOGGER.info("Print usage");
                return;
            }
            if (options.getUrl() == null)
            {
                parser.printUsage(System.err);
                LOGGER.info("Print usage");
                return;
            }
            final Main main = new Main(options);
            main.run();
        }
        catch (CmdLineException e)
        {
            LOGGER.fatal(e.getLocalizedMessage());
            if (parser != null)
            {
                parser.printUsage(System.out);
            }
        }
        catch (IOException e)
        {
            LOGGER.fatal("Cannot create configuration database.", e);
        }
        catch (SQLException e)
        {
            LOGGER.fatal("Problem encountered with database.  Unable to continue", e);
        }
        catch (Throwable t)
        {
            LOGGER.fatal("Unexpected exception caught.", t);
        }
    }

    private void listClusters() throws SQLException, IllegalAccessException, InstantiationException
    {
        LOGGER.info("Listing cluster metadata for " + options.getList());
        ClusterFactory factory = ClusterFactory.getClusterFactory();
        Set<Cluster> clusters = factory.getAllClusters();
        for (Cluster cluster : clusters)
        {
            System.out.println("Metadata for cluster [" + cluster.getName() + "]");
            final Node master = cluster.getMaster();

            // Print out the master node
            System.out.println("Master");
            printNode(master);

            Set<Node> slaves = cluster.getSlaves();
            System.out.println("\nSlaves");
            for (Node slave : slaves)
            {
                printNode(slave);
                System.out.println();
            }
        }
    }

    private void printNode(final Node node) throws SQLException
    {
        System.out.println(node.toString());

        // Print matching schemas
        final BasicDataSource nodeDataSource = DatabaseBuilder.makeDataSource(node.getUri(), null, null);
        final RegExReplicationStrategy replicationStrategy = new RegExReplicationStrategy(nodeDataSource);
        final ArrayList<String> tables = replicationStrategy.getTables(node, null);
        System.out.println("\tMatching tables:");
        for (String table : tables)
        {
            System.out.println("\t" + table);
        }
    }

    /**
     * Loads table/row values from a dbUnit-formatted xml file into the configuration database. 
     * @throws SQLException
     * @throws DatabaseUnitException
     * @throws FileNotFoundException
     */
    private Set<Node> loadDataFile() throws SQLException, DatabaseUnitException, FileNotFoundException, IllegalAccessException, InstantiationException
    {
        LOGGER.info("Loading data file into configuration database: " + options.getDataFile());
        DatabaseDataSourceConnection dataSourceConnection = new DatabaseDataSourceConnection(this.dataSource);

        DatabaseConfig config = dataSourceConnection.getConfig();
        config.setFeature("http://www.dbunit.org/features/qualifiedTableNames", true);
        
        DatabaseOperation operation = DatabaseOperation.INSERT;
        switch(options.getOperation())
        {
            case UPDATE: operation = DatabaseOperation.UPDATE; break;
            case CLEAN_INSERT: operation = DatabaseOperation.CLEAN_INSERT; break;
            case DELETE: operation = DatabaseOperation.DELETE; break;
        }

        // Load the data file
        IDataSet dataSet = new XmlDataSet(new FileInputStream(options.getDataFile()));
        ITable yfNodeTable = dataSet.getTable("bruce.yf_node");

        // Now generate a list of the node names that we are loading
        ArrayList<String> nodeNames = new ArrayList<String>();
        if (yfNodeTable != null)
        {
            int numNodes = yfNodeTable.getRowCount();
            for (int i=0; i<numNodes; i++)
            {
                try
                {
                    Object nodeName = yfNodeTable.getValue(i, "name");
                    nodeNames.add(String.valueOf(nodeName));
                }
                catch (DataSetException e)
                {
                    LOGGER.warn("No node name provided in data file.");
                }
            }
        }

        // insert/update/delete rows in DB based on file
        operation.execute(dataSourceConnection, dataSet);
        dataSourceConnection.close();

        // Now that we've loaded the nodes, let's get instances of what was loaded
        // If the operation was a DELETE, we don't bother with this
        Set<Node> nodes = new HashSet<Node>();
        if (operation != DatabaseOperation.DELETE)
        {
            ClusterFactory clusterFactory = ClusterFactory.getClusterFactory();

            for (String nodeName : nodeNames)
            {
                nodes.add(clusterFactory.getNode(nodeName));
            }
        }
        return nodes;
    }

    private static final Logger LOGGER = Logger.getLogger(Main.class);
    private final BasicDataSource dataSource;
    private final Options options;
}
