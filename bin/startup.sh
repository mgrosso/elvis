#!/bin/sh
# ---------------------------------------------------------------------------
# This is the main startup script for the replication engine.  It is based
# in part on the apache tomcat startup scripts.
#
# Environment variables:
#
#   JAVA_HOME   Must point to a Java 1.5 environment - either JRE or JDK.
#
# $Id$
# ---------------------------------------------------------------------------

CLUSTER=$1
POSTGRESQL_DB_NAME=bruce_config
POSTGRESQL_PORT=5432
POSTGRESQL_URL=jdbc:postgresql://localhost:${POSTGRESQL_PORT}/${POSTGRESQL_DB_NAME}?user=${USER}

HIBERNATE_CONNECTION_URL=${POSTGRESQL_URL}
HIBERNATE_CONNECTION_USERNAME=${USER}
HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect

PID_FILE=bruce.pid

# Check to see if there is already a running daemon
if [ -f $PID_FILE ] ; then
  RUNNING_PID=`cat $PID_FILE`
  echo "PID file exists.  Checking status of PID ${RUNNING_PID}"
  if ps -p "${RUNNING_PID}" | grep -v PID > /dev/null 2>&1
  then
    echo "It appears that a replication daemon is already running as process ID $RUNNING_PID.  Please shutdown the running process first"
    exit 1
  fi
fi

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

BRUCE_OPTS="-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Dpid.file=${PID_FILE} -Dlog4j.configuration=file://${PRGPATH}/log4j.properties -Dpostgresql.db_name=${POSTGRESQL_DB_NAME} -Dpostgresql.URL=${POSTGRESQL_URL} -Dhibernate.connection.url=${POSTGRESQL_URL} -Dhibernate.connection.username=${USER} -Dhibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"

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
CLASSPATH="$CLASSPATH":"$LIB_DIR"/postgresql-8.1-407.jdbc3.jar:"$LIB_DIR"/hibernate/hibernate3.jar
CLASSPATH="$CLASSPATH":"$LIB_DIR"/hibernate/hibernate-annotations.jar:"$LIB_DIR"/hibernate/hibernate-commons-annotations.jar
CLASSPATH="$CLASSPATH":"$LIB_DIR"/hibernate/commons-logging-1.0.4.jar:"$LIB_DIR"/hibernate/c3p0-0.9.1.jar
CLASSPATH="$CLASSPATH":"$LIB_DIR"/hibernate/asm.jar:"$LIB_DIR"/hibernate/asm-attrs.jar:"$LIB_DIR"/hibernate/jta.jar
CLASSPATH="$CLASSPATH":"$LIB_DIR"/hibernate/cglib-2.1.3.jar:"$LIB_DIR"/hibernate/dom4j-1.6.1.jar
CLASSPATH="$CLASSPATH":"$LIB_DIR"/hibernate/ehcache-1.2.3.jar:"$LIB_DIR"/hibernate/ejb3-persistence.jar
CLASSPATH="$CLASSPATH":"$LIB_DIR"/hibernate/jdbc2_0-stdext.jar:"$LIB_DIR"/hibernate/jta.jar:"$LIB_DIR"/hibernate/xml-apis.jar
CLASSPATH="$CLASSPATH":"$JAVA_HOME"/lib/tools.jar

RUN_JAVA=$JAVA_HOME/bin/java


echo "Using JAVA_HOME:      $JAVA_HOME"
#echo "Using CLASSPATH:      $CLASSPATH"
#echo "Running:              $RUN_JAVA $BRUCE_OPTS -classpath $CLASSPATH com.netblue.bruce.Main $CLUSTER <&- &"

# Start the daemon and get its pid
"$RUN_JAVA" $BRUCE_OPTS -classpath "$CLASSPATH" com.netblue.bruce.Main "$CLUSTER" <&- &
PID=$!

# Give the process a few seconds to fail if it's going to
sleep 5

if ps -p "${PID}" | grep -v PID > /dev/null 2>&1
then
    echo ${PID} > ${PID_FILE}
else
    echo "Unable to start daemon."
fi