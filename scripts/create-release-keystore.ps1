param(
    [string]$OutputDir = "keystores",
    [string]$Alias = "keyxif-release",
    [int]$ValidityDays = 10000
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$outputPath = Join-Path $root $OutputDir
$keystorePath = Join-Path $outputPath "keyxif-release.jks"
$secretsPath = Join-Path $outputPath "github-secrets.txt"

function New-Secret {
    $bytes = [byte[]]::new(32)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    return [Convert]::ToBase64String($bytes).TrimEnd("=").Replace("+", "-").Replace("/", "_")
}

$keytoolCandidates = @()
if ($env:JAVA_HOME) {
    $keytoolCandidates += (Join-Path $env:JAVA_HOME "bin/keytool.exe")
}
$keytoolCandidates += @(
    "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe",
    "C:\Program Files\Java\jdk-17\bin\keytool.exe",
    "keytool"
)

$keytool = $keytoolCandidates | Where-Object {
    if ($_ -eq "keytool") {
        [bool](Get-Command $_ -ErrorAction SilentlyContinue)
    } else {
        Test-Path -LiteralPath $_
    }
} | Select-Object -First 1

if (-not $keytool) {
    throw "keytool.exe was not found. Install Android Studio or set JAVA_HOME to a JDK path."
}

New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
if (Test-Path $keystorePath) {
    throw "Keystore already exists: $keystorePath"
}

$storePassword = New-Secret
$keyPassword = New-Secret

& $keytool `
    -genkeypair `
    -v `
    -keystore $keystorePath `
    -storetype JKS `
    -alias $Alias `
    -keyalg RSA `
    -keysize 2048 `
    -validity $ValidityDays `
    -storepass $storePassword `
    -keypass $keyPassword `
    -dname "CN=Keyxif, OU=Keyxif, O=Keyxif, L=Seoul, S=Seoul, C=KR"

$keystoreBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystorePath))
$secretText = @"
KEYSTORE_BASE64=$keystoreBase64
KEYSTORE_PASSWORD=$storePassword
KEY_ALIAS=$Alias
KEY_PASSWORD=$keyPassword
"@
$secretText | Set-Content -Encoding UTF8 -Path $secretsPath

Write-Host ""
Write-Host "Release keystore created:"
Write-Host $keystorePath
Write-Host ""
Write-Host "GitHub Secrets file created:"
Write-Host $secretsPath
Write-Host ""
Write-Host "Keep the .jks file and passwords private. Do not commit them."
