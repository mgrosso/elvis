#!/bin/bash
#------------------------------------------------------------------------------
# This script will run a stress test of the replication system, with the
# following steps:
#
# Shutdown any running replication daemon and remove log files.
# Create 4 databases: bruce_config, bruce_master, bruce_slave_1
#   and bruce_slave_2.
# Initialize the configuration database and load it with sample
#   cluster data from ../data/config.xml
# Start the replication daemon for the sample cluster
# Run pgJDBCBench on the master database
# Print the size of the replicated tables for all databases
# Shutdown the daemon
#------------------------------------------------------------------------------


PRGDIR=`dirname "$0"`
cd $PRGDIR
PRGPATH=`pwd`
TOP=../../
LIBPATH=$TOP/lib

CLASSPATH=""
for i in `find $LIBPATH -name "*.jar"` $TOP/bruce.jar $TOP/bruce-tests.jar ; do
    if [ "$CLASSPATH" == "" ] ; then
	CLASSPATH=$i
    else
	CLASSPATH=$CLASSPATH:$i
    fi
done

PSQL_COMMAND=psql

RESULTS_FILE=../data/results.sql
CONFIG_DB=jdbc:postgresql://localhost:5432/bruce_config?user=bruce
MASTER_DB=jdbc:postgresql://localhost:5432/bruce_master?user=bruce
SLAVE1_DB=jdbc:postgresql://localhost:5432/bruce_slave_1?user=bruce
SLAVE2_DB=jdbc:postgresql://localhost:5432/bruce_slave_2?user=bruce


# Step 0.  If there is a running daemon, kill it
if [ -f $TOP/bin/bruce.pid ] ; then
  echo Shutting down running replication daemon.  This will take 60 seconds
  $TOP/bin/shutdown.sh
  sleep 60
fi

# Step 1. Remove the current log file
if [ -f bruce.log ] ; then
  echo Removing old log files.
  rm $TOP/bin/bruce.log
fi

# Step 2. Run TestDaemon - this cleans the databases and primes them with fresh data,
#                          then provisions the cluster and starts the daemon
java -classpath $CLASSPATH com.netblue.bruce.profile.TestDaemon -uri $MASTER_DB $*


# Step 5.  Print a count of each table in each database.  This is a rough
# test, since we don't compare values in the tables.  Branches, accounts,
# and tellers all have modified data.  But until we get to the point that
# the tables are reliably replicated, we'll just print counts
# Sleep for a sec to be sure the master has replicated everthing
sleep 2
echo "MASTER"
$PSQL_COMMAND bruce_master < $RESULTS_FILE
echo
echo "SLAVE ONE"
$PSQL_COMMAND bruce_slave_1 < $RESULTS_FILE
echo
echo "SLAVE TWO"
$PSQL_COMMAND bruce_slave_2 < $RESULTS_FILE

