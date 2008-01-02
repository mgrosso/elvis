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

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.SQLException;

/**
 * @author rklahn
 * @version $Id$
 */
public class MortalUserCanModifyTableTest extends ReplicationTest {
    @BeforeClass public static void setupTestClass() throws SQLException {
	// Root Logger is off, light up our messages
	logger = Logger.getLogger(MortalUserCanModifyTableTest.class);
	logger.setLevel(Level.INFO);
	// Get a DB connection as user:bruce
	connectionB = TestDatabaseHelper.getTestDatabaseConnection();
	// Create a mortal user
	Statement s = connectionB.createStatement();
	try {
	    s.execute("drop schema mortal cascade");
	} catch (SQLException e) { logger.debug(e); } // Error OK, probably schema did not already exist
	try {
	    s.execute("drop user mortal");
	} catch (SQLException e) { logger.debug(e); } // Error OK, probably user did not already exist
	s.execute("create user mortal");
	s.execute("alter user mortal with LOGIN PASSWORD 'mortal'");
	// Create a schema owned by the mortal user
	s.execute("create schema AUTHORIZATION mortal");
	// Build a URL to the database as the mortal user
	String urlM = 
	    (new StringBuilder().
	     append(TestDatabaseHelper.getBasePostgresUrl()).
	     append(System.getProperty("postgresql.db_name")).
	     append("?user=mortal&password=mortal")
	     ).toString();
	connectionM = DriverManager.getConnection(urlM);
	// Create a table to be replicated
	Statement sM = connectionM.createStatement();
	sM.execute("create table test (id bigserial,t text)");
	// and as the bruce superuser, add it to replication
	s.execute("create trigger test_sn "+
		  "BEFORE INSERT OR DELETE OR UPDATE "+
		  "ON mortal.test "+
		  "FOR EACH STATEMENT EXECUTE PROCEDURE bruce.logsnapshottrigger()");
	s.execute("create trigger test_tx "+
		  "AFTER INSERT OR DELETE OR UPDATE "+
		  "ON mortal.test "+
		  "FOR EACH ROW EXECUTE PROCEDURE bruce.logtransactiontrigger()");
	s.close();
	sM.close();
    }

    // Clean up our droppings
    @AfterClass public static void teardownTestClass() throws SQLException {
	connectionB = TestDatabaseHelper.getTestDatabaseConnection();
	// Create a mortal user
	Statement s = connectionB.createStatement();
	try {
	    s.execute("drop schema mortal cascade");
	} catch (SQLException e) { logger.debug(e); } // Error OK, probably schema did not already exist
	try {
	    s.execute("drop user mortal");
	} catch (SQLException e) { logger.debug(e); } // Error OK, probably user did not already exist
    }

    @Before public void beforeTest() {
	super.setUp();
    }

    @Test public void testMortalModify() throws SQLException {
	Statement s = connectionM.createStatement();
	s.execute("insert into test (t) values ('greetings')");
	s.execute("update test set t = 'salutations'");
	s.execute("delete from test");
    }

    private static Logger logger;
    private static Connection connectionB; // Connection to test database as user: bruce
    private static Connection connectionM; // Connection to test database as user: mortal
}
