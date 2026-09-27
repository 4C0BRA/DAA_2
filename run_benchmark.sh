#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
javac -encoding UTF-8 -d out src/*.java

java -Xms1g -Xmx1g -XX:+UseG1GC -cp out Benchmark

java -Xms1g -Xmx1g -XX:+UseG1GC -XX:G1HeapRegionSize=32m -cp out Benchmark --only w3 --tag g1_32mb_regions
java -Xms1g -Xmx1g -XX:+UseParallelGC -cp out Benchmark --only w3 --tag parallelgc

python3 scripts/plot_results.py
