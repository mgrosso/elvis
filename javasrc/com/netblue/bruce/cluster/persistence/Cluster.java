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


import com.netblue.bruce.cluster.DefaultCluster;
import com.netblue.bruce.cluster.Node;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.hibernate.annotations.Index;

import javax.persistence.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Persistent representation of a replication cluster.  
 * @author lanceball
 */
@Entity
@Table( name = "YF_CLUSTER")
class Cluster extends DefaultCluster
{
    /**
     * Gets the unique ID for this <code>Cluster</code>
     *
     * @return the ID
     */
    public Long getId()
    {
        return this.id;
    }

    /**
     * Gets the name for this <code>Cluster</code>
     *
     * @return the name
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * Sets the name for this <code>Cluster</code>
     *
     * @param name the name of the cluster
     */
    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * Gets the configuration data for the master database
     *
     * @return the one and only Master - should never return null.
     */
    public com.netblue.bruce.cluster.Node getMaster()
    {
        return  master;
    }

    /**
     * Gets the set of 0 or more slave databases.
     *
     * @return the unmodifiable set of slaves - should never return null, but the set may be empty
     */
    public Set<com.netblue.bruce.cluster.Node> getSlaves()
    {
        return Collections.unmodifiableSet(filterNodes());
    }

    /**
     * A simple helper method that filters the child nodes to only include slaves - no masters
     */
    private Set<Node> filterNodes()
    {
        Set<Node> filteredNodes = new LinkedHashSet<Node>();
        for (Node node : slaves)
        {
            if (!node.equals(this.master)) { filteredNodes.add(node);}
        }
        return filteredNodes;
    }

    /**
     * Adds a new slave to the database cluster. 
     *
     * @param newSlave the new slave
     */
    public void addSlave(com.netblue.bruce.cluster.Node newSlave)
    {
        // Be sure it back references correctly
        setParent(newSlave);

        // Add the new slave to our list of slaves
        slaves.add(newSlave);

        notifySlaveAdded(newSlave);
    }

    /**
     * Adds this <code>Cluster</code> to the list of parents for the node
     * @param node the node
     */
    private void setParent(final Node node)
    {
        Set<com.netblue.bruce.cluster.Cluster> parents = node.getCluster();
        if (parents == null)
        {
            // No Cluster registered for this node at all
            parents = new HashSet<com.netblue.bruce.cluster.Cluster>();
        }
        if (parents.size() == 0 || !parents.contains(this))
        {
            // Add ourselves to the list of parents
            parents.add(this);
        }
        // set and notify
        node.setCluster(parents);
    }

    /**
     * Removes <code>slave</code> from the current database cluster.  If <code>slave</code> does not exist in the
     * cluster, null is returned.  Otherwise, the slave that has been removed will be returned with {@link
     * com.netblue.bruce.cluster.Slave#available()} returning <code>false</code>.
     *
     * @param slave the slave to remove The Barrel House Mamas
     *
     * @return the slave to remove or null if the slave did not already exist in the cluster
     */
    public com.netblue.bruce.cluster.Node removeSlave(com.netblue.bruce.cluster.Node slave)
    {
        if (slaves.contains(slave))
        {
            slaves.remove(slave);
            notifySlaveRemoved(slave);
            return slave;
        }
        return null;
    }

    /**
     * Replaces the current master node with <code>newMaster</code>
     *
     * @param newMaster the new master database node
     *
     * @return the old master database node - never null
     */
    public com.netblue.bruce.cluster.Node replaceMaster(Node newMaster)
    {
        Node oldMaster = this.master;
        this.master = (com.netblue.bruce.cluster.persistence.Node) newMaster;
        setParent(newMaster);
        notifyMasterReplaced(oldMaster, newMaster);
        return oldMaster;
    }

    /**
     * Sets the list of slaves for this Cluster.  This is equivalent to removing all slaves from the cluster and
     * replacing them with a new collection
     *
     * @param slaves the Set of slave nodes
     */
    public void setSlaves(final Set<Node> newSlaves)
    {
        for (Node slave : this.slaves)
        {
            this.removeSlave(slave);
        }
        for (Node slave : newSlaves)
        {
            this.addSlave(slave);
        }
    }


    public int hashCode()
    {
        return new HashCodeBuilder()
                .append(id)
                .append(name)
                .append(master)
                .append(slaves)
                .toHashCode();
    }

    public boolean equals(Object object)
    {
        if (object instanceof Cluster == false)
        {
            return false;
        }
        if (this == object)
        {
            return true;
        }
        Cluster rhs = (Cluster) object;

        // this is a total hack, but since we are dealing with hibernate internals as the set representation
        // for the slaves, we have to extract all slaves from the set and put them in a new set to ensure that
        // equals() works.  Hokey, but if you can find a better way, please do!
        LinkedHashSet<Node> mySlaves = new LinkedHashSet<Node>(getSlaves());
        LinkedHashSet<Node> rhsSlaves = new LinkedHashSet<Node>(rhs.getSlaves());
        return new EqualsBuilder()
                .append(id, rhs.id)
                .append(name, rhs.name)
                .append(master, rhs.master)
                .append(mySlaves, rhsSlaves)
                .isEquals();
    }

    @Id @GeneratedValue
    Long id;  // did not make this private b/c need to set it during tests, but don't want users to set it explicitly

    @Column
    @Index(name = "yf_cluster_name_idx")
    private String name;

    @OneToOne(targetEntity = com.netblue.bruce.cluster.persistence.Node.class, fetch = FetchType.EAGER)
    @JoinColumn( name = "master_node_id", nullable = true, unique = true) 
    private com.netblue.bruce.cluster.persistence.Node master;

    @ManyToMany(mappedBy = "cluster", targetEntity = com.netblue.bruce.cluster.persistence.Node.class, fetch = FetchType.EAGER)
    private Set<com.netblue.bruce.cluster.Node> slaves = new LinkedHashSet<Node>();
}
