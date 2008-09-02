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
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.text.MessageFormat;
import java.util.Properties;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import static java.text.MessageFormat.format;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.SortedSet;
import java.util.TreeSet;
import java.lang.ClassNotFoundException ;
import java.lang.InstantiationException ;
import java.lang.IllegalAccessException ;
import java.lang.RuntimeException ;

/**
 * Responsible for obtaining {@link com.netblue.bruce.Snapshot}s from the <code>SnapshotCache</code>
 *
 * @author lanceball
 * @version $Id: SlaveRunner.java 85 2007-09-06 22:19:38Z rklahn $
 */
public class SlaveRunner extends DaemonThread {

    private SlaveRunner(){
        //not implemented.
    }

    public SlaveRunner(final Cluster cluster, final Node node){
        masterUri = cluster.getMaster().getUri();
        masterId = cluster.getId();
        nodeUri = node.getUri();
        nodeName = node.getName();
        setDebugInfo( "slave from cluster "+masterId +" to "+node.getId()+" : "+nodeName);

        resultsCleanup = new ArrayList<ResultSet>();
        connectionCleanup = new ArrayList<Connection>();
        statementCleanup = new ArrayList<PreparedStatement>();
        // very important that we only ever initialize the query cache once.
        queryCache = new QueryCache();
    }

    protected boolean childInit() throws Exception {
        properties = new BruceProperties();
        // Get our query strings
        selectLastSnapshotQuery = 
            properties.getProperty(SNAPSHOT_STATUS_SELECT_KEY, SNAPSHOT_STATUS_SELECT_DEFAULT);
        updateLastSnapshotQuery = 
            properties.getProperty(SNAPSHOT_STATUS_UPDATE_KEY, SNAPSHOT_STATUS_UPDATE_DEFAULT);
        slaveTransactionIdQuery = properties.getProperty(
            SLAVE_UPDATE_TRANSACTION_ID_KEY, SLAVE_UPDATE_TRANSACTION_ID_DEFAULT);
        slaveTransactionIdPostgresVersion_8_3_HigherQuery = properties.getProperty(
                SLAVE_UPDATE_TRANSACTION_ID_8_3_HIGHER_KEY, SLAVE_UPDATE_TRANSACTION_ID_8_3_HIGHER_DEFAULT);
        applyTransactionsQuery = properties.getProperty(
            APPLY_TRANSACTION_KEY, APPLY_TRANSACTION_DEFAULT);
        slaveTableIDQuery = properties.getProperty(
            SLAVE_TABLE_ID_KEY,SLAVE_TABLE_ID_DEFAULT);
        transactionLogFetchSize  = properties.getIntProperty( 
            TRANSACTIONLOG_FETCH_SIZE_KEY, TRANSACTIONLOG_FETCH_SIZE_DEFAULT );
        inListSize  = properties.getIntProperty( 
            INLIST_SIZE_KEY, INLIST_SIZE_DEFAULT );
        plusNSnapshotQuery = properties.getProperty( 
            PLUSN_SNAPSHOT_QUERY_KEY, PLUSN_SNAPSHOT_QUERY_DEFAULT);
        postgresVersionQuery = properties.getProperty(
            POSTGRES_VERSION_QUERY_KEY, POSTGRES_VERSION_QUERY_DEFAULT);

        createOutstandingTransactionsTmptableQuery = properties.getProperty(
            CREATE_OUTSTANDING_TRANSACTIONS_TMPTABLE_KEY,
            CREATE_OUTSTANDING_TRANSACTIONS_TMPTABLE_DEFAULT);
        insertOutstandingTransactionsQuery = properties.getProperty(
            INSERT_OUTSTANDING_TRANSACTIONS_KEY,
            INSERT_OUTSTANDING_TRANSACTIONS_DEFAULT);
        getTransactionsTmptableQuery = properties.getProperty(
            GET_TRANSACTIONS_TMPTABLE_KEY,
            GET_TRANSACTIONS_TMPTABLE_DEFAULT);
        getTransactionsNoOutstandingQuery = properties.getProperty(
            GET_TRANSACTIONS_NO_OUTSTANDING_KEY,
            GET_TRANSACTIONS_NO_OUTSTANDING_DEFAULT);
        getTransactionsInclauseQuery = properties.getProperty(
            GET_TRANSACTIONS_INCLAUSE_KEY,
            GET_TRANSACTIONS_INCLAUSE_DEFAULT);

        LOGGER.info("Replicating node: " + nodeName + " at " + nodeUri );
        // creates a connection and all of our prepared statements
        initializeDatabaseResources();
        // Now get the last snapshot processed from the DB
        findLastProcessedSnapshot();
        setSlaveTables();
        return true;
    }

    protected void checkSanity()throws Exception {
        if (lastProcessedSnapshot == null) {
            throw new Exception ("Cannot replicate slave node.  No starting point has been "+
                "identified.  Please ensure that the slavesnapshotstatus table on " + 
                nodeUri + " has been properly initialized. this thread will "+
                "sleep and retry");
        }
        if( masterConnection.isClosed() || slaveConnection.isClosed() ){
            throw new Exception("a connection was closed but should not be. Throwing exception so"+
            " init() will happen again, after a sleep.");
        }
        if( slaveTables == null || slaveTables.isEmpty()){
            throw new Exception("Either no tables have the slave trigger, or no something prevented"+
            " the query from succeeding that would have identified the slave tables. Throwing an "+
            "exception so we can sleep and retry.");
        }
        masterConnection.rollback();//just being paranoid.
        slaveConnection.rollback();//just being paranoid.
    }

    protected void doWork()throws Exception {
        final Snapshot nextSnapshot = getNextSnapshot();
        if (nextSnapshot != null){
            processSnapshot(nextSnapshot);
        }
    }

    protected String getConfigPrefix(){
        return "slave.";
    }
    protected Class getChildClass()throws Exception {
        return SlaveRunner.class ;
    }

    /**
     * Gets a slave DB connection.  does no validation. If anything has gone wrong, SQLExceptions will be 
     * caught at top level, and the next init() cycle will take care of it.
     *
     * @return slave connection.
     * @throws SQLException
     */
    private Connection getConnection() throws SQLException {
        return slaveConnection;
    }

    /**
     * Gets a master DB connection.  does no validation. If anything has gone wrong, SQLExceptions will be 
     * caught at top level, and the next init() cycle will take care of it.
     *
     * @return master connection.
     * @throws SQLException
     */
    private Connection getMasterConnection() throws SQLException {
        return masterConnection;
    }

    /**
     * Opens a connection to the database, sets our internal instance to that connection, and initializes all
     * PreparedStatments we will use.
     *
     * @throws SQLException
     */
    private void initializeDatabaseResources() 
    throws Exception{
        //first close anything existing...
        //
        // Setup our database connection, then prepare all statements.
        releaseDatabaseResources();
        slaveConnection = constructConnection(nodeUri );
        connectionCleanup.add(slaveConnection);
        masterConnection = constructConnection(masterUri);
        connectionCleanup.add(masterConnection);
        prepareStatements();
        prepareMasterStatements();
    }

    /**
     * Prepares all of the {@link java.sql.PreparedStatement}s we need for this class.  Assumes a valid and open {@link
     * #slaveConnection} with auto commit off.
     * @throws Exception 
     */
    private void prepareStatements() throws Exception {
        slaveConnection.setSavepoint();
        selectLastSnapshotStatement =   capture(slaveConnection.prepareStatement(selectLastSnapshotQuery));
        updateLastSnapshotStatement =   capture(slaveConnection.prepareStatement(updateLastSnapshotQuery));
        applyTransactionsStatement =    capture(slaveConnection.prepareStatement(applyTransactionsQuery));
        slaveTableIDStatement =         capture(slaveConnection.prepareStatement(slaveTableIDQuery));
        postgresVersionStatement =      capture(slaveConnection.prepareStatement(postgresVersionQuery));
        
        if (isPostgresVersion_8_3_OrHigher()) {
            slaveTransactionIdStatement = capture(slaveConnection.prepareStatement(slaveTransactionIdPostgresVersion_8_3_HigherQuery));
            LOGGER.trace("Using the following slave transaction id query: " + slaveTransactionIdPostgresVersion_8_3_HigherQuery);
        }
        else {
            slaveTransactionIdStatement = capture(slaveConnection.prepareStatement(slaveTransactionIdQuery));
            LOGGER.trace("Using the following slave transaction id query: " + slaveTransactionIdQuery);
        }
        
        slaveConnection.commit();
    }

    /**
     * Prepares all of the master database {@link java.sql.PreparedStatement}s we need for this class.  Assumes a valid and open {@link
     * #masterConnection} with auto commit off.
     *
     * @throws SQLException
     */
    private void prepareMasterStatements() throws SQLException {
        masterConnection.setSavepoint() ;
        plusNSnapshotStatement = capture(masterConnection.prepareStatement(plusNSnapshotQuery));
        masterConnection.commit();
    }

    /**
     * Queries for the postgres version and returns true if the version is 8.3 or higher.
     *
     * @return true if the postgres version is 8.3 or higher, otherwise, false
     * @throws Exception 
     */
    public boolean isPostgresVersion_8_3_OrHigher() throws Exception {
        String version = null;
        LOGGER.trace("fetching Postgres Version from database");
        
        final ResultSet rs = capture(postgresVersionStatement.executeQuery());
        if (rs.next()) {
            version = rs.getString("setting");
        }
        release(rs);
        slaveConnection.rollback();

        if (version == null) {
            throw new Exception("Could not get postgres version from slave");
        }
        
        String versionNumString = null;
        String versionStrings[] = version.split("\\.");
        if (versionStrings != null && versionStrings.length > 1) {
            versionNumString = new StringBuffer().append(versionStrings[0]).append("0").append(versionStrings[1]).toString();
        }
        
        if (versionNumString == null) {
            throw new Exception("Invalid postgres version string from slave: " + version);
        }
        
        LOGGER.trace("Slave Postgres version,version_numeric: " + version + ", " + versionNumString);
        
        int num = Integer.parseInt(versionNumString);
        if (num >= POSTGRES_VERSION_8_3_NUMERIC)
            return true;
        
        return false;
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
        return lastProcessedSnapshot;
    }

    /**
     * Updates the slave node with transactions from <code>snapshot</code> and sets this node's status table with the
     * latest snapshot status - atomically.
     *
     * @param snapshot the <code>Snapshot</code> to process
     */
    protected void processSnapshot(final Snapshot snapshot) throws Exception {
        logMem( "Last processed snapshot: " + lastProcessedSnapshot + " new snapshot: "+snapshot );
        slaveConnection.setSavepoint();
        applyAllChangesForTransaction2(snapshot);
        updateSnapshotStatus(snapshot);
        slaveConnection.commit();
        this.lastProcessedSnapshot = snapshot;
        logMem( "committed all between snapshots: " + lastProcessedSnapshot + " and new snapshot: "+snapshot );
    }

    /**
     * Queries for the latest processed <code>Snapshot</code> from the slavesnapshotstatus table
     *
     * @return the last known <code>Snapshot</code> to have been processed by this node or null if this node has not
     *         processed any <code>Snapshot</code>s.  Not private simply for testing puposes.
     */
    private void findLastProcessedSnapshot() throws SQLException {
        Connection connection = getConnection();
        selectLastSnapshotStatement.setLong(1, masterId );
        // If nothing is in the result set, then our lastProcessedSnapshot is null
        final ResultSet resultSet = capture(selectLastSnapshotStatement.executeQuery());
        if (resultSet.next()) {
            lastProcessedSnapshot = new Snapshot(new TransactionID(resultSet.getLong("master_current_xaction")),
                                    new TransactionID(resultSet.getLong("master_min_xaction")),
                                    new TransactionID(resultSet.getLong("master_max_xaction")),
                                    resultSet.getString("master_outstanding_xactions"));
        }
        release(resultSet);
        connection.rollback();
    }

    /**
     * this method exists to support the testProcessSnapshotUpdatesStatus() test in our test class.
     *
     * @return the last known <code>Snapshot</code> to have been processed by this node or null if this node has not
     *         processed any <code>Snapshot</code>s.  Not private simply for testing puposes.
     */
    protected Snapshot queryForLastProcessedSnapshot() throws SQLException {
        findLastProcessedSnapshot();
        return lastProcessedSnapshot;
    }

    /**
     * Applies all outstanding {@link com.netblue.bruce.Change}s to this slave
     *
     * @param snapshot A {@link com.netblue.bruce.Snapshot} containing the latest master snapshot.
     */
    private void applyAllChangesForTransaction2(final Snapshot snapshot) throws Exception {
        // This method is part of a larger slave side transaction.  We don't
        // validate/get the connection here, because if the connection becomes
        // invalid as a part of that larger transaction, we're screwed anyway
        // and we don't want to create a new connection for just part of the
        // transaction
        if (snapshot == null) {
            LOGGER.trace("Latest Master snapshot is null");
            return;
        } 

        //starting with older  in progress xids that are not in newer in progress list.
        SortedSet<TransactionID> outstanding  = new TreeSet<TransactionID> (lastProcessedSnapshot.getInProgressXids());
        outstanding.removeAll( snapshot.getInProgressXids());
        int outstanding_size = outstanding.size();

        StringBuilder sb = new StringBuilder();
        while(outstanding.size()>0){
            if(sb.length()>0){
                sb.append(',');
            }
            TransactionID xid = outstanding.first();
            outstanding.remove(xid);
            sb.append(xid);
        }
        String xidlist = sb.toString();

        //start transaction
        masterConnection.setSavepoint();
        String getChangesSql;

        if( outstanding_size > inListSize ){
            //create + populate temp table
            //then create sql which uses that.
            PreparedStatement tmptable = capture(masterConnection.prepareStatement(
                createOutstandingTransactionsTmptableQuery));
            tmptable.execute();
            PreparedStatement tmpTableInsert = capture(masterConnection.prepareStatement(
                insertOutstandingTransactionsQuery));
            tmpTableInsert.setString(1,xidlist);
            tmpTableInsert.execute();
            getChangesSql=getTransactionsTmptableQuery ;
        }else if(outstanding_size==0){
            //put the outstanding into a where clause in list.
            getChangesSql=getTransactionsNoOutstandingQuery ;
        }else{
            getChangesSql=MessageFormat.format(getTransactionsInclauseQuery,xidlist);
        }
        long lower_bound = lastProcessedSnapshot.getCurrentXid().getLong() ;
        long upper_bound = snapshot.getCurrentXid().getLong() ;
        LOGGER.trace("sql: "+getChangesSql+" ?1=lastProcessedSnapshot.getCurrentXid()="+ lower_bound + " ?2=snapshot.getCurrentXid()="+ upper_bound );

        PreparedStatement getOutstanding = capture(masterConnection.prepareStatement(getChangesSql));
        getOutstanding.setLong( 1,lastProcessedSnapshot.getCurrentXid().getLong());
        getOutstanding.setLong( 2,snapshot.getMaxXid().getLong());
        getOutstanding.setFetchSize(transactionLogFetchSize);
        ResultSet txrs = capture(getOutstanding.executeQuery());
        applyAllChangesForResultSet(txrs,snapshot);
        release(txrs);
        masterConnection.rollback();
    }

    private void applyAllChangesForSQL(final String sql,final Snapshot snapshot) throws Exception {
        LOGGER.trace("applying transactions for: " + sql );
        masterConnection.setSavepoint();
        PreparedStatement s = capture(masterConnection.prepareStatement(sql));
        s.setFetchSize(transactionLogFetchSize);
        ResultSet rs = capture(s.executeQuery());
        applyAllChangesForResultSet(rs,snapshot);
        release(rs);
        masterConnection.rollback();
    }

    private void applyAllChangesForResultSet(final ResultSet results,final Snapshot snapshot) throws Exception {
        ArrayList<TransactionLogRow> txrows = new ArrayList<TransactionLogRow>(transactionLogFetchSize);
        txrows=pullChanges( results, txrows, transactionLogFetchSize );
        while(txrows.size()>0){
            applyChanges(txrows,snapshot,masterConnection);
            if(txrows.size()==transactionLogFetchSize){
                txrows=pullChanges( results, txrows, transactionLogFetchSize );
            }else{
                break;
            }
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
    throws Exception {
        LOGGER.debug("Processing changes for chunk of " +txrows.size()+ " snapshot "+snapshot);
        for( TransactionLogRow tlr : txrows ){
            TransactionID tid = tlr.getXaction();
            // Skip transactions not between snapshots, most likely because newer snapshot has it
            // still in the in-progress list.
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
            try{
                applyTransactionsStatement.execute();
            }catch(Exception e){
                throw new RuntimeException( 
                    "could not apply change. "+debug_detail+" queryParams: "+queryParams, e);
            }
        }
    }

    /**
     * Gets the next snapshot from the master database. Will return null if no next snapshot
     * available.
     *
     * @return the next Snapshot when it becomes available
     */
    private Snapshot getNextSnapshot() throws SQLException {
        Snapshot retVal = null;
        final Snapshot processedSnapshot = getLastProcessedSnapshot();	
        LOGGER.trace("Getting next snapshot after: "+processedSnapshot );
        ResultSet rs = null;
        for (long l: snaphot_query_sizes){
            LOGGER.trace("trying lastProcessedSnapshot +"+l);
            retVal = null;
            plusNSnapshotStatement.setLong(1,processedSnapshot.getCurrentXid().getLong());
            plusNSnapshotStatement.setLong(2,l);
            plusNSnapshotStatement.setLong(3,processedSnapshot.getMinXid().getLong());
            plusNSnapshotStatement.setLong(4,processedSnapshot.getMinXid().getLong());
            plusNSnapshotStatement.setLong(5,processedSnapshot.getMaxXid().getLong());
            rs=capture(plusNSnapshotStatement.executeQuery());
            if (rs.next()) {
                retVal = new Snapshot(new TransactionID(rs.getLong("current_xaction")),
                                      new TransactionID(rs.getLong("min_xaction")),
                                      new TransactionID(rs.getLong("max_xaction")),
                                      rs.getString("outstanding_xactions"));
                LOGGER.trace("Retrived "+retVal);
                if (snapshotLT(processedSnapshot,retVal)) {
                    release(rs);
                    masterConnection.rollback();
                    return retVal;
                } else {
                    LOGGER.trace("However, retrived snapshot less than lastProcessedSnapshot");
                    retVal=null;
                }
            } else {
                LOGGER.trace("No snapshot >= lastProcessedSnapshot +"+l+" snapshot="+processedSnapshot+" sql="+plusNSnapshotQuery);
            }
            release(rs);
            masterConnection.rollback();
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
        updateLastSnapshotStatement.setLong(6, masterId );
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
        final ResultSet rs = capture(slaveTransactionIdStatement.executeQuery());
        rs.next();
        long xaction_id = rs.getLong("transaction");
        release(rs);
        return xaction_id;
    }

    public synchronized void shutdown()
    {
        shutdownRequested = true;
        LOGGER.info("Shutting down slave: " + nodeName );
    }

    private void setSlaveTables() throws SQLException {
        LOGGER.trace("fetching Slave Tables from database:");
        slaveTables = new HashSet<String>();
        ResultSet rs = capture(slaveTableIDStatement.executeQuery());
        while (rs.next()) {
            String table = rs.getString("tablename");
            slaveTables.add(table);
            LOGGER.trace(table);
        }
        release(rs);
        slaveConnection.commit();
    }

    // --------- Class fields ---------------- //
    private boolean shutdownRequested = false;
    private BruceProperties properties;

    private ArrayList<ResultSet>  resultsCleanup ;
    private ArrayList<Connection> connectionCleanup ;        
    private ArrayList<PreparedStatement> statementCleanup ;        

    private Connection slaveConnection;
    private Connection masterConnection;

    private Snapshot lastProcessedSnapshot;
    private PreparedStatement selectLastSnapshotStatement;
    private PreparedStatement updateLastSnapshotStatement;
    private PreparedStatement slaveTransactionIdStatement;
    private PreparedStatement applyTransactionsStatement;
    private PreparedStatement slaveTableIDStatement;
    private PreparedStatement plusNSnapshotStatement;
    private PreparedStatement getOutstandingTransactionsStatement;
    private PreparedStatement postgresVersionStatement;

    private String createOutstandingTransactionsTmptableStatement ;
    private String insertOutstandingTransactionsStatement ;
    private String getTransactionsTmptableStatement ;
    private String getTransactionsNoOutstandingStatement ;
    private String getTransactionsInclauseStatement ;

    private HashSet<String> slaveTables ;

    private int transactionLogFetchSize;
    private int inListSize;
    private String selectLastSnapshotQuery;
    private String updateLastSnapshotQuery;
    private String slaveTransactionIdQuery;
    private String slaveTransactionIdPostgresVersion_8_3_HigherQuery;
    private String applyTransactionsQuery;
    private String daemonModeQuery;
    private String normalModeQuery;
    private String slaveTableIDQuery;
    private String plusNSnapshotQuery;
    private String postgresVersionQuery;

    private String createOutstandingTransactionsTmptableQuery ;
    private String insertOutstandingTransactionsQuery ;
    private String getTransactionsTmptableQuery ;
    private String getTransactionsNoOutstandingQuery ;
    private String getTransactionsInclauseQuery ;

    // --------- Constants ------------------- //
    private String masterUri ;
    private Long   masterId ;
    private String nodeUri ;
    private String nodeName ;


    // --------- Static Constants ------------ //
    private static final Logger LOGGER = Logger.getLogger(SlaveRunner.class);

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
    private static final String SLAVE_UPDATE_TRANSACTION_ID_8_3_HIGHER_KEY = "bruce.slave.select.transactionid.8.3.higher";
    private static final String SLAVE_UPDATE_TRANSACTION_ID_8_3_HIGHER_DEFAULT = new StringBuilder()
            .append("select txid_current() as transaction").toString();

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

    private static final String CREATE_OUTSTANDING_TRANSACTIONS_TMPTABLE_KEY =
        "bruce.slave.create_outstanding_transactions_tmptable";
    private static final String CREATE_OUTSTANDING_TRANSACTIONS_TMPTABLE_DEFAULT =
        "create temporary table outstanding_xactions ( xaction bigint unique );";
    private static final String INSERT_OUTSTANDING_TRANSACTIONS_KEY =
        "bruce.slave.insert_outstanding_transactions";
    private static final String INSERT_OUTSTANDING_TRANSACTIONS_DEFAULT =
        "select count(*) from (select * from bruce.execute_sql_array('insert into outstanding_xactions(xaction)values('::text ,(?)::text ,','::text ,')'::text )) as x";
    private static final String GET_TRANSACTIONS_TMPTABLE_KEY =
        "bruce.slave.get_transactions_tmptable";
    private static final String GET_TRANSACTIONS_TMPTABLE_DEFAULT =
        "select * from bruce.transactionlog where ( xaction >= ? and xaction < ? ) or xaction in (select xaction from outstanding_xactions ) order by rowid asc";
    private static final String GET_TRANSACTIONS_NO_OUTSTANDING_KEY =
        "bruce.slave.get_transactions_no_outstanding";
    private static final String GET_TRANSACTIONS_NO_OUTSTANDING_DEFAULT =
        "select * from bruce.transactionlog where ( xaction >= ? and xaction < ? ) order by rowid asc";
    private static final String GET_TRANSACTIONS_INCLAUSE_KEY =
        "bruce.slave.get_transactions_inclause";
    private static final String GET_TRANSACTIONS_INCLAUSE_DEFAULT =
        "select * from bruce.transactionlog where ( xaction >= ? and xaction < ? ) or xaction in ({0}) order by rowid asc";

    private static final String PLUSN_SNAPSHOT_QUERY_KEY = "bruce.slave.plusNSnapshotQuery" ;
    private static final String PLUSN_SNAPSHOT_QUERY_DEFAULT =
	"select * from bruce.snapshotlog "+
	// 4,294,967,296 == 2^32, maximum transaction id, wraps around back at this point
	" where current_xaction >= (? + ?) % 4294967296 "+ 
	// Scans for a snapshot that is actualy greater than the current snapshot
	"   and ((min_xaction > ?) or ((min_xaction = ?) and (max_xaction > ?))) "+
	" order by current_xaction asc limit 1";

    // Query to determine the postgres version
    private static final String POSTGRES_VERSION_QUERY_KEY = "bruce.postgres.version.query";
    private static final String POSTGRES_VERSION_QUERY_DEFAULT = new StringBuilder()
            .append("select setting from pg_settings where name = 'server_version'").toString();
    private static final int POSTGRES_VERSION_8_3_NUMERIC = 803;

    private static final String TRANSACTIONLOG_FETCH_SIZE_KEY = "bruce.transactionLogFetchSize";
    private static int TRANSACTIONLOG_FETCH_SIZE_DEFAULT = 5000;

    private static final String INLIST_SIZE_KEY = "bruce.inlistSizeKey";
    private static int INLIST_SIZE_DEFAULT = 50;

    private static final long[] snaphot_query_sizes = new long []{2000L, 500L,125L,25L,5L,3L,2L,1L} ;
    private QueryCache queryCache;
}
