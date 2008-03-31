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

import java.util.Properties;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import static java.text.MessageFormat.format;
import java.util.HashSet;
import java.lang.ClassNotFoundException ;
import java.lang.InstantiationException ;
import java.lang.IllegalAccessException ;

/**
 * base class for threads that must perdiodically do something to one or more datbases.
 */
abstract public class DaemonThread implements Runnable {

    abstract protected boolean childInit() throws Exception ;
    abstract protected void checkSanity() throws Exception ;
    abstract protected void doWork()throws Exception ;
    abstract protected String getConfigPrefix()throws Exception ;
    abstract protected Class getChildClass()throws Exception ;

    public DaemonThread(){
        sleepTime=SLEEP_DEFAULT;
        errorSleepTime=ERROR_SLEEP_DEFAULT;
        maxIterationsBetweenRestart = MAX_LOOP_DEFAULT ;
        resultsCleanup = new HashSet<ResultSet>();
        connectionCleanup = new HashSet<Connection>();
        statementCleanup = new HashSet<PreparedStatement>();
        properties = new BruceProperties();
    }

    public boolean init() throws Exception {
        LOGGER = Logger.getLogger(getChildClass());
        if(debugInfo == null ){
            debugInfo = getConfigPrefix();
        }
        if(properties == null ){
            properties = new BruceProperties();
        }
        sleepTime = properties.getIntProperty(
                getKey(SLEEP_KEY), SLEEP_DEFAULT);
        errorSleepTime = properties.getIntProperty(
                getKey(ERROR_SLEEP_KEY), ERROR_SLEEP_DEFAULT);
        maxIterationsBetweenRestart = properties.getIntProperty(
            getKey(MAX_LOOP_KEY), MAX_LOOP_DEFAULT);
        driverName = properties.getProperty(  
                //only one default driver, children can override per connection below by passing
                //in a different classname to constructConnection(), so we dont use a getKey()
                //here, since that would force users to override for each class something
                //they probably only want to change in one place.
                JDBC_DRIVER_NAME_KEY, JDBC_DRIVER_NAME_DEFAULT);
        return childInit();
    }

    /**
     * Opens a connection to the database, registers it as a resource that may need closing,
     * sets transaction isolation level to repeatable read, and returns it.
     *
     * @param uri the jdbc database uri
     * @throws SQLException
     * @returns Connection
     */
    protected Connection constructConnection(String uri) throws Exception {
        return constructConnection(uri,driverName,properties);
    }

    /**
     * Opens a connection to the database, registers it as a resource that may need closing,
     * sets transaction isolation level to repeatable read, and returns it.
     *
     * @param uri the jdbc database uri
     * @param drivername the jdbc driver classname
     * @param properties the properties for the connection, defaults to BruceProperties if null.
     * @throws SQLException
     * @returns Connection
     */
    protected Connection constructConnection(String uri, String classname, Properties p) 
    throws Exception {
        Class driverclass = Class.forName(classname);
        java.sql.Driver driver = (java.sql.Driver)driverclass.newInstance();
        Connection giveback = driver.connect(uri,p);
        giveback.setAutoCommit(false);
        giveback.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        return capture(giveback);
    }

    /**
     * Releases all database resources used by this slave.  Used during shutdown to cleanup after ourselves.
     */
    protected void releaseDatabaseResources() {
        LOGGER.debug("releasing database resources "+debugInfo);
        for( ResultSet rs : resultsCleanup ){
            try {
                if( rs != null ){
                    rs.close();
                }
            }catch( Exception e){
                LOGGER.error(
                        "Unable to close a ResultSet, possibly because of a previous error: "
                        +debugInfo, e);
            }
        }
        resultsCleanup.clear();
        for( PreparedStatement ps : statementCleanup ){
            try {
                if (ps != null ){
                    ps.close();
                }
            } catch (Exception e) {
                LOGGER.error(
                    "Unable to close a PreparedStatement, possibly because of a previous error."
                    + debugInfo, e);
            }
        }
        statementCleanup.clear();
        for( Connection c : connectionCleanup ){
            try {
                if (! c.isClosed() ) {
                    c.close();
                }
            } catch (Exception e) {
                LOGGER.error(
                        "Unable to close a Connection, possibly because of a previous error."
                        +debugInfo, e);
            }
        }
        connectionCleanup.clear();
        LOGGER.debug("finished releasing database resources "+debugInfo);
    }

    /** put a database resource into the cleanup array to be cleaned up by releaseDatabaseResources() */
    protected PreparedStatement capture(PreparedStatement ps){
        statementCleanup.add(ps);
        return ps;
    }

    /** put a database resource into the cleanup array to be cleaned up by releaseDatabaseResources() */
    protected ResultSet capture(ResultSet rs){
        resultsCleanup.add(rs);
        return rs;
    }

    /** put a database resource into the cleanup array to be cleaned up by releaseDatabaseResources() */
    protected Connection capture(Connection conn){
        connectionCleanup.add(conn);
        return conn;
    }

    /** release ResultSet resources, and avoid double closing. */
    protected void release(ResultSet rs)throws SQLException {
        resultsCleanup.remove(rs);
        rs.close();
    }

    public void run()
    {
        LOGGER.info("about to enter run() loop: "+debugInfo);
        while (!shutdownRequested) {
            LOGGER.debug("top of outer while loop in run(): "+debugInfo);
            int loopsLeft = maxIterationsBetweenRestart ;
            try{
                if(init()==true){
                    LOGGER.debug("init()=true, entering inner while loop in run(): "+debugInfo);
                    while (!shutdownRequested && (loopsLeft-- > 0)) {
                        LOGGER.debug("top of inner while loop in run() loop "
                            +loopsLeft+" of "+debugInfo);
                        checkSanity();
                        LOGGER.debug("sanity check ok, about to doWork(): "+debugInfo);
                        doWork();
                        LOGGER.debug("doWork() completed ok, sleep for next iteration: "+debugInfo);
                        doSleep(sleepTime);
                    }
                }
            }catch (Exception e) {
                LOGGER.error( "Exception thread will now sleep,init(),retry: "+debugInfo ,e);
            }
            if(shutdownRequested){
                LOGGER.info( "shutdown requested, release database resources: "+debugInfo);
                releaseDatabaseResources();
            }else if(loopsLeft > 0){
                //we are here because init() failed or because of exception.
                //we must sleep to avoid chewing up cpu because of an error condition.
                doSleep(errorSleepTime);
            }else{
                //either shutdown was requested or we reached the maxIterationsBetweenRestart
                LOGGER.info("releasing db resources and re-initializing after "
                    +maxIterationsBetweenRestart+" inner loop iterations in run(): "+debugInfo);
                releaseDatabaseResources();
            }
        }
        LOGGER.info("shutdown complete."+debugInfo);
    }

    protected void doSleep(int msec){
        try{
            Thread.sleep(msec);
        }catch (InterruptedException ie) {
            LOGGER.warn("Slave sleep interrupted, ignoring:"+debugInfo, ie);
        }
    }

    protected void logMem(String msg){
        Runtime r=java.lang.Runtime.getRuntime();
        long max = r.maxMemory();
        long free = r.freeMemory();
        long total = r.totalMemory();
        long used = total-free;
        LOGGER.info( "("+msg+") memory stats: max="+max+" free="+free+" total="+total+" used="+used);
    }

    public synchronized void shutdown() {
        shutdownRequested = true;
        LOGGER.info("Shutting down slave: " + debugInfo);
    }

    private String getKey(String key)throws Exception {
        return "bruce." + getConfigPrefix() + key ;
    }


    // --------- protected class that have get/set ----------- //
    protected boolean shutdownRequested = false;
    public void setShutdownRequested (boolean  shutdownRequested ){
        this.shutdownRequested = shutdownRequested ;
    }
    public boolean getShutdownRequested (){
        return shutdownRequested ;
    }

    protected int sleepTime ;
    public void setSleepTime(int sleepTime ){
        this.sleepTime=sleepTime;
    }
    public int getSleepTime(){
        return sleepTime;
    }

    protected int errorSleepTime;
    public void setErrorSleepTime(int errorSleepTime ){
        this.errorSleepTime=errorSleepTime;
    }
    public int getErrorSleepTime(){
        return errorSleepTime;
    }

    protected int maxIterationsBetweenRestart ;
    public void setMaxIterationsBetweenRestart(int maxIterationsBetweenRestart ){
        this.maxIterationsBetweenRestart=maxIterationsBetweenRestart;
    }
    public int getMaxIterationsBetweenRestart(){
        return maxIterationsBetweenRestart;
    }

    protected String driverName ;
    public String getDriverName (){
        return driverName ;
    }
    public void setDriverName (String driverName  ){
        this.driverName =driverName ;
    }

    protected String debugInfo ;
    protected String getDebugInfo (){
        return debugInfo ;
    }
    protected void setDebugInfo (String debugInfo  ){
        this.debugInfo =debugInfo ;
    }

    protected BruceProperties properties;
    public BruceProperties getProperties(){
        return this.properties;
    }
    public void setProperties(BruceProperties properties ){
        this.properties=properties;
    }

    // --------- protected class variables that dont have get/set ----------- //
    protected Logger LOGGER = Logger.getLogger(DaemonThread.class);
    protected Logger getLogger(){
        return LOGGER;
    }
    protected void setLogger(Logger logger ){
        LOGGER=logger;
    }

    // --------- protected class variables ----------- //
    private HashSet<ResultSet>  resultsCleanup ;
    private HashSet<PreparedStatement> statementCleanup ;        
    private HashSet<Connection> connectionCleanup ;        

    // --------- Static Constants ------------ //
    protected static final String SLEEP_KEY = "Sleep";
    protected static final int SLEEP_DEFAULT = 1000;

    protected static final String ERROR_SLEEP_KEY = "ErrorSleep";
    protected static final int  ERROR_SLEEP_DEFAULT = 30000;

    protected static final String MAX_LOOP_KEY = "MaxLoops" ;
    protected static final int  MAX_LOOP_DEFAULT = 100 ;

    protected static final String JDBC_DRIVER_NAME_KEY = "bruce.jdbcDriverName";
    protected static final String  JDBC_DRIVER_NAME_DEFAULT ="org.postgresql.Driver";

}
