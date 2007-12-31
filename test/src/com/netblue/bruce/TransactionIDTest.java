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

import org.apache.log4j.BasicConfigurator;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;

public class TransactionIDTest
{
    // To run with Ant (version 1.6.5???), we need this suite() method to run our tests.
    // Ant uses an JUnit 3.x runner instead of a 4.X one. See http://junit.sourceforge.net/doc/faq/faq.htm#tests_1
    public static junit.framework.Test suite()
    {
        return new junit.framework.JUnit4TestAdapter(TransactionIDTest.class);
    }

    // Test setup for this object
    @Before
    public void setUp()
    {
        // Light up log4j - root logger on the console at level==DEBUG
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure();
    }

    @SuppressWarnings({"UnusedAssignment"})
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidThrowsInvalid1()
    {
        TransactionID t = new TransactionID(TransactionID.INVALID);
        assertTrue(1 == 0); // Should be unreachable, but we should fail if we do reach it.
    }

    @SuppressWarnings({"UnusedAssignment"})
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidThrowsInvalid2()
    {
        TransactionID t = new TransactionID(-1);
        assertTrue(1 == 0); // Should be unreachable, but we should fail if we do reach it.
    }

    @SuppressWarnings({"UnusedAssignment"})
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidThrowsInvalid3()
    {
        TransactionID t = new TransactionID(TransactionID.MAXNORMAL + 1);
        assertTrue(1 == 0); // Should be unreachable, but we should fail if we do reach it.
    }

    @SuppressWarnings({"UnusedAssignment"})
    @Test(expected = NumberFormatException.class)
    public void testInvalidThrowsInvalidString()
    {
        TransactionID t = new TransactionID("hi");
        assertTrue(1 == 0); // Should be unreachable, but we should fail if we do reach it.
    }

    @Test public void testLastNormal() {
	assertEquals((new TransactionID(TransactionID.FIRSTNORMAL)).lastNormal(),
		     new TransactionID(2147483651L));
	assertEquals((new TransactionID(TransactionID.MAXNORMAL)).lastNormal(),
		     new TransactionID(2147483648L));
	assertEquals((new TransactionID(TransactionID.TWO_TO_THE_THIRTY_FIRST)).lastNormal(),
		     new TransactionID(4294967295L));
	assertEquals((new TransactionID(TransactionID.TWO_TO_THE_THIRTY_FIRST+2L)).lastNormal(),
		     new TransactionID(3L));
    }

    @Test public void testGetLong() {
        assertEquals((new TransactionID(TransactionID.BOOTSTRAP)).getLong(),TransactionID.BOOTSTRAP);
        assertEquals((new TransactionID(TransactionID.FROZEN)).getLong(),TransactionID.FROZEN);
        assertEquals((new TransactionID(TransactionID.FIRSTNORMAL)).getLong(),TransactionID.FIRSTNORMAL);
        assertEquals((new TransactionID(TransactionID.FIRSTNORMAL + 1)).getLong(),TransactionID.FIRSTNORMAL + 1);
        assertEquals((new TransactionID(TransactionID.MAXNORMAL - 1)).getLong(),TransactionID.MAXNORMAL - 1);
        assertEquals((new TransactionID(TransactionID.MAXNORMAL)).getLong(),TransactionID.MAXNORMAL);
    }

    @Test
    public void testNormal()
    {
        assertFalse((new TransactionID(TransactionID.BOOTSTRAP)).normal());
        assertFalse((new TransactionID(TransactionID.FROZEN)).normal());
        assertTrue((new TransactionID(TransactionID.FIRSTNORMAL)).normal());
        assertTrue((new TransactionID(TransactionID.FIRSTNORMAL + 1)).normal());
        assertTrue((new TransactionID(TransactionID.MAXNORMAL - 1)).normal());
        assertTrue((new TransactionID(TransactionID.MAXNORMAL)).normal());
    }

    @Test
    public void testEquals()
    {
        assertTrue((new TransactionID(TransactionID.BOOTSTRAP)).equals(new TransactionID("1")));
        assertTrue((new TransactionID(TransactionID.FROZEN)).equals(new TransactionID("2")));
        assertTrue((new TransactionID(TransactionID.FIRSTNORMAL)).equals(new TransactionID("3")));
        assertTrue((new TransactionID(TransactionID.FIRSTNORMAL + 1)).equals(new TransactionID("4")));
        assertTrue((new TransactionID(TransactionID.MAXNORMAL - 1)).equals(new TransactionID("4294967294")));
        assertTrue((new TransactionID(TransactionID.MAXNORMAL)).equals(new TransactionID("4294967295")));
        assertFalse((new TransactionID(TransactionID.BOOTSTRAP)).
		    equals(new TransactionID(TransactionID.FROZEN)));
        assertFalse((new TransactionID(TransactionID.FROZEN)).
		    equals(new TransactionID(TransactionID.FIRSTNORMAL)));
        assertFalse((new TransactionID(TransactionID.FIRSTNORMAL)).
		    equals(new TransactionID(TransactionID.FIRSTNORMAL + 1)));
    }

    @Test
    public void testCompareTo()
    {
        Random r = new Random();
        assertTrue((new TransactionID(TransactionID.BOOTSTRAP)).
		   compareTo(new TransactionID(TransactionID.FROZEN)) < 0);
        for (int i = 0; i < 10000; i++)
        {
            // Generate a random "normal" transactionID
            TransactionID t1 = null;
            while ((t1 == null) || !t1.normal())
            {
                try
                {
                    t1 = new TransactionID(r.nextLong() & 0xFFFFFFFFL);
                }
                catch (Exception e) { }
            }
            // BOOTSTRAP and FROZEN transaction IDs are less than ALL normal transaction IDs, even if
            // they are >2^31
            assertTrue((new TransactionID(TransactionID.BOOTSTRAP)).compareTo(t1) < 0);
            assertTrue((new TransactionID(TransactionID.FROZEN)).compareTo(t1) < 0);
            assertTrue(t1.compareTo(new TransactionID(TransactionID.FROZEN)) > 0);
            assertTrue(t1.compareTo(new TransactionID(TransactionID.BOOTSTRAP)) > 0);
        }
        for (int i = 0; i < 10000; i++)
        {
            // Two normal transaction IDs, within 2^31 of each other, compare like Long
            TransactionID t1 = null;
            TransactionID t2 = null;
            Long l1 = null;
            Long l2 = null;
            while ((t1 == null) || (t2 == null) | !t1.normal() || !t2.normal() || t1.equals(t2))
            {
                try
                {
                    l1 = r.nextLong() & 0xFFFFFFFFL;
                    l2 = r.nextLong() & 0xFFFFFFFFL;
                    if (Math.abs(l1 - l2) > TransactionID.TWO_TO_THE_THIRTY_FIRST)
                    {
                        continue;
                    }
                    t1 = new TransactionID(l1);
                    t2 = new TransactionID(l2);
                }
                catch (Exception e) {}
            }
            assertEquals(l1.compareTo(l2), t1.compareTo(t2));
        }
        for (int i = 0; i < 10000; i++)
        {
            // Two normal transaction IDs, more than 2^31 of each other, compare the reverse of Long
            TransactionID t1 = null;
            TransactionID t2 = null;
            Long l1 = null;
            Long l2 = null;
            while ((t1 == null) || (t2 == null) | !t1.normal() || !t2.normal() || t1.equals(t2))
            {
                try
                {
                    l1 = r.nextLong() & 0xFFFFFFFFL;
                    l2 = r.nextLong() & 0xFFFFFFFFL;
                    if (Math.abs(l1 - l2) < TransactionID.TWO_TO_THE_THIRTY_FIRST)
                    {
                        continue;
                    }
                    t1 = new TransactionID(l1);
                    t2 = new TransactionID(l2);
                }
                catch (Exception e) {}
            }
            assertEquals(0 - l1.compareTo(l2), t1.compareTo(t2));
        }
    }
}	
