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

import com.netblue.bruce.cluster.Cluster;
import com.netblue.bruce.cluster.Node;
import org.apache.log4j.Logger;
import org.junit.*;

import java.sql.SQLException;
import java.util.Set;

/**
 * Tests that the daemon picks up changes in the cluster configuration on a restart
 *
 * @author lanceball
 * @version $Id: ModifyClusterAcceptanceTest.java 72519 2007-06-27 14:24:08Z lball $
 */
public class ModifyClusterAcceptanceTest extends ReplicationTest
{

    @BeforeClass
    public static void setupClusterDatabaseResources() throws SQLException
    {
        SetupClusterFromExistingDbAcceptanceTest.initializeDatabaseResources();
    }

    @AfterClass
    public static void releaseClusterDatabaseResources() throws SQLException
    {
        SetupClusterFromExistingDbAcceptanceTest.releaseDatabaseResources();
    }

    @Before
    public void setupDatabases() throws SQLException
    {
        super.setUp();
        // First let's ensure out database is setup correctly for the test
        try
        {
            SetupClusterFromExistingDbAcceptanceTest.createTestSchemas();
        }
        catch (Exception e)
        {
            LOGGER.error("Cannot create test schemas!", e);
        }
    }

    @Test
    public void testNodeInsertPickedUpByDaemon() throws SQLException
    {
        ReplicationDaemon daemon = new ReplicationDaemon();

        // create a cluster in the database 
        SetupClusterFromExistingDbAcceptanceTest.setupTestCluster();

        // Validate the cluster with a daemon
        daemon.loadCluster("ClusterOne");
        Cluster cluster = daemon.getCluster();

        Set<Node> nodes = cluster.getSlaves();
        Assert.assertEquals("Unexpected node count in cluster", 2, nodes.size());

        // Now insert one node into the cluster
        String[] args = new String[]{"-data", TestDatabaseHelper.getTestDataDir() + "/admin-test-slave-from-backup.xml",
                                     "-initnodeschema",
                                     "-initsnapshots", "MASTER",
                                     "-operation", "INSERT",
                                     "-url", TestDatabaseHelper.buildUrl(CONFIG_DB)};

        com.netblue.bruce.admin.Main.main(args);

        // Now start load a daemon and ensure that the new node was added
        daemon = new ReplicationDaemon();
        daemon.loadCluster("ClusterOne");
        cluster = daemon.getCluster();
        nodes = cluster.getSlaves();

        // validate the slave count
        Assert.assertEquals("Unexpected node count in cluster", 3, nodes.size());
    }

    @Test
    public void testMasterSlaveSwapPickedUpByDaemon() throws SQLException
    {
        ReplicationDaemon daemon = new ReplicationDaemon();

        // This test will create a cluster in the database and validate it's setup
        SetupClusterFromExistingDbAcceptanceTest.setupTestCluster();

        // Validate the cluster with a daemon
        daemon.loadCluster("ClusterOne");
        Cluster cluster = daemon.getCluster();

        // Validate that the master has a row in snapshotlog, and get the current xaction and update time
/*
        final BasicDataSource masterDataSource = TestDatabaseHelper.createDataSource(cluster.getMaster().getUri());
        final Connection masterConnection = masterDataSource.getConnection();
        final Statement statement = masterConnection.createStatement();
        final ResultSet resultSet = statement.executeQuery("select * from snapshotlog");
        Assert.assertTrue("No snapshot in master snapshotlog", resultSet.next());
        long masterCurrentXaction = resultSet.getLong("current_xaction");
        Date masterUpdateTime = resultSet.getDate("update_time");
*/

        Set<Node> nodes = cluster.getSlaves();
        Assert.assertEquals("Unexpected node count in cluster", 2, nodes.size());

        // TODO:  Add some data to master and slave to be sure it's replicated correctly
        // TODO:  Need to start a daemon to do the replication???
        // Now delete one node from the cluster
        String[] args = new String[]{"-data", TestDatabaseHelper.getTestDataDir() + "/admin-test-swap-master-slave.xml",
                                     "-initsnapshots", "SLAVE",
                                     "-operation", "CLEAN_INSERT",
                                     "-url", TestDatabaseHelper.buildUrl(CONFIG_DB)};

        com.netblue.bruce.admin.Main.main(args);

        // TODO:  After the change, check to be sure that the daemon snapshotlog and transaction log tables are correct
        // TODO:  After the change, check to be sure that the slavesnapshotstatus table is correct

        // Now start load a daemon and ensure that the master and slave were swapped
        daemon = new ReplicationDaemon();
        daemon.loadCluster("ClusterOne");
        cluster = daemon.getCluster();
        nodes = cluster.getSlaves();

        // Validate the master node
        Assert.assertEquals("Master node is incorrect", "Cluster 0 - Was slave now master", cluster.getMaster().getName());
        // validate the slave count
        Assert.assertEquals("Unexpected node count in cluster", 2, nodes.size());
    }

    @Test
    public void testNodeDeletePickedUpByDaemon() throws SQLException
    {
        ReplicationDaemon daemon = new ReplicationDaemon();

        // This test will create a cluster in the database and validate it's setup
        SetupClusterFromExistingDbAcceptanceTest.setupTestCluster();

        // Validate the cluster with a daemon
        daemon.loadCluster("ClusterOne");
        Cluster cluster = daemon.getCluster();

        Set<Node> nodes = cluster.getSlaves();
        Assert.assertEquals("Unexpected node count in cluster", 2, nodes.size());

        // Now delete one node from the cluster
        String[] args = new String[]{"-data", TestDatabaseHelper.getTestDataDir() + "/admin-test-delete.xml",
                                     "-operation", "DELETE",
                                     "-url", TestDatabaseHelper.buildUrl(CONFIG_DB)};

        com.netblue.bruce.admin.Main.main(args);

        // Now start load a daemon and ensure that the new node was added
        daemon = new ReplicationDaemon();
        daemon.loadCluster("ClusterOne");
        cluster = daemon.getCluster();
        nodes = cluster.getSlaves();

        // validate the slave count
        Assert.assertEquals("Unexpected node count in cluster", 1, nodes.size());
    }

    @Test
    public void testNodeChangePickedUpByDaemon() throws SQLException
    {
        ReplicationDaemon daemon = new ReplicationDaemon();

        // This test will create a cluster in the database and validate it's setup
        SetupClusterFromExistingDbAcceptanceTest.setupTestCluster();

        // Validate the cluster with a daemon
        daemon.loadCluster("ClusterOne");
        Cluster cluster = daemon.getCluster();

        Set<Node> nodes = cluster.getSlaves();
        Assert.assertEquals("Unexpected node count in cluster", 2, nodes.size());

        // Now modify one node from the cluster
        String[] args = new String[]{"-data", TestDatabaseHelper.getTestDataDir() + "/admin-test-update.xml",
                                     "-operation", "UPDATE",
                                     "-url", TestDatabaseHelper.buildUrl(CONFIG_DB)};

        com.netblue.bruce.admin.Main.main(args);

        // Now start load a daemon and ensure that the new node was added
        daemon = new ReplicationDaemon();
        daemon.loadCluster("ClusterOne");
        cluster = daemon.getCluster();
        nodes = cluster.getSlaves();

        // validate the slave count
        Assert.assertEquals("Unexpected node count in cluster", 2, nodes.size());
        final String modifiedName = "Cluster 0 - Slave Ein";
        final String modifiedReplicationRegEx = "replication_test\\.replicate_.+";
        boolean foundModifiedNode = false;
        for (Node node : nodes)
        {
            if (node.getName().equals(modifiedName))
            {
                foundModifiedNode = true;
                Assert.assertEquals("Table regex not modified correctly", node.getIncludeTable(), modifiedReplicationRegEx);
                break;
            }
        }
        Assert.assertTrue("Node was not modified", foundModifiedNode);
    }

    private static final Logger LOGGER = Logger.getLogger(ModifyClusterAcceptanceTest.class);
    private static final String CONFIG_DB = "bruce";
}
