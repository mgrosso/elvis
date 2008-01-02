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

import com.netblue.bruce.TestDatabaseHelper;
import com.netblue.bruce.cluster.persistence.HibernatePersistenceTest;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests the {@link com.netblue.bruce.cluster.RegExReplicationStrategy} class
 * @author lanceball
 * @version $Id$
 */
public class RegExReplicationStrategyTest extends HibernatePersistenceTest
{
    @Before
    public void setUp()
    {
        super.setUp();
        SessionFactory sessionFactory = getSessionFactory();
        Session session = sessionFactory.openSession();
        masterOne = getNodeByName(session, "Cluster 0 - Primary master");
        slaveOne = getNodeByName(session, "Cluster 0 - Slave Uno");
        slaveTwo = getNodeByName(session, "Cluster 0 - Slave Dos");
        masterTwo = getNodeByName(session, "Cluster 1 - Master");
        session.close();
        sessionFactory.close();
    }

    @SuppressWarnings("unchecked")
    private static Node getNodeByName(final Session session, final String nodeName)
    {
        Criteria nodeCriteria = session.createCriteria(Node.class).add(Restrictions.eq("name", nodeName));
        List<Node> nodes = nodeCriteria.list();
        assertEquals(1, nodes.size());
        return nodes.get(0);
    }

    @Test
    public void testGetTablesForSchema()
    {
        RegExReplicationStrategy replicationStrategy = new RegExReplicationStrategy(TestDatabaseHelper.getTestDataSource());

        // Get the master tables and validate
        ArrayList<String> tables = replicationStrategy.getTables(masterOne, REGEXTEST_SCHEMA);
        assertEquals("Unexpected number of tables returned from ReplicationStrategey#getTables(): " + tables, 2, tables.size());
        assertTrue("Unexpected table returned", tables.contains("regextest.orange"));
        assertTrue("Unexpected table returned", tables.contains("regextest.blue"));

        // Get the slave tables and validate.  Tests
        tables = replicationStrategy.getTables(slaveOne, REGEXTEST_SCHEMA);
        assertEquals("Unexpected number of tables returned from ReplicationStrategey#getTables(): " + tables, 1, tables.size());
        assertTrue("Unexpected table returned", tables.contains("regextest.green"));

        tables = replicationStrategy.getTables(slaveTwo, REGEXTEST_SCHEMA);
        assertEquals("Unexpected number of tables returned from ReplicationStrategey#getTables(): " + tables, 3, tables.size());
        assertTrue("Unexpected table returned", tables.contains("regextest.blue"));
        assertTrue("Unexpected table returned", tables.contains("regextest.orange"));
        assertTrue("Unexpected table returned", tables.contains("regextest.green"));
    }

    @Test
    public void testGetTablesWithoutSchema()
    {
        RegExReplicationStrategy replicationStrategy = new RegExReplicationStrategy(TestDatabaseHelper.getTestDataSource());

        // Get the master tables and validate
        ArrayList<String> tables = replicationStrategy.getTables(masterOne, null);
        assertEquals("Unexpected number of tables returned from ReplicationStrategey#getTables(): " + tables, 3, tables.size());
        assertTrue("Unexpected table returned", tables.contains("regextest.orange"));
        assertTrue("Unexpected table returned", tables.contains("regextest.blue"));
        assertTrue("Unexpected table returned", tables.contains("regextest_s2.orange2"));

        // Get the slave tables and validate.  Tests
        tables = replicationStrategy.getTables(slaveOne, null);
        assertEquals("Unexpected number of tables returned from ReplicationStrategey#getTables(): " + tables, 2, tables.size());
        assertTrue("Unexpected table returned", tables.contains("regextest.green"));
        assertTrue("Unexpected table returned", tables.contains("regextest_s2.green2"));

        tables = replicationStrategy.getTables(slaveTwo, null);
        assertEquals("Unexpected number of tables returned from ReplicationStrategey#getTables(): " + tables, 6, tables.size());
        assertTrue("Unexpected table returned", tables.contains("regextest.blue"));
        assertTrue("Unexpected table returned", tables.contains("regextest.orange"));
        assertTrue("Unexpected table returned", tables.contains("regextest.green"));
        assertTrue("Unexpected table returned", tables.contains("regextest_s2.blue2"));
        assertTrue("Unexpected table returned", tables.contains("regextest_s2.orange2"));
        assertTrue("Unexpected table returned", tables.contains("regextest_s2.green2"));
    }

    @Test
    public void testGetTablesWithDefaultValuesWithSchema()
    {
        RegExReplicationStrategy replicationStrategy = new RegExReplicationStrategy(TestDatabaseHelper.getTestDataSource());

        ArrayList<String> tables = replicationStrategy.getTables(masterTwo, REGEXTEST_SCHEMA);
        assertEquals("Unexpected number of tables returned from ReplicationStrategey#getTables(): " + tables, 4, tables.size());
        assertTrue("Unexpected table returned", tables.contains("regextest.blue"));
        assertTrue("Unexpected table returned", tables.contains("regextest.orange"));
        assertTrue("Unexpected table returned", tables.contains("regextest.green"));
        assertTrue("Unexpected table returned", tables.contains("regextest.red"));
    }

    @Test
    public void testGetTablesWithDefaultValuesWithoutSchema()
    {
        RegExReplicationStrategy replicationStrategy = new RegExReplicationStrategy(TestDatabaseHelper.getTestDataSource());

        ArrayList<String> tables = replicationStrategy.getTables(masterTwo, null);
        assertEquals("Unexpected number of tables returned from ReplicationStrategey#getTables(): " + tables, 8, tables.size());
        assertTrue("Unexpected table returned", tables.contains("regextest.blue"));
        assertTrue("Unexpected table returned", tables.contains("regextest.orange"));
        assertTrue("Unexpected table returned", tables.contains("regextest.green"));
        assertTrue("Unexpected table returned", tables.contains("regextest.red"));
        assertTrue("Unexpected table returned", tables.contains("regextest_s2.blue2"));
        assertTrue("Unexpected table returned", tables.contains("regextest_s2.orange2"));
        assertTrue("Unexpected table returned", tables.contains("regextest_s2.green2"));
        assertTrue("Unexpected table returned", tables.contains("regextest_s2.red2"));
    }

    /**
     * no-op.
     *
     * @param connection A JDBC connection with auto commit turned on
     */
    protected void setUpDatabase(Connection jdcbConnection)
    {
        super.setUpDatabase(jdcbConnection);
        TestDatabaseHelper.applyDDLFromFile(jdcbConnection, TestDatabaseHelper.getSchemaFile("unit-tests-ddl.sql"));
    }

    private static final String REGEXTEST_SCHEMA = "regextest";
    private Node masterOne;
    private Node masterTwo;
    private Node slaveOne;
    private Node slaveTwo;
}
