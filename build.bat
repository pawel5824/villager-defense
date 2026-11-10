@echo off
title Villager Defense - Build
cd /d C:\Users\wikto\Desktop\villager-defense
echo.
echo Budowanie pluginu Villager Defense...
echo.
call mvn clean package
if %errorlevel% neq 0 (
    echo.
    echo [BLAD] Budowanie nie powiodlo sie! Sprawdz logi powyzej.
    pause
    exit /b 1
)
echo.
echo [OK] Budowanie zakonczone! JAR znajduje sie w: target\ServerPlugin.jar
echo.
pause
