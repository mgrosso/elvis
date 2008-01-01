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

import java.io.Serializable;
import java.math.BigInteger;

/**
 * A TransactionID is nearly similar to an unsigned integer, except it has some special properties.
 * See the discussion in section 22.1.3 in the {@link <a href="http://www.postgresql.org/docs/8.2/static/routine-vacuuming.html">Postgresql manual</a>}.
 * <p>
 * To make a longer story short, some IDs are special, and IDs are only 32-bits. Normal transaction IDs are
 * compared using {@link <a href="http://en.wikipedia.org/wiki/Modulo_arithmetic">modulo-2^31 arithmetic<a>}.
 * <p>
 * The special transaction IDs are:
 * <ul>
 * <li>InvalidTransactionID(0). This should never been seen.
 * <li>BootstrapTransactionID(1). Used during the database cluster creation process. Should be considered valid,
 *                            but older than any normal ID.
 * <li>FrozenTransactionID(2). Very old transaction IDs. Valid, but older than any normal ID.
 * </ul>
 * The first normal transaction ID is 3, and the maximum is (2^32)-1.
 * <p>
 * Because of the special nature of TransactionIDs, the normal numeric operators can not safely be used.
 * Use the methods provided.
 * Dont even be tempted to "new Long(xid.toString())". You have been warned.
 *
 * @author rklahn
 * @version $Id: TransactionID.java 72519 2007-06-27 14:24:08Z lball $
 */
public class TransactionID implements Comparable, Serializable
{

    // --Commented out by Inspection (3/20/07 4:15 PM):static final Logger logger = Logger.getLogger(TransactionID.class.getName());

    public static final long INVALID = 0L;
    public static final long BOOTSTRAP = 1L;
    public static final long FROZEN = 2L;
    public static final long FIRSTNORMAL = 3L;
    public static final long MAXNORMAL = (new BigInteger("2")).pow(32).longValue() - 1L;
    public static final long TWO_TO_THE_THIRTY_FIRST = (new BigInteger("2")).pow(31).longValue();

    /**
     * @serial used to verify that the sender and receiver of a serialized object have loaded classes for that object
     * that are compatible with respect to serialization.
     */
    private static final long serialVersionUID = 1L;
    /**
     * @serial The long representation of this TransactionID.
     */
    private final long id;

    /**
     * @param value The long representation of the desired TransactionID
     * @throws IllegalArgumentException if the requested TransactionID is invalid (<1 or >(2^32)-1)
     */
    public TransactionID(long value)
    {
        id = value;
        if ((id == INVALID) || (id < 0) || (id > MAXNORMAL))
        {
            throw new IllegalArgumentException(id + " is an invalid Transaction ID");
        }
    }

    /**
     * @param value The String representation of the desired TransactionID
     * @throws IllegalArgumentException if the requested TransactionID is invalid (<1 or >(2^32)-1)
     * @throws NumberFormatException if the String does not contain a parsable long.
     */
    public TransactionID(String value)
    {
        this(new Long(value));
    }

   /**
     * Returns a hash code value for the object.
     * @see Object#hashCode() for more detailed information
     * @return hashcode for the object
     */
    @Override
    public int hashCode()
    {
        return (new Long(this.id)).hashCode();
    }

    /**
     * Compares the specified Object with this TransactionID for equality
     *
     * @param otherObject object to be compared for equality with this TransactionID
     * @return true if the specified Object is equal to this TransactionID
     */
    @Override
    public boolean equals(Object otherObject)
    {
        TransactionID otherTID;
        try
        {
            otherTID = (TransactionID) otherObject;
        }
        catch (Exception e)
        {
            return false;
        }
        return this.id == otherTID.id;
    }

    /**
     * Compares this TransactionID to another Object for order.
     * Returns a negative integer, zero, or a positive integer as this TransactionID
     * is less than, equal to, or greater than the specified Object.
     *
     * @param otherObject The Object to be compared
     * @return a negative integer, zero, or a positive integer as this TransactionID
     * is less than, equal to, or greater than the specified object.
     * @throws java.lang.ClassCastException if the specified object is not a TransactionID
     */
    public int compareTo(Object otherObject)
    {
        TransactionID otherTID = (TransactionID) otherObject;
        if (this.equals(otherTID))
        {
            return 0;
        }
        // All "normal" ids are logicaly greater than "special" ids,
        // even if the normal ID >2^31 than the special one
        if (!this.normal() || !otherTID.normal())
        {
            if (this.normal())
            {
                return 1;
            }
            else
            {
                return -1;
            }
        }
        long diff = Math.abs(this.id - otherTID.id);
        boolean comp = this.id > otherTID.id;
        // Meaning of the comparison is reversed
        if (diff > TWO_TO_THE_THIRTY_FIRST)
        {
            comp = !comp;
        }
        if (comp)
        {
            return 1;
        }
        else
        {
            return -1;
        }
        // Unreachable
    }

    /**
     * Tests if the TransactionID is normal, per the class discussion
     *
     * @return true if the TransactionID is normal, per the class discussion
     */
    public boolean normal()
    {
        return id >= FIRSTNORMAL;
    }

    /**
     * Returns the next normal TransactionID, per the class discussion regardling normality.
     * Usually, this will be the TransactionID, plus one, but in several cases, the TransactionID returned
     * will be "3"; where the TransactionID is valid, but not normal, where the TransactionID is (2^32)-1,
     * and where the TransactionID is invalid.
     *
     * @return The next normal TransactionID, per the class discussion
     */
    public TransactionID nextNormal()
    {
        long newID = this.id + 1L;
        if ((newID > MAXNORMAL) || (newID < FIRSTNORMAL))
        {
            newID = FIRSTNORMAL;
        }
        return new TransactionID(newID);
    }

    /* Returns the previous normal TransactionID, per the class discussion regarding normality.
     * Usually, this will be TransactionID, minus one, but in several cases, the 
     * TransactionID returned will be TransactionID.MAXNORMAL.
     *
     * @return The previous normal TransactionID, per the class discussion
     */
    public TransactionID priorNormal() {
	long newID = this.id - 1L;
        if ((newID > MAXNORMAL) || (newID < FIRSTNORMAL)) {
	    newID = MAXNORMAL;
	}
	return new TransactionID(newID);
    }

    /**
     * Returns the greatest normal TransactionID greater than this TransactionID, per the class discussion
     * regarding normality. This is going to be this + 2^31, taking wraparound into account.
     *
     * @return The greatest normal TransactionID greater than this TransactionID.
     */
    public TransactionID lastNormal() {
	long newID = this.id + TWO_TO_THE_THIRTY_FIRST;
	// Wraparound?
	if (newID > MAXNORMAL) {
	    newID -= MAXNORMAL;
	    // Make sure we did not land in non-normal TransactionID land.
	    if (newID < FIRSTNORMAL) {
		newID = MAXNORMAL;
	    }
	}
	return new TransactionID(newID);
    }

    /**
     * Returns the long representation of this TransactionID.
     *
     * @return a long representing this TransactionID
     */
    public long getLong() {
	// TODO: Unit test this method
	return this.id;
    }

    /**
     * Returns a string representation of this TransactionID. 
     *
     * @return a string representation of the TransactionID
     */
    @Override
    public String toString()
    {
        return (new Long(id)).toString();
    }
}
