# Build debug + release APKs and copy them into outputs\.
# Usage: powershell -ExecutionPolicy Bypass -File tools\build.ps1
# (ASCII only on purpose: Windows PowerShell 5.1 reads .ps1 as ANSI.)
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = 'D:\Programs\jdk-11'
$env:ANDROID_HOME = 'D:\Programs\android-sdk'
$env:GRADLE_USER_HOME = 'D:\Programs\.gradle-home'

Push-Location $projectRoot
try {
    & .\gradlew.bat :app:assembleDebug :app:assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Build failed with exit code $LASTEXITCODE" }

    $outputs = Join-Path $projectRoot 'outputs'
    New-Item -ItemType Directory -Force -Path $outputs | Out-Null
    Copy-Item "$projectRoot\app\build\outputs\apk\debug\app-debug.apk" `
        "$outputs\codexdone-debug.apk" -Force
    Copy-Item "$projectRoot\app\build\outputs\apk\release\app-release.apk" `
        "$outputs\codexdone-release.apk" -Force
    Write-Host "APKs copied to $outputs"
}
finally {
    Pop-Location
}
