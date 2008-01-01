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
import com.netblue.bruce.cluster.Node;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ThreadFactory;

/**
 * A simple factory class to create <code>Thread</code>s for the slave nodes.
 * @author lanceball  
 * @version $Id: SlaveFactory.java 72519 2007-06-27 14:24:08Z lball $
 */
public class SlaveFactory implements ThreadFactory
{
    /**
     * Creates a new <code>SlaveFactory</code> capable of spawning slaves for each slave {@link Node} in <code>cluster</code> 
     *
     * @param cluster the cluster configuration for the slaves
     */
    public SlaveFactory(final Cluster cluster)
    {
        if (cluster == null)
        {
            throw new IllegalArgumentException("Cluster cannot be null");
        }
        this.cluster = cluster;
        LOGGER.setLevel(Level.INFO);
        threadGroup = new ThreadGroup(this.cluster.getName());
        threadGroup.setDaemon(true);

        masterDataSource = new BasicDataSource();
        masterDataSource.setUrl(cluster.getMaster().getUri());
        masterDataSource.setDriverClassName(System.getProperty("bruce.jdbcDriverName", "org.postgresql.Driver"));
        masterDataSource.setValidationQuery(System.getProperty("bruce.poolQuery", "select now()"));

        snapshotCache = new SnapshotCache(masterDataSource);
    }

    /**
     * Creates a new <code>Thread</code> using <code>runnable</code>.  May be used to add another thread - for example
     * a maintenance thread - to the <code>ThreadGroup</code> managed by this <code>SlaveFactory</code>.
     * @param runnable
     * @return a new thread which is part of the ThreadGroup managed by this SlaveFactory
     */
    public Thread newThread(final Runnable runnable)
    {
        return new Thread(threadGroup, runnable);
    }

    /**
     * Spawns a {@link Thread} for each slave in the <code>Cluster</code>.
     * @return A <code>ThreadGroup</code> containing all slave threads for this <code>Cluster</code>
     */
    public ThreadGroup spawnSlaves()
    {
        final Set<Node> nodes = cluster.getSlaves();
        for (Node node : nodes)
        {
            final SlaveRunner slaveRunner = new SlaveRunner(snapshotCache, cluster, node);
            Thread thread = newThread(slaveRunner);
            thread.setName(node.getName());
            LOGGER.info("[" + threadGroup.getName() + "]: spawning slave thread for node: " + node.getName());
            thread.start();
            threadMap.put(thread, slaveRunner);
        }
        return threadGroup;
    }

    /**
     * Gets the Cluster configuration used by this <code>SlaveFactory</code> to spawn
     * <code>SlaveRunner</code> threads and to create a <code>SnapshotCache</code> for
     * the master database
     * @return the <code>Cluster</code> used by this <code>SlaveFactory</code>
     */
    public Cluster getCluster()
    {
        return cluster;
    }

    public synchronized void shutdown()
    {
        LOGGER.info("Shutting down slaves.");
        final Set<Thread> threads = threadMap.keySet();
        for (Thread thread : threads)
        {
            SlaveRunner runner = threadMap.get(thread);
            runner.shutdown();
            try
            {
                thread.join();
            }
            catch (InterruptedException e)
            {
                LOGGER.warn("Interrupted waiting for thread [" + thread.getName() + "] to shutdown");
            }
        }
        try
        {
            masterDataSource.close();
        }
        catch (SQLException e)
        {
            LOGGER.warn("Unable to close master data source");
        }
    }

    private final Cluster cluster;
    private final ThreadGroup threadGroup;
    private final SnapshotCache snapshotCache;
    private final HashMap<Thread, SlaveRunner> threadMap = new HashMap<Thread, SlaveRunner>();
    private static final Logger LOGGER = Logger.getLogger(SlaveFactory.class);
    private BasicDataSource masterDataSource;
}
