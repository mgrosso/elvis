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
 * Installs the configuration schema on a database via a user-supplied <code>BasicDataSource</code>. WARNING:  This
 * class is "dumb".  If you install the configuration schema on a database that already has a configuration schema
 * installed, you will lose any data that may have existed in those original tables.  You have been warned.
 *
 * @author lanceball
 * @version $Id: ConfigurationDatabaseBuilder.java 72519 2007-06-27 14:24:08Z lball $
 */
public class ConfigurationDatabaseBuilder extends DatabaseBuilder
{


    /**
     * Reads a text file, splitting it into SQL statements (delimited by ';').
     *
     * @return an array of sql statements
     */
    public String[] getSqlStrings()
    {
        ArrayList<String> statements = new ArrayList<String>();
        statements.add("create schema bruce;"); // hibernate won't create the schema for us

        try
        {
            // schema ddl is in a .sql file at the root of bruce.jar
            final String ddlString = readFileResource("cluster-ddl.sql").toString();
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
