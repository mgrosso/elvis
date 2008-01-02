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

import org.apache.commons.collections.map.LRUMap;
import org.apache.log4j.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.SortedSet;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A bounded cache of <code>Snapshot</code> and <code>Transaction</code> objects permitting a
 * <code>getNextSnapshot</code> method for <code>Snapshot</code>. Retreval operations block when a <code>Snapshot</code>
 * or <code>Transaction</code> is unavailable, with the first entering thread responsible for retreval from the master
 * database.
 *
 * @author rklahn
 * @version $Id$
 */
public class SnapshotCache
{
    // No publicly accessable empty constructor
    private SnapshotCache()
    {}

    /**
     * Constructs a <code>SnapshotCache</code> against the master database, with the specified capacity
     *
     * @param ds A <code>DataSource</code> to the Master database
     * @param capacity the capacity of the SnapshotCache
     */
    public SnapshotCache(DataSource ds, int capacity)
    {
        if (capacity < 1)
        {
            throw new IllegalArgumentException("SnapshotCache capacity must be greater than zero");
        }
        this.ds = ds;
        this.capacity = capacity;
	this.snapshotMap = new LRUMap(capacity);
        this.p = new BruceProperties();
        p.putAll(System.getProperties());
    }

    /**
     * Constructs a <code>SnapshotCache</code> against the master database, with the default capacity of 10,000
     *
     * @param ds A <code>DataSource</code> to the Master database
     */
    public SnapshotCache(DataSource ds)
    {
        this(ds, 10000);
    }

    /**
     * Returns a specified <code>Snapshot</code>. If not already present in the cache, retreves the
     * <code>Snapshot</code> from the database, blocking all other threads who may request the same
     * <code>Snapshot</code> while the <code>Snapshot</code> is being retrieved.
     *
     * @param xid <code>Snapshot</code> requested for this <code>TransactionID</code>
     *
     * @return <code>Snapshot</code> as requested
     */
    public Snapshot getSnapshot(final TransactionID xid)
    {
        // Is this Snapshot already in the cache?
 	Snapshot retVal = cachedSnapshotByXID(xid);
	if (retVal != null) {
	    // If so, our work here is done.
	    return retVal;
	}
        // We are going to the master database, make sure we are the only thread that is
        dbAccessLock.lock();
        try
        {
            // Its possible, even probable, that the data we need was obtained by another thread
            // before we managed to get the database access lock. We make this (on first glance)
            // redundent check (we checked just before we attempted to get the lock, above),
            // because it is really expensive to go to the database compared to going to the cache.
	    retVal = cachedSnapshotByXID(xid);
	    if (retVal == null)
		{ // Oh, well, we really do have to go to the master db
		    Connection c = ds.getConnection();
		    // This try block is to make sure that the connection we just opened
		    // gets closed, preventing datasource connection leakage.
		    try { 
			c.setAutoCommit(true);
			PreparedStatement ps = 
			    c.prepareStatement(p.getProperty(SPECIFIC_SNAPSHOT_QUERY_KEY,
							     SPECIFIC_SNAPSHOT_QUERY_DEFAULT));
			ps.setLong(1, xid.getLong());
			logger.trace("xid:"+xid.getLong()+
				     " query:"+
				     p.getProperty(SPECIFIC_SNAPSHOT_QUERY_KEY,
						   SPECIFIC_SNAPSHOT_QUERY_DEFAULT));
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
			    retVal = cacheSnapshot(rs);
			} else {
			    retVal = null;
			}
		    } finally {
			c.close();
		    }
		}
        }
        catch (SQLException e)
        {
            logger.error("Unable to obtain snapshot from database", e);
        }
        finally
        {
            dbAccessLock.unlock();
        }
        return retVal;
    }

    /**
     * Returns a <code>Snapshot</code> greater than the given <code>TransactionID</code>. 
     * If not already present in the
     * cache, retreves the <code>Snapshot</code> from the database blocking any other threads 
     * that may request the same
     * <code>Snapshot</code>
     *
     * @param xid <code>Snapshot</code> greater than this <code>TransactionID</code> 
     * is being requested
     *
     * @return <code>Snapshot</code> greater than the requested <code>TransactionID</code>
     */
    public Snapshot getNextSnapshot(final TransactionID xid)
    {
	logger.trace("getNextSnapshot("+xid+")");
	Snapshot retVal = cachedSnapshotNext(xid);
	if (retVal != null) {
	    return retVal;
	}
        // Going to the database, time to get the lock
        dbAccessLock.lock();
        try
        {
            // Possible, even probable, that another thread figured out the next XID before we were
            // able to obtain the dbAccessLock. Dont take the expensive step of actualy going to 
	    // the database, instead return the value obtained by the other thread.
	    retVal = cachedSnapshotNext(xid);
	    if (retVal == null) {
		logger.trace("next snapshot not in cache, going to database");
		// Sorry, we really have to go to the database.
		// We have to determine possible values for the next XID. There is a detailed 
		// discussion around the nature of PostgreSQL transaction IDs in 
		// TransactionID.java, but the short version is this:
		// TransactionIDs are 32-bit modulo-31 numbers, with 2^31st TransactionIDs 
		// greater than, and 2^31 TransactionIDs less than any TransactionID. 
		// Except: Some TransactionIDs are special, and for the purpose of this 
		// discussion, can be considered always less than our TransactionID
		long nextNormalXID = xid.nextNormal().getLong();
		long lastNormalXID = xid.lastNormal().getLong();
		// Build up the query we are going to need to find the next Snapshot
		String query = p.getProperty(NEXT_SNAPSHOT_QUERY_BASE_KEY, 
					     NEXT_SNAPSHOT_QUERY_BASE_DEFAULT);
		logger.trace("About to get a DB connection");
		Connection c = ds.getConnection();
		logger.trace("GOT a DB connection");
		// This try block is to make sure that the connection we just opened
		// gets closed, preventing datasource connection leakage.
		try {
		    c.setAutoCommit(true);
		    PreparedStatement ps;
		    if (nextNormalXID < lastNormalXID) {
			// The "no wraparound" case
			query += " and " +
			    p.getProperty(NEXT_SNAPSHOT_QUERY_ADDWHERE_KEY, 
					  NEXT_SNAPSHOT_QUERY_ADDWHERE_DEFAULT) +
			    " " + 
			    p.getProperty(NEXT_SNAPSHOT_QUERY_LIMIT_KEY, 
					  NEXT_SNAPSHOT_QUERY_LIMIT_DEFAULT);
			ps = c.prepareStatement(query);
			ps.setLong(1, TransactionID.INVALID);
			ps.setLong(2, TransactionID.BOOTSTRAP);
			ps.setLong(3, TransactionID.FROZEN);
			ps.setLong(4, nextNormalXID);
			ps.setLong(5, lastNormalXID);
		    } else {
			// The "wraparound" case
			query += " and (" +
			    p.getProperty(NEXT_SNAPSHOT_QUERY_ADDWHERE_KEY, 
					  NEXT_SNAPSHOT_QUERY_ADDWHERE_DEFAULT) +
			    " or " +
			    p.getProperty(NEXT_SNAPSHOT_QUERY_ADDWHERE_KEY, 
					  NEXT_SNAPSHOT_QUERY_ADDWHERE_DEFAULT) +
			    ") "+
			    p.getProperty(NEXT_SNAPSHOT_QUERY_LIMIT_KEY, 
					  NEXT_SNAPSHOT_QUERY_LIMIT_DEFAULT);
			ps = c.prepareStatement(query);	
			ps.setLong(1, TransactionID.INVALID);
			ps.setLong(2, TransactionID.BOOTSTRAP);
			ps.setLong(3, TransactionID.FROZEN);
			ps.setLong(4, nextNormalXID);
			ps.setLong(5, TransactionID.MAXNORMAL);
			ps.setLong(6, TransactionID.FIRSTNORMAL);
			ps.setLong(7, lastNormalXID);
		    }
		    logger.trace("nextNormalXID:" + nextNormalXID +
				 " lastNormalXID:" + lastNormalXID +
				 " getting next snapshot, query: " + query);
		    ResultSet rs = ps.executeQuery();
		    if (rs.next()) {
			// Got the snapshot. Release the lock
			retVal = cacheSnapshot(rs);
		    } // Else we were unable to retreve a next snapshot. 
		// Release the lock, return null, giving the caller a chance 
		// to terminate if it chooses.
		} finally {
		    c.close();
		}
	    }
	}
        catch (SQLException e)
        {
            logger.warn("Unable to obtain NEXT snapshot from database", e);
        }
        finally
        {
            dbAccessLock.unlock();
        }
	logger.trace("returning:"+retVal);
        return retVal;
    }

    /**
     * Returns a <code>Snapshot</code> greater than the given <code>Snapshot</code>. 
     * If not already present in the
     * cache, retreves the <code>Snapshot</code> from the database blocking any 
     * other threads that may request the same
     * <code>Snapshot</code>
     *
     * @param s <code>Snapshot</code> greater than this <code>Snapshot</code> is being requested
     *
     * @return <code>Snapshot</code> greater than the requested <code>Snapshot</code>
     */
    public Snapshot getNextSnapshot(final Snapshot s)
    {
	logger.trace("getNextSnapshot("+s+")");
        return getNextSnapshot(s.getCurrentXid());
    }

    /**
     * Returns a <code>Transaction</code> containing all <code>Change</code> objects between 
     * two given <code>Snapshot</code> objects.
     *
     * @param s1 Begining <code>Snapshot</code>
     * @param s2 Ending <code>Snapshot</code>
     *
     * @return <code>Transaction</code> object containing all <code>Change</code> 
     * objects that occured between s1 and s2
     */
    public Transaction getOutstandingTransactions(final Snapshot s1, final Snapshot s2)
    {
        // s2 must be greater than s1
        if (s1.compareTo(s2) >= 0)
        {
            throw new 
		IllegalArgumentException("Begining Snapshot must be less than Ending Snapshot");
        }
        Transaction retVal = new Transaction();
	SortedSet<TransactionID> potentialXids = s1.tIDsBetweenSnapshots(s2);
	if (!potentialXids.isEmpty()) {
	    dbAccessLock.lock();
	    try {
		logger.trace("Got db access lock");
		Connection c = ds.getConnection();
		// This try block is to make sure that the connection we just opened
		// gets closed, preventing datasource connection leakage.
		try {
		    c.setAutoCommit(false);
		    c.setSavepoint();
		    PreparedStatement ps;
		    String query = p.getProperty(TRANSACTIONS_QUERY_BASE_KEY,
						 TRANSACTIONS_QUERY_BASE_DEFAULT);
		    long diff = Math.abs(potentialXids.first().getLong()-
					 potentialXids.last().getLong());
		    if (diff > TransactionID.TWO_TO_THE_THIRTY_FIRST) {
			query += "("+
			    p.getProperty(TRANSACTIONS_QUERY_ADDWHERE_KEY,
					  TRANSACTIONS_QUERY_ADDWHERE_DEFAULT)+
			    ") or ("+
			    p.getProperty(TRANSACTIONS_QUERY_ADDWHERE_KEY,
					  TRANSACTIONS_QUERY_ADDWHERE_DEFAULT)+
			    ")";
			ps = c.prepareStatement(query);
			ps.setLong(1,potentialXids.first().getLong());
			ps.setLong(2,TransactionID.MAXNORMAL);
			ps.setLong(3,TransactionID.FIRSTNORMAL);
			ps.setLong(4,potentialXids.last().getLong());
		    } else { 
			query += p.getProperty(TRANSACTIONS_QUERY_ADDWHERE_KEY,
					       TRANSACTIONS_QUERY_ADDWHERE_DEFAULT);
			ps = c.prepareStatement(query);
			ps.setLong(1,potentialXids.first().getLong());
			ps.setLong(2,potentialXids.last().getLong());
		    }
		    logger.trace("firstXid:"+potentialXids.first().getLong()+
				 " lastXid:"+potentialXids.last().getLong()+
				 " query:"+query);
		    ps.setFetchSize(50);
		    ResultSet rs = ps.executeQuery();
		    while (rs.next()) {
			TransactionID xid = new TransactionID(rs.getLong("xaction"));
			if (potentialXids.contains(xid)) {
			    Change change = new Change(rs.getLong("rowid"),
						       xid,
						       rs.getString("cmdtype"),
						       rs.getString("tabname"),
						       rs.getString("info"));
			    retVal.add(change);
			}
		    }
		    c.rollback(); // Cause we should not have changed anything in the master DB
		} finally {
		    c.close();
		}
	    } catch (SQLException e) {
		logger.error("Unable to retreve Transaction", e);
	    } finally {
		dbAccessLock.unlock();
		logger.trace("Released db access lock");
	    }
	}
	logger.trace("returning:"+retVal);
	return retVal;
    }

    /**
     * @return the capacity of this <code>SnapshotCache</code>
     */
    public int getCapacity()
    {
        return this.capacity;
    }

    /**
     * @return the number of Snapshot elements in the cache. should not exceed capacity
     */
    public int getSnapshotUsed()
    {
        return snapshotMap.size();
    }

    /**
     * {@inheritDoc}
     */
    public String toString()
    {
        return getClass().getName() + "{capacity:" + this.capacity + 
	    " snapshotInUse:" + snapshotMap.size() + "}";
    }

    private Snapshot cachedSnapshotNext(final TransactionID xid)
    {
        synchronized (snapshotMap)
        {
            if (snapshotMap.nextKey(xid) != null) { 
		// Any key greater than xid?
                return (Snapshot) snapshotMap.get(snapshotMap.lastKey());
            }
            else
            {
                return null; // No key greater than xid
            }
        }
    }

    private Snapshot cachedSnapshotByXID(final TransactionID xid)
    {
        synchronized (snapshotMap)
        {
            return (Snapshot) snapshotMap.get(xid);
        }
    }

    private Snapshot cacheSnapshot(final ResultSet rs) throws SQLException
    {
	Snapshot s = new Snapshot(new TransactionID(rs.getLong("current_xaction")),
				  new TransactionID(rs.getLong("min_xaction")),
				  new TransactionID(rs.getLong("max_xaction")),
				  rs.getString("outstanding_xactions"));
	synchronized (snapshotMap) {
	    snapshotMap.put(s.getCurrentXid(),s);
	    return s;
	}
    }

    private DataSource ds; // Master server data source
    private int capacity;  // Capacity of this SnapshotCache
    private BruceProperties p;
    // One might ask: Why not Collections.synchronizedMap(new LRUMap())?
    // Because we use some methods specific to the Apache Collections Maps (nextKey(), lastKey())
    // not available in the Map interface, and thus, not protected by synchronizedMap(). Because
    // of this, we have to be old school, and synchronize ourselfs.
    private LRUMap snapshotMap; // Backing Map for the cache of Snapshot objects
    // Only one thread at a time is allowed to access the master DB
    private ReentrantLock dbAccessLock = new ReentrantLock();

    private static final Logger logger = Logger.getLogger(SnapshotCache.class);

    // Queries related to getting snapshots out of databases
    private static final String SPECIFIC_SNAPSHOT_QUERY_KEY = "bruce.specificSnapshotQuery";
    private static final String SPECIFIC_SNAPSHOT_QUERY_DEFAULT =
	"select * from bruce.snapshotlog where current_xaction = ?";
    private static final String NEXT_SNAPSHOT_QUERY_BASE_KEY = "bruce.nextSnapshotQuery.base";
    private static final String NEXT_SNAPSHOT_QUERY_BASE_DEFAULT =
	"select * from bruce.snapshotlog where current_xaction not in (?,?,?) ";
    private static final String NEXT_SNAPSHOT_QUERY_ADDWHERE_KEY = 
	"bruce.nextSnapshotQuery.additionalWhere";
    private static final String NEXT_SNAPSHOT_QUERY_ADDWHERE_DEFAULT =
	"current_xaction >= ? and current_xaction <= ?";
    private static final String NEXT_SNAPSHOT_QUERY_LIMIT_KEY = "bruce.nextSnapshotQuery.limit";
    private static final String NEXT_SNAPSHOT_QUERY_LIMIT_DEFAULT =
	"order by current_xaction desc limit 1";

    // Queries related to getting transactions out of databases
    private static final String TRANSACTIONS_QUERY_BASE_KEY = "bruce.transactionsQuery.base";
    private static final String TRANSACTIONS_QUERY_BASE_DEFAULT =
 	"select * from bruce.transactionlog where ";
    private static final String TRANSACTIONS_QUERY_ADDWHERE_KEY = 
	"bruce.transactionsQuery.addWhere";
    private static final String TRANSACTIONS_QUERY_ADDWHERE_DEFAULT =
	"xaction >= ? and xaction <= ?";
}
