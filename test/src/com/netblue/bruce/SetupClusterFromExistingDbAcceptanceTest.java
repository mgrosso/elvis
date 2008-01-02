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

import com.netblue.bruce.admin.Version;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Tests http://bi02.yfdirect.net:8080/xplanner/do/view/task?oid=1137 and http://bi02.yfdirect.net:8080/xplanner/do/view/task?oid=1294
 *
 * @author lanceball
 * @version $Id: SetupClusterFromExistingDbAcceptanceTest.java 72519 2007-06-27 14:24:08Z lball $
 */
public class SetupClusterFromExistingDbAcceptanceTest
{
    @BeforeClass
    public static void initializeDatabaseResources() throws SQLException
    {
        properties = new Properties(System.getProperties());
        properties.put(CONFIG_URL_KEY, TestDatabaseHelper.buildUrl(TestDatabaseHelper.CONFIG_DB));
        properties.put(MASTER_URL_KEY, TestDatabaseHelper.buildUrl(MASTER_DB));
        properties.put(SLAVE1_URL_KEY, TestDatabaseHelper.buildUrl(SLAVE1_DB));
        properties.put(SLAVE2_URL_KEY, TestDatabaseHelper.buildUrl(SLAVE2_DB));

        masterDS = TestDatabaseHelper.createDataSource(properties, MASTER_URL_KEY);
        slave1DS = TestDatabaseHelper.createDataSource(properties, SLAVE1_URL_KEY);
        slave2DS = TestDatabaseHelper.createDataSource(properties, SLAVE2_URL_KEY);
        configDS = TestDatabaseHelper.createDataSource(properties, CONFIG_URL_KEY);

        createDatabases(properties);
    }

    @AfterClass
    public static void releaseDatabaseResources() throws SQLException
    {
        masterDS.close();
        slave1DS.close();
        slave2DS.close();
        configDS.close();
        createTestSchemas();
    }


    @Test
    public void testSetupCluster() throws SQLException
    {
        setupTestCluster();

        // First, we expect the schema to have been installed on all databases
        Version.isSameVersion(masterDS);
        Version.isSameVersion(slave1DS);
        Version.isSameVersion(slave2DS);

        // We also expect the configuration database to contain the cluster we setup
        assertClusterConfiguration();

        // The master database should have one row in the snapshotlog view
        assertMasterDatabase();

        // The slave databases should have one row in the slavesnapshotstatus table
        assertSlaveDatabase(slave1DS);
        assertSlaveDatabase(slave2DS);
    }

    @Test
    public void testSetupClusterAndDeleteNode() throws SQLException
    {
        // setup the database with expected nodes
        setupTestCluster();

        // Now delete one node from the cluster
        String[] args = new String[]{"-data", TestDatabaseHelper.getTestDataDir() + "/admin-test-delete.xml",
                                     "-operation", "DELETE",
                                     "-url", TestDatabaseHelper.buildUrl(TestDatabaseHelper.CONFIG_DB)};

        com.netblue.bruce.admin.Main.main(args);

        // The configuration database to contain the cluster we setup
        assertClusterConfigurationAfterDelete();
    }

    @Test
    public void testSetupClusterAndModifyNode() throws SQLException
    {
        // setup the database with expected nodes
        setupTestCluster();

        // Now modify one node from the cluster
        String[] args = new String[]{"-data", TestDatabaseHelper.getTestDataDir() + "/admin-test-update.xml",
                                     "-operation", "UPDATE",
                                     "-url", TestDatabaseHelper.buildUrl(TestDatabaseHelper.CONFIG_DB)};

        com.netblue.bruce.admin.Main.main(args);

        // The configuration database to contain the cluster we setup
        assertClusterConfigurationAfterUpdate();
    }

    @Test
    public void testSetupClusterAndInsertNode() throws SQLException
    {
        // setup the database with expected nodes
        setupTestCluster();

        // Now insert one node into the cluster
        String[] args = new String[]{"-data", TestDatabaseHelper.getTestDataDir() + "/admin-test-insert.xml",
                                     "-initnodeschema",
                                     "-initsnapshots", "MASTER",
                                     "-operation", "INSERT",
                                     "-url", TestDatabaseHelper.buildUrl(TestDatabaseHelper.CONFIG_DB)};

        com.netblue.bruce.admin.Main.main(args);

        // The configuration database to contain the cluster we setup
        assertClusterConfigurationAfterInsert();
    }

    public static void createTestSchemas() throws SQLException
    {
        createSchema(masterDS);
        createSchema(slave1DS);
        createSchema(slave2DS);
    }
    
    public static void setupTestCluster()
    {
        String[] args = new String[]{"-data", TestDatabaseHelper.getTestDataDir() + "/admin-test-setup.xml",
                                     "-initnodeschema",
                                     "-initsnapshots", "MASTER",
                                     "-loadschema",
                                     "-operation", "CLEAN_INSERT",
                                     "-url", TestDatabaseHelper.buildUrl(TestDatabaseHelper.CONFIG_DB)};
        com.netblue.bruce.admin.Main.main(args);
    }
    
    private void assertSlaveDatabase(final BasicDataSource slaveDS)
    {
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;

        try
        {
            connection = slaveDS.getConnection();
            statement = connection.createStatement();

            // check that the snapshot log contains one row
            resultSet = statement.executeQuery("select count(*) from slavesnapshotstatus");
            Assert.assertTrue("Unexpected results from slavesnapshotstatus", resultSet.next());
            Assert.assertEquals("Unexpected number of rows in the slavesnapshotstatus", 1, resultSet.getInt(1));
        }
        catch (SQLException e)
        {
            e.printStackTrace();
            Assert.fail("Cannot validate master database: " + e.getLocalizedMessage());
        }
        finally
        {
            try
            {
                if (resultSet != null) { resultSet.close(); }
                if (statement != null) { statement.close(); }
                if (connection != null) { connection.close(); }
            }
            catch (SQLException e)
            {
                e.printStackTrace();
            }
        }
    }

    private void assertMasterDatabase()
    {
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;

        try
        {
            connection = masterDS.getConnection();
            statement = connection.createStatement();

            // check that the snapshot log contains one row
            resultSet = statement.executeQuery("select count(*) from snapshotlog");
            Assert.assertTrue("Unexpected results from snapshotlog", resultSet.next());
            Assert.assertEquals("Unexpected number of rows in the snapshotlog", 1, resultSet.getInt(1));
        }
        catch (SQLException e)
        {
            e.printStackTrace();
            Assert.fail("Cannot validate master database: " + e.getLocalizedMessage());
        }
        finally
        {
            try
            {
                if (resultSet != null) { resultSet.close(); }
                if (statement != null) { statement.close(); }
                if (connection != null) { connection.close(); }
            }
            catch (SQLException e)
            {
                e.printStackTrace();
            }
        }
    }

    private void assertClusterConfiguration()
    {
        ResultSet resultSet = null;
        Connection connection = null;
        Statement statement = null;
        try
        {
            connection = configDS.getConnection();
            statement = connection.createStatement();

            // Check that the nodes exist
            resultSet = statement.executeQuery("select * from yf_node where name like 'Cluster 0 - Primary master'");
            Assert.assertTrue("No master node found", resultSet.next());
            Assert.assertEquals("Wrong include table regex found", "replication_test\\..+", resultSet.getString("INCLUDETABLE"));
            Assert.assertEquals("Wrong id for master found", 1, resultSet.getInt("ID"));

            resultSet = statement.executeQuery("select * from yf_node where name like 'Cluster 0 - Slave Uno'");
            Assert.assertTrue("No slave 1 node found", resultSet.next());
            Assert.assertEquals("Wrong include table regex found", "replication_test\\..+", resultSet.getString("INCLUDETABLE"));
            Assert.assertEquals("Wrong id for slave 1 found", 2, resultSet.getInt("ID"));

            resultSet = statement.executeQuery("select * from yf_node where name like 'Cluster 0 - Slave Dos'");
            Assert.assertTrue("No slave 2 node found", resultSet.next());
            Assert.assertEquals("Wrong include table regex found", "replication_test\\..+", resultSet.getString("INCLUDETABLE"));
            Assert.assertEquals("Wrong id for slave 2 found", 3, resultSet.getInt("ID"));

            // Check that the cluster exists
            resultSet = statement.executeQuery("select * from yf_cluster where name like 'ClusterOne'");
            Assert.assertTrue("No cluster found", resultSet.next());
            Assert.assertEquals("Wrong cluster ID found", 1000, resultSet.getInt("ID"));
            Assert.assertEquals("Wrong master node for cluster", 1, resultSet.getInt("MASTER_NODE_ID"));
        }
        catch (SQLException e)
        {
            e.printStackTrace();
            Assert.fail("Cannot validate cluster configuration: " + e.getLocalizedMessage());
        }
        finally
        {
            try
            {
                if (resultSet != null) { resultSet.close(); } 
                if (statement != null) { statement.close(); } 
                if (connection != null) { connection.close(); }
            }
            catch (SQLException e)
            {
                e.printStackTrace();
            }
        }
    }

    private void assertClusterConfigurationAfterUpdate()
    {
        ResultSet resultSet = null;
        Connection connection = null;
        Statement statement = null;
        try
        {
            connection = configDS.getConnection();
            statement = connection.createStatement();

            // Check that the nodes exist
            resultSet = statement.executeQuery("select * from yf_node where name like 'Cluster 0 - Slave Ein'");
            Assert.assertTrue("No slave 1 node found after update", resultSet.next());
            Assert.assertEquals("Wrong id for slave 1 found", 2, resultSet.getInt("ID"));
        }
        catch (SQLException e)
        {
            e.printStackTrace();
            Assert.fail("Cannot validate cluster configuration: " + e.getLocalizedMessage());
        }
        finally
        {
            try
            {
                if (resultSet != null) { resultSet.close(); }
                if (statement != null) { statement.close(); }
                if (connection != null) { connection.close(); }
            }
            catch (SQLException e)
            {
                e.printStackTrace();
            }
        }
    }

    private void assertClusterConfigurationAfterInsert()
    {
        ResultSet resultSet = null;
        Connection connection = null;
        Statement statement = null;
        try
        {
            connection = configDS.getConnection();
            statement = connection.createStatement();

            // Check that the nodes exist
            resultSet = statement.executeQuery("select * from yf_node where name like 'Cluster 0 - Slave Three'");
            Assert.assertTrue("No slave 3 node found after update", resultSet.next());
            Assert.assertEquals("Wrong id for slave 3 found", 4, resultSet.getInt("ID"));
        }
        catch (SQLException e)
        {
            e.printStackTrace();
            Assert.fail("Cannot validate cluster configuration: " + e.getLocalizedMessage());
        }
        finally
        {
            try
            {
                if (resultSet != null) { resultSet.close(); }
                if (statement != null) { statement.close(); }
                if (connection != null) { connection.close(); }
            }
            catch (SQLException e)
            {
                e.printStackTrace();
            }
        }
    }

    private void assertClusterConfigurationAfterDelete()
    {
        ResultSet resultSet = null;
        Connection connection = null;
        Statement statement = null;
        try
        {
            connection = configDS.getConnection();
            statement = connection.createStatement();

            // Check that the third node has been deleted
            resultSet = statement.executeQuery("select * from yf_node where name like 'Cluster 0 - Slave Dos'");
            Assert.assertFalse("Slave 2 was not deleted", resultSet.next());

            resultSet = statement.executeQuery("select * from node_cluster where node_id = 3");
            Assert.assertFalse("Slave 2 was not deleted from cluster mapping table", resultSet.next());
        }
        catch (SQLException e)
        {
            e.printStackTrace();
            Assert.fail("Cannot validate cluster configuration: " + e.getLocalizedMessage());
        }
        finally
        {
            try
            {
                if (resultSet != null) {resultSet.close();}
                if (statement != null) {statement.close();}
                if (connection != null) {connection.close();}
            }
            catch (SQLException e)
            {
                e.printStackTrace();
            }
        }
    }

    public static void createSchema(BasicDataSource dataSource) throws SQLException
    {
        Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();

        TestDatabaseHelper.executeAndLogSupressingExceptions(statement, "drop schema replication_test");
        TestDatabaseHelper.executeAndLogSupressingExceptions(statement, "create schema replication_test");
        TestDatabaseHelper.executeAndLogSupressingExceptions(statement, "drop table replication_test");
        TestDatabaseHelper.executeAndLogSupressingExceptions(statement, "create table replication_test.replicate_this (name character varying(64), value integer)");
        statement.close();
        connection.close();
    }

    public static void createDatabases(final Properties postgresProperties) throws SQLException
    {
        final DataSource adminDataSource = TestDatabaseHelper.getAdminDataSource();
        final Connection adminConnection = adminDataSource.getConnection();
        final Statement adminStatement = adminConnection.createStatement();
        dropAndCreateDb(adminStatement, TestDatabaseHelper.CONFIG_DB);
        dropAndCreateDb(adminStatement, MASTER_DB);
        dropAndCreateDb(adminStatement, SLAVE1_DB);
        dropAndCreateDb(adminStatement, SLAVE2_DB);
        adminStatement.close();
        adminConnection.close();
    }

    private static void dropAndCreateDb(Statement statement, String database)
    {
        TestDatabaseHelper.executeAndLogSupressingExceptions(statement, "drop database " + database);
        TestDatabaseHelper.executeAndLogSupressingExceptions(statement, "create database " + database);
    }

    private static final Logger LOGGER = Logger.getLogger(SetupClusterFromExistingDbAcceptanceTest.class);
    private static final String MASTER_DB = "bruce_master";
    private static final String SLAVE1_DB = "bruce_slave_1";
    private static final String SLAVE2_DB = "bruce_slave_2";
    private static final String CONFIG_URL_KEY = "config.url";
    private static final String MASTER_URL_KEY = "master.url";
    private static final String SLAVE1_URL_KEY = "slave1.url";
    private static final String SLAVE2_URL_KEY = "slave2.url";

    private static Properties properties;

    private static BasicDataSource masterDS;
    private static BasicDataSource slave1DS;
    private static BasicDataSource slave2DS;
    private static BasicDataSource configDS;
}
