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
    public SlaveRunner(final DataSource masterDataSource, final Cluster cluster, final Node node)
    {
        this.node = node;
        this.cluster = cluster;
        this.masterDataSource = masterDataSource;
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

            // Now get the last snapshot processed from the DB
            lastProcessedSnapshot = queryForLastProcessedSnapshot();
            if (getLastProcessedSnapshot() == null)
            {
                LOGGER.error("Cannot replicate slave node.  No starting point has been identified.  Please ensure that " +
                        "the slavesnapshotstatus table on " + this.node.getUri() + " has been properly initialized.");
            }

        }
        catch (SQLException e)
        {
            final String errorMessage = format(
                    "Unable to obtain a connection to slave node.  Cluster node {0} at {1} will not be replicated.",
                    node.getName(), node.getUri());
            LOGGER.error(errorMessage, e);
        }
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
     * Releases all database resources used by this slave.  Used during shutdown to cleanup after ourselves.
     */
    private void releaseDatabaseResources()
    {
        try
        {
            closeStatements();
            if (hasValidConnection())
            {
                getConnection().close();
            }
        }
        catch (SQLException e)
        {
            LOGGER.error("Unable to close database resources.", e);
        }
    }

    private void closeStatements() throws SQLException
    {
        selectLastSnapshotStatement.close();
        updateLastSnapshotStatement.close();
        slaveTransactionIdStatement.close();
        applyTransactionsStatement.close();
        daemonModeStatement.close();
        normalModeStatement.close();
	slaveTableIDStatement.close();
    }

    /**
     * Prepares all of the {@link java.sql.PreparedStatement}s we need for this class.  Assumes a valid and open {@link
     * #theOneConnection} with auto commit off.
     *
     * @throws SQLException
     */
    private void prepareStatements() throws SQLException
    {
        Connection connection = getConnection();
        selectLastSnapshotStatement = connection.prepareStatement(selectLastSnapshotQuery);
        updateLastSnapshotStatement = connection.prepareStatement(updateLastSnapshotQuery);
        slaveTransactionIdStatement = connection.prepareStatement(slaveTransactionIdQuery);
        applyTransactionsStatement = connection.prepareStatement(applyTransactionsQuery);
        daemonModeStatement = connection.prepareStatement(daemonModeQuery);
        normalModeStatement = connection.prepareStatement(normalModeQuery);
	slaveTableIDStatement = connection.prepareStatement(slaveTableIDQuery);
        connection.commit();
    }

    public void run()
    {
        while (!shutdownRequested)
        {
            if (getLastProcessedSnapshot() != null)
            {
                try
                {
                    final Snapshot nextSnapshot = getNextSnapshot();
                    if (nextSnapshot != null)
                    {
                        processSnapshot(nextSnapshot);
                    }
                    Thread.sleep(sleepTime);
                }
                catch (InterruptedException e)
                {
                    LOGGER.error("Slave interrupted", e);
                }
            }
        }
        releaseDatabaseResources();
        LOGGER.info(node.getName() + " shutdown complete.");
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
            applyAllChangesForTransaction(snapshot);
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
    protected Snapshot queryForLastProcessedSnapshot() throws SQLException
    {
        Snapshot snapshot = null;
        Connection connection = getConnection();
        selectLastSnapshotStatement.setLong(1, this.cluster.getId());
        // If nothing is in the result set, then our lastProcessedSnapshot is null
        final ResultSet resultSet = selectLastSnapshotStatement.executeQuery();
        if (resultSet.next())
        {
            snapshot = new Snapshot(new TransactionID(resultSet.getLong("master_current_xaction")),
                                    new TransactionID(resultSet.getLong("master_min_xaction")),
                                    new TransactionID(resultSet.getLong("master_max_xaction")),
                                    resultSet.getString("master_outstanding_xactions"));
        }
        resultSet.close();
        connection.rollback();
        return snapshot;
    }

    /**
     * Applies all outstanding {@link com.netblue.bruce.Change}s to this slave
     *
     * @param snapshot A {@link com.netblue.bruce.Snapshot} containing the latest master snapshot.
     */
    private void applyAllChangesForTransaction(final Snapshot snapshot) throws SQLException
    {
        // This method is part of a larger transaction.  We don't validate/get the connection here,
        // because if the connection becomes invalid as a part of that larger transaction, we're screwed
        // anyway and we don't want to create a new connection for just part of the transaction
	if (snapshot == null) {
            LOGGER.trace("Latest Master snapshot is null");
	} else {
            LOGGER.trace("Applying transactions: ");
	    LOGGER.trace("Getting master database connection");
	    Connection masterC = masterDataSource.getConnection();
	    try { // prevent connection pool leakage
		masterC.setAutoCommit(false);
		PreparedStatement masterPS = 
		    masterC.prepareStatement(properties.getProperty(GET_OUTSTANDING_TRANSACTIONS_KEY,
								    GET_OUTSTANDING_TRANSACTIONS_DEFAULT));
		masterPS.setFetchSize(50);
		masterPS.setLong(1,lastProcessedSnapshot.getMinXid().getLong());
		masterPS.setLong(2,snapshot.getMaxXid().getLong());
		ResultSet masterRS = masterPS.executeQuery();
		LOGGER.trace("Entering daemon mode for slave");
		daemonModeStatement.execute();
		HashSet<String> slaveTables = getSlaveTables();
		LOGGER.trace("Processing changes");
		while (masterRS.next()) {
		    TransactionID tid = new TransactionID(masterRS.getLong("xaction"));
		    // Skip transactions not between snapshots
		    if (lastProcessedSnapshot.transactionIDGE(tid) &&
			snapshot.transactionIDLT(tid)) {
			if (slaveTables.contains(masterRS.getString("tabname"))) {
			    LOGGER.trace("Applying change."+
					 " xid:"+masterRS.getLong("rowid")+
					 " tid:"+tid+
					 " tabname:"+masterRS.getString("tabname")+
					 " cmdtype:"+masterRS.getString("cmdtype")+
					 " info:"+masterRS.getString("info"));
			    applyTransactionsStatement.setString(1, masterRS.getString("cmdtype"));
			    applyTransactionsStatement.setString(2, masterRS.getString("tabname"));
			    applyTransactionsStatement.setString(3, masterRS.getString("info"));
			    applyTransactionsStatement.execute();
			    LOGGER.trace("Change applied");
			} else {
			    LOGGER.trace("NOT applying change. Table not replicated on slave."+
					 " xid:"+masterRS.getLong("rowid")+
					 " tid:"+tid+
					 " tabname:"+masterRS.getString("tabname")+
					 " cmdtype:"+masterRS.getString("cmdtype")+
					 " info:"+masterRS.getString("info"));
			}
		    } else {
			LOGGER.trace("Transaction not between snapshots. tid:"+tid+
				     " lastslaveS:"+lastProcessedSnapshot+" masterS:"+snapshot);
		    }
		}
	    } finally {
		masterC.close();
	    }
            normalModeStatement.execute();
        }
    }

    /**
     * Gets the next snapshot from the master database. Will return null if no next snapshot
     * available.
     *
     * @return the next Snapshot when it becomes available
     */
    private Snapshot getNextSnapshot()
    {
        LOGGER.trace("Getting next snapshot");
	Snapshot retVal = null;
        final Snapshot processedSnapshot = getLastProcessedSnapshot();
	long nextNormalXID = processedSnapshot.getCurrentXid().nextNormal().getLong();
	long lastNormalXID = processedSnapshot.getCurrentXid().lastNormal().getLong();
	LOGGER.trace("processedSnapshot:"+processedSnapshot.getCurrentXid()+
		     " nextNormalXID:"+nextNormalXID+" lastNormalXID:"+lastNormalXID);
	// We have to determine possible values for the next XID. There is a detailed 
	// discussion around the nature of PostgreSQL transaction IDs in 
	// TransactionID.java, but the short version is this:
	// TransactionIDs are 32-bit modulo-31 numbers, with 2^31st TransactionIDs 
	// greater than, and 2^31 TransactionIDs less than any TransactionID. 
	// Except: Some TransactionIDs are special, and for the purpose of this 
	// discussion, can be considered always less than our TransactionID
	try {
	    Connection c = masterDataSource.getConnection();
	    try { // Make sure the connection we just got gets closed
		PreparedStatement ps;
		if (nextNormalXID < lastNormalXID) {
		    // Two cases here. One, where the nextNormalID is less than lastNormalXID, and,
		    // thus, a simple less than or equals test can be used
		    LOGGER.trace("simple case");
		    ps = c.prepareStatement(properties.getProperty(NEXT_SNAPSHOT_SIMPLE_KEY,
								   NEXT_SNAPSHOT_SIMPLE_DEFAULT));
		    ps.setLong(1, TransactionID.INVALID);
		    ps.setLong(2, TransactionID.BOOTSTRAP);
		    ps.setLong(3, TransactionID.FROZEN);
		    ps.setLong(4, nextNormalXID);
		    ps.setLong(5, lastNormalXID);
		} else {
		    // Second case is where nextNormalID is greater than lastNormalXID. This occurs
		    // when the lastNormalXID has wrapped around 2^32. The test here is a little more
		    // complex, we are looking for snapshots either >=nextNormalXID or <=lastNormalXID
		    LOGGER.trace("wraparound case");
		    ps =c.prepareStatement(properties.getProperty(NEXT_SNAPSHOT_WRAPAROUND_KEY,
								  NEXT_SNAPSHOT_WRAPAROUND_DEFAULT));
		    ps.setLong(1, TransactionID.INVALID);
		    ps.setLong(2, TransactionID.BOOTSTRAP);
		    ps.setLong(3, TransactionID.FROZEN);
		    ps.setLong(4, nextNormalXID);
		    ps.setLong(5, TransactionID.MAXNORMAL);
		    ps.setLong(6, TransactionID.FIRSTNORMAL);
		    ps.setLong(7, lastNormalXID);
		}
		ResultSet rs = ps.executeQuery();
		if (rs.next()) {
		    retVal = new Snapshot(new TransactionID(rs.getLong("current_xaction")),
					  new TransactionID(rs.getLong("min_xaction")),
					  new TransactionID(rs.getLong("max_xaction")),
					  rs.getString("outstanding_xactions"));
		}
	    } finally {
		c.close();
	    }
	} catch (SQLException e) {
	    LOGGER.info("Can not obtain next Snapshot due to SQLException",e);
	}
	return retVal;
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
	HashSet<String> retVal = new HashSet<String>();
	ResultSet rs = slaveTableIDStatement.executeQuery();
	while (rs.next()) {
	    retVal.add(rs.getString("tablename"));
	}
	rs.close();
	return retVal;
    }
    
    // --------- Class fields ---------------- //
    private boolean shutdownRequested = false;
    private BruceProperties properties;
    private Connection theOneConnection;
    private Snapshot lastProcessedSnapshot;
    private PreparedStatement selectLastSnapshotStatement;
    private PreparedStatement updateLastSnapshotStatement;
    private PreparedStatement slaveTransactionIdStatement;
    private PreparedStatement applyTransactionsStatement;
    private PreparedStatement daemonModeStatement;
    private PreparedStatement normalModeStatement;
    private PreparedStatement slaveTableIDStatement;

    // --------- Constants ------------------- //
    private final int sleepTime;
    private final Node node;
    private final Cluster cluster;
    private final String selectLastSnapshotQuery;
    private final String updateLastSnapshotQuery;
    private final String slaveTransactionIdQuery;
    private final String applyTransactionsQuery;
    private final String daemonModeQuery;
    private final String normalModeQuery;
    private final String slaveTableIDQuery;
    private final DataSource masterDataSource;
    private final BasicDataSource dataSource = new BasicDataSource();

    // --------- Static Constants ------------ //
    private static final Logger LOGGER = Logger.getLogger(SlaveRunner.class);

    // Daemon mode for inserting data into the slave's replicated tables
    private static final String DAEMONMODE_QUERY_ID_KEY = "bruce.daemonmode.query";
    private static final String DAEMONMODE_QUERY_ID_DEFAULT = "select bruce.daemonmode()";

    // Normal mode to keep replicated tables read only
    private static final String NORMALMODE_QUERY_ID_KEY = "bruce.normalmode.query";
    private static final String NORMALMODE_QUERY_ID_DEFAULT = "select bruce.normalmode()";

    // Apply transactions to a slave
    private static final String APPLY_TRANSACTION_KEY = "bruce.applytransaction.query";
    private static final String APPLY_TRANSACTION_DEFAULT = "select bruce.applyLogTransaction(?, ?, ?)";

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
	"   and current_xaction >= ? "+
	"   and current_xaction <= ? "+
	"   and current_xaction >= ? "+
	"   and current_xaction <= ? "+
	" order by current_xaction desc limit 1";

    private static final String GET_OUTSTANDING_TRANSACTIONS_KEY =
	"bruce.slave.getOutstandingTransactions";
    private static final String GET_OUTSTANDING_TRANSACTIONS_DEFAULT =
	"select * from bruce.transactionlog where xaction >= ? and xaction < ? order by rowid asc";

    // How long to wait if a 'next' snapshot is unavailable, in miliseconds
    private static final String NEXT_SNAPSHOT_UNAVAILABLE_SLEEP_KEY = "bruce.nextSnapshotUnavailableSleep";
    // This default value may need some tuning. 100ms seemed too small, 1s might be right
    private static int NEXT_SNAPSHOT_UNAVAILABLE_SLEEP_DEFAULT = 1000;
}