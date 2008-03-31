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
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.log4j.Logger;

/**
 * A TransactionLogRow represents a change to a single row on the replication master. 
 * It is defined by a rowid that comes from a sequence on the transactionlog_$N tables.
 * It also contains:
 * <p>
 * <ul>
 * <li> xaction, The current transaction ID at the time the row was modified.
 * <li> cmdtype, char, type of command, I=INSERT,U=UPDATE,D=DELETE.
 * <li> tabname, the table name.
 * <li> info, the change info. see csrc/bruce.c for documentation of its format.
 * </ul>
 *
 * @author mgrosso
 * @version $Id$
 */
public class TransactionLogRow {

    private static final Logger logger = Logger.getLogger(TransactionLogRow.class.getName());

    /** rowid from transactionlog_rowseq */
    private final long rowid ;

    /** <code>TransactionID</code> at the time the snapshot was taken.  */
    private final TransactionID xaction;

    /** cmdtype, char, type of command, I=INSERT,U=UPDATE,D=DELETE.  */
    private final String cmdtype;

    /** tabname, name of table modified.  */
    private final String tabname;

    /** info, string describing the change.  */
    private final String info;

    /**
     * @param rowid
     * @param xaction
     * @param cmdtype
     * @param tabname
     * @param info
     */
    public TransactionLogRow( final long rowid, final TransactionID xaction, final String cmdtype, 
        final String tabname, final String info) 
    {
	this.rowid = rowid;
        this.xaction = xaction;
        this.cmdtype = cmdtype;
        this.tabname = tabname;
	this.info = info;
    }

    /** @param rs a ResultSet selecting * from transactionlog */
    public TransactionLogRow(ResultSet rs ) throws SQLException {
        this(
                rs.getLong("rowid"),
                new TransactionID(rs.getLong("xaction")),
                rs.getString("cmdtype"),
                rs.getString("tabname"),
                rs.getString("info")
        );
    }

    /** @return rowid for this transactionlog entry.  */
    public long getRowid() {
        return rowid;
    }

    /** @return the <code>TransactionID</code> that this row change was made under */
    public TransactionID getXaction() {
	return xaction;
    }

    /** @return cmdtype */
    public String getCmdtype() {
        return cmdtype;
    }

    /** @return tabname */
    public String getTabname() {
        return tabname;
    }

    /** @return info */
    public String getInfo() {
        return info;
    }
}
