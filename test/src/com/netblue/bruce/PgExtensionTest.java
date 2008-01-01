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
import org.apache.log4j.Logger;
import org.junit.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.sql.*;
import java.text.MessageFormat;
import java.util.SortedSet;
// -----------------------------------------------
// ${CLASS}
// -----------------------------------------------

/**
 * @author rklahn
 */
public class PgExtensionTest extends ReplicationTest
{


    private final static Logger logger = Logger.getLogger(PgExtensionTest.class.getName());
    private final static int clusterID = 1;

    // To run with Ant (version 1.6.5???), we need this suite() method to run our tests.
    // Ant uses an JUnit 3.x runner instead of a 4.X one. See http://junit.sourceforge.net/doc/faq/faq.htm#tests_1
    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(PgExtensionTest.class);
    }


    /**
     * Initializes the database.  If you need to create tables, indices, etc at runtime, you can use this method to do
     * that.  If not, just make it a no-op.
     *
     * @param connection A JDBC connection with auto commit turned on
     */
    protected void setUpDatabase(Connection connection)
    {
        try
        {
            super.setUpDatabase(connection);
            TestDatabaseHelper.applyDDLFromFile(connection, SCHEMA_UNIT_TESTS_DDL_SQL);
            initSlave(connection);
            Statement statement = connection.createStatement();
            TestDatabaseHelper.executeAndLog(statement,"delete from test1");
            applyLoggedTransactions();
            statement.close();
        }
        catch (SQLException e)
        {
            logger.error("Cannot initialize slaves", e);
        }
    }

    @BeforeClass
    public static void initializeDatabaseObjects()
    {
        try
        {
            connection = TestDatabaseHelper.getTestDatabaseConnection();
            statement = connection.createStatement();
            connection.setAutoCommit(true);
        }
        catch (SQLException e)
        {
            Assert.fail("Cannot acquire test database connection: " + e.getLocalizedMessage());
        }
    }

    @AfterClass
    public static void releaseConnection()
    {
        try
        {
            statement.close();
            connection.close();
        }
        catch (SQLException e)
        {
            logger.warn("Cannot close JDBC Connection", e);
        }
    }

    @Before public void setUp()
    {
        super.setUp();
        logger.debug("--------------Begin Test----------------");
    }

    @After public void afterTest()
    {
        logger.debug("---------------End Test-----------------");
    }

    @Test public void denyAccessTrigger() throws SQLException
    {
        logger.info("Testing the denyaccesstrigger()");
        try { // we are expecting to throw SQLException on this one. Cant update a replicated table.....
            logger.info("Inserting into a table (test2) that has the trigger. We expect to get an error.");
            TestDatabaseHelper.executeAndLog(statement,"insert into test2(id) values (42)");
            logger.fatal("Something has gone very, very wrong. We were able to insert into the table.");
            Assert.fail();
        } catch(SQLException e) {
            logger.info(e);
            logger.trace(null,e);
            Assert.assertTrue(true);
        }
        logger.info("Now testing daemonmode(), so we can update test2.");
        logger.info("Calling daemonmode()");
        TestDatabaseHelper.executeAndLog(statement,"select daemonmode()");
        logger.info("DONE calling daemonmode()");
        logger.info("INSERTING a row into test2");
        TestDatabaseHelper.executeAndLog(statement,"insert into test2(id) values (42)");
        logger.info("DONE");
        logger.info("Now DELETEing all rows");
        TestDatabaseHelper.executeAndLog(statement,"delete from test2");
        logger.info("DONE");
        logger.info("Going back to normalmode()");
        TestDatabaseHelper.executeAndLog(statement,"select normalmode()");
        logger.info("DONE");
        try { // we are expecting to throw SQLException on this one. Cant update a replicated table.....
            logger.info("Inserting again. We expect to get an error, because we are in normalmode() again");
            TestDatabaseHelper.executeAndLog(statement,"insert into test2(id) values (42)");
            logger.fatal("Something has gone very, very wrong. We were able to insert into the table.");
            Assert.fail();
        } catch(SQLException e) {
            logger.info(e);
            logger.trace(null,e);
            Assert.assertTrue(true);
        }
        Assert.assertTrue(true);
    }

    @Test public void insertTransaction() throws SQLException
    {
        logger.info("testing INSERTs");
        logger.info("Inserting rows into test1");
        insertBaseRows();
        logger.info("Replicating inserts to test2");
        applyLoggedTransactions();
        logger.info("DONE replicating");
        testTestTablesEqual();
        logger.info("INSERTs successfuly replicated");
    }

    @Test public void updateTransaction() throws SQLException
    {
        logger.info("testing UPDATEs");
        // Give us some data to test with
        insertBaseRows();
        logger.info("updating rows in test1");
        TestDatabaseHelper.executeAndLog(statement,"update test1 set c_text = c_int");
        TestDatabaseHelper.executeAndLog(statement,"update test1 set c_bytea = decode(c_text,'escape')");
        logger.info("Replicating updates to test2");
        applyLoggedTransactions();
        logger.info("DONE replicating");
        testTestTablesEqual();
        logger.info("UPDATEs successfuly replicated");
    }

    @Test public void deleteTransaction() throws SQLException
    {
        logger.info("testing DELETEs");
        // Give us some data to test with
        insertBaseRows();
        logger.info("deleting rows from test1");
        TestDatabaseHelper.executeAndLog(statement,"delete from test1");
        logger.info("Replicating deletes to test2");
        applyLoggedTransactions();
        logger.info("DONE replicating");
        testTestTablesEqual();
        logger.info("DELETEs successfuly replicated");
    }

    @Test public void snapshot() throws SQLException
    {
        logger.info("testing logsnapshot()");
        Connection c1 = TestDatabaseHelper.getTestDataSource().getConnection();
        // Start a transaction under connection 1
        logger.info("Start, and keep open, a transaction.");
        c1.setAutoCommit(false);
        c1.setSavepoint();
        // Whats our transaction number? At a minimum, we are going to have a transactionid ExclusiveLock
        ResultSet rs1 = TestDatabaseHelper.executeQueryAndLog(c1.createStatement(),
                                           "select * from pg_locks where pid = pg_backend_pid()"+
                                                   " and locktype = 'transactionid'");
        rs1.next();
        TransactionID tid = new TransactionID(rs1.getString("transaction"));
        logger.info("open TransactionID is:"+tid);
        // Create a snapshot under a second connection
        logger.info("In a second connection, do a logsnapshot().");
        Connection c2 = TestDatabaseHelper.getTestDataSource().getConnection();
        c2.setAutoCommit(true);
        TestDatabaseHelper.executeAndLog(c2.createStatement(),"select logsnapshot()");
        logger.info("This snapshot should include the open transaction on its outstanding_xaction list");
        // This snapshot should include tid1 on its outstanding_xactions list
        Snapshot ss1 = getLastSnapshot(c1);
        logger.info(ss1);
        // Give up the transaction under c1
        c1.rollback();
        c1.close();
        logger.info("Now we close the open transaction, and logsnapshot() again");
        // Snapshot again. This time, tid1 should NOT be on its outstanding_xactions list
        TestDatabaseHelper.executeAndLog(c2.createStatement(),"select logsnapshot()");
        Snapshot ss2 = getLastSnapshot(c2);
        logger.info("This time, the ex-open transaction should not be on the outstanding_xaction list");
        logger.info(ss2);
//        // All that being said, tid should be GE snapshot1, and LT(or equal) snapshot2
        Assert.assertTrue(ss1.transactionIDGE(tid));
        Assert.assertTrue(ss2.transactionIDLT(tid));
        logger.info("logsnapshot() test successful");
        c2.close();
    }

    private Snapshot getLastSnapshot(final Connection connection) throws SQLException
    {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(true);
        final Statement statement = connection.createStatement();
        ResultSet rs = TestDatabaseHelper.executeQueryAndLog(statement,
                                          "select * from snapshotlog where current_xaction = (select max(current_xaction) from snapshotlog)");
        rs.next();
        final Snapshot snapshot = (new Snapshot(new TransactionID(rs.getLong("current_xaction")),
                                                new TransactionID(rs.getLong("min_xaction")),
                                                new TransactionID(rs.getLong("max_xaction")),
                                                rs.getString("outstanding_xactions")));
        rs.close();
        statement.close();
        connection.setAutoCommit(autoCommit);
        return snapshot;
    }

    private void testTestTablesEqual() throws SQLException
    {
        logger.info("Testing if tables test1 and test2 are the same");
        final Statement statement1 = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                                                       ResultSet.CONCUR_READ_ONLY);
        ResultSet rs1 = TestDatabaseHelper.executeQueryAndLog(statement1,
                                           "select * from test1 order by id");
        final Statement statement2 = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                                                       ResultSet.CONCUR_READ_ONLY);
        ResultSet rs2 = TestDatabaseHelper.executeQueryAndLog(statement2,
                                           "select * from test2 order by id");
        // Obtain the row counts, which must be equal
        rs1.last();
        rs2.last();
        int test1RowCount = rs1.getRow();
        int test2RowCount = rs2.getRow();
        // Only compare row contents if row count is equal
        if (test1RowCount == test2RowCount) {
            logger.info("row counts are the same ("+test1RowCount+"=="+test2RowCount+"). Now checking contents.");
            logger.debug("looping through the rows, verifying contents are the same");
            rs1.beforeFirst();
            rs2.beforeFirst();
            while (rs1.next() && rs2.next()) {
                TestRow r1 = new TestRow(rs1);
                TestRow r2 = new TestRow(rs2);
                logger.trace("test1 row:"+r1);
                logger.trace("test2 row:"+r2);
                logger.trace(r1.equals(r2));
                Assert.assertEquals(r1,r2);
            }
            logger.info("contents are the same");
        }
        else
        {
            logger.fatal("row counts of test1 ("+test1RowCount+") and test2 ("+test2RowCount+") are not the same");
            // if row count not equal
            Assert.fail();
        }
        rs1.close();
        rs2.close();
        statement1.close();
        statement2.close();
    }

    private void applyLoggedTransactions() throws SQLException
    {
        logger.info("Applying logged transactions");
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        Statement s = connection.createStatement();
        connection.setSavepoint();
        // Get last applied slave snapshot
        ResultSet rs = TestDatabaseHelper.executeQueryAndLog(s,"select * from slavesnapshotstatus where clusterid = "+clusterID);
        rs.next();
        Snapshot slaveS = new Snapshot(new TransactionID(rs.getLong("master_current_xaction")),
				       new TransactionID(rs.getLong("master_min_xaction")),
                                       new TransactionID(rs.getLong("master_max_xaction")),
                                       rs.getString("master_outstanding_xactions"));
        logger.debug("slaveS:"+slaveS);
        // Get last logged master snapshot
        rs = TestDatabaseHelper.executeQueryAndLog(s,"select * from snapshotlog where current_xaction = (select max(current_xaction) from snapshotlog)");
        rs.next();
        Snapshot masterS = new Snapshot(new TransactionID(rs.getLong("current_xaction")),
					new TransactionID(rs.getLong("min_xaction")),
                                        new TransactionID(rs.getLong("max_xaction")),
                                        rs.getString("outstanding_xactions"));
        logger.debug("masterS:"+masterS);
        // Get the list of transactions completed between the snapshots
        SortedSet<TransactionID> xactions = slaveS.tIDsBetweenSnapshots(masterS);
        logger.debug("TransactionList:"+xactions);
        // Gather changes that occured during those transactions
        Transaction t = new Transaction();
        for (TransactionID tid:xactions)
        {
            Transaction lt = new Transaction();
            rs = TestDatabaseHelper.executeQueryAndLog(s,"select * from transactionlog where xaction = "+tid.toString());
            while (rs.next())
            {
                Change ch = new Change(rs.getLong("rowid"),tid,rs.getString("cmdtype"),
                                      "bruce.test2", // On a real slave, this will be rs.getString("tabname")
                                      rs.getString("info"));
                logger.trace("Change: "+ch);
                lt.add(ch);
            }
            t.addAll(lt);
        }
        // Apply changes
        TestDatabaseHelper.executeAndLog(s,"select daemonmode()");
        logger.debug("Changes to apply"+t);
        for (Change ch:t)
        {
            logger.trace("Applying change: "+ch);
            TestDatabaseHelper.executeAndLog(s,"select applyLogTransaction('"+ch.getCmdType()+"','"+ch.getTableName()+"','"+ch.getInfo()+"')");
        }
        TestDatabaseHelper.executeAndLog(s,"select normalmode()");
        // Update slave replication status
        //
        // First, determine our transactionID
        rs = TestDatabaseHelper.executeQueryAndLog(s,"select * from pg_locks where pid = pg_backend_pid()"+
                " and locktype = 'transactionid'");
        rs.next();
        TransactionID stid = new TransactionID(rs.getString("transaction"));
        logger.debug("Slave transactionID: "+stid);
        // Then we can update the slave status
        String stmt = "update slavesnapshotstatus "+
                "set slave_xaction = ?, master_current_xaction = ?, master_min_xaction = ?, master_max_xaction = ?, "+
                "    master_outstanding_xactions = ?, update_time = now() "+
                "where clusterid = "+clusterID;
        PreparedStatement p = connection.prepareStatement(stmt);
        p.setLong(1,new Long(stid.toString()));
        p.setLong(2,new Long(masterS.getCurrentXid().toString()));
        p.setLong(3,new Long(masterS.getMinXid().toString()));
        p.setLong(4,new Long(masterS.getMaxXid().toString()));
        p.setString(5,masterS.getInFlight());
        logger.debug(stmt+",{"+stid+","+masterS.getCurrentXid()+","+masterS.getMinXid()+","+masterS.getMaxXid()+","+masterS.getInFlight()+"}");
        p.execute();
        connection.commit();
        connection.setAutoCommit(autoCommit);
        s.close();
        p.close();
        rs.close();
        logger.info("Transactions applied");
    }

    private void insertBaseRows() throws SQLException
    {
        int rowsToTest = 10;
        logger.info("Inserting "+rowsToTest+" rows into test1.");
        for (int i=0;i<rowsToTest;i++) {
            TestDatabaseHelper.executeAndLog(statement,MessageFormat.format("insert into test1(c_int) values ({0,number,#})",
                                           Math.ceil((Math.random() - 0.5) * 100000)));
        }
    }

    private static void initSlave(Connection c) throws SQLException
    {
        boolean autoCommit = c.getAutoCommit();
        c.setAutoCommit(false);
        c.setSavepoint();
        Statement s = c.createStatement();
        TestDatabaseHelper.executeAndLog(s,"select logsnapshot()");
        ResultSet rs = TestDatabaseHelper.executeQueryAndLog(s,"select * from snapshotlog where current_xaction = (select max(current_xaction) from snapshotlog)");
        rs.next();
        TestDatabaseHelper.executeAndLog(s,"insert into slavesnapshotstatus "+
                "(clusterid,slave_xaction,master_current_xaction,master_min_xaction,master_max_xaction,master_outstanding_xactions) "+
                "values ("+clusterID+","+rs.getLong("current_xaction")+","+rs.getLong("max_xaction")+","+
		      rs.getLong("min_xaction")+","+
                rs.getLong("max_xaction")+",'"+rs.getString("outstanding_xactions")+"')");
        c.commit();
        c.setAutoCommit(autoCommit);
        s.close();
        rs.close();
    }

    class TestRow
    {
        private Long id;
        private String t;
        private Long i;
        private byte[] b;

        public TestRow (ResultSet rs) throws SQLException
        {
            this.id=rs.getLong("id");
            this.t=rs.getString("c_text");
            this.i=rs.getLong("c_int");
            this.b=rs.getBytes("c_bytea");
        }

        public boolean equals(Object o) {
            TestRow otherRow;
            try
            {
                otherRow = (TestRow) o;
            }
            catch (Exception e) {
                return false;
            }

            try
            {
                if (!this.id.equals(otherRow.id)) return false;
            }
            catch (NullPointerException e)
            {
                // If one is null, the other one better be too.
                if ((this.id!=null) || (otherRow.id!=null)) return false;
            }

            try
            {
                if (!this.t.equals(otherRow.t)) return false;
            }
            catch (NullPointerException e)
            {
                if ((this.t!=null) || (otherRow.t!=null)) return false;
            }

            try
            {
                if (!this.i.equals(otherRow.i)) return false;
            }
            catch (NullPointerException e)
            {
                if ((this.i!=null) || (otherRow.i!=null)) return false;
            }

            try
            {
                if (!ByteBuffer.wrap(this.b).equals(ByteBuffer.wrap(otherRow.b))) return false;
            }
            catch (NullPointerException e)
            {
                if ((this.b!=null) || (otherRow.b!=null)) return false;
            }
            return true;
        }

        public String toString()
        {
            String retVal = "{id:"+id+" t:"+t+" i:"+i+" b:";
            if (b==null) {
                retVal+="null";
            } else {
                for (byte be:b)
                {
                    retVal+=String.format(" 0x%X",be);
                }
            }
            retVal+="}";
            return retVal;
        }
    }

    private static final File SCHEMA_UNIT_TESTS_DDL_SQL = TestDatabaseHelper.getSchemaFile("unit-tests-ddl.sql");
    private static Connection connection;
    private static Statement statement;
}
