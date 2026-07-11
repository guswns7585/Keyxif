param(
    [Parameter(Mandatory = $true)]
    [string]$VersionName,

    [Parameter(Mandatory = $true)]
    [int]$VersionCode,

    [string]$OwnerRepo = "username/keyxif",
    [string]$MinRequiredVersionCode = "1",
    [string]$Message = "Keyxif의 새 APK가 준비되었습니다.",
    [switch]$ForceUpdate
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$gradleFile = Join-Path $root "app/build.gradle.kts"
$updateFile = Join-Path $root "docs/update.json"
$tag = "v$VersionName"
$apkName = "keyxif-$VersionName.apk"

Write-Host "Updating Gradle version to $VersionName ($VersionCode)"
$gradle = Get-Content -Raw -Encoding UTF8 $gradleFile
$gradle = $gradle -replace 'versionCode\s*=\s*\d+', "versionCode = $VersionCode"
$gradle = $gradle -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$VersionName`""
Set-Content -Encoding UTF8 -Path $gradleFile -Value $gradle

Write-Host "Updating docs/update.json"
$update = [ordered]@{
    latestVersionCode = $VersionCode
    latestVersionName = $VersionName
    minRequiredVersionCode = [int]$MinRequiredVersionCode
    title = "새 버전이 있습니다"
    message = $Message
    apkUrl = "https://github.com/$OwnerRepo/releases/download/$tag/$apkName"
    releaseNoteUrl = "https://github.com/$OwnerRepo/releases/tag/$tag"
    forceUpdate = [bool]$ForceUpdate
}
$update | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -Path $updateFile

Write-Host "Running release sanity build"
& (Join-Path $root "gradlew.bat") ":app:compileReleaseKotlin"

Write-Host "Creating release commit and tag"
git add app/build.gradle.kts docs/update.json
git commit -m "Release $tag"
git tag $tag

Write-Host "Pushing main branch and tag"
git push
git push origin $tag

Write-Host "Release tag pushed: $tag"
