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

import org.apache.log4j.*;
import org.dbunit.DatabaseUnitException;
import org.dbunit.database.DatabaseDataSourceConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.junit.AfterClass;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.BeforeClass;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Base test class that initializes an IDatabaseConnection for dbunit based testing. Abstract method {@link
 * #getTestDataSet()} must be implemented by subclasses in order to setup the appropriate data for their tests.
 *
 * @author lanceball
 */
public abstract class DBUnitAbstractInitializer
{

    @BeforeClass
    public static void initializeDatabaseResources()
    {
        try
        {
            // Make sure the database exists
            TestDatabaseHelper.createTestDatabase();

            // The config package uses hibernate for its data model.  Export the schema.
  //          TestDatabaseHelper.createConfigSchema();

            // Subclasses use a JDBC connection to setup their database.  We'll keep just one
            jdcbConnection = TestDatabaseHelper.getTestDatabaseConnection();
            jdcbConnection.setAutoCommit(true);

            // We use DataSourceConnection to import test data.  Again, just keep one
            testDataDS = new DatabaseDataSourceConnection(TestDatabaseHelper.getTestDataSource());
            testDataDS.getConfig().setFeature("http://www.dbunit.org/features/qualifiedTableNames", true);
        }
        catch (SQLException e)
        {
            e.printStackTrace();
            fail("Unable to create database connection to test database: " + e.getLocalizedMessage());
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Unexpected exception setting up test: " + e.getLocalizedMessage());
        }
    }

    @AfterClass
    public static void releaseDatabaseResources()
    {
        try
        {
            jdcbConnection.close();
            testDataDS.close();
        }
        catch (SQLException e)
        {
            LOGGER.warn("Unable to close JDBC Connection.  Exception message: " + e.getLocalizedMessage());
        }
        TestDatabaseHelper.closeTestDataSource();
        TestDatabaseHelper.closeAdminDataSource();
    }

    /**
     * Template setUp method that initializes the test database connection, and delegates to subclasses to provide the
     * data set.
     */
    @Before
    public void setUp()
    {
        try
        {
            Statement statement = jdcbConnection.createStatement();
            TestDatabaseHelper.resetBruceSchema(statement);
            statement.close();

            // Setup the database with data set from the subclasses
            setUpDatabase(jdcbConnection);

            // Give subclasses a chance to add test data to the database
            final IDataSet dataSet = getTestDataSet();
            if (dataSet != null)
            {
                DatabaseOperation.CLEAN_INSERT.execute(testDataDS, dataSet);
            }
        }
        catch (SQLException e)
        {
            e.printStackTrace();
            fail("Unable to create database connection: " + e.getLocalizedMessage());
        }
        catch (DatabaseUnitException e)
        {
            e.printStackTrace();
            fail("Unable to insert test data: " + e.getLocalizedMessage());
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Exception setting up test: " + e.getLocalizedMessage());
        }

    }

    /**
     * Get the data set under test.  The {@link #setUp()} method will call use this data to perform a {@link
     * DatabaseOperation.CLEAN_INSERT} on the database.
     *
     * @return
     */
    protected abstract IDataSet getTestDataSet();

    /**
     * Initializes the database.  If you need to create tables, indices, etc at runtime, you can use this method to do
     * that.  If not, just make it a no-op.
     *
     * @param connection A JDBC connection with auto commit turned on
     */
    protected abstract void setUpDatabase(Connection jdcbConnection);

    protected static final Logger LOGGER = Logger.getLogger(DBUnitAbstractInitializer.class);
    private static Connection jdcbConnection;
    private static DatabaseDataSourceConnection testDataDS;
}
