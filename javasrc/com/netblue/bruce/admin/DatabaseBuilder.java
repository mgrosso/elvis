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

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.log4j.Logger;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A simple utility class to ease executing groups of database statements from a file.
 * @author lanceball
 * @version $Id: DatabaseBuilder.java 72519 2007-06-27 14:24:08Z lball $
 */
public abstract class DatabaseBuilder
{
    /**
     * Executes the SQL statements from {@link #getSqlStrings()} onto <code>dataSource</code>.
     * Will drop and recreate the schema on the target
     * <code>dataSource</code> so use with caution.
     *
     * @param dataSource the target location for the schema installation
     *
     * @throws java.io.IOException If the schema resource file cannot be read
     * @throws java.sql.SQLException If there is a problem connecting to the database.
     */
    public void buildDatabase(final DataSource dataSource) throws IOException, SQLException
    {
        // get a connection to the database
        Connection connection = dataSource.getConnection();
        if (connection == null)
        {
            throw new RuntimeException("Unable to obtain a connection from datasource: " + dataSource.toString());
        }
        connection.setAutoCommit(true);

        // get all of the statments we'll apply to the database
        String schemaStatements[] = getSqlStrings();

        Statement statement = connection.createStatement();
        for (String sqlcmd : schemaStatements)
        {
            try
            {
                LOGGER.info(sqlcmd);
                statement.execute(sqlcmd);
            }
            catch (SQLException e)
            {
                LOGGER.warn("Exception executing SQL", e);
            }
        }
        statement.close();
    }

    /**
     * Implement this method to provide an array of SQL statements to {@link #buildDatabase(javax.sql.DataSource)}
     * @return an array of SQL statements
     */
    public abstract String[] getSqlStrings();

    /**
     * Utility method to read a resource from the ClassLoader into a ByteArrayOutputStream
     * @param resourceName the name of the resource to read
     * @return An output stream containing the contents of the resource
     * @throws IOException
     */
    protected ByteArrayOutputStream readFileResource(String resourceName) throws IOException
    {
        final ClassLoader loader = ConfigurationDatabaseBuilder.class.getClassLoader();
        final ByteArrayOutputStream ddl = new ByteArrayOutputStream();
        final URL resource = loader.getResource(resourceName);
        LOGGER.info("DDL resource path: " + resource);
        final InputStream inputStream = resource.openStream();

        // iterate over the stream and break it into discrete sql statements
        if (inputStream != null)
        {
            int b = inputStream.read();
            while (b != -1)
            {
                ddl.write(b);
                b = inputStream.read();
            }
        }
        else
        {
            throw new RuntimeException(new StringBuilder().append("Unable to load resource: ").append(resourceName).toString());
        }
        return ddl;
    }

    /**
     * Utility method to obtain a DataSource given the URL, username and password options
     * @param url the database url
     * @param username the database username, may be null
     * @param password the database password, may be null
     * @return a data source for the options provided
     */
    public static BasicDataSource makeDataSource(final String url, final String username, final String password)
    {
        final BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName(System.getProperty("bruce.jdbcDriverName", "org.postgresql.Driver"));
        dataSource.setValidationQuery(System.getProperty("bruce.poolQuery", "select now()"));
        dataSource.setUrl(url);
        if (password != null)
        {
            dataSource.setPassword(password);
        }
        if (username != null)
        {
            dataSource.setUsername(username);
        }
        return dataSource;
    }

    protected static final Logger LOGGER = Logger.getLogger(DatabaseBuilder.class);
    protected static final String DDL_DELIMITER = ";";
}
