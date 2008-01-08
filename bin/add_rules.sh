#!/bin/bash -vx

NODE_ID=$1
shift

psql $@ <<EOF
\set ON_ERROR_EXIT
create table bruce.slave_snapshot_history as select $NODE_ID as node_id, s.* from bruce.slavesnapshotstatus as s ;
create unique index slave_snapshot_history_u1 on bruce.slave_snapshot_history ( node_id, clusterid, master_current_xaction );
create or replace rule a_slavesnapshotstatus_update_rule as on update to bruce.slavesnapshotstatus do also select bruce.logsnapshot();
create or replace rule a_slavesnapshotstatus_insert_rule as on insert to bruce.slavesnapshotstatus do also select bruce.logsnapshot();
create or replace rule slavesnapshotstatus_update_rule as 
    on update to bruce.slavesnapshotstatus do also 
        insert into bruce.slave_snapshot_history values ( $NODE_ID, NEW.* );
create or replace rule slavesnapshotstatus_insert_rule as 
    on insert to bruce.slavesnapshotstatus do also 
        insert into bruce.slave_snapshot_history values ( $NODE_ID, NEW.* );
EOF

