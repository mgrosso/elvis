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

import com.netblue.bruce.cluster.*;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.log4j.Logger;
import org.junit.*;
import static org.junit.Assert.*;

import java.sql.*;

/**
 * Tests the SlaveRunner class. We are testing the general logic of the class, but not testing the 
 * run() method, which does not lend itself to being tested in a single thread.
 * @author rklahn
 * Strongly based on original SlaveRunnerTest by lanceball
 * @version $Id$
 */
public class SlaveRunnerTest {
    @BeforeClass public static void setupBeforeClass() 
	throws SQLException, IllegalAccessException, InstantiationException {
	TestDatabaseHelper.createNamedTestDatabase("bruce_master");
	TestDatabaseHelper.createNamedTestDatabase("bruce_slave_1");
	// Use the admin tool to create a cluster
	String[] args = new String[]{"-data",
				     TestDatabaseHelper.getTestDataDir() + "/slave-runner-test.xml",
				     "-initnodeschema",
				     "-initsnapshots", "MASTER",
				     "-loadschema",
				     "-operation", "CLEAN_INSERT",
				     "-url", TestDatabaseHelper.buildUrl("bruce_master")};
	com.netblue.bruce.admin.Main.main(args);
	ClusterFactory clusterFactory = ClusterFactory.getClusterFactory();
	cluster = clusterFactory.getCluster(CLUSTER_NAME);
	node = cluster.getSlaves().iterator().next();
	masterDataSource = new BasicDataSource();
	masterDataSource.setUrl(cluster.getMaster().getUri());
	masterDataSource.setDriverClassName(System.getProperty("bruce.jdbcDriverName","org.postgresql.Driver"));
	masterDataSource.setValidationQuery(System.getProperty("bruce.poolQuery", "select now()"));
    }

    @AfterClass public static void teardownAfterClass() throws SQLException {
	if (masterDataSource != null) {
	    masterDataSource.close();
	}
    }

    @Test public void testLastSnapshotAtStartup() {
	SlaveRunner sr = new SlaveRunner(masterDataSource,cluster,node);
	Snapshot lps = sr.getLastProcessedSnapshot();
	assertNotNull("Last processed snapshot should not be null",lps);
	Snapshot mns = sr.getNextSnapshot();
	// As we have applied no snapshots to the master database, mns should be null
	assertNull("Next snapshot should be null",mns);
    }
    
    @Test public void testProcessSnapshot() throws SQLException {
	SlaveRunner sr = new SlaveRunner(masterDataSource,cluster,node);
	Snapshot lps = sr.getLastProcessedSnapshot();
	assertNotNull("Last processed snapshot should not be null",lps);
	Connection c = masterDataSource.getConnection();
	try { // Make sure connection gets closed
	    Statement s = c.createStatement();
	    try { // Make sure statement gets closed 
		s.execute("select bruce.logsnapshot()");
	    } finally {
		s.close();
	    }
	} finally {
	    c.close();
	}
	Snapshot mns = sr.getNextSnapshot();
	// As we have applied a snapshot to the master, mns should not be null
	assertNotNull("Next snapshot should not be null",mns);
	assertTrue("Next snapshot should be greater than last processed snapshot",mns.compareTo(lps)>0);
	
	// This is what we are really testing. 
	sr.processSnapshot(mns);

	// Check that the class returns the new snapshot
	assertEquals("processSnapshot did not update itself",sr.getLastProcessedSnapshot(),mns);

	// Check to see if the Slave's slavesnapshotstatus was actually updated in the database.
	// We are presuming that SlaveRunner.queryForLastProcessedSnapshot() works as advertised.
	// But if its not, we are likely to fail here, because we are unlikely to get back the 
	// same snapshot from SlaveRunner.queryForLastProcessedSnapshot() as we have in mns.
	assertEquals("processSnapshot did not update status table", sr.queryForLastProcessedSnapshot(),mns);
    }
    
    private final static String CLUSTER_NAME = "Cluster Un";
    private final static Logger logger = Logger.getLogger(SlaveRunnerTest.class);
    private static BasicDataSource masterDataSource;
    private static Cluster cluster;
    private static Node node;
}
