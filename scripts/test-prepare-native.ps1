$ErrorActionPreference = 'Stop'
$prepare = Join-Path $PSScriptRoot 'prepare-native.ps1'
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('AnyPlayer-native-fixture-' + [guid]::NewGuid().ToString('N'))
$repository = Join-Path $fixtureRoot 'repository'
$downloads = Join-Path $repository 'native\downloads'
New-Item -ItemType Directory -Path $downloads -Force | Out-Null
function Assert-Fixture([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
try {
    $stub = Join-Path $fixtureRoot 'tool.exe'
    Add-Type -OutputAssembly $stub -OutputType ConsoleApplication -TypeDefinition @'
using System;
public class NativeFixtureTool {
    public static void Main(string[] args) {
        Console.WriteLine(" V..... gif fixture");
        Console.WriteLine(" V..... apng fixture");
        Console.WriteLine(" V..... webp_anim fixture");
    }
}
'@

    function New-Fixture([string]$Version, [bool]$Broken = $false) {
        $dependencies = [ordered]@{}
        foreach ($name in @('vlc', 'ffmpeg', 'mediainfo')) {
            $payload = Join-Path $fixtureRoot "$Version-$name"
            New-Item -ItemType Directory -Path $payload -Force | Out-Null
            if ($name -eq 'vlc') {
                $vlc = Join-Path $payload "vlc-$Version"
                New-Item -ItemType Directory -Path $vlc | Out-Null
                foreach ($file in @('libvlc.dll', 'libvlccore.dll')) { Set-Content -LiteralPath (Join-Path $vlc $file) -Value $Version }
                if ($Version -eq 'A') { Set-Content -LiteralPath (Join-Path $vlc 'obsolete-module.dll') -Value 'old' }
            } elseif ($name -eq 'ffmpeg') {
                Copy-Item -LiteralPath $stub -Destination (Join-Path $payload 'ffmpeg.exe')
                if (-not $Broken) { Copy-Item -LiteralPath $stub -Destination (Join-Path $payload 'ffprobe.exe') }
                Set-Content -LiteralPath (Join-Path $payload 'LICENSE') -Value 'fixture'
            } else {
                Copy-Item -LiteralPath $stub -Destination (Join-Path $payload 'MediaInfo.exe')
                Set-Content -LiteralPath (Join-Path $payload 'LIBCURL.DLL') -Value $Version
                Set-Content -LiteralPath (Join-Path $payload 'LICENSE') -Value 'fixture'
            }
            $extension = if ($name -eq 'ffmpeg') { '.7z' } else { '.zip' }
            $archive = Join-Path $downloads ($name + $extension)
            if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive }
            if ($name -eq 'ffmpeg') {
                & 7z a -t7z $archive (Join-Path $payload '*') -bso0 -bsp0
                if ($LASTEXITCODE -ne 0) { throw 'Fixture archive creation failed.' }
            } else { Compress-Archive -Path (Join-Path $payload '*') -DestinationPath $archive }
            $dependencies[$name] = [ordered]@{ version=$Version; url="https://invalid.local/$name"; sha256=(Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() }
        }
        @{ 'windows-x86_64'=$dependencies } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $repository 'native\dependencies.json') -Encoding UTF8
        return $dependencies
    }

    $first = New-Fixture A
    $incomplete = Join-Path $downloads ('vlc-' + $first.vlc.sha256)
    New-Item -ItemType Directory -Path $incomplete | Out-Null
    Set-Content -LiteralPath (Join-Path $incomplete 'partial') -Value 'interrupted'
    & $prepare -Repository $repository | Out-Null
    $bundle = Join-Path $repository 'native\windows-x86_64\native'
    Assert-Fixture ((Get-Content -LiteralPath (Join-Path $bundle 'vlc\libvlc.dll') -Raw).Trim() -eq 'A') 'Initial archive was not extracted.'
    Assert-Fixture (-not (Test-Path -LiteralPath (Join-Path $incomplete 'partial'))) 'Incomplete extraction was reused.'
    $second = New-Fixture B
    & $prepare -Repository $repository | Out-Null
    Assert-Fixture ((Get-Content -LiteralPath (Join-Path $bundle 'vlc\libvlc.dll') -Raw).Trim() -eq 'B') 'Upgrade reused old native binaries.'
    Assert-Fixture (-not (Test-Path -LiteralPath (Join-Path $bundle 'vlc\obsolete-module.dll'))) 'Old VLC module survived upgrade.'
    $marker = Join-Path $downloads ('vlc-' + $second.vlc.sha256 + '\.anyplayer-extraction.sha256')
    $stamp = (Get-Item -LiteralPath $marker).LastWriteTimeUtc
    & $prepare -Repository $repository | Out-Null
    Assert-Fixture ((Get-Item -LiteralPath $marker).LastWriteTimeUtc -eq $stamp) 'Matching extraction was not reused.'
    $null = New-Fixture C $true
    $failed = $false
    try { & $prepare -Repository $repository | Out-Null } catch { $failed = $true }
    Assert-Fixture $failed 'Incomplete replacement unexpectedly succeeded.'
    Assert-Fixture ((Get-Content -LiteralPath (Join-Path $bundle 'vlc\libvlc.dll') -Raw).Trim() -eq 'B') 'Failed preparation replaced the working bundle.'
    $bundled = Get-Content -LiteralPath (Join-Path $bundle 'dependencies.json') -Raw | ConvertFrom-Json
    Assert-Fixture ($bundled.'windows-x86_64'.vlc.version -eq 'B') 'Failed preparation replaced the working manifest.'
    Assert-Fixture (@(Get-ChildItem -LiteralPath (Split-Path -Parent $bundle) -Filter '.native-preparing-*').Count -eq 0) 'Staging directory leaked.'
    Write-Output 'Native preparation fixtures passed: incomplete extraction, upgrade, stale module removal, reuse, failure preservation.'
} finally {
    $resolved = [IO.Path]::GetFullPath($fixtureRoot)
    $prefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\AnyPlayer-native-fixture-'
    if (-not $resolved.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { throw "Unsafe fixture cleanup path: $resolved" }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
