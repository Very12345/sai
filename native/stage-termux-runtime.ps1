param(
    [string]$CacheRoot = 'D:\Code\TermuxRuntimeCache'
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$outputRoot = Join-Path $repositoryRoot 'core\runtime\src\main\jniLibs'
$baseUrl = 'https://packages.termux.dev/apt/termux-main/pool/main'

$packages = @(
    @{ Name = 'proot'; Version = '5.1.107.89'; Pool = 'p/proot'; Hashes = @{
        aarch64 = 'ec9fe38c50cfd49dd31fe360ffbcc3124a945dc1ea16293a8a769303dd724f46'
        x86_64 = '0d76da0515f38dfb2217f647b0d79fcd61b38f80e25cbf2d39237697b02dd016'
    } },
    @{ Name = 'libtalloc'; Version = '2.4.3'; Pool = 'libt/libtalloc'; Hashes = @{
        aarch64 = 'ac81ad623d74c209718b9f3acb2dd702cc8a88c431e820d212229910b4db29da'
        x86_64 = '7ca2eaae2e53b28228a01301bc410b62845403d6317c25b8e0a7f40681de0628'
    } },
    @{ Name = 'libandroid-shmem'; Version = '0.7'; Pool = 'liba/libandroid-shmem'; Hashes = @{
        aarch64 = '0da3a24d558b93c92bcf8d611e0826a99ff96e396b148e6cdf33b47c47c57ff6'
        x86_64 = 'ffa9e4c87467b158b148d0ff92dda796aa038276c2075af3269cdcdb06f25797'
    } }
)

function Get-CheckedPackage([hashtable]$Package, [string]$Architecture) {
    $fileName = "$($Package.Name)_$($Package.Version)_$Architecture.deb"
    $path = Join-Path $CacheRoot $fileName
    if (-not (Test-Path -LiteralPath $path)) {
        $url = "$baseUrl/$($Package.Pool)/$fileName"
        & curl.exe -L --fail --retry 3 --output $path $url
        if ($LASTEXITCODE -ne 0) { throw "Unable to download $url" }
    }
    $actual = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    $expected = $Package.Hashes[$Architecture]
    if ($actual -ne $expected) { throw "SHA-256 mismatch for $fileName" }
    return $path
}

function Expand-Deb([string]$Deb, [string]$Destination, [string[]]$Entries) {
    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    & tar.exe -xf $Deb -C $Destination
    if ($LASTEXITCODE -ne 0) { throw "Unable to unpack $Deb" }
    $dataArchive = Get-ChildItem -LiteralPath $Destination -Filter 'data.tar.*' | Select-Object -First 1
    if ($null -eq $dataArchive) { throw "Missing data archive in $Deb" }
    & tar.exe -xf $dataArchive.FullName -C $Destination @Entries
    if ($LASTEXITCODE -ne 0) { throw "Unable to extract required files from $Deb" }
}

function Replace-Ascii([string]$Path, [string]$Old, [string]$New) {
    if ($New.Length -gt $Old.Length) { throw 'Replacement must not grow the ELF string table' }
    $bytes = [IO.File]::ReadAllBytes($Path)
    $oldBytes = [Text.Encoding]::ASCII.GetBytes($Old)
    $newBytes = [Text.Encoding]::ASCII.GetBytes($New)
    $matches = 0
    for ($index = 0; $index -le $bytes.Length - $oldBytes.Length; $index++) {
        $equal = $true
        for ($offset = 0; $offset -lt $oldBytes.Length; $offset++) {
            if ($bytes[$index + $offset] -ne $oldBytes[$offset]) { $equal = $false; break }
        }
        if (-not $equal) { continue }
        [Array]::Copy($newBytes, 0, $bytes, $index, $newBytes.Length)
        for ($offset = $newBytes.Length; $offset -lt $oldBytes.Length; $offset++) { $bytes[$index + $offset] = 0 }
        $matches++
        $index += $oldBytes.Length - 1
    }
    if ($matches -lt 1) { throw "ELF string '$Old' was not found in $Path" }
    [IO.File]::WriteAllBytes($Path, $bytes)
}

New-Item -ItemType Directory -Force -Path $CacheRoot | Out-Null
foreach ($architecture in @('aarch64', 'x86_64')) {
    $abi = if ($architecture -eq 'aarch64') { 'arm64-v8a' } else { 'x86_64' }
    $stage = Join-Path $CacheRoot "stage-$architecture"
    $abiOutput = Join-Path $outputRoot $abi
    New-Item -ItemType Directory -Force -Path $abiOutput | Out-Null

    foreach ($package in $packages) {
        $deb = Get-CheckedPackage $package $architecture
        $prefix = './data/data/com.termux/files/usr'
        $entries = switch ($package.Name) {
            'proot' { @("$prefix/bin/proot", "$prefix/libexec/proot/loader") }
            'libtalloc' { @("$prefix/lib/libtalloc.so.2.4.3") }
            'libandroid-shmem' { @("$prefix/lib/libandroid-shmem.so") }
        }
        Expand-Deb $deb (Join-Path $stage $package.Name) $entries
    }

    $prefix = "data\data\com.termux\files\usr"
    $proot = Join-Path $stage "proot\$prefix\bin\proot"
    $loader = Join-Path $stage "proot\$prefix\libexec\proot\loader"
    $talloc = Join-Path $stage "libtalloc\$prefix\lib\libtalloc.so.2.4.3"
    $shmem = Join-Path $stage "libandroid-shmem\$prefix\lib\libandroid-shmem.so"
    foreach ($required in @($proot, $loader, $talloc, $shmem)) {
        if (-not (Test-Path -LiteralPath $required)) { throw "Missing runtime file: $required" }
    }

    $prootOutput = Join-Path $abiOutput 'libproot.so'
    Copy-Item -LiteralPath $proot -Destination $prootOutput -Force
    Replace-Ascii $prootOutput 'libtalloc.so.2' 'libtalloc.so'
    Copy-Item -LiteralPath $loader -Destination (Join-Path $abiOutput 'libproot-loader.so') -Force
    Copy-Item -LiteralPath $talloc -Destination (Join-Path $abiOutput 'libtalloc.so') -Force
    Copy-Item -LiteralPath $shmem -Destination (Join-Path $abiOutput 'libandroid-shmem.so') -Force
}

Get-ChildItem -LiteralPath $outputRoot -Recurse -File | ForEach-Object {
    "$($_.FullName) $((Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant())"
}
