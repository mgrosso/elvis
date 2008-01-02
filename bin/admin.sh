#!/bin/sh
# ---------------------------------------------------------------------------
# This is the startup script for the replication admin tool.  It is based
# in part on the apache tomcat startup scripts.
#
# Environment variables:
#
#   JAVA_HOME   Must point to a Java 1.5 environment - either JRE or JDK.
#
# $Id: admin.sh 69033 2007-05-11 20:03:02Z lball $
# ---------------------------------------------------------------------------

ARGS=$*

# resolve links and get our working directory - $0 may be a softlink
PRG="$0"
while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

PRGDIR=`dirname "$PRG"`
cd $PRGDIR
PRGPATH=`pwd`

BRUCE_OPTS="-Dlog4j.configuration=${PRGPATH}/log4j.properties"

 # Be sure we have a JAVA_HOME set - default to /usr/local/java
[ -z "$JAVA_HOME" ] && JAVA_HOME=/usr/local/java
if [ ! -r "$JAVA_HOME/bin/java" ] ; then
    echo "JAVA_HOME environment variable is not set or points to an invalid Java installation."
    echo "Cannot continue"
    exit 1
fi

# Setup the classpath
LIB_DIR=../lib

if [ -d ../build/classes ] ; then
    CLASSPATH="../build/classes"
else
    CLASSPATH="../bruce.jar"
fi

for JAR in `find $LIB_DIR -name '*.jar'` ; do
    CLASSPATH=$CLASSPATH:$JAR
done

RUN_JAVA=$JAVA_HOME/bin/java

echo "Using JAVA_HOME:      $JAVA_HOME"

# Start the daemon and get its pid
"$RUN_JAVA" $BRUCE_OPTS -classpath "$CLASSPATH" com.netblue.bruce.admin.Main $* 
