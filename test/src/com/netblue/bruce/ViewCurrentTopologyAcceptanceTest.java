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
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * @author rklahn
 */
public class ViewCurrentTopologyAcceptanceTest extends ReplicationTest
{
    // To run with Ant (version 1.6.5???), we need this suite() method to run our tests.
    // Ant uses an JUnit 3.x runner instead of a 4.X one. See http://junit.sourceforge.net/doc/faq/faq.htm#tests_1
    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(ViewCurrentTopologyAcceptanceTest.class);
    }

    @Before
    public void beforeTest() throws SQLException
    {
        super.setUp();
        // Root logger of off (see ReplicationTest.java), light up our messages
        logger = Logger.getLogger(ViewCurrentTopologyAcceptanceTest.class);
        logger.setLevel(Level.DEBUG);
        //	logger.setLevel(Level.INFO);
        // Clear contents of cluster config
        Connection c = TestDatabaseHelper.getTestDatabaseConnection();
        boolean autoCommit = c.getAutoCommit();
        c.setAutoCommit(false);
        c.setSavepoint();
        TestDatabaseHelper.executeAndLog(c.createStatement(), "delete from node_cluster");
        TestDatabaseHelper.executeAndLog(c.createStatement(), "delete from yf_cluster");
        TestDatabaseHelper.executeAndLog(c.createStatement(), "delete from yf_node");
        // Set up a (pseudo) cluster
        TestDatabaseHelper.executeAndLog(c.createStatement(),
                                         "insert into bruce.yf_node (id,available,includetable,name,uri) " +
                                                 "values (1,true,'public\\.test1','master','" + System.getProperty("postgresql.URL") + "')");
        TestDatabaseHelper.executeAndLog(c.createStatement(),
                                         "insert into bruce.yf_node (id,available,includetable,name,uri) " +
                                                 "values (2,true,'public\\.test1','slave 1','" + System.getProperty("postgresql.URL") + "')");
        TestDatabaseHelper.executeAndLog(c.createStatement(),
                                         "insert into bruce.yf_node (id,available,includetable,name,uri) " +
                                                 "values (3,true,'public\\.test1','slave 2','" + System.getProperty("postgresql.URL") + "')");
        TestDatabaseHelper.executeAndLog(c.createStatement(),
                                         "insert into bruce.yf_cluster (id,name,master_node_id) values (1,'cluster',1)");
        TestDatabaseHelper.executeAndLog(c.createStatement(),
                                         "insert into bruce.node_cluster (node_id,cluster_id) values (2,1)");
        TestDatabaseHelper.executeAndLog(c.createStatement(),
                                         "insert into bruce.node_cluster (node_id,cluster_id) values (3,1)");
        c.commit();
        c.setAutoCommit(autoCommit);
        logger.info("--------------Begin Test----------------");
    }

    @After
    public void afterTest()
    {
        logger.info("---------------End Test-----------------");
    }

    @Test
    public void testCurrentTopology() throws IOException, InterruptedException
    {

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream spawnOut = new PrintStream(outBytes);
        PrintStream spawnErr = new PrintStream(errBytes);

        PrintStream beforeOut = System.out;
        PrintStream beforeErr = System.err;

        // Set the System streams with our own
        System.setOut(spawnOut);
        System.setErr(spawnErr);

        // capture the current log4j configuration. The below call to main() is going to reconfigure log4j
        Properties log4jP = Log4jCapture.getLog4jConfig();

        // Run the main here
        com.netblue.bruce.admin.Main.main(new String[]{"-list", "-url", System.getProperty("postgresql.URL")});

        // Restore original out and err
        System.setOut(beforeOut);
        System.setErr(beforeErr);

        // restore the original log4j configuration
        Log4jCapture.setLog4jConfig(log4jP);

        String stdout = outBytes.toString();
        String stderr = errBytes.toString();

        if (stderr.length() > 0)
        {
            Assert.fail("admin tool contains stderr output:" + stderr);
        }
        Assert.assertTrue(stdout.contains("Metadata for cluster [cluster]"));
        Assert.assertTrue(stdout.contains("Master"));
        Assert.assertTrue(stdout.contains("Slaves"));
        Assert.assertTrue(stdout.contains("Name:\tmaster\n" +
                "\tURL: " + System.getProperty("postgresql.URL") + "\n" +
                "\tInclude table: public.test1\n"));
        Assert.assertTrue(stdout.contains("Name:\tslave 1\n" +
                "\tURL: " + System.getProperty("postgresql.URL") + "\n" +
                "\tInclude table: public.test1\n"));
        Assert.assertTrue(stdout.contains("Name:\tslave 2\n" +
                "\tURL: " + System.getProperty("postgresql.URL") + "\n" +
                "\tInclude table: public.test1\n"));
    }

    private Logger logger;
}
