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

import com.netblue.bruce.*;
import com.netblue.bruce.cluster.Cluster;
import com.netblue.bruce.cluster.ClusterFactory;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.log4j.Logger;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.XmlDataSet;
import org.junit.AfterClass;
import org.junit.Assert;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.sql.*;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Tests the SlaveRunner class
 * @author lanceball
 * @version $Id$
 */
public class SlaveRunnerTest extends ReplicationTest
{

    @Before
    public void setUp()
    {
        super.setUp();
        try
        {
            final ClusterFactory clusterFactory = ClusterFactory.getClusterFactory();
            cluster = clusterFactory.getCluster(CLUSTER_NAME);
            newCluster = clusterFactory.getCluster(NEW_CLUSTER_NAME);
            if (masterDataSource == null)
            {
                masterDataSource = new BasicDataSource();
                masterDataSource.setUrl(cluster.getMaster().getUri());
                masterDataSource.setDriverClassName(System.getProperty("bruce.jdbcDriverName", "org.postgresql.Driver"));
                masterDataSource.setValidationQuery(System.getProperty("bruce.poolQuery", "select now()"));
            }
	    // Make sure that at least one snapshot/transaction log exists before we do anything else
	    LogSwitchThread lt = new LogSwitchThread(new BruceProperties(),masterDataSource);
	    Connection c = masterDataSource.getConnection();
	    Statement s = c.createStatement();
	    lt.newLogTable(s);
	    c.close();
	    s.close();
        }
        catch (Exception e)
        {
            fail(e.getLocalizedMessage());
        }
    }


    @AfterClass
    public static void releaseDatabaseResources()
    {
        try
        {
            if (masterDataSource != null)
            {
                masterDataSource.close();
            }
        }
        catch (SQLException e)
        {
            Logger.getLogger(SlaveRunnerTest.class).error("Cannot close datasource", e);
        }
    }

    @Test
    public void testNoLastSnapshotExists()
    {
        SlaveRunner slaveRunner = new SlaveRunner(masterDataSource, newCluster, newCluster.getSlaves().iterator().next());
        assertNull("Last processed snapshot should be null", slaveRunner.getLastProcessedSnapshot());
        slaveRunner.shutdown();
    }

    @Test public void testLastSnapshotAtStartup()
    {
        final SlaveRunner slaveRunner = makeSlaveRunner();
        final Snapshot snapshot = slaveRunner.getLastProcessedSnapshot();
        assertNotNull("Last processed snapshot should not be null", snapshot);
        assertEquals("Unexpected values for last snapshot", new TransactionID(99L), snapshot.getMinXid());
        assertEquals("Unexpected values for last snapshot", new TransactionID(100L), snapshot.getMaxXid());
        slaveRunner.shutdown();
    }

    /**
     * Tests that the SlaveRunner updates status for an existing slave with a previous status already there
     */
    @Test
    public void testProcessSnapshotUpdatesStatus()
    {
        final TransactionID minId = new TransactionID(101L);
        final TransactionID maxId = new TransactionID(102L);
        final SortedSet<TransactionID> inFlightIds = new TreeSet<TransactionID>();
        final Snapshot snapshot = new Snapshot(minId, minId, maxId, inFlightIds);
        final SlaveRunner slaveRunner = makeSlaveRunner();

        // Here's what we are testing
        slaveRunner.processSnapshot(snapshot);

        // Check that the class returns the new snapshot
        assertEquals("processSnapshot did not update itself", slaveRunner.getLastProcessedSnapshot(), snapshot);

        // now check to see if the slave's status was updated in the database
        // this test assumes that SlaveRunner#queryForLastProcessedSnapshot works as
        // advertised.  It is possible that it doesn't.  If not, that should be caught
        // in other tests
        try
        {
            assertEquals("processSnapshot did not update status table", slaveRunner.queryForLastProcessedSnapshot(), snapshot);
        }
        catch (SQLException e)
        {
            fail(e.getLocalizedMessage());
        }
        slaveRunner.shutdown();
    }


    private SlaveRunner makeSlaveRunner()
    {
        SlaveRunner slaveRunner = new SlaveRunner(masterDataSource, cluster, cluster.getSlaves().iterator().next());
        return slaveRunner;
    }

    /**
     * Get the data set under test.  The {@link #setUp()} method will call use this data to perform a {@link
     * org.dbunit.operation.DatabaseOperation.CLEAN_INSERT} on the database.
     *
     * @return
     */
    protected IDataSet getTestDataSet()
    {
        if (dataSet == null)
        {
            try
            {
                dataSet = new XmlDataSet(new FileInputStream(TestDatabaseHelper.getDataFile("slave-status.xml")));
            }
            catch (Exception e)
            {
                e.printStackTrace();
                Assert.fail("Cannot load test dataset:  " + e.getLocalizedMessage());
            }
        }
        return dataSet;
    }


    private IDataSet dataSet = null;
    private static final String CLUSTER_NAME = "Cluster Un";
    private static final String NEW_CLUSTER_NAME = "Cluster Deux";
    private static Cluster cluster;
    private static Cluster newCluster;
    private static BasicDataSource masterDataSource;
}
