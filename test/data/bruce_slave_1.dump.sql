--
-- PostgreSQL database dump
--

SET client_encoding = 'SQL_ASCII';
SET check_function_bodies = false;
SET client_min_messages = warning;

--
-- Name: bruce; Type: SCHEMA; Schema: -; Owner: bruce
--

CREATE SCHEMA bruce;


ALTER SCHEMA bruce OWNER TO bruce;

--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: bruce
--

COMMENT ON SCHEMA public IS 'Standard public schema';


--
-- Name: replication_test; Type: SCHEMA; Schema: -; Owner: bruce
--

CREATE SCHEMA replication_test;


ALTER SCHEMA replication_test OWNER TO bruce;

SET search_path = bruce, pg_catalog;

--
-- Name: applylogtransaction(text, text, text); Type: FUNCTION; Schema: bruce; Owner: bruce
--

CREATE FUNCTION applylogtransaction(text, text, text) RETURNS boolean
    AS 'bruce.so', 'applyLogTransaction'
    LANGUAGE c;


ALTER FUNCTION bruce.applylogtransaction(text, text, text) OWNER TO bruce;

--
-- Name: daemonmode(); Type: FUNCTION; Schema: bruce; Owner: bruce
--

CREATE FUNCTION daemonmode() RETURNS integer
    AS 'bruce.so', 'daemonMode'
    LANGUAGE c;


ALTER FUNCTION bruce.daemonmode() OWNER TO bruce;

--
-- Name: denyaccesstrigger(); Type: FUNCTION; Schema: bruce; Owner: bruce
--

CREATE FUNCTION denyaccesstrigger() RETURNS "trigger"
    AS 'bruce.so', 'denyAccessTrigger'
    LANGUAGE c;


ALTER FUNCTION bruce.denyaccesstrigger() OWNER TO bruce;

--
-- Name: logsnapshot(); Type: FUNCTION; Schema: bruce; Owner: bruce
--

CREATE FUNCTION logsnapshot() RETURNS boolean
    AS 'bruce.so', 'logSnapshot'
    LANGUAGE c;


ALTER FUNCTION bruce.logsnapshot() OWNER TO bruce;

--
-- Name: logsnapshottrigger(); Type: FUNCTION; Schema: bruce; Owner: bruce
--

CREATE FUNCTION logsnapshottrigger() RETURNS "trigger"
    AS 'bruce.so', 'logSnapshot'
    LANGUAGE c;


ALTER FUNCTION bruce.logsnapshottrigger() OWNER TO bruce;

--
-- Name: logtransactiontrigger(); Type: FUNCTION; Schema: bruce; Owner: bruce
--

CREATE FUNCTION logtransactiontrigger() RETURNS "trigger"
    AS 'bruce.so', 'logTransactionTrigger'
    LANGUAGE c;


ALTER FUNCTION bruce.logtransactiontrigger() OWNER TO bruce;

--
-- Name: normalmode(); Type: FUNCTION; Schema: bruce; Owner: bruce
--

CREATE FUNCTION normalmode() RETURNS integer
    AS 'bruce.so', 'normalMode'
    LANGUAGE c;


ALTER FUNCTION bruce.normalmode() OWNER TO bruce;

--
-- Name: currentlog_id_seq; Type: SEQUENCE; Schema: bruce; Owner: bruce
--

CREATE SEQUENCE currentlog_id_seq
    INCREMENT BY 1
    NO MAXVALUE
    NO MINVALUE
    CACHE 1;


ALTER TABLE bruce.currentlog_id_seq OWNER TO bruce;

--
-- Name: currentlog_id_seq; Type: SEQUENCE SET; Schema: bruce; Owner: bruce
--

SELECT pg_catalog.setval('currentlog_id_seq', 1, true);


SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: currentlog; Type: TABLE; Schema: bruce; Owner: bruce; Tablespace: 
--

CREATE TABLE currentlog (
    id integer DEFAULT nextval('currentlog_id_seq'::regclass) NOT NULL,
    create_time timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE bruce.currentlog OWNER TO bruce;

--
-- Name: replication_version; Type: TABLE; Schema: bruce; Owner: bruce; Tablespace: 
--

CREATE TABLE replication_version (
    major integer,
    minor integer,
    patch integer,
    name character(64)
);


ALTER TABLE bruce.replication_version OWNER TO bruce;

--
-- Name: slavesnapshotstatus; Type: TABLE; Schema: bruce; Owner: bruce; Tablespace: 
--

CREATE TABLE slavesnapshotstatus (
    clusterid bigint NOT NULL,
    slave_xaction bigint NOT NULL,
    master_current_xaction bigint NOT NULL,
    master_min_xaction bigint NOT NULL,
    master_max_xaction bigint NOT NULL,
    master_outstanding_xactions text,
    update_time timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE bruce.slavesnapshotstatus OWNER TO bruce;

--
-- Name: snapshotlog_1; Type: TABLE; Schema: bruce; Owner: bruce; Tablespace: 
--

CREATE TABLE snapshotlog_1 (
    current_xaction bigint NOT NULL,
    min_xaction bigint NOT NULL,
    max_xaction bigint NOT NULL,
    outstanding_xactions text,
    update_time timestamp without time zone DEFAULT now()
);


ALTER TABLE bruce.snapshotlog_1 OWNER TO bruce;

--
-- Name: snapshotlog; Type: VIEW; Schema: bruce; Owner: bruce
--

CREATE VIEW snapshotlog AS
    SELECT snapshotlog_1.current_xaction, snapshotlog_1.min_xaction, snapshotlog_1.max_xaction, snapshotlog_1.outstanding_xactions, snapshotlog_1.update_time FROM snapshotlog_1;


ALTER TABLE bruce.snapshotlog OWNER TO bruce;

--
-- Name: transactionlog_rowseq; Type: SEQUENCE; Schema: bruce; Owner: bruce
--

CREATE SEQUENCE transactionlog_rowseq
    START WITH 1
    INCREMENT BY 1
    NO MAXVALUE
    NO MINVALUE
    CACHE 1;


ALTER TABLE bruce.transactionlog_rowseq OWNER TO bruce;

--
-- Name: transactionlog_rowseq; Type: SEQUENCE SET; Schema: bruce; Owner: bruce
--

SELECT pg_catalog.setval('transactionlog_rowseq', 1, false);


--
-- Name: transactionlog_1; Type: TABLE; Schema: bruce; Owner: bruce; Tablespace: 
--

CREATE TABLE transactionlog_1 (
    rowid bigint DEFAULT nextval('transactionlog_rowseq'::regclass),
    xaction integer,
    cmdtype character(1),
    tabname text,
    info text
);


ALTER TABLE bruce.transactionlog_1 OWNER TO bruce;

--
-- Name: transactionlog; Type: VIEW; Schema: bruce; Owner: bruce
--

CREATE VIEW transactionlog AS
    SELECT transactionlog_1.rowid, transactionlog_1.xaction, transactionlog_1.cmdtype, transactionlog_1.tabname, transactionlog_1.info FROM transactionlog_1;


ALTER TABLE bruce.transactionlog OWNER TO bruce;

SET search_path = replication_test, pg_catalog;

--
-- Name: replicate_this; Type: TABLE; Schema: replication_test; Owner: bruce; Tablespace: 
--

CREATE TABLE replicate_this (
    name character varying(64),
    value integer
);


ALTER TABLE replication_test.replicate_this OWNER TO bruce;

SET search_path = bruce, pg_catalog;

--
-- Data for Name: currentlog; Type: TABLE DATA; Schema: bruce; Owner: bruce
--

COPY currentlog (id, create_time) FROM stdin;
1	2007-06-25 16:44:58.476086
\.


--
-- Data for Name: replication_version; Type: TABLE DATA; Schema: bruce; Owner: bruce
--

COPY replication_version (major, minor, patch, name) FROM stdin;
0	5	0	Replication Pre-release Alpha                                   
\.


--
-- Data for Name: slavesnapshotstatus; Type: TABLE DATA; Schema: bruce; Owner: bruce
--

COPY slavesnapshotstatus (clusterid, slave_xaction, master_current_xaction, master_min_xaction, master_max_xaction, master_outstanding_xactions, update_time) FROM stdin;
1000	16527812	16527808	16527808	16527809	\N	2007-06-25 16:46:03.596566
\.


--
-- Data for Name: snapshotlog_1; Type: TABLE DATA; Schema: bruce; Owner: bruce
--

COPY snapshotlog_1 (current_xaction, min_xaction, max_xaction, outstanding_xactions, update_time) FROM stdin;
\.


--
-- Data for Name: transactionlog_1; Type: TABLE DATA; Schema: bruce; Owner: bruce
--

COPY transactionlog_1 (rowid, xaction, cmdtype, tabname, info) FROM stdin;
\.


SET search_path = replication_test, pg_catalog;

--
-- Data for Name: replicate_this; Type: TABLE DATA; Schema: replication_test; Owner: bruce
--

COPY replicate_this (name, value) FROM stdin;
One	1
Two	2
Three	3
Four	4
Five	5
\.


SET search_path = bruce, pg_catalog;

--
-- Name: currentlog_pkey; Type: CONSTRAINT; Schema: bruce; Owner: bruce; Tablespace: 
--

ALTER TABLE ONLY currentlog
    ADD CONSTRAINT currentlog_pkey PRIMARY KEY (id);


--
-- Name: slavesnapshotstatus_pkey; Type: CONSTRAINT; Schema: bruce; Owner: bruce; Tablespace: 
--

ALTER TABLE ONLY slavesnapshotstatus
    ADD CONSTRAINT slavesnapshotstatus_pkey PRIMARY KEY (clusterid);


--
-- Name: snapshotlog_1_pkey; Type: CONSTRAINT; Schema: bruce; Owner: bruce; Tablespace: 
--

ALTER TABLE ONLY snapshotlog_1
    ADD CONSTRAINT snapshotlog_1_pkey PRIMARY KEY (current_xaction);


--
-- Name: transactionlog_1_rowid_key; Type: CONSTRAINT; Schema: bruce; Owner: bruce; Tablespace: 
--

ALTER TABLE ONLY transactionlog_1
    ADD CONSTRAINT transactionlog_1_rowid_key UNIQUE (rowid);


--
-- Name: transactionlog_1_xaction_idx; Type: INDEX; Schema: bruce; Owner: bruce; Tablespace: 
--

CREATE INDEX transactionlog_1_xaction_idx ON transactionlog_1 USING btree (xaction);


SET search_path = replication_test, pg_catalog;

--
-- Name: replicate_this_deny; Type: TRIGGER; Schema: replication_test; Owner: bruce
--

CREATE TRIGGER replicate_this_deny
    BEFORE INSERT OR DELETE OR UPDATE ON replicate_this
    FOR EACH ROW
    EXECUTE PROCEDURE bruce.denyaccesstrigger();


--
-- Name: public; Type: ACL; Schema: -; Owner: bruce
--

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM bruce;
GRANT ALL ON SCHEMA public TO bruce;
GRANT ALL ON SCHEMA public TO PUBLIC;


--
-- PostgreSQL database dump complete
--

