# build-and-deploy.ps1
# Usage: run in PowerShell as Administrator (if needed)

$project = "C:\Users\wikto\Desktop\villager-defense"
$serverPlugins = "C:\Users\wikto\Desktop\mc-server\plugins"

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
$expectedJar = Join-Path $project "target\VillagerDefense.jar"
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
}

Copy-Item -Path $jarPath -Destination $dest -Force
Write-Host "Copied $jarPath -> $dest"

Pop-Location
Write-Host "Build and deploy finished. Restart server to load new plugin." -ForegroundColor Green

exit 0
