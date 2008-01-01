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

import java.util.Set;

/**
 * Describes the interface for a <code>Node</code> in a database <code>Cluster</code>.
 *
 * @author lanceball
 */
public interface Node
{
    /**
     * The URI for this node
     * @return the URI
     */
    public String getUri();

    /**
     * Sets the URI for this node
     * @param uri
     */
    public void setUri(String uri);

    /**
     * True if the database node is currently available
     * @return true of the database node is available
     */
    public boolean isAvailable();

    /**
     * Sets whether this node is available
     * @param available
     */
    public void isAvailable(boolean available);

    /**
     * The name for this node - may be null
     * @return the name
     */
    public String getName();

    /**
     * Sets the name for this <code>Node</code>
     * @param name the name of the node
     */
    public void setName(String name);

    /**
     * Gets the set of <code>Cluster</code>s this node belongs to.
     * @return all parent <code>Cluster</code>s.  The set could be empty.
     */
    public Set<Cluster> getCluster();

    /**
     * Sets the set of <code>Cluster</code>s this node belongs to
     * @param cluster the set of parent <code>Cluster</code>s
     */
    public void setCluster(Set<Cluster> cluster);

    /**
     * Gets the regular expression string describing the tables to be
     * included in node replication
     * @return the regular expression string or null if not set
     */
    public String getIncludeTable();

    /**
     * Sets the regular expression string describing the tables to be
     * included in node replication
     * @param regex the regular expression string
     */
    public void setIncludeTable(String regex);

    /**
     * Gets the globally unique ID for this <code>Node</code>
     * @return the ID
     */
    public Long getId();
}
