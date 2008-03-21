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

import com.netblue.bruce.cluster.Cluster;
import com.netblue.bruce.cluster.ClusterFactory;
import com.netblue.bruce.cluster.ClusterInitializationException;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.log4j.Logger;

/**
 * <code>ReplicationDaemon</code> is the main engine of the replication process.  It is responsible for loading up the
 * cluster configuration data, spawning threads for each of the slave databases, and initializing the snapshot cache
 * (not necessarily in that order).  This class is not thread safe.  Specifically, access to and changes within the
 * <code>Cluster</code> managed by this class are not synchronized.
 *
 * @author lanceball
 * @version $Id: ReplicationDaemon.java 78 2007-09-05 01:24:54Z rklahn $
 */
public final class ReplicationDaemon implements Runnable {

    /**
     * Creates a new <code>ReplicationDaemon</code>
     */
    public ReplicationDaemon() {
    }

    /**
     * Loads a <code>Cluster</code> configuration named <code>clusterName</code>.  If <code>clusterName</code> does not
     * correspond to an existing configuration, a new <code>Cluster</code> will be created.
     *
     * @param clusterName
     *
     * @throws ClusterInitializationException if the <code>Cluster</code> cannot be initialized or if
     * <code>clusterName</code> is null
     */
    public void loadCluster(final String clusterName) throws Exception {
        final Cluster cluster = ClusterFactory.getClusterFactory().getCluster(clusterName);
        masterUri = cluster.getMaster().getUri();
        slaveFactory = new SlaveFactory(cluster);
    }

    /**
     * Starts the replication process for the currently loaded {@link com.netblue.bruce.cluster.Cluster}.  If no
     */
    public void run() {
        try{
            if (slaveFactory == null)
            {
                Main.fatalError( "Cannot run replication daemon without a valid cluster configuration and snapshot cache");
            }
            logSwitchRunner = new LogSwitchThread(masterUri);
            logSwitchThread = new Thread(logSwitchRunner,"LogSwitch");
            logSwitchThread.start();
            slaves = slaveFactory.spawnSlaves();
        }catch( Throwable t ){
            Main.fatalError("ReplicationDaemon.java caught throwable while spawning threads.",t);
        }
    }

    /**
     * Shuts down the replication daemon
     */
    public void shutdown() {
	try {
	    if (logSwitchThread != null) {
                logSwitchRunner.shutdown();
                logSwitchThread.join();
	    }
	} catch (InterruptedException e) { }
	if (slaveFactory != null) {
	    slaveFactory.shutdown();
	}
    }


    private String masterUri ;
    private ThreadGroup slaves;
    private SlaveFactory slaveFactory;
    private Thread logSwitchThread;
    private LogSwitchThread logSwitchRunner;
}
