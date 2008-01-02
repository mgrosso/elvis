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
package com.netblue.bruce;

import org.apache.log4j.Logger;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.XmlDataSet;
import org.junit.Assert;
import static org.junit.Assert.fail;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * All unit tests that need to test against the database, with the replication schema in place should extend this class.
 * When the test class is initialized, it drops and recreates the test database.  Before each test is run, it drops and
 * recreates the bruce schema.
 *
 * @author lanceball
 * @version $Id$
 */
public class ReplicationTest extends DBUnitAbstractInitializer
{

    public static final File SCHEMA_REPLICATION_DDL_SQL = TestDatabaseHelper.getSchemaFile("replication-ddl.sql");
    public static final File CLUSTER_CONFIGURATION_DDL_SQL = TestDatabaseHelper.getSchemaFile("cluster-ddl.sql");

    /**
     * This is a base class named *Test.java.  JUnit will barf it a test class doesn't contain a test
     */
    @Test
    public void testSchema()
    {
        Connection connection = null;
        try
        {
            connection = TestDatabaseHelper.getTestDatabaseConnection();
            final ResultSet resultSet = connection.createStatement().executeQuery("select * from bruce.replication_version");
            resultSet.next();
            int major = resultSet.getInt(1);
            int minor = resultSet.getInt(2);
            int patch = resultSet.getInt(3);
            String name = resultSet.getString(4).trim();
            Assert.assertEquals("Unexpected schema name", "Replication 1.0 release", name);
            Assert.assertEquals("Unexpected major version", 1, major);
            Assert.assertEquals("Unexpected minor version", 0, minor);
            Assert.assertEquals("Unexpected patch version", 0, patch);

        }
        catch (SQLException e)
        {
            fail(e.getLocalizedMessage());
        }
        finally
        {
            try
            {
                if (connection != null)
                {
                    connection.close();
                }
            }
            catch (SQLException e)
            {
                fail(e.getLocalizedMessage());
            }
        }
    }

    /**
     * Get the data set under test.  The {@link #setUp()} method will call use this data to perform a {@link
     * org.dbunit.operation.DatabaseOperation.CLEAN_INSERT} on the database.
     *
     * @return
     */
    protected IDataSet getTestDataSet()
    {
        if (clusterConfigDataSet == null)
        {
            try
            {
                clusterConfigDataSet = new XmlDataSet(new FileInputStream(clusterConfigDataFile));
            }
            catch (Exception e)
            {
                e.printStackTrace();
                Assert.fail("Cannot load test dataset:  " + e.getLocalizedMessage());
            }
        }
        return clusterConfigDataSet;
    }

    /**
     * Initializes the database.  If you need to create tables, indices, etc at runtime, you can use this method to do
     * that.  If not, just make it a no-op.
     *
     * @param connection A JDBC connection with auto commit turned on
     */
    protected void setUpDatabase(Connection connection)
    {
        TestDatabaseHelper.applyDDLFromFile(connection, SCHEMA_REPLICATION_DDL_SQL);
        TestDatabaseHelper.applyDDLFromFile(connection, CLUSTER_CONFIGURATION_DDL_SQL);
    }

    private static IDataSet clusterConfigDataSet;
    private static final Logger LOGGER = Logger.getLogger(ReplicationTest.class);
    private final File clusterConfigDataFile = TestDatabaseHelper.getDataFile("config.xml");
    public static final String CLUSTER_NAME = "Cluster Un";
}
