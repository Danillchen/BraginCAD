param(
    [string]$Device = "emulator-5554",
    [int]$Iterations = 20,
    [int]$IntervalMs = 100
)

$ErrorActionPreference = "Stop"
$project = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $env:ANDROID_HOME "platform-tools/adb.exe"
$sourceFiles = @(
    (Get-Item (Join-Path $project "test-data/test_01.dwg"))
    (Get-Item (Join-Path $project "test-data/test_06.dwg"))
)
$remoteNames = @("rapid_01.dwg", "rapid_06.dwg")

& $adb -s $Device shell run-as com.bragincad mkdir -p files/regression | Out-Null
for ($index = 0; $index -lt $sourceFiles.Count; $index++) {
    $temporary = "/data/local/tmp/$($remoteNames[$index])"
    & $adb -s $Device push $sourceFiles[$index].FullName $temporary | Out-Null
    & $adb -s $Device shell run-as com.bragincad cp $temporary "files/regression/$($remoteNames[$index])"
    if ($LASTEXITCODE) { throw "Failed to stage $($sourceFiles[$index].Name)" }
}

& $adb -s $Device logcat -c
& $adb -s $Device shell am force-stop com.bragincad
for ($iteration = 0; $iteration -lt $Iterations; $iteration++) {
    $name = $remoteNames[$iteration % $remoteNames.Count]
    & $adb -s $Device shell am start -n com.bragincad/.MainActivity --es regression_file $name | Out-Null
    Start-Sleep -Milliseconds $IntervalMs
}

$expected = $remoteNames[($Iterations - 1) % $remoteNames.Count]
$deadline = [DateTime]::UtcNow.AddSeconds(90)
do {
    Start-Sleep -Milliseconds 500
    $log = (& $adb -s $Device logcat -d -v brief BraginCAD:I BraginCADRender:I AndroidRuntime:E "*:S") -join "`n"
    $ready = $log -match "file=$([regex]::Escape($expected)) " -and $log -match "firstRenderMs=.*primitives="
} until ($ready -or [DateTime]::UtcNow -gt $deadline)

if (-not $ready) { throw "Final rapid-open file $expected did not render" }
if ($log -match "FATAL EXCEPTION|AndroidRuntime.*Process: com.bragincad") { throw "AndroidRuntime crash detected" }

& $adb -s $Device shell uiautomator dump /sdcard/bragincad_rapid.xml | Out-Null
$ui = (& $adb -s $Device shell cat /sdcard/bragincad_rapid.xml) -join "`n"
if ($ui -notmatch [regex]::Escape($expected)) { throw "Stale result visible: expected $expected" }

$processId = (& $adb -s $Device shell pidof com.bragincad).Trim()
$fds = @(& $adb -s $Device shell run-as com.bragincad ls "/proc/$processId/fd").Count
$previousErrorAction = $ErrorActionPreference
$ErrorActionPreference = "SilentlyContinue"
$temporaryOutput = & $adb -s $Device shell run-as com.bragincad ls cache/opened 2>&1
$ErrorActionPreference = $previousErrorAction
$temps = @($temporaryOutput).Where({$_ -and $_ -notmatch "No such file"}).Count
$stale = ([regex]::Matches($log, "Discarding stale DWG result")).Count

[pscustomobject]@{
    operations = $Iterations
    intervalMs = $IntervalMs
    expectedFile = $expected
    staleResultsDiscarded = $stale
    fileDescriptors = $fds
    temporaryFiles = $temps
    crash = $false
} | Format-List
