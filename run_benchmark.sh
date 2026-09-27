#!/usr/bin/env bash
# Reproduces every table and plot in results/.
set -euo pipefail
cd "$(dirname "$0")"
javac -encoding UTF-8 -d out src/*.java

# Main run: all four workloads + JDK reference, default collector (G1) made explicit.
java -Xms1g -Xmx1g -XX:+UseG1GC -cp out Benchmark

# Ablation for workload 3 (why the array's shift loop is slow at n = 100 000):
#  a) G1 with 32 MB regions: the whole array + its elements fit in one region;
#  b) Parallel collector: plain card-marking barrier on every reference store.
java -Xms1g -Xmx1g -XX:+UseG1GC -XX:G1HeapRegionSize=32m -cp out Benchmark --only w3 --tag g1_32mb_regions
java -Xms1g -Xmx1g -XX:+UseParallelGC -cp out Benchmark --only w3 --tag parallelgc

# Plots (needs Python 3 with matplotlib).
python3 scripts/plot_results.py
