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

/* This class is a light port of pgbench, distributed in the contrib directory
 * of the postgresql source.
 *
 * Therefore, portions....
 *
 * Copyright (c) 2000-2005      Tatsuo Ishii
 *
 * Permission to use, copy, modify, and distribute this software and
 * its documentation for any purpose and without fee is hereby
 * granted, provided that the above copyright notice appear in all
 * copies and that both that copyright notice and this permission
 * notice appear in supporting documentation, and that the name of the
 * author not be used in advertising or publicity pertaining to
 * distribution of the software without specific, written prior
 * permission. The author makes no representations about the
 * suitability of this software for any purpose.  It is provided "as
 * is" without express or implied warranty.
 */

package com.netblue.bruce;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.log4j.*;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.Map.Entry;

public class PgJDBCBench {

    public void run(CmdLineParser parser) {
	logger.debug("PgJDBCBench.run():");
	logger.debug("uri:"+uri);
	logger.debug("helpme:"+helpme);
	logger.debug("poolSize:"+poolSize);
	logger.debug("initMode:"+initMode);
	logger.debug("numThreads:"+numThreads);
	logger.debug("numTransactions:"+numTransactions);
	logger.debug("numScale:"+numScale);
	if (helpme) {
	    parser.printUsage(System.err);
	} else {
	    // Set up the database connection pool
	    ds = new BasicDataSource();
	    ds.setUrl(uri);
	    ds.setDriverClassName(System.getProperty("jdbcDriverName","org.postgresql.Driver"));
	    ds.setValidationQuery(System.getProperty("poolQuery","select now()"));
	    ds.setMaxActive(poolSize);
	    if (initMode) {
		initMode();
	    } else {
		purgeData();
		initData();
		java.util.Date startDate = new java.util.Date();
		logger.info("starting test at:"+startDate);
		HashSet<Thread> tpsThreads = new HashSet<Thread>();
		// Set up testing threads
		for (int i=1;i<=numThreads;i++) { tpsThreads.add(new tpsThread()); }
		// Start up testing threads
		for (Thread t:tpsThreads) { t.start(); }
		// Wait for each thread to complete
		for (Thread t:tpsThreads) { 
		    try {
			t.join(); 
		    } catch (InterruptedException e) {
			logger.error("Unexpected InterruptedException",e);
		    }
		}
		java.util.Date endDate = new java.util.Date();
		logger.info("ending test at:"+endDate);
		results(startDate,endDate);
	    }
	    try {
		ds.close();
	    } catch (SQLException e) {
		logger.error(null,e);
	    }
	}
    }

    private void results(java.util.Date start, java.util.Date end) {
	logger.info("number of threads:"+numThreads);
	logger.info("pool size:"+poolSize);
	logger.info("number of transactions per thread:"+numTransactions);
	long totTransactions = numThreads*numTransactions;
	logger.info("total number of transactions:"+totTransactions);
	double runSeconds = (end.getTime()-start.getTime())/1000.0;
	logger.info("Run time (seconds):"+runSeconds);
	double tps = totTransactions/runSeconds;
	logger.info("TPS:"+tps);
    }

    private void initData() {
	int nBranches = baseBranches*numThreads*numScale;
	int nTellers = baseTellers*numThreads*numScale;
	int nAccounts = baseAccounts*numThreads*numScale;
	logger.debug("PgJDBCBench.initData()");
	logger.debug("nBranches:"+nBranches);
	logger.debug("nTellers:"+nTellers);
	logger.debug("nAccounts:"+nAccounts);
	try {
	    Connection c = ds.getConnection();
	    try {
		c.setAutoCommit(false);
		// branches
		PreparedStatement ps = 
		    c.prepareStatement("insert into _test.branches(bid,bbalance) values(?,0)");
		c.setSavepoint();
		logger.info("preparing branches table");
		for (int i=1;i<=nBranches;i++) {
		    ps.setInt(1,i);
		    ps.execute();
		    if (i%1000==0) {
			c.commit();
			c.setSavepoint();
		    }
		}
		c.commit();
		// Tellers
		ps = c.prepareStatement("insert into _test.tellers(tid,bid,tbalance) "+
					"values (?,?,0)");
		
		c.setSavepoint();
		logger.info("preparing tellers table");
		for (int i=1;i<=nTellers;i++) {
		    ps.setInt(1,i);
		    ps.setInt(2,(i%nBranches)+1);
		    ps.execute();
		    if (i%1000==0) {
			c.commit();
			c.setSavepoint();
		    }
		}
		c.commit();
		// Accounts
		ps = c.prepareStatement("insert into _test.accounts(aid,bid,abalance) "+
					"values (?,?,0)");
		logger.info("preparing accounts table");
		c.setSavepoint();
		for (int i=1;i<=nAccounts;i++) {
		    ps.setInt(1,i);
		    ps.setInt(2,(i%nBranches)+1);
		    ps.execute();
		    if (i%1000==0) {
			c.commit();
			c.setSavepoint();
		    }
		}
		c.commit();
	    } finally {
		c.close();
	    }
	} catch (SQLException e) {
	    logger.error(null,e);
	}
    }

    private void purgeData() {
	try {
	    Connection c = ds.getConnection();
	    try {
		c.setAutoCommit(true);
		Statement s = c.createStatement();
		// accounts
		logger.info("purging accounts table");
		while (true) {
		    s.execute("delete from _test.accounts "+
			      " where aid in (select aid from _test.accounts limit 1000)");
		    int updateC = s.getUpdateCount();
		    if (updateC==0) break;
		}
		// branches
		logger.info("purging branches table");
		while (true) {
		    s.execute("delete from _test.branches "+
			      " where bid in (select bid from _test.branches limit 1000)");
		    int updateC = s.getUpdateCount();
		    if (updateC==0) break;
		}
		// history
		logger.info("purging history table");
		while (true) {
		    s.execute("delete from _test.history "+
			      " where mtime in (select mtime from _test.history limit 1000)");
		    int updateC = s.getUpdateCount();
		    if (updateC==0) break;
		}
		// tellers
		logger.info("purging tellers table");
		while (true) {
		    s.execute("delete from _test.tellers "+
			      " where tid in (select tid from _test.tellers limit 1000)");
		    int updateC = s.getUpdateCount();
		    if (updateC==0) break;
		}
		logger.info("VACUUMing all test tables");
		s.execute("VACUUM FULL ANALYZE _test.accounts");
		s.execute("VACUUM FULL ANALYZE _test.branches");
		s.execute("VACUUM FULL ANALYZE _test.history");
		s.execute("VACUUM FULL ANALYZE _test.tellers");
	    } finally {
		c.close();
	    }
	} catch (SQLException e) {
	    logger.error(null,e);
	}
    }

    private void initMode() {
	try {
	    Connection c = ds.getConnection();
	    try {
		c.setAutoCommit(true);
		Statement s = c.createStatement();
		try { // We dont care if the schema already does not exist
		    s.execute("drop schema _test cascade");
		} catch (SQLException e) {}
		s.execute("create schema _test");
		s.execute("create table _test.branches"+
			  "(bid int primary key,bbalance bigint)");
		s.execute("create table _test.tellers"+
			  "(tid int primary key,bid int,tbalance bigint)");
		s.execute("create table _test.accounts"+
			  "(aid int primary key,bid int,abalance bigint)");
		s.execute("create table _test.history"+
			  "(tid int,bid int,aid int,delta bigint,mtime timestamp)");
		s.execute("create index history_mtime_idx on _test.history(mtime)");
	    } finally {
		c.close();
	    }
	} catch (SQLException e) {
	    logger.error(null,e);
	}
    }

    public static void main(String[] args) {
	PgJDBCBench bench = new PgJDBCBench();
	CmdLineParser parser = new CmdLineParser(bench);
	logger.setLevel(Level.INFO);
	try {
	    parser.parseArgument(args);
	    bench.run(parser);
	} catch (CmdLineException e) {
	    logger.error(null,e);
	    parser.printUsage(System.err);
	}
    }

    private class tpsThread extends Thread {
	public tpsThread() { super(); }

	public void run() {
	    try {
		for (int i=0;i<numTransactions;i++) {
		    int account = r.nextInt(nAccounts-1)+1;
		    int delta = r.nextInt();
		    int branch = 0;
		    int teller = 0;
		    Connection c = ds.getConnection();
		    try {
			c.setAutoCommit(false);
			Statement s = c.createStatement();
			c.setSavepoint();
			// Query the account record to get the branch id
			PreparedStatement ps = 
			    c.prepareStatement("select * from _test.accounts where aid = ?");
			ps.setInt(1,account);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
			    branch = rs.getInt("bid");
			}
			// Randomly select a teller from the branch
			ps = c.prepareStatement("select * from _test.tellers where bid = ? "+
						"order by random() limit 1");
			ps.setInt(1,branch);
			rs=ps.executeQuery();
			if (rs.next()) {
			    teller = rs.getInt("tid");
			}
			ps = c.prepareStatement("update _test.accounts "+
						"   set abalance = abalance + ? "+
						" where aid = ?");
			ps.setInt(1,delta);
			ps.setInt(2,account);
			ps.execute();
			ps = c.prepareStatement("update _test.tellers "+
						"   set tbalance = tbalance + ? "+
						" where tid = ?");
			ps.setInt(1,delta);
			ps.setInt(2,teller);
			ps.execute();
			ps = c.prepareStatement("update _test.branches "+
						"   set bbalance = bbalance + ? "+
						" where bid = ?");
			ps.setInt(1,delta);
			ps.setInt(2,branch);
			ps.execute();
			ps = c.prepareStatement("insert into _test.history "+
						"       (tid,bid,aid,delta,mtime) "+
						"values (?,?,?,?,now())");
			ps.setInt(1,teller);
			ps.setInt(2,branch);
			ps.setInt(3,account);
			ps.setInt(4,delta);
			ps.execute();
			c.commit();
		    } finally {
			c.close();
		    }	
		}
	    } catch (SQLException e) {
		logger.error(null,e);
	    }
	}

	private final Random r = new Random();
	private final int nAccounts = baseAccounts*numThreads*numScale;
    }

    @Option(name="-uri",usage="uri to database",required=true)
	private String uri;
    @Option(name="-help",usage="show help") private boolean helpme;
    @Option(name="-pool",usage="database connection pool size. Default is 10.") 
	private int poolSize = 10;
    @Option(name="-init",usage="initialize mode. (Re-)Create '_test' schema")
	private boolean initMode;
    @Option(name="-threads",usage="number of database threads updating database. Default is 1.")
	private int numThreads = 1;
    @Option(name="-transactions",usage="number of transactions per thread to test.\n"+
	    "Default is 1000")
	private int numTransactions = 1000;
    @Option(name="-scale",usage="scaling factor. Default is 1.\n"+
	    "Number of braches used = 1*threads*scale,\n"+
	    "Number of tellers used = 10*threads*scale,\n"+
	    "Number of accounts used = 10,000*threads*scale")
	private int numScale = 1;
	
    static {
	BasicConfigurator.resetConfiguration();
	BasicConfigurator.configure();
    }

    private final static String user = System.getProperty("user.name");
    private final static int baseBranches = 1;
    private final static int baseTellers = 10;
    private final static int baseAccounts = 10000;
    private static Logger logger = Logger.getLogger(PgJDBCBench.class);
    private BasicDataSource ds;
}
