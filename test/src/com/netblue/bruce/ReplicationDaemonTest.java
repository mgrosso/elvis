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

import com.netblue.bruce.admin.ReplicationDatabaseBuilder;
import com.netblue.bruce.cluster.Cluster;
import com.netblue.bruce.cluster.ClusterInitializationException;
import com.netblue.bruce.cluster.Node;
import org.apache.log4j.*;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.*;
import java.util.Set;

/**
 * Tests the ReplicationDaemon class
 *
 * @author lanceball
 * @version $Id$
 */
public class ReplicationDaemonTest extends ReplicationTest
{
    static
    {
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure();
        Logger.getRootLogger().removeAllAppenders();
        Logger.getRootLogger().addAppender(new ConsoleAppender(new PatternLayout("%d{HH:mm:ss,SSS} [%t] %p %l %x%n%m%n")));
        // Suppress messages by default. Main effect is to suppress hibernate output.
        Logger.getRootLogger().setLevel(Level.OFF);
    }

    /**
     * Initializes the database.  If you need to create tables, indices, etc at runtime, you can use this method to do
     * that.  If not, just make it a no-op.
     *
     * @param connection A JDBC connection with auto commit turned on
     */
    protected void setUpDatabase(Connection connection)
    {
        super.setUpDatabase(connection);
	ReplicationDatabaseBuilder rdb = new ReplicationDatabaseBuilder();
        TestDatabaseHelper.applyDDLFromSArray(connection, rdb.getSqlStrings());
    }

    // Setup slavesnapshotstatus if it is not already set up.
    @BeforeClass
    public static void setupSlaves()
    {
        Snapshot masterSnapshot;
        Connection masterConnection = null;
        Connection slaveConnection = null;
        try
        {
            ReplicationDaemon daemon = new ReplicationDaemon();
            daemon.loadCluster(CLUSTER_NAME);
            final Cluster cluster = daemon.getCluster();
            Long clusterID = cluster.getId();
            String masterURL = cluster.getMaster().getUri();
            masterConnection = DriverManager.getConnection(masterURL);
            masterConnection.createStatement().execute(createMasterSnapshot);
            ResultSet rs = masterConnection.createStatement().executeQuery(lastMasterSnapshotQuery);
            if (rs.next())
            {
                masterSnapshot = new Snapshot(new TransactionID(rs.getLong("current_xaction")),
                                              new TransactionID(rs.getLong("min_xaction")),
                                              new TransactionID(rs.getLong("max_xaction")),
                                              rs.getString("outstanding_xactions"));
            }
            else
            {
                throw new SQLException("Unable to aquire master snapshot");
            }
            slaveConnection = null;
            Set<Node> slaves = cluster.getSlaves();
            for (Node slave : slaves)
            {
                slaveConnection = DriverManager.getConnection(slave.getUri());
                // Determine if this slave already has a snapshotstatusentry
                PreparedStatement sPs = slaveConnection.prepareStatement(getSlaveSnapshot);
                sPs.setLong(1, clusterID);
                ResultSet sRs = sPs.executeQuery();
                if (!sRs.next())
                { // Slave DOES not already have a slavesnapshotstatus entry
                    sPs = slaveConnection.prepareStatement(createSlaveSnapshot);
                    sPs.setLong(1, new Long(clusterID));
                    sPs.setLong(2, masterSnapshot.getCurrentXid().getLong());
                    sPs.setLong(3, masterSnapshot.getMinXid().getLong());
                    sPs.setLong(4, masterSnapshot.getMaxXid().getLong());
                    sPs.setString(5, masterSnapshot.getInFlight());
                    sPs.execute();
                }
                slaveConnection.close();
            }
        }
        catch (SQLException e)
        {
            LOGGER.error("SQLExcption in test class setup", e);
        }
        catch (Exception e)
        {
            LOGGER.error("Exception in test class setup", e);
        }
        finally
        {
            if (masterConnection != null)
            {
                try
                {
                    masterConnection.close();
                }
                catch (SQLException e) {}
            }
            if (slaveConnection != null)
            {
                try
                {
                    slaveConnection.close();
                }
                catch (SQLException e) {}
            }
        }
    }


    @Test
    public void testLoadCluster()
    {
        ReplicationDaemon daemon = new ReplicationDaemon();
        daemon.loadCluster(CLUSTER_NAME);
        final Cluster cluster = daemon.getCluster();
        assertNotNull("Cluster was not loaded", cluster);
        assertEquals(CLUSTER_NAME, cluster.getName());
        assertNotNull(cluster.getMaster());
        assertEquals(2, cluster.getSlaves().size());
        daemon.shutdown();
    }

    @Test
    public void testLoadClusterNullName()
    {
        try
        {
            ReplicationDaemon daemon = new ReplicationDaemon();
            daemon.loadCluster(null);
            fail("ReplicationDaemon should throw an exception if a null cluster name is provided");
        }
        catch (Exception e)
        {
            assertTrue(e instanceof ClusterInitializationException);
        }
    }

    @Test
    public void testRunNoClusterInitialized()
    {
        try
        {
            ReplicationDaemon daemon = new ReplicationDaemon();
            daemon.run();
            fail("ReplicationDaemon should throw an exception if it's run without loading a cluster");
        }
        catch (Exception e)
        {
            assertTrue(e instanceof ClusterInitializationException);
        }
    }

    @Test
    public void testRun()
    {
        System.setProperty("bruce.logSwitchDelay", "10000");
        ReplicationDaemon daemon = new ReplicationDaemon();
        daemon.loadCluster(CLUSTER_NAME);
        daemon.run();
        assertEquals(2, daemon.getActiveSlaveCount());
        daemon.shutdown();
    }

    // Does not handle TID wraparound, but should be sufficient for our purposes here
    private final static String lastMasterSnapshotQuery =
            "select * from bruce.snapshotlog " +
                    " where current_xaction = (select max(current_xaction) from bruce.snapshotlog)";
    private final static String createMasterSnapshot =
            "select logsnapshot()";
    private final static String getSlaveSnapshot =
            "select * from bruce.slavesnapshotstatus where clusterid = ?";
    private final static String createSlaveSnapshot =
            "insert into bruce.slavesnapshotstatus " +
                    "(clusterid,slave_xaction,master_current_xaction,master_min_xaction,master_max_xaction," +
                    " master_outstanding_xactions) " +
                    "values (?,1,?,?,?,?)";

    private static final Logger LOGGER = Logger.getLogger(ReplicationDaemon.class);
}
