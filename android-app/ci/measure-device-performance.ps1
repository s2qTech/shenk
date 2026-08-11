param(
    [string]$AdbPath = "adb",
    [string]$PackageName = "io.s2qtech.shenk",
    [string]$ActivityName = "io.s2qtech.shenk/.MainActivity",
    [ValidateRange(3, 25)]
    [int]$ColdStartIterations = 5
)

$ErrorActionPreference = "Stop"

$deviceLines = & $AdbPath devices
if ($LASTEXITCODE -ne 0) {
    throw "adb devices failed"
}
$devices = @($deviceLines | Where-Object { $_ -match "\tdevice$" })
if ($devices.Count -ne 1) {
    throw "Expected exactly one connected and authorized Android device; found $($devices.Count)."
}

$coldStarts = @()
for ($iteration = 1; $iteration -le $ColdStartIterations; $iteration++) {
    & $AdbPath shell am force-stop $PackageName | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to stop $PackageName before iteration $iteration."
    }
    $launch = & $AdbPath shell am start -W -n $ActivityName
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to launch $ActivityName during iteration $iteration."
    }
    $totalLine = $launch | Where-Object { $_ -match "^TotalTime:\s+\d+$" } | Select-Object -First 1
    if (-not $totalLine) {
        throw "Android did not report TotalTime during iteration $iteration."
    }
    $coldStarts += [int]($totalLine -replace "^TotalTime:\s+", "")
}

$sorted = @($coldStarts | Sort-Object)
$middle = [math]::Floor($sorted.Count / 2)
$median = if (($sorted.Count % 2) -eq 1) {
    $sorted[$middle]
} else {
    [math]::Round(($sorted[$middle - 1] + $sorted[$middle]) / 2.0, 1)
}
$p95Index = [math]::Max(0, [math]::Ceiling($sorted.Count * 0.95) - 1)
$graphics = & $AdbPath shell dumpsys gfxinfo $PackageName
$graphicsSummary = @(
    $graphics |
        Where-Object { $_ -match "^(Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile):" } |
        Select-Object -First 6
)

[pscustomobject]@{
    package = $PackageName
    coldStartMilliseconds = $coldStarts
    medianColdStartMilliseconds = $median
    p95ColdStartMilliseconds = $sorted[$p95Index]
    graphicsSummary = $graphicsSummary
} | ConvertTo-Json -Depth 3
