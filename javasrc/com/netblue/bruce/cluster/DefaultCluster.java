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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Abstract <code>Cluster</code> implementation providing <code>ClusterChangeListener</code> helpers.  Child classes of this
 * <code>DefaultCluster</code> should use the <code>notify...</code> methods in this implementation to
 * notify all listeners of changes in the <code>Cluster</code> state.
 * @author lanceball
 */
public abstract class DefaultCluster implements Cluster
{

    /**
     * Adds a listener to this cluster.  Does not check if this listener has already been added.
     * @param listener the listener to add
     */
    public void addClusterChangeListener(ClusterChangeListener listener)
    {
        listeners.add(listener);
    }

    /**
     * Removes the listener from this cluster
     * @param listener the listener to remove
     * @return true of the listener was removed
     */
    public boolean removeClusterChangeListener(ClusterChangeListener listener)
    {
        return listeners.remove(listener);
    }

    /**
     * Gets all change listeners currently registered with this cluster
     * @return all change listeners currently registered with this cluster
     */
    public Collection<ClusterChangeListener> getClusterChangeListeners()
    {
        return listeners;
    }

    /**
     * Notifies all listeners that <code>newSlave</code> was added to this <code>Cluster</code>
     * @param newSlave the <code>Slave</code> that was added
     */
    public void notifySlaveAdded(Node newSlave)
    {
        for (ClusterChangeListener listener : listeners)
        {
            listener.slaveAdded(newSlave);
        }
    }

    /**
     * Notifies all listeners that <code>oldSlave</code> was removed from this <code>Cluster</code>
     * @param oldSlave the <code>Slave</code> that was removed from the this <code>Cluster</code>
     */
    public void notifySlaveRemoved(Node oldSlave)
    {
        for (ClusterChangeListener listener : listeners)
        {
            listener.slaveRemoved(oldSlave);
        }
    }

    /**
     * Notifies all listeners that <code>slave</code> was disabled
     * @param slave the <code>Slave</code> that was removed from the cluster
     */
    public void notifySlaveDisabled(Node slave)
    {
        for (ClusterChangeListener listener : listeners)
        {
            listener.slaveDisabled(slave);
        }
    }

    /**
     * Notifies all listeners that the <code>Master</code> for this <code>Cluster</code> has been replaced
     * @param oldMaster the <code>Master</code> that has been replaced
     * @param newMaster the new <code>Master</code> for this <code>Cluster</code>
     */
    public void notifyMasterReplaced(Node oldMaster, Node newMaster)
    {
        for (ClusterChangeListener listener : listeners)
        {
            listener.masterReplaced(oldMaster, newMaster);
        }
    }

    /**
     * Notifies all listeners that the <code>Master</code> node for this <code>Cluster</code> is unresponsive.
     * @param master the unresponsive <code>Master</code> node
     */
    public void notifyMasterUnavailable(Node master)
    {
        for (ClusterChangeListener listener : listeners)
        {
            listener.masterUnavailable(master);
        }
    }

    private Set<ClusterChangeListener> listeners = Collections.synchronizedSet(new HashSet<ClusterChangeListener>());

}
