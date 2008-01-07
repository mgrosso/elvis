#!/bin/bash
## ----------------------------------------------------------------------
## ----------------------------------------------------------------------
##
## File:      pg_bench_test.sh
## Author:    mgrosso 
## Created:   Thu Jan  3 15:55:52 PST 2008 on caliban
## Project:   
## Purpose:   
## 
## $Id$
## ----------------------------------------------------------------------
## ----------------------------------------------------------------------

if [ -z $BASE ] ; then
    BASE=elvis-pgbench-test
fi


if [ -z $TESTUSER ] ; then
    TESTUSER=bruce
fi
if [ -z $CLUSTER ] ; then 
    CLUSTER=pg_bench_replication_test
fi


if [ ! -z $BRUCE_CLASSPATH ] ; then
    CLASSPATH=$BRUCE_CLASSPATH
else
    for JAR in `find $LIB_DIR -name '*.jar'` ; do
        CLASSPATH=$CLASSPATH:$JAR
    done
fi

if [ -z $RUN_JAVA ] ; then
    RUN_JAVA=$JAVA_HOME/bin/java
fi
if [ -z $JAVAOPTS ] ; then
    JAVAOPTS="-server"
fi
if [ -z $BRUCEJAR ] ; then
    BRUCEJAR=release/bruce.jar
fi

#globals and constants
MAINCLASS=com.netblue.bruce.Main
PGBENCH_TABLES="accounts branches history tellers pgbench_test_heartbeat"
TABLEREGEX='^(accounts|branches|history|tellers|pgbench_test_heartbeat)$'

if [ -z $NODE_NUMBERS ] ; then
    NODE_NUMBERS="0 1 2 3 4 5"
fi
if [ -z $PGBENCH_CLIENTS ] ; then
    PGBENCH_CLIENTS=8
fi
if [ -z $PGBENCH_SCALEFACTOR ] ; then
    PGBENCH_SCALEFACTOR=4
fi
if [ -z $PGBENCH_TRANSACTIONS ] ; then
    PGBENCH_TRANSACTIONS=1000
fi
#TOPOLOGY="0:0,1,2,3,4,5"

export DIGIT
export PG_DATA
export PORT
export DB
export UHP
export DBURL

function puke(){
    echo $@
    exit 1
}

function do_or_puke (){
    $@
    if [ $? -ne 0 ] ; then
        puke "command failed: $@"
    fi
}


function maketop(){
    rm -rf $BASE
    mkdir -p $BASE
}

function stop_any_running_db (){
    for N in $NODE_NUMBERS ; do
        set_uhp $N 
        while [ $( ps -fe | grep $PG_DATA | grep -v grep | wc -l ) -gt 0 ] ; do
            kill -QUIT $( ps -fe | grep $PG_DATA | grep -v grep | tr -s ' ' | cut -d ' ' -f 2 )
            sleep 1 
        done
        pg_ctl -D $PG_DATA -m immediate stop 
    done
    kill $(cat bruce.pid)
    sleep 3 
    kill -9 $(cat bruce.pid)
}

function waitfordb(){
    N=0
    while [ $N -lt 30 ] ; do 
        grep "database system is ready" $PG_DATA/pg_log/postgresql.log*
        if [ $? -eq 0 ] ; then
            #sadly, seeing this statement in the log does not mean the database system is ready...
            sleep 5 
            return
        fi
        sleep 1
        N=$((N+1))
    done
    puke "database in $PG_DATA did not start up in 30 seconds."
}

function edit_postgresql_conf(){

#    perl -psi.bak  -e "s/^\#port = 5432/port = ${PORT}/;" $PG_DATA/postgresql.conf
#    if [ $? -ne 0 ] ; then
#        puke "command failed: perl -psi.bak  -e 's/^\#port = 5432/port = $PORT/;' $PG_DATA/postgresql.conf"
#    fi
#    perl -psi.bak  -e "s/^\#redirect_stderr = off/redirect_stderr = on/;" $PG_DATA/postgresql.conf
#    if [ $? -ne 0 ] ; then
#        puke "command failed: perl -psi.bak  -e 's/^\#redirect_stderr = off/redirect_stderr = on/;' $PG_DATA/postgresql.conf"
#    fi
#    perl -psi.bak  -e "s/^\#fsync = on/fsync = off/;" $PG_DATA/postgresql.conf
#    if [ $? -ne 0 ] ; then
#        puke "command failed: perl -psi.bak  -e  's/^\#fsync = on/fsync = off/;' $PG_DATA/postgresql.conf"
#    fi
#log_filename = 'postgresql-%Y-%m-%d_%H%M%S.log' # Log file name pattern.
#    perl -psi.bak  -e "s/^\#log_filename = .*/log_filename = 'postgresql.log' /;" $PG_DATA/postgresql.conf
#    if [ $? -ne 0 ] ; then
#        puke "command failed: perl -psi.bak  -e  's/^\#log_filename = /log_filename = postgresql.log/;' $PG_DATA/postgresql.conf"
#    fi

    cat >>$PG_DATA/postgresql.conf <<EOF
    log_filename = 'postgresql.log'
    port = $PORT
    redirect_stderr = on
    fsync = off
    log_duration = on
    log_line_prefix = '%u d=%d %r p%p t=%m ses=%c[%l] tx=%x '           # Special values:
    log_statement = 'none'          # none, mod, ddl, all
EOF

    if [ $? -ne 0 ] ; then
        puke "command failed: cat >>$PG_DATA/postgresql.conf <<EOF "
    fi

}

function make_schema (){
    do_or_puke psql $UHP -e -f schema/replication-ddl.sql $DB >replication-ddl-${DIGIT}.out 2>&1
    do_or_puke psql $UHP -e -f schema/cluster-ddl.sql $DB >cluster-ddl-${DIGIT}.out 2>&1
    $RUNPSQL "create table pgbench_test_heartbeat ( id bigint primary key, t timestamp not null unique );" >create-table-${DIGIT}.out 2>&1 ||
            puke "psql failed to create table ${TESTUSER}_heartbeat"
    do_or_puke pgbench -i $UHP -d $DB -s $SCALEFACTOR
}

function makedb (){
    set_uhp $1
    do_or_puke mkdir -p $PG_DATA
    do_or_puke initdb -E utf8 -U $TESTUSER -D $PG_DATA
    edit_postgresql_conf
    nohup pg_ctl -D $PG_DATA start >$PG_DATA.out 2>&1 &
    waitfordb
    do_or_puke createdb -E utf8 $UHP -O $TESTUSER $DB
    make_schema
}

function set_uhp(){
    DIGIT=$1
    PG_DATA=$BASE/pg_data_$DIGIT
    PORT=5432$DIGIT
    DB=$TESTUSER
    UHP="-U $TESTUSER -h localhost -p $PORT"
    RUNPSQL="psql -e $UHP -d $DB -c "
    RUNPSQLF="psql -e $UHP -d $DB -f "
    DBURL="jdbc:postgresql://localhost:$PORT/$DB?user=$TESTUSER"
    BRUCE_OPTS="-Dpid.file=bruce.pid -Dlog4j.configuration=bin/log4j.properties -Dpostgresql.db_name=${DB} -Dpostgresql.URL=${DBURL} -Dhibernate.connection.url=${DBURL} -Dhibernate.connection.username=${TESTUSER} -Dhibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
}

function run_sql (){
    DIGIT=$1
    SQL=$2
    do_or_puke psql -e $UHP -d $DB -c "$SQL"
}

function add_slave_triggers(){
    for TABLE in $PGBENCH_TABLES ; do
        set_uhp $1
        $RUNPSQL "create trigger ${TABLE}_deny before insert or delete or update on $TABLE for each row execute procedure bruce.denyaccesstrigger();" ||
            puke "psql failed "
    done
}

function add_master_triggers(){
    for TABLE in $PGBENCH_TABLES ; do
        set_uhp $1
        $RUNPSQL "create trigger ${TABLE}_tx after insert or delete or update on $TABLE for each row execute procedure bruce.logtransactiontrigger();" ||
            puke "psql failed "
    done
}

function setup_cluster(){
    for N in $NODE_NUMBERS ; do
        set_uhp $N
        URI=$DBURL
        set_uhp 0
        psql -e $UHP $DB  -c "insert into bruce.yf_node (id,available,includetable,name,uri) values ($N,true,'$TABLEREGEX', 'node_$N','$URI');"  ||
            puke "psql failed "
    done
    set_uhp 0
    $RUNPSQL "insert into bruce.yf_cluster (id,name,master_node_id) values (0,'$CLUSTER',0);" ||
        puke "psql failed "
    for N in $NODE_NUMBERS ; do
        set_uhp $N
        URI=$DBURL
        set_uhp 0
        psql -e $UHP $DB  -c "insert into bruce.node_cluster (node_id,cluster_id) values ($N,0);"  ||
            puke "psql failed "
    done
}

function distribute_snapshot(){
    set_uhp 0
    $RUNPSQL "select bruce.logsnapshot();" || puke "psql failed "
    psql -q -t $UHP -d $DB -c "select 'insert into bruce.slavesnapshotstatus (clusterid,slave_xaction,master_current_xaction,master_min_xaction,master_max_xaction,master_outstanding_xactions) values (0,0,' || current_xaction || ',' || min_xaction || ',' || max_xaction || ',' || ' \\'' || outstanding_xactions || ' \\'' || ');' from bruce.snapshotlog_1 order by current_xaction limit 1;" \
        -o slavesnapshot.sql   || puke "psql $UHP failed to create snapshot sql"
    for N in $NODE_NUMBERS ; do
        set_uhp $N
        $RUNPSQLF slavesnapshot.sql || puke "psql $UHP failed to run snapshotsql"
    done
}

function start_replication (){
    set_uhp 0
    echo >bruce.log
    nohup $RUN_JAVA $JAVAOPTS $BRUCE_OPTS -classpath $BRUCEJAR:$CLASSPATH $MAINCLASS $CLUSTER >${TESTUSER}-daemon.err  2>&1 &
    echo $! >bruce.pid
}

function run_pgbench (){
    set_uhp 0
    pgbench $UHP -d $DB -t 100 -c $(($SCALEFACTOR*2)) >${TESTUSER}-pgbench.err  2>&1
}

function insert_heartbeat (){
    ID=$1
    set_uhp 0
    $RUNPSQL "insert into pgbench_test_heartbeat (id,t) values ($ID,now());" || puke "psql failed "
}

function update_heartbeat (){
    ID=$1
    OLDID=$2
    set_uhp 0
    $RUNPSQL "update pgbench_test_heartbeat set id = $ID, t = now() where id = $OLDID ;" || puke "psql failed "
}

function delete_heartbeat (){
    ID=$1
    set_uhp 0
    $RUNPSQL "delete from pgbench_test_heartbeat where id = $ID;" || puke "psql failed "
}

function wait_for_heartbeat(){
    set_uhp $1
    ID=$2
    while : ; do
        echo >heartbeat.out
        $RUNPSQL "select 'grep_this_buddy',id,t,now()-t from pgbench_test_heartbeat where id = $ID ;" -o heartbeat.out || puke "psql failed "
        grep grep_this_buddy heartbeat.out
        if [ $? -eq 0 ] ; then 
            break
        fi
        sleep 10
    done
}

function wait_for_all(){
    ID=$1
    for N in $NODE_NUMBERS ; do 
        wait_for_heartbeat $N $ID
    done
}

function add_triggers(){
    add_master_triggers 0
    add_slave_triggers 1
    add_slave_triggers 2
    add_slave_triggers 3
    add_slave_triggers 4
    add_slave_triggers 5
}

function check_heartbeat(){
    insert_heartbeat 0
    wait_for_all 0
    update_heartbeat 1 0
    wait_for_all 1
    delete_heartbeat 1
    insert_heartbeat 0
    wait_for_all 0
    update_heartbeat 1 0
    wait_for_all 1
    delete_heartbeat 1
}

stop_any_running_db 
maketop
for N in $NODE_NUMBERS ; do
    makedb $N
done
setup_cluster
add_triggers
distribute_snapshot
start_replication
check_heartbeat

insert_heartbeat 1
wait_for_all 1
run_pgbench
insert_heartbeat 2
wait_for_all 2

#for N in 0 1 2 3 4 5 ; do psql -h localhost -p 5432${N} -U bruce -d bruce  -c "select sum(tbalance) from tellers; select sum(bbalance) from branches; select sum(abalance) from accounts;" ; done
