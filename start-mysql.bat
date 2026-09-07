@echo off
chcp 65001 >nul
setlocal

set "MYSQLD=C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe"
set "INI=%~dp0.mysql-run.ini"
set "PORT=3306"

echo ========================================
echo   Start local MySQL (background)
echo ========================================
echo.

if not exist "%MYSQLD%" (
  echo [ERROR] mysqld not found:
  echo   %MYSQLD%
  goto :fail
)

if not exist "%INI%" (
  echo [ERROR] config not found:
  echo   %INI%
  goto :fail
)

:: Already listening?
netstat -an | findstr /R /C:":%PORT% .*LISTENING" >nul 2>&1
if %ERRORLEVEL%==0 (
  echo [OK] MySQL already running on port %PORT%.
  goto :done
)

echo Starting mysqld as a detached background process...
echo Config: "%INI%"
echo.

:: Detached process (not a child of this cmd window).
:: Closing this window will NOT stop MySQL.
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "Start-Process -FilePath '%MYSQLD%' -ArgumentList '--defaults-file=%INI%' -WindowStyle Hidden"

if errorlevel 1 (
  echo [ERROR] Failed to launch mysqld.
  goto :fail
)

:: Wait up to ~30s for port
set /a n=0
:wait
set /a n+=1
netstat -an | findstr /R /C:":%PORT% .*LISTENING" >nul 2>&1
if %ERRORLEVEL%==0 goto :ready
if %n% GEQ 30 goto :timeout
timeout /t 1 /nobreak >nul
goto :wait

:ready
echo [OK] MySQL is ready on 127.0.0.1:%PORT%
echo.
echo Database: vue_springboot_system
echo User:     root
echo Password: ^(same as backend application.yml^)
echo.
echo Tip: close this window anytime; MySQL keeps running.
echo      Use stop-mysql.bat to shut it down.
goto :done

:timeout
echo [ERROR] Started but port %PORT% not listening within 30s.
echo Check error log under D:\mysql-data-cursor\*.err
goto :fail

:fail
echo.
pause
exit /b 1

:done
echo.
pause
exit /b 0
