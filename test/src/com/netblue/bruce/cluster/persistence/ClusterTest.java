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

import com.netblue.bruce.cluster.Cluster;
import com.netblue.bruce.cluster.ClusterChangeListener;
import com.netblue.bruce.cluster.ClusterFactory;
import com.netblue.bruce.cluster.Node;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

/**
 * @author lanceball
 */
public class ClusterTest extends HibernatePersistenceTest
{
    private ClusterFactory defaultFactory = null;

    @Before
    public void setUp()
    {
        super.setUp();
        defaultFactory = PersistentClusterFactoryTest.getClusterFactory();
    }

    @Test
    public void testAddSlave()
    {
        // Get the cluster we want to work with
        Cluster cluster = getCluster();

        // Create a new slave and initialize all of its values
        Node slave = new com.netblue.bruce.cluster.persistence.Node();
        slave.setName("A new test slave");
        slave.setUri("This is the URI!");

        // Create a listener to test if we get updated when the slave was added
        final MockClusterChangeListener clusterChangeListener = new MockClusterChangeListener();
        cluster.addClusterChangeListener(clusterChangeListener);
        assertFalse("Listener should start with slaveAdded = false", clusterChangeListener.slaveAdded);

        // Add the slave to our cluster
        cluster.addSlave(slave);

        // check that our listener was notified when the slave was added
        assertTrue("Cluster not notified when slave changes", clusterChangeListener.slaveAdded);

        // Get another instance of the same cluster and see if the slave is there
        Cluster anotherCluster = getCluster();
        Set<Node> slaves = anotherCluster.getSlaves();
        assertEquals("Cluster slave collection not updated correctly", 3, slaves.size());
        assertEquals("Cluster slave collection not updated correctly", 3, cluster.getSlaves().size());

        // Be sure the one we added is in the set
        boolean foundSlave = false;
        for (Node node : slaves)
        {
            if (node == slave) { foundSlave = true; }
        }

        assertTrue("Couldn't find the slave we expected in DB", foundSlave);

        // Now be sure the cluster added itself to the slave
        Set<Cluster> parentCluster = slave.getCluster();
        assertEquals("Unexected cluster size", 1, parentCluster.size());

        // Make sure we can't add the slave again
        cluster.addSlave(slave);

        // assert the list of slaves has not grown
        assertEquals(cluster.getSlaves().size(), 3);
    }

    @Test
    public void testRemoveSlave()
    {
        Cluster cluster = getCluster();
        Node slave = cluster.getSlaves().iterator().next();
        assertNotNull(slave);

        // Create a change listener
        final MockClusterChangeListener clusterChangeListener = new MockClusterChangeListener();
        cluster.addClusterChangeListener(clusterChangeListener);

        // be sure the slave was removed and the listener fired
        assertSame(cluster.removeSlave(slave), slave);
        assertTrue("Cluster not notified when slave changes", clusterChangeListener.slaveRemoved);

        // get a different instance of the cluster and check to see if the size of the slave node set is correct
        Cluster anotherCluster = getCluster();
        Set<Node> slaves = anotherCluster.getSlaves();
        assertEquals(slaves.size(), 1);

        // Now try to remove a slave that doesn't already exist
        clusterChangeListener.slaveRemoved = false;
        Node otherSlave = new com.netblue.bruce.cluster.persistence.Node();
        assertNull("Cluster should return null when a non-existent slave is removed", cluster.removeSlave(otherSlave));
        assertFalse("Cluster should not have notified when a non-existent slave was removed", clusterChangeListener.slaveRemoved);
    }

    @Test
    public void testSlaveDisabledEnabled()
    {
        Cluster cluster = getCluster();
        Node slave = cluster.getSlaves().iterator().next();
        assertNotNull(slave);

        // Create a change listener
        final MockClusterChangeListener clusterChangeListener = new MockClusterChangeListener();
        cluster.addClusterChangeListener(clusterChangeListener);

        // Now disable the slave and ensure that we are notified
        slave.isAvailable(false);
        assertTrue("Cluster not notified when a slave is disabled", clusterChangeListener.slaveDisabled);

        // Enable it again
        slave.isAvailable(true);
        assertTrue("Cluster not notified when a slave is enabled", clusterChangeListener.slaveEnabled);
    }

    @Test
    public void testAddGetClusterChangeListener()
    {
        Cluster cluster = getCluster();
        assertEquals("Unexpected number of listeners found", 0, cluster.getClusterChangeListeners().size());

        final MockClusterChangeListener clusterChangeListener = new MockClusterChangeListener();
        cluster.addClusterChangeListener(clusterChangeListener);
        assertEquals("Unexpected number of listeners found", 1, cluster.getClusterChangeListeners().size());
        assertSame("Unexpected listener found", clusterChangeListener, cluster.getClusterChangeListeners().iterator().next());

        // let's be sure we don't get duplicates when the same listener is added twice
        cluster.addClusterChangeListener(clusterChangeListener);
        assertEquals("Unexpected number of listeners found", 1, cluster.getClusterChangeListeners().size());
    }

    @Test
    public void testRemoveClusterChangeListener()
    {
        // first test the initial state
        Cluster cluster = getCluster();
        assertEquals("Unexpected number of listeners found.", 0, cluster.getClusterChangeListeners().size());

        // create and add a listener
        final MockClusterChangeListener clusterChangeListener = new MockClusterChangeListener();
        cluster.addClusterChangeListener(clusterChangeListener);

        // be sure it was added
        assertEquals("Unexpected number of listeners found", 1, cluster.getClusterChangeListeners().size());
        assertSame("Unexpected listener found", clusterChangeListener, cluster.getClusterChangeListeners().iterator().next());

        // now remove it
        assertTrue("Cluster should return true when a listener is removed", cluster.removeClusterChangeListener(clusterChangeListener));
        assertEquals("Unexpected number of listeners found.", 0, cluster.getClusterChangeListeners().size());

        // Now try to remove one that didn't exist
        assertFalse("Cluster should return false when an invalid listener is removed", cluster.removeClusterChangeListener(new MockClusterChangeListener()));
    }

    @Test
    public void testGetId()
    {
        Cluster cluster = getCluster();
        assertEquals(1000L, cluster.getId());
    }

    @Test
    public void testSetGetName()
    {
        Cluster cluster = getCluster();
        final String newName = "This is a new name for the cluster";
        cluster.setName(newName);
        assertEquals(newName, cluster.getName());
    }

    @Test
    public void testGetReplaceMaster()
    {
        Cluster cluster = getCluster();
        final Node oldMaster = cluster.getMaster();
        assertNotNull("Master should not be null", oldMaster);

        // Create a change listener so we can verify that folks are notified when this happens
        final MockClusterChangeListener clusterChangeListener = new MockClusterChangeListener();
        assertFalse(clusterChangeListener.masterReplaced);
        cluster.addClusterChangeListener(clusterChangeListener);

        Node newMaster = new com.netblue.bruce.cluster.persistence.Node();
        assertEquals("replaceMaster() should return old master", oldMaster, cluster.replaceMaster(newMaster));
        assertSame("New master node not set", newMaster, cluster.getMaster());
        assertTrue("Listener not updated when the master node was replaced", clusterChangeListener.masterReplaced);
    }

    @Test
    public void testMasterUnavailable()
    {
        Cluster cluster = getCluster();
        Node master = cluster.getMaster();

        // Create a change listener so we can verify that folks are notified when this happens
        final MockClusterChangeListener clusterChangeListener = new MockClusterChangeListener();
        assertFalse(clusterChangeListener.masterUnavailable);
        cluster.addClusterChangeListener(clusterChangeListener);

        master.isAvailable(false);
        assertFalse("Listener not updated when the master node became unavailable", clusterChangeListener.masterUnavailable);
    }

    @Test
    public void testEquals()
    {
        Cluster clusterOne = getCluster();
        assertFalse(clusterOne.equals(null));
        assertTrue(clusterOne.equals(clusterOne));

        Cluster clusterTwo = new com.netblue.bruce.cluster.persistence.Cluster();
        clusterTwo.setName(clusterOne.getName());
        clusterTwo.setSlaves(clusterOne.getSlaves());
        clusterTwo.replaceMaster(clusterOne.getMaster());
        ((com.netblue.bruce.cluster.persistence.Cluster)clusterTwo).id = clusterOne.getId();
        assertTrue(clusterOne.equals(clusterTwo));
        assertTrue(clusterTwo.equals(clusterOne));
        clusterOne.setName("FOOBAR");
        assertFalse(clusterOne.equals(clusterTwo));
        assertFalse(clusterTwo.equals(clusterOne));
    }

    @Test
    public void testSetSlaves()
    {
        // create a cluster initialized from the db
        Cluster one = getCluster();
        // create an empty cluster
        Cluster clusterTwo = new com.netblue.bruce.cluster.persistence.Cluster();

        // get the original set for the empty cluster (should be empty)
        Set<Node> twoSlaves = clusterTwo.getSlaves();
        assertNotNull(twoSlaves);
        assertEquals("Slave set should be empty", 0, twoSlaves.size());

        // add a single node to the slave set - just so we can test that it was removed
        clusterTwo.addSlave(new com.netblue.bruce.cluster.persistence.Node());        

        // Create a change listener so we can verify that folks are notified when this happens
        final MockClusterChangeListener clusterChangeListener = new MockClusterChangeListener();
        assertFalse(clusterChangeListener.slaveRemoved);
        assertEquals(0, clusterChangeListener.slaveRemovedCounter);

        assertFalse(clusterChangeListener.slaveAdded);
        assertEquals(0, clusterChangeListener.slaveAddedCounter);
        clusterTwo.addClusterChangeListener(clusterChangeListener);

        // set the slaves on our empty cluster.
        clusterTwo.setSlaves(one.getSlaves());

        // make sure our listener was notified - 2 nodes were added
        assertTrue(clusterChangeListener.slaveAdded);
        assertEquals(2, clusterChangeListener.slaveAddedCounter);

        // one node was removed
        assertTrue(clusterChangeListener.slaveRemoved);
        assertEquals(1, clusterChangeListener.slaveRemovedCounter);

    }

    private Cluster getCluster()
    {
        return defaultFactory.getCluster(CLUSTER_NAME);
    }

    private static final class MockClusterChangeListener implements ClusterChangeListener
    {
        int slaveAddedCounter = 0;
        int slaveRemovedCounter = 0;
        boolean slaveAdded = false;
        boolean slaveRemoved = false;
        boolean slaveEnabled = false;
        boolean slaveDisabled = false;
        boolean masterReplaced = false;
        boolean masterUnavailable = false;

        /**
         * Notifies listeners when a new <code>Slave</code> is added to the cluster.
         *
         * @param newSlave the slave that was added to the cluster
         */
        public void slaveAdded(Node newSlave)
        {
            this.slaveAdded = true;
            slaveAddedCounter++;
        }

        /**
         * Notifies listeners when a slave is removed from the cluster
         *
         * @param oldSlave the slave that was removed
         */
        public void slaveRemoved(Node oldSlave)
        {
            this.slaveRemoved = true;
            slaveRemovedCounter++;
        }

        /**
         * Notifies listeners when a slave is disabled (this may mean that it is simply unavailable)
         *
         * @param slave
         */
        public void slaveDisabled(Node slave)
        {
            this.slaveDisabled = true;
        }

        /**
         * Notifies listeners when a slave is enabled
         *
         * @param slave
         */
        public void slaveEnabled(Node slave)
        {
            this.slaveEnabled = true;
        }

        /**
         * Notifies listeners when the cluster's master is replaced.
         *
         * @param oldMaster the old master or null, if a master did not already exist
         * @param newMaster the new master
         */
        public void masterReplaced(Node oldMaster, Node newMaster)
        {
            this.masterReplaced = true;
        }

        /**
         * Notifies listeners when the master is disabled - which usually means it's unavailable
         *
         * @param master the unavailable master
         */
        public void masterUnavailable(Node master)
        {
            this.masterUnavailable = true;
        }

    }

    private static final String CLUSTER_NAME = "Cluster Un";
}
