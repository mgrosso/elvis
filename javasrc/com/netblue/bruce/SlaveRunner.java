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

import com.netblue.bruce.cluster.Cluster;
import com.netblue.bruce.cluster.Node;
import com.netblue.bruce.cluster.RegExReplicationStrategy;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.dbcp.DelegatingConnection;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.postgresql.PGConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import static java.text.MessageFormat.format;
import java.util.ArrayList;
import java.util.HashSet;
import javax.sql.DataSource;

/**
 * Responsible for obtaining {@link com.netblue.bruce.Snapshot}s from the <code>SnapshotCache</code>
 *
 * @author lanceball
 * @version $Id: SlaveRunner.java 85 2007-09-06 22:19:38Z rklahn $
 */
public class SlaveRunner implements Runnable
{
    public SlaveRunner(final DataSource masterDataSource, final Cluster cluster, final Node node){
        this.node = node;
        this.cluster = cluster;
        this.masterDataSource = masterDataSource;
        sleepTime=NEXT_SNAPSHOT_UNAVAILABLE_SLEEP_DEFAULT;
        errorSleepTime=ERROR_SLEEP_DEFAULT;
        // very important that we only ever initialize the query cache once.
        queryCache = new QueryCache();
    }

    private boolean init(){
        properties = new BruceProperties();
        // Get our query strings
        selectLastSnapshotQuery = properties.getProperty(SNAPSHOT_STATUS_SELECT_KEY, SNAPSHOT_STATUS_SELECT_DEFAULT);
        updateLastSnapshotQuery = properties.getProperty(SNAPSHOT_STATUS_UPDATE_KEY, SNAPSHOT_STATUS_UPDATE_DEFAULT);
        slaveTransactionIdQuery = properties.getProperty(SLAVE_UPDATE_TRANSACTION_ID_KEY, SLAVE_UPDATE_TRANSACTION_ID_DEFAULT);
        applyTransactionsQuery = properties.getProperty(APPLY_TRANSACTION_KEY, APPLY_TRANSACTION_DEFAULT);
        daemonModeQuery = properties.getProperty(DAEMONMODE_QUERY_ID_KEY, DAEMONMODE_QUERY_ID_DEFAULT);
        normalModeQuery = properties.getProperty(NORMALMODE_QUERY_ID_KEY, NORMALMODE_QUERY_ID_DEFAULT);
	slaveTableIDQuery = properties.getProperty(SLAVE_TABLE_ID_KEY,SLAVE_TABLE_ID_DEFAULT);
        sleepTime = properties.getIntProperty(NEXT_SNAPSHOT_UNAVAILABLE_SLEEP_KEY, NEXT_SNAPSHOT_UNAVAILABLE_SLEEP_DEFAULT);
        errorSleepTime = properties.getIntProperty(ERROR_SLEEP_KEY, ERROR_SLEEP_DEFAULT);
        transactionLogFetchSize  = properties.getIntProperty( TRANSACTIONLOG_FETCH_SIZE_KEY, TRANSACTIONLOG_FETCH_SIZE_DEFAULT );

        plusNSnapshotQuery = properties.getProperty( PLUSN_SNAPSHOT_QUERY_KEY, PLUSN_SNAPSHOT_QUERY_DEFAULT);
        getOutstandingTransactionsQuery = properties.getProperty(GET_OUTSTANDING_TRANSACTIONS_KEY,GET_OUTSTANDING_TRANSACTIONS_DEFAULT);
        // Setup our data source
        dataSource.setDriverClassName(properties.getProperty("bruce.jdbcDriverName", "org.postgresql.Driver"));
        dataSource.setValidationQuery(properties.getProperty("bruce.poolQuery", "select now()"));
        dataSource.setUrl(node.getUri());
        dataSource.setAccessToUnderlyingConnectionAllowed(true);

        try
        {
            LOGGER.info("Replicating node: " + node.getName() + " at " + node.getUri());
            RegExReplicationStrategy strategy = new RegExReplicationStrategy(dataSource);
            final ArrayList<String> replicatedTables = strategy.getTables(node, null);
            LOGGER.info("Replicating " + replicatedTables.size() + " tables");
            for (String table : replicatedTables)
            {
                LOGGER.info("Replicating table: " + table);
            }

            // creates a connection and all of our prepared statements
            initializeDatabaseResources();
            initializeMasterDatabaseResources();

            // Now get the last snapshot processed from the DB
            setLastProcessedSnapshot();
            if (getLastProcessedSnapshot() == null) {
                LOGGER.error("Cannot replicate slave node.  No starting point has been identified.  Please ensure that " +
                        "the slavesnapshotstatus table on " + this.node.getUri() + " has been properly initialized.");
                return false;
            }
            return true;
        }
        catch (SQLException e)
        {
            final String errorMessage = format(
                    "Unable to obtain a connection to slave node.  Cluster node {0} at {1} will not be replicated.",
                    node.getName(), node.getUri());
            LOGGER.error(errorMessage, e);
        }
        return false;
    }

    /**
     * Gets a DB connection, and ensures that all {@link java.sql.PreparedStatement}s we have are valid.
     *
     * @return
     *
     * @throws SQLException
     */
    private Connection getConnection() throws SQLException
    {
        if (!hasValidConnection())
        {
            initializeDatabaseResources();
        }
        return theOneConnection;
    }

    /**
     * Gets the master DB connection, and ensures that all {@link java.sql.PreparedStatement}s we have are valid.
     *
     * @return
     *
     * @throws SQLException
     */
    private Connection getMasterConnection() throws SQLException
    {
        if (!hasValidMasterConnection())
        {
            initializeMasterDatabaseResources();
        }
        return masterConnection;
    }

    /**
     * Checks the state of our connection
     *
     * @return true if we have a valid, open connection
     */
    private boolean hasValidConnection()
    {
        try
        {
            return (theOneConnection != null && !theOneConnection.isClosed());
        }
        catch (SQLException e)
        {
            LOGGER.error(e);
        }
        return false;
    }

    /**
     * Checks the state of our connection
     *
     * @return true if we have a valid, open connection
     */
    private boolean hasValidMasterConnection()
    {
        try
        {
            return (masterConnection != null && !masterConnection.isClosed());
        }
        catch (SQLException e)
        {
            LOGGER.error(e);
        }
        return false;
    }

    /**
     * Opens a connection to the database, sets our internal instance to that connection, and initializes all
     * PreparedStatments we will use.
     *
     * @throws SQLException
     */
    private void initializeDatabaseResources() throws SQLException
    {
        theOneConnection = dataSource.getConnection();
        theOneConnection.setAutoCommit(false);
        theOneConnection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        try
        {
            PGConnection theOneConnectionPg =
                    (PGConnection) ((DelegatingConnection) theOneConnection).getInnermostDelegate();
            theOneConnectionPg.setPrepareThreshold(1);
        }
        catch (Throwable t)
        {
            LOGGER.debug("Throwable when setting Pg JDBC Prepare Threshold. Proceding anyways.", t);
        }
        prepareStatements();
    }

    /**
     * Opens a connection to the database, sets our internal instance to that connection, and initializes all
     * PreparedStatments we will use.
     *
     * @throws SQLException
     */
    private void initializeMasterDatabaseResources() throws SQLException
    {
        masterConnection = masterDataSource.getConnection();
        masterConnection.setAutoCommit(false);
        //masterConnection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        prepareMasterStatements();
    }

    /**
     * Releases all database resources used by this slave.  Used during shutdown to cleanup after ourselves.
     */
    private void releaseDatabaseResources() {
        try {
            closeStatements();
            if (hasValidConnection()) {
                getConnection().close();
            }
        } catch (Exception e) {
            LOGGER.error("Unable to close slave database resources.", e);
        }
        try {
            closeMasterStatements();
            if (hasValidMasterConnection()) {
                getMasterConnection().close();
            }
        } catch (Exception e) {
            LOGGER.error("Unable to close master database resources.", e);
        }
    }

    private void closeStatements() throws SQLException {
        selectLastSnapshotStatement.close();
        updateLastSnapshotStatement.close();
        slaveTransactionIdStatement.close();
        applyTransactionsStatement.close();
        slaveTableIDStatement.close();
    }

    private void closeMasterStatements() throws SQLException {
        plusNSnapshotStatement.close();
        getOutstandingTransactionsStatement.close();
    }

    /**
     * Prepares all of the {@link java.sql.PreparedStatement}s we need for this class.  Assumes a valid and open {@link
     * #theOneConnection} with auto commit off.
     *
     * @throws SQLException
     */
    private void prepareStatements() throws SQLException {
        Connection connection = getConnection();
        selectLastSnapshotStatement = connection.prepareStatement(selectLastSnapshotQuery);
        updateLastSnapshotStatement = connection.prepareStatement(updateLastSnapshotQuery);
        slaveTransactionIdStatement = connection.prepareStatement(slaveTransactionIdQuery);
        applyTransactionsStatement = connection.prepareStatement(applyTransactionsQuery);
	slaveTableIDStatement = connection.prepareStatement(slaveTableIDQuery);
        connection.commit();
    }

    /**
     * Prepares all of the master database {@link java.sql.PreparedStatement}s we need for this class.  Assumes a valid and open {@link
     * #masterConnection} with auto commit off.
     *
     * @throws SQLException
     */
    private void prepareMasterStatements() throws SQLException {
        Connection connection = getMasterConnection();
        plusNSnapshotStatement = connection.prepareStatement(plusNSnapshotQuery);
        getOutstandingTransactionsStatement = connection.prepareStatement(getOutstandingTransactionsQuery);
        connection.commit();
    }

    public void run()
    {
        while (!shutdownRequested) {
            try{
                if(init()==true){
                    while (!shutdownRequested) {
                        doWork();
                        doSleep(sleepTime);
                    }
                }
            }catch (Exception e) {
                LOGGER.error("SlaveRunner caught exception at toplevel. will sleep,init(),retry:", e);
            }
            //we are here because init() failed or because of exception.
            releaseDatabaseResources();
            doSleep(errorSleepTime);
        }
        releaseDatabaseResources();
        LOGGER.info(node.getName() + " shutdown complete.");
    }

    public void doSleep(int msec){
        try{
            Thread.sleep(msec);
        }catch (InterruptedException ie) {
            LOGGER.warn("Slave sleep interrupted, ignoring.", ie);
        }
    }

    private void doWork()throws SQLException {
        LOGGER.trace("Last processed snapshot: " + lastProcessedSnapshot);
        if (getLastProcessedSnapshot() == null) {
            LOGGER.error("doWork(): BUG: getLastProcessedSnapshot() returns null, which implies init() failed, but we should never get here if init failed.");
        }
        final Snapshot nextSnapshot = getNextSnapshot();
        if (nextSnapshot != null){
            processSnapshot(nextSnapshot);
        }
    }

    /**
     * Gets the last <code>Snapshot</code> object successfully processed by this node or null if this node has no
     * replicated data (that it is aware of).  Does not go to the database.  <code>SlaveRunner</code>s maintain last
     * processed status in memory.
     *
     * @return the last {@link com.netblue.bruce.Snapshot} or null
     */
    public Snapshot getLastProcessedSnapshot()
    {
        LOGGER.trace("Last processed snapshot: " + lastProcessedSnapshot);
        return lastProcessedSnapshot;
    }

    /**
     * Updates the slave node with transactions from <code>snapshot</code> and sets this node's status table with the
     * latest snapshot status - atomically.
     *
     * @param snapshot the <code>Snapshot</code> to process
     */
    protected void processSnapshot(final Snapshot snapshot)
    {
        LOGGER.trace("Processing next snapshot: " + snapshot.getCurrentXid());
        Connection connection = null;
        try
        {
            connection = getConnection();
            connection.setSavepoint();
            applyAllChangesForTransaction2(snapshot);
            updateSnapshotStatus(snapshot);
            connection.commit();
            this.lastProcessedSnapshot = snapshot;
        }
        catch (SQLException e)
        {
            LOGGER.error("Cannot commit last processed snapshot.", e);
            try
            {
                if (connection != null)
                {
                    connection.rollback();
                    connection.close();
                }
            }
            catch (SQLException e1)
            {
                LOGGER.error("Unable to rollback last processed snapshot transaction.", e);
            }
        }
    }

    /**
     * Queries for the latest processed <code>Snapshot</code> from the slavesnapshotstatus table
     *
     * @return the last known <code>Snapshot</code> to have been processed by this node or null if this node has not
     *         processed any <code>Snapshot</code>s.  Not private simply for testing puposes.
     */
    private void setLastProcessedSnapshot() throws SQLException {
        Connection connection = getConnection();
        selectLastSnapshotStatement.setLong(1, this.cluster.getId());
        // If nothing is in the result set, then our lastProcessedSnapshot is null
        final ResultSet resultSet = selectLastSnapshotStatement.executeQuery();
        if (resultSet.next()) {
            lastProcessedSnapshot = new Snapshot(new TransactionID(resultSet.getLong("master_current_xaction")),
                                    new TransactionID(resultSet.getLong("master_min_xaction")),
                                    new TransactionID(resultSet.getLong("master_max_xaction")),
                                    resultSet.getString("master_outstanding_xactions"));
        }
        resultSet.close();
        connection.rollback();
    }

    /**
     * this method exists to support the testProcessSnapshotUpdatesStatus() test in our test class.
     *
     * @return the last known <code>Snapshot</code> to have been processed by this node or null if this node has not
     *         processed any <code>Snapshot</code>s.  Not private simply for testing puposes.
     */
    protected Snapshot queryForLastProcessedSnapshot() throws SQLException {
        setLastProcessedSnapshot();
        return lastProcessedSnapshot;
    }

    /**
     * Applies all outstanding {@link com.netblue.bruce.Change}s to this slave
     *
     * @param snapshot A {@link com.netblue.bruce.Snapshot} containing the latest master snapshot.
     */
    private void applyAllChangesForTransaction2(final Snapshot snapshot) throws SQLException {
        // This method is part of a larger transaction.  We don't validate/get
        // the connection here, because if the connection becomes invalid as a
        // part of that larger transaction, we're screwed anyway and we don't
        // want to create a new connection for just part of the transaction
        if (snapshot == null) {
            LOGGER.trace("Latest Master snapshot is null");
            return;
        } 

        LOGGER.trace("getting transactions for snapshot with current xid: "
                +snapshot.getCurrentXid());

        Connection masterConnection = getMasterConnection();
        getOutstandingTransactionsStatement.setFetchSize(transactionLogFetchSize);
        getOutstandingTransactionsStatement.setLong(
                1,lastProcessedSnapshot.getMinXid().getLong());
        getOutstandingTransactionsStatement.setLong(
                2,snapshot.getMaxXid().getLong());
        ResultSet txrs = getOutstandingTransactionsStatement.executeQuery();

        ArrayList<TransactionLogRow> txrows = new ArrayList<TransactionLogRow>(transactionLogFetchSize);
        txrows=pullChanges( txrs, txrows, transactionLogFetchSize );
        if(txrows.size()<transactionLogFetchSize){
            //good, we can release master resources asap 
            LOGGER.trace("release master db resources early.");
            txrs.close();
            masterConnection.rollback();
            applyChanges(txrows,snapshot,masterConnection);
        }else{
            //we have a backlog of more than x rows, so we'll have to keep our
            //master connection open while we update the child. this is to
            //avoid having the memory size of the daemon need to be the sum of the 
            //backlog size of all the slaves.
            while(txrows.size()>0){
                applyChanges(txrows,snapshot,masterConnection);
                if(txrows.size()==transactionLogFetchSize){
                    txrows=pullChanges( txrs, txrows, transactionLogFetchSize );
                }else{
                    break;
                }
            }
            LOGGER.trace("... and finally release master db resources.");
            txrs.close();
            masterConnection.rollback();
        }
    }

    private ArrayList<TransactionLogRow> pullChanges(
        ResultSet txrs, ArrayList<TransactionLogRow> txrows, int max )
    throws SQLException {
        int i=0;
        txrows.clear();
        while(txrs.next()){
            txrows.add(new TransactionLogRow(txrs));
            if( ++i == max ){ //more than or exactly max records were in resultset.
                return txrows;
            }
        }
        //less than max records were in resultset.
        return txrows; 
    }

    private void applyChanges( 
        ArrayList<TransactionLogRow> txrows, final Snapshot snapshot, Connection masterC ) 
    throws SQLException {
        HashSet<String> slaveTables = getSlaveTables();
        LOGGER.trace("Processing changes for chunk of " +txrows.size());
        for( TransactionLogRow tlr : txrows ){
            TransactionID tid = tlr.getXaction();
            // Skip transactions not between snapshots
            if (!(lastProcessedSnapshot.transactionIDGE(tid)&&snapshot.transactionIDLT(tid))){
                LOGGER.trace("skipping transaction not between snapshots. tid:"+tid+
                     " lastslaveS:"+lastProcessedSnapshot+" masterS:"+snapshot);
                continue;
            }
            String debug_detail = 
                     " xid:"+tlr.getRowid()+
                     " tid:"+tid+
                     " tabname:"+tlr.getTabname()+
                     " cmdtype:"+tlr.getCmdtype()+
                     " info:"+tlr.getInfo() ;
            if (!slaveTables.contains(tlr.getTabname())){
                LOGGER.trace("NOT applying change. Table not replicated on slave."+debug_detail);
                continue;
            }
            LOGGER.trace("Applying change."+debug_detail);
            // call query cache
            QueryParams queryParams = queryCache.getQueryInfo(
                tlr.getCmdtype(),tlr.getTabname(),tlr.getInfo(), masterC );

            applyTransactionsStatement.setInt(1, queryParams.getIndex());
            applyTransactionsStatement.setInt(2, queryParams.getNumParamTypes());
            applyTransactionsStatement.setString(3, queryParams.getQuery());
            applyTransactionsStatement.setString(4, queryParams.getParamTypeNames());
            applyTransactionsStatement.setString(5, queryParams.getParamInfoIndices());
            applyTransactionsStatement.setString(6, tlr.getInfo());
            applyTransactionsStatement.execute();
            LOGGER.trace("Change applied");
        }
    }

    /**
     * Gets the next snapshot from the master database. Will return null if no next snapshot
     * available.
     *
     * @return the next Snapshot when it becomes available
     */
    private Snapshot getNextSnapshot() throws SQLException {
        LOGGER.trace("Getting next snapshot");
	Snapshot retVal = null;
        final Snapshot processedSnapshot = getLastProcessedSnapshot();	
        ResultSet rs;
        for (long l:new long[]{100000L, 50000L, 25000L, 10000L, 5000L, 2000L, 1000L,
                               500L,250L,125L,100L,75L,50L,25L,10L,5L,4L,3L,2L,1L}) {
            LOGGER.trace("trying lastProcessedSnapshot +"+l);
            retVal = null;
            plusNSnapshotStatement.setLong(1,processedSnapshot.getCurrentXid().getLong());
            plusNSnapshotStatement.setLong(2,l);
            plusNSnapshotStatement.setLong(3,processedSnapshot.getMinXid().getLong());
            plusNSnapshotStatement.setLong(4,processedSnapshot.getMinXid().getLong());
            plusNSnapshotStatement.setLong(5,processedSnapshot.getMaxXid().getLong());
            rs=plusNSnapshotStatement.executeQuery();
            if (rs.next()) {
                retVal = new Snapshot(new TransactionID(rs.getLong("current_xaction")),
                                      new TransactionID(rs.getLong("min_xaction")),
                                      new TransactionID(rs.getLong("max_xaction")),
                                      rs.getString("outstanding_xactions"));
                LOGGER.trace("Retrived "+retVal);
                if (snapshotLT(processedSnapshot,retVal)) {
                    return retVal;
                } else {
                    LOGGER.trace("However, retrived snapshot less than lastProcessedSnapshot");
                    retVal=null;
                }
            } else {
                LOGGER.trace("No snapshot >= lastProcessedSnapshot +"+l);
            }
        }
        return retVal;
    }


    private boolean snapshotLT(Snapshot lesserSnapshot, Snapshot greaterSnapshot) {
	if (lesserSnapshot.getMinXid().equals(greaterSnapshot.getMinXid())) {
	    return (lesserSnapshot.getMaxXid().compareTo(greaterSnapshot.getMaxXid())<0);
	} else {
	    return (lesserSnapshot.getMinXid().compareTo(greaterSnapshot.getMinXid())<0);
	}
    }

    /**
     * Updates the SLAVESNAPSHOTSTATUS table with <code>Snapshot</code> data
     *
     * @param snapshot the <code>Snapshot</code> to update with
     *
     * @throws SQLException if this cluster does not already has a row in SLAVESNAPSHOTSTAUS
     */
    private void updateSnapshotStatus(final Snapshot snapshot) throws SQLException
    {
        // This method is part of a larger transaction.  We don't validate/get the connection here,
        // because if the connection becomes invalid as a part of that larger transaction, we're screwed
        // anyway and we don't want to create a new connection for just part of the transaction        
        updateLastSnapshotStatement.setLong(1, getCurrentTransactionId());
        updateLastSnapshotStatement.setLong(2, new Long(snapshot.getCurrentXid().toString()));
        updateLastSnapshotStatement.setLong(3, new Long(snapshot.getMinXid().toString()));
        updateLastSnapshotStatement.setLong(4, new Long(snapshot.getMaxXid().toString()));
        updateLastSnapshotStatement.setString(5, snapshot.getInFlight());
        updateLastSnapshotStatement.setLong(6, cluster.getId());
        updateLastSnapshotStatement.execute();
    }

    /**
     * Helper method to get the transaction ID of the currently executing transaction. If no transaction is active, a
     * new transaction is created just to get it's ID - which is sort of pointless...
     *
     * @return The transaction ID of the currently executing transaction
     *
     * @throws SQLException
     */
    private long getCurrentTransactionId() throws SQLException
    {
        // This method is part of a larger transaction.  We don't validate/get the connection here,
        // because if the connection becomes invalid as a part of that larger transaction, we're screwed
        // anyway and we don't want to create a new connection for just part of the transaction        
        final ResultSet resultSet = slaveTransactionIdStatement.executeQuery();
        resultSet.next();
        long xaction_id = resultSet.getLong("transaction");
        resultSet.close();
        return xaction_id;
    }

    public synchronized void shutdown()
    {
        LOGGER.info("Shutting down slave: " + node.getName());
        shutdownRequested = true;
    }

    private HashSet<String> getSlaveTables() throws SQLException {
        if( slaveTables != null && slaveTables.size() > 0 ){
            return slaveTables;
        }
        LOGGER.trace("fetching Slave Tables from database:");
	slaveTables = new HashSet<String>();
	ResultSet rs = slaveTableIDStatement.executeQuery();
	while (rs.next()) {
            String table = rs.getString("tablename");
	    slaveTables.add(table);
            LOGGER.trace(table);
	}
	rs.close();
	return slaveTables;
    }
    
    // --------- Class fields ---------------- //
    private boolean shutdownRequested = false;
    private BruceProperties properties;
    private Connection theOneConnection;
    private Connection masterConnection;
    private Snapshot lastProcessedSnapshot;
    private PreparedStatement selectLastSnapshotStatement;
    private PreparedStatement updateLastSnapshotStatement;
    private PreparedStatement slaveTransactionIdStatement;
    private PreparedStatement applyTransactionsStatement;
    private PreparedStatement slaveTableIDStatement;
    private PreparedStatement plusNSnapshotStatement;
    private PreparedStatement getOutstandingTransactionsStatement;
    private HashSet<String> slaveTables ;

    private int sleepTime;
    private int errorSleepTime;
    private int transactionLogFetchSize;
    private String selectLastSnapshotQuery;
    private String updateLastSnapshotQuery;
    private String slaveTransactionIdQuery;
    private String applyTransactionsQuery;
    private String daemonModeQuery;
    private String normalModeQuery;
    private String slaveTableIDQuery;
    private String getOutstandingTransactionsQuery;
    private String plusNSnapshotQuery;
    private BasicDataSource dataSource = new BasicDataSource();

    // --------- Constants ------------------- //
    private final Node node;
    private final Cluster cluster;
    private final DataSource masterDataSource;

    // --------- Static Constants ------------ //
    private static final Logger LOGGER = Logger.getLogger(SlaveRunner.class);

    // Daemon mode for inserting data into the slave's replicated tables
    private static final String DAEMONMODE_QUERY_ID_KEY = "bruce.daemonmode.query";
    private static final String DAEMONMODE_QUERY_ID_DEFAULT = "select bruce.daemonmode()";

    // Normal mode to keep replicated tables read only
    private static final String NORMALMODE_QUERY_ID_KEY = "bruce.normalmode.query";
    private static final String NORMALMODE_QUERY_ID_DEFAULT = "select bruce.normalmode()";

    // Apply transactions to a slave
    //private static final String APPLY_TRANSACTION_KEY = "bruce.applytransaction.query";
    //private static final String APPLY_TRANSACTION_DEFAULT = "select bruce.applyLogTransaction(?, ?, ?)";

    // Apply transactions to a slave
    private static final String APPLY_TRANSACTION_KEY = "bruce.applytransaction.query";
    private static final String APPLY_TRANSACTION_DEFAULT = "select bruce.applyLogTransaction2(?, ?, ?, ?, ?, ?)";

    // Query the status table
    private static final String SNAPSHOT_STATUS_SELECT_KEY = "bruce.slave.query";
    private static final String SNAPSHOT_STATUS_SELECT_DEFAULT = new StringBuilder()
            .append("select * from bruce.slavesnapshotstatus ")
            .append("where clusterid = ?").toString();

    // Update existing record in status table
    private static final String SNAPSHOT_STATUS_UPDATE_KEY = "bruce.slave.updatestatus";
    private static final String SNAPSHOT_STATUS_UPDATE_DEFAULT = new StringBuilder()
            .append("update bruce.slavesnapshotstatus ")
            .append("set slave_xaction = ?,  master_current_xaction = ?, master_min_xaction = ?, master_max_xaction = ?, ")
            .append("master_outstanding_xactions = ?, update_time = now() where clusterid = ?").toString();

    // Get transaction ID for slave update transaction
    private static final String SLAVE_UPDATE_TRANSACTION_ID_KEY = "bruce.slave.select.transactionid";
    private static final String SLAVE_UPDATE_TRANSACTION_ID_DEFAULT = new StringBuilder()
            .append("select * from pg_locks where pid = pg_backend_pid()")
            .append(" and locktype = 'transactionid'").toString();

    // Query to determine tables that have Slave trigger
    private static final String SLAVE_TABLE_ID_KEY = "bruce.slave.hasSlaveTrigger";
    private static final String SLAVE_TABLE_ID_DEFAULT = 
	"select n.nspname||'.'||c.relname as tablename from pg_class c, pg_namespace n "+
	" where c.relnamespace = n.oid "+
	"   and c.oid in (select tgrelid from pg_trigger "+
	"                  where tgfoid = (select oid from pg_proc "+
	"                                   where proname = 'denyaccesstrigger' "+
	"                                     and pronamespace = (select oid from pg_namespace "+
	"                                                          where nspname = 'bruce')))";

    // Query to determine the next snapshot, when nextNormalXID < lastNormalXID
    private static final String NEXT_SNAPSHOT_SIMPLE_KEY = "bruce.slave.nextSnapshotSimple";
    private static final String NEXT_SNAPSHOT_SIMPLE_DEFAULT =
	"select * from bruce.snapshotlog "+
	" where current_xaction not in (?,?,?) "+
	"   and current_xaction >= ? "+
	"   and current_xaction <= ? "+
	" order by current_xaction desc limit 1";

    // Query to determine the next snapshot, when nextNormalXID > lastNormalXID
    private static final String NEXT_SNAPSHOT_WRAPAROUND_KEY = 
	"bruce.slave.nextSnapshotWraparound";
    private static final String NEXT_SNAPSHOT_WRAPAROUND_DEFAULT =
	"select * from bruce.snapshotlog "+
	" where current_xaction not in (?,?,?) "+
	"   and ((current_xaction >= ? and current_xaction <= ?) "+
	"     or (current_xaction >= ? and current_xaction <= ?)) "+
	" order by current_xaction desc limit 1";

    private static final String GET_OUTSTANDING_TRANSACTIONS_KEY =
	"bruce.slave.getOutstandingTransactions";
    private static final String GET_OUTSTANDING_TRANSACTIONS_DEFAULT =
	"select * from bruce.transactionlog where xaction >= ? and xaction < ? order by rowid asc";
    
    private static final String PLUSN_SNAPSHOT_QUERY_KEY = "bruce.slave.plusNSnapshotQuery" ;
    private static final String PLUSN_SNAPSHOT_QUERY_DEFAULT =
	"select * from bruce.snapshotlog "+
	// 4,294,967,296 == 2^32, maximum transaction id, wraps around back at this point
	" where current_xaction >= (? + ?) % 4294967296 "+ 
	// Scans for a snapshot that is actualy greater than the current snapshot
	"   and ((min_xaction > ?) or ((min_xaction = ?) and (max_xaction > ?))) "+
	" order by current_xaction asc limit 1";

    // How long to wait if a 'next' snapshot is unavailable, in miliseconds
    private static final String NEXT_SNAPSHOT_UNAVAILABLE_SLEEP_KEY = "bruce.nextSnapshotUnavailableSleep";
    // This default value may need some tuning. 100ms seemed too small, 1s might be right
    private static int NEXT_SNAPSHOT_UNAVAILABLE_SLEEP_DEFAULT = 1000;

    private static final String ERROR_SLEEP_KEY = "bruce.slaveErrorSleep";
    // This default value may need some tuning. 100ms seemed too small, 1s might be right
    private static int ERROR_SLEEP_DEFAULT = 30000;


    private static final String TRANSACTIONLOG_FETCH_SIZE_KEY = "bruce.transactionLogFetchSize";
    private static int TRANSACTIONLOG_FETCH_SIZE_DEFAULT = 5000;
    
    private QueryCache queryCache;
}
