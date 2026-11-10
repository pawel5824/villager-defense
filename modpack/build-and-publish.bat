@echo off
REM Narzedzie CZESCIOWE (sam mod; pyta "Wypchnac na GitHub? (t/n)").
REM Cala publikacje bez pytania — mod + plugin + paczka — robi: bash tools/publish-all.sh
setlocal
title Villager Defense - Build and Publish
echo.
echo ============================================
echo   Villager Defense - Build and Publish
echo ============================================
echo.
echo Mod raft-hud jedzie do graczy Z TEGO REPO
echo (modpack/mods/raft-hud-1.0.0.jar), a nie z wydania na GitHubie.
echo Nie potrzebujesz zadnego tokenu - wystarczy push.
echo.

set "MODPACK=%~dp0"
set "ROOT=%~dp0.."
set "PACK=%ROOT%\pack"
set "JAR=raft-hud-1.0.0.jar"

:: --- 1. Kontrola repo ----------------------------------------------------
:: Sprawdzamy to PRZED buildem, zeby nie skonczyc z commitem, ktorego nie ma gdzie wypchnac.
echo [1/6] Kontrola repo (remote i galaz)...
cd /d "%ROOT%"
set "REMOTE="
set "BRANCH="
set "HASUP="
set "STAGED="
for /f "delims=" %%R in ('git remote') do if not defined REMOTE set "REMOTE=%%R"
if not defined REMOTE (
    echo.
    echo [BLAD] To repo nie ma zadnego remote, wiec nie ma gdzie wypchnac paczki.
    echo        Dodaj go raz i uruchom ponownie:
    echo.
    echo            git remote add origin ^<adres-repo^>
    echo.
    pause
    exit /b 1
)
for /f "delims=" %%B in ('git rev-parse --abbrev-ref HEAD') do set "BRANCH=%%B"
if /i "%BRANCH%"=="HEAD" (
    echo.
    echo [BLAD] Repo jest w stanie detached HEAD - nie wiadomo, co wypchnac.
    echo        Przelacz sie na galaz:  git switch master
    echo.
    pause
    exit /b 1
)
git rev-parse --abbrev-ref --symbolic-full-name "@{u}" >nul 2>&1 && set "HASUP=1"
echo       remote: %REMOTE%   galaz: %BRANCH%

:: --- 2. Build moda -------------------------------------------------------
echo.
echo [2/6] Budowanie moda...
cd /d "%PACK%"
call "%PACK%\gradlew.bat" build --console=plain -q
if errorlevel 1 (
    echo.
    echo [BLAD] Build nie powiodl sie - nic nie zostalo zmienione.
    pause
    exit /b 1
)
if not exist "%PACK%\build\libs\%JAR%" (
    echo [BLAD] Brak pliku pack\build\libs\%JAR%
    pause
    exit /b 1
)
echo       OK

:: --- 3. Kopiowanie JAR-a do paczki --------------------------------------
echo.
echo [3/6] Kopiowanie JAR-a do modpack\mods...
copy /Y "%PACK%\build\libs\%JAR%" "%MODPACK%mods\%JAR%" >nul
if errorlevel 1 (
    echo [BLAD] Nie udalo sie skopiowac JAR-a.
    pause
    exit /b 1
)
for /f "tokens=*" %%H in ('powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 '%MODPACK%mods\%JAR%').Hash.ToLower()"') do set "JARHASH=%%H"
echo       sha256: %JARHASH%

:: --- 4. Odswiezenie indeksu packwiz -------------------------------------
echo.
echo [4/6] packwiz refresh (indeks + hashe)...
cd /d "%MODPACK%"
packwiz refresh
if errorlevel 1 (
    echo [BLAD] packwiz refresh nie powiodl sie. Czy packwiz jest w PATH?
    pause
    exit /b 1
)

:: --- 5. Kontrola spojnosci ----------------------------------------------
echo.
echo [5/6] Kontrola spojnosci paczki...
for /f "tokens=*" %%H in ('powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 '%MODPACK%index.toml').Hash.ToLower()"') do set "IDXHASH=%%H"
findstr /C:"%IDXHASH%" "%MODPACK%pack.toml" >nul
if errorlevel 1 (
    echo [BLAD] Hash index.toml w pack.toml sie nie zgadza - nie pushuje.
    echo        index.toml: %IDXHASH%
    pause
    exit /b 1
)
findstr /C:"%JARHASH%" "%MODPACK%index.toml" >nul
if errorlevel 1 (
    echo [BLAD] Hash JAR-a nie trafil do index.toml - nie pushuje.
    pause
    exit /b 1
)
if exist "%MODPACK%mods\raft-hud.pw.toml" (
    echo [BLAD] Wraca stary problem: istnieje modpack\mods\raft-hud.pw.toml.
    echo        Ten plik ciagnie moda z wydania GitHub i dubluje wpis dla
    echo        tego samego pliku. Usun go i uruchom ponownie.
    pause
    exit /b 1
)
echo       OK - JAR i indeks zgodne, brak duplikatu

:: --- 6. Publikacja ------------------------------------------------------
:: Ten skrypt commituje i wypycha WYLACZNIE pliki, ktore sam generuje:
::   modpack/index.toml, modpack/pack.toml, modpack/mods/raft-hud-1.0.0.jar
:: Dlatego commit idzie z "--only <sciezki>": nawet jesli ktos (albo inne
:: narzedzie) ma juz cos przygotowane w indeksie (git add), to nie wejdzie
:: do tego commita - zostanie przygotowane na pozniej, nietkniete.
:: Na koniec skrypt sprawdza zawartosc commita i nie pushuje, jesli trafilo
:: do niego cokolwiek spoza tej listy.
echo.
echo [6/6] Publikacja paczki...
cd /d "%ROOT%"
echo.
echo Stan paczki (tylko modpack):
git status --short modpack
for /f "delims=" %%S in ('git diff --cached --name-only') do set "STAGED=1"
if defined STAGED (
    echo.
    echo Uwaga: w indeksie sa juz przygotowane zmiany. Ten skrypt ich NIE dotknie
    echo        i NIE wejda do commita - zostana przygotowane na pozniej:
    git diff --cached --name-only
)
echo.
set /p "GO=Wypchnac na GitHub? (t/n): "
if /i not "%GO%"=="t" (
    echo.
    echo Przerwane. Nic nie zostalo dodane do gita ani wypchniete.
    echo Uruchom ponownie, gdy bedziesz gotowy.
    pause
    exit /b 0
)

echo.
echo Commituje tylko pliki z tej paczki...
git commit --only -m "raft-hud 1.0.0: aktualizacja moda [packwiz]" -- "modpack/index.toml" "modpack/pack.toml" "modpack/mods/%JAR%"
if errorlevel 1 (
    echo.
    echo [INFO] Nie bylo czego zapisac (JAR i indeks takie same jak w ostatnim commicie)
    echo        albo commit sie nie udal - szczegoly w komunikacie gita wyzej.
    echo        Nic nie zostalo wypchniete.
    pause
    exit /b 0
)

:: Kontrola: w commicie maja byc tylko index.toml, pack.toml i JAR.
set "EXPECTED= modpack/index.toml modpack/pack.toml modpack/mods/%JAR% "
set "FOREIGN="
git show --pretty=format: --name-only HEAD > "%TEMP%\vd_commit_files.txt"
for /f "usebackq delims=" %%F in ("%TEMP%\vd_commit_files.txt") do (
    echo %EXPECTED% | findstr /C:" %%F " >nul
    if errorlevel 1 (
        echo       obcy plik w commicie: %%F
        set "FOREIGN=1"
    )
)
del "%TEMP%\vd_commit_files.txt" >nul 2>&1
if defined FOREIGN (
    echo.
    echo [BLAD] Do commita trafilo cos, czego ten skrypt nie generuje (patrz wyzej).
    echo        Nic nie zostalo wypchniete. Cofnij commit i sprawdz repo:
    echo.
    echo            git reset --soft HEAD~1
    echo.
    pause
    exit /b 1
)
echo       OK - commit zawiera tylko: index.toml, pack.toml, %JAR%

echo.
if defined HASUP (
    echo Commity, ktore pojda na %REMOTE%/%BRANCH%:
    git log --oneline "@{u}..HEAD"
) else (
    echo Pierwszy push z tej galezi - ustawiam upstream na %REMOTE%/%BRANCH%.
)
echo.
git push -u %REMOTE% %BRANCH%
if errorlevel 1 (
    echo.
    echo [BLAD] git push nie powiodl sie - sprawdz remote i uprawnienia.
    pause
    exit /b 1
)

echo.
echo ============================================
echo   OPUBLIKOWANO
echo ============================================
echo.
echo Gracze dostana nowa wersje przy nastepnym starcie launchera.
pause
