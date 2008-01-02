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

import java.io.IOException;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * Installs the replication schema
 * @author lanceball
 * @version $Id$
 */
public class ReplicationDatabaseBuilder extends DatabaseBuilder
{
    /**
     * Implement this method to provide an array of SQL statements to {@link #buildDatabase(javax.sql.DataSource)}
     *
     * @return an array of SQL statements
     */
    public String[] getSqlStrings()
    {
        ArrayList<String> statements = new ArrayList<String>();

        try
        {
            // schema ddl is in a .sql file at the root of bruce.jar
            final String ddlString = readFileResource("replication-ddl.sql").toString();
            final StringTokenizer tokenizer = new StringTokenizer(ddlString, DDL_DELIMITER);
            while (tokenizer.hasMoreTokens())
            {
                statements.add(tokenizer.nextToken());
            }
        }
        catch (IOException e)
        {
            LOGGER.error("Cannot load SQL strings", e);
        }
        return statements.toArray(new String[statements.size()]);
    }
}
