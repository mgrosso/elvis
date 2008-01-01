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

import org.apache.log4j.*;

import java.io.File;

/**
 * The main class for the Replication engine.  This class is responsible for reading property files, setting
 * up loggers, daemonizing the process, and ensuring that we shut down cleanly.
 * 
 * @author lanceball
 * @version $Id: Main.java 72519 2007-06-27 14:24:08Z lball $
 */
public class Main
{
    /**
     * Runs the replication daemon
     * @param args - no arguments
     */
    public static void main(String[] args)
    {
        try
        {
            if (args.length < 1 || args[0].length() == 0)
            {
                fatalError("Usage: startup.sh [cluster name]");
            }


            // Be sure any log messages generated before we daemonize are sent to System.err
            // just in case something bad happens on startup
            Appender startupAppender = new ConsoleAppender(new SimpleLayout(), "System.err");

            try
            {
                // Configure logging
                LOGGER.addAppender(startupAppender);
                BasicConfigurator.configure();
                BasicConfigurator.resetConfiguration();
                PropertyConfigurator.configure(System.getProperty("log4j.configuration"));
                PROPERTIES.logProperties();

                // Daemonize (close stdout & stderr)
                LOGGER.info("Daemonizing...");
                Main.daemonize();
                // Make sure we clean up properly when we're killed
                Main.addShutdownHook();
            }
            catch(Throwable t)
            {
                LOGGER.fatal("Start up failed.", t);
            }
            finally
            {
                LOGGER.removeAppender(startupAppender);
            }

            // Start the main daemon process
            LOGGER.info("Starting replication...");
            startReplicationDaemon(args[0]);

            // Loop while checking for shutdown requests
            while (!isShutdownRequested())
            {
                try { Thread.sleep(1000); }
                catch (InterruptedException e) { }
            }
        }
        catch (Throwable throwable)
        {
            LOGGER.fatal("Exception caught.  Bailing.", throwable);
        }
    }

    private static void fatalError(String message)
    {
        System.err.println(message);
        System.exit(1);
    }

    /**
     * Gets the PID file for the currently active daemon process.  This file may or may not exist.  If the daemon
     * is running, we can reasonably expect it to be there.
     * @return the PID file for the currently active daemon process
     */
    static File getPidFile()
    {
        return new File(PROPERTIES.getProperty(PID_FILE_KEY, PID_FILE_DEFAULT));
    }

    /**
     * Daemonizes the running VM by closing <code>System.out</code> and <code>System.err</code> and registering
     * <code>deleteOnExit()</code> for the PID file of the running process (if it exists).
     */
    static void daemonize()
    {
        getPidFile().deleteOnExit();
        System.out.close();
        System.err.close();
    }

    /**
     * Start the <code>ReplicationDaemon</code> in its own thread
     * @param clusterName The name of the cluster that this daemon will operate under.
     */
    private static void startReplicationDaemon(final String clusterName)
    {
        daemon = new ReplicationDaemon();
        LOGGER.info("Loading cluster \"" + clusterName + "\"");
        daemon.loadCluster(clusterName);
        daemonThread = new Thread(daemon);
        LOGGER.info("Spawning daemon thread");
        daemonThread.start();
    }

    /**
     * Adds a <code>ShutdownThread</code> to the <code>Runtime</code> shutdown hooks.
     */
    private static void addShutdownHook()
    {
        Runtime.getRuntime().addShutdownHook(new ShutdownThread());
    }

    /**
     * Requests a shutdown of the daemon
     */
    private static void shutdown()
    {
        shutdownRequested = true;
        LOGGER.info("Shutdown requested.  Stopping daemon.");
        try
        {
            daemon.shutdown();
            daemonThread.join();
        }
        catch (InterruptedException e)
        {
            Thread.dumpStack();
            LOGGER.error("Interrupted on daemon shutdown.", e);
        }
    }

    /**
     * Returns true if a shutdown request has been made
     * @return true if a shutdown request has been made
     */
    public static boolean isShutdownRequested()
    {
        return shutdownRequested;
    }

    /**
     * A simple class used to shutdown the daemon in a runtime shutdown hook
     */
    private static class ShutdownThread extends Thread
    {
        public void run() { Main.shutdown(); }
    }

    private static boolean shutdownRequested = false;
    private static final Logger LOGGER = Logger.getLogger(Main.class);
    private static final String PROPERTIES_FILENAME_KEY = "bruce.propertiesFileName";
    private static final String PROPERTIES_FILENAME_DEFAULT = "bruce.properties";
    private static final String PID_FILE_KEY = "pid.file";
    private static final String PID_FILE_DEFAULT = "bruce.pid";
    private static final BruceProperties PROPERTIES = new BruceProperties(PROPERTIES_FILENAME_KEY, PROPERTIES_FILENAME_DEFAULT);;

    private static ReplicationDaemon daemon;
    private static Thread daemonThread;
}
