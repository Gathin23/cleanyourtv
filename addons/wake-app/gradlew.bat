@echo off
rem Reuse the repository wrapper; this add-on remains its own Gradle project.
call "%~dp0..\..\launcher\gradlew.bat" -p "%~dp0." %*
