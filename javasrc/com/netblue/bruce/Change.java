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
// -----------------------------------------------
// ${CLASS}
// -----------------------------------------------

/**
 * A Change represents a single change to a database row
 *
 * @author rklahn
 * @version $Id: Change.java 72519 2007-06-27 14:24:08Z lball $
 */
public class Change implements Comparable, Serializable
{
    /**
     * @serial used to verify that the sender and receiver of a serialized object have loaded classes for that object
     * that are compatible with respect to serialization.
     */
    private static final long serialVersionUID = 1L;

    /**
     * @serial identifies the order that this change can be applied relative to other Change objects
     */
    private long rowid;
    /**
     * @serial transactioID this change occured under
     */
    private TransactionID xactionID;
    /**
     * @serial I=Insert, U=Update, D=Delete
     */
    private String cmdType;
    /**
     * Insert Command
     */
    public static final String INSERT = "I";
    /**
     * Update Command
     */
    public static final String UPDATE = "U";
    /**
     * Delete Command
     */
    public static final String DELETE = "D";
    /**
     * @serial table that changed
     */
    private String tableName;
    /**
     * @serial internaly formated version of the row change
     */
    private String info;

    /**
     * @param rowid identifies the order that this change can be applied relative to other Change objects
     * @param xactionID transactioID this change occured under
     * @param cmdType I=Insert, U=Update, D=Delete
     * @param tableName table that changed
     * @param info internaly formated version of the row change
     * @throws IllegalArgumentException if cmdType not INSERT, UPDATE, or DELETE
     */
    public Change (long rowid, TransactionID xactionID, String cmdType, String tableName, String info)
    {
        this.rowid=rowid;
        this.xactionID=xactionID;
        this.cmdType=cmdType;
        this.tableName=tableName;
        this.info=info;
        if (!((cmdType.equals(INSERT))||(cmdType.equals(UPDATE))||(cmdType.equals(DELETE))))
        {
            throw new IllegalArgumentException("cmdType must be INSERT, UPDATE, or DELETE");
        }
    }

    /**
     * @return transactionID this change was performed under
     */
    public TransactionID getXactionID()
    {
        return xactionID;
    }

    /**
     * @return INSERT, UPDATE, or DELETE
     */
    public String getCmdType()
    {
        return cmdType;
    }

    /**
     * @return table that changed
     */
    public String getTableName()
    {
        return tableName;
    }

    /**
     * @return internaly formatted version of this row change
     */
    public String getInfo()
    {
        return info;
    }

    /**
     * Compares the specified Object with this Change for equality
     *
     * @param otherObject object to be compared for equality with this Change
     * @return true if the specified Object is equal to this Change
     * @throws java.lang.ClassCastException if the specified object is not a Change
     */
    @Override
    public boolean equals(Object otherObject)
    {
        Change otherChange = (Change) otherObject;
        return this.equals(otherChange);
    }

    /**
     * Compares the specified Change with this Change for equality
     *
     * @param otherChange Change to be compared for equality with this Change
     * @return true if the specified Change is equal to this Change
     */
    public boolean equals(Change otherChange)
    {
        return (otherChange.rowid == rowid);
    }

    /**
     * Compares this Change to another Object for order.
     * Returns a negative integer, zero, or a positive integer as this Change
     * is less than, equal to, or greater than the specified Change.
     *
     * @param otherObject object to be compared for equality with this Change
     * @return true if the specified Object is equal to this Change
     * @throws java.lang.ClassCastException if the specified object is not a Change
     */
    public int compareTo(final Object otherObject)
    {
        Change otherChange = (Change) otherObject;
        return (new Long(rowid)).compareTo(otherChange.rowid);
    }

    public String toString() {
        String retVal = "{rowid="+rowid+",transactionID="+xactionID+",cmdType=";
        if (cmdType.equals(INSERT)) retVal+="INSERT";
        if (cmdType.equals(UPDATE)) retVal+="UPDATE";
        if (cmdType.equals(DELETE)) retVal+="DELETE";
        retVal+=",tableName="+tableName+",info=\""+info+"\"}";
        return retVal;
    }
}
