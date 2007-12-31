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

/**
 * Listens for changes in the cluster's configuration.
 * @see Cluster
 * @author lanceball
 */
public interface ClusterChangeListener
{
    /**
     * Notifies listeners when a new <code>Slave</code> is added to the cluster.
     * @param newSlave the slave that was added to the cluster
     */
    public void slaveAdded(Node newSlave);

    /**
     * Notifies listeners when a slave is removed from the cluster
     * @param oldSlave the slave that was removed
     */
    public void slaveRemoved(Node oldSlave);

    /**
     * Notifies listeners when a slave is disabled (this may mean that it is simply unavailable)
     * @param slave
     */
    public void slaveDisabled(Node slave);

    /**
     * Notifies listeners when a slave is enabled
     * @param slave
     */
    public void slaveEnabled(Node slave);

    /**
     * Notifies listeners when the cluster's master is replaced.
     * @param oldMaster the old master or null, if a master did not already exist
     * @param newMaster the new master
     */
    public void masterReplaced(Node oldMaster, Node newMaster);

    /**
     * Notifies listeners when the master is disabled - which usually means it's unavailable
     * @param master the unavailable master
     */
    public void masterUnavailable(Node master);
}
