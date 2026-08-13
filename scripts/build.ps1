$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\dev-env.ps1"

$root = Split-Path -Parent $PSScriptRoot
& "$root\gradlew.bat" --no-daemon testDebugUnitTest assembleDebug @args
exit $LASTEXITCODE

