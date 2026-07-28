[CmdletBinding()]
param(
    [string]$EngineRoot = ''
)

$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -lt 7) {
    $pwsh = Get-Command pwsh.exe -ErrorAction SilentlyContinue
    if (-not $pwsh) {
        throw 'PowerShell 7 (pwsh.exe) is required for the engine protocol probe.'
    }
    $arguments = @('-NoProfile', '-File', $PSCommandPath)
    if ($EngineRoot) {
        $arguments += @('-EngineRoot', $EngineRoot)
    }
    & $pwsh.Source @arguments
    exit $LASTEXITCODE
}
if ([string]::IsNullOrWhiteSpace($EngineRoot)) {
    $EngineRoot = Join-Path $PSScriptRoot 'engines'
}

function Invoke-ProtocolProbe {
    param(
        [Parameter(Mandatory)][string]$Executable,
        [Parameter(Mandatory)][string[]]$Commands,
        [Parameter(Mandatory)][string]$Expected
    )

    if (-not (Test-Path -LiteralPath $Executable -PathType Leaf)) {
        throw "Engine executable not found: $Executable"
    }

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $Executable
    $startInfo.WorkingDirectory = Split-Path -Parent $Executable
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    $startInfo.StandardInputEncoding = $utf8NoBom
    $startInfo.StandardOutputEncoding = $utf8NoBom
    $startInfo.StandardErrorEncoding = $utf8NoBom

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) {
            throw "Failed to start engine: $Executable"
        }
        foreach ($command in $Commands) {
            $process.StandardInput.WriteLine($command)
        }
        $process.StandardInput.Close()
        $output = $process.StandardOutput.ReadToEnd()
        $errorOutput = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) {
            throw "Engine exited with code $($process.ExitCode): $errorOutput"
        }
        $normalizedLines = @($output -split "\r?\n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' })
        if ($normalizedLines -notcontains $Expected) {
            throw "Engine did not return expected protocol marker '$Expected'. Output: $($normalizedLines -join ' | ')"
        }
        return $normalizedLines
    } finally {
        if (-not $process.HasExited) {
            $process.Kill()
        }
        $process.Dispose()
    }
}

$pikafish = Join-Path $EngineRoot 'pikafish\pikafish.exe'
$rapfi = Join-Path $EngineRoot 'rapfi\rapfi.exe'

$pikafishOutput = Invoke-ProtocolProbe -Executable $pikafish -Commands @('uci', 'quit') -Expected 'uciok'
$rapfiOutput = Invoke-ProtocolProbe -Executable $rapfi -Commands @('START 15', 'END') -Expected 'OK'

Write-Host "Pikafish UCI probe passed ($($pikafishOutput.Count) lines)."
Write-Host "Rapfi Piskvork probe passed ($($rapfiOutput.Count) lines)."
