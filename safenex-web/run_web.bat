@echo off
title SAFENEX Web Server
echo ===================================================
echo ???  SAFENEX Web Application Server
echo URL: http://localhost:8080/
echo ===================================================
start http://localhost:8080/
powershell -ExecutionPolicy Bypass -File "%~dp0server.ps1"
pause
