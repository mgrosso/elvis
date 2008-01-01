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

import org.apache.log4j.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Implements the {@link com.netblue.bruce.cluster.ReplicationStrategy} interface by interpreting the {@link Node}'s
 * include and exclude properties as regular expressions.
 *
 * @author lanceball
 * @version $Id: RegExReplicationStrategy.java 72519 2007-06-27 14:24:08Z lball $
 * @see Node#getIncludeTable() 
 */
public class RegExReplicationStrategy implements ReplicationStrategy
{
    /**
     * Create a new instance with <code>dataSource</code> as the database to be replicated
     *
     * @param dataSource
     */
    public RegExReplicationStrategy(DataSource dataSource)
    {
        this.dataSource = dataSource;
    }

    /**
     * Gets the list of database tables that <code>node</code> should replicate for <code>schema</code>.  If
     * <code>schema</code> is null, this method should return the list of all tables in all schemas to be replicated by
     * this node, using the convention <code>SCHEMA.TABLE</code> for the results.
     *
     * @param node the replicating <code>Node</code>
     * @param schema filters so results are only returned for the given database schema
     *
     * @return a list of table names that should be replicated for the <code>node</code>
     */
    public ArrayList<String> getTables(Node node, String schema)
    {
        // The final list of tables we'll return
        ArrayList<String> tables = new ArrayList<String>();

        Connection connection = null;
        try
        {
            connection = this.dataSource.getConnection();
            final DatabaseMetaData metaData = connection.getMetaData();
            final ResultSet resultSet = metaData.getTables(null, schema, "%", TABLE_TYPES);
            while (resultSet.next())
            {
                final String schemaName = resultSet.getString(2);
                final String tableName = resultSet.getString(3);
                final String qualifiedTableName = new StringBuilder().append(schemaName).append(".").append(tableName).toString();
                if (!isIgnoredSchema(schemaName) && isTableMatch(qualifiedTableName, node))
                {
                    tables.add(qualifiedTableName);
                }
            }
        }
        catch (SQLException e)
        {
            LOGGER.error("Cannot get connection to slave database", e);
        }
        finally
        {
            try
            {
                if (connection != null && !connection.isClosed())
                {
                    connection.close();
                }
            }
            catch (SQLException e)
            {
                LOGGER.error(e);
            }
        }
        return tables;
    }

    private boolean isTableMatch(final String qualifiedTableName, final Node node) throws SQLException
    {
        // Include rules always take precedence over exclude rules.  If no match, the table is included
        return Pattern.matches(node.getIncludeTable(), qualifiedTableName);
    }


    private boolean isIgnoredSchema(final String schemaName)
    {
        return schemaName.equalsIgnoreCase("pg_catalog") ||
                schemaName.equalsIgnoreCase("information_schema") ||
                schemaName.equalsIgnoreCase("pg_toast") ||
                schemaName.equalsIgnoreCase("bruce");  // By default, we won't replicate things in the bruce schema
    }


    private DataSource dataSource;
    private static final Logger LOGGER = Logger.getLogger(RegExReplicationStrategy.class);
    private static final String[] TABLE_TYPES = new String[]{"TABLE"};
}
