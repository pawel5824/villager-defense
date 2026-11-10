@echo off
title Villager Defense - Packwiz Installer
echo.
echo ============================================
echo   Villager Defense - Packwiz Auto-Update
echo ============================================
echo.
echo Instalacja modpacka z auto-aktualizacja...
echo.

:: Sciezka do folderu .minecraft gracza
set "MC_DIR=%APPDATA%\.minecraft"

:: Utworz folder .minecraft jesli nie istnieje
if not exist "%MC_DIR%" (
    echo [!] Folder .minecraft nie znaleziony: %MC_DIR%
    echo     Ustaw MC_DIR w skrypcie recznie.
    pause
    exit /b 1
)

echo [1/3] Kopiowanie packwiz-installer-bootstrap.jar...
copy /Y "%~dp0packwiz-installer-bootstrap.jar" "%MC_DIR%\packwiz-installer-bootstrap.jar" >nul
if %errorlevel% neq 0 (
    echo [BLAD] Nie udalo sie skopiowac bootstrap JAR
    pause
    exit /b 1
)
echo       OK

:: Utwierz plik pack.toml w .minecraft (skopiuj z modpack/)
echo [2/3] Kopiowanie pack.toml...
copy /Y "%~dp0pack.toml" "%MC_DIR%\pack.toml" >nul
if %errorlevel% neq 0 (
    echo [BLAD] Nie udalo sie skopiowac pack.toml
    pause
    exit /b 1
)
echo       OK

echo [3/3] Tworzenie skryptu auto-update...
:: Utworz launch wrapper
(
echo @echo off
echo title Villager Defense - Auto-Update
echo cd /d "%%~dp0"
echo java -jar packwiz-installer-bootstrap.jar https://raw.githubusercontent.com/YOUR_USERNAME/YOUR_REPO/refs/heads/main/modpack/pack.toml
echo if %%errorlevel%% neq 0 ^(
echo     echo [!] Auto-update nie powiodlo sie. Uruchamiam Minecraft...
echo ^)
echo "%%APPDATA%%\..\Local\Programs\PrismLauncher\prismlauncher.exe" --launch "Villager Defense"
) > "%MC_DIR%\launch-vd.bat"

echo       OK
echo.
echo ============================================
echo   INSTALACJA ZAKONCZONA
echo ============================================
echo.
echo Nastepne kroki:
echo 1. Edytuj launch-vd.bat i zmien URL na prawdziwy link GitHub
echo 2. Uruchom launch-vd.bat przed kazda gra
echo 3. Auto-update sprawdzi i pobierze nowosci
echo.
echo Aby uruchomic manualnie:
echo   cd %MC_DIR%
echo   java -jar packwiz-installer-bootstrap.jar ^<URL_PACK.TOML^>
echo.
pause
