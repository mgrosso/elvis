create language plpgsql;
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

CREATE FUNCTION bruce.applylogtransaction2(int, int, text, text, text, text) RETURNS boolean
        AS 'bruce.so', 'applyLogTransaction2' LANGUAGE c;

CREATE FUNCTION bruce.debug_fakeapplylog(int, int, text, text, text, text) RETURNS cstring
        AS 'bruce.so', 'debug_fakeapplylog' LANGUAGE c;

CREATE FUNCTION bruce.debug_setcacheitem(int, int, text, text, text) RETURNS cstring
        AS 'bruce.so', 'debug_setcacheitem' LANGUAGE c;

CREATE FUNCTION bruce.debug_peekcacheitem(int) RETURNS cstring
        AS 'bruce.so', 'debug_peekcacheitem' LANGUAGE c;

CREATE FUNCTION bruce.debug_parseinfo(int, text) RETURNS cstring
        AS 'bruce.so', 'debug_parseinfo' LANGUAGE c;

CREATE FUNCTION bruce.debug_applyinfo(int, text) RETURNS boolean
        AS 'bruce.so', 'debug_applyinfo' LANGUAGE c;

CREATE FUNCTION bruce.debug_echo(int, text) RETURNS cstring
        AS 'bruce.so', 'debug_echo' LANGUAGE c;

CREATE FUNCTION bruce.set_tightmem(int) RETURNS cstring
        AS 'bruce.so', 'set_tightmem' LANGUAGE c;

CREATE FUNCTION bruce.debug_get_slave_tables () RETURNS SETOF VARCHAR AS $$ 
select n.nspname|| '.' ||c.relname as tablename 
from pg_class c, pg_namespace n
where c.relnamespace = n.oid and c.oid in (
        select tgrelid 
        from pg_trigger
        where tgfoid = (
            select oid from pg_proc
            where 
                proname = 'denyaccesstrigger'
                and pronamespace = (
                    select oid 
                    from pg_namespace
                    where nspname = 'bruce'
                )
        )
)
order by 1;
$$ language sql ;



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



create or replace function bruce.array_to_set( vals anyarray ) returns setof anyelement as $X$ 
declare
    i           int ;
    len         int ;
begin
    i:=1;
    len := array_upper(vals,1) ;
    loop
        if i > len then
            exit;
        end if;
        return next vals[i];
        i := i+1 ;
    end loop;
end;
$X$ language plpgsql;

create or replace function bruce.execute_sql( statement text ) returns void as $X$
begin
    execute statement ;
end;
$X$ language plpgsql;

create or replace function bruce.execute_sql_array( preamble text, delimited_values text, delimiter text, suffix text ) returns setof void as $X$ 
select bruce.execute_sql( $1 || val || $4 ) from bruce.array_to_set(string_to_array($2,$3)) as val ;
$X$ language sql;


