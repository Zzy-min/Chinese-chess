param(
    [int]$QipusStartPage = 0,
    [int]$QipusEndPage = 49,
    [int]$RequestDelayMs = 120,
    [int]$MaxCanjuBooks = 0,
    [string]$FenFile = "data/xqipu_fens.txt",
    [string]$JavaFile = "src/main/java/com/xiangqi/ai/XqipuLearnedSet.java",
    [switch]$SkipFetch,
    [switch]$Compile,
    [switch]$Publish,
    [string]$CommitMessage = "chore(ai): refresh XqipuLearnedSet from canjugupu and qipus"
)

$ErrorActionPreference = "Stop"

function Normalize-Line([string]$line) {
    if ($null -eq $line) { return $null }
    $t = $line.Trim()
    if ([string]::IsNullOrWhiteSpace($t)) { return $null }
    $space = $t.IndexOf(' ')
    if ($space -ge 0) {
        $t = $t.Substring(0, $space)
    }
    return $t
}

if (-not $SkipFetch) {
    Write-Host "Step 1/4: Fetching latest xqipu FENs..."
    $fetchArgs = @(
        "-File", "tools/update_xqipu_fens.ps1",
        "-QipusStartPage", "$QipusStartPage",
        "-QipusEndPage", "$QipusEndPage",
        "-RequestDelayMs", "$RequestDelayMs",
        "-OutFile", $FenFile
    )
    if ($MaxCanjuBooks -gt 0) {
        $fetchArgs += @("-MaxCanjuBooks", "$MaxCanjuBooks")
    }
    & pwsh @fetchArgs
} else {
    Write-Host "Step 1/4: Skip fetching, use existing $FenFile"
}

Write-Host "Step 2/4: Generating XqipuLearnedSet.java..."
$lines = Get-Content -Path $FenFile -ErrorAction Stop
$set = New-Object 'System.Collections.Generic.HashSet[string]'
foreach ($line in $lines) {
    $n = Normalize-Line $line
    if ($n) { [void]$set.Add($n) }
}

$fens = $set | Sort-Object
$generatedAt = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$sourceInfo = "https://www.xqipu.com/qipus + https://www.xqipu.com/canjugupu"

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("package com.xiangqi.ai;")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("import com.xiangqi.model.Board;")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("import java.util.Collections;")
[void]$sb.AppendLine("import java.util.HashSet;")
[void]$sb.AppendLine("import java.util.Set;")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("/**")
[void]$sb.AppendLine(" * 来自 xqipu.com/canjugupu 与 xqipu.com/qipus 的已学习局面集合（自动生成）。")
[void]$sb.AppendLine(" * 生成时间: $generatedAt")
[void]$sb.AppendLine(" * 数据来源: $sourceInfo")
[void]$sb.AppendLine(" */")
[void]$sb.AppendLine("public final class XqipuLearnedSet {")
[void]$sb.AppendLine("    private static final Set<String> BOARD_PARTS = new HashSet<String>();")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("    static {")
$chunkSize = 700
$chunkCount = [Math]::Ceiling($fens.Count / $chunkSize)
for ($i = 0; $i -lt $chunkCount; $i++) {
    [void]$sb.AppendLine("        loadChunk$($i + 1)();")
}
[void]$sb.AppendLine("    }")
[void]$sb.AppendLine("")

for ($i = 0; $i -lt $chunkCount; $i++) {
    [void]$sb.AppendLine("    private static void loadChunk$($i + 1)() {")
    $start = $i * $chunkSize
    $end = [Math]::Min($fens.Count, $start + $chunkSize) - 1
    for ($j = $start; $j -le $end; $j++) {
        $escaped = $fens[$j].Replace("\", "\\").Replace('"', '\"')
        [void]$sb.AppendLine("        BOARD_PARTS.add(""$escaped"");")
    }
    [void]$sb.AppendLine("    }")
    [void]$sb.AppendLine("")
}

[void]$sb.AppendLine("    private XqipuLearnedSet() {")
[void]$sb.AppendLine("    }")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("    public static boolean contains(Board board) {")
[void]$sb.AppendLine("        return board != null && containsFen(FenCodec.toFen(board));")
[void]$sb.AppendLine("    }")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("    public static boolean containsFen(String fen) {")
[void]$sb.AppendLine("        if (fen == null) {")
[void]$sb.AppendLine("            return false;")
[void]$sb.AppendLine("        }")
[void]$sb.AppendLine("        String trimmed = fen.trim();")
[void]$sb.AppendLine("        if (trimmed.isEmpty()) {")
[void]$sb.AppendLine("            return false;")
[void]$sb.AppendLine("        }")
[void]$sb.AppendLine("        int space = trimmed.indexOf(' ');")
[void]$sb.AppendLine("        String boardPart = space >= 0 ? trimmed.substring(0, space) : trimmed;")
[void]$sb.AppendLine("        return BOARD_PARTS.contains(boardPart);")
[void]$sb.AppendLine("    }")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("    public static int size() {")
[void]$sb.AppendLine("        return BOARD_PARTS.size();")
[void]$sb.AppendLine("    }")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("    public static Set<String> all() {")
[void]$sb.AppendLine("        return Collections.unmodifiableSet(BOARD_PARTS);")
[void]$sb.AppendLine("    }")
[void]$sb.AppendLine("}")

$dir = Split-Path -Parent $JavaFile
if ($dir -and !(Test-Path $dir)) {
    New-Item -ItemType Directory -Path $dir | Out-Null
}
$sb.ToString() | Set-Content -Path $JavaFile -Encoding UTF8
Write-Host "Generated $JavaFile with $($fens.Count) FEN entries."

if ($Compile -or $Publish) {
    Write-Host "Step 3/4: Compiling Java sources..."
    $files = Get-ChildItem -Path src/main/java -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    if (!(Test-Path target/classes)) {
        New-Item -ItemType Directory -Path target/classes | Out-Null
    }
    & javac -encoding UTF-8 -d target/classes $files
    if ($LASTEXITCODE -ne 0) {
        throw "Compilation failed."
    }
}

if ($Publish) {
    Write-Host "Step 4/4: Publishing to GitHub main..."
    & git add $JavaFile $FenFile tools/update_xqipu_fens.ps1 tools/update_xqipu_learnedset.ps1
    & git commit -m $CommitMessage
    & git push origin main
}
