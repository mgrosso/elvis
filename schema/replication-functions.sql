   UPDATE bruce.replication_version set major = 1, minor = 1, patch = 2, name =  'Elvis 1.1.2' ;

   CREATE OR REPLACE FUNCTION bruce.applylogtransaction(text, text, text) RETURNS boolean
        AS 'elvis-1.1.2.so', 'applyLogTransaction' LANGUAGE c;

   CREATE OR REPLACE FUNCTION bruce.daemonmode() RETURNS integer
        AS 'elvis-1.1.2.so', 'daemonMode' LANGUAGE c;
   
   CREATE OR REPLACE FUNCTION bruce.denyaccesstrigger() RETURNS trigger
        AS 'elvis-1.1.2.so', 'denyAccessTrigger' LANGUAGE c;
   
   CREATE OR REPLACE FUNCTION bruce.logsnapshottrigger() RETURNS trigger
        AS 'elvis-1.1.2.so', 'logSnapshot' LANGUAGE c;
    
   CREATE OR REPLACE FUNCTION bruce.logsnapshot() RETURNS boolean
        AS 'elvis-1.1.2.so', 'logSnapshot' LANGUAGE c;
   
   CREATE OR REPLACE FUNCTION bruce.logtransactiontrigger() RETURNS trigger
        AS 'elvis-1.1.2.so', 'logTransactionTrigger' LANGUAGE c;
   
   CREATE OR REPLACE FUNCTION bruce.normalmode() RETURNS integer
        AS 'elvis-1.1.2.so', 'normalMode' LANGUAGE c;
   
   CREATE OR REPLACE FUNCTION bruce.applylogtransaction2(int, int, text, text, text, text) RETURNS boolean
        AS 'elvis-1.1.2.so', 'applyLogTransaction2' LANGUAGE c;

   CREATE OR REPLACE FUNCTION bruce.debug_fakeapplylog(int, int, text, text, text, text) RETURNS cstring
        AS 'elvis-1.1.2.so', 'debug_fakeapplylog' LANGUAGE c;

   CREATE OR REPLACE FUNCTION bruce.debug_setcacheitem(int, int, text, text, text) RETURNS cstring
        AS 'elvis-1.1.2.so', 'debug_setcacheitem' LANGUAGE c;

   CREATE OR REPLACE FUNCTION bruce.debug_peekcacheitem(int) RETURNS cstring
        AS 'elvis-1.1.2.so', 'debug_peekcacheitem' LANGUAGE c;

   CREATE OR REPLACE FUNCTION bruce.debug_parseinfo(int, text) RETURNS cstring
        AS 'elvis-1.1.2.so', 'debug_parseinfo' LANGUAGE c;

   CREATE OR REPLACE FUNCTION bruce.debug_applyinfo(int, text) RETURNS boolean
        AS 'elvis-1.1.2.so', 'debug_applyinfo' LANGUAGE c;

   CREATE OR REPLACE FUNCTION bruce.debug_echo(int, text) RETURNS cstring
        AS 'elvis-1.1.2.so', 'debug_echo' LANGUAGE c;

   CREATE OR REPLACE FUNCTION bruce.set_tightmem(int) RETURNS cstring
        AS 'elvis-1.1.2.so', 'set_tightmem' LANGUAGE c;

   CREATE OR REPLACE FUNCTION bruce.debug_get_slave_tables () RETURNS SETOF VARCHAR AS $$ select n.nspname|| '.' ||c.relname as tablename from pg_class c, pg_namespace n where c.relnamespace = n.oid and c.oid in (
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
