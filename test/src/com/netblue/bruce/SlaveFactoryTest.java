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
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for the SlaveFactory class
 * @author lanceball
 * @version $Id$
 */
public class SlaveFactoryTest extends ReplicationTest
{
    private static ClusterFactory clusterFactory;
    private static Cluster cluster;

    @Before
    public synchronized void initializeCluster()
    {
        if (clusterFactory == null || cluster == null)
        {
            try
            {
                clusterFactory = ClusterFactory.getClusterFactory();
                cluster = clusterFactory.getCluster(CLUSTER_NAME);
            }
            catch (Exception e)
            {
                e.printStackTrace();
                fail("Unexpected exception: " + e.getClass().getName() + "\n" + e.getLocalizedMessage());
            }
        }
    }

    @Test
    public void testNewThread()
    {
        SlaveFactory factory = new SlaveFactory(cluster);
        assertNotNull(factory.newThread(new SimpleRunnable()));
        factory.shutdown();
    }

    @Test
    public void testNullConstructor()
    {
        try
        {
            new SlaveFactory(null);
            fail("SlaveFactory should fail with null constructor arguments");
        }
        catch (Exception e)
        {
            assertTrue("Exception of wrong type caught", e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testSpawnSlaves()
    {
        SlaveFactory slaveFactory = new SlaveFactory(cluster);
        ThreadGroup threadGroup = slaveFactory.spawnSlaves();
        assertNotNull("ThreadGroup from SlaveFactory should not be null", threadGroup);

        // The thread group should be named the same as the cluster
        assertEquals(threadGroup.getName(), cluster.getName());
        // should be a daemon thread group (destroyed when last thread is stopped
        assertTrue("ThreadGroup for cluster should be a daemon", threadGroup.isDaemon());
        // should be valid
        assertFalse(threadGroup.isDestroyed());

        // TODO:  This test is fragile and will probably break as we continue implementation
        // Should contain the same number of threads as our cluster
        final int slaveCount = cluster.getSlaves().size();
        Thread[] threads = new Thread[slaveCount];
        threadGroup.enumerate(threads);
        assertEquals(slaveCount, threadGroup.activeCount());
        assertNotNull(threads[0]);
        assertNotNull(threads[1]);
        slaveFactory.shutdown();
    }

    private static final class SimpleRunnable implements Runnable
    {
        public void run()
        {
            try { Thread.sleep(1); }
            catch (InterruptedException e) {}
        }
    }

}
