@echo off
REM Build entry point.
REM Delegates to build.ps1, bypassing the PowerShell execution policy for this call only.
REM
REM Usage:
REM   build.cmd                       -> assembleDebug
REM   build.cmd testDebugUnitTest
REM   build.cmd assembleRelease
REM
REM NOTE: keep this file pure ASCII. cmd.exe reads .cmd files using the system
REM ANSI codepage; non-ASCII comments become mojibake and get executed as commands.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1" %*
exit /b %ERRORLEVEL%
