/*
 * Bruce - A PostgreSQL Database Replication System
 * 
 * Portions Copyright (c) 2007, Connexus Corporation
 * 
 * Permission to use, copy, modify, and distribute this software and its
 * documentation for any purpose, without fee, and without a written 
 * agreement is hereby granted, provided that the above copyright notice and
 * this paragraph and the following two paragraphs appear in all copies.
*/
package com.netblue.bruce.profile;

import com.netblue.bruce.PgJDBCBench;
import com.netblue.bruce.ReplicationDaemon;
import com.netblue.bruce.TestDatabaseHelper;
import com.netblue.bruce.admin.Main;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Sets up 4 databases: bruce_config, bruce_master, bruce_slave_1, and bruce_slave_2.  It then configures a
 * cluster with those databases, and runs the daemon.  This class can be used to easily setup tests for profiling.
 * Point the profiler at this class and fire it up.  Then run whatever stress tests against the server that you like.
 * This class will be most useful when testing from an IDE since it depends on the test environment.
 * @author lanceball
 * @version $Id$
 */
public class TestDaemon
{
    public static void main(String[] args) throws SQLException, IOException
    {
        // Initialize our postgres properties.  They are retrieved from System.properties
        System.setProperty(TestDatabaseHelper.POSTGRESQL_ADMIN_URL_KEY, TestDatabaseHelper.buildUrl("postgres"));

        // Drop and create the databases we'll need for testing.
        // All DBs are on the same PostgreSQL instance
        final DataSource adminSource = TestDatabaseHelper.getAdminDataSource();
        final Connection adminConnection = adminSource.getConnection();
        final Statement adminStatement = adminConnection.createStatement();

        TestDatabaseHelper.executeAndLogSupressingExceptions(adminStatement, "drop database bruce_config");
        TestDatabaseHelper.executeAndLogSupressingExceptions(adminStatement, "drop database bruce_master");
        TestDatabaseHelper.executeAndLogSupressingExceptions(adminStatement, "drop database bruce_slave_1");
        TestDatabaseHelper.executeAndLogSupressingExceptions(adminStatement, "drop database bruce_slave_2");

        TestDatabaseHelper.executeAndLog(adminStatement, "create database bruce_config");
        TestDatabaseHelper.executeAndLog(adminStatement, "create database bruce_master");
        TestDatabaseHelper.executeAndLog(adminStatement, "create database bruce_slave_1");
        TestDatabaseHelper.executeAndLog(adminStatement, "create database bruce_slave_2");

        // Now initialize the master and each slave with the schema used in PgJDBCBench
        PgJDBCBench.main(new String[]{
                "-uri", TestDatabaseHelper.buildUrl("bruce_master"), "-init"
        });
        PgJDBCBench.main(new String[]{
                "-uri", TestDatabaseHelper.buildUrl("bruce_slave_1"), "-init"
        });
        PgJDBCBench.main(new String[]{
                "-uri", TestDatabaseHelper.buildUrl("bruce_slave_2"), "-init"
        });

        // Now use the admin tool to initialize the configuration database and load the cluster metadata
        Main.main(new String[]{ // Main.main() - how goofy
                "-url", TestDatabaseHelper.buildUrl("bruce_config"),
                "-data", "sample/config.xml", // hmm - should this be someplace besides "sample"?
                "-initsnapshots", "MASTER",
                "-operation", "CLEAN_INSERT",
                "-loadschema", "-initnodeschema"
        });

        // Now run the daemon
        ReplicationDaemon daemon = new ReplicationDaemon();
        daemon.loadCluster("ClusterOne");
        daemon.run();

        // Now run the benchmark tests
        PgJDBCBench.main(new String[]{
                "-uri", TestDatabaseHelper.buildUrl("bruce_master")
        });
    }
}
