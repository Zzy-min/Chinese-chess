param(
    [int]$StartPage = 0,
    [int]$EndPage = 4,
    [string]$OutFile = "data/event_fens.txt",
    [int]$RequestDelayMs = 120,
    [int]$MaxEvents = 0
)

$ErrorActionPreference = "Stop"

function Get-AbsoluteUrl([string]$path) {
    if ($path.StartsWith("http")) { return $path }
    return "https://www.xqipu.com$path"
}

function Get-EventLinks([string]$html) {
    $matches = [regex]::Matches($html, 'href="/eventqipu/\d+"')
    $set = New-Object 'System.Collections.Generic.HashSet[string]'
    foreach ($m in $matches) {
        $path = $m.Value.Replace('href="', '').Replace('"', '')
        [void]$set.Add((Get-AbsoluteUrl $path))
    }
    return $set
}

function Get-Fens([string]$html) {
    $matches = [regex]::Matches($html, 'data-fen="([^"]+)"')
    foreach ($m in $matches) {
        $fen = $m.Groups[1].Value.Trim()
        if ($fen) { $fen }
    }
}

function Fetch-ContentWithRetry([string]$url, [int]$retry = 3) {
    $lastErr = $null
    for ($i = 1; $i -le $retry; $i++) {
        try {
            if ($RequestDelayMs -gt 0) {
                Start-Sleep -Milliseconds $RequestDelayMs
            }
            return (Invoke-WebRequest -UseBasicParsing -Uri $url).Content
        } catch {
            $lastErr = $_
            Write-Warning "Fetch failed ($i/$retry): $url"
            Start-Sleep -Milliseconds ([Math]::Min(1500, 180 * $i))
        }
    }
    throw $lastErr
}

$allEventLinks = New-Object 'System.Collections.Generic.HashSet[string]'

for ($i = $StartPage; $i -le $EndPage; $i++) {
    $url = "https://www.xqipu.com/eventlist?page=$i"
    Write-Host "Scanning $url"
    $html = Fetch-ContentWithRetry $url
    $links = Get-EventLinks $html
    foreach ($link in $links) {
        [void]$allEventLinks.Add($link)
    }
}

$allFens = New-Object 'System.Collections.Generic.HashSet[string]'
$eventIndex = 0
foreach ($eventUrl in ($allEventLinks | Sort-Object)) {
    $eventIndex++
    if ($MaxEvents -gt 0 -and $eventIndex -gt $MaxEvents) {
        break
    }
    Write-Host "Fetching $eventUrl"
    try {
        $html = Fetch-ContentWithRetry $eventUrl
        $fens = Get-Fens $html
        foreach ($fen in $fens) {
            [void]$allFens.Add($fen)
        }
    } catch {
        Write-Warning "Skip event due to repeated failures: $eventUrl"
    }
}

$dir = Split-Path -Parent $OutFile
if ($dir -and !(Test-Path $dir)) {
    New-Item -ItemType Directory -Path $dir | Out-Null
}

$allFens | Sort-Object | Set-Content -Path $OutFile -Encoding UTF8
Write-Host "Saved $($allFens.Count) unique FEN entries from $eventIndex events to $OutFile"
