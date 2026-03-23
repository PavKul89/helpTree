# Build and Run Script for helpTree
# Usage: .\build-and-run.ps1

$ErrorActionPreference = "Stop"

Write-Host "=== Stopping Java processes ===" -ForegroundColor Yellow
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2

Write-Host "=== Building project ===" -ForegroundColor Yellow
cd $PSScriptRoot
.\mvnw.cmd clean package -DskipTests -q

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed!" -ForegroundColor Red
    exit 1
}

Write-Host "=== Starting services ===" -ForegroundColor Yellow
Start-Process java -ArgumentList '-jar', "$PSScriptRoot\helpTree-service\target\helpTree-service-0.0.1-SNAPSHOT.jar" -NoNewWindow
Start-Process java -ArgumentList '-jar', "$PSScriptRoot\rating-service\target\rating-service-0.0.1-SNAPSHOT.jar" -NoNewWindow

Write-Host "Services starting..." -ForegroundColor Green
Start-Sleep -Seconds 15
Write-Host "Done!" -ForegroundColor Green
