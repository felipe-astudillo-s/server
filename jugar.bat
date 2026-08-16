@echo off
rem Abre el server. Baja el mundo antes y lo sube al cerrar.
rem Para cerrar bien, escribi 'stop' en la consola del server.
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

java -jar mcbackup.jar host %*
echo.
pause
