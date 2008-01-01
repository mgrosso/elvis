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

/*
 * A class used to capture an error in a multithreaded test, capturing the (<code>Throwable</code>) error
 * and the thread it took place in. Generaly, these will be <code>AssertionErrors</code>, thrown by the jUnit 
 * <code>Assert.*</code> methods when the assertion fails. But this is not the exclusive use, any 
 * <code>Throwable</code> will do. As a side note, <code>Error</code> and <code>Exception</code> extend
 * <code>Throwable</code>, and, thus, any Error or Exception can be caputured in this class.
 *
 * The main thread of a multithreaded test should create a syncronized set of <code>MultithreadedError</code>
 * which will need to be passed to the constructor of each <code>MultithreadedTestThread</code>, thus:
 * <code>
 * // Set to hold Throwable's thrown by each thread (Presumably from failed tests)                             
 * Set<MultithreadedError> testResults = Collections.synchronizedSet(new HashSet<MultithreadedError>());
 * ...
 * TestThread updateThread = new DBLongUpdateThread(testResults);
 * </code>
 *
 * Once all the test threads have been <code>shutdown()</code> and <code>join()ed</code>, the main thread
 * should report on all errors, and jUnit <code>fail()</code> if there were any errors, thus:
 * <code>
 * // Process any failed results                                                                                   
 * if (testResults.size()>0) {
 *   for (MultithreadedError e:testResults) {
 *     logger.error("In thread:"+e.t.getName(),e.a);
 *   }
 *   fail("failed tests during multi-threaded testing");
 * }
 * </code>
 *
 * @author rklahn
 */
class MultithreadedError {
    public MultithreadedError(Thread t, Throwable a) {
	this.t=t;
	this.a=a;
    }

    public Thread t;
    public Throwable a;
}
