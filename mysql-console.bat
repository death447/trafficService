@echo off
chcp 65001 >nul
setlocal

set "MYSQL=C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
set "PORT=3306"

echo ========================================
echo   MySQL console  root@127.0.0.1:%PORT%
echo ========================================
echo.

if not exist "%MYSQL%" (
  echo [ERROR] mysql client not found:
  echo   %MYSQL%
  pause
  exit /b 1
)

netstat -an | findstr /R /C:":%PORT% .*LISTENING" >nul 2>&1
if errorlevel 1 (
  echo [ERROR] MySQL is not running. Double-click start-mysql.bat first.
  pause
  exit /b 1
)

echo Connecting... ^(password = backend application.yml root password^)
echo.
"%MYSQL%" -uroot -p --protocol=tcp -h127.0.0.1 -P%PORT% --default-character-set=utf8mb4

echo.
pause
