@echo off
cd /d "%~dp0"
javac -encoding UTF-8 -d out src\*.java || exit /b 1
java -Xms1g -Xmx1g -XX:+UseG1GC -cp out Benchmark || exit /b 1
java -Xms1g -Xmx1g -XX:+UseG1GC -XX:G1HeapRegionSize=32m -cp out Benchmark --only w3 --tag g1_32mb_regions || exit /b 1
java -Xms1g -Xmx1g -XX:+UseParallelGC -cp out Benchmark --only w3 --tag parallelgc || exit /b 1
python scripts\plot_results.py
