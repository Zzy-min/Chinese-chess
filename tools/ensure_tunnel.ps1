param(
    [string]$ProjectRoot = "D:\claude项目\XiangqiGame",
    [string]$TunnelName = "xiangqiarena",
    [string]$TunnelId = "189d02e7-c522-4592-8006-b7ac898f0077",
    [string]$CloudflaredConfig = "C:\Users\Lenovo\.cloudflared\config.yml"
)

$ErrorActionPreference = "Stop"

function Resolve-Cloudflared {
    $wingetPath = "C:\Users\Lenovo\AppData\Local\Microsoft\WinGet\Packages\Cloudflare.cloudflared_Microsoft.Winget.Source_8wekyb3d8bbwe\cloudflared.exe"
    if (Test-Path $wingetPath) {
        return $wingetPath
    }
    $cmd = Get-Command cloudflared -ErrorAction SilentlyContinue
    if ($cmd -and $cmd.Source) {
        return $cmd.Source
    }
    throw "cloudflared executable not found"
}

function Test-TunnelRunning {
    param([string]$NeedleName, [string]$NeedleId)
    $items = Get-CimInstance Win32_Process -Filter "name='cloudflared.exe'" -ErrorAction SilentlyContinue
    foreach ($item in $items) {
        $line = [string]$item.CommandLine
        if ($line -match [Regex]::Escape($NeedleName) -or $line -match [Regex]::Escape($NeedleId)) {
            return $true
        }
    }
    return $false
}

if (Test-TunnelRunning -NeedleName $TunnelName -NeedleId $TunnelId) {
    exit 0
}

$cloudflaredExe = Resolve-Cloudflared
if (-not (Test-Path $CloudflaredConfig)) {
    throw "cloudflared config missing: $CloudflaredConfig"
}

Start-Process -FilePath $cloudflaredExe `
    -ArgumentList @("tunnel", "--protocol", "http2", "--config", $CloudflaredConfig, "run", $TunnelName) `
    -WorkingDirectory $ProjectRoot `
    -WindowStyle Hidden | Out-Null
