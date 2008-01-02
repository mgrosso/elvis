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
package com.netblue.bruce;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.cfg.Configuration;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.junit.Assert;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * Helper class for tests that deal with the database (which is almost all of them).
 *
 * @author lanceball
 * @version $Id$
 */
public class TestDatabaseHelper
{
    private static final Logger LOGGER = Logger.getLogger(TestDatabaseHelper.class);
    static { LOGGER.setLevel(Level.DEBUG); }

    public static final String DDL_DELIMITER             = ";";
    public static final String CONFIG_DB                 = "bruce";
    public static final String POSTGRESQL_ADMIN_URL_KEY  = "postgresql.adminURL";
    public static final String POSTGRESQL_TEST_URL_KEY   = "postgresql.URL";
    public static final String POSTGRESQL_PORT_KEY      = "postgresql.port";
    public static final String POSTGRESQL_PORT_DEFAULT  = "5432";
    public static final String JDBC_POSTGRESQL_PREFIX = "jdbc:postgresql://localhost:";
    
    public static synchronized Properties getPostgresProperties()
    {
        if (postgresProperties == null)
        {
            postgresProperties = new Properties(System.getProperties());
        }
        return postgresProperties;
    }

    public static String getBasePostgresUrl()
    {
        return JDBC_POSTGRESQL_PREFIX + getPostgresProperties().getProperty(POSTGRESQL_PORT_KEY, POSTGRESQL_PORT_DEFAULT) + "/";
    }

    public static String buildUrl(String dbName)
    {
        return new StringBuilder().append(getBasePostgresUrl()).append(dbName).append("?user=bruce&password=bruce").toString();
    }

    public static void executeAndLogSupressingExceptions(Statement statement, String query)
    {
        try
        {
            executeAndLog(statement, query);
        }
        catch (SQLException e)
        {
            LOGGER.warn(e);
        }
    }

    public static void applyDDLFromFile(final Connection connection, final File file)
    {
        try
        {
            final boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(true);
            // Create test schema in the test database
            Statement s = connection.createStatement();
            String schemaStatements[] = readDDLFile(file);
            for (String sqlcmd : schemaStatements)
            {
                try
                {
                    executeAndLog(s, sqlcmd);
                }
                catch (SQLException e)
                {
                    LOGGER.debug("Exception executing SQL", e);
                }
            }
            s.close();
            connection.setAutoCommit(autoCommit);

        }
        catch (SQLException e)
        {
            Assert.fail(e.getLocalizedMessage());
        }
    }


    public static ResultSet executeQueryAndLog(Statement s, String cmd) throws SQLException
    {
        LOGGER.debug(cmd);
        return s.executeQuery(cmd);
    }

    public static boolean executeAndLog(Statement s, String cmd) throws SQLException
    {
        LOGGER.debug(cmd);
        return s.execute(cmd);
    }

    /**
     * Reads a text file, splitting it into SQL statements (delimited by ';').
     *
     * @param file the file name/location
     *
     * @return an array of sql statements from <code>file</code>
     */
    public static String[] readDDLFile(File ddlFile)
    {
        ArrayList<String> statements = new ArrayList<String>();
        try
        {
            FileInputStream ddlFileStream = new FileInputStream(ddlFile);
            byte[] ddl = new byte[(int) ddlFile.length()];
            ddlFileStream.read(ddl);
            String ddlString = new String(ddl);
            StringTokenizer tokenizer = new StringTokenizer(ddlString, DDL_DELIMITER);
            while (tokenizer.hasMoreTokens())
            {
                statements.add(tokenizer.nextToken());
            }
        }
        catch (Exception e)
        {
            Assert.fail(e.getLocalizedMessage());
        }
        return statements.toArray(new String[statements.size()]);
    }

    /**
     * Returns the directory location for test data sets.  A convenience method for subclasses.  Assumes "test/data",
     * but a system property for "dir.test.data" will override this.
     *
     * @return The directory path
     */
    public static String getTestDataDir()
    {
        return System.getProperty("dir.test.data", "test/data");
    }

    /**
     * Gets the directory location for the schema DDL files.  A convenience method. Assumes "schema", but a system
     * property for "dir.schema" will override this.
     *
     * @return the directory path
     */
    public static String getSchemaDir()
    {
        return System.getProperty("dir.schema", "schema");
    }

    /**
     * Gets a <code>File</code> for <code>fileName</code> prepended with the path returned from {@link #getSchemaDir()}.
     * Does not validate the existence of the <code>File</code> returned.
     *
     * @param fileName
     *
     * @return
     */
    public static File getSchemaFile(String fileName)
    {
        return new File(getSchemaDir() + "/" + fileName);
    }

    /**
     * Returns a <code>File</code> for <code>fileName</code> prepended with the path returned from {@link
     * #getTestDataDir()}.  This is a convenience method, but does not validate the existence of the <code>File</code>
     * returned.
     *
     * @param fileName
     *
     * @return
     */
    public static File getDataFile(String fileName)
    {
        return new File(getTestDataDir() + "/" + fileName);
    }

    /**
     * Creates a DataSource object
     *
     * @param postgresProperties
     * @param s
     *
     * @return
     */
    public static BasicDataSource createDataSource(final Properties postgresProperties, final String dbURIkey)
    {
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(postgresProperties.getProperty(dbURIkey));
        return dataSource;
    }

    /**
     * Creates a postgres database DataSource for <code>url</code>
     * @param url
     * @return
     */
    public static BasicDataSource createDataSource(final String url)
    {
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(url);
        return dataSource;
    }

    /**
     * Creates the schema for the cluster configuration tables
     */
    public static void createConfigSchema()
    {
        Configuration configuration = new AnnotationConfiguration().configure();
        SchemaExport schemaExport = new SchemaExport(configuration);
        schemaExport.create(false, true);
    }

    /**
     * Creates the bruce database if it doesn't already exist.  Otherwise it drops and recreates the bruce schema
     *
     * @param dataSource
     *
     * @throws java.sql.SQLException
     */
    public static void createTestDatabase()
            throws SQLException, IOException, InterruptedException
    {

        final Connection connection = getAdminDataSource().getConnection();
        final Statement statement = connection.createStatement();
        final String testDBName = getTestDatabaseName();
        connection.setAutoCommit(true);

        // Force a shutdown of any backends connected to the test database
/*
        ResultSet rs = executeQueryAndLog(statement,
                                          "select procpid from pg_stat_activity where datname = '" + testDBName + "'");
        while (rs.next())
        {
            Process p = Runtime.getRuntime().exec("kill " + rs.getString("procpid"));
            p.waitFor();
            // Give the kill a chance to work
            Thread.sleep(3000L);
            LOGGER.debug("Terminated:" + rs.getString("procpid"));
        }
*/

        // First see if the database is already there.  If it is, just drop and recreate the schema
        try
        {
            ResultSet rs = executeQueryAndLog(statement, "select * from pg_database where datname = '" + testDBName + "'");
            if (!rs.next())
            {
                LOGGER.debug("Creating replication database");
                executeAndLog(statement, "create database " + testDBName);
            }
            rs.close();
        }
        catch (SQLException e)
        {
            LOGGER.info(e);
        }
        statement.close();
        connection.close();
        LOGGER.debug("DONE Creating test database");
    }

    public static String getTestDatabaseName()
    {
        return (String) System.getProperties().get("postgresql.db_name");
    }

    public static void resetBruceSchema(final Statement statement)
    {
        try
        {
            executeAndLog(statement, "drop schema bruce cascade");
        }
        catch (SQLException e)
        {
            LOGGER.error("Cannot drop bruce schema", e);
        }
        try
        {
            executeAndLog(statement, "create schema bruce");
        }
        catch (SQLException e)
        {
            LOGGER.error("Cannot create bruce schema");
        }
    }

    /**
     * Gets a DataSource object for the database under test
     *
     * @return
     */
    public static synchronized DataSource getTestDataSource()
    {
        if (testDataSource == null)
        {
            LOGGER.debug("Creating test DataSource");
            testDataSource = createDataSource(getPostgresProperties(), POSTGRESQL_TEST_URL_KEY);
        }
        return testDataSource;
    }

    public static synchronized Connection getTestDatabaseConnection()
    {
        try
        {
            if (testDatabaseConnection == null || testDatabaseConnection.isClosed())
            {
                testDatabaseConnection = getTestDataSource().getConnection();
            }
        }
        catch (SQLException e)
        {
            LOGGER.error("Cannot get JDBC connection from test datasource");
        }
        return testDatabaseConnection;
    }

    public static synchronized DataSource getAdminDataSource()
    {
        if (adminDataSource == null)
        {
            LOGGER.debug("Creating admin DataSource");
            adminDataSource = createDataSource(getPostgresProperties(), POSTGRESQL_ADMIN_URL_KEY);
        }
        return adminDataSource;
    }

    public static String getTestDataSourceUri()
    {
        return getPostgresProperties().getProperty(POSTGRESQL_TEST_URL_KEY);
    }

    public static synchronized void closeTestDataSource()
    {
        if (testDataSource != null)
        {
            try
            {
                LOGGER.debug("Closing test DataSource");
                testDataSource.close();
                testDataSource = null;
            }
            catch (SQLException e)
            {
                LOGGER.error("Cannot close DataSource", e);
            }
        }
    }

    public static synchronized void closeAdminDataSource()
    {
        if (adminDataSource != null)
        {
            try
            {
                LOGGER.debug("Closing admin DataSource");
                adminDataSource.close();
                adminDataSource = null;
            }
            catch (SQLException e)
            {
                LOGGER.error("Cannot close DataSource", e);
            }
        }
    }


    private static BasicDataSource testDataSource;
    private static Properties postgresProperties;
    private static BasicDataSource adminDataSource;
    private static Connection testDatabaseConnection;
}
