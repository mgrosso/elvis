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
package com.netblue.bruce.cluster;

import java.util.Collection;
import java.util.Set;

/**
 * Describes the inteface for a database <code>Cluster</code>.  Each cluster can have one master <code>Node</code>
 * and 0 to n slave <code>Node</code>s.  To be notified of changes in the cluster, add a {@link com.netblue.bruce.cluster.ClusterChangeListener}.
 * @author lanceball
 */
public interface Cluster
{
    /**
     * Gets the unique ID for this <code>Cluster</code>
     * @return the ID
     */
    public Long getId();

    /**
     * Gets the name for this <code>Cluster</code>
     * @return the name
     */
    public String getName();

    /**
     * Sets the name for this <code>Cluster</code>
     * @param name the name of the cluster
     */
    public void setName(String name);

    /**
     * Gets the configuration data for the master database
     * @return the one and only Master - should never return null.
     */
    public Node getMaster();

    /**
     * Gets the set of 0 or more slave databases.
     * @return the set of slaves - should never return null, but the set may be empty
     */
    public Set<Node> getSlaves();

    /**
     * Adds a new slave to the database cluster
     * @param newSlave the new slave
     */
    public void addSlave(Node newSlave);

    /**
     * Removes <code>slave</code> from the current database cluster.  If <code>slave</code> does not
     * exist in the cluster, null is returned.  Otherwise, the slave that has been removed will be
     * returned with {@link com.netblue.bruce.cluster.Slave#available()} returning <code>false</code>.
     * @param slave the slave to remove
     * @return the slave to remove or null if the slave did not already exist in the cluster
     */
    public Node removeSlave(Node slave);

    /**
     * Replaces the current master node with <code>newMaster</code>
     * @param newMaster the new master database node
     * @return the old master database node - never null
     */
    public Node replaceMaster(Node newMaster);

    /**
     * Adds a change listener to this Cluster
     * @param listener the listener to add
     */
    public void addClusterChangeListener(ClusterChangeListener listener);

    /**
     * Removes the change listener from this Cluster.
     * @param listener the listener to remove
     * @return true if the listener was contained in this cluster
     */
    public boolean removeClusterChangeListener(ClusterChangeListener listener);

    /**
     * Gets the collection of change listeners currently active on this Cluster
     * @return the collection of listeners - never null, but the list may be empty
     */
    public Collection<ClusterChangeListener> getClusterChangeListeners();

    /**
     * Sets the list of slaves for this Cluster.  This is equivalent to removing all
     * slaves from the cluster and replacing them with a new collection
     * @param slaves the Set of slave nodes
     */
    void setSlaves(final Set<Node> slaves);
}
