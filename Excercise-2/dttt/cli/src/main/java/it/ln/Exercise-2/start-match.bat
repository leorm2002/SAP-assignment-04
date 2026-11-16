@echo off
setlocal

set "ROOT=%~dp0dttt"

pushd "%ROOT%" || exit /b 1
call mvn -q package
if errorlevel 1 (
    echo Build failed.
    popd
    exit /b 1
)
popd

set "COMMONS=%ROOT%\commons\target\classes"
set "CLIENT_API=%ROOT%\client\clientapi\target\classes"
set "SERVER_API=%ROOT%\server\serverapi\target\classes"
set "CLI=%ROOT%\cli\target\classes"
set "CLIENT_GAME=%ROOT%\client\game\target\classes"
set "SERVER_GAME=%ROOT%\server\gameengine\target\classes"

set "SERVER_CP=%SERVER_GAME%;%SERVER_API%;%CLIENT_API%;%COMMONS%"
set "CLIENT_CP=%CLIENT_GAME%;%CLIENT_API%;%SERVER_API%;%CLI%;%COMMONS%"

start "TTT Server" /D "%ROOT%" cmd /k java -cp "%SERVER_CP%" it.ln.server.gameengine.Server
timeout /t 2 /nobreak >nul
start "TTT Player 1" /D "%ROOT%" cmd /k java -cp "%CLIENT_CP%" it.ln.client.game.GameLoop
start "TTT Player 2" /D "%ROOT%" cmd /k java -cp "%CLIENT_CP%" it.ln.client.game.GameLoop
