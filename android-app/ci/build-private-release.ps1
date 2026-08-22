param(
    [Parameter(Mandatory = $true)]
    [string]$RollbackRevision,
    [string]$OutputDirectory = ""
)

$ErrorActionPreference = "Stop"

$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
$androidRoot = Join-Path $repositoryRoot "android-app"
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $repositoryRoot "outputs\private-release"
}
$OutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)

$dirty = & git -C $repositoryRoot status --porcelain
if ($LASTEXITCODE -ne 0) {
    throw "Unable to inspect the source worktree."
}
if ($dirty) {
    throw "The private release must be built from a clean committed worktree."
}

$sourceRevision = (& git -C $repositoryRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $sourceRevision -notmatch "^[0-9a-f]{40}$") {
    throw "Unable to resolve the release source revision."
}
$rollbackRevision = (& git -C $repositoryRoot rev-parse --verify "$RollbackRevision`^{commit}").Trim()
if ($LASTEXITCODE -ne 0 -or $rollbackRevision -notmatch "^[0-9a-f]{40}$") {
    throw "RollbackRevision must resolve to a committed source revision."
}

$propertiesPath = Join-Path $androidRoot "gradle.properties"
$properties = @{}
foreach ($line in Get-Content -LiteralPath $propertiesPath) {
    if ($line -match "^\s*([^#!][^=]+?)\s*=\s*(.*)$") {
        $properties[$matches[1].Trim()] = $matches[2].Trim()
    }
}
$versionCode = [int]$properties["SHENK_VERSION_CODE"]
$versionName = [string]$properties["SHENK_VERSION_NAME"]
if ($versionCode -lt 11 -or $versionName -notmatch "-rc\.[0-9]+$") {
    throw "Private RC versioning must use versionCode 11 or newer and an -rc.N version name."
}

$gradle = Join-Path $androidRoot "gradlew.bat"
& $gradle package8ReleaseCandidateCheck "-PSHENK_REQUIRE_RELEASE_SIGNING=true"
if ($LASTEXITCODE -ne 0) {
    throw "The signed Package 8 release-candidate gate failed."
}

$apk = Join-Path $androidRoot "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path -LiteralPath $apk)) {
    throw "The signed release APK was not produced."
}

$sdkDirectory = ""
$localPropertiesPath = Join-Path $androidRoot "local.properties"
if (Test-Path -LiteralPath $localPropertiesPath) {
    $sdkLine = Get-Content -LiteralPath $localPropertiesPath |
        Where-Object { $_ -match "^sdk\.dir=" } |
        Select-Object -First 1
    if ($sdkLine) {
        $sdkDirectory = $sdkLine -replace "^sdk\.dir=", ""
        $sdkDirectory = $sdkDirectory -replace "\\:", ":"
        $sdkDirectory = $sdkDirectory -replace "\\\\", "\"
    }
}
if ([string]::IsNullOrWhiteSpace($sdkDirectory)) {
    $sdkDirectory = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
}
if ([string]::IsNullOrWhiteSpace($sdkDirectory)) {
    throw "Android SDK location is unavailable."
}

$buildToolsDirectory = Get-ChildItem -LiteralPath (Join-Path $sdkDirectory "build-tools") -Directory |
    Sort-Object { [version]$_.Name } -Descending |
    Where-Object {
        (Test-Path -LiteralPath (Join-Path $_.FullName "apksigner.bat")) -and
        (Test-Path -LiteralPath (Join-Path $_.FullName "aapt.exe"))
    } |
    Select-Object -First 1
if (-not $buildToolsDirectory) {
    throw "Android build tools with apksigner and aapt are unavailable."
}
$apksigner = Join-Path $buildToolsDirectory.FullName "apksigner.bat"
$aapt = Join-Path $buildToolsDirectory.FullName "aapt.exe"

$signatureReport = & $apksigner verify --verbose --print-certs $apk
if ($LASTEXITCODE -ne 0) {
    throw "APK signature verification failed."
}
$certificateLine = $signatureReport |
    Where-Object { $_ -match "Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]+)" } |
    Select-Object -First 1
if (-not $certificateLine) {
    throw "The signing certificate SHA-256 digest was not reported."
}
$certificateSha256 = ([regex]::Match($certificateLine, "([0-9a-fA-F]{64})")).Value.ToLowerInvariant()

$badging = & $aapt dump badging $apk
if ($LASTEXITCODE -ne 0) {
    throw "Unable to inspect the signed APK manifest."
}
$packageLine = $badging | Where-Object { $_ -match "^package:" } | Select-Object -First 1
if (-not $packageLine) {
    throw "The signed APK package metadata is missing."
}
$packageMatch = [regex]::Match(
    $packageLine,
    "name='([^']+)'\s+versionCode='([^']+)'\s+versionName='([^']+)'"
)
if (-not $packageMatch.Success) {
    throw "The signed APK package metadata is malformed."
}
if (
    $packageMatch.Groups[1].Value -ne "io.s2qtech.shenk" -or
    [int]$packageMatch.Groups[2].Value -ne $versionCode -or
    $packageMatch.Groups[3].Value -ne $versionName
) {
    throw "The signed APK identity does not match the canonical RC version."
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$shortRevision = $sourceRevision.Substring(0, 12)
$artifactName = "shenk-$versionName-$versionCode-$shortRevision.apk"
$artifactPath = Join-Path $OutputDirectory $artifactName
Copy-Item -LiteralPath $apk -Destination $artifactPath -Force

$artifact = Get-Item -LiteralPath $artifactPath
$sha256 = (Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
$builtAt = [DateTimeOffset]::UtcNow.ToString("o")
$record = [ordered]@{
    schema = "shenk_private_release_record/v1"
    applicationId = "io.s2qtech.shenk"
    versionCode = $versionCode
    versionName = $versionName
    artifact = $artifactName
    sizeBytes = $artifact.Length
    sha256 = $sha256
    signingCertificateSha256 = $certificateSha256
    sourceRevision = $sourceRevision
    rollbackRevision = $rollbackRevision
    builtAt = $builtAt
}
$recordPath = Join-Path $OutputDirectory "shenk-$versionName-$versionCode-$shortRevision.json"
$checksumPath = "$artifactPath.sha256"
$record | ConvertTo-Json | Set-Content -LiteralPath $recordPath -Encoding utf8
"$sha256  $artifactName" | Set-Content -LiteralPath $checksumPath -Encoding ascii

[pscustomobject]@{
    artifact = $artifactPath
    releaseRecord = $recordPath
    checksum = $checksumPath
    versionCode = $versionCode
    versionName = $versionName
    sha256 = $sha256
    signingCertificateSha256 = $certificateSha256
    sourceRevision = $sourceRevision
    rollbackRevision = $rollbackRevision
} | ConvertTo-Json
