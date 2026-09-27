# Run unit tests (HTTP request parsing, token check, text trimming).
# Usage: powershell -ExecutionPolicy Bypass -File tools\run-tests.ps1
#
# Why the junction: this project lives under a non-ASCII path
# (F:\CodeX\01-开发项目\...), and Gradle's test worker cannot load test classes from
# such a classpath on this machine (ClassNotFoundException). Running the same
# build through an ASCII junction works, so the script creates one, runs the
# tests, then removes it.
# (ASCII only on purpose: Windows PowerShell 5.1 reads .ps1 as ANSI.)
$ErrorActionPreference = 'Stop'

$projectRoot = (Split-Path -Parent $PSScriptRoot).TrimEnd('\')
$link = 'F:\CodeX\04-Codex工作区\codexdone-testrun'

$env:JAVA_HOME = 'D:\Programs\jdk-11'
$env:ANDROID_HOME = 'D:\Programs\android-sdk'
$env:GRADLE_USER_HOME = 'D:\Programs\.gradle-home'

if (Test-Path $link) { (Get-Item $link).Delete() }
New-Item -ItemType Junction -Path $link -Target $projectRoot | Out-Null

Push-Location $link
try {
    & .\gradlew.bat :app:testDebugUnitTest -PcxdTestRun --console=plain
    $code = $LASTEXITCODE
}
finally {
    Pop-Location
    if (Test-Path $link) { (Get-Item $link).Delete() }
}

if ($code -ne 0) { throw "Tests failed with exit code $code" }
Write-Host "Tests passed. Report: $projectRoot\app\build-testrun\reports\tests\testDebugUnitTest\index.html"
