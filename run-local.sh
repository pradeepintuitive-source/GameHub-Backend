#!/usr/bin/env bash
set -euo pipefail

# Helper to run GameHub locally with the bundled JDK 21 installation
export JAVA_HOME=/Users/swethamurthy1/.jdk/jdk-21.0.10/jdk-21.0.10+7/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

echo "JAVA_HOME=$JAVA_HOME"

# If Railway CLI is available, use it to inject service env vars
if command -v railway >/dev/null 2>&1; then
  echo "Railway CLI detected — running with Railway environment variables"
  railway run -- mvn spring-boot:run
else
  echo "Running mvn spring-boot:run (ensure GAMEHUB_DB_* env vars are set if needed)"
  mvn spring-boot:run
fi
