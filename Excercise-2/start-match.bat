@echo off
setlocal
cd /d "%~dp0dttt" || exit /b 1

call mvn -q install
if errorlevel 1 exit /b 1

start "TTT Server" cmd /k mvn -q -pl server/gameengine exec:exec
timeout /t 2 /nobreak >nul
start "TTT Player 1" cmd /k mvn -q -pl client/game exec:exec
start "TTT Player 2" cmd /k mvn -q -pl client/game exec:exec
