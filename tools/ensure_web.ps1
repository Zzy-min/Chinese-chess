param(
    [string]$ProjectRoot = "D:\claude项目\XiangqiGame",
    [int]$Port = 18388
)

$ErrorActionPreference = "Stop"

function Test-WebListening {
    param([int]$TargetPort)
    $conn = Get-NetTCPConnection -LocalPort $TargetPort -State Listen -ErrorAction SilentlyContinue
    return $null -ne $conn
}

if (Test-WebListening -TargetPort $Port) {
    exit 0
}

$runScript = Join-Path $ProjectRoot "run_web.bat"
if (-not (Test-Path $runScript)) {
    throw "run_web.bat not found: $runScript"
}

$env:NO_BROWSER = "1"
Start-Process -FilePath "cmd.exe" -ArgumentList "/c", """$runScript""" -WorkingDirectory $ProjectRoot -WindowStyle Hidden | Out-Null

