#!/bin/sh
# ---------------------------------------------------------------------------
# LM-GUARD lightweight Maven wrapper.
#
#   1. If a `mvn` is already on the PATH, use it (fast path, no download).
#   2. Otherwise download the Maven distribution pinned in
#      .mvn/wrapper/maven-wrapper.properties into ~/.m2/wrapper/dists and use it.
#
# Usage:  ./mvnw clean verify
#         ./mvnw spring-boot:run
# ---------------------------------------------------------------------------
set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
WRAPPER_PROPS="$SCRIPT_DIR/.mvn/wrapper/maven-wrapper.properties"

if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi

if [ ! -f "$WRAPPER_PROPS" ]; then
  echo "mvnw: missing $WRAPPER_PROPS and no 'mvn' on PATH." >&2
  echo "mvnw: install Maven 3.9+ (https://maven.apache.org/download.cgi) and retry." >&2
  exit 1
fi

DIST_URL=$(sed -n 's/^distributionUrl=//p' "$WRAPPER_PROPS" | tr -d '\r')
if [ -z "$DIST_URL" ]; then
  echo "mvnw: distributionUrl not set in $WRAPPER_PROPS" >&2
  exit 1
fi

ZIP_NAME=$(basename "$DIST_URL")
DIST_NAME=$(echo "$ZIP_NAME" | sed 's/-bin\.zip$//')
DIST_HOME="${MAVEN_USER_HOME:-$HOME/.m2}/wrapper/dists/$DIST_NAME"

if [ ! -x "$DIST_HOME/bin/mvn" ]; then
  echo "mvnw: downloading $DIST_URL ..." >&2
  mkdir -p "$DIST_HOME"
  TMP_ZIP="$DIST_HOME/$ZIP_NAME"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "$TMP_ZIP" "$DIST_URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$TMP_ZIP" "$DIST_URL"
  else
    echo "mvnw: neither curl nor wget available to download Maven." >&2
    exit 1
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$TMP_ZIP" -d "$DIST_HOME.tmp"
  else
    echo "mvnw: 'unzip' is required to unpack the Maven distribution." >&2
    exit 1
  fi
  rm -f "$TMP_ZIP"
  rm -rf "$DIST_HOME"
  mv "$DIST_HOME.tmp/$DIST_NAME" "$DIST_HOME"
  rm -rf "$DIST_HOME.tmp"
  chmod +x "$DIST_HOME/bin/mvn"
fi

exec "$DIST_HOME/bin/mvn" "$@"
