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

import junit.framework.JUnit4TestAdapter;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * @author rklahn
 */
public class SnapshotCacheTest extends ReplicationTest
{

    // To run with Ant (version 1.6.5???), we need this suite() method to run our tests.
    // Ant uses an JUnit 3.x runner instead of a 4.X one. See http://junit.sourceforge.net/doc/faq/faq.htm#tests_1
    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(SnapshotCacheTest.class);
    }

    protected void setUpDatabase(Connection connection)
    {
        super.setUpDatabase(connection);
        TestDatabaseHelper.applyDDLFromFile(connection, SCHEMA_UNIT_TESTS_DDL_SQL);
    }

    @Before
    public void setUp()
    {
        super.setUp();
        // Root logger is off, light up our messages
        //logger.setLevel(Level.TRACE);
        logger.setLevel(Level.DEBUG);
        // Light up messages for the class we are testing
        //logger.getLogger(SnapshotCache.class).setLevel(Level.TRACE);
        logger.getLogger(SnapshotCache.class).setLevel(Level.DEBUG);
        logger.getLogger(ReplicationTest.class).setLevel(Level.INFO);
        logger.getLogger(DBUnitAbstractInitializer.class).setLevel(Level.INFO);

        cache = new SnapshotCache(TestDatabaseHelper.getTestDataSource(), 100);
        logger.debug("--------------Begin Test----------------");
        Runtime r = Runtime.getRuntime();
        logger.trace("free:" + r.freeMemory() + " total:" + r.totalMemory() + " max:" + r.maxMemory());
    }

    @After
    public void afterTest()
    {
        logger.debug("---------------End Test-----------------");
        Runtime r = Runtime.getRuntime();
        logger.trace("free:" + r.freeMemory() + " total:" + r.totalMemory() + " max:" + r.maxMemory());
    }

    @Test
    public void testGetSnapshotSimple() throws SQLException
    {
        logger.debug("TEST: testGetSnapshotSimple");
        Connection c = TestDatabaseHelper.getTestDatabaseConnection();
        boolean autoCommit = true;
        try
        {
            autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
        }
        catch (SQLException e)
        {
            logger.warn("Cannot set autoCommit state on JDBC Connection.  Message: " + e.getLocalizedMessage());
            e.printStackTrace();
        }
        c.setSavepoint();
        // Create a snapshot
        TestDatabaseHelper.executeAndLog(c.createStatement(), "select bruce.logsnapshot()");
        c.commit();
        // Retreve the snapshot directly from the database, and construct a Snapshot object from it.
        Snapshot s = getLastSnapshot();
        TransactionID xid = s.getCurrentXid();
        // Try and get the same snapshot from the Cache. Measure the time it took. This should require the
        // Cache to get the snapshot from the database.
        long beforeNS = System.nanoTime();
        Snapshot sc = cache.getSnapshot(xid);
        long afterNS = System.nanoTime();
        long retrevalTimeDB = afterNS - beforeNS;
        logger.debug("DB Retreval took (ns):" + retrevalTimeDB);
        assertEquals(s, sc);
        // Try and get the same snapshot from the Cache again. Measure the time it took. This time,
        // the Snapshot should come out of the Cache memory, and should be substantialy faster.
        beforeNS = System.nanoTime();
        sc = cache.getSnapshot(xid);
        afterNS = System.nanoTime();
        long retrevalTimeMem = afterNS - beforeNS;
        logger.debug("Memory Retreval took (ns):" + retrevalTimeMem);
        assertEquals(s, sc);
        c.setAutoCommit(autoCommit);
        // Memory retreval less than database retreval (by at least two orders of magnatude)
        // except when trace logging, in which case, we just expect it to be less.
        if (logger.isTraceEnabled() || logger.getLogger(SnapshotCache.class).isTraceEnabled())
        {
            assertTrue(retrevalTimeDB > retrevalTimeMem);
        }
        else
        {
            assertTrue(retrevalTimeDB > (retrevalTimeMem * 100));
        }
    }

    @Test
    public void testGetNextSnapshotSimple() throws SQLException
    {
        logger.debug("TEST: testGetNextSnapshotSimple");
        Connection c = TestDatabaseHelper.getTestDatabaseConnection();
        boolean autoCommit = true;
        try
        {
            autoCommit = c.getAutoCommit();
            c.setAutoCommit(true);
        }
        catch (SQLException e)
        {
            logger.warn("Cannot set autoCommit state on JDBC Connection.  Message: " + e.getLocalizedMessage());
            e.printStackTrace();
        }
        // Make sure we have at least one snapshot, and extract the TransactionID from it
        TestDatabaseHelper.executeAndLog(c.createStatement(), "select bruce.logsnapshot()");
        Snapshot s1 = getLastSnapshot();
        // Now, 110 times, create a snapshot, get the last snapshot,
        // make sure its greater than the last
        // retreved snapshot. 110 times so that we know that we fill
        // the cache, and snapshots drop out of
        // the cache.
        for (int i = 0; i < 110; i++)
        {
            TestDatabaseHelper.executeAndLog(c.createStatement(), "select bruce.logsnapshot()");
            Snapshot s2 = cache.getNextSnapshot(s1);
            assertTrue(s1.compareTo(s2) < 0);
            assertTrue(cache.getSnapshotUsed() <= 100);
            s1 = s2;
        }
        c.setAutoCommit(autoCommit);
    }

    @Test
    public void testMultithreaded() throws InterruptedException, SQLException, IOException
    {
        logger.debug("TEST: testMultithreaded");
        cache = new SnapshotCache(TestDatabaseHelper.getTestDataSource());
        ((BasicDataSource) TestDatabaseHelper.getTestDataSource()).setMaxActive(50);
        // Keep track of created threads
        Set<MultithreadedThread> updateThreads =
                Collections.synchronizedSet(new HashSet<MultithreadedThread>());
        Set<MultithreadedThread> emulateSlaveThreads =
                Collections.synchronizedSet(new HashSet<MultithreadedThread>());
        // Set to hold AssertionErrors thrown by each thread (Presumably from failed tests)
        Set<MultithreadedError> testResults = Collections.synchronizedSet(new HashSet<MultithreadedError>());
        // Set up 1 database update threads (Long running updates)
        for (int i = 0; i < 1; i++) { updateThreads.add(new DBLongUpdateThread(testResults)); }
        // Set up 10 database update threads (Short running updates)
        for (int i = 0; i < 1; i++) { updateThreads.add(new DBShortUpdateThread(testResults)); }
        // Set up 10 emulate slave threads
        for (int i = 0; i < 1; i++)
        {
            emulateSlaveThreads.add(new EmulateSlaveThread(testResults, cache, "EmulateSlaveThread-"+i));
        }
        // Start each database update thread
        for (MultithreadedThread t : updateThreads) { t.start(); }
        // Start each emulate slave thread
        for (MultithreadedThread t : emulateSlaveThreads) { t.start(); }
        // Start up a thread to force a regular snapshot. We are only going to terminate
        // the emulateSlaveThraeds
        // when no changes were applied in the last Snapshot processing loop.
        MultithreadedThread forceSnapshotThread = new ForceSnapshot(testResults,"ForceSnapshot");
        forceSnapshotThread.start();
        Thread.currentThread().sleep(10000L); // run the test for XXXXX
        // Stop database update threads
        for (MultithreadedThread t : updateThreads)
        {
            t.shutdown();
            t.join();
        }
        // Order each emulated slave to stop - This make take some time because a slave thread
        // will only
        // terminate when it has run out of transactions to apply.
        for (MultithreadedThread t : emulateSlaveThreads)
        {
	    logger.trace("shutting down emulate thread:"+t);
            t.shutdown();
            t.join();
        }
        // stop the snapshot thread
        forceSnapshotThread.shutdown();
        forceSnapshotThread.join();
        // Process any failed results
        if (testResults.size() > 0)
        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos);
            ps.println("failed tests during multi-threaded testing");

            for (MultithreadedError e : testResults)
            {
                logger.error("In thread:" + e.t.getName(), e.a);
            }
            fail("failed tests during multi-threaded testing");
        }
        // Take a database dump of the test database, for later examination
//        if (logger.isDebugEnabled())
//        {
//            logger.debug("Dumping database");
//            Process p = Runtime.getRuntime().exec("pg_dump --file=bruce.pg_dump." + (new java.util.Date()).getTime() + " bruce", null, null);
//            p.waitFor();
//        }
        // Test master table vs. emulated slave tables. Should have the same row count and contents;
        Connection c = TestDatabaseHelper.getTestDatabaseConnection();
        boolean autoCommit = true;
        try
        {
            autoCommit = c.getAutoCommit();
            c.setAutoCommit(true);
        }
        catch (SQLException e)
        {
            logger.warn("Cannot set autoCommit state on JDBC Connection.  Message: " + e.getLocalizedMessage());
            e.printStackTrace();
        }
        // First determine the names of the emulated slave tables
        Set<String> emulatedSlaveTables = Collections.synchronizedSet(new HashSet<String>());
        ResultSet rs1 = TestDatabaseHelper.executeQueryAndLog(c.createStatement(),
                                                              "select relname from pg_class " +
                                                                      // relkind == 'r' == table (vice view, sequence, etc.)
                                                                      " where relkind = 'r' and relname like 'test_rnd%'");
        while (rs1.next())
        {
            emulatedSlaveTables.add(rs1.getString("relname"));
        }
        // IDs in the master table
        HashMap<String, TreeSet<Long>> idM = new HashMap<String, TreeSet<Long>>();
        TreeSet<Long> test1TS = new TreeSet<Long>();
        rs1 = TestDatabaseHelper.executeQueryAndLog(c.createStatement(),
                                                    "select id from bruce.test1");
        while (rs1.next())
        {
            test1TS.add(new Long(rs1.getLong(1)));
        }
        idM.put("test1", test1TS);
        // IDs in each slave table
        for (String tableName : emulatedSlaveTables)
        {
            TreeSet<Long> slaveTS = new TreeSet<Long>();
            rs1 = TestDatabaseHelper.executeQueryAndLog(c.createStatement(),
                                                        "select id from bruce." + tableName);
            while (rs1.next())
            {
                slaveTS.add(new Long(rs1.getLong(1)));
            }
            idM.put(tableName, slaveTS);
        }
        // Count of IDs should be the same in both master and slave tables
        Long mCount = new Long(idM.get("test1").size());
        for (String key : idM.keySet())
        {
            Long sCount = new Long(idM.get(key).size());
            if (!mCount.equals(sCount))
            {
                fail("Count of rows between tables not equal. test1:" + mCount + " " + key + ":" + sCount);
            }
        }
        // ID content should be the same on both master and slave tables
        for (String key : idM.keySet())
        {
            if (!key.equals("test1"))
            { // skip comparing the master to itself
                TreeSet<Long> uniqueIDs = new TreeSet<Long>(idM.get("test1"));
                uniqueIDs.removeAll(idM.get(key));
                if (!uniqueIDs.isEmpty()) { fail("UniqueIDs exist in master table. IDs:" + uniqueIDs.toString()); }
                uniqueIDs.clear();
                uniqueIDs.addAll(idM.get(key));
                uniqueIDs.removeAll(idM.get("test1"));
                if (!uniqueIDs.isEmpty())
                {
                    fail("UniqueIDs exist in slave table. table:" + key + " IDs:" + uniqueIDs.toString());
                }
            }
        }
        // Row content should be the same on both master and slave tables
        for (String key : idM.keySet())
        {
            if (!key.equals("test1"))
            { // skip comparing the master to itself
                rs1 =
                        TestDatabaseHelper.executeQueryAndLog(c.createStatement(),
                                                              "select sum(case " +
                                                                      "           when test1.c_bytea = t.c_bytea then 0 " +
                                                                      "           when test1.c_bytea is null " +
                                                                      "                AND t.c_bytea is null then 0 " +
                                                                      "           else 1 end) + " +
                                                                      "       sum(case " +
                                                                      "           when test1.c_text = t.c_text then 0 " +
                                                                      "           when test1.c_text is null " +
                                                                      "                AND t.c_text is null then 0 " +
                                                                      "           else 1 end) + " +
                                                                      "       sum(case " +
                                                                      "           when test1.c_int = t.c_int then 0 " +
                                                                      "           when test1.c_int is null " +
                                                                      "                AND t.c_int is null then 0 " +
                                                                      "           else 1 end) " +
                                                                      "  from bruce.test1, " + key + " t where test1.id = t.id");
                rs1.next();
                if (rs1.getLong(1) != 0)
                {
                    fail("Row contents are different between test1 and " + key +
                            ". Number of differences:" +
                            rs1.getLong(1));
                }
            }
        }
        c.setAutoCommit(autoCommit);
    }

    private void closeConnection(final Connection c)
    {
        try
        {
            c.close();
        }
        catch (SQLException e)
        {
            logger.warn("Cannot close JDBC Connection.  Message: " + e.getLocalizedMessage());
            e.printStackTrace();
        }
    }


    class ForceSnapshot extends MultithreadedThread
    {
        public ForceSnapshot(Set<MultithreadedError> results,String name)
        {
            super(results,name);
        }

        public void run()
        {
            Connection c = null;
            try
            {
                c = TestDatabaseHelper.getTestDataSource().getConnection();
                c.setAutoCommit(false);
            }
            catch (SQLException e)
            {
                logger.warn("Cannot set autoCommit state on JDBC Connection.  Message: " + e.getLocalizedMessage());
                e.printStackTrace();
            }

            while (!shutdownThread)
            {
                try
                {
                    // Force a snapshot
                    c.setSavepoint();
                    TestDatabaseHelper.executeAndLog(c.createStatement(), "select bruce.logsnapshot()");
                    c.commit();
		    logger.trace("commited forced snapshot");
                    Thread.sleep(1000L); // Sleep for a moment before snapshoting again.
                }
                catch (Throwable t)
                {
                    results.add(new MultithreadedError(this, t));
                }
            }
            closeConnection(c);
        }
    }

    class EmulateSlaveThread extends MultithreadedThread
    {
        public EmulateSlaveThread(Set<MultithreadedError> results, SnapshotCache cache, String name)
        {
            super(results,name);
            this.cache = cache;
            try
            {
                // Set up a slave table mirroring test1
                Connection c = TestDatabaseHelper.getTestDataSource().getConnection();
                boolean autoCommit = c.getAutoCommit();
                c.setAutoCommit(false);
                c.setSavepoint();
                // Because we are not going to update a replicated table, but still need to know
                // the master status in this transaction, we need to snapshot. This snapshot
                // becomes our base slave snapshot. Normaly, this status would be kept in the
                // slavesnapshotstatus table, but for the purposes of this test, we are just
                // going to keep it in memory.
                TestDatabaseHelper.executeAndLog(c.createStatement(), "select bruce.logsnapshot()");
                // Determine our transaction ID from pg_locks
                ResultSet rs = TestDatabaseHelper.executeQueryAndLog(c.createStatement(),
                                                                     "select * from pg_locks " +
                                                                             " where pid = pg_backend_pid() " +
                                                                             "   and locktype = 'transactionid'");
                rs.next();
                // Aquire the snapshot that we just made above
                PreparedStatement p =
                        c.prepareStatement("select * from bruce.snapshotlog " +
                                " where current_xaction = ?");
                p.setLong(1, new Long(rs.getString("transaction")));
                rs = p.executeQuery();
                rs.next();
                s1 = new Snapshot(new TransactionID(rs.getLong("current_xaction")),
                                  new TransactionID(rs.getLong("min_xaction")),
                                  new TransactionID(rs.getLong("max_xaction")),
                                  rs.getString("outstanding_xactions"));
                // Copy the current contents of test1 into our slave table
                slaveTableName = "test_rnd_" + Math.abs((new Random()).nextInt());
                TestDatabaseHelper.executeAndLog(c.createStatement(),
                                                 "create table bruce." + slaveTableName +
                                                         " as select * from bruce.test1");
                TestDatabaseHelper.executeAndLog(c.createStatement(),
                                                 "alter table bruce." + slaveTableName + " add primary key(id)");
                c.commit();
                c.setAutoCommit(autoCommit);
            }
            catch (Throwable t)
            {
                logger.debug(t);
                results.add(new MultithreadedError(this, t));
            }
        }

        public void run()
        {
            lastChangeCount = 0;
            Snapshot s2 = null;
            Connection c = null;
            try
            {
                c = TestDatabaseHelper.getTestDataSource().getConnection();
                c.setAutoCommit(false);
            }
            catch (SQLException e)
            {
                logger.warn("Cannot set autoCommit state on JDBC Connection.  Message: " + e.getLocalizedMessage());
                e.printStackTrace();
            }
            while (!(shutdownThread && lastChangeCount == 0 && s1 != null && s2 != null))
            {
                try
                {
                    s2 = cache.getNextSnapshot(s1);
                    logger.trace("s1:" + s1 + " s2:" + s2);
                    if (s2 != null)
                    {
                        Transaction t = cache.getOutstandingTransactions(s1, s2);
                        try
                        {
                            c.setSavepoint();
                            lastChangeCount = 0;
                            for (Change ch : t)
                            {
                                lastChangeCount++;
                                TestDatabaseHelper.executeAndLog(c.createStatement(),
                                                                 "select bruce.applyLogTransaction('" +
                                                                         ch.getCmdType() + "','" +
                                                                         slaveTableName + "','" +
                                                                         ch.getInfo() + "')");
                            }
                            c.commit();
                        }
                        catch (SQLException e)
                        {
                            logger.warn("SQLException caught in test.  Message: " + e.getLocalizedMessage());
                            e.printStackTrace();
                        }
                        s1 = s2;
                    } else {
			logger.trace("No next snapshot available. Sleeping for a while....");
			Thread.sleep(1000L);
		    }
                }
                catch (Throwable t)
                {
                    logger.debug("s1:" + s1 + " s2:" + s2, t);
                    results.add(new MultithreadedError(this, t));
                    return;
                }
            }
            closeConnection(c);
        }

        private int lastChangeCount;
        private String slaveTableName;
        private Snapshot s1;
        private SnapshotCache cache;
    }

    class DBLongUpdateThread extends MultithreadedThread
    {
        public DBLongUpdateThread(Set<MultithreadedError> results)
        {
            super(results);
        }

        public void run()
        {
            Connection c = null;
            PreparedStatement ps = null;
            try
            {
                c = TestDatabaseHelper.getTestDataSource().getConnection();
                ps = c.prepareStatement("insert into test1 (c_bytea,c_text,c_int) " +
                        "values (?,random(),((random()*2)-1)*pow(2,31))");
            }
            catch (SQLException e)
            {
                logger.error("Cannot create prepared statement.  Message: " + e.getLocalizedMessage());
                e.printStackTrace();
            }
            while (!shutdownThread)
            {
                try
                {
                    Random r = new Random();
                    byte[] bytea = new byte[(int) (r.nextFloat() * 1 * 1014 * 1024)]; // zero to 1M byte array
                    r.nextBytes(bytea); // Randomly populated
                    ps.setBytes(1, bytea);
                    ps.execute();
                }
                catch (Throwable t)
                {
                    results.add(new MultithreadedError(this, t));
                }
            }
            closeConnection(c);
        }

    }

    class DBShortUpdateThread extends MultithreadedThread
    {
        public DBShortUpdateThread(Set<MultithreadedError> results)
        {
            super(results);
        }

        public void run()
        {
            Connection c = null;
            PreparedStatement ps = null;
            try
            {
                c = TestDatabaseHelper.getTestDataSource().getConnection();
                ps = c.prepareStatement("insert into test1 (c_int) " +
                        "values (((random()*2)-1)*pow(2,31))");
            }
            catch (SQLException e)
            {
                logger.error("Cannot create prepared statement.  Message: " + e.getLocalizedMessage());
                e.printStackTrace();
            }
            while (!shutdownThread)
            {
                try
                {
                    ps.execute();
                }
                catch (Throwable t)
                {
                    results.add(new MultithreadedError(this, t));
                }
            }
            closeConnection(c);
        }
    }

    private Snapshot getLastSnapshot() throws SQLException
    {
        Connection c = TestDatabaseHelper.getTestDataSource().getConnection();
        boolean autoCommit = true;
        autoCommit = c.getAutoCommit();
        c.setAutoCommit(false);
        ResultSet rs = TestDatabaseHelper.executeQueryAndLog(c.createStatement(),
                                                             "select * from bruce.snapshotlog " +
                                                                     " where current_xaction = (select max(current_xaction) " +
                                                                     "  from bruce.snapshotlog)");
        rs.next();
        final Snapshot snapshot = new Snapshot(new TransactionID(rs.getLong("current_xaction")),
                                               new TransactionID(rs.getLong("min_xaction")),
                                               new TransactionID(rs.getLong("max_xaction")),
                                               rs.getString("outstanding_xactions"));
        rs.close();
        c.commit();
        c.setAutoCommit(autoCommit);
        return snapshot;
    }

    private static final Logger logger = Logger.getLogger(SnapshotCacheTest.class);
    private static final File SCHEMA_UNIT_TESTS_DDL_SQL = TestDatabaseHelper.getSchemaFile("unit-tests-ddl.sql");
    private SnapshotCache cache;

}
