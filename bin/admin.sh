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

CLASSPATH="$LIB_DIR"/../bruce.jar:"$LIB_DIR"/commons-collections-3.2.jar:"$LIB_DIR"/commons-dbcp-1.2.1.jar
CLASSPATH="$CLASSPATH":"$LIB_DIR"/commons-lang-2.3.jar:"$LIB_DIR"/commons-pool-1.3.jar:"$LIB_DIR"/log4j-1.2.14.jar
CLASSPATH="$CLASSPATH":"$LIB_DIR"/postgresql-8.1-407.jdbc3.jar:"$LIB_DIR"/args4j-2.0.7.jar:"$LIB_DIR"/dbunit-2.2.jar
CLASSPATH="$CLASSPATH":"$LIB_DIR"/hibernate/hibernate3.jar:"$LIB_DIR"/hibernate/hibernate-annotations.jar:"$LIB_DIR"/hibernate/hibernate-commons-annotations.jar
CLASSPATH="$CLASSPATH":"$LIB_DIR"/hibernate/commons-logging-1.0.4.jar:"$LIB_DIR"/hibernate/c3p0-0.9.1.jar
CLASSPATH="$CLASSPATH":"$LIB_DIR"/hibernate/asm.jar:"$LIB_DIR"/hibernate/asm-attrs.jar:"$LIB_DIR"/hibernate/jta.jar
CLASSPATH="$CLASSPATH":"$LIB_DIR"/hibernate/cglib-2.1.3.jar:"$LIB_DIR"/hibernate/dom4j-1.6.1.jar
CLASSPATH="$CLASSPATH":"$LIB_DIR"/hibernate/ehcache-1.2.3.jar:"$LIB_DIR"/hibernate/ejb3-persistence.jar
CLASSPATH="$CLASSPATH":"$LIB_DIR"/hibernate/jdbc2_0-stdext.jar:"$LIB_DIR"/hibernate/jta.jar:"$LIB_DIR"/hibernate/xml-apis.jar
CLASSPATH="$CLASSPATH":"$JAVA_HOME"/lib/tools.jar

RUN_JAVA=$JAVA_HOME/bin/java


echo "Using JAVA_HOME:      $JAVA_HOME"

# Start the daemon and get its pid
"$RUN_JAVA" $BRUCE_OPTS -classpath "$CLASSPATH" com.netblue.bruce.admin.Main $* 
