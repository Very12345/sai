$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\dev-env.ps1"

$properties = @"
idea.config.path=D:/Code/Android/StudioData/config
idea.system.path=D:/Code/Android/StudioData/system
idea.plugins.path=D:/Code/Android/StudioData/plugins
idea.log.path=D:/Code/Android/StudioData/log
"@
Set-Content -LiteralPath $env:IDEA_PROPERTIES -Value $properties -Encoding UTF8

$studio = 'D:\Code\Android Studio\bin\studio64.exe'
if (-not (Test-Path -LiteralPath $studio)) {
    throw "Android Studio is not installed at $studio"
}

Start-Process -FilePath $studio -ArgumentList (Split-Path -Parent $PSScriptRoot)

