@echo off
chcp 65001 >nul
setlocal

set "PORT=3306"

echo ========================================
echo   Stop local MySQL (port %PORT%)
echo ========================================
echo.

:: Find PID listening on 3306
set "PID="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr /R /C:":%PORT% .*LISTENING"') do (
  set "PID=%%a"
  goto :have_pid
)

echo [OK] No process listening on port %PORT% ^(already stopped^).
goto :done

:have_pid
echo Found PID %PID% on port %PORT%.
tasklist /FI "PID eq %PID%" /FI "IMAGENAME eq mysqld.exe" 2>nul | findstr /I mysqld >nul
if errorlevel 1 (
  echo [WARN] PID %PID% is not mysqld.exe — not killing it.
  goto :fail
)

taskkill /PID %PID% /F >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Failed to stop PID %PID%. Try run as Administrator.
  goto :fail
)

timeout /t 2 /nobreak >nul
netstat -an | findstr /R /C:":%PORT% .*LISTENING" >nul 2>&1
if %ERRORLEVEL%==0 (
  echo [WARN] Port %PORT% still listening.
) else (
  echo [OK] MySQL stopped.
)
goto :done

:fail
echo.
pause
exit /b 1

:done
echo.
pause
exit /b 0
