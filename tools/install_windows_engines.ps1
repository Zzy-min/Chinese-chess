[CmdletBinding()]
param(
    [string]$Destination = '',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if ($PSVersionTable.PSVersion.Major -lt 7) {
    $pwsh = Get-Command pwsh.exe -ErrorAction SilentlyContinue
    if (-not $pwsh) {
        throw 'PowerShell 7 (pwsh.exe) is required to install and probe the Windows engines without adding a UTF-8 BOM to protocol input.'
    }
    $arguments = @('-NoProfile', '-File', $PSCommandPath)
    if ($Destination) {
        $arguments += @('-Destination', $Destination)
    }
    if ($Force) {
        $arguments += '-Force'
    }
    & $pwsh.Source @arguments
    exit $LASTEXITCODE
}
if ([string]::IsNullOrWhiteSpace($Destination)) {
    $Destination = Join-Path $PSScriptRoot 'engines'
}

$releases = @(
    @{
        Name = 'Pikafish'
        Url = 'https://github.com/official-pikafish/Pikafish/releases/download/Pikafish-2026-01-02/Pikafish.2026-01-02.7z'
        Sha256 = '84257063905615919fb4ee6a70273a94843bb6ec04c45e3ac706098838bc1a49'
        Archive = 'pikafish.7z'
        Folder = 'pikafish'
        Executable = 'pikafish.exe'
        Candidates = @('pikafish-avx2.exe', 'pikafish-modern.exe', 'pikafish.exe')
    },
    @{
        Name = 'Rapfi'
        Url = 'https://github.com/dhbloo/rapfi/releases/download/250615/Rapfi-engine.7z'
        Sha256 = '1a3e24024062a153ac079060ee9589a37c6bdd1ecc54fed3908793c519594e05'
        Archive = 'rapfi.7z'
        Folder = 'rapfi'
        Executable = 'rapfi.exe'
        Candidates = @('pbrain-rapfi-windows-avx2.exe', 'pbrain-rapfi-windows-sse.exe', 'pbrain-rapfi.exe')
    }
)

function Expand-SevenZipArchive {
    param(
        [Parameter(Mandatory)][string]$Archive,
        [Parameter(Mandatory)][string]$Output
    )

    $sevenZip = Get-Command 7z.exe, 7zz.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($sevenZip) {
        & $sevenZip.Source x -y "-o$Output" $Archive | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "7-Zip extraction failed for $Archive"
        }
        return
    }

    $tar = Get-Command tar.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $tar) {
        throw 'Neither 7z.exe nor Windows tar.exe is available to extract official engine archives.'
    }
    & $tar.Source -xf $Archive -C $Output
    if ($LASTEXITCODE -ne 0) {
        throw "Windows tar extraction failed for $Archive"
    }
}

function Select-EngineExecutable {
    param(
        [Parameter(Mandatory)][string]$Root,
        [Parameter(Mandatory)][string[]]$Candidates
    )

    foreach ($candidate in $Candidates) {
        $match = Get-ChildItem -LiteralPath $Root -Recurse -File -Filter $candidate |
            Select-Object -First 1
        if ($match) {
            return $match
        }
    }
    throw "No supported Windows engine executable was found under $Root"
}

$resolvedDestination = [System.IO.Path]::GetFullPath($Destination)
New-Item -ItemType Directory -Force -Path $resolvedDestination | Out-Null
$downloadRoot = Join-Path ([System.IO.Path]::GetTempPath()) 'qingqiju-engine-installer'
New-Item -ItemType Directory -Force -Path $downloadRoot | Out-Null

foreach ($release in $releases) {
    $archivePath = Join-Path $downloadRoot $release.Archive
    $extractPath = Join-Path $downloadRoot ($release.Folder + '-extract')
    $enginePath = Join-Path $resolvedDestination $release.Folder

    if ((Test-Path -LiteralPath $enginePath) -and -not $Force) {
        throw "$enginePath already exists. Re-run with -Force to replace this local engine installation."
    }

    $actualHash = if (Test-Path -LiteralPath $archivePath) {
        (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
    } else {
        ''
    }
    if ($actualHash -ne $release.Sha256) {
        Write-Host "Downloading pinned $($release.Name) release..."
        Invoke-WebRequest -Uri $release.Url -OutFile $archivePath -UseBasicParsing
        $actualHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
    } else {
        Write-Host "Using verified cached $($release.Name) archive."
    }
    if ($actualHash -ne $release.Sha256) {
        throw "$($release.Name) SHA-256 mismatch. Expected $($release.Sha256), got $actualHash."
    }

    if (Test-Path -LiteralPath $extractPath) {
        Remove-Item -LiteralPath $extractPath -Recurse -Force
    }
    New-Item -ItemType Directory -Path $extractPath | Out-Null
    Expand-SevenZipArchive -Archive $archivePath -Output $extractPath
    $selectedExecutable = Select-EngineExecutable -Root $extractPath -Candidates $release.Candidates

    if (Test-Path -LiteralPath $enginePath) {
        Remove-Item -LiteralPath $enginePath -Recurse -Force
    }
    New-Item -ItemType Directory -Path $enginePath | Out-Null
    Copy-Item -Path (Join-Path $extractPath '*') -Destination $enginePath -Recurse -Force
    Copy-Item -LiteralPath $selectedExecutable.FullName `
        -Destination (Join-Path $enginePath $release.Executable) -Force
}

& (Join-Path $PSScriptRoot 'smoke_test_windows_engines.ps1') -EngineRoot $resolvedDestination
Write-Host "Windows engines installed under $resolvedDestination"
