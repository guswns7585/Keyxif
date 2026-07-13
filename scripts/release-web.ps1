param(
    [string]$VersionName
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$webRoot = Join-Path $root "web"

if ($VersionName) {
    $packageFile = Join-Path $webRoot "package.json"
    $package = Get-Content -Raw -Encoding UTF8 $packageFile | ConvertFrom-Json
    $package.version = $VersionName
    $package | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $packageFile
}

Push-Location $webRoot
try {
    npm install
    npm run build
} finally {
    Pop-Location
}

Write-Host "Web build complete. Deploy web/dist with Cloudflare Pages."
