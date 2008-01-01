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

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A Snapshot represents a point in time on the replication master. It is defined by:
 * <p>
 * <ul>
 * <li> The current transaction ID at the time the snapshot was taken
 * <li> The minimum transaction at said point in time
 * <li> The maximum transaction at said point in time. This should be unique among Snapshot objects.
 * <li> A list of transactions that have initiated an update, between the minimum and the maximum.
 * </ul>
 *
 * @author rklahn
 * @version $Id: Snapshot.java 72519 2007-06-27 14:24:08Z lball $
 */
public class Snapshot implements Comparable, Serializable
{

    private static final Logger logger = Logger.getLogger(Snapshot.class.getName());

    /**
     * @serial used to verify that the sender and receiver of a serialized object have loaded classes for that object
     * that are compatible with respect to serialization.
     */
    private static final long serialVersionUID = 2L;

    /**
     * @serial <code>TransactionID</code> at the time the snapshot was taken.
     */
    private final TransactionID currentXid;

    /**
     * @serial minimum transaction ID at the point in time the snapshot represents
     */
    private final TransactionID minXid;
    /**
     * @serial maximum transaction ID at the point in time the snapshot represents
     * maxXid will be unique within a replication cluster, and can be used to compare
     * snapshot objects
     */
    private final TransactionID maxXid;
    /**
     * @serial transaction ID in progress at the point in time the snapshot represents
     */
    private final SortedSet<TransactionID> inProgressXids;

    /**
     * @param currentTID current <code>TransactionID</code> when the snapshot was taken
     * @param minTID minimum transaction ID at the point in time the snapshot represents
     * @param maxTID maximum transaction ID at the point in time the snapshot represents
     * @param inFlightTIDs transaction ID in progress at the point in time the snapshot represents. Comma seperated.
     * @throws  IllegalArgumentException if the maximum TransactionID is less than the Minimum
     */
    public Snapshot(TransactionID currentTID, 
		    TransactionID minTID, 
		    TransactionID maxTID, 
		    SortedSet<TransactionID> inFlightTIDs)
    {
	this.currentXid = currentTID;
        this.minXid = minTID;
        this.maxXid = maxTID;
        this.inProgressXids = inFlightTIDs;
        if (this.maxXid.compareTo(this.minXid) != 1)
        {
            throw new IllegalArgumentException("Max TransactionID must be greater than Min TransactionID");
        }
    }

    /**
      * @param minTID minimum transaction ID at the point in time the snapshot represents
      * @param maxTID maximum transaction ID at the point in time the snapshot represents
      * @param inFlightTIDs transaction ID in progress at the point in time the snapshot represents. Comma seperated.
      * @throws  IllegalArgumentException if the maximum TransactionID is less than the Minimum
      */
    public Snapshot(TransactionID currentTID, TransactionID minTID, TransactionID maxTID, String inFlightTIDs)
    {
        this(currentTID,minTID,maxTID,new TreeSet<TransactionID>());
        if (inFlightTIDs != null)
        {
            String[] tidSA = inFlightTIDs.split(",");
            for (int i = 0; i < tidSA.length; i++)
            {
                try
                {
                    inProgressXids.add(new TransactionID(tidSA[i]));
                }
                catch (NumberFormatException e) {
                    if (tidSA[i].length()!=0) {
                        logger.debug(MessageFormat.format("inFlightTIDs[{0}]=''{1}'' not numeric, ignoring.", i, tidSA[i]));
                    }
                }
            }
        }
    }

    /**
     * Compares this Snapshot to another Object for order.
     * Returns a negative integer, zero, or a positive integer as this Snapshot
     * is less than, equal to, or greater than the specified Object.
     *
     * @param otherObject object to be compared for equality with this Snapshot
     * @return true if the specified Object is equal to this Snapshot
     * @throws java.lang.ClassCastException if the specified object is not a Snapshot
     */
    public int compareTo(Object otherObject)
    {
        Snapshot otherSnapshot = (Snapshot) otherObject;
        return currentXid.compareTo(otherSnapshot.currentXid);
    }

    /**
     * Compares the specified Object with this Snapshot for equality
     *
     * @param otherObject object to be compared for equality with this Snapshot
     * @return true if the specified Object is equal to this Snapshot
     */
    @Override
    public boolean equals(Object otherObject)
    {
        Snapshot otherSnapshot;
        try {
            otherSnapshot = (Snapshot) otherObject;
        }
        catch (ClassCastException e) {
            return false;
        }
        return currentXid.equals(otherSnapshot.currentXid);
    }

    /**
     * @return the <code>TransactionID</code> that this Snapshot was taken under
     */
    public TransactionID getCurrentXid() {
	return currentXid;
    }

    /**
     * @return minimum TransactionID for this Snapshot
     */
    public TransactionID getMinXid()
    {
        return minXid;
    }

    /**
     * @return maximum TransactionID for this Snapshot
     */
    public TransactionID getMaxXid()
    {
        return maxXid;
    }

    /**
     * @return comma seperated string of in flight transactions for this Snapshot
     */
    public String getInFlight()
    {
        String retVal = null;
        for (TransactionID t:inProgressXids)
        {
            if (retVal==null)
            {
                retVal=t.toString();
            }
            else
            {
                retVal+=","+t.toString();
            }
        }
        return retVal;
    }

    /**
     * Compares the specified TransactionID with this Snapshot for less than.
     *
     * @param tid TransactionID to be compared for less than with this Snapshot
     * @return true if the specified TransactionID is less than the Snapshot
     */
    public boolean transactionIDLT(TransactionID tid)
    {
        return tid.compareTo(minXid) < 0 || tid.compareTo(maxXid) < 0 && !inProgressXids.contains(tid);
    }

    /**
     * Compares the specified TransactionID with this Snapshot for greater than or equal to.
     *
     * @param tid TransactionID to be compared for greater than or equal to with this Snapshot
     * @return true if the specified TransactionID is greater than or equal to the Snapshot
     */
    public boolean transactionIDGE(TransactionID tid)
    {
        return tid.compareTo(maxXid) >= 0 || tid.compareTo(minXid) >= 0 && inProgressXids.contains(tid);
    }

    /**
     * Generate a SortedSet of TransactionIDs that completed between two Snapshots, this Snapshot
     * and a provided Snapshot
     *
     * @param otherSnapshot Snapshot representing a different point in time from this Snapshot
     * @return A SortedSet of TransactionIDs that completed between the two points in time represented by the
     * Snapshots
     */
    public SortedSet<TransactionID> tIDsBetweenSnapshots(Snapshot otherSnapshot)
    {
        Snapshot s1 = this;
        Snapshot s2 = otherSnapshot;
        if (s1.maxXid.compareTo(s2.maxXid) > 0)
        {
            s2 = this;
            s1 = otherSnapshot;
        }

	return new TidsBetweenSnapshotsSet(s1,s2);
    }

    /**
     * Returns a string representation of this Snapshot.
     *
     * @return a string representation of the Snapshot
     */
    @Override
    public String toString()
    {
        return "{" + "currentXID=" + currentXid+ ",minXid=" + minXid + ",maxXid=" + maxXid + 
	    ",inProgressXids=" + inProgressXids + "}";
    }
}
