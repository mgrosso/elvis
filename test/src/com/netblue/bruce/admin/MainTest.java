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

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * Tests the Main class for the admin utility
 * @author lanceball
 * @version $Id: MainTest.java 72519 2007-06-27 14:24:08Z lball $
 */
public class MainTest
{
    /**
     * Tests that we get the usage message to System.out when the -usage option is supplied
     */
    @Test
    public void testUsage()
    {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream outStream = new PrintStream(outBytes);
        System.setOut(outStream);
        Main.main(new String[] { "-usage" });

        String output = outBytes.toString();
        System.out.println(output);
        Assert.assertTrue(output.contains("-data FILE"));
        Assert.assertTrue(output.contains("-list"));
        Assert.assertTrue(output.contains("-loadschema"));
        Assert.assertTrue(output.contains("-operation INSERT | CLEAN_INSERT | UPDATE | DELETE"));
        Assert.assertTrue(output.contains("-pass PASSWORD"));
        Assert.assertTrue(output.contains("-url"));
        Assert.assertTrue(output.contains("-usage"));
        Assert.assertTrue(output.contains("-initnodes"));
        Assert.assertTrue(output.contains("-user USERNAME"));
    }

    /**
     * Tests that we get the usage message to System.err when to URL is provided
     */
    @Test
    public void testNoUrl()
    {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream outStream = new PrintStream(outBytes);
        System.setErr(outStream);
        Main.main(new String[] { "-loadschema" });

        String output = outBytes.toString();
        System.out.println(output);
        Assert.assertTrue(output.contains("-data FILE"));
        Assert.assertTrue(output.contains("-list"));
        Assert.assertTrue(output.contains("-loadschema"));
        Assert.assertTrue(output.contains("-operation INSERT | CLEAN_INSERT | UPDATE | DELETE"));
        Assert.assertTrue(output.contains("-pass PASSWORD"));
        Assert.assertTrue(output.contains("-url"));
        Assert.assertTrue(output.contains("-usage"));
        Assert.assertTrue(output.contains("-initnodes"));
        Assert.assertTrue(output.contains("-user USERNAME"));
    }
}
