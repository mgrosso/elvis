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



------------------------------------------------------------------------
-- listing functions for finding slave, master tables, etc...
------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION bruce.find_trigger_tables (
    func_ text
    ,out schema_ pg_namespace.nspname%TYPE
    ,out table_  pg_class.relname%TYPE
) RETURNS SETOF RECORD AS $Q$ 
select n.nspname as schema_, c.relname as table_
from pg_class c, pg_namespace n
where c.relnamespace = n.oid and c.oid in (
    select tgrelid 
    from pg_trigger
    where tgfoid = (
        select oid 
        from pg_proc
        where 
            proname like quote_ident($1)
            and pronamespace = (
                select oid 
                from pg_namespace
                where nspname like 'bruce'
            )
    )
)
order by 1,2 ;
$Q$ language sql ;



CREATE OR REPLACE FUNCTION bruce.find_tables_in_schema (
    schem_ text
    ,out table_  pg_class.relname%TYPE
) RETURNS SETOF pg_class.relname%TYPE AS $Q$ 

select c.relname as table_
from pg_class c, pg_namespace n
where c.relnamespace = n.oid 
    and n.nspname = quote_ident($1)
    and c.relkind = 'r'
order by 1;

$Q$ language sql ;



CREATE OR REPLACE FUNCTION bruce.get_master_tables (
    out schema_ pg_namespace.nspname%TYPE
    ,out table_  pg_class.relname%TYPE
) RETURNS SETOF RECORD AS $$ 
select * from bruce.find_trigger_tables('logtransactiontrigger');
$$ language sql ;



CREATE OR REPLACE FUNCTION bruce.get_slave_tables (
    out schema_ pg_namespace.nspname%TYPE
    ,out table_  pg_class.relname%TYPE
) RETURNS SETOF RECORD AS $$ 
select * from bruce.find_trigger_tables('denyaccesstrigger');
$$ language sql ;



------------------------------------------------------------------------
-- helper functions for changing replication triggers
------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION bruce.add_trigger ( 
    schema_ pg_namespace.nspname%TYPE
    ,table_  pg_class.relname%TYPE
    ,func_  pg_proc.proname%TYPE
    ,suffix_  pg_proc.proname%TYPE
    ,before_after_  pg_proc.proname%TYPE
) RETURNS VOID AS $$ 
begin
    execute 'create trigger ' 
                || quote_ident(table_ || suffix_) 
                || ' ' || before_after_ 
                ||' insert or delete or update on ' 
                || quote_ident(schema_) || '.' || quote_ident(table_) 
                || ' for each row execute procedure bruce.' 
                || quote_ident(func_) || '()';
end;
$$ language plpgsql ;




CREATE OR REPLACE FUNCTION bruce.drop_trigger ( 
    schema_ pg_namespace.nspname%TYPE
    ,table_  pg_class.relname%TYPE
    ,suffix_  pg_proc.proname%TYPE
) RETURNS VOID AS $$ 
begin
    execute 'drop trigger ' || quote_ident(table_||suffix_) || ' on '
                || quote_ident(schema_) || '.' || quote_ident(table_) 
                ;
end;
$$ language plpgsql ;



CREATE OR REPLACE FUNCTION bruce.for_all_with_x_add_y(
    xfunc_  pg_proc.proname%TYPE
    ,yfunc_  pg_proc.proname%TYPE
    ,suffix_  pg_proc.proname%TYPE
    ,before_after_  pg_proc.proname%TYPE
    ,out schema_ pg_namespace.nspname%TYPE
    ,out table_  pg_class.relname%TYPE
    ,out ignore_  void
) RETURNS SETOF RECORD AS $$ 
select 
    t.schema_,t.table_,bruce.add_trigger(t.schema_,t.table_,$2,$3,$4) 
from 
    (
    select * from bruce.find_trigger_tables($1) 
    except 
    select * from bruce.find_trigger_tables($2)
    ) as t
order by 1,2;
$$ language sql ;



CREATE OR REPLACE FUNCTION bruce.make_table_x(
    schema_ pg_namespace.nspname%TYPE
    ,table_  pg_class.relname%TYPE
    ,dedupe_func_  pg_proc.proname%TYPE
    ,new_func_  pg_proc.proname%TYPE
    ,suffix  pg_proc.proname%TYPE
    ,before_after_  pg_proc.proname%TYPE
    ,out oschema_ pg_namespace.nspname%TYPE
    ,out otable_  pg_class.relname%TYPE
) RETURNS RECORD AS $$ 
begin
    execute
        'select bruce.add_trigger('
            || quote_literal(schema_) || ','
            || quote_literal(table_) || ','
            || quote_literal(new_func_) || ','
            || quote_literal(suffix) || ','
            || quote_literal(before_after_) || ')'
        || ' from bruce.find_tables_in_schema(' || quote_literal(schema_) || ')' 
        || ' where table_ = ' || quote_literal(table_)
        || ' and table_ not in ('
            || ' select table_ from bruce.' 
            || quote_ident(dedupe_func_)
            || '() where schema_ = ' || quote_literal(schema_) 
            || ' and table_ = ' || quote_literal(table_) 
        || ')';
    oschema_ := schema_;
    otable_ := table_;
end;
$$ language plpgsql ;

CREATE OR REPLACE FUNCTION bruce.make_schema_x(
    schema_ pg_namespace.nspname%TYPE
    ,dedupe_func_  pg_proc.proname%TYPE
    ,new_func_  pg_proc.proname%TYPE
    ,suffix_  pg_proc.proname%TYPE
    ,before_after_  pg_proc.proname%TYPE
    ,out oschema_ pg_namespace.nspname%TYPE
    ,out otable_  pg_class.relname%TYPE
) RETURNS SETOF RECORD AS $$ 
select bruce.make_table_x($1,t.table_,$2,$3,$4,$5) from bruce.find_tables_in_schema($1) as t;
$$ language sql ;



CREATE OR REPLACE FUNCTION bruce.drop_all_trigger_x(
    xfunc_  pg_proc.proname%TYPE
    ,suffix_ pg_proc.proname%TYPE
    ,out schema_ pg_namespace.nspname%TYPE
    ,out table_  pg_class.relname%TYPE
    ,out ignore_  void
) RETURNS SETOF RECORD AS $$
select schema_,table_,bruce.drop_trigger(schema_, table_, $2 ) as ignore_ from bruce.find_trigger_tables( $1 );
$$ language sql ;



------------------------------------------------------------------------
-- functions to add master/slave triggers to all current slave/master
------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION bruce.make_all_slave_also_master(
    out schema_ pg_namespace.nspname%TYPE
    ,out table_  pg_class.relname%TYPE
) RETURNS SETOF RECORD AS $$ 
select schema_, table_ from bruce.for_all_with_x_add_y('denyaccesstrigger','logtransactiontrigger','_tx','after') order by 1,2;
$$ language sql ;

CREATE OR REPLACE FUNCTION bruce.make_all_master_also_slave(
    out schema_ pg_namespace.nspname%TYPE
    ,out table_  pg_class.relname%TYPE
) RETURNS SETOF RECORD AS $$ 
select schema_, table_ from bruce.for_all_with_x_add_y('logtransactiontrigger','denyaccesstrigger','_deny','before') order by 1,2;
$$ language sql ;



------------------------------------------------------------------------
-- functions to drop all master or slave replication triggers
------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION bruce.drop_all_slave_triggers(
    out schema_ pg_namespace.nspname%TYPE
    ,out table_  pg_class.relname%TYPE
) RETURNS SETOF RECORD AS $$ 
select schema_,table_ from bruce.drop_all_trigger_x('denyaccesstrigger','_deny');
$$ language sql ;

CREATE OR REPLACE FUNCTION bruce.drop_all_master_triggers(
    out schema_ pg_namespace.nspname%TYPE
    ,out table_  pg_class.relname%TYPE
) RETURNS SETOF RECORD AS $$ 
select schema_,table_ from bruce.drop_all_trigger_x('logtransactiontrigger','_tx');
$$ language sql ;



------------------------------------------------------------------------
-- functions to add/drop master or slave replication triggers to one table
------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION bruce.make_table_slave(
    schema_ pg_namespace.nspname%TYPE
    ,table_  pg_class.relname%TYPE
    ,out oschema_ pg_namespace.nspname%TYPE
    ,out otable_  pg_class.relname%TYPE
) RETURNS RECORD AS $$ 
select bruce.make_table_x($1,$2,'get_slave_tables','denyaccesstrigger','_deny','before');
$$ language sql ;

CREATE OR REPLACE FUNCTION bruce.make_table_master(
    schema_ pg_namespace.nspname%TYPE
    ,table_  pg_class.relname%TYPE
    ,out oschema_ pg_namespace.nspname%TYPE
    ,out otable_  pg_class.relname%TYPE
) RETURNS RECORD AS $$ 
select bruce.make_table_x($1,$2,'get_master_tables','logtransactiontrigger','_tx','after');
$$ language sql ;



------------------------------------------------------------------------
-- functions to add/drop master or slave replication triggers to all tables in a schema
------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION bruce.make_schema_slave(
    schema_ pg_namespace.nspname%TYPE
) RETURNS SETOF RECORD AS $$ 
select bruce.make_schema_x($1,'get_slave_tables','denyaccesstrigger','_deny','after');
$$ language sql ;

CREATE OR REPLACE FUNCTION bruce.make_schema_master(
    schema_ pg_namespace.nspname%TYPE
) RETURNS SETOF RECORD AS $$ 
select bruce.make_schema_x($1,'get_master_tables','logtransactiontrigger','_tx','before');
$$ language sql ;


------------------------------------------------------------------------
-- functions to help in failover.
------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION bruce.make_slave_from_master(
    newnode_id_    int
    ,cluster_id_    int
) RETURNS VOID AS $$
declare 
    curlog_id_ int;
    curlog_ name;
begin
    execute 'create table bruce.slavesnapshotstatus_pre_node' || newnode_id_ || '_cluster' || cluster_id_
        || ' as select * from bruce.slavesnapshotstatus';
    delete from bruce.slavesnapshotstatus where clusterid = cluster_id_ ;
    select into curlog_id_ max(id) from bruce.currentlog;
    curlog_ := 'bruce.snapshotlog_' || curlog_id_ ;
    execute 'insert into bruce.slavesnapshotstatus ('
        || 'clusterid,slave_xaction,master_current_xaction,master_min_xaction,'
        || 'master_max_xaction,master_outstanding_xactions ) '
        || 'select ' 
        ||      cluster_id_ || ' as clusterid, '
        ||      ' 0 as slave_xaction, '
        ||      ' current_xaction as master_current_xaction, '
        ||      ' min_xaction as master_min_xaction, '
        ||      ' max_xaction as master_max_xaction, '
        ||      ' outstanding_xactions as master_outstanding_xactions '
        || ' from ' || curlog_ 
        || ' where current_xaction = ('
        ||      ' select current_xaction from ' || curlog_
        ||      ' order by current_xaction desc limit 1'
        || ')'
        ;
end;
$$ language plpgsql ;


