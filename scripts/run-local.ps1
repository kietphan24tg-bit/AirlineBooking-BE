$envFile = Join-Path $PSScriptRoot "..\.env"
$gradleUserHome = Join-Path $PSScriptRoot "..\.gradle-user"

if (-not (Test-Path $envFile)) {
    Write-Error ".env file not found at $envFile"
    exit 1
}

if (-not (Test-Path $gradleUserHome)) {
    New-Item -ItemType Directory -Path $gradleUserHome | Out-Null
}

Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -match '^\s*$') {
        return
    }

    $name, $value = $_ -split '=', 2
    if (-not $name) {
        return
    }

    [System.Environment]::SetEnvironmentVariable($name.Trim(), $value, 'Process')
}

 [System.Environment]::SetEnvironmentVariable('GRADLE_USER_HOME', $gradleUserHome, 'Process')

Write-Host "Loaded environment variables from .env"
Write-Host "Using GRADLE_USER_HOME=$gradleUserHome"
& "$PSScriptRoot\..\gradlew.bat" :app-bootstrap:bootRun
