param(
    [string]$CacheRoot = 'D:\Code\sai-dsh-runtime',
    [string]$NodeVersion = '24.19.0',
    [string]$DshVersion = '0.1.0-rc.6',
    [int]$RuntimeRevision = 49,
    [string]$DshForkRoot = 'D:\Code\deepseek-harness',
    [switch]$ReuseInstalledStages
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$assetRoot = Join-Path $repoRoot 'core\dsh\src\main\assets\dsh-runtime'
$pluginRoot = Join-Path $repoRoot 'dsh-plugins'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
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

function Assert-ElfMachine([string]$Path, [int]$ExpectedMachine) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "Missing ELF binary: $Path" }
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    if ($bytes.Length -lt 20 -or $bytes[0] -ne 0x7f -or $bytes[1] -ne 0x45 -or
        $bytes[2] -ne 0x4c -or $bytes[3] -ne 0x46) { throw "Not an ELF binary: $Path" }
    $machine = [int]$bytes[18] -bor ([int]$bytes[19] -shl 8)
    if ($machine -ne $ExpectedMachine) { throw "ELF machine $machine does not match $ExpectedMachine`: $Path" }
}

function Install-SaiMobileWebUi([string]$AppRoot, [string]$ForkRoot) {
    # sai deliberately carries a mobile-first DSH Web UI fork. These files
    # are compiled from the audited source checkout and replace only browser
    # packages in the otherwise pinned runtime closure.
    $overrides = @(
        @{ Source = 'packages\client\ui-layout\lib\client.js'; Target = 'node_modules\@deepseek-ai\dsh-client-ui-layout\lib\client.js' },
        @{ Source = 'packages\client\ui-conversation\lib\client.js'; Target = 'node_modules\@deepseek-ai\dsh-client-ui-conversation\lib\client.js' },
        @{ Source = 'packages\client\ui-model-selection\lib\client.js'; Target = 'node_modules\@deepseek-ai\dsh-client-ui-model-selection\lib\client.js' },
        @{ Source = 'packages\client\ui-primitives\lib\index.js'; Target = 'node_modules\@deepseek-ai\dsh-client-ui-primitives\lib\index.js' },
        @{ Source = 'packages\client\ui-settings-general\lib\client.js'; Target = 'node_modules\@deepseek-ai\dsh-client-ui-settings-general\lib\client.js' },
        @{ Source = 'packages\extensions\ui-cordis\lib\client.js'; Target = 'node_modules\@deepseek-ai\dsh-client-ui-cordis\lib\client.js' }
    )
    foreach ($override in $overrides) {
        $source = Join-Path $ForkRoot $override.Source
        $target = Join-Path $AppRoot $override.Target
        if (-not (Test-Path -LiteralPath $source)) { throw "Missing built sai Web UI override: $source" }
        if (-not (Test-Path -LiteralPath $target)) { throw "Missing DSH runtime package target: $target" }
        Copy-Item -Force -LiteralPath $source -Destination $target
    }

    # The published DSH web package is a Vite bundle. Replacing package-level
    # client.js files alone cannot affect that already-bundled application, so
    # ship the dist produced from our audited mobile fork as well.
    $webDistSource = Join-Path $ForkRoot 'apps\web\dist'
    $webDistTarget = Join-Path $AppRoot 'node_modules\@deepseek-ai\dsh-web-frontend\dist'
    if (-not (Test-Path -LiteralPath (Join-Path $webDistSource 'index.html'))) {
        throw "Missing built sai Web UI dist: $webDistSource (run pnpm run build:web first)"
    }
    if (Test-Path -LiteralPath $webDistTarget) {
        Remove-Item -LiteralPath $webDistTarget -Recurse -Force
    }
    Copy-Item -LiteralPath $webDistSource -Destination $webDistTarget -Recurse -Force
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
    $packageJson = @"
{
  "name": "sai-dsh-runtime",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "dependencies": {
    "@img/sharp-libvips-linux-$($target.NpmCpu)": "1.3.2",
    "@img/sharp-linux-$($target.NpmCpu)": "0.35.3",
    "@lydell/node-pty-linux-$($target.NpmCpu)": "1.1.0",
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
    "@sai/dsh-vision": "file:vendor/vision",
    "@sai/dsh-voice": "file:vendor/voice"
  }
}
"@
    [System.IO.File]::WriteAllText((Join-Path $app 'package.json'), $packageJson, $utf8NoBom)
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
    $saiPatch = @'
- id: credentials
  name: '@deepseek-ai/dsh-credentials-local'
  disabled: true

- id: sandbox-policy
  name: '@deepseek-ai/dsh-sandbox-policy'
  config:
    mode: workspace-write
    workspaceRoot: !!js process.cwd()

- id: permission
  name: '@deepseek-ai/dsh-permission-presets'
  config:
    defaultPreset: workspace-write
    presets:
      read-only: { sandbox: read-only, approval: ask }
      workspace-write: { sandbox: workspace-write, approval: ask }
      danger-full-access: { sandbox: danger-full-access, approval: never }

- insert:
    - { id: sai-credentials, name: '@sai/dsh-credentials' }
    - { id: sai-android, name: '@sai/dsh-android' }
    - { id: sai-models, name: '@sai/dsh-models' }
    - { id: sai-request-guard, name: '@sai/dsh-request-guard', config: { maxConcurrentPerProvider: 2 } }
    - { id: sai-vision, name: '@sai/dsh-vision', inject: [llm] }
    - { id: sai-voice, name: '@sai/dsh-voice', inject: [tools, systemPrompt, saiAndroid], config: { promptOnly: false } }
    - { id: sai-artifacts, name: '@sai/dsh-artifacts', inject: [tools, systemPrompt, saiAndroid] }
    - { id: sai-github, name: '@sai/dsh-github', inject: [saiAndroid] }
    - { id: sai-legacy-import, name: '@sai/dsh-legacy-import', inject: [sessions] }
    - { id: sai-market, name: '@sai/dsh-market' }
    - { id: sai-pet, name: '@sai/dsh-pet', inject: [saiAndroid, sessions] }
    - { id: sai-ui, name: '@sai/dsh-ui' }
'@
    [System.IO.File]::WriteAllText((Join-Path $app 'sai.cordis.patch.yml'), $saiPatch, $utf8NoBom)
    Copy-Item -Force -LiteralPath (Join-Path $PSScriptRoot 'sai-dsh-launcher.mjs') -Destination (Join-Path $app 'sai-dsh-launcher.mjs')

    $oldOs = $env:npm_config_os; $oldCpu = $env:npm_config_cpu; $oldLibc = $env:npm_config_libc
    try {
        $env:npm_config_os = 'linux'
        $env:npm_config_cpu = $target.NpmCpu
        $env:npm_config_libc = 'glibc'
        # DSH depends on sharp and node-pty. Linux prebuilds are not present when npm
        # assembles the closure on Windows, so the platform packages above are explicit.
        Invoke-Checked 'npm.cmd' @('install','--omit=dev','--include=optional','--ignore-scripts','--no-audit','--no-fund','--package-lock=true','--force') $app
    } finally {
        $env:npm_config_os = $oldOs; $env:npm_config_cpu = $oldCpu; $env:npm_config_libc = $oldLibc
    }

    # Voice mode uses a real agent preset so its mandatory speak policy stays
    # in the model's system prompt and never appears as a user-visible chat
    # message. It inherits the audited standard tool composition.
    $presetRoot = Join-Path $app 'node_modules\@deepseek-ai\dsh\config\agent-presets'
    $standardPreset = Join-Path $presetRoot 'standard'
    $voicePreset = Join-Path $presetRoot 'sai-voice'
    if (-not (Test-Path -LiteralPath (Join-Path $standardPreset 'agent.cordis.yml'))) {
        throw "Missing standard DSH agent preset: $standardPreset"
    }
    if (Test-Path -LiteralPath $voicePreset) { Remove-Item -LiteralPath $voicePreset -Recurse -Force }
    Copy-Item -LiteralPath $standardPreset -Destination $voicePreset -Recurse -Force
    [System.IO.File]::WriteAllText((Join-Path $voicePreset 'preset.yml'), "name: sai Voice`ndescription: Continuous offline voice conversation with mandatory speak tool output.`n", $utf8NoBom)

    Install-SaiMobileWebUi $app $DshForkRoot

    $ptyPackage = Join-Path $app "node_modules\@lydell\node-pty-linux-$($target.NpmCpu)\pty.node"
    $ptyPrebuildRoot = Join-Path $app 'node_modules\node-pty\prebuilds'
    if (Test-Path $ptyPrebuildRoot) { Remove-Item -LiteralPath $ptyPrebuildRoot -Recurse -Force }
    $ptyTargetRoot = Join-Path $ptyPrebuildRoot "linux-$($target.NpmCpu)"
    New-Item -ItemType Directory -Force -Path $ptyTargetRoot | Out-Null
    Copy-Item -Force -LiteralPath $ptyPackage -Destination (Join-Path $ptyTargetRoot 'pty.node')
    $expectedMachine = if ($target.NpmCpu -eq 'arm64') { 183 } else { 62 }
    Assert-ElfMachine (Join-Path $ptyTargetRoot 'pty.node') $expectedMachine
    $sharpNode = Get-ChildItem (Join-Path $app "node_modules\@img\sharp-linux-$($target.NpmCpu)\lib") -Filter '*.node' | Select-Object -First 1
    if ($null -eq $sharpNode) { throw "Missing sharp native addon for $($target.NpmCpu)" }
    Assert-ElfMachine $sharpNode.FullName $expectedMachine
    $libvips = Get-ChildItem (Join-Path $app "node_modules\@img\sharp-libvips-linux-$($target.NpmCpu)\lib") -Filter 'libvips-cpp.so.*' | Select-Object -First 1
    if ($null -eq $libvips) { throw "Missing libvips for $($target.NpmCpu)" }
    Assert-ElfMachine $libvips.FullName $expectedMachine
    $lockHashes[$abi] = (Get-FileHash -Algorithm SHA256 (Join-Path $app 'package-lock.json')).Hash.ToLowerInvariant()

    $archiveName = "sai-dsh-$DshVersion-node-$NodeVersion-$abi.tar.xz"
    $archive = Join-Path $assetRoot $archiveName
    if (Test-Path $archive) { Remove-Item -LiteralPath $archive -Force }
    # A cancelled local build may leave Windows tar holding its previous
    # output briefly. Per-process names make retries independent and keep all
    # large intermediates under D:\Code.
    $temporaryTar = Join-Path $CacheRoot "$archiveName.$PID.tmp.tar"
    $temporaryXz = "$temporaryTar.xz"
    if (Test-Path $temporaryTar) { Remove-Item -LiteralPath $temporaryTar -Force }
    if (Test-Path $temporaryXz) { Remove-Item -LiteralPath $temporaryXz -Force }
    tar -cf $temporaryTar -C $stage node app
    if ($LASTEXITCODE -ne 0) { throw 'Failed to create uncompressed DSH runtime archive' }
    xz -T0 -6 -f $temporaryTar
    if ($LASTEXITCODE -ne 0) { throw 'Failed to compress DSH runtime archive' }
    Move-Item -Force -LiteralPath $temporaryXz -Destination $archive
    $hash = (Get-FileHash -Algorithm SHA256 $archive).Hash.ToLowerInvariant()
    $closures[$abi] = [ordered]@{
        asset = "dsh-runtime/$archiveName"
        sha256 = $hash
        bytes = (Get-Item $archive).Length
    }
}

$sourceCommit = (& git -C $DshForkRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $sourceCommit -notmatch '^[0-9a-f]{40}$') {
    throw "Cannot resolve audited DSH fork commit from $DshForkRoot"
}

$manifest = [ordered]@{
    schemaVersion = 1
    runtimeVersion = "sai-dsh-$DshVersion-node-$NodeVersion-r$RuntimeRevision"
    dshVersion = $DshVersion
    nodeVersion = $NodeVersion
    sourceCommit = $sourceCommit
    sourceRepository = 'https://github.com/Very12345/deepseek-harness'
    webUiVariant = "sai-mobile-r$RuntimeRevision"
    packageLockSha256 = $lockHashes
    port = 3080
    archives = $closures
}
[System.IO.File]::WriteAllText(
    (Join-Path $assetRoot 'manifest.json'),
    ($manifest | ConvertTo-Json -Depth 8),
    $utf8NoBom
)
Write-Output "Prepared offline DSH runtime under $assetRoot; cache remains in $CacheRoot"
