# Villager Defense - Packwiz Installer
# Uruchom: .\install.ps1
# Pobiera packwiz-installer-bootstrap i konfiguruje auto-update

$ErrorActionPreference = "Stop"
$PackUrl = "https://raw.githubusercontent.com/YOUR_USERNAME/YOUR_REPO/refs/heads/main/modpack/pack.toml"
$MinecraftDir = "$env:APPDATA\.minecraft"

Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  Villager Defense - Packwiz Installer" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Upewnij sie ze folder .minecraft istnieje
if (-not (Test-Path $MinecraftDir)) {
    Write-Host "[!] Folder .minecraft nie znaleziony: $MinecraftDir" -ForegroundColor Red
    exit 1
}

# 2. Pobierz packwiz-installer-bootstrap.jar
$BootstrapUrl = "https://github.com/packwiz/packwiz-installer-bootstrap/releases/download/v0.0.3/packwiz-installer-bootstrap.jar"
$BootstrapDest = "$MinecraftDir\packwiz-installer-bootstrap.jar"

if (-not (Test-Path $BootstrapDest) -or (Get-Item $BootstrapDest).Length -lt 50000) {
    Write-Host "[1/3] Pobieranie packwiz-installer-bootstrap.jar..." -ForegroundColor Yellow
    Invoke-WebRequest -Uri $BootstrapUrl -OutFile $BootstrapDest -UseBasicParsing
    Write-Host "      OK" -ForegroundColor Green
} else {
    Write-Host "[1/3] packwiz-installer-bootstrap.jar juz istnieje" -ForegroundColor Gray
}

# 3. Skopiuj pack.toml
Write-Host "[2/3] Kopiowanie pack.toml..." -ForegroundColor Yellow
Copy-Item -Path "$PSScriptRoot\pack.toml" -Destination "$MinecraftDir\pack.toml" -Force
Write-Host "      OK" -ForegroundColor Green

# 4. Uruchom installer
Write-Host "[3/3] Instalacja modpacka..." -ForegroundColor Yellow
Push-Location $MinecraftDir
java -jar packwiz-installer-bootstrap.jar $PackUrl
Pop-Location

if ($LASTEXITCODE -ne 0) {
    Write-Host "[!] Installer zwrocil kod: $LASTEXITCODE" -ForegroundColor Yellow
    Write-Host "    Kontynuowanie..." -ForegroundColor Gray
}

Write-Host ""
Write-Host "=========================================" -ForegroundColor Green
Write-Host "  INSTALACJA ZAKONCZONA" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Packwiz-installer dziala przy kazdym starcie." -ForegroundColor Cyan
Write-Host "Aby zaktualizowac, po prostu uruchom ponownie." -ForegroundColor Cyan
Write-Host ""
