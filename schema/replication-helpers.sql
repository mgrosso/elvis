
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
CREATE OR REPLACE FUNCTION bruce.find_best_slave_snapshot_from_history(
    clusterid bigint
    ,nodeid    bigint
    ,out current_xaction bigint
    ,out min_xaction bigint
    ,out max_xaction bigint
    ,out outstanding_xactions text
) RETURNS RECORD AS $$
select 
    master_current_xaction as current_xaction, 
    master_min_xaction as min_xaction, 
    master_max_xaction as max_xaction, 
    master_outstanding_xactions as outstanding_xactions
from  bruce.slave_snapshot_history where node_id=$2 and clusterid=$1 order by master_current_xaction desc limit 1;
$$ language sql ;


CREATE OR REPLACE FUNCTION bruce.find_best_slave_snapshot(
    out current_xaction bigint
    ,out min_xaction bigint
    ,out max_xaction bigint
    ,out outstanding_xactions text
) RETURNS RECORD AS $$
declare
    curlog_id_ int;
    curlog_ name;
begin
    select into curlog_id_ max(id) from bruce.currentlog;
    curlog_ := 'bruce.snapshotlog_' || curlog_id_ ;
    execute 'select ' 
        ||      ' current_xaction as master_current_xaction, '
        ||      ' min_xaction as master_min_xaction, '
        ||      ' max_xaction as master_max_xaction, '
        ||      ' outstanding_xactions as master_outstanding_xactions '
        || ' from ' || curlog_ 
        || ' where current_xaction = ('
        ||      ' select current_xaction from ' || curlog_
        ||      ' order by current_xaction desc limit 1'
        || ')'
        into current_xaction ,min_xaction ,max_xaction ,outstanding_xactions ;
end;
$$ language plpgsql ;

CREATE OR REPLACE FUNCTION bruce.set_slave_status(
    clusterid_ bigint
    ,slave_xaction_ bigint
    ,current_xaction bigint
    ,min_xaction bigint
    ,max_xaction bigint
    ,outstanding_xactions text
) RETURNS VOID AS $$
declare
    n name;
begin
    select into n to_char(now(),'YYYYMMDD_HH_MI_SS_US')::name ;

    execute 'create table bruce.slavesnapshotstatus_at_' || n
        || ' as select * from bruce.slavesnapshotstatus';

    delete from bruce.slavesnapshotstatus where clusterid = clusterid_ ;

    insert into bruce.slavesnapshotstatus (
            clusterid,
            slave_xaction,
            master_current_xaction,
            master_min_xaction,
            master_max_xaction,
            master_outstanding_xactions 
        ) values (
            clusterid_, 
            slave_xaction_,
            current_xaction,
            min_xaction,
            max_xaction,
            outstanding_xactions
        );
end;
$$ language plpgsql ;


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


CREATE OR REPLACE FUNCTION bruce.make_history_rules(
    newnode_id_    bruce.slavesnapshotstatus.clusterid%TYPE
) RETURNS VOID as $$
begin
    create or replace rule _a_slavesnapshotstatus_update_rule as on update to bruce.slavesnapshotstatus do also select bruce.daemonmode();
    create or replace rule _a_slavesnapshotstatus_insert_rule as on insert to bruce.slavesnapshotstatus do also select bruce.daemonmode();
    create or replace rule a_slavesnapshotstatus_update_rule as on update to bruce.slavesnapshotstatus do also select bruce.logsnapshot();
    create or replace rule a_slavesnapshotstatus_insert_rule as on insert to bruce.slavesnapshotstatus do also select bruce.logsnapshot();
    execute 'create or replace rule slavesnapshotstatus_update_rule as 
        on update to bruce.slavesnapshotstatus do also insert into bruce.slave_snapshot_history values ( ' || newnode_id_ || ', NEW.* )';
    execute 'create or replace rule slavesnapshotstatus_insert_rule as 
        on insert to bruce.slavesnapshotstatus do also insert into bruce.slave_snapshot_history values ( '|| newnode_id_ || ', NEW.* )';
end;
$$ language plpgsql;

CREATE OR REPLACE FUNCTION bruce.make_history_table(
    newnode_id_    bruce.slavesnapshotstatus.clusterid%TYPE
) RETURNS VOID as $$
declare 
    discard_ record;
begin
    create table bruce.slave_snapshot_history as select newnode_id_ as node_id, s.* from bruce.slavesnapshotstatus as s ;
    alter table bruce.slave_snapshot_history add unique ( node_id, clusterid, master_current_xaction );
    select into discard_ * from bruce.make_table_slave('bruce','slave_snapshot_history');
    select into discard_ * from bruce.make_table_master('bruce','slave_snapshot_history');
    select bruce.make_history_rules(newnode_id_);
end;
$$ language plpgsql;

CREATE OR REPLACE FUNCTION bruce.remake_history_table(
    newnode_id_    bruce.slavesnapshotstatus.clusterid%TYPE
) RETURNS VOID as $$
declare 
    discard_ record;
    n        name;
begin
    select into discard_ * 
    from pg_class 
    where 
        relname = 'slave_snapshot_history' 
        and relnamespace = (
            select oid from pg_namespace where nspname like 'bruce' 
        );
    if found then
        select into n to_char(now(),'YYYYMMDD_HH_MI_SS_US')::name ;
        execute 'alter table bruce.slave_snapshot_history rename to slave_snapshot_history_at_' || n;
    end if;
    perform bruce.make_history_table(newnode_id_); 
end;
$$ language plpgsql;

CREATE OR REPLACE FUNCTION bruce.make_slave_from_master_backup(
    newnode_id_    bruce.slavesnapshotstatus.clusterid%TYPE
    ,cluster_id_    bruce.slavesnapshotstatus.clusterid%TYPE
) RETURNS VOID AS $$
begin
    perform bruce.remake_history_table(newnode_id_);
    perform bruce.make_history_rules(newnode_id_);
    perform bruce.set_slave_status(
        cluster_id_, 0, current_xaction, min_xaction, max_xaction, outstanding_xactions
    ) from bruce.find_best_slave_snapshot();
end;
$$ language plpgsql ;


CREATE OR REPLACE FUNCTION bruce.grant_w_on_xdoty_to_z(
    priv_   name
    ,schema_ pg_namespace.nspname%TYPE
    ,table_  pg_class.relname%TYPE
    ,to_whom_    pg_roles.rolname%TYPE
) RETURNS VOID AS $$
begin
    execute 'grant ' || priv_ || ' on ' || schema_ || '.' || table_ || ' to ' || to_whom_ ;
end;
$$ language plpgsql ;



