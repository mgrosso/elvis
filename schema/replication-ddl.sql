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

CREATE OR REPLACE FUNCTION bruce.find_tables_with_trigger (
    schem_ text
    ,func_ text
    ,out schema_ pg_namespace.nspname%TYPE
    ,out table_  pg_class.relname%TYPE
) RETURNS SETOF RECORD AS $Q$ 
declare 
    r record;
begin
for r in 
    select n.nspname as nam, c.relname as rel
    from pg_class c, pg_namespace n
    where c.relnamespace = n.oid and c.oid in (
        select tgrelid 
        from pg_trigger
        where tgfoid = (
            select oid 
            from pg_proc
            where 
                proname like quote_ident(func_)
                and pronamespace = (
                    select oid 
                    from pg_namespace
                    where nspname like quote_ident(schem_)
                )
        )
    )
    order by 1 LOOP
        schema_ := r.nam ;
        table_  := r.rel ;
        return next;
end loop;
end;
$Q$ language plpgsql ;



CREATE OR REPLACE FUNCTION bruce.find_tables_in_schema (
    schem_ text
    ,out table_  pg_class.relname%TYPE
) RETURNS SETOF pg_class.relname%TYPE AS $Q$ 
declare 
    r record;
begin
for r in 
    select c.relname as rel
    from pg_class c, pg_namespace n
    where c.relnamespace = n.oid 
        and n.nspname = quote_ident(schem_)
        and c.relkind = 'r'
    order by 1 LOOP
        table_  := r.rel ;
        return next;
end loop;
end;
$Q$ language plpgsql ;



CREATE OR REPLACE FUNCTION bruce.get_master_tables (
    out schema_ pg_namespace.nspname%TYPE
    ,out table_  pg_class.relname%TYPE
) RETURNS SETOF RECORD AS $$ 
begin
    return query select * from bruce.find_tables_with_trigger('bruce' , 'logtransactiontrigger');
end;
$$ language plpgsql ;



CREATE OR REPLACE FUNCTION bruce.get_slave_tables (
    out schema_ pg_namespace.nspname%TYPE
    ,out table_  pg_class.relname%TYPE
) RETURNS SETOF RECORD AS $$ 
begin
    return query select * from bruce.find_tables_with_trigger('bruce' , 'denyaccesstrigger');
end;
$$ language plpgsql ;



------------------------------------------------------------------------
-- helper functions for changing replication triggers
------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION bruce.add_trigger ( 
    schema_ pg_namespace.nspname%TYPE
    ,table_  pg_class.relname%TYPE
    ,func_  pg_proc.proname%TYPE
    ,suffix_  pg_proc.proname%TYPE
) RETURNS VOID AS $$ 
begin
    execute 'create trigger ' 
                || quote_ident(table_) || quote_ident(suffix_) 
                || ' before insert or delete or update on ' 
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
    execute 'drop trigger ' || quote_ident(table_) || quote_ident(suffix_) || ' on '
                || quote_ident(schema_) || '.' || quote_ident(table_) 
                ;
end;
$$ language plpgsql ;



CREATE OR REPLACE FUNCTION bruce.for_all_with_x_add_y(
    elvisschema_ pg_namespace.nspname%TYPE
    ,xfunc_  pg_proc.proname%TYPE
    ,yfunc_  pg_proc.proname%TYPE
    ,suffix_  pg_proc.proname%TYPE
) RETURNS VOID AS $$ 
declare 
    t record ;
begin
for t in 
    select * from bruce.find_tables_with_trigger(elvisschema_,xfunc_) loop
    begin
        perform bruce.drop_trigger(t.schema_,t.table_,suffix_);
    exception
        when others then
    end;
    perform bruce.add_trigger(t.schema_,t.table_,yfunc_,suffix_);
end loop;
end;
$$ language plpgsql ;



CREATE OR REPLACE FUNCTION bruce.make_table_x(
    schema_ pg_namespace.nspname%TYPE
    ,table_  pg_class.relname%TYPE
    ,dedupe_func_  pg_proc.proname%TYPE
    ,new_func_  pg_proc.proname%TYPE
    ,suffix  pg_proc.proname%TYPE
) RETURNS VOID AS $$ 
begin
    execute
        'select bruce.add_trigger('
            || quote_literal(schema_) || ','
            || quote_literal(table_) || ','
            || quote_literal(new_func_) || ','
            || quote_literal(suffix) || ')'
        || ' from bruce.find_tables_in_schema(' || quote_literal(schema_) || ')' 
        || ' where table_ = ' || quote_literal(table_)
        || ' and table_ not in ('
            || ' select table_ from bruce.' 
            || quote_ident(dedupe_func_)
            || '() where schema_ = ' || quote_literal(schema_) 
            || ' and table_ = ' || quote_literal(table_) 
        || ')';
end;
$$ language plpgsql ;

CREATE OR REPLACE FUNCTION bruce.make_schema_x(
    schema_ pg_namespace.nspname%TYPE
    ,dedupe_func_  pg_proc.proname%TYPE
    ,new_func_  pg_proc.proname%TYPE
    ,suffix  pg_proc.proname%TYPE
) RETURNS VOID AS $$ 
declare 
    t record ;
begin
for t in select * from bruce.find_tables_in_schema(schema_) loop
    perform bruce.make_table_x(schema_,t.table_,dedupe_func_,new_func_,suffix);
end loop;
end;
$$ language plpgsql ;



CREATE OR REPLACE FUNCTION bruce.for_all_with_x_drop_x(
    elvisschema_ pg_namespace.nspname%TYPE
    ,xfunc_  pg_proc.proname%TYPE
    ,suffix_ pg_proc.proname%TYPE
) RETURNS VOID AS $$ 
declare 
    t record ;
begin
for t in 
    select * from bruce.find_tables_with_trigger(elvisschema_,xfunc_) loop
    perform bruce.drop_trigger(t.schema_,t.table_,suffix_);
end loop;
end;
$$ language plpgsql ;



------------------------------------------------------------------------
-- functions to add master/slave triggers to all current slave/master
------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION bruce.make_all_slave_also_master() RETURNS VOID AS $$ 
begin
    perform bruce.for_all_with_x_add_y('bruce','denyaccesstrigger','logtransactiontrigger','_tx');
end;
$$ language plpgsql ;

CREATE OR REPLACE FUNCTION bruce.make_all_master_also_slave() RETURNS VOID AS $$ 
begin
    perform bruce.for_all_with_x_add_y('bruce','logtransactiontrigger','denyaccesstrigger','_deny');
end;
$$ language plpgsql ;



------------------------------------------------------------------------
-- functions to drop all master or slave replication triggers
------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION bruce.drop_all_slave_triggers() RETURNS VOID AS $$ 
declare 
    t record ;
begin
    perform bruce.for_all_with_x_drop_x('bruce','denyaccesstrigger','_deny');
end;
$$ language plpgsql ;

CREATE OR REPLACE FUNCTION bruce.drop_all_master_triggers() RETURNS VOID AS $$ 
declare 
    t record ;
begin
    perform bruce.for_all_with_x_drop_x('bruce','logtransactiontrigger','_tx');
end;
$$ language plpgsql ;



------------------------------------------------------------------------
-- functions to add/drop master or slave replication triggers to one table
------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION bruce.make_table_slave(
    schema_ pg_namespace.nspname%TYPE
    ,table_  pg_class.relname%TYPE
) RETURNS VOID AS $$ 
begin
    perform bruce.make_table_x(schema_,table_,'get_slave_tables','denyaccesstrigger','_deny');
end;
$$ language plpgsql ;

CREATE OR REPLACE FUNCTION bruce.make_table_master(
    schema_ pg_namespace.nspname%TYPE
    ,table_  pg_class.relname%TYPE
) RETURNS VOID AS $$ 
begin
    perform bruce.make_table_x(schema_,table_,'get_master_tables','logtransactiontrigger','_tx');
end;
$$ language plpgsql ;



------------------------------------------------------------------------
-- functions to add/drop master or slave replication triggers to all tables in a schema
------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION bruce.make_schema_slave(
    schema_ pg_namespace.nspname%TYPE
) RETURNS VOID AS $$ 
begin
    perform bruce.make_schema_x(schema_,'get_slave_tables','denyaccesstrigger','_deny');
end;
$$ language plpgsql ;

CREATE OR REPLACE FUNCTION bruce.make_table_master(
    schema_ pg_namespace.nspname%TYPE
) RETURNS VOID AS $$ 
begin
    perform bruce.make_schema_x(schema_,'get_master_tables','logtransactiontrigger','_tx');
end;
$$ language plpgsql ;


