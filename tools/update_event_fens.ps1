param(
    [int]$StartPage = 0,
    [int]$EndPage = 4,
    [string]$OutFile = "data/event_fens.txt"
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

$allEventLinks = New-Object 'System.Collections.Generic.HashSet[string]'

for ($i = $StartPage; $i -le $EndPage; $i++) {
    $url = "https://www.xqipu.com/eventlist?page=$i"
    Write-Host "Scanning $url"
    $html = (Invoke-WebRequest -UseBasicParsing -Uri $url).Content
    $links = Get-EventLinks $html
    foreach ($link in $links) {
        [void]$allEventLinks.Add($link)
    }
}

$allFens = New-Object 'System.Collections.Generic.HashSet[string]'
foreach ($eventUrl in $allEventLinks) {
    Write-Host "Fetching $eventUrl"
    $html = (Invoke-WebRequest -UseBasicParsing -Uri $eventUrl).Content
    $fens = Get-Fens $html
    foreach ($fen in $fens) {
        [void]$allFens.Add($fen)
    }
}

$dir = Split-Path -Parent $OutFile
if ($dir -and !(Test-Path $dir)) {
    New-Item -ItemType Directory -Path $dir | Out-Null
}

$allFens | Sort-Object | Set-Content -Path $OutFile -Encoding UTF8
Write-Host "Saved $($allFens.Count) unique FEN entries to $OutFile"
