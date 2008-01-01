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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * @author lanceball
 * @version $Id: MainTest.java 72519 2007-06-27 14:24:08Z lball $
 */
public class MainTest
{
    @Test
        public void testGetPidFile()
    {
        // The default pid file name is bruce.pid
        File pidFile = Main.getPidFile();
        assertEquals("Unexpected default PID file.", "bruce.pid", pidFile.getName());
    }

    @Test
    public void testDaemonize()
    {
        // The daemonize method should close System.out and System.err.  We don't have any way
        // of checking that without using our own PrintStream for OUT and ERR and checking a flag
        final CloseCheckPrintStream out = new CloseCheckPrintStream(new ByteArrayOutputStream(1));
        final CloseCheckPrintStream err = new CloseCheckPrintStream(new ByteArrayOutputStream(1));

        // Set the System streams with our own
        System.setOut(out);
        System.setErr(err);

        // Daemonize and check to see if the streams were closed
        Main.daemonize();
        assertTrue("System.out not closed on daemonize", out.closeCalled);
        assertTrue("System.err not closed on daemonize", err.closeCalled);
    }

    private final class CloseCheckPrintStream extends PrintStream
    {
        boolean closeCalled = false;

        public CloseCheckPrintStream(final OutputStream outputStream)
        {
            super(outputStream);
        }


        public void close()
        {
            super.close();
            closeCalled = true;
        }
    }

}
