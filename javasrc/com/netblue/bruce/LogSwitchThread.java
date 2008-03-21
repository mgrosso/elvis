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

import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

class LogSwitchThread extends DaemonThread {

    //constructors
    /** @param uri this will be used to obtain a uri for the master db connection */
    public LogSwitchThread(String uri ){
        this.uri=uri;
    }

    /** if you use the default constructor, you must call setUri setCluster before
     * calling init() or run() so that this object can find the master database */
    public LogSwitchThread(){
    }

    //abstract methods of parent.

    protected boolean childInit() throws Exception {
        setLogger( Logger.getLogger(LogSwitchThread.class));
        rotateFrequency     = getProperties().getIntProperty(ROTATE_KEY, ROTATE_DEFAULT);
        retainFrequency     = getProperties().getIntProperty(RETAIN_KEY, RETAIN_DEFAULT);
        createSnapshotQuery = getProperties().getProperty(CREATE_SNAPSHOT_QUERY_KEY,CREATE_SNAPSHOT_QUERY_DEFAULT); 
        if(uri==null){
                throw new IllegalStateException( "the uri must be set to a non-null value." );
        }
        connection = constructConnection(uri);
        createSnapshotStatement = capture(connection.prepareStatement(createSnapshotQuery));
        getLogger().debug( "childInit(): uri: "+ uri +
            " rotateFrequency: " + rotateFrequency + 
            " retainFrequency: " + retainFrequency +
            " snapshotFrequency: " + getSleepTime() + 
            " createSnapshotQuery: " + createSnapshotQuery
        );
        return true;
    }

    public void checkSanity()throws Exception {
        if(connection == null || connection.isClosed() ){
            throw new IllegalStateException(
                "database connection is null or closed. must re-initialize.");
        }
        //TODO: more check statements here.
    }

    protected String getConfigPrefix(){
        return "logSwitch.";
    }
    protected Class getChildClass()throws Exception {
        return LogSwitchThread.class ;
    }

    public void doWork() throws Exception {
        getLogger().trace("about to generate snapshot...");
        generateSnapshot();
        connection.commit();
        getLogger().trace("generated snapshot and committed.");
    }


    //our own methods and data.
    public void rotateTables()throws Exception {
        //acquireLock();
        //newLogTable(s); // Create new log tables if needed
        //dropLogTable(s); // Drop old log tables if needed
        //connection.commit();
    }

    //our own methods and data.
    public void generateSnapshot()throws Exception {
        createSnapshotStatement.execute();
        connection.commit();
    }


    //things you could conceivably want to get/set 
    //
    private String uri;
    String getUri(){
        return uri;
    }
    void setUri(String uri){
        this.uri=uri;
    }

    private int rotateFrequency;
    int getRotateFrequency (){
        return rotateFrequency;
    }
    void setRotateFrequency (int rotateFrequency){
        this.rotateFrequency=rotateFrequency;
    }

    private int retainFrequency;
    int getRetainFrequency (){
        return retainFrequency;
    }
    void setRetainFrequency (int retainFrequency){
        this.retainFrequency=retainFrequency;
    }

    private String createSnapshotQuery;
    String getCreateSnapshotQuery(){
        return createSnapshotQuery;
    }
    void setCreateSnapshotQuery(String createSnapshotQuery){
        this.createSnapshotQuery= createSnapshotQuery;
    }


    //things you cant get/set 
    //
    private PreparedStatement createSnapshotStatement;
    private PreparedStatement shareLockStatement;


    private Connection connection;

    // Properties that drive actions for this thread
    //
    // Query that generates a snapshot
    private static final String SHARE_LOCK_QUERY_KEY = "bruce.shareLockQuery";
    private static final String SHARE_LOCK_QUERY_DEFAULT = "lock table currentlog in access exclusive mode";
    // Query that generates a snapshot
    private static final String CREATE_SNAPSHOT_QUERY_KEY = "bruce.createSnapshotQuery";
    private static final String CREATE_SNAPSHOT_QUERY_DEFAULT = "select bruce.logsnapshot()";
    // How often to create new transaction and snapshot log tables
    private static final String ROTATE_KEY = "bruce.rotateTime";
    private static final int ROTATE_DEFAULT = 1440; // One day
    // How often to retain transaction and snapshot log tables
    private static final String RETAIN_KEY = "bruce.retainTime";
    private static final int RETAIN_DEFAULT = 7200; // Five days

}
