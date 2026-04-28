param(
    [switch]$SkipBuild
)

$configPath = Join-Path $PSScriptRoot "local-env.ps1"
$examplePath = Join-Path $PSScriptRoot "local-env.example.ps1"

if (-not (Test-Path $configPath)) {
    Write-Host "Missing local config: $configPath" -ForegroundColor Yellow
    Write-Host "Copy this file first:" -ForegroundColor Yellow
    Write-Host "  $examplePath" -ForegroundColor Cyan
    exit 1
}

. $configPath

if (-not $env:SPRING_PROFILES_ACTIVE) {
    $env:SPRING_PROFILES_ACTIVE = "local"
}

$activeProfiles = $env:SPRING_PROFILES_ACTIVE.Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ }

$requiredVars = @(
    "JWT_SECRET"
)

if ($activeProfiles -contains "mysql") {
    $requiredVars += @(
        "MYSQL_HOST",
        "MYSQL_PORT",
        "MYSQL_DB",
        "MYSQL_USERNAME",
        "MYSQL_PASSWORD"
    )
}

foreach ($varName in $requiredVars) {
    $envItem = Get-Item "Env:$varName" -ErrorAction SilentlyContinue
    if (-not $envItem -or -not $envItem.Value) {
        Write-Host "Missing required environment variable: $varName" -ForegroundColor Red
        exit 1
    }
}

Write-Host "Starting backend with profile: $env:SPRING_PROFILES_ACTIVE" -ForegroundColor Green
if ($activeProfiles -contains "mysql") {
    Write-Host "MySQL target: $($env:MYSQL_HOST):$($env:MYSQL_PORT)/$($env:MYSQL_DB)" -ForegroundColor Green
} else {
    Write-Host "Using the default local H2 database on port 8080." -ForegroundColor Green
}
Write-Host "JWT secret is configured for local auth." -ForegroundColor Green

if ($env:MAIL_ENABLED -eq "true") {
    Write-Host "Mail is enabled for: $env:MAIL_USERNAME" -ForegroundColor Green
} else {
    Write-Host "Mail is disabled. Codes will appear only in backend logs." -ForegroundColor Yellow
}

Push-Location $PSScriptRoot
try {
    if (-not $SkipBuild) {
        mvn -q -DskipTests compile
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    }

    mvn spring-boot:run
} finally {
    Pop-Location
}
