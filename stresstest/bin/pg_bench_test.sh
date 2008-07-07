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

## ----------------------------------------------------------------------
## 1- define defaults and set options as needed.
## 2- define functions
## 3- run the argument
## ----------------------------------------------------------------------

## ----------------------------------------------------------------------
## 1- define defaults and set options as needed.
## ----------------------------------------------------------------------
ARG=$1
if [ -z $ARG ] ; then
    ARG=fulltest
fi

if [ -z $BASE ] ; then
    export BASE=elvis-pgbench-test
fi

if [ -z $LOGS ] ; then
    export LOGS=$BASE/logs/
fi

if [ -z $TESTUSER ] ; then
    TESTUSER=bruce
fi
if [ -z $CLUSTER_NAME ] ; then 
    CLUSTER_NAME=pg_bench_replication_test
fi

#see parse_topology() below
if [ -z $TOPOLOGY ] ; then 
    #TOPOLOGY=0,1,3,4:1,2:2,5
    TOPOLOGY=0,1
fi
ALL_CLUSTERS=$( echo $TOPOLOGY|tr ':' ' ' )
ALL_NODE_NUMBERS=$(  echo $TOPOLOGY|tr ':,' '  ' | xargs -n 1 echo | sort -u )
ROOT_NODE=$( echo $TOPOLOGY|tr ':,' '  ' | xargs -n 1 echo 2>/dev/null | head -1 )

if [ ! -z $BRUCE_CLASSPATH ] ; then
    CLASSPATH=$BRUCE_CLASSPATH
else
    if [ -z $LIB_DIR ] ; then
        LIB_DIR=lib
    fi
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

if [ -z $PGBENCH_CLIENTS ] ; then
    PGBENCH_CLIENTS=3
fi
if [ -z $PGBENCH_SCALEFACTOR ] ; then
    PGBENCH_SCALEFACTOR=6
fi
if [ -z $PGBENCH_TRANSACTIONS ] ; then
    PGBENCH_TRANSACTIONS=1000
fi

LOG_DESTINATION="log_destination = stderr
logging_collector = on
"
if [ -z $PGVER ] ; then
    psql --version | grep -q "8\.3\."
    if [ $? -eq 0 ] ; then
        PGVER=83
    else 
        psql --version | grep -q "8\.1\."
        if [ $? -eq 0 ] ;then 
            PGVER=81
            LOG_DESTINATION="redirect_stderr = on"
        else
            echo "warning. psql is missing or a bad version. this probably wont work."
            exit 1
        fi
    fi
fi

export DIGIT PG_DATA PORT DB UHP DBURL
export TOPOLOGY CLUSTER_TOPOLOGY NODE_NUMBERS MASTER_NODE SLAVE_NODES CLUSTER_NAME
export ALL_CLUSTERS ALL_NODE_NUMBERS ROOT_NODE

## ----------------------------------------------------------------------
## 2- define functions
## ----------------------------------------------------------------------

function puke(){
    echo $@
    exit 1
}

function do_or_puke (){
    echo do_or_puke $@
    $@
    if [ $? -ne 0 ] ; then
        puke "command failed: $@"
    fi
}


function maketop(){
    rm -rf $BASE
    mkdir -p $BASE
    mkdir -p $BASE/logs/
}

function foreach_cluster (){
    FUNC=$1
    shift
    for CLUSTER in $ALL_CLUSTERS ; do 
        $FUNC $CLUSTER $@
    done
}

function foreach_node (){
    FUNC=$1
    shift
    for NODE in $ALL_NODE_NUMBERS ; do 
        $FUNC $NODE $@
    done
}

function parse_topology(){
    CLUSTER_TOPOLOGY=$1
    NODE_NUMBERS=$(echo $CLUSTER_TOPOLOGY|tr ',' ' ' )
    MASTER_NODE=$(echo $CLUSTER_TOPOLOGY|cut -d ',' -f 1)
    local NODE_COUNT=$(echo $NODE_NUMBERS|xargs -n 1 echo | wc -l )
    local SLAVE_COUNT=$((NODE_COUNT-1))
    SLAVE_NODES=$(echo $NODE_NUMBERS|xargs -n 1 echo | tail -n ${SLAVE_COUNT})
    CLUSTER_NAME=cluster_${MASTER_NODE}
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
    BRUCE_OPTS="${EXTRA_BRUCE_OPTS} -Dpid.file=bruce.pid -Dpostgresql.db_name=${DB} -Dpostgresql.URL=${DBURL} -Dhibernate.connection.url=${DBURL} -Dhibernate.connection.username=${TESTUSER} -Dhibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"

}

function stop_any_running_db (){
    for N in $ALL_NODE_NUMBERS ; do
        set_uhp $N 
        while [ $( ps -fe | grep $PG_DATA | grep -v grep | wc -l ) -gt 0 ] ; do
            kill -QUIT $( ps -fe | grep $PG_DATA | grep -v grep | tr -s ' ' | cut -d ' ' -f 2 )
            sleep 1 
        done
        pg_ctl -D $PG_DATA -m immediate stop 
    done
    kill $( ps -ef | grep java | grep bruce.jar | grep -v grep | tr -s ' ' | cut -d ' ' -f 2 )
    sleep 3 
    kill -9 $( ps -ef | grep java | grep bruce.jar | grep -v grep | tr -s ' ' | cut -d ' ' -f 2 )
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
# $PGVER
    $LOG_DESTINATION
    log_directory = 'pg_log'
    fsync = off
    log_duration = on
    log_line_prefix = '%u d=%d %r p%p t=%m ses=%c[%l] tx=%x '           # Special values:
    log_statement = 'all'          # none, mod, ddl, all
EOF

    if [ $? -ne 0 ] ; then
        puke "command failed: cat >>$PG_DATA/postgresql.conf <<EOF "
    fi

}

function make_schema (){
    do_or_puke psql $UHP -e -f schema/replication-ddl.sql $DB >$LOGS/replication-ddl-${DIGIT}.out 2>&1
    do_or_puke psql $UHP -e -f schema/cluster-ddl.sql $DB >$LOGS/cluster-ddl-${DIGIT}.out 2>&1
    $RUNPSQL "create table pgbench_test_heartbeat ( id bigint primary key, t timestamp not null unique );" >$LOGS/create-table-${DIGIT}.out 2>&1 ||
            puke "psql failed to create table ${TESTUSER}_heartbeat"
    do_or_puke pgbench -i $UHP -d $DB -s $PGBENCH_SCALEFACTOR 
    $RUNPSQL "alter table public.history add column id bigserial primary key;" >>$LOGS/create-table-${DIGIT}.out 2>&1 ||
            puke "psql failed on [alter table public.history add column id bigserial primary key;]"
}

function makedb (){
    set_uhp $1
    do_or_puke mkdir -p $PG_DATA
    do_or_puke initdb -E utf8 -U $TESTUSER -D $PG_DATA
    edit_postgresql_conf
    startdb $1
    do_or_puke createdb -E utf8 $UHP -O $TESTUSER $DB
    make_schema
}

function startdb (){
    set_uhp $1
    nohup pg_ctl -D $PG_DATA start >$LOGS/pg.${DIGIT}.out 2>&1
    if [ $? -ne 0 ] ; then
        puke "could not start db with this command: pg_ctl -D $PG_DATA start >$LOGS/pg.${DIGIT}.out 2>&1 "
    fi
    waitfordb
}

function run_sql (){
    DIGIT=$1
    SQL=$2
    do_or_puke psql -e $UHP -d $DB -c "$SQL"
}

function add_slave_triggers(){
    set_uhp $1
    for TABLE in $PGBENCH_TABLES ; do
        $RUNPSQL "create trigger ${TABLE}_deny before insert or delete or update on $TABLE for each row execute procedure bruce.denyaccesstrigger();" ||
            puke "psql failed "
    done
}

function add_master_triggers(){
    set_uhp $1
    for TABLE in $PGBENCH_TABLES ; do
        $RUNPSQL "create trigger ${TABLE}_tx after insert or delete or update on $TABLE for each row execute procedure bruce.logtransactiontrigger();" ||
            puke "psql failed "
    done
}

function setup_cluster(){
    parse_topology $1
    for N in $NODE_NUMBERS ; do
        set_uhp $N
        URI=$DBURL
        set_uhp $MASTER_NODE
        psql -e $UHP $DB  -c "insert into bruce.yf_node (id,available,includetable,name,uri) values ($N,true,'$TABLEREGEX', 'node_$N','$URI');"  ||
            puke "psql failed "
    done
    #we use $MASTER_NODE as the cluster id
    $RUNPSQL "insert into bruce.yf_cluster (id,name,master_node_id) values ($MASTER_NODE,'$CLUSTER_NAME',$MASTER_NODE);" ||
        puke "psql failed "
    for N in $NODE_NUMBERS ; do
        psql -e $UHP $DB  -c "insert into bruce.node_cluster (node_id,cluster_id) values ($N,$MASTER_NODE);"  ||
            puke "psql failed "
    done
}

function distribute_snapshot(){
    parse_topology $1
    set_uhp $MASTER_NODE
    $RUNPSQL "select bruce.logsnapshot();" || puke "psql failed "
    psql -q -t $UHP -d $DB -c "select 'insert into bruce.slavesnapshotstatus (clusterid,slave_xaction,master_current_xaction,master_min_xaction,master_max_xaction,master_outstanding_xactions) values ($MASTER_NODE ,0,' || current_xaction || ',' || min_xaction || ',' || max_xaction || ',' || ' \\'' || outstanding_xactions || ' \\'' || ');' from bruce.snapshotlog order by current_xaction limit 1;" \
        -o slavesnapshot.sql   || puke "psql $UHP failed to create snapshot sql"
    for N in $SLAVE_NODES ; do
        set_uhp $N
        $RUNPSQLF slavesnapshot.sql || puke "psql $UHP failed to run snapshotsql"
    done
}

function start_replication (){
    parse_topology $1
    set_uhp $MASTER_NODE
    LOG=$LOGS/${CLUSTER_NAME}-${TESTUSER}-daemon.err
    LOG4JLOG=$LOGS/${CLUSTER_NAME}-${TESTUSER}-daemon.log
    LOG4JPROP=$BASE/log4j-${CLUSTER_NAME}.properties
    cat bin/log4j.properties >$LOG4JPROP
    echo "log4j.appender.R.File=$LOG4JLOG" >>$LOG4JPROP
    echo >$LOG
    nohup $RUN_JAVA $JAVAOPTS -Dlog4j.configuration=$LOG4JPROP $BRUCE_OPTS -classpath $BRUCEJAR:$CLASSPATH $MAINCLASS ${CLUSTER_NAME} >$LOG 2>&1 &
    echo $! >${LOGS}/${CLUSTER_NAME}-bruce.pid
}

function run_pgbench (){
    set_uhp $ROOT_NODE
    pgbench $UHP -d $DB -t $PGBENCH_TRANSACTIONS -c $PGBENCH_CLIENTS >$LOGS/${TESTUSER}-pgbench.err  2>&1
}

function insert_heartbeat (){
    ID=$1
    set_uhp $ROOT_NODE
    $RUNPSQL "insert into pgbench_test_heartbeat (id,t) values ($ID,now());" || puke "psql failed "
}

function insert_next_heartbeat (){
    ID=$1
    set_uhp $ROOT_NODE
    $RUNPSQL "insert into pgbench_test_heartbeat (id,t) select max(id)+1,now() from pgbench_test_heartbeat ;" || puke "psql failed "
}

function vacuum_heartbeat (){
    ID=$1
    set_uhp $ROOT_NODE
    $RUNPSQL "vacuum full pgbench_test_heartbeat;" || puke "psql failed "
}

function update_heartbeat (){
    ID=$1
    OLDID=$2
    set_uhp $ROOT_NODE
    $RUNPSQL "update pgbench_test_heartbeat set id = $ID, t = now() where id = $OLDID ;" || puke "psql failed "
}

function delete_heartbeat (){
    ID=$1
    set_uhp $ROOT_NODE
    $RUNPSQL "delete from pgbench_test_heartbeat where id = $ID;" || puke "psql failed "
}

function wait_for_heartbeat(){
    set_uhp $1
    ID=$2
    while : ; do
        echo >heartbeat.out
        $RUNPSQL "select 'grep_this_buddy',id,t,now()-t from pgbench_test_heartbeat where id = $ID ;" -o $LOGS/heartbeat.out || puke "psql failed "
        grep grep_this_buddy $LOGS/heartbeat.out
        if [ $? -eq 0 ] ; then 
            break
        fi
        sleep 10
    done
}

function wait_for_all(){
    ID=$1
    foreach_node wait_for_heartbeat $ID
}

function add_triggers(){
    for NODE in $ALL_NODE_NUMBERS ; do 
        add_master_triggers $NODE
    done
    for CLUSTER in $ALL_CLUSTERS ; do 
        parse_topology $CLUSTER
        for SLAVE in $SLAVE_NODES ; do
            add_slave_triggers $SLAVE
        done
    done
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

function validate (){
    for N in 0 1 2 3 4 5 ; do psql -h localhost -p 5432${N} -U bruce -d bruce  -c "select sum(tbalance) from tellers; select sum(bbalance) from branches; select sum(abalance) from accounts;" ; done
}


function _stop (){
    stop_any_running_db 
}

function _init (){
    _stop
    maketop
    foreach_node makedb
    foreach_cluster setup_cluster
    add_triggers
}

function _start(){
    foreach_cluster distribute_snapshot
    foreach_cluster start_replication
    check_heartbeat
}

function _startdb(){
    foreach_node startdb
}

function _startelvis(){
    foreach_cluster start_replication
}

function _sync(){
    insert_heartbeat $1
    wait_for_all $1
}

function _benchmark (){
    _sync 1
    run_pgbench
    _sync 2
}

function _vacuum_heartbeat (){
    foreach_node vacuum_heartbeat
}

function _fulltest (){
    _init
    _start
    _benchmark
    _stop
}

function _help (){

cat <<HELP
$0 : the usage is explained pretty well by the code snippet below.

case $ARG in 
     stop) _stop ;;     #stop all postgres and replication daemons
     init) _init ;;     #initialize databases and setup replication
     start) _start ;;   #install snapshot tables and start replication
     startdb) _startdb ;;   #start each postgres
     startelvis) _startelvis ;;   #start each replication daemon
     benchmark) _benchmark ;;   #run the pg_bench benchmark.
     fulltest) _fulltest ;;     #same as doing stop,init,start,benchmark,stop
     sync) _sync $2 ;;  #inserts a row with id of arg and waits for it to replicate
     async) _async $2 ;;  #inserts a row with a new id into the root node heartbeat table and returns immediately
     vacuum_heartbeat) _vacuum_heartbeat ;;  #vacuums the heartbeat tables
     help)              
     *) _help ;;        #print out usage information
esac

its also worth noting that you can configure the pg bench run using the
following variables listed below with their defaults (see postgresql
contrib/pg_bench for more information ... ):

    PGBENCH_CLIENTS=3
    PGBENCH_SCALEFACTOR=6
    PGBENCH_TRANSACTIONS=1000

for a really good time, try changing the default TOPOLOGY variable. the grammer of 
this variable is as follows...  

    <topology>  ::=   <cluster> { ":" <cluster> }
    <cluster> ::= <master> { "," <slave> }
    <master> ::= <node>
    <slave> ::= <node>
    <node> ::= digit

The default value is TOPOLOGY=0,1,3,4:1,2:2,5  which corresponds to a topology with
a root master node 0 with 3 slave nodes 1,3, and 4, where node 1 has node 2 as
a slave, and node 2 has node 5 as a slave in turn.

HELP
}

## ----------------------------------------------------------------------
## 3- run the argument
## ----------------------------------------------------------------------

case $ARG in 
     stop) _stop ;;     #stop all postgres and replication daemons
     init) _init ;;     #initialize databases and setup replication
     start) _start ;;   #initialize databases and replication daemons and start them
     startdb) _startdb ;;   #start databases 
     startelvis) _startelvis ;;   #start replication daemons 
     benchmark) _benchmark ;;   #run the pg_bench benchmark.
     fulltest) _fulltest ;;     #same as doing stop,init,start,benchmark,stop
     sync) _sync $2 ;;  #inserts a row with id of arg and waits for it to replicate
     async) _async $2 ;;  #inserts a row with a new id into the root node heartbeat table and returns immediately
     vacuum_heartbeat) _vacuum_heartbeat ;;  #vacuums heartbeat tables
     *) _help ;;        #print out usage information
esac
