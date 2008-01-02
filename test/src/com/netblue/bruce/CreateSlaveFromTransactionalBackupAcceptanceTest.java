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
import com.netblue.bruce.cluster.ClusterFactory;
import com.netblue.bruce.cluster.Node;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Tests taking a transactional backup of a master in a replication cluster and
 * creating a slave node from that backup.
 * @author lanceball
 * @version $Id$
 */
public class CreateSlaveFromTransactionalBackupAcceptanceTest extends ReplicationTest
{
    @Before
    public void setPostgresPort()
    {
        port = TestDatabaseHelper.getPostgresProperties().getProperty(TestDatabaseHelper.POSTGRESQL_PORT_KEY, TestDatabaseHelper.POSTGRESQL_PORT_DEFAULT);
    }
    
    @Test
    public void testCreateSlaveFromMasterBackup() 
    {
        LOGGER.setLevel(Level.DEBUG);
        Connection adminConnection = null;
        Connection slaveConnection = null;
        try
        {
            // Create a new database
            final DataSource adminDataSource = TestDatabaseHelper.getAdminDataSource();
            adminConnection = adminDataSource.getConnection();
            final Statement adminStatement = adminConnection.createStatement();
            TestDatabaseHelper.executeAndLogSupressingExceptions(adminStatement, "drop database " + NEW_DB);
            TestDatabaseHelper.executeAndLogSupressingExceptions(adminStatement, "create database " + NEW_DB);
            adminConnection.commit();

            // Make sure we have a table that can replicate on zee new node
            final BasicDataSource slaveDataSource = TestDatabaseHelper.createDataSource(TestDatabaseHelper.buildUrl(NEW_DB));
            slaveConnection = slaveDataSource.getConnection();
            final Statement slaveStatement = slaveConnection.createStatement();
            TestDatabaseHelper.executeAndLogSupressingExceptions(slaveStatement, "drop schema replication_test cascade");
            TestDatabaseHelper.executeAndLogSupressingExceptions(slaveStatement, "drop schema bruce cascade");

            // Use psql to load a dump of a master database
            final String postgresBinDir = System.getProperty("postgresql.bin");
            final String psqlBinary = (postgresBinDir == null ? "" : postgresBinDir + "/") + "psql";
            final String command = psqlBinary + " -p " + System.getProperty("postgresql.port")
                    + " -U " + System.getProperty("postgresql.user") + " " + NEW_DB
                    + " -f " + TestDatabaseHelper.getDataFile("bruce_master.dump.sql").getAbsolutePath();
            LOGGER.info("Restoring master database from backup.  Command = " + command);
            final Process process = Runtime.getRuntime().exec(command);
            process.waitFor();
            final InputStream errorStream = process.getErrorStream();
            byte[] output = new byte[128];
            while(errorStream.read(output) != -1)
            {
                String outputString = new String(output);
                System.err.print(outputString);
            }
            LOGGER.info("Master database restored for testing and conversion to slave");

            // Now use the admin tool to add this new node to the cluster and see how things work out
            String[] args = new String[]{"-data", TestDatabaseHelper.getTestDataDir() + "/admin-test-slave-from-backup.xml",
                                         "-initsnapshots", "SLAVE",
                                         "-operation", "INSERT",
                                         "-url", TestDatabaseHelper.buildUrl(TestDatabaseHelper.CONFIG_DB)};

            com.netblue.bruce.admin.Main.main(args);

            // We need to be sure that the node was added.  First check the config DB to see if it's there
            final Node addedNode = ClusterFactory.getClusterFactory().getNode("Restored From Backup");
            Assert.assertNotNull("New node was not added correctly", addedNode);
            Assert.assertEquals("Wrong URI for new node", "jdbc:postgresql://localhost:"+port+"/new_slave_node?user=bruce", addedNode.getUri());
            Assert.assertEquals("Wrong ID for new node", 10L, addedNode.getId());
            Assert.assertEquals("Wrong INCLUDE_TABLE for new node", "replication_test\\.replicate_.+", addedNode.getIncludeTable());

            // Now validate that the new node has the replication schema installed
            Assert.assertTrue("Correct replication schema was not installed on new node", Version.isSameVersion(slaveDataSource));
            
            // Now check the replicated table to see that triggers were added.
            ResultSet slaveResultSet = slaveStatement.executeQuery("select t.tgname as trigger from pg_class c, pg_trigger t, pg_namespace n  where t.tgname = 'replicate_this_deny' and t.tgrelid = c.oid and c.relnamespace = n.oid and c.relname = 'replicate_this' and n.nspname = 'replication_test'");
            Assert.assertTrue("Deny trigger not set on new slave node", slaveResultSet.next());

            slaveResultSet = slaveStatement.executeQuery("select t.tgname as trigger from pg_class c, pg_trigger t, pg_namespace n  where t.tgname = 'replicate_this_sn' and t.tgrelid = c.oid and c.relnamespace = n.oid and c.relname = 'replicate_this' and n.nspname = 'replication_test'");
            Assert.assertTrue("Snapshot trigger removed on new slave node", slaveResultSet.next());

            slaveResultSet = slaveStatement.executeQuery("select t.tgname as trigger from pg_class c, pg_trigger t, pg_namespace n  where t.tgname = 'replicate_this_tx' and t.tgrelid = c.oid and c.relnamespace = n.oid and c.relname = 'replicate_this' and n.nspname = 'replication_test'");
            Assert.assertTrue("Transaction trigger removed on new slave node", slaveResultSet.next());


            // Now check that the slavesnapshotstatus table has expected values
            slaveResultSet = slaveStatement.executeQuery("select * from bruce.slavesnapshotstatus");
            Assert.assertTrue("SLAVESNAPSHOTSTATUS table should not be empty", slaveResultSet.next());

            // We know these expected values because the node was created from a static backup
            Assert.assertEquals(16527808L, slaveResultSet.getLong("master_current_xaction"));
            Assert.assertEquals(16527808L, slaveResultSet.getLong("master_min_xaction"));
            Assert.assertEquals(16527809L, slaveResultSet.getLong("master_max_xaction"));
            Assert.assertEquals(1000, slaveResultSet.getInt("clusterid"));
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Assert.fail("Unexpected exception during test: " + e.getLocalizedMessage());
        }
        finally
        {
            if (adminConnection != null)
            {
                try
                {
                    adminConnection.close();
                }
                catch (SQLException e)
                {
                    e.printStackTrace(); // TODO:  Autogenerated code stub
                }
            }
            if (slaveConnection != null)
            {
                try
                {
                    slaveConnection.close();
                }
                catch (SQLException e)
                {
                    e.printStackTrace(); // TODO:  Autogenerated code stub
                }
            }
        }
    }

    @Test
    public void testCreateSlaveFromSlaveBackup() 
    {
        LOGGER.setLevel(Level.DEBUG);
        Connection adminConnection = null;
        Connection slaveConnection = null;
        try
        {
            // Create a new database
            final DataSource adminDataSource = TestDatabaseHelper.getAdminDataSource();
            adminConnection = adminDataSource.getConnection();
            final Statement adminStatement = adminConnection.createStatement();
            TestDatabaseHelper.executeAndLogSupressingExceptions(adminStatement, "drop database " + NEW_DB);
            TestDatabaseHelper.executeAndLogSupressingExceptions(adminStatement, "create database " + NEW_DB);
            adminConnection.commit();

            // Make sure we have a table that can replicate on zee new node
            final BasicDataSource slaveDataSource = TestDatabaseHelper.createDataSource(TestDatabaseHelper.buildUrl(NEW_DB));
            slaveConnection = slaveDataSource.getConnection();
            final Statement slaveStatement = slaveConnection.createStatement();
            TestDatabaseHelper.executeAndLogSupressingExceptions(slaveStatement, "drop schema replication_test cascade");
            TestDatabaseHelper.executeAndLogSupressingExceptions(slaveStatement, "drop schema bruce cascade");

            // Use psql to load a dump of a master database
            final String postgresBinDir = System.getProperty("postgresql.bin");
            final String psqlBinary = (postgresBinDir == null ? "" : postgresBinDir + "/") + "psql";
            final String command = psqlBinary + " -p " + System.getProperty("postgresql.port") + " "
                    + " -U " + System.getProperty("postgresql.user") + " " + NEW_DB
                    + " -f " + TestDatabaseHelper.getDataFile("bruce_slave_1.dump.sql").getAbsolutePath();
            LOGGER.info("Restoring master database from backup.  Command = " + command);
            final Process process = Runtime.getRuntime().exec(command);
            process.waitFor();
            final InputStream errorStream = process.getErrorStream();
            byte[] output = new byte[128];
            while(errorStream.read(output) != -1)
            {
                String outputString = new String(output);
                System.err.print(outputString);
            }
            LOGGER.info("Master database restored for testing and conversion to slave");

            // Now use the admin tool to add this new node to the cluster and see how things work out
            String[] args = new String[]{"-data", TestDatabaseHelper.getTestDataDir() + "/admin-test-slave-from-backup.xml",
                                         "-initsnapshots", "NONE",
                                         "-operation", "INSERT",
                                         "-url", TestDatabaseHelper.buildUrl(TestDatabaseHelper.CONFIG_DB)};

            com.netblue.bruce.admin.Main.main(args);

            // We need to be sure that the node was added.  First check the config DB to see if it's there
            final Node addedNode = ClusterFactory.getClusterFactory().getNode("Restored From Backup");
            Assert.assertNotNull("New node was not added correctly", addedNode);
            Assert.assertEquals("Wrong URI for new node", "jdbc:postgresql://localhost:"+port+"/new_slave_node?user=bruce", addedNode.getUri());
            Assert.assertEquals("Wrong ID for new node", 10L, addedNode.getId());
            Assert.assertEquals("Wrong INCLUDE_TABLE for new node", "replication_test\\.replicate_.+", addedNode.getIncludeTable());

            // Now validate that the new node has the replication schema installed
            Assert.assertTrue("Correct replication schema was not installed on new node", Version.isSameVersion(slaveDataSource));

            // Now check the replicated table to see that triggers were added.
            ResultSet slaveResultSet = slaveStatement.executeQuery("select t.tgname as trigger from pg_class c, pg_trigger t, pg_namespace n  where t.tgname = 'replicate_this_deny' and t.tgrelid = c.oid and c.relnamespace = n.oid and c.relname = 'replicate_this' and n.nspname = 'replication_test'");
            Assert.assertTrue("Deny trigger not set on new slave node", slaveResultSet.next());

            // Now check that the slavesnapshotstatus table has expected values
            slaveResultSet = slaveStatement.executeQuery("select * from bruce.slavesnapshotstatus");
            Assert.assertTrue("SLAVESNAPSHOTSTATUS table should not be empty", slaveResultSet.next());

            // We know these expected values because the node was created from a static backup
            Assert.assertEquals(16527812L, slaveResultSet.getLong("slave_xaction"));
            Assert.assertEquals(16527808L, slaveResultSet.getLong("master_current_xaction"));
            Assert.assertEquals(16527808L, slaveResultSet.getLong("master_min_xaction"));
            Assert.assertEquals(16527809L, slaveResultSet.getLong("master_max_xaction"));
            Assert.assertEquals(1000, slaveResultSet.getInt("clusterid"));
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Assert.fail("Unexpected exception during test: " + e.getLocalizedMessage());
        }
        finally
        {
            if (adminConnection != null)
            {
                try
                {
                    adminConnection.close();
                }
                catch (SQLException e)
                {
                    e.printStackTrace(); // TODO:  Autogenerated code stub
                }
            }
            if (slaveConnection != null)
            {
                try
                {
                    slaveConnection.close();
                }
                catch (SQLException e)
                {
                    e.printStackTrace(); // TODO:  Autogenerated code stub
                }
            }
        }
    }

    private final String NEW_DB = "new_slave_node";
    private static final Logger LOGGER = Logger.getLogger(CreateSlaveFromTransactionalBackupAcceptanceTest.class);
    private String port;
}
