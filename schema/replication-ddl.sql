DROP SCHEMA bruce;
CREATE SCHEMA bruce;

DROP TABLE bruce.replication_version;
CREATE TABLE bruce.replication_version
(
    major int,
    minor int,
    patch int,
    name character(64)
);

INSERT INTO bruce.replication_version VALUES (0, 5, 0, 'Replication Pre-release Alpha');

DROP TABLE bruce.currentlog;
DROP VIEW bruce.snapshotlog;
DROP TABLE bruce.snapshotlog_1;
DROP VIEW bruce.transactionlog;
DROP TABLE bruce.transactionlog_1;
DROP TABLE bruce.slavesnapshotstatus;
DROP INDEX bruce.transactionlog_1_xaction_idx;

DROP FUNCTION bruce.applylogtransaction(text, text, text) cascade;
DROP FUNCTION bruce.daemonmode() cascade;
DROP FUNCTION bruce.denyaccesstrigger() cascade;
DROP FUNCTION bruce.logsnapshottrigger() cascade;
DROP FUNCTION bruce.logsnapshot() cascade;
DROP FUNCTION bruce.logtransactiontrigger() cascade;
DROP FUNCTION bruce.normalmode() cascade;

DROP SEQUENCE bruce.currentlog_id_seq;
DROP SEQUENCE bruce.transactionlog_rowseq;


CREATE FUNCTION bruce.applylogtransaction(text, text, text) RETURNS boolean
        AS 'bruce.so', 'applyLogTransaction' LANGUAGE c;

CREATE FUNCTION bruce.daemonmode() RETURNS integer
        AS 'bruce.so', 'daemonMode' LANGUAGE c;

CREATE FUNCTION bruce.denyaccesstrigger() RETURNS trigger
        AS 'bruce.so', 'denyAccessTrigger' LANGUAGE c;

CREATE FUNCTION bruce.logsnapshottrigger() RETURNS trigger
        AS 'bruce.so', 'logSnapshot' LANGUAGE c;

CREATE FUNCTION bruce.logsnapshot() RETURNS boolean
        AS 'bruce.so', 'logSnapshot' LANGUAGE c;

CREATE FUNCTION bruce.logtransactiontrigger() RETURNS trigger
        AS 'bruce.so', 'logTransactionTrigger' LANGUAGE c;

CREATE FUNCTION bruce.normalmode() RETURNS integer
        AS 'bruce.so', 'normalMode' LANGUAGE c;


CREATE SEQUENCE bruce.currentlog_id_seq INCREMENT BY 1 NO MAXVALUE NO MINVALUE CACHE 1;
CREATE SEQUENCE bruce.transactionlog_rowseq INCREMENT BY 1 NO MAXVALUE NO MINVALUE CACHE 1;

CREATE TABLE bruce.currentlog
(
    id integer DEFAULT nextval('bruce.currentlog_id_seq'::regclass) NOT NULL primary key,
    create_time timestamp without time zone DEFAULT now() NOT NULL
);

SELECT pg_catalog.setval('bruce.currentlog_id_seq', 1, true);

insert into bruce.currentlog (id, create_time) values (1, now());

CREATE TABLE bruce.snapshotlog_1 (
	current_xaction bigint primary key,
        min_xaction bigint NOT NULL,
        max_xaction bigint NOT NULL,
        outstanding_xactions text,
        update_time timestamp default now()
        );

CREATE VIEW bruce.snapshotlog AS SELECT * FROM snapshotlog_1;


CREATE TABLE bruce.transactionlog_1 (
        rowid bigint DEFAULT nextval('bruce.transactionlog_rowseq'::regclass) UNIQUE,
        xaction integer,
        cmdtype character(1),
        tabname text,
        info text
        );

CREATE INDEX transactionlog_1_xaction_idx ON bruce.transactionlog_1 USING btree (xaction);

CREATE VIEW bruce.transactionlog AS SELECT * FROM bruce.transactionlog_1;

CREATE TABLE bruce.slavesnapshotstatus (
    clusterid bigint NOT NULL primary key,
    slave_xaction bigint NOT NULL,
    master_current_xaction bigint NOT NULL,
    master_min_xaction bigint NOT NULL,
    master_max_xaction bigint NOT NULL,
    master_outstanding_xactions text,
    update_time timestamp without time zone default now() NOT NULL
);