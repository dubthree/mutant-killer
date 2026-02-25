#!/bin/sh
# Maven Wrapper script

set -e

# Determine Java command
if [ -n "$JAVA_HOME" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
else
    JAVA_CMD="java"
fi

# Project base directory
BASE_DIR=$(cd "$(dirname "$0")" && pwd)
WRAPPER_JAR="$BASE_DIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_PROPERTIES="$BASE_DIR/.mvn/wrapper/maven-wrapper.properties"

# Read distribution URL from properties
if [ -f "$WRAPPER_PROPERTIES" ]; then
    DIST_URL=$(sed -n 's/^distributionUrl=//p' "$WRAPPER_PROPERTIES" | tr -d '\r')
fi
DIST_URL="${DIST_URL:-https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip}"

# Download wrapper JAR if missing
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading Maven Wrapper..."
    mkdir -p "$BASE_DIR/.mvn/wrapper"
    if command -v curl > /dev/null; then
        curl -fsSL -o "$WRAPPER_JAR" "https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"
    elif command -v wget > /dev/null; then
        wget -q -O "$WRAPPER_JAR" "https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"
    else
        echo "Error: curl or wget required to download Maven Wrapper"
        exit 1
    fi
fi

exec "$JAVA_CMD" \
    -classpath "$WRAPPER_JAR" \
    "-Dmaven.multiModuleProjectDirectory=$BASE_DIR" \
    org.apache.maven.wrapper.MavenWrapperMain \
    "$@"
