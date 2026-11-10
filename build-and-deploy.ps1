# build-and-deploy.ps1
# Narzedzie CZESCIOWE (sam plugin). Cala publikacje — mod do graczy, plugin na serwer
# i paczka zasobow, bez pytania o potwierdzenie — robi: bash tools/publish-all.sh
# Usage: run in PowerShell as Administrator (if needed)

$project = "C:\Users\wikto\Desktop\villager-defense"
$serverPlugins = "C:\Users\wikto\Desktop\VD - serwer\plugins"

Push-Location $project

Write-Host "Starting build in $project"

# Use mvn from PATH; if you need to use a specific mvn executable, replace 'mvn' with full path
$mvn = "mvn"

Write-Host "Running: $mvn clean package"
& $mvn clean package 2>&1 | Tee-Object build.log
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed. See build.log in project directory for details." -ForegroundColor Red
    Pop-Location
    exit 1
}

# Determine JAR file. If pom.xml sets finalName, look for that; otherwise pick first jar in target
$expectedJar = Join-Path $project "target\ServerPlugin.jar"
if (Test-Path $expectedJar) {
    $jarPath = $expectedJar
} else {
    $found = Get-ChildItem -Path (Join-Path $project "target") -Filter *.jar -Recurse | Select-Object -First 1
    if ($found) {
        $jarPath = $found.FullName
    } else {
        Write-Host "No JAR found under target\. Build may have succeeded but produced no JAR." -ForegroundColor Yellow
        Pop-Location
        exit 1
    }
}

Write-Host "Found JAR: $jarPath"

# Ensure plugins folder exists
if (-not (Test-Path $serverPlugins)) {
    Write-Host "Server plugins folder not found: $serverPlugins" -ForegroundColor Red
    Pop-Location
    exit 1
}

$dest = Join-Path $serverPlugins (Split-Path $jarPath -Leaf)
$timestamp = (Get-Date).ToString("yyyyMMddHHmmss")
if (Test-Path $dest) {
    $backup = "$dest.bak.$timestamp"
    Copy-Item -Path $dest -Destination $backup -Force
    Write-Host "Backed up existing plugin to $backup"

    # Trzymaj tylko 2 najnowsze backupy (np. ServerPlugin.jar.bak.<yyyyMMddHHmmss>).
    # Sortowanie po nazwie = chronologiczne, bo znacznik czasu jest w nazwie.
    $backupFilter = "$(Split-Path $dest -Leaf).bak.*"
    $backups = Get-ChildItem -Path $serverPlugins -Filter $backupFilter -File |
        Sort-Object Name -Descending
    $toRemove = $backups | Select-Object -Skip 2
    foreach ($old in $toRemove) {
        Remove-Item -Path $old.FullName -Force
        Write-Host "Removed old backup: $($old.Name)"
    }
}

Copy-Item -Path $jarPath -Destination $dest -Force
Write-Host "Copied $jarPath -> $dest"

Pop-Location
Write-Host "Build and deploy finished. Restart server to load new plugin." -ForegroundColor Green

exit 0
