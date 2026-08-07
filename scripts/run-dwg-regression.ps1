param(
    [string]$Device = "",
    [string]$InputDirectory = "test-data",
    [string]$OutputDirectory = "build/regression",
    [int]$TimeoutSeconds = 90,
    [switch]$SkipBuild,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$project = Split-Path -Parent $PSScriptRoot
$inputPath = [IO.Path]::GetFullPath((Join-Path $project $InputDirectory))
$outputPath = [IO.Path]::GetFullPath((Join-Path $project $OutputDirectory))
$adb = Join-Path $env:ANDROID_HOME "platform-tools/adb.exe"
if (-not (Test-Path $adb)) { throw "adb not found; set ANDROID_HOME" }
if (-not $Device) {
    $deviceLine = (& $adb devices | Select-String "\tdevice$" | Select-Object -First 1)
    if (-not $deviceLine) { throw "No connected Android device" }
    $Device = $deviceLine.Line.Split("`t")[0]
}
$files = @(Get-ChildItem -LiteralPath $inputPath -Filter *.dwg -File | Sort-Object Name)
if (-not $files.Count) { throw "No DWG files in $inputPath" }
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null

if (-not $SkipBuild) {
    $wrapper = Join-Path $project "gradlew.bat"
    if (-not (Test-Path $wrapper)) { throw "gradlew.bat not found; build the APK first or pass -SkipBuild" }
    & $wrapper --no-daemon :app:assembleDebug
    if ($LASTEXITCODE) { throw "Gradle build failed" }
}
if (-not $SkipInstall) {
    & $adb -s $Device install -r (Join-Path $project "app/build/outputs/apk/debug/app-debug.apk") | Out-Host
    if ($LASTEXITCODE) { throw "APK install failed" }
}

function Match-Value([string]$text, [string]$pattern) {
    $match = [regex]::Match($text, $pattern)
    if (-not $match.Success) { return $null }
    return $match.Groups[1].Value
}

$results = foreach ($file in $files) {
    Write-Host "Running $($file.Name)..."
    $remoteFile = "/data/local/tmp/bragincad-$($file.Name)"
    & $adb -s $Device push $file.FullName $remoteFile | Out-Null
    if ($LASTEXITCODE) { throw "Cannot push $($file.Name)" }
    & $adb -s $Device shell run-as com.bragincad mkdir -p files/regression | Out-Null
    & $adb -s $Device shell run-as com.bragincad cp $remoteFile "files/regression/$($file.Name)" | Out-Null
    if ($LASTEXITCODE) { throw "Cannot stage $($file.Name) in app storage" }
    & $adb -s $Device logcat -c
    & $adb -s $Device shell am start -S -n com.bragincad/.MainActivity --es regression_file $file.Name | Out-Null

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $log = ""
    do {
        Start-Sleep -Milliseconds 500
        $log = (& $adb -s $Device logcat -d -v brief 2>&1) -join "`n"
        $selected = $log -match "RegressionInput .*extra=$([regex]::Escape($file.Name)).*exists=true"
        $complete = $selected -and $log -match "file=$([regex]::Escape($file.Name)) .*modelBuildMs=" -and $log -match "firstRenderMs="
        $crashed = $log -match "FATAL EXCEPTION|Fatal signal|AndroidRuntime.*Process: com\.bragincad"
    } until ($complete -or $crashed -or [DateTime]::UtcNow -gt $deadline)

    $appLine = ([regex]::Matches($log, "(?m)^.*BraginCAD\(.*file=$([regex]::Escape($file.Name)) .*$") | Select-Object -Last 1).Value
    $nativeLine = ([regex]::Matches($log, "(?m)^.*BraginCADNative\(.*decoded objects=.*$") | Select-Object -Last 1).Value
    $timingLine = ([regex]::Matches($log, "(?m)^.*BraginCADNative\(.*timing decode_ms=.*$") | Select-Object -Last 1).Value
    $renderLine = ([regex]::Matches($log, "(?m)^.*BraginCAD\(.*firstRenderMs=.*$") | Select-Object -Last 1).Value
    $meminfo = (& $adb -s $Device shell dumpsys meminfo com.bragincad 2>&1) -join "`n"
    $pss = Match-Value $meminfo "TOTAL PSS:\s+(\d+)"
    $unsupported = Match-Value $appLine "unsupportedRanking=\[(.*)\]"
    $hasCrash = $crashed -or -not $complete

    [pscustomobject]@{
        file = $file.Name
        bytes = $file.Length
        dwgVersion = Match-Value $appLine "version=([^ ]+)"
        totalObjects = [int](Match-Value $appLine "objects=(\d+)")
        graphicalEntities = [int](Match-Value $appLine "graphical=(\d+)")
        supportedEntities = [int](Match-Value $appLine "supported=(\d+)")
        unsupportedVisibleEntities = [int](Match-Value $appLine "unsupportedVisible=(\d+)")
        graphicalCoveragePercent = [double](Match-Value $appLine " coverage=([0-9.]+)%")
        visibleGraphicalCoveragePercent = [double](Match-Value $appLine "visibleCoverage=([0-9.]+)%")
        unsupportedTypes = $unsupported
        decodeMs = [double](Match-Value $timingLine "decode_ms=([0-9.]+)")
        nativeModelExpansionMs = [double](Match-Value $timingLine "native_model_build_ms=([0-9.]+)")
        jniKotlinModelBuildMs = [double](Match-Value $appLine "modelBuildMs=(\d+)")
        firstRenderMs = [double](Match-Value $renderLine "firstRenderMs=([0-9.]+)")
        renderPrimitiveCount = [int](Match-Value $appLine "primitives=(\d+)")
        pssKb = if ($pss) { [int]$pss } else { $null }
        crashed = [bool]$hasCrash
    }
    $log | Set-Content -LiteralPath (Join-Path $outputPath "$($file.BaseName).log") -Encoding utf8
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$jsonPath = Join-Path $outputPath "regression-$timestamp.json"
$csvPath = Join-Path $outputPath "regression-$timestamp.csv"
$markdownPath = Join-Path $outputPath "regression-$timestamp.md"
$results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $jsonPath -Encoding utf8
$results | Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding utf8
$rows = $results | ForEach-Object { "| $($_.file) | $($_.dwgVersion) | $($_.supportedEntities)/$($_.graphicalEntities) | $($_.unsupportedVisibleEntities) | $($_.graphicalCoveragePercent)% | $($_.visibleGraphicalCoveragePercent)% | $($_.decodeMs) | $($_.nativeModelExpansionMs) | $($_.jniKotlinModelBuildMs) | $($_.firstRenderMs) | $($_.renderPrimitiveCount) | $($_.pssKb) | $($_.crashed) |" }
@("# BraginCAD multi-DWG regression", "", "| File | Version | Supported/graphical | Unsupported visible | Coverage | Visible coverage | Decode ms | Native expansion ms | JNI/Kotlin ms | First render ms | Primitives | PSS KB | Crash |", "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|", $rows, "", "Unsupported type breakdown is retained in the JSON/CSV and per-file Logcat files.") | Set-Content -LiteralPath $markdownPath -Encoding utf8
$results | Format-Table file,dwgVersion,visibleGraphicalCoveragePercent,unsupportedVisibleEntities,decodeMs,nativeModelExpansionMs,firstRenderMs,pssKb,crashed -AutoSize
Write-Host "Reports: $jsonPath $csvPath $markdownPath"
if ($results.crashed -contains $true) { exit 2 }
