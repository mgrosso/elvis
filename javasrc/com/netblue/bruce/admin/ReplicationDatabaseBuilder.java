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
package com.netblue.bruce.admin;

import org.apache.log4j.Logger;

/**
 * Installs the replication schema
 * @author lanceball
 * @version $Id$
 */
public class ReplicationDatabaseBuilder extends DatabaseBuilder
{
    /**
     * Implement this method to provide an array of SQL statements to {@link #buildDatabase(javax.sql.DataSource)}
     *
     * @return an array of SQL statements
     */
    public String[] getSqlStrings() {
        return replicationDDL;
    }

    private static final Logger logger = Logger.getLogger(ReplicationDatabaseBuilder.class);
    private static final String[] replicationDDL = 
	{"DROP SCHEMA bruce",
	 "CREATE SCHEMA bruce",
	 "GRANT usage ON SCHEMA bruce TO public",
	 "DROP TABLE bruce.replication_version",
	 "CREATE TABLE bruce.replication_version "+
	 "           ( major int, "+
	 "             minor int, "+
	 "             patch int, "+
	 "             name character(64))",
	 "INSERT INTO bruce.replication_version VALUES (1, 0, 0, 'Replication 1.0 release')",
	 "DROP TABLE bruce.currentlog",
	 "DROP VIEW bruce.snapshotlog",
	 "DROP TABLE bruce.snapshotlog_1",
	 "DROP VIEW bruce.transactionlog",
	 "DROP TABLE bruce.transactionlog_1",
	 "DROP TABLE bruce.slavesnapshotstatus",
	 "DROP INDEX bruce.transactionlog_1_xaction_idx",
	 "DROP FUNCTION bruce.applylogtransaction(text, text, text) cascade",
	 "DROP FUNCTION bruce.daemonmode() cascade",
	 "DROP FUNCTION bruce.denyaccesstrigger() cascade",
	 "DROP FUNCTION bruce.logsnapshottrigger() cascade",
	 "DROP FUNCTION bruce.logsnapshot() cascade",
	 "DROP FUNCTION bruce.logtransactiontrigger() cascade",
	 "DROP FUNCTION bruce.normalmode() cascade",
	 "DROP SEQUENCE bruce.currentlog_id_seq",
	 "DROP SEQUENCE bruce.transactionlog_rowseq",
	 "CREATE FUNCTION bruce.applylogtransaction(text, text, text) RETURNS boolean "+
	 "             AS 'bruce.so', 'applyLogTransaction' LANGUAGE c",
	 "CREATE FUNCTION bruce.daemonmode() RETURNS integer "+
	 "             AS 'bruce.so', 'daemonMode' LANGUAGE c",
	 "CREATE FUNCTION bruce.denyaccesstrigger() RETURNS trigger "+
	 "             AS 'bruce.so', 'denyAccessTrigger' LANGUAGE c",
	 "CREATE FUNCTION bruce.logsnapshottrigger() RETURNS trigger "+
	 "             AS 'bruce.so', 'logSnapshot' LANGUAGE c",
	 "CREATE FUNCTION bruce.logsnapshot() RETURNS boolean "+
	 "             AS 'bruce.so', 'logSnapshot' LANGUAGE c",
	 "CREATE FUNCTION bruce.logtransactiontrigger() RETURNS trigger "+
	 "             AS 'bruce.so', 'logTransactionTrigger' LANGUAGE c",
	 "CREATE FUNCTION bruce.normalmode() RETURNS integer "+
	 "             AS 'bruce.so', 'normalMode' LANGUAGE c",
	 "CREATE OR REPLACE FUNCTION bruce.getslaves () RETURNS SETOF VARCHAR AS "+
	 "'"+
	 "     select n.nspname||''.''||c.relname as tablename from pg_class c, pg_namespace n "+
	 "      where c.relnamespace = n.oid "+
	 "        and c.oid in (select tgrelid from pg_trigger "+
	 "                       where tgfoid = (select oid from pg_proc "+
	 "                                        where proname = ''denyaccesstrigger'' "+
	 "                                          and pronamespace = "+
	 "                                              (select oid from pg_namespace "+
	 "                                                where nspname = ''bruce''))) "+
	 "     order by 1; "+
	 "' "+
	 "LANGUAGE SQL",
	 "CREATE OR REPLACE FUNCTION bruce.getmasters () RETURNS SETOF VARCHAR AS "+
	 "'"+
	 "     select n.nspname||''.''||c.relname as tablename from pg_class c, pg_namespace n "+
	 "      where c.relnamespace = n.oid "+
	 "        and c.oid in (select tgrelid from pg_trigger "+
	 "                       where tgfoid = (select oid from pg_proc "+
	 "                                        where proname = ''logtransactiontrigger'' "+
	 "                                          and pronamespace = "+
	 "                                                (select oid from pg_namespace "+
	 "                                                  where nspname = ''bruce''))) "+
	 "     order by 1; "+
	 "' "+
	 "LANGUAGE SQL",
	 "CREATE SEQUENCE bruce.currentlog_id_seq INCREMENT BY 1 NO MAXVALUE NO MINVALUE CACHE 1",
	 "CREATE SEQUENCE bruce.transactionlog_rowseq "+
	 "      INCREMENT BY 1 NO MAXVALUE NO MINVALUE CACHE 1",
	 "GRANT ALL ON bruce.transactionlog_rowseq TO public",
	 "CREATE TABLE bruce.currentlog "+
	 "           ( id integer DEFAULT nextval('bruce.currentlog_id_seq'::regclass) "+
	 "                        NOT NULL primary key, "+
	 "             create_time timestamp without time zone DEFAULT now() NOT NULL)",
	 "GRANT select ON bruce.currentlog TO public",
	 "SELECT pg_catalog.setval('bruce.currentlog_id_seq', 1, true)",
	 "insert into bruce.currentlog (id, create_time) values (1, now())",
	 "CREATE TABLE bruce.snapshotlog_1 "+
	 "           ( current_xaction bigint primary key, "+
	 "             min_xaction bigint NOT NULL, "+
	 "             max_xaction bigint NOT NULL, "+
	 "             outstanding_xactions text, "+
	 "             update_time timestamp default now())",
	 "GRANT ALL ON bruce.snapshotlog_1 TO PUBLIC",
	 "CREATE VIEW bruce.snapshotlog AS SELECT * FROM snapshotlog_1",
	 "GRANT ALL ON bruce.snapshotlog TO PUBLIC",
	 "CREATE TABLE bruce.transactionlog_1 "+
	 "           ( rowid bigint DEFAULT nextval('bruce.transactionlog_rowseq'::regclass) "+
	 "                          UNIQUE, "+
	 "             xaction integer, "+
	 "             cmdtype character(1), "+
	 "             tabname text, "+
	 "             info text)",
	 "GRANT ALL ON bruce.transactionlog_1 TO PUBLIC",
	 "CREATE INDEX transactionlog_1_xaction_idx "+
	 "          ON bruce.transactionlog_1 USING btree (xaction)",
	 "CREATE VIEW bruce.transactionlog AS SELECT * FROM bruce.transactionlog_1",
	 "GRANT ALL ON bruce.transactionlog TO PUBLIC",
	 "CREATE TABLE bruce.slavesnapshotstatus "+
	 "           ( clusterid bigint NOT NULL primary key, "+
	 "             slave_xaction bigint NOT NULL, "+
	 "             master_current_xaction bigint NOT NULL, "+
	 "             master_min_xaction bigint NOT NULL, "+
	 "             master_max_xaction bigint NOT NULL, "+
	 "             master_outstanding_xactions text, "+
	 "             update_time timestamp without time zone default now() NOT NULL)"};
}
