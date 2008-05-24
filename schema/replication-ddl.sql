CREATE TRUSTED PROCEDURAL LANGUAGE plpgsql ;
DROP SCHEMA bruce cascade;
CREATE SCHEMA bruce;

GRANT usage ON SCHEMA bruce TO public;

CREATE TABLE bruce.replication_version
(
    major int,
    minor int,
    patch int,
    name character(64)
);

INSERT INTO bruce.replication_version VALUES (1, 0, 0, 'Replication 1.0 release');

CREATE FUNCTION bruce.applylogtransaction(text, text, text) RETURNS boolean
        AS 'elvis.so', 'applyLogTransaction' LANGUAGE c;

CREATE FUNCTION bruce.daemonmode() RETURNS integer
        AS 'elvis.so', 'daemonMode' LANGUAGE c;

CREATE FUNCTION bruce.denyaccesstrigger() RETURNS trigger
        AS 'elvis.so', 'denyAccessTrigger' LANGUAGE c;

CREATE FUNCTION bruce.logsnapshottrigger() RETURNS trigger
        AS 'elvis.so', 'logSnapshot' LANGUAGE c;

CREATE FUNCTION bruce.logsnapshot() RETURNS boolean
        AS 'elvis.so', 'logSnapshot' LANGUAGE c;

CREATE FUNCTION bruce.logtransactiontrigger() RETURNS trigger
        AS 'elvis.so', 'logTransactionTrigger' LANGUAGE c;

CREATE FUNCTION bruce.normalmode() RETURNS integer
        AS 'elvis.so', 'normalMode' LANGUAGE c;

CREATE FUNCTION bruce.applylogtransaction2(int, int, text, text, text, text) RETURNS boolean
        AS 'elvis.so', 'applyLogTransaction2' LANGUAGE c;

CREATE FUNCTION bruce.debug_fakeapplylog(int, int, text, text, text, text) RETURNS cstring
        AS 'elvis.so', 'debug_fakeapplylog' LANGUAGE c;

CREATE FUNCTION bruce.debug_setcacheitem(int, int, text, text, text) RETURNS cstring
        AS 'elvis.so', 'debug_setcacheitem' LANGUAGE c;

CREATE FUNCTION bruce.debug_peekcacheitem(int) RETURNS cstring
        AS 'elvis.so', 'debug_peekcacheitem' LANGUAGE c;

CREATE FUNCTION bruce.debug_parseinfo(int, text) RETURNS cstring
        AS 'elvis.so', 'debug_parseinfo' LANGUAGE c;

CREATE FUNCTION bruce.debug_applyinfo(int, text) RETURNS boolean
        AS 'elvis.so', 'debug_applyinfo' LANGUAGE c;

CREATE FUNCTION bruce.debug_echo(int, text) RETURNS cstring
        AS 'elvis.so', 'debug_echo' LANGUAGE c;

CREATE FUNCTION bruce.set_tightmem(int) RETURNS cstring
        AS 'elvis.so', 'set_tightmem' LANGUAGE c;



CREATE SEQUENCE bruce.currentlog_id_seq INCREMENT BY 1 NO MAXVALUE NO MINVALUE CACHE 1;
CREATE SEQUENCE bruce.transactionlog_rowseq INCREMENT BY 1 NO MAXVALUE NO MINVALUE CACHE 1;

GRANT ALL ON bruce.transactionlog_rowseq TO public;

CREATE TABLE bruce.currentlog
(
    id integer DEFAULT nextval('bruce.currentlog_id_seq'::regclass) NOT NULL primary key,
    create_time timestamp without time zone DEFAULT now() NOT NULL
);

GRANT select ON bruce.currentlog TO public;

SELECT pg_catalog.setval('bruce.currentlog_id_seq', 1, true);

insert into bruce.currentlog (id, create_time) values (1, now());

CREATE TABLE bruce.snapshotlog_1 (
	current_xaction bigint primary key,
        min_xaction bigint NOT NULL,
        max_xaction bigint NOT NULL,
        outstanding_xactions text,
        update_time timestamp default now()
        );

GRANT ALL ON bruce.snapshotlog_1 TO PUBLIC;

CREATE VIEW bruce.snapshotlog AS SELECT * FROM bruce.snapshotlog_1;

GRANT ALL ON bruce.snapshotlog TO PUBLIC;

CREATE TABLE bruce.transactionlog_1 (
        rowid bigint DEFAULT nextval('bruce.transactionlog_rowseq'::regclass) UNIQUE,
        xaction bigint,
        cmdtype character(1),
        tabname text,
        info text
        );

GRANT ALL ON bruce.transactionlog_1 TO PUBLIC;

CREATE INDEX transactionlog_1_xaction_idx ON bruce.transactionlog_1 USING btree (xaction);

CREATE VIEW bruce.transactionlog AS SELECT * FROM bruce.transactionlog_1;

GRANT ALL ON bruce.transactionlog TO PUBLIC;

CREATE TABLE bruce.slavesnapshotstatus (
    clusterid bigint NOT NULL primary key,
    slave_xaction bigint NOT NULL,
    master_current_xaction bigint NOT NULL,
    master_min_xaction bigint NOT NULL,
    master_max_xaction bigint NOT NULL,
    master_outstanding_xactions text,
    update_time timestamp without time zone default now() NOT NULL
);


