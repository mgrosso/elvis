#!/bin/bash

PRGDIR=`dirname "$0"`
cd $PRGDIR
PRGPATH=`pwd`
LIBPATH=$PRGPATH/../lib

CLASSPATH=""
for i in `find $LIBPATH -name "*.jar"` $PRGPATH/../bruce.jar ; do
    if [ "$CLASSPATH" == "" ] ; then
	CLASSPATH=$i
    else
	CLASSPATH=$CLASSPATH:$i
    fi
done

java -classpath $CLASSPATH com.netblue.bruce.PgJDBCBench $@