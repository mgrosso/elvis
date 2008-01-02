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

import com.netblue.bruce.admin.ReplicationDatabaseBuilder;
import org.apache.log4j.Logger;
import org.dbunit.dataset.IDataSet;
import org.junit.Assert;
import org.junit.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * All unit tests that need to test against the database, with the replication schema in place should extend this class.
 * During setUp, it drops the database and recreates it.
 *
 * @author lanceball
 * @version $Id: ReplicationDatabaseBuilderTest.java 72519 2007-06-27 14:24:08Z lball $
 */
public class ReplicationDatabaseBuilderTest extends DBUnitAbstractInitializer
{
    /**
     * This is a base class named *Test.java.  JUnit will barf it a test class doesn't contain a test
     */
    @Test
    public void testSchema()
    {
        ReplicationDatabaseBuilder builder = new ReplicationDatabaseBuilder();
        final DataSource dataSource = TestDatabaseHelper.getTestDataSource();
        Connection connection = null;
        try
        {
            builder.buildDatabase(dataSource);
            connection = TestDatabaseHelper.getTestDatabaseConnection();
            final ResultSet resultSet = connection.createStatement().executeQuery("select * from replication_version");
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
            Assert.fail(e.getLocalizedMessage());
        }
        catch (IOException e)
        {
            Assert.fail(e.getLocalizedMessage());
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
                Assert.fail(e.getLocalizedMessage());
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
        return null;
    }

    /**
     * Initializes the database.  If you need to create tables, indices, etc at runtime, you can use this method to do
     * that.  If not, just make it a no-op.
     *
     * @param connection A JDBC connection with auto commit turned on
     */
    protected void setUpDatabase(Connection jdcbConnection)
    {
        // no op
    }


    private static final Logger LOGGER = Logger.getLogger(ReplicationDatabaseBuilderTest.class);
    private static final String CONFIG_XML = "config.xml";
    private static IDataSet dataSet;
}
