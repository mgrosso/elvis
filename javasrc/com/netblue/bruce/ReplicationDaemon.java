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
 * @version $Id: ReplicationDaemon.java 72519 2007-06-27 14:24:08Z lball $
 */
public final class ReplicationDaemon implements Runnable
{

    /**
     * Creates a new <code>ReplicationDaemon</code>
     *
     * @throws ClusterInitializationException if the cluster factory cannot initialize
     */
    public ReplicationDaemon()
    {
        properties = new BruceProperties();
        properties.putAll(System.getProperties());
        try
        {
            clusterFactory = ClusterFactory.getClusterFactory();
            masterDataSource.setDriverClassName(System.getProperty("bruce.jdbcDriverName", "org.postgresql.Driver"));
            masterDataSource.setValidationQuery(System.getProperty("bruce.poolQuery", "select now()"));
        }
        catch (Throwable throwable)
        {
            LOGGER.fatal("Cannot initialize Cluster configuration factory.  Nothing else to do but bail", throwable);
            throw new ClusterInitializationException("Unable to initialize ClusterFactory", throwable);
        }

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
    public void loadCluster(final String clusterName)
    {
        final Cluster cluster = clusterFactory.getCluster(clusterName);
        masterDataSource.setUrl(cluster.getMaster().getUri());

        // Create our SlaveFactory
        slaveFactory = new SlaveFactory(cluster);
    }

    /**
     * Gets the <code>Cluster</code> configuration that is currently active
     *
     * @return the <code>Cluster</code> or <code>null</code> if one has not been initialized
     */
    public Cluster getCluster()
    {
        return slaveFactory != null ? slaveFactory.getCluster() : null;
    }

    /**
     * Starts the replication process for the currently loaded {@link com.netblue.bruce.cluster.Cluster}.  If no
     * <code>Cluster</code> has been loaded, throws {@link com.netblue.bruce.cluster.ClusterInitializationException}
     */
    public void run()
    {
        if (slaveFactory == null)
        {
            throw new ClusterInitializationException("Cannot run replication daemon without a valid cluster configuration and snapshot cache");
        }
        logSwitchRunner = new LogSwitchThread(properties, masterDataSource);
        logSwitchThread = new Thread(logSwitchRunner,"LogSwitch");
        logSwitchThread.start();
	generateSnapshotRunner = new GenerateSnapshotThread(properties, masterDataSource);
	generateSnapshotThread = new Thread(generateSnapshotRunner,"GenerateSnapshot");
	generateSnapshotThread.start();
        slaves = slaveFactory.spawnSlaves();
    }

    /**
     * Returns the number of active slaves for the current cluster.  If the daemon is not currently initialized with a
     * cluster, returns 0.
     *
     * @return The number of active slaves for the current cluster.
     */
    public int getActiveSlaveCount()
    {
        return slaves.activeCount();
    }

    /**
     * Shuts down the replication daemon
     */
    public void shutdown()
    {
	try {
	    if (logSwitchThread != null) {
                logSwitchRunner.shutdown();
                logSwitchThread.join();
	    }
	} catch (InterruptedException e) { }
	try {
	    if (generateSnapshotThread != null) {
                generateSnapshotRunner.shutdown();
                generateSnapshotThread.join();
	    }
	} catch (InterruptedException e) { }
	if (slaveFactory != null) {
	    slaveFactory.shutdown();
	}
    }


    private ThreadGroup slaves;
    private SlaveFactory slaveFactory;
    private Thread logSwitchThread;
    private LogSwitchThread logSwitchRunner;
    private Thread generateSnapshotThread;
    private GenerateSnapshotThread generateSnapshotRunner;
    private final BruceProperties properties;
    private final ClusterFactory clusterFactory;
    private final BasicDataSource masterDataSource = new BasicDataSource();
    private static final Logger LOGGER = Logger.getLogger(ReplicationDaemon.class);
}
