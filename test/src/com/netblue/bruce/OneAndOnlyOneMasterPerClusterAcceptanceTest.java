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
import org.apache.log4j.BasicConfigurator;
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

public class OneAndOnlyOneMasterPerClusterAcceptanceTest extends ReplicationTest
{
    // To run with Ant (version 1.6.5???), we need this suite() method to run our tests.
    // Ant uses an JUnit 3.x runner instead of a 4.X one. 
    // See http://junit.sourceforge.net/doc/faq/faq.htm#tests_1
    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(OneAndOnlyOneMasterPerClusterAcceptanceTest.class);
    }

    @Before
    public void ourSetUp() throws SQLException
    {
        super.setUp();
        logger.setLevel(Level.DEBUG);
        // Clear contents of cluster configuration
        Connection c = TestDatabaseHelper.getTestDatabaseConnection();
        c.setAutoCommit(false);
        c.setSavepoint();
        TestDatabaseHelper.executeAndLog(c.createStatement(), "delete from bruce.node_cluster");
        TestDatabaseHelper.executeAndLog(c.createStatement(), "delete from bruce.yf_cluster");
        TestDatabaseHelper.executeAndLog(c.createStatement(), "delete from bruce.yf_node");
        c.commit();
        logger.info("--------------Begin Test----------------");
    }

    @After
    public void afterTest()
    {
        logger.info("---------------End Test-----------------");
    }

    @Test
    public void testOneMaster() throws IOException, InterruptedException
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
        BasicConfigurator.resetConfiguration();
        com.netblue.bruce.admin.Main.main(new String[]{"-data",
                                                       "test/data/admin-test-setup.xml",
                                                       "-operation",
                                                       "INSERT",
                                                       "-url",
                                                       System.getProperty("postgresql.URL")});

        BasicConfigurator.resetConfiguration();
        com.netblue.bruce.admin.Main.main(new String[]{"-data",
                                                       "test/data/" +
                                                               "one-and-only-one-master-add.xml",
                                                       "-operation",
                                                       "INSERT",
                                                       "-url",
                                                       System.getProperty("postgresql.URL")});
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

        Assert.assertTrue("Unexpected output from admin tool:  " + stdout, stdout.contains("org.postgresql.util.PSQLException: " +
                "ERROR: duplicate key violates unique constraint " +
                "\"yf_cluster_master_node_id_key\""));
    }

    private final static Logger logger =
            Logger.getLogger(OneAndOnlyOneMasterPerClusterAcceptanceTest.class);
}
