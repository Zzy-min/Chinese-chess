param(
    [string]$ProjectRoot = "D:\claude项目\XiangqiGame",
    [int]$IntervalSeconds = 60
)

$ErrorActionPreference = "Continue"

$logDir = Join-Path $ProjectRoot "logs"
if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
}

$logPath = Join-Path $logDir "web_tunnel_watchdog.log"
$webScript = Join-Path $ProjectRoot "tools\ensure_web.ps1"
$tunnelScript = Join-Path $ProjectRoot "tools\ensure_tunnel.ps1"

function Write-WatchdogLog {
    param([string]$Message)
    $stamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Add-Content -Path $logPath -Value "[$stamp] $Message" -Encoding UTF8
}

Write-WatchdogLog "watchdog started; interval=${IntervalSeconds}s"

while ($true) {
    try {
        & $webScript -ProjectRoot $ProjectRoot
        & $tunnelScript -ProjectRoot $ProjectRoot
        Write-WatchdogLog "health check completed"
    } catch {
        Write-WatchdogLog "health check failed: $($_.Exception.Message)"
    }
    Start-Sleep -Seconds $IntervalSeconds
}
