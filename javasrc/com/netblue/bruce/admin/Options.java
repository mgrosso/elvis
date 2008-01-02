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

import org.kohsuke.args4j.Option;

/**
 * A simple bean class to handle all of the admin tool options
 * @author lanceball
 * @version $Id$
 */
public class Options
{
    /**
     * Represents the possible database operations
     */
    public enum Operation
    {
        INSERT, UPDATE, CLEAN_INSERT, DELETE
    }

    public enum SnapshotInitialization
    {
        MASTER, SLAVE, NONE 
    }

    @Option(name = "-url", usage = "Configuration database connection URL", metaVar = "URL")
    public void setUrl(String url)
    {
        this.url = url;
    }

    @Option(name = "-user", usage = "Configuration database username", metaVar = "USERNAME")
    public void setUsername(String username)
    {
        this.username = username;
    }

    @Option(name = "-pass", usage = "Configuration database password", metaVar = "PASSWORD")
    public void setPassword(String password)
    {
        this.password = password;
    }

    @Option(name = "-loadschema", usage = "When specified, the configuration database schema is applied to URL")
    public void setLoadSchema(boolean loadSchema)
    {
        this.loadSchema = loadSchema;
    }

    @Option(name = "-data", usage = "Cluster configuration xml data file", metaVar = "FILE")
    public void setDataFile(String dataFile)
    {
        this.dataFile = dataFile;
    }

    @Option(name = "-operation", usage = "Used with the -data option.  Can be one of INSERT, CLEAN_INSERT, " +
            "UPDATE, or DELETE.  " +
            "INSERT inserts data into the database, leaving existing rows as they are.  " +
            "CLEAN_INSERT removes any existing data in the database, replacing with new data provided.  " +
            "This is the default if not provided.  " +
            "UPDATE updates existing rows in the database.  " +
            "DELETE deletes existing rows in the database.  " +
            "This option is only used in conjunction with the -data option", metaVar = "INSERT | CLEAN_INSERT | UPDATE | DELETE")
    public void setOperation(Operation operation)
    {
        this.operation = operation;
    }

    @Option(name = "-initnodeschema", usage = "Used with the -data option.  Installs the replication " +
            "schema on each node and adds triggers to the node's replicated tables.  Applies to both " +
            "master and slave nodes in a cluster.")
    public void setInitNodes(boolean initNodes)
    {
        this.initNodes = initNodes;
    }

    @Option(name = "-initsnapshots", usage = "Used with the -data option.  Initializes each slave node's snapshot " +
            "status using either the existing master for the node's cluster, or from the slave itself. " +
            "Can be one of MASTER, SLAVE, or NONE. " +
            "MASTER examines the master database for the slave node's cluster and uses the latest row from the snapshotlog. " +
            "This option is only appropriate if no updates have occured on the master since the slave was created.  " +
            "SLAVE assumes that the slave node being initialized is a transactional backup of an existing master.  " +
            "The master may have been updated since the transactional backup was created - but that's OK.  " +
            "NONE will do nothing.  This is the default.  Use this option if the node being initialized was " +
            "created from a transactional backup of an existing slave.", metaVar = "MASTER | SLAVE | NONE")
    public void setInitSnapshots(SnapshotInitialization initSnapshots)
    {
        this.initSnapshots = initSnapshots;
    }

    @Option(name = "-usage", usage = "Prints this message")
    public void setUsage(boolean usage)
    {
        this.usage = usage;
    }

    @Option(name = "-list", usage = "Lists metadata for master and slave nodes for all clusters at URL")
    public void setList(boolean list)
    {
        this.list = list;
    }


    public String getUrl()
    {
        return url;
    }

    public String getUsername()
    {
        return username;
    }

    public String getPassword()
    {
        return password;
    }

    public String getDataFile()
    {
        return dataFile;
    }

    public boolean getList()
    {
        return list;
    }

    public Operation getOperation()
    {
        return operation;
    }

    public boolean getLoadSchema()
    {
        return loadSchema;
    }

    public boolean getUsage()
    {
        return usage;
    }

    public boolean getInitNodes()
    {
        return initNodes;
    }

    public SnapshotInitialization getInitSnapshots()
    {
        return initSnapshots;
    }

    private String url          = null;
    private String username     = null;
    private String password     = null;
    private String dataFile     = null;
    private boolean list        = false;
    private boolean loadSchema  = false;
    private boolean initNodes   = false;
    private boolean usage       = false;
    private Operation operation = Operation.INSERT;
    private SnapshotInitialization initSnapshots = SnapshotInitialization.NONE;
}
