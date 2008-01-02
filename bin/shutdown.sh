#!/bin/sh
# ---------------------------------------------------------------------------
# This is the shutdown script for the replication engine.
#
# $Id$
# ---------------------------------------------------------------------------

PID_FILE=bruce.pid


if [ -e bruce.pid ] ; then
    PID=`cat $PID_FILE`
    if ps -p $PID | grep -v PID > /dev/null 2>&1
    then
        kill $PID
    else
        echo "No process with PID $PID exists; unable to shutdown."
    fi
else
    echo "No PID file exists; unable to shutdown."
fi