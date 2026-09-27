#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
javac -encoding UTF-8 -d out src/*.java
java -cp out Tests
