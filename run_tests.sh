#!/usr/bin/env bash
# Compile everything and run the correctness suite.
set -euo pipefail
cd "$(dirname "$0")"
javac -encoding UTF-8 -d out src/*.java
java -cp out Tests
