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

import java.util.ArrayList;

/**
 * Defines an interface that can interpret a {@link Node}'s replication patterns and provide
 * a list of schemas and tables to be replicated.  Implementations of this interface will need
 * to have access to the node's database to query database metadata.
 * @author lanceball
 * @version $Id: ReplicationStrategy.java 72519 2007-06-27 14:24:08Z lball $
 */
public interface ReplicationStrategy
{
    /**
     * Gets the list of database tables that <code>node</code> should replicate for <code>schema</code>.  If
     * <code>schema</code> is null, this method should return the list of all tables in all schemas to be replicated
     * by this node, using the convention <code>SCHEMA.TABLE</code> for the results.
     * @param node the replicating <code>Node</code>
     * @param schema filters so results are only returned for the given database schema
     * @return a list of table names that should be replicated for the <code>node</code>
     */
    public ArrayList<String> getTables(Node node, String schema);
}
