param(
    [ValidateSet('windows-x86_64', 'windows-arm64')]
    [string]$Target = 'windows-x86_64',
    [string]$Repository = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$repository = (Resolve-Path -LiteralPath $Repository).Path
$nativeRoot = [IO.Path]::GetFullPath((Join-Path $repository 'native'))
function Assert-PreparedPath([string]$Path) {
    $resolved = [IO.Path]::GetFullPath($Path)
    if (-not $resolved.StartsWith($nativeRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw "Refusing cleanup outside native directory: $resolved" }
}
function Remove-PreparedPath([string]$Path) {
    Assert-PreparedPath $Path
    $resolved = [IO.Path]::GetFullPath($Path)
    if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
}
$manifestPath = Join-Path $repository 'native\dependencies.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$dependencies = $manifest.$Target
$downloads = Join-Path $repository 'native\downloads'
$bundle = Join-Path $repository "native\$Target\native"
$staging = Join-Path (Split-Path -Parent $bundle) ('.native-preparing-' + [guid]::NewGuid().ToString('N'))
$backup = Join-Path (Split-Path -Parent $bundle) ('.native-backup-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $downloads, (Join-Path $staging 'vlc'), (Join-Path $staging 'bin'), (Join-Path $staging 'licenses') -Force | Out-Null

try {
foreach ($name in @('vlc', 'ffmpeg', 'mediainfo')) {
    $dependency = $dependencies.$name
    if ($dependency.sha256 -notmatch '^[a-fA-F0-9]{64}$') { throw "Invalid checksum for $name." }
    $extension = if ($dependency.archive) { '.' + $dependency.archive } elseif ($name -eq 'ffmpeg') { '.7z' } else { '.zip' }
    $identity = $name + '-' + $dependency.sha256.ToLowerInvariant()
    $archive = Join-Path $downloads ($identity + $extension)
    if (-not (Test-Path -LiteralPath $archive)) {
        $legacy = Join-Path $downloads ($name + $extension)
        if ((Test-Path -LiteralPath $legacy) -and (Get-FileHash -LiteralPath $legacy -Algorithm SHA256).Hash -eq $dependency.sha256) {
            Copy-Item -LiteralPath $legacy -Destination $archive
        } else {
        Write-Output "Downloading $name $($dependency.version)..."
        Invoke-WebRequest -Uri $dependency.url -OutFile $archive -UseBasicParsing -TimeoutSec 300
        }
    }
    $actual = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $dependency.sha256) { throw "$name checksum mismatch. Delete the incorrect archive and retry." }
    $extracted = Join-Path $downloads $identity
    $complete = Join-Path $extracted '.anyplayer-extraction.sha256'
    if (-not (Test-Path -LiteralPath $complete) -or (Get-Content -LiteralPath $complete -Raw).Trim() -ne $dependency.sha256) {
        Remove-PreparedPath $extracted
        try {
        if ($extension -eq '.zip') { Expand-Archive -LiteralPath $archive -DestinationPath $extracted }
        else {
            & 7z x $archive "-o$extracted" -y -bso0 -bsp0
            if ($LASTEXITCODE -ne 0) { throw "Failed to extract $name. Install 7-Zip for this development step." }
        }
        Set-Content -LiteralPath $complete -Value $dependency.sha256 -Encoding Ascii
        } catch { Remove-PreparedPath $extracted; throw }
    }
    if ($name -eq 'vlc') {
        $vlcDirectory = Get-ChildItem -LiteralPath $extracted -Directory | Select-Object -First 1
        Copy-Item -Path (Join-Path $vlcDirectory.FullName '*') -Destination (Join-Path $staging 'vlc') -Recurse -Force
    } elseif ($name -eq 'ffmpeg') {
        foreach ($tool in @('ffmpeg.exe', 'ffprobe.exe')) {
            $file = Get-ChildItem -LiteralPath $extracted -Filter $tool -Recurse -File | Select-Object -First 1
            if (-not $file) { throw "Missing $tool in the verified archive." }
            Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $staging 'bin') -Force
        }
        $license = Get-ChildItem -LiteralPath $extracted -Recurse -File | Where-Object Name -Match '^(LICENSE|COPYING)(\..*)?$' | Select-Object -First 1
        if ($license) { Copy-Item -LiteralPath $license.FullName -Destination (Join-Path $staging 'licenses\ffmpeg.txt') -Force }
    } else {
        foreach ($file in @('MediaInfo.exe', 'LIBCURL.DLL')) {
            Copy-Item -LiteralPath (Join-Path $extracted $file) -Destination (Join-Path $staging 'bin') -Force
        }
        Copy-Item -LiteralPath (Join-Path $extracted 'LICENSE') -Destination (Join-Path $staging 'licenses\mediainfo.txt') -Force
    }
}

Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $staging 'dependencies.json') -Force
foreach ($file in @('vlc\libvlc.dll', 'vlc\libvlccore.dll', 'bin\ffprobe.exe', 'bin\MediaInfo.exe', 'bin\LIBCURL.DLL')) {
    if (-not (Test-Path -LiteralPath (Join-Path $staging $file) -PathType Leaf)) { throw "Missing native file: $file" }
}
if ($Target -eq 'windows-arm64') {
    foreach ($file in @('bin\ffmpeg.exe','bin\ffprobe.exe','bin\MediaInfo.exe','vlc\libvlc.dll','vlc\libvlccore.dll')) {
        $peReader = [IO.BinaryReader]::new([IO.File]::OpenRead((Join-Path $staging $file)))
        try {
            if ($peReader.ReadUInt16() -ne 0x5A4D) { throw "Not a PE binary: $file" }
            $peReader.BaseStream.Position = 0x3C
            $offset = $peReader.ReadInt32()
            $peReader.BaseStream.Position = $offset
            if ($peReader.ReadUInt32() -ne 0x4550 -or $peReader.ReadUInt16() -ne 0xAA64) { throw "Not an ARM64 binary: $file" }
        } finally { $peReader.Dispose() }
    }
}
$canRunNative = ($Target -eq 'windows-x86_64' -and [Environment]::Is64BitProcess) -or
    ($Target -eq 'windows-arm64' -and [Runtime.InteropServices.RuntimeInformation]::ProcessArchitecture -eq 'Arm64')
if ($canRunNative) {
$ffmpeg = Join-Path $staging 'bin\ffmpeg.exe'
$decoders = & $ffmpeg -hide_banner -decoders 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) { throw 'The prepared FFmpeg cannot enumerate decoders.' }
foreach ($codec in @('webp_anim', 'apng', 'gif')) {
    if ($decoders -notmatch "\s$codec\s") { throw "The selected FFmpeg build lacks the $codec decoder." }
}
$probeVersion = & (Join-Path $staging 'bin\ffprobe.exe') -version 2>&1
if ($LASTEXITCODE -ne 0) { throw 'The prepared ffprobe cannot run.' }
$probeVersion | Select-Object -First 1
& (Join-Path $staging 'bin\MediaInfo.exe') --Version
if ($LASTEXITCODE -ne 0) { throw 'The prepared MediaInfo cannot run.' }
} else { Write-Output 'Foreign-architecture binaries checked structurally; runtime checks require the matching CPU.' }
foreach ($path in @($bundle, $backup, $staging)) { Assert-PreparedPath $path }
if (Test-Path -LiteralPath $bundle) { Move-Item -LiteralPath $bundle -Destination $backup }
try { Move-Item -LiteralPath $staging -Destination $bundle }
catch { if (Test-Path -LiteralPath $backup) { Move-Item -LiteralPath $backup -Destination $bundle }; throw }
Remove-PreparedPath $backup
Write-Output "Native runtime prepared: $bundle"
} finally { Remove-PreparedPath $staging }
