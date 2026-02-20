param(
    [int]$QipusStartPage = 0,
    [int]$QipusEndPage = 49,
    [int]$RequestDelayMs = 120,
    [int]$MaxCanjuBooks = 0,
    [string]$OutFile = "data/xqipu_fens.txt"
)

$ErrorActionPreference = "Stop"

function Get-AbsoluteUrl([string]$path) {
    if ($path.StartsWith("http")) { return $path }
    return "https://www.xqipu.com$path"
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

function Get-FensFromHtml([string]$html) {
    $matches = [regex]::Matches($html, 'data-fen="([^"]+)"')
    foreach ($m in $matches) {
        $fen = $m.Groups[1].Value.Trim()
        if ($fen) { $fen }
    }
}

function Get-CanjuBookLinks([string]$html) {
    $matches = [regex]::Matches($html, 'href="/canjugupu/\d+"')
    $set = New-Object 'System.Collections.Generic.HashSet[string]'
    foreach ($m in $matches) {
        $path = $m.Value.Replace('href="', '').Replace('"', '')
        if ($path -eq "/canjugupu") { continue }
        [void]$set.Add((Get-AbsoluteUrl $path))
    }
    return $set
}

function Extract-MaxPage([string]$html, [string]$basePathPrefix) {
    $escaped = [regex]::Escape($basePathPrefix)
    $re = "$escaped\?page=(\d+)"
    $matches = [regex]::Matches($html, $re)
    $maxPage = 0
    foreach ($m in $matches) {
        $n = 0
        if ([int]::TryParse($m.Groups[1].Value, [ref]$n)) {
            if ($n -gt $maxPage) { $maxPage = $n }
        }
    }
    return $maxPage
}

$allFens = New-Object 'System.Collections.Generic.HashSet[string]'

Write-Host "Scanning qipus pages $QipusStartPage..$QipusEndPage"
for ($p = $QipusStartPage; $p -le $QipusEndPage; $p++) {
    $url = "https://www.xqipu.com/qipus?page=$p"
    $html = Fetch-ContentWithRetry $url
    foreach ($fen in (Get-FensFromHtml $html)) {
        [void]$allFens.Add($fen)
    }
}

Write-Host "Scanning canjugupu index"
$canjuIndexHtml = Fetch-ContentWithRetry "https://www.xqipu.com/canjugupu"
$bookLinks = Get-CanjuBookLinks $canjuIndexHtml | Sort-Object

$bookCount = 0
foreach ($bookUrl in $bookLinks) {
    $bookCount++
    if ($MaxCanjuBooks -gt 0 -and $bookCount -gt $MaxCanjuBooks) {
        break
    }

    Write-Host "Fetching canju book: $bookUrl"
    $bookHtml = Fetch-ContentWithRetry $bookUrl
    foreach ($fen in (Get-FensFromHtml $bookHtml)) {
        [void]$allFens.Add($fen)
    }

    $uri = [System.Uri]$bookUrl
    $bookPath = $uri.AbsolutePath
    $maxPage = Extract-MaxPage $bookHtml $bookPath
    if ($maxPage -le 0) { continue }
    for ($p = 1; $p -le $maxPage; $p++) {
        $pageUrl = "${bookUrl}?page=$p"
        $pageHtml = Fetch-ContentWithRetry $pageUrl
        foreach ($fen in (Get-FensFromHtml $pageHtml)) {
            [void]$allFens.Add($fen)
        }
    }
}

$dir = Split-Path -Parent $OutFile
if ($dir -and !(Test-Path $dir)) {
    New-Item -ItemType Directory -Path $dir | Out-Null
}

$allFens | Sort-Object | Set-Content -Path $OutFile -Encoding UTF8
Write-Host "Saved $($allFens.Count) unique FEN entries to $OutFile"
