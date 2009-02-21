
-- create function that delivers returns a table rotation id, rotating if needed.
CREATE OR REPLACE FUNCTION bruce.migration_rotate() RETURNS VOID AS $Q$ 
declare
    clog record ;
begin
    raise notice 'bruce.migration_rotate(): top %', now();
    execute $A$select bruce.logsnapshot()$A$;
    begin
        execute $A$DROP TABLE bruce.transactionlog_elvis1x $A$ ;
    exception when undefined_table then
        -- do nothing
    end;
    begin
        execute $A$DROP TABLE bruce.snapshotlog_elvis1x $A$ ;
    exception when undefined_table then
        -- do nothing
    end;

    for clog in select * from bruce.currentlog order by id loop
        begin
            execute $A$DROP INDEX bruce.snapshotlog_$A$ || clog.id || $B$_i0 $B$ ;
        exception when undefined_object then
            -- do nothing
        end;
        raise notice 'bruce.migration_rotate(): creating index on snapshotlog_% at %', clog.id, now();
        execute $A$create index snapshotlog_$A$ || clog.id || $B$_i0 on bruce.snapshotlog_$B$ || clog.id || $C$ (update_time)$C$ ;
    end loop;
    raise notice 'bruce.migration_rotate(): starting backup of transactionlog top 100000 at %', now();
    execute $A$CREATE TABLE bruce.transactionlog_elvis1x as select * from bruce.transactionlog order by rowid desc limit 100000 $A$ ;
    raise notice 'bruce.migration_rotate(): starting backup of snapshotlog top 10000 at %', now();
    execute $A$CREATE TABLE bruce.snapshotlog_elvis1x as select * from bruce.snapshotlog order by update_time desc limit 10000 $A$ ;
    raise notice 'bruce.migration_rotate(): done backup of recent snapshots and transactions at %', now();
end;
$Q$ language plpgsql ;

select bruce.migration_rotate();


UPDATE bruce.replication_version set major = 2, minor = 0, patch = 0, name =  'Elvis 2.0.0' ;

-- the commented out functions here can be run from either the old bruce.so or the new shared object.
-- here we are leaving them as the old shared object to minimize rollback time and rollback complexity.
--
DROP FUNCTION bruce.applylogtransaction(text, text, text) ;

-- set and drop daemon mode.
CREATE OR REPLACE FUNCTION bruce.daemonmode() RETURNS integer
        AS 'elvis.so', 'daemonMode' LANGUAGE c;

CREATE OR REPLACE FUNCTION bruce.normalmode() RETURNS integer
        AS 'elvis.so', 'normalMode' LANGUAGE c;


-- slave denyaccess and logtransaction are the slave and master triggers respectively.
CREATE OR REPLACE FUNCTION bruce.denyaccesstrigger() RETURNS trigger
        AS 'elvis.so', 'denyAccessTrigger' LANGUAGE c;

CREATE OR REPLACE FUNCTION bruce.logsnapshottrigger() RETURNS trigger
        AS 'elvis.so', 'logSnapshot' LANGUAGE c;



-- logSnapshot function to create a snapshot comes in regular, and trigger version.
-- regular version is used by daemon.  trigger version is only useful if you want 
-- every change to a table to generate a snapshot... but you dont want that.
CREATE OR REPLACE FUNCTION bruce.logsnapshot() RETURNS boolean
        AS 'elvis.so', 'logSnapshot' LANGUAGE c;

CREATE OR REPLACE FUNCTION bruce.logtransactiontrigger() RETURNS trigger
        AS 'elvis.so', 'logTransactionTrigger' LANGUAGE c;


-- applylogtransaction2 is called by daemon to apply changes.
CREATE OR REPLACE FUNCTION bruce.applylogtransaction2(int, int, text, text, text, text) RETURNS boolean
        AS 'elvis.so', 'applyLogTransaction2' LANGUAGE c;

-- this changes some options so that elvis will use somewhat less memory when
-- there are many many tables. this is not normally needed, so dont use it
-- unless you know you have memory problems and millions of small tables.
CREATE OR REPLACE FUNCTION bruce.set_tightmem(int) RETURNS cstring
        AS 'elvis.so', 'set_tightmem' LANGUAGE c;


-- functions used for manipulating transactions and ids, whether we are in 8.1 or 8.3
CREATE OR REPLACE FUNCTION bruce.get_xaction() RETURNS bigint
        AS 'elvis.so', 'get_xaction' LANGUAGE c;

CREATE OR REPLACE FUNCTION bruce.get_xaction_highbits() RETURNS bigint
        AS 'elvis.so', 'get_xaction_highbits' LANGUAGE c;

CREATE OR REPLACE FUNCTION bruce.get_xaction_mask() RETURNS bigint
        AS 'elvis.so', 'get_xaction_mask' LANGUAGE c;


-- undocumented debug functions
CREATE OR REPLACE FUNCTION bruce.debug_fakeapplylog(int, int, text, text, text, text) RETURNS cstring
        AS 'elvis.so', 'debug_fakeapplylog' LANGUAGE c;

CREATE OR REPLACE FUNCTION bruce.debug_setcacheitem(int, int, text, text, text) RETURNS cstring
        AS 'elvis.so', 'debug_setcacheitem' LANGUAGE c;

CREATE OR REPLACE FUNCTION bruce.debug_peekcacheitem(int) RETURNS cstring
        AS 'elvis.so', 'debug_peekcacheitem' LANGUAGE c;

CREATE OR REPLACE FUNCTION bruce.debug_parseinfo(int, text) RETURNS cstring
        AS 'elvis.so', 'debug_parseinfo' LANGUAGE c;

CREATE OR REPLACE FUNCTION bruce.debug_applyinfo(int, text) RETURNS boolean
        AS 'elvis.so', 'debug_applyinfo' LANGUAGE c;

CREATE OR REPLACE FUNCTION bruce.debug_echo(int, text) RETURNS cstring
        AS 'elvis.so', 'debug_echo' LANGUAGE c;


-- helper functions

-- helper function used by default values in currentlog table.
CREATE OR REPLACE FUNCTION bruce.get_ymdh(
) RETURNS BIGINT AS $Q$ 
    select (to_char(now(),'YYYYMMDDHH'))::bigint;
$Q$ language sql ;

-- log_rotate_xaction_bitmask is used to configure the so; different xaction_mask
-- can cause elvis to replicate faster.
CREATE TABLE bruce.log_rotate_xaction_bitmask (
    xaction_mask bigint not null primary key default ( (2^32-1)::bigint # (2^26-1)::bigint ),
    one_row_only boolean not null default true unique,
    check(one_row_only = true )
);
INSERT INTO bruce.log_rotate_xaction_bitmask (one_row_only)values (true );

CREATE SEQUENCE bruce.currentlog_id_seq INCREMENT BY 1 NO MAXVALUE NO MINVALUE CACHE 1;
CREATE SEQUENCE bruce.snapshotlog_id_seq INCREMENT BY 1 NO MAXVALUE NO MINVALUE CACHE 1;

GRANT ALL ON bruce.snapshotlog_id_seq TO public;
GRANT ALL ON bruce.currentlog_id_seq TO public;

-- this should already exist.
-- CREATE SEQUENCE bruce.transactionlog_rowseq INCREMENT BY 1 NO MAXVALUE NO MINVALUE CACHE 1;
-- GRANT ALL ON bruce.transactionlog_rowseq TO public;



-- migrate currentlog table 
ALTER TABLE bruce.currentlog rename to currentlog_elvis1x ;

CREATE TABLE bruce.currentlog (
    id                  bigint default nextval('bruce.currentlog_id_seq'::regclass) primary key
    ,create_time         timestamp without time zone not null default now()
    ,xaction_highbits    bigint not null default bruce.get_xaction_highbits()
    ,first_xaction_id    bigint not null default bruce.get_xaction()
    ,first_yyyymmddhh    bigint not null default bruce.get_ymdh()
    ,xaction_mask        bigint not null default bruce.get_xaction_mask()
    ,is_current          boolean not null default true
);

CREATE UNIQUE INDEX currentlog_ak1 on bruce.currentlog ( xaction_highbits ) where is_current = true;
CREATE UNIQUE INDEX currentlog_ak2 on bruce.currentlog ( is_current ) where is_current = true;
CREATE UNIQUE INDEX currentlog_ak3 on bruce.currentlog ( xaction_highbits, first_yyyymmddhh ) ;
GRANT select,insert,update ON bruce.currentlog TO public;
SELECT pg_catalog.setval('bruce.currentlog_id_seq', 1, true);

-- create new snapshotlog and transactionlog parent tables
DROP VIEW bruce.snapshotlog;
CREATE TABLE bruce.snapshotlog (
                id BIGINT DEFAULT NEXTVAL('bruce.snapshotlog_id_seq') PRIMARY KEY
                ,current_xaction BIGINT UNIQUE
                ,min_xaction BIGINT NOT NULL
                ,max_xaction BIGINT NOT NULL
                ,outstanding_xactions TEXT
                ,update_time TIMESTAMP DEFAULT NOW()
                ,currentlog_id BIGINT NOT NULL default -1
                );
GRANT INSERT,SELECT ON TABLE bruce.snapshotlog to public ;

DROP VIEW bruce.transactionlog ;
CREATE TABLE bruce.transactionlog (
        rowid BIGINT DEFAULT NEXTVAL('bruce.transactionlog_rowseq'::regclass) UNIQUE
        ,xaction BIGINT
        ,cmdtype CHARACTER(1)
        ,tabname TEXT
        ,info TEXT
        ,currentlog_id BIGINT NOT NULL DEFAULT -1
        )
        ;
GRANT INSERT,SELECT ON TABLE bruce.transactionlog to public ;

INSERT INTO bruce.snapshotlog (current_xaction,min_xaction,max_xaction,outstanding_xactions,update_time ) select current_xaction,min_xaction,max_xaction,outstanding_xactions,update_time from bruce.snapshotlog_elvis1x ;
INSERT INTO bruce.transactionlog (  rowid,xaction,cmdtype,tabname,info  )select rowid,xaction,cmdtype,tabname,info from bruce.transactionlog_elvis1x ;

-- create function that delivers returns a table rotation id, rotating if needed.
CREATE OR REPLACE FUNCTION bruce.get_rotate_id() RETURNS pg_class.relname%TYPE AS $Q$ 
declare 
    logid pg_class.relname%TYPE;
begin
    select id::text::name into logid from bruce.currentlog 
        where is_current = true and xaction_highbits = bruce.get_xaction_highbits() ;
    if found then
        return logid;
    end if;
    lock table bruce.currentlog in exclusive mode ;
    -- check again, on busy server, most likely someone else got their first.
    select id::text::name into logid from bruce.currentlog 
        where is_current = true and xaction_highbits = bruce.get_xaction_highbits() ;
    if found then
        return logid;
    end if;
    -- ok its our responsibility to rotate
    update bruce.currentlog set is_current = false where is_current = true;
    -- TODO: validate update should have affected exactly one row unless this is first time ever.
    insert into bruce.currentlog (is_current)values(true);
    -- TODO: validate insert should have affected exactly one row 
    select id::text::name into logid from bruce.currentlog 
        where is_current = true and xaction_highbits = bruce.get_xaction_highbits() ;
    if not found then
        raise EXCEPTION 'could not rotate logs because currentlog update/insert/select failed to produce the expected row.';
    end if;
    -- now make new tables TODO: add check constraints on them and have them inherit from parent.
    execute $A$ CREATE TABLE bruce.snapshotlog_p$A$ || logid || $B$ (
                id BIGINT DEFAULT NEXTVAL('bruce.snapshotlog_id_seq'::regclass) PRIMARY KEY
                ,current_xaction BIGINT UNIQUE
                ,min_xaction BIGINT NOT NULL
                ,max_xaction BIGINT NOT NULL
                ,outstanding_xactions TEXT
                ,update_time TIMESTAMP DEFAULT NOW()
                ,currentlog_id BIGINT not null default $B$ || logid || $Z$ 
                ,check( currentlog_id = $Z$ || logid || $AA$ ) 
                ) inherits ( bruce.snapshotlog ) $AA$;
    execute $C$ GRANT INSERT,SELECT ON TABLE bruce.snapshotlog_p$C$ || logid || $D$ to public $D$;
    execute $X$ CREATE TABLE bruce.transactionlog_p$X$ || logid ||  $Y$ (
        rowid BIGINT DEFAULT NEXTVAL('bruce.transactionlog_rowseq'::regclass) PRIMARY KEY
        ,xaction BIGINT
        ,cmdtype CHARACTER(1)
        ,tabname TEXT
        ,info TEXT
        ,currentlog_id BIGINT not null default $Y$ || logid || $Z$ 
        ,check( currentlog_id = $Z$ || logid || $AA$ ) 
        ) inherits (bruce.transactionlog) $AA$
        ;
    execute $G$ CREATE UNIQUE INDEX transactionlog_p$G$ || logid || 
        $H$_i0 ON bruce.transactionlog_p$H$ || logid || 
        $I$ ( xaction, rowid ) $I$;
    execute $E$ GRANT INSERT,SELECT ON TABLE bruce.transactionlog_p$E$ || logid || 
        $F$ to public $F$;
    return logid;
end;
$Q$ language plpgsql ;


