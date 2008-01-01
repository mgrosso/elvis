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
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.TreeSet;
// -----------------------------------------------
// ${CLASS}
// -----------------------------------------------

/**
 * @author rklahn
 */
public class TransactionTest
{
    public static final Snapshot s3 = new Snapshot(new TransactionID(1231408),new TransactionID(1231408),new TransactionID(1231409),"");
    public static final Snapshot s4 = new Snapshot(new TransactionID(1231410),new TransactionID(1231410),new TransactionID(1231414),"1231410");
    public static final Snapshot s5 = new Snapshot(new TransactionID(1231411),new TransactionID(1231410),new TransactionID(1231418),"1231410");
    public static final Snapshot s6 = new Snapshot(new TransactionID(1231418),new TransactionID(1231418),new TransactionID(1231419),"");
    public static final Snapshot s7 = new Snapshot(new TransactionID(1231421),new TransactionID(1231421),new TransactionID(1231422),"");

    public static final TransactionID tID1231410 = new TransactionID(1231410);
    public static final TransactionID tID1231412 = new TransactionID(1231412);
    public static final TransactionID tID1231416 = new TransactionID(1231416);
    public static final TransactionID tID1231420 = new TransactionID(1231420);

    public static final Change c101 = new Change(101,tID1231410,"I","public.test1","id:23:MzE=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:MTMyNTg=:!");
    public static final Change c102 = new Change(102,tID1231410,"I","public.test1","id:23:MzI=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:OTMyMTM=:!");
    public static final Change c103 = new Change(103,tID1231410,"I","public.test1","id:23:MzM=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:NTkxODA=:!");
    public static final Change c104 = new Change(104,tID1231410,"I","public.test1","id:23:MzQ=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:OTI3Mzk=:!");
    public static final Change c105 = new Change(105,tID1231410,"I","public.test1","id:23:MzU=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:NzIyOTM=:!");
    public static final Change c106 = new Change(106,tID1231410,"I","public.test1","id:23:MzY=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:MTc0MzU=:!");
    public static final Change c107 = new Change(107,tID1231410,"I","public.test1","id:23:Mzc=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:NjYxNzE=:!");
    public static final Change c108 = new Change(108,tID1231410,"I","public.test1","id:23:Mzg=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:MzI0ODU=:!");
    public static final Change c109 = new Change(109,tID1231410,"I","public.test1","id:23:Mzk=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:MTQ3MA==:!");
    public static final Change c110 = new Change(110,tID1231410,"I","public.test1","id:23:NDA=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:Njk3NTg=:!");
    public static final Change c111 = new Change(111,tID1231412,"I","public.test1","id:23:NDE=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:NDg5MjM=:!");
    public static final Change c112 = new Change(112,tID1231412,"I","public.test1","id:23:NDI=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:NDk5OTI=:!");
    public static final Change c113 = new Change(113,tID1231412,"I","public.test1","id:23:NDM=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:MTY2NDg=:!");
    public static final Change c114 = new Change(114,tID1231412,"I","public.test1","id:23:NDQ=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:NTM1NzQ=:!");
    public static final Change c115 = new Change(115,tID1231412,"I","public.test1","id:23:NDU=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:NjY1OTU=:!");
    public static final Change c116 = new Change(116,tID1231412,"I","public.test1","id:23:NDY=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:NzE4MDc=:!");
    public static final Change c117 = new Change(117,tID1231412,"I","public.test1","id:23:NDc=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:MjE2MTI=:!");
    public static final Change c118 = new Change(118,tID1231412,"I","public.test1","id:23:NDg=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:MjcyMjY=:!");
    public static final Change c119 = new Change(119,tID1231412,"I","public.test1","id:23:NDk=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:OTExMzU=:!");
    public static final Change c120 = new Change(120,tID1231412,"I","public.test1","id:23:NTA=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:NDg3Njk=:!");
    public static final Change c121 = new Change(121,tID1231412,"U","public.test1","id:23:NDE=:NDE=|c_bytea:17:!:!|c_text:25:!:NDg5MjM=|c_int:23:NDg5MjM=:NDg5MjM=");
    public static final Change c122 = new Change(122,tID1231412,"U","public.test1","id:23:NDI=:NDI=|c_bytea:17:!:!|c_text:25:!:NDk5OTI=|c_int:23:NDk5OTI=:NDk5OTI=");
    public static final Change c123 = new Change(123,tID1231412,"U","public.test1","id:23:NDM=:NDM=|c_bytea:17:!:!|c_text:25:!:MTY2NDg=|c_int:23:MTY2NDg=:MTY2NDg=");
    public static final Change c124 = new Change(124,tID1231412,"U","public.test1","id:23:NDQ=:NDQ=|c_bytea:17:!:!|c_text:25:!:NTM1NzQ=|c_int:23:NTM1NzQ=:NTM1NzQ=");
    public static final Change c125 = new Change(125,tID1231412,"U","public.test1","id:23:NDU=:NDU=|c_bytea:17:!:!|c_text:25:!:NjY1OTU=|c_int:23:NjY1OTU=:NjY1OTU=");
    public static final Change c126 = new Change(126,tID1231412,"U","public.test1","id:23:NDY=:NDY=|c_bytea:17:!:!|c_text:25:!:NzE4MDc=|c_int:23:NzE4MDc=:NzE4MDc=");
    public static final Change c127 = new Change(127,tID1231412,"U","public.test1","id:23:NDc=:NDc=|c_bytea:17:!:!|c_text:25:!:MjE2MTI=|c_int:23:MjE2MTI=:MjE2MTI=");
    public static final Change c128 = new Change(128,tID1231412,"U","public.test1","id:23:NDg=:NDg=|c_bytea:17:!:!|c_text:25:!:MjcyMjY=|c_int:23:MjcyMjY=:MjcyMjY=");
    public static final Change c129 = new Change(129,tID1231412,"U","public.test1","id:23:NDk=:NDk=|c_bytea:17:!:!|c_text:25:!:OTExMzU=|c_int:23:OTExMzU=:OTExMzU=");
    public static final Change c130 = new Change(130,tID1231412,"U","public.test1","id:23:NTA=:NTA=|c_bytea:17:!:!|c_text:25:!:NDg3Njk=|c_int:23:NDg3Njk=:NDg3Njk=");
    public static final Change c131 = new Change(131,tID1231412,"U","public.test1","id:23:NDE=:NDE=|c_bytea:17:!:NDg5MjM=|c_text:25:NDg5MjM=:NDg5MjM=|c_int:23:NDg5MjM=:NDg5MjM=");
    public static final Change c132 = new Change(132,tID1231412,"U","public.test1","id:23:NDI=:NDI=|c_bytea:17:!:NDk5OTI=|c_text:25:NDk5OTI=:NDk5OTI=|c_int:23:NDk5OTI=:NDk5OTI=");
    public static final Change c133 = new Change(133,tID1231412,"U","public.test1","id:23:NDM=:NDM=|c_bytea:17:!:MTY2NDg=|c_text:25:MTY2NDg=:MTY2NDg=|c_int:23:MTY2NDg=:MTY2NDg=");
    public static final Change c134 = new Change(134,tID1231412,"U","public.test1","id:23:NDQ=:NDQ=|c_bytea:17:!:NTM1NzQ=|c_text:25:NTM1NzQ=:NTM1NzQ=|c_int:23:NTM1NzQ=:NTM1NzQ=");
    public static final Change c135 = new Change(135,tID1231412,"U","public.test1","id:23:NDU=:NDU=|c_bytea:17:!:NjY1OTU=|c_text:25:NjY1OTU=:NjY1OTU=|c_int:23:NjY1OTU=:NjY1OTU=");
    public static final Change c136 = new Change(136,tID1231412,"U","public.test1","id:23:NDY=:NDY=|c_bytea:17:!:NzE4MDc=|c_text:25:NzE4MDc=:NzE4MDc=|c_int:23:NzE4MDc=:NzE4MDc=");
    public static final Change c137 = new Change(137,tID1231412,"U","public.test1","id:23:NDc=:NDc=|c_bytea:17:!:MjE2MTI=|c_text:25:MjE2MTI=:MjE2MTI=|c_int:23:MjE2MTI=:MjE2MTI=");
    public static final Change c138 = new Change(138,tID1231412,"U","public.test1","id:23:NDg=:NDg=|c_bytea:17:!:MjcyMjY=|c_text:25:MjcyMjY=:MjcyMjY=|c_int:23:MjcyMjY=:MjcyMjY=");
    public static final Change c139 = new Change(139,tID1231412,"U","public.test1","id:23:NDk=:NDk=|c_bytea:17:!:OTExMzU=|c_text:25:OTExMzU=:OTExMzU=|c_int:23:OTExMzU=:OTExMzU=");
    public static final Change c140 = new Change(140,tID1231412,"U","public.test1","id:23:NTA=:NTA=|c_bytea:17:!:NDg3Njk=|c_text:25:NDg3Njk=:NDg3Njk=|c_int:23:NDg3Njk=:NDg3Njk=");
    public static final Change c141 = new Change(141,tID1231416,"I","public.test1","id:23:NTE=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:OTUwNjc=:!");
    public static final Change c142 = new Change(142,tID1231416,"I","public.test1","id:23:NTI=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:NTQyMzQ=:!");
    public static final Change c143 = new Change(143,tID1231416,"I","public.test1","id:23:NTM=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:ODM4Mjc=:!");
    public static final Change c144 = new Change(144,tID1231416,"I","public.test1","id:23:NTQ=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:Njk0MDA=:!");
    public static final Change c145 = new Change(145,tID1231416,"I","public.test1","id:23:NTU=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:NjM4OTI=:!");
    public static final Change c146 = new Change(146,tID1231416,"I","public.test1","id:23:NTY=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:ODExOTU=:!");
    public static final Change c147 = new Change(147,tID1231416,"I","public.test1","id:23:NTc=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:OTQ1OTc=:!");
    public static final Change c148 = new Change(148,tID1231416,"I","public.test1","id:23:NTg=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:NDkxOTg=:!");
    public static final Change c149 = new Change(149,tID1231416,"I","public.test1","id:23:NTk=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:NzY0ODA=:!");
    public static final Change c150 = new Change(150,tID1231416,"I","public.test1","id:23:NjA=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:MTIyMDI=:!");
    public static final Change c151 = new Change(151,tID1231416,"D","public.test1","id:23:NDE=:!|c_bytea:17:NDg5MjM=:!|c_text:25:NDg5MjM=:!|c_int:23:NDg5MjM=:!");
    public static final Change c152 = new Change(152,tID1231416,"D","public.test1","id:23:NDI=:!|c_bytea:17:NDk5OTI=:!|c_text:25:NDk5OTI=:!|c_int:23:NDk5OTI=:!");
    public static final Change c153 = new Change(153,tID1231416,"D","public.test1","id:23:NDM=:!|c_bytea:17:MTY2NDg=:!|c_text:25:MTY2NDg=:!|c_int:23:MTY2NDg=:!");
    public static final Change c154 = new Change(154,tID1231416,"D","public.test1","id:23:NDQ=:!|c_bytea:17:NTM1NzQ=:!|c_text:25:NTM1NzQ=:!|c_int:23:NTM1NzQ=:!");
    public static final Change c155 = new Change(155,tID1231416,"D","public.test1","id:23:NDU=:!|c_bytea:17:NjY1OTU=:!|c_text:25:NjY1OTU=:!|c_int:23:NjY1OTU=:!");
    public static final Change c156 = new Change(156,tID1231416,"D","public.test1","id:23:NDY=:!|c_bytea:17:NzE4MDc=:!|c_text:25:NzE4MDc=:!|c_int:23:NzE4MDc=:!");
    public static final Change c157 = new Change(157,tID1231416,"D","public.test1","id:23:NDc=:!|c_bytea:17:MjE2MTI=:!|c_text:25:MjE2MTI=:!|c_int:23:MjE2MTI=:!");
    public static final Change c158 = new Change(158,tID1231416,"D","public.test1","id:23:NDg=:!|c_bytea:17:MjcyMjY=:!|c_text:25:MjcyMjY=:!|c_int:23:MjcyMjY=:!");
    public static final Change c159 = new Change(159,tID1231416,"D","public.test1","id:23:NDk=:!|c_bytea:17:OTExMzU=:!|c_text:25:OTExMzU=:!|c_int:23:OTExMzU=:!");
    public static final Change c160 = new Change(160,tID1231416,"D","public.test1","id:23:NTA=:!|c_bytea:17:NDg3Njk=:!|c_text:25:NDg3Njk=:!|c_int:23:NDg3Njk=:!");
    public static final Change c161 = new Change(161,tID1231416,"D","public.test1","id:23:NTE=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:OTUwNjc=:!");
    public static final Change c162 = new Change(162,tID1231416,"D","public.test1","id:23:NTI=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:NTQyMzQ=:!");
    public static final Change c163 = new Change(163,tID1231416,"D","public.test1","id:23:NTM=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:ODM4Mjc=:!");
    public static final Change c164 = new Change(164,tID1231416,"D","public.test1","id:23:NTQ=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:Njk0MDA=:!");
    public static final Change c165 = new Change(165,tID1231416,"D","public.test1","id:23:NTU=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:NjM4OTI=:!");
    public static final Change c166 = new Change(166,tID1231416,"D","public.test1","id:23:NTY=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:ODExOTU=:!");
    public static final Change c167 = new Change(167,tID1231416,"D","public.test1","id:23:NTc=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:OTQ1OTc=:!");
    public static final Change c168 = new Change(168,tID1231416,"D","public.test1","id:23:NTg=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:NDkxOTg=:!");
    public static final Change c169 = new Change(169,tID1231416,"D","public.test1","id:23:NTk=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:NzY0ODA=:!");
    public static final Change c170 = new Change(170,tID1231416,"D","public.test1","id:23:NjA=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:MTIyMDI=:!");
    public static final Change c171 = new Change(171,tID1231420,"D","public.test1","id:23:MzE=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:MTMyNTg=:!");
    public static final Change c172 = new Change(172,tID1231420,"D","public.test1","id:23:MzI=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:OTMyMTM=:!");
    public static final Change c173 = new Change(173,tID1231420,"D","public.test1","id:23:MzM=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:NTkxODA=:!");
    public static final Change c174 = new Change(174,tID1231420,"D","public.test1","id:23:MzQ=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:OTI3Mzk=:!");
    public static final Change c175 = new Change(175,tID1231420,"D","public.test1","id:23:MzU=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:NzIyOTM=:!");
    public static final Change c176 = new Change(176,tID1231420,"D","public.test1","id:23:MzY=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:MTc0MzU=:!");
    public static final Change c177 = new Change(177,tID1231420,"D","public.test1","id:23:Mzc=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:NjYxNzE=:!");
    public static final Change c178 = new Change(178,tID1231420,"D","public.test1","id:23:Mzg=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:MzI0ODU=:!");
    public static final Change c179 = new Change(179,tID1231420,"D","public.test1","id:23:Mzk=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:MTQ3MA==:!");
    public static final Change c180 = new Change(180,tID1231420,"D","public.test1","id:23:NDA=:!|c_bytea:17:!:!|c_text:25:!:!|c_int:23:Njk3NTg=:!");

    public static final Transaction t1231410 = new Transaction();
    public static final Transaction t1231412 = new Transaction();
    public static final Transaction t1231416 = new Transaction();
    public static final Transaction t1231420 = new Transaction();

    // To run with Ant (version 1.6.5???), we need this suite() method to run our tests.
    // Ant uses an JUnit 3.x runner instead of a 4.X one. See http://junit.sourceforge.net/doc/faq/faq.htm#tests_1
    public static junit.framework.Test suite()
    {
        return new junit.framework.JUnit4TestAdapter(TransactionTest.class);
    }

    @BeforeClass
    public static void oneTimeSetup()
    {
        t1231410.add(c101);
        t1231410.add(c102);
        t1231410.add(c103);
        t1231410.add(c104);
        t1231410.add(c105);
        t1231410.add(c106);
        t1231410.add(c107);
        t1231410.add(c108);
        t1231410.add(c109);
        t1231410.add(c110);
        t1231412.add(c111);
        t1231412.add(c112);
        t1231412.add(c113);
        t1231412.add(c114);
        t1231412.add(c115);
        t1231412.add(c116);
        t1231412.add(c117);
        t1231412.add(c118);
        t1231412.add(c119);
        t1231412.add(c120);
        t1231412.add(c121);
        t1231412.add(c122);
        t1231412.add(c123);
        t1231412.add(c124);
        t1231412.add(c125);
        t1231412.add(c126);
        t1231412.add(c127);
        t1231412.add(c128);
        t1231412.add(c129);
        t1231412.add(c130);
        t1231412.add(c131);
        t1231412.add(c132);
        t1231412.add(c133);
        t1231412.add(c134);
        t1231412.add(c135);
        t1231412.add(c136);
        t1231412.add(c137);
        t1231412.add(c138);
        t1231412.add(c139);
        t1231412.add(c140);
        t1231416.add(c141);
        t1231416.add(c142);
        t1231416.add(c143);
        t1231416.add(c144);
        t1231416.add(c145);
        t1231416.add(c146);
        t1231416.add(c147);
        t1231416.add(c148);
        t1231416.add(c149);
        t1231416.add(c150);
        t1231416.add(c151);
        t1231416.add(c152);
        t1231416.add(c153);
        t1231416.add(c154);
        t1231416.add(c155);
        t1231416.add(c156);
        t1231416.add(c157);
        t1231416.add(c158);
        t1231416.add(c159);
        t1231416.add(c160);
        t1231416.add(c161);
        t1231416.add(c162);
        t1231416.add(c163);
        t1231416.add(c164);
        t1231416.add(c165);
        t1231416.add(c166);
        t1231416.add(c167);
        t1231416.add(c168);
        t1231416.add(c169);
        t1231416.add(c170);
        t1231420.add(c171);
        t1231420.add(c172);
        t1231420.add(c173);
        t1231420.add(c174);
        t1231420.add(c175);
        t1231420.add(c176);
        t1231420.add(c177);
        t1231420.add(c178);
        t1231420.add(c179);
        t1231420.add(c180);
    }

    @Test
    public void testConstructorEmpty()
    {
        Transaction t = new Transaction();
        Assert.assertEquals(0,t.size());
        Assert.assertTrue(t.isEmpty());
    }

    @Test
    public void testConstructorChange()
    {
        Transaction t = new Transaction(c101);
        Assert.assertEquals(1,t.size());
        Assert.assertFalse(t.isEmpty());
    }

    @Test
    public void testConstructorTransaction()
    {
        Transaction t = new Transaction(t1231420);
        Assert.assertTrue(t.equals(t1231420));
        Assert.assertTrue(t1231420.equals(t));
    }

    @Test
    public void testgetTransactionIDs()
    {
        // One TransactionID tests
        TreeSet<TransactionID> ts1 = new TreeSet<TransactionID>();
        ts1.add(tID1231410);
        Assert.assertEquals(ts1,t1231410.getTransactionIDs());
        TreeSet<TransactionID> ts2 = new TreeSet<TransactionID>();
        ts2.add(tID1231412);
        Assert.assertEquals(ts2,t1231412.getTransactionIDs());
        TreeSet<TransactionID> ts3 = new TreeSet<TransactionID>();
        ts3.add(tID1231416);
        Assert.assertEquals(ts3,t1231416.getTransactionIDs());
        TreeSet<TransactionID> ts4 = new TreeSet<TransactionID>();
        ts4.add(tID1231420);
        Assert.assertEquals(ts4,t1231420.getTransactionIDs());
        // A test with all four TransactionIDs in it
        Transaction tAll = new Transaction();
        tAll.addAll(t1231410);
        tAll.addAll(t1231412);
        tAll.addAll(t1231416);
        tAll.addAll(t1231420);
        TreeSet<TransactionID> tsAll = new TreeSet<TransactionID>();
        tsAll.add(tID1231410);
        tsAll.add(tID1231412);
        tsAll.add(tID1231416);
        tsAll.add(tID1231420);
        Assert.assertEquals(tsAll,tAll.getTransactionIDs());
    }

    @Test
    public void testbetweenSnapshots()
    {
        // calling betweenSnapshots() where first or last snapshot is null should throw
        // NullPointerException
        try
        {
            t1231410.betweenSnapshots(null,s3);
            Assert.fail();
        }
        catch (NullPointerException e)
        {
            Assert.assertTrue(true);
        }
        try
        {
            t1231410.betweenSnapshots(s3,null);
            Assert.fail();
        }
        catch (NullPointerException e)
        {
            Assert.assertTrue(true);
        }
        try
        {
            t1231410.betweenSnapshots(null,null);
            Assert.fail();
        }
        catch (NullPointerException e)
        {
            Assert.assertTrue(true);
        }
        // Calling betweenSnapshots() where first > last should throw IllegalArgumentException
        try
        {
            t1231410.betweenSnapshots(s4,s3);
            Assert.fail();
        }
        catch (IllegalArgumentException e)
        {
            Assert.assertTrue(true);
        }
        // The relevant Snapshots and TransactionIDs, and the order they occured in.
        // Watch out: The order is not in numeric TransactionID order.
        // Snapshot | TransactionID
        //    s3    |
        //          |     tID1231412
        //    s4    |
        //          |     tID1231416
        //    s5    |
        //          |     tID1231410
        //    s6    |
        //          |     tID1231420
        //    s7    |
        Assert.assertFalse(t1231410.betweenSnapshots(s3,s4));
        Assert.assertFalse(t1231410.betweenSnapshots(s4,s5));
        Assert.assertTrue(t1231410.betweenSnapshots(s5,s6));
        Assert.assertFalse(t1231410.betweenSnapshots(s6,s7));
        Assert.assertTrue(t1231412.betweenSnapshots(s3,s4));
        Assert.assertFalse(t1231412.betweenSnapshots(s4,s5));
        Assert.assertFalse(t1231412.betweenSnapshots(s5,s6));
        Assert.assertFalse(t1231412.betweenSnapshots(s6,s7));
        Assert.assertFalse(t1231416.betweenSnapshots(s3,s4));
        Assert.assertTrue(t1231416.betweenSnapshots(s4,s5));
        Assert.assertFalse(t1231416.betweenSnapshots(s5,s6));
        Assert.assertFalse(t1231416.betweenSnapshots(s6,s7));
        Assert.assertFalse(t1231420.betweenSnapshots(s3,s4));
        Assert.assertFalse(t1231420.betweenSnapshots(s4,s5));
        Assert.assertFalse(t1231420.betweenSnapshots(s5,s6));
        Assert.assertTrue(t1231420.betweenSnapshots(s6,s7));
        //
        // Now make sure we can put transactions together, and skip snapshots, and the betweenSnapshots
        // tests still work
        // First, put together tID1231412 and tID1231416
        Transaction t1 = new Transaction();
        t1.addAll(t1231412);
        t1.addAll(t1231416);
        // And tID1231410 and tID1231410 together
        Transaction t2 = new Transaction();
        t2.addAll(t1231410);
        t2.addAll(t1231420);
        // And we should be able to test that this table holds true:
        // Snapshot | TransactionID
        //    s3    |
        //          |     tID1231412 | AKA t1
        //          |     tID1231416 |
        //    s5    |
        //          |     tID1231410 | AKA t2
        //          |     tID1231420 |
        //    s7    |
        Assert.assertTrue(t1.betweenSnapshots(s3,s5));
        Assert.assertFalse(t1.betweenSnapshots(s5,s7));
        Assert.assertFalse(t2.betweenSnapshots(s3,s5));
        Assert.assertTrue(t2.betweenSnapshots(s5,s7));
        // Now lets put all the transactions together
        Transaction t3 = new Transaction();
        t3.addAll(t1231412);
        t3.addAll(t1231416);
        t3.addAll(t1231410);
        t3.addAll(t1231420);
        // And we should be able to test this:
        // Snapshot | TransactionID
        //    s3    |
        //          |     tID1231412 | AKA t3
        //          |     tID1231416 |
        //          |     tID1231410 |
        //          |     tID1231420 |
        //    s7    |
        Assert.assertTrue(t3.betweenSnapshots(s3,s7));
        // And for grins, make sure that from 's4' is false (because some of the transactions are outside the snapshot
        Assert.assertFalse(t3.betweenSnapshots(s4,s7));
    }
}
