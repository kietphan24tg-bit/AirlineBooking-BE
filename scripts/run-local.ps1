$envFile = Join-Path $PSScriptRoot "..\.env"

if (-not (Test-Path $envFile)) {
    Write-Error ".env file not found at $envFile"
    exit 1
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

Write-Host "Loaded environment variables from .env"
& "$PSScriptRoot\..\gradlew.bat" :app-bootstrap:bootRun
