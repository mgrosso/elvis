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
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.hibernate.annotations.ForeignKey;

import javax.persistence.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Persistent representation of a database node in the replication cluster.
 *
 * @author lanceball
 */
@Entity
@Table(name = "YF_NODE")
class Node implements com.netblue.bruce.cluster.Node
{

    /**
     * The unique ID for this node
     *
     * @return a unique ID
     */
    public Long getId()
    {
        return this.id;
    }

    /**
     * The URI for this node
     *
     * @return the URI
     */
    public String getUri()
    {
        return this.uri;
    }

    /**
     * The name for this node
     *
     * @return the name (may be null)
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * True if the database node is currently available
     *
     * @return true of the database node is available
     */
    public boolean isAvailable()
    {
        return this.available;
    }

    /**
     * Gets the cluster for this node.
     *
     * @return this <code>Cluster</code> this node belongs to
     */
    public Set<com.netblue.bruce.cluster.Cluster> getCluster()
    {
        return this.cluster;
    }

    /**
     * Sets the URI for this node
     *
     * @param uri
     */
    public void setUri(String uri)
    {
        this.uri = uri;
    }

    /**
     * Sets whether this node is available
     *
     * @param available
     */
    public void isAvailable(boolean available)
    {
        if (this.available == null || this.available != available)
        {
            this.available = available;
            Set<Cluster> parents = getCluster();
            if (parents.size() > 0) // This may seem redundant, but it's necessary for hibernate
            {
                for (Cluster parent : parents)
                {
                    final Collection<ClusterChangeListener> changeListeners = parent.getClusterChangeListeners();
                    for (ClusterChangeListener listener : changeListeners)
                    {
                        if (available)
                        {
                            listener.slaveEnabled(this);
                        }
                        else
                        {
                            listener.slaveDisabled(this);
                        }
                    }
                }
            }
        }
    }

    /**
     * An alias for {@link #isAvailable()}
     *
     * @param available
     */
    public void setAvailable(boolean available)
    {
        this.isAvailable(available);
    }

    /**
     * Sets the parent <code>Cluster</code>s for this <code>Node</code>
     *
     * @param cluster
     */
    public void setCluster(Set<com.netblue.bruce.cluster.Cluster> cluster)
    {
        this.cluster = cluster;
    }

    /**
     * Gets the regular expression string describing the tables to be included in node replication
     *
     * @return the regular expression string or null if not set
     */
    public String getIncludeTable()
    {
        return includeTable == null ? DEFAULT_TABLE_MATCH : (includeTable.length() == 0 ? DEFAULT_TABLE_MATCH : includeTable);
    }

    /**
     * Sets the regular expression string describing the tables to be included in node replication
     *
     * @param regex the regular expression string
     *
     * @throws {@link java.util.regex.PatternSyntaxException} if <code>regex</code> syntax is invalid
     */
    public void setIncludeTable(String regex)
    {
        if (regex != null)
        {
            Pattern.compile(regex);
        }
        this.includeTable = regex;
    }

    /**
     * Sets the user-friendly <code>name</code> for this <code>Node</code>
     *
     * @param name the name of this <code>Node</code>
     */
    public void setName(String name)
    {
        this.name = name;
    }


    public int hashCode()
    {
        return new HashCodeBuilder(3, 5)
                .append(id)
                .append(name)
                .append(uri)
                .append(available)
                .append(includeTable)
                .toHashCode();
    }

    public boolean equals(Object object)
    {
        if (object instanceof Node == false)
        {
            return false;
        }
        if (this == object)
        {
            return true;
        }
        Node rhs = (Node) object;
        return new EqualsBuilder()
                .append(id, rhs.id)
                .append(name, rhs.name)
                .append(uri, rhs.uri)
                .append(available, rhs.available)
                .append(includeTable, rhs.includeTable)
                .isEquals();
    }


    public String toString()
    {
        StringBuffer buffer = new StringBuffer();
        buffer.append("Name:\t" + getName());
        buffer.append("\n\tURL: " + getUri());
        buffer.append("\n\tInclude table: " + getIncludeTable());
        return buffer.toString();
    }

    /**
     * Sets the ID for this node
     *
     * @param id
     */
    private void setId(Long id)
    {
        this.id = id;
    }

    @Id
    @GeneratedValue
    Long id = null;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String uri;

    @Column
    private Boolean available;

    @ManyToMany(targetEntity = com.netblue.bruce.cluster.persistence.Cluster.class, fetch = FetchType.EAGER)
    @JoinTable(
            name = "NODE_CLUSTER",
            joinColumns = {@JoinColumn(name = "node_id")},
            inverseJoinColumns = {@JoinColumn(name = "cluster_id")}
    )
    @ForeignKey(name="node_id_fk")
    private Set<com.netblue.bruce.cluster.Cluster> cluster = new HashSet<com.netblue.bruce.cluster.Cluster>();

    @Lob
    private String includeTable;

    private static final String DEFAULT_TABLE_MATCH = ".*";
}
