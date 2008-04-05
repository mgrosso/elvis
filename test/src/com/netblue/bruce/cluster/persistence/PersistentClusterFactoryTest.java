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
package com.netblue.bruce.cluster.persistence;

import com.netblue.bruce.ReplicationTest;
import com.netblue.bruce.cluster.Cluster;
import com.netblue.bruce.cluster.ClusterFactory;
import com.netblue.bruce.cluster.ClusterInitializationException;
import com.netblue.bruce.cluster.Node;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

/**
 * Tests {@link com.netblue.bruce.cluster.persistence.PersistentClusterFactory}
 * @author lanceball
 */
public class PersistentClusterFactoryTest extends ReplicationTest
{
    private ClusterFactory defaultFactory = null;

    @Before
    public void setUp()
    {
        super.setUp();
        defaultFactory = getClusterFactory();
    }

    @Test
    public void testDefaultCluster()
    {
        // Test that we get what we expect whit the default cluster
        Cluster cluster = defaultFactory.getCluster(ClusterFactory.DEFAULT_CLUSTER_NAME);
        assertNotNull("Cluster should not be null", cluster);
        assertEquals("Unexpected default Cluster instance", ClusterFactory.DEFAULT_CLUSTER_NAME, cluster.getName());
    }

    /**
     * Tests whether the ClusterFactory creates more than one cluster for a given name
     */
    @Test
    public void testSingletonClusterInstances()
    {
        // first test the default
        Cluster cluster = defaultFactory.getCluster(ClusterFactory.DEFAULT_CLUSTER_NAME);
        assertSame("PersistentClusterFactory should only create one instance for a given cluster", cluster, defaultFactory.getCluster(ClusterFactory.DEFAULT_CLUSTER_NAME));

        // Then test for a custom name
        cluster = defaultFactory.getCluster("FOOBAR");
        assertSame("PersistentClusterFactory should only create one instance for a given cluster", cluster, defaultFactory.getCluster("FOOBAR"));
    }

    @Test
    public void testNullName()
    {
        try
        {
            Cluster cluster = defaultFactory.getCluster(null);
            fail("Cluster initialization should fail with a null name");
        }
        catch (Exception e)
        {
            assertTrue(e instanceof ClusterInitializationException);
        }
    }

    @Test
    public void testClusterInitialization()
    {
        try
        {            
            Cluster cluster = defaultFactory.getCluster("Cluster Un");
            assertNotNull("Test cluster should not be null", cluster);

            // now check that it has the data we expect from the database
            assertEquals("Wrong ID Found", 1000L, cluster.getId());
            assertEquals("Wrong name found", "Cluster Un", cluster.getName());

            // Now check for the master node
            Node master = cluster.getMaster();
            assertNotNull("Master node for cluster should not be null", master);
            assertEquals("Unexpected name for master found", "Cluster 0 - Primary master", master.getName());
            assertEquals("Unexpected ID for master found", 1L, master.getId());
            assertTrue("Master should be available", master.isAvailable());

            // Now check for the slave nodes
            Set<Node> slaves = cluster.getSlaves();
            assertNotNull("Slave node set should not be null", slaves);
            assertEquals("Unexpected length for slave node set", 2, slaves.size());
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Exception caught: " + e.getLocalizedMessage());
        }
    }

    public void assertPersistentClusterFactory(Object factory)
    {
        assertTrue("Expected an instance of PersistentClusterFactory", factory instanceof PersistentClusterFactory);
    }

    public static final ClusterFactory getClusterFactory()
    {
        ClusterFactory factory = null;
        try
        {
            factory = ClusterFactory.getClusterFactory();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Cannot create defaultFactory: " + e.getLocalizedMessage());
        }
        assertNotNull("Cannot instantiate defaultFactory", factory);
        return factory;
    }

}
