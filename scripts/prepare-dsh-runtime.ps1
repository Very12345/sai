param(
    [string]$CacheRoot = 'D:\Code\sai-dsh-runtime',
    [string]$NodeVersion = '24.19.0',
    [string]$DshVersion = '0.1.0-rc.6',
    [int]$RuntimeRevision = 3,
    [switch]$ReuseInstalledStages
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$assetRoot = Join-Path $repoRoot 'core\dsh\src\main\assets\dsh-runtime'
$pluginRoot = Join-Path $repoRoot 'dsh-plugins'
New-Item -ItemType Directory -Force -Path $CacheRoot, $assetRoot | Out-Null

function Invoke-Checked([string]$FilePath, [string[]]$Arguments, [string]$WorkingDirectory) {
    $process = Start-Process -FilePath $FilePath -ArgumentList $Arguments -WorkingDirectory $WorkingDirectory -NoNewWindow -Wait -PassThru
    if ($process.ExitCode -ne 0) { throw "$FilePath exited with $($process.ExitCode)" }
}

function Get-File([string]$Url, [string]$Destination) {
    if (Test-Path $Destination) { return }
    $partial = "$Destination.partial"
    Invoke-WebRequest -Uri $Url -OutFile $partial -UseBasicParsing
    Move-Item -Force -LiteralPath $partial -Destination $Destination
}

$closures = @{}
$lockHashes = @{}
foreach ($target in @(
    @{ Abi = 'arm64-v8a'; NodeArch = 'arm64'; NpmCpu = 'arm64' },
    @{ Abi = 'x86_64'; NodeArch = 'x64'; NpmCpu = 'x64' }
)) {
    $abi = $target.Abi
    $stage = Join-Path $CacheRoot "stage-$abi"
    $download = Join-Path $CacheRoot "node-v$NodeVersion-linux-$($target.NodeArch).tar.xz"
    Get-File "https://nodejs.org/dist/v$NodeVersion/node-v$NodeVersion-linux-$($target.NodeArch).tar.xz" $download

    $reuse = $ReuseInstalledStages -and (Test-Path (Join-Path $stage 'app\node_modules\@deepseek-ai\dsh\lib\bin.js'))
    if (-not $reuse) {
        if (Test-Path $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
        New-Item -ItemType Directory -Force -Path (Join-Path $stage 'node'), (Join-Path $stage 'app') | Out-Null
        & python (Join-Path $PSScriptRoot 'extract-portable-tar.py') $download (Join-Path $stage 'node') --strip-components 1
        if ($LASTEXITCODE -ne 0) { throw 'Failed to extract Node.js' }
    }
    $app = Join-Path $stage 'app'
    @"
{
  "name": "sai-dsh-runtime",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "dependencies": {
    "@deepseek-ai/dsh": "$DshVersion",
    "@sai/dsh-android": "file:vendor/android",
    "@sai/dsh-artifacts": "file:vendor/artifacts",
    "@sai/dsh-credentials": "file:vendor/credentials",
    "@sai/dsh-github": "file:vendor/github",
    "@sai/dsh-legacy-import": "file:vendor/legacy-import",
    "@sai/dsh-market": "file:vendor/market",
    "@sai/dsh-models": "file:vendor/models",
    "@sai/dsh-pet": "file:vendor/pet",
    "@sai/dsh-request-guard": "file:vendor/request-guard",
    "@sai/dsh-ui": "file:vendor/ui",
    "@sai/dsh-voice": "file:vendor/voice"
  }
}
"@ | Set-Content -Encoding utf8 (Join-Path $app 'package.json')
    $vendor = Join-Path $app 'vendor'
    New-Item -ItemType Directory -Force -Path $vendor | Out-Null
    Get-ChildItem (Join-Path $pluginRoot 'packages') -Directory | ForEach-Object {
        $vendorTarget = Join-Path $vendor $_.Name
        if (Test-Path $vendorTarget) { Remove-Item -LiteralPath $vendorTarget -Recurse -Force }
        Copy-Item -Recurse -Force -LiteralPath $_.FullName -Destination $vendorTarget
        if ($reuse) {
            $installedTarget = Join-Path $app "node_modules\@sai\dsh-$($_.Name)"
            if (Test-Path $installedTarget) { Remove-Item -LiteralPath $installedTarget -Recurse -Force }
            Copy-Item -Recurse -Force -LiteralPath $_.FullName -Destination $installedTarget
        }
    }
@'
- id: credentials
  name: '@sai/dsh-credentials'
  inject: [saiAndroid]

- insert:
    - { id: sai-android, name: '@sai/dsh-android' }
    - { id: sai-models, name: '@sai/dsh-models' }
    - { id: sai-request-guard, name: '@sai/dsh-request-guard', config: { maxConcurrentPerProvider: 2 } }
    - { id: sai-voice, name: '@sai/dsh-voice', inject: [saiAndroid], config: { promptOnly: false } }
    - { id: sai-artifacts, name: '@sai/dsh-artifacts', inject: [saiAndroid] }
    - { id: sai-github, name: '@sai/dsh-github', inject: [saiAndroid] }
    - { id: sai-legacy-import, name: '@sai/dsh-legacy-import', inject: [sessions] }
    - { id: sai-market, name: '@sai/dsh-market' }
    - { id: sai-pet, name: '@sai/dsh-pet', inject: [saiAndroid, sessions] }
    - { id: sai-ui, name: '@sai/dsh-ui' }
'@ | Set-Content -Encoding utf8 (Join-Path $app 'sai.cordis.patch.yml')
    Copy-Item -Force -LiteralPath (Join-Path $PSScriptRoot 'sai-dsh-launcher.mjs') -Destination (Join-Path $app 'sai-dsh-launcher.mjs')

    $oldOs = $env:npm_config_os; $oldCpu = $env:npm_config_cpu
    try {
        $env:npm_config_os = 'linux'
        $env:npm_config_cpu = $target.NpmCpu
        if ($reuse) {
            # File dependencies may have changed even though the expensive DSH closure is reused.
            # Refresh the lock so it remains an accurate, auditable description of the archive.
            Invoke-Checked 'npm.cmd' @('install','--package-lock-only','--ignore-scripts','--no-audit','--no-fund') $app
        } else {
            Invoke-Checked 'npm.cmd' @('install','--omit=dev','--ignore-scripts','--no-audit','--no-fund','--package-lock=true') $app
        }
    } finally {
        $env:npm_config_os = $oldOs; $env:npm_config_cpu = $oldCpu
    }
    $lockHashes[$abi] = (Get-FileHash -Algorithm SHA256 (Join-Path $app 'package-lock.json')).Hash.ToLowerInvariant()

    $archiveName = "sai-dsh-$DshVersion-node-$NodeVersion-$abi.tar.xz"
    $archive = Join-Path $assetRoot $archiveName
    if (Test-Path $archive) { Remove-Item -LiteralPath $archive -Force }
    tar -cJf $archive -C $stage node app
    if ($LASTEXITCODE -ne 0) { throw 'Failed to create DSH runtime archive' }
    $hash = (Get-FileHash -Algorithm SHA256 $archive).Hash.ToLowerInvariant()
    $closures[$abi] = [ordered]@{
        asset = "dsh-runtime/$archiveName"
        sha256 = $hash
        bytes = (Get-Item $archive).Length
    }
}

$manifest = [ordered]@{
    schemaVersion = 1
    runtimeVersion = "sai-dsh-$DshVersion-node-$NodeVersion-r$RuntimeRevision"
    dshVersion = $DshVersion
    nodeVersion = $NodeVersion
    sourceCommit = '47f943859bef60e4160492346772ded9b24f765a'
    packageLockSha256 = $lockHashes
    port = 3080
    archives = $closures
}
$manifest | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 (Join-Path $assetRoot 'manifest.json')
Write-Output "Prepared offline DSH runtime under $assetRoot; cache remains in $CacheRoot"
