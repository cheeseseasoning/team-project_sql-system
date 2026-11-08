@echo off

if not defined SQLFLOW_SSH_KEY (
    echo Please set the SQLFLOW_SSH_KEY environment variable.
    pause
    exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0connect-db.ps1" -KeyPath "%SQLFLOW_SSH_KEY%" -SshUser "ubuntu"
set "TUNNEL_EXIT_CODE=%ERRORLEVEL%"
pause
exit /b %TUNNEL_EXIT_CODE%