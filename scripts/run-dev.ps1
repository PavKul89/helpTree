# Starts helpTree locally against the docker-compose infrastructure.
#
# The application has no development fallback secrets: every required variable
# must come from the environment or from .env. This script loads .env, checks
# the required keys, and then runs the Spring Boot Maven plugin with the `dev`
# profile.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts/run-dev.ps1
#   powershell -ExecutionPolicy Bypass -File scripts/run-dev.ps1 -EnvFile .env.local
#   powershell -ExecutionPolicy Bypass -File scripts/run-dev.ps1 -- -Dspring-boot.run.jvmArguments="-Xmx1g"

[CmdletBinding()]
param(
    [string]$EnvFile = '',
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ExtraArgs = @()
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path $repoRoot '.env'
}

function Import-DotEnv {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith('#')) {
            return
        }

        $separator = $line.IndexOf('=')
        if ($separator -lt 1) {
            return
        }

        $name = $line.Substring(0, $separator).Trim()
        $value = $line.Substring($separator + 1).Trim()

        if ($value.Length -ge 2) {
            $quotedWithDouble = $value.StartsWith('"') -and $value.EndsWith('"')
            $quotedWithSingle = $value.StartsWith("'") -and $value.EndsWith("'")
            if ($quotedWithDouble -or $quotedWithSingle) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }

        Set-Item -Path ("Env:" + $name) -Value $value
    }
}

function Get-EnvironmentValue {
    param([string]$Name)

    $item = Get-Item -Path ("Env:" + $Name) -ErrorAction SilentlyContinue
    if ($null -eq $item) {
        return ''
    }
    return [string]$item.Value
}

Import-DotEnv -Path $EnvFile

$required = @(
    'DB_URL',
    'DB_USER',
    'DB_PASS',
    'JWT_SECRET',
    'SILO_ENDPOINT',
    'SILO_PUBLIC_ENDPOINT',
    'SILO_ACCESS_KEY',
    'SILO_SECRET_KEY'
)

$missing = @()
foreach ($name in $required) {
    if ([string]::IsNullOrWhiteSpace((Get-EnvironmentValue -Name $name))) {
        $missing += $name
    }
}

if ($missing.Count -gt 0) {
    Write-Host 'Missing required environment variables:' -ForegroundColor Red
    $missing | ForEach-Object { Write-Host ("  - " + $_) -ForegroundColor Red }
    Write-Host ''
    Write-Host "Create .env from .env.example and fill in the values:" -ForegroundColor Yellow
    Write-Host "  Copy-Item .env.example .env"
    Write-Host ''
    Write-Host "The application deliberately has no built-in development secrets."
    exit 1
}

$placeholders = @()
foreach ($name in $required) {
    if ((Get-EnvironmentValue -Name $name) -match 'REPLACE_WITH_') {
        $placeholders += $name
    }
}
if ($placeholders.Count -gt 0) {
    Write-Host 'These variables still contain template placeholders:' -ForegroundColor Yellow
    $placeholders | ForEach-Object { Write-Host ("  - " + $_) -ForegroundColor Yellow }
    Write-Host 'Local startup may work, but replace them before sharing the environment.'
    Write-Host ''
}

if ([string]::IsNullOrWhiteSpace((Get-EnvironmentValue -Name 'SPRING_PROFILES_ACTIVE'))) {
    Set-Item -Path 'Env:SPRING_PROFILES_ACTIVE' -Value 'dev'
}

Write-Host ("Starting helpTree with profile '{0}'..." -f (Get-EnvironmentValue -Name 'SPRING_PROFILES_ACTIVE')) -ForegroundColor Green

Push-Location $repoRoot
try {
    & (Join-Path $repoRoot 'mvnw.cmd') -B -pl helpTree-service spring-boot:run @ExtraArgs
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
