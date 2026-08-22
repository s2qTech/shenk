param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath,
    [string]$AdbPath = "adb",
    [string]$PackageName = "io.s2qtech.shenk",
    [string]$ActivityName = "io.s2qtech.shenk/.MainActivity"
)

$ErrorActionPreference = "Stop"
$ApkPath = (Resolve-Path -LiteralPath $ApkPath).Path
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
$androidRoot = Join-Path $repositoryRoot "android-app"

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

function Get-CertificateSha256([string]$Path) {
    $report = & $apksigner verify --print-certs $Path
    if ($LASTEXITCODE -ne 0) {
        throw "APK signature verification failed."
    }
    $line = $report |
        Where-Object { $_ -match "Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]+)" } |
        Select-Object -First 1
    $digest = if ($line) { ([regex]::Match($line, "([0-9a-fA-F]{64})")).Value } else { "" }
    if ([string]::IsNullOrWhiteSpace($digest)) {
        throw "The APK signing certificate digest is missing."
    }
    return $digest.ToLowerInvariant()
}

function Get-PackageState {
    $state = & $AdbPath shell dumpsys package $PackageName
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect the installed package."
    }
    $versionCodeMatch = [regex]::Match(($state -join "`n"), "versionCode=(\d+)")
    $versionNameMatch = [regex]::Match(($state -join "`n"), "versionName=([^\r\n]+)")
    $firstInstallMatch = [regex]::Match(($state -join "`n"), "firstInstallTime=([^\r\n]+)")
    if (-not $versionCodeMatch.Success -or -not $firstInstallMatch.Success) {
        throw "The installed package state is incomplete."
    }
    return [pscustomobject]@{
        versionCode = [int]$versionCodeMatch.Groups[1].Value
        versionName = $versionNameMatch.Groups[1].Value.Trim()
        firstInstallTime = $firstInstallMatch.Groups[1].Value.Trim()
    }
}

$deviceLines = & $AdbPath devices
if ($LASTEXITCODE -ne 0) {
    throw "adb devices failed."
}
$devices = @($deviceLines | Where-Object { $_ -match "\tdevice$" })
if ($devices.Count -ne 1) {
    throw "Expected exactly one connected and authorized Android device; found $($devices.Count)."
}

$badging = & $aapt dump badging $ApkPath
if ($LASTEXITCODE -ne 0) {
    throw "Unable to inspect the release APK."
}
$packageLine = $badging | Where-Object { $_ -match "^package:" } | Select-Object -First 1
$packageMatch = [regex]::Match(
    $packageLine,
    "name='([^']+)'\s+versionCode='([^']+)'\s+versionName='([^']+)'"
)
if (-not $packageMatch.Success -or $packageMatch.Groups[1].Value -ne $PackageName) {
    throw "The release APK application ID is not $PackageName."
}
$releaseVersionCode = [int]$packageMatch.Groups[2].Value
$releaseVersionName = $packageMatch.Groups[3].Value
$releaseCertificate = Get-CertificateSha256 $ApkPath

$before = Get-PackageState
if ($releaseVersionCode -le $before.versionCode) {
    throw "Release versionCode $releaseVersionCode must be greater than installed versionCode $($before.versionCode)."
}

$installedPathLine = & $AdbPath shell pm path $PackageName | Select-Object -First 1
if ($LASTEXITCODE -ne 0 -or $installedPathLine -notmatch "^package:(.+)$") {
    throw "Unable to resolve the installed base APK."
}
$temporaryApk = Join-Path ([System.IO.Path]::GetTempPath()) "shenk-installed-$([guid]::NewGuid().ToString('N')).apk"
try {
    & $AdbPath pull $matches[1] $temporaryApk | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect the installed signing certificate."
    }
    $installedCertificate = Get-CertificateSha256 $temporaryApk
} finally {
    if (Test-Path -LiteralPath $temporaryApk) {
        Remove-Item -LiteralPath $temporaryApk -Force
    }
}

if ($releaseCertificate -ne $installedCertificate) {
    throw "Signing certificate mismatch. The existing app will not be uninstalled or cleared; establish the intended signing identity before retrying."
}

& $AdbPath install -r $ApkPath
if ($LASTEXITCODE -ne 0) {
    throw "adb install -r failed. The existing app was not uninstalled or cleared."
}

$after = Get-PackageState
if ($after.versionCode -ne $releaseVersionCode -or $after.versionName -ne $releaseVersionName) {
    throw "The installed package version does not match the release APK."
}
if ($after.firstInstallTime -ne $before.firstInstallTime) {
    throw "The package install identity changed; data-preserving update acceptance failed."
}

$launch = & $AdbPath shell am start -W -n $ActivityName
if ($LASTEXITCODE -ne 0) {
    throw "The release candidate did not launch."
}
$totalTimeLine = $launch | Where-Object { $_ -match "^TotalTime:\s+\d+$" } | Select-Object -First 1
$waitTimeLine = $launch | Where-Object { $_ -match "^WaitTime:\s+\d+$" } | Select-Object -First 1
$launchTime = if ($totalTimeLine) {
    [int]($totalTimeLine -replace "^TotalTime:\s+", "")
} elseif ($waitTimeLine) {
    [int]($waitTimeLine -replace "^WaitTime:\s+", "")
} else {
    $null
}

[pscustomobject]@{
    package = $PackageName
    previousVersionCode = $before.versionCode
    versionCode = $after.versionCode
    versionName = $after.versionName
    firstInstallTime = $after.firstInstallTime
    signingCertificateSha256 = $releaseCertificate
    coldLaunchMilliseconds = $launchTime
    dataPreservingUpdate = $true
} | ConvertTo-Json
