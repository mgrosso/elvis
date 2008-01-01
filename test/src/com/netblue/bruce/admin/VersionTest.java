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

import com.netblue.bruce.ReplicationTest;
import com.netblue.bruce.TestDatabaseHelper;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests {@link com.netblue.bruce.admin.Version}
 * @author lanceball
 * @version $Id$
 */
public class VersionTest extends ReplicationTest
{

    @Test
    public void testVersionNumber()
    {
        final int major = Version.getMajorVersionNumber();
        final int minor = Version.getMinorVersionNumber();
        final int patch = Version.getPatchVersionNumber();
        final String name = Version.getVersionName();

        Assert.assertEquals("Unexpected major version number", 0, major);
        Assert.assertEquals("Unexpected minor version number", 5, minor);
        Assert.assertEquals("Unexpected patch version number", 0, patch);
        Assert.assertEquals("Unexpected patch version number", "Replication Pre-release Alpha", name);
    }

    @Test
    public void testIsSameVersion()
    {
        Assert.assertTrue("Schema and code version conflict", Version.isSameVersion(TestDatabaseHelper.getTestDataSource()));
        Assert.assertTrue("Schema and code version conflict", Version.isSameVersion(0, 5, 0, "Replication Pre-release Alpha"));
    }

    @Test
    public void testIsSameVersionForNonExistentDatabase()
    {
        Assert.assertFalse("Version.isSameVersion() returned true for a non-existent DB",
                           Version.isSameVersion(
                                   TestDatabaseHelper.createDataSource(
                                           "jdbc:postgresql://localhost:5432/some_db_that_does_not_exist?user=bruce&password=bruce")));
    }
}
