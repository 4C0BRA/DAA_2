@echo off
cd /d "%~dp0"
javac -encoding UTF-8 -d out src\*.java || exit /b 1
java -cp out Tests
