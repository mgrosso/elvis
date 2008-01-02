#!/bin/bash

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

java -classpath $CLASSPATH com.netblue.bruce.PgJDBCBench $@ 