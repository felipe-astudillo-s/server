@echo off
rem Instalacion del server. Se corre una sola vez.
cd /d "%~dp0"

java -version >nul 2>&1
if errorlevel 1 (
    echo.
    echo No encuentro Java en esta computadora.
    echo Instalalo desde https://adoptium.net y volve a intentar.
    echo.
    pause
    exit /b 1
)

java -jar mcbackup.jar instalar
echo.
pause
