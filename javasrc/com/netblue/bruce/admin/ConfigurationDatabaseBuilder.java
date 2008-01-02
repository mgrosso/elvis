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
package com.netblue.bruce.admin;

import org.apache.log4j.Logger;

/**
 * Installs the configuration schema on a database via a user-supplied <code>BasicDataSource</code>. WARNING:  This
 * class is "dumb".  If you install the configuration schema on a database that already has a configuration schema
 * installed, you will lose any data that may have existed in those original tables.  You have been warned.
 *
 * @author lanceball
 * @version $Id$
 */
public class ConfigurationDatabaseBuilder extends DatabaseBuilder
{


    /**
     * Reads a text file, splitting it into SQL statements (delimited by ';').
     *
     * @return an array of sql statements
     */
    public String[] getSqlStrings() {
	return configurationDDL;
    }

    private static final Logger logger = Logger.getLogger(ConfigurationDatabaseBuilder.class);
    private static final String[] configurationDDL = {
	"create schema bruce", // If not already present
	"alter table bruce.NODE_CLUSTER drop constraint cluster_id_fk",
	"alter table bruce.NODE_CLUSTER drop constraint node_id_fk",
	"alter table bruce.YF_CLUSTER drop constraint master_node_id_fk",
	"drop sequence bruce.hibernate_sequence",
	"drop table bruce.NODE_CLUSTER",
	"drop table bruce.YF_CLUSTER",
	"drop table bruce.YF_NODE",
	"create table bruce.NODE_CLUSTER ( node_id int8 not null, cluster_id int8 not null, primary key (node_id, cluster_id))",
	"create table bruce.YF_CLUSTER ( id int8 not null, name text, master_node_id int8 unique, primary key (id))",
	"create table bruce.YF_NODE "+
	"           ( id int8 not null, "+
	"             available bool, "+
	"             includeTable text, "+
	"             name text not null, "+
	"             uri text not null, "+
	"             primary key (id))",
	"alter table bruce.NODE_CLUSTER add constraint cluster_id_fk foreign key (cluster_id) references bruce.YF_CLUSTER",
	"alter table bruce.NODE_CLUSTER add constraint node_id_fk foreign key (node_id) references bruce.YF_NODE",
	"create index yf_cluster_name_idx on bruce.YF_CLUSTER (name)",
	"alter table bruce.YF_CLUSTER add constraint master_node_id_fk foreign key (master_node_id) references bruce.YF_NODE",
	"create sequence bruce.hibernate_sequence"
    };
}
