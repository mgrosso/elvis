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

import org.apache.log4j.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Simple bean class to represent a version number
 * @author lanceball
 * @version $Id: Version.java 72519 2007-06-27 14:24:08Z lball $
 */
public class Version
{
    public static int getMajorVersionNumber()
    {
        return MAJOR_VERSION;
    }

    public static int getMinorVersionNumber()
    {
        return MINOR_VERSION;
    }

    public static int getPatchVersionNumber()
    {
        return PATCH_VERSION;
    }

    public static String getVersionName()
    {
        return NAME;
    }

    public static boolean isSameVersion(DataSource dataSource)
    {
        boolean sameVersion = false;
        Connection connection = null;
        Statement statement = null;
        try
        {
            connection = dataSource.getConnection();
            statement    = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(VERSION_QUERY);
            resultSet.next();
            final int major = resultSet.getInt(MAJOR_COLUMN);
            final int minor = resultSet.getInt(MINOR_COLUMN);
            final int patch = resultSet.getInt(PATCH_COLUMN);
            final String name = resultSet.getString(NAME_COLUMN).trim();
            sameVersion = Version.isSameVersion(major, minor, patch, name);
        }
        catch (SQLException e)
        {
            LOGGER.error(e);
            sameVersion = false;
        }
        finally
        {
            try
            {
                statement.close();
                connection.close();
            }
            catch (SQLException e)
            {
                LOGGER.error("Cannot close database connection", e);
            }
        }
        return sameVersion;
    }

    public static boolean isSameVersion(int major, int minor, int patch, String name)
    {
        return MAJOR_VERSION == major &&
               MINOR_VERSION == minor &&
               PATCH_VERSION == patch &&
               NAME.equals(name);
    }

    // We don't need no stinkin instances
    private Version(){}

    private static final int MAJOR_VERSION = 0;
    private static final int MINOR_VERSION = 5;
    private static final int PATCH_VERSION = 0;
    private static final String NAME = "Replication Pre-release Alpha";
    static final String VERSION_QUERY = "select * from bruce.replication_version";
    private static final Logger LOGGER = Logger.getLogger(Version.class);
    private static final String MAJOR_COLUMN = "MAJOR";
    private static final String MINOR_COLUMN = "MINOR";
    private static final String PATCH_COLUMN = "PATCH";
    private static final String NAME_COLUMN = "NAME";
}
