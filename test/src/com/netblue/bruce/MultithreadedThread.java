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

import java.util.Set;

/**
 * An abstract class acting as a base class for all multithreaded tests. Implementing classes (which are likely 
 * to be inner classes) will be forced to implement run(), unlike the behavior of the <code>Thread</code> class.
 *
 * The result set should be used to capture any <code>Throwable</code> that may occur during the execution of the
 * thread. See the class description of MultithreadedError for more details.
 * 
 * Implementing classes should also test the value of shutdownThread (true==shutdown, false==remain running).
 *
 * An example usage:
 *    class ForceSnapshot extends MultithreadedThread {
 *	public ForceSnapshot (Set<MultithreadedError> results) {
 *	    super(results);
 *	}
 *
 *	public void run() {
 *	    while (!shutdownThread) {
 *		try {
 *		    // Force a snapshot
 *		    Connection c = getTestDataSource().getConnection();
 *		    c.setAutoCommit(true);
 *		    executeAndLog(c.createStatement(),"select logsnapshot()");
 *		    c.close();
 *		    Thread.sleep(100L); // Sleep for a moment before snapshoting again.
 *		} catch (Throwable t) {
 *		    results.add(new MultithreadedError(this,t));
 *		}
 *	    }
 *	}
 *    }
 *
 * @author rklahn
 */
abstract class MultithreadedThread extends Thread {
    public MultithreadedThread (Set<MultithreadedError> results) {
	this.results=results;
    }

    public void shutdown() {
	shutdownThread=true;
    }
    
    abstract public void run();
    
    public Set<MultithreadedError> results;
    public boolean shutdownThread = false;
}

