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

import org.apache.log4j.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class GenerateSnapshotThread implements Runnable {
    // No (accessable) empty constructor
    private GenerateSnapshotThread() {}
    
    public GenerateSnapshotThread(BruceProperties p, DataSource ds) {
	this.masterDS=ds;
	this.createSnapshotQuery=p.getProperty(CREATE_SNAPSHOT_QUERY_KEY,
					       CREATE_SNAPSHOT_QUERY_DEFAULT);
	logger.debug("createSnapshotQuery:"+this.createSnapshotQuery);
	this.snapshotFrequency=p.getIntProperty(SNAPSHOT_FREQUENCY_KEY,
						SNAPSHOT_FREQUENCY_DEFAULT); 
	logger.debug("snapshotFrequency:"+this.snapshotFrequency);
	this.retryTimewait=p.getIntProperty(SQL_RETRY_TIMEWAIT_KEY,SQL_RETRY_TIMEWAIT_DEFAULT);
	logger.debug("retryTimewait:"+this.retryTimewait);
    }
 
    public synchronized void shutdown() {
        shutdownRequested = true;
        logger.info("Shutting down Generate Snapshot Thread.");
    }

    public void run() {
	while (!shutdownRequested) {
	    try {
		sqlError=false;		
		Connection c = masterDS.getConnection();
		try { // Make sure connection gets closed
		    c.setAutoCommit(true);
		    c.createStatement().execute(createSnapshotQuery);
		} finally {
		    c.close();
		}
	    } catch (SQLException e) {
		sqlError=true;
		logger.error("SQLException in Generate Snapshot Thread. Waiting retryTimewait:"+
			     retryTimewait+" before next attempt",
			     e);
	    }
	    try {
		if (sqlError) {
		    Thread.sleep(retryTimewait);
		} else {
		    Thread.sleep(snapshotFrequency);
		}
	    } catch (InterruptedException te) {
		logger.warn("Generate Snapshot Thread was interrupted",te);
	    }
	}
    }

    private DataSource masterDS; // datasource to master database
    private static final Logger logger = 
	Logger.getLogger(GenerateSnapshotThread.class);
    private String createSnapshotQuery;
    private int snapshotFrequency;
    private int retryTimewait;
    private boolean shutdownRequested = false;
    private boolean sqlError = false;

    //
    // Properties that drive actions for this thread
    // 
    // Query that generates a snapshot
    private static final String CREATE_SNAPSHOT_QUERY_KEY = "bruce.createSnapshotQuery";
    private static final String CREATE_SNAPSHOT_QUERY_DEFAULT = "select bruce.logsnapshot()";
    // How often to try and snapshot. Not guarenteed. Likely to be several miliseconds later
    private static final String SNAPSHOT_FREQUENCY_KEY = "bruce.snapshotFrequency";
    private static final int SNAPSHOT_FREQUENCY_DEFAULT = 1000; // One second
    // How long to wait after a SQL error
    private static final String SQL_RETRY_TIMEWAIT_KEY = "bruce.snapshotSQLTimeWait";
    private static final int SQL_RETRY_TIMEWAIT_DEFAULT = 1000;
}
