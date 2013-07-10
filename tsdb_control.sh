#!/bin/bash

# Find the location of the bin directory and change to the root of opentsdb 
TSDB_BASE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$TSDB_BASE_DIR"
TSDB_BUILD_DIR=$TSDB_BASE_DIR/build

# Load up the classpath
for jar in $TSDB_BUILD_DIR/*.jar; do
        CLASSPATH="$CLASSPATH:$jar"
done

# Add dependency jars to classpath
for jar in `make -C "$TSDB_BUILD_DIR" printdeps | sed '/third_party.*jar/!d'`; do
  for dir in "$TSDB_BUILD_DIR" "$TSDB_BASE_DIR"; do
    test -f "$dir/$jar" && CLASSPATH="$CLASSPATH:$dir/$jar" && continue 2
  done
  echo >&2 "$me: error: Couldn't find \`$jar' either under \`$TSDB_BUILD_DIR' or \`$TSDB_BASE_DIR'."
  exit 2
done
# Add the src dir so we can find logback.xml
CLASSPATH="$CLASSPATH:$TSDB_BASE_DIR/src"

JAVA_OPTS=

if [ "$TS_PID_FILE" = "" ]; then
        TS_PID_FILE=$TSDB_BASE_DIR/tsdb.pid
fi

# Use JAVA_HOME if set, otherwise look for java in PATH
if [ -n "$JAVA_HOME" ]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA=java
fi

function start()
{
        test -r "$TSDB_BUILD_DIR/tsdb.local" && . "$TSDB_BUILD_DIR/tsdb.local"
        exec $JAVA $JAVA_OPTS -classpath "$CLASSPATH" net.opentsdb.tools.TSDMain --port=$TSD_LISTENING_PORT --staticroot=$TSDB_BUILD_DIR/staticroot/ --cachedir=$TSDB_BASE_DIR/tmp/ "$@" &
        echo $! > "$TS_PID_FILE"
}

function stop()
{
        kill `cat $TS_PID_FILE` > /dev/null 2>&1
        while kill -0 `cat $TS_PID_FILE` > /dev/null 2>&1; do
                echo -n "."
                sleep 1;
        done
	echo
        rm $TS_PID_FILE
}

if [ "$1" = "start" ] ; then
        shift && start
elif [ "$1" = "stop" ] ; then
        shift && stop
elif [ "$1" = "restart" ] ; then
        shift && stop && start
else
        echo "Unrecognized command."
        exit 1
fi
