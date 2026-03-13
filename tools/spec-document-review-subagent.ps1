param(
    [Parameter(Mandatory = $true)]
    [string]$SpecPath,

    [string]$OutputPath = "",

    [string]$Model = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-AbsolutePath {
    param([Parameter(Mandatory = $true)][string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location).Path $Path))
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptDir ".." )).Path
$specFullPath = Resolve-AbsolutePath $SpecPath

if (-not (Test-Path $specFullPath -PathType Leaf)) {
    throw "Spec file not found: $specFullPath"
}

$templatePath = Join-Path $repoRoot "docs\superpowers\reviewers\spec-document-reviewer-prompt.md"
if (-not (Test-Path $templatePath -PathType Leaf)) {
    throw "Prompt template not found: $templatePath"
}

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $reviewsDir = Join-Path $repoRoot "docs\superpowers\reviews"
    New-Item -ItemType Directory -Force -Path $reviewsDir | Out-Null
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $specName = [System.IO.Path]::GetFileNameWithoutExtension($specFullPath)
    $OutputPath = Join-Path $reviewsDir "$stamp-$specName-review.md"
}

$outputFullPath = Resolve-AbsolutePath $OutputPath
$outputDir = Split-Path -Parent $outputFullPath
if (-not [string]::IsNullOrWhiteSpace($outputDir)) {
    New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
}

$template = Get-Content -Raw -Encoding UTF8 $templatePath
$prompt = $template.Replace("[SPEC_FILE_PATH]", $specFullPath).Trim()

$codexArgs = @(
    "exec",
    "--cd", $repoRoot,
    "--sandbox", "read-only",
    "--output-last-message", $outputFullPath,
    "--skip-git-repo-check",
    "-"
)

if (-not [string]::IsNullOrWhiteSpace($Model)) {
    $codexArgs = @("exec", "--model", $Model) + $codexArgs[1..($codexArgs.Length - 1)]
}

$null = $prompt | & codex @codexArgs
$exitCode = $LASTEXITCODE

if ($exitCode -ne 0) {
    throw "codex exec failed with exit code $exitCode"
}

Write-Output "Spec review saved: $outputFullPath"

