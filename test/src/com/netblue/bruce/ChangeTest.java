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

import org.junit.Assert;
import org.junit.Test;
// -----------------------------------------------
// ${CLASS}
// -----------------------------------------------

/**
 * @author rklahn
 */
public class ChangeTest
{
    private final TransactionID t = new TransactionID(1229469);
    private final String table = "public.test1";
    private final String info = "id:23:MzE=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:NDI=:!";
    private final Change c1 = new Change(101,t,Change.INSERT,table,info);
    private final Change c2 = new Change(102,t,Change.INSERT,table,info);

    // To run with Ant (version 1.6.5???), we need this suite() method to run our tests.
    // Ant uses an JUnit 3.x runner instead of a 4.X one. See http://junit.sourceforge.net/doc/faq/faq.htm#tests_1
    public static junit.framework.Test suite()
    {
        return new junit.framework.JUnit4TestAdapter(ChangeTest.class);
    }

    @Test
    public void testEquals()
    {
        Assert.assertTrue(c1.equals(c1));
        Assert.assertTrue(c2.equals(c2));
        Assert.assertFalse(c1.equals(c2));
    }

    @Test
    public void testEqualsNotChange()
    {
        try
        {
            c1.equals(1); // Better throw ClassCastException
            Assert.fail();
        }
        catch (ClassCastException c)
        {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testCompareTo()
    {
        Assert.assertEquals(c1.compareTo(c2),-1);
        Assert.assertEquals(c2.compareTo(c1),1);
        Assert.assertEquals(c1.compareTo(c1),0);
        Assert.assertEquals(c2.compareTo(c2),0);
    }

    @Test
    public void testCompareToNotChange()
    {
        try
        {
            c1.compareTo(1); // Better throw ClassCastException
            Assert.fail();                                                                
        }
        catch (ClassCastException c)
        {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testGetters()
    {
        Assert.assertEquals(c1.getXactionID(),t);
        Assert.assertEquals(c1.getCmdType(),Change.INSERT);
        Assert.assertEquals(c1.getTableName(),table);
        Assert.assertEquals(c1.getInfo(),info);
    }
}
