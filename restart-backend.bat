@echo off
setlocal
call "%~dp0stop-backend.bat"
call "%~dp0start-backend.bat" %*
