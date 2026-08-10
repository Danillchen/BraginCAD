param(
    [string]$Device = "emulator-5554",
    [int]$Iterations = 24,
    [string]$Output = "build/open-stress.csv"
)

$ErrorActionPreference = "Stop"
$project = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $env:ANDROID_HOME "platform-tools/adb.exe"
$files = @(Get-ChildItem (Join-Path $project "test-data") -Filter *.dwg -File | Sort-Object Name)
if (-not $files.Count) { throw "No DWG files" }

$cases = @()
& $adb -s $Device shell run-as com.bragincad mkdir -p files/regression | Out-Null
for ($index = 0; $index -lt $files.Count; $index++) {
    $file = $files[$index]
    $safeName = "stress_{0:D2}.dwg" -f $index
    $remote = "/data/local/tmp/$safeName"
    & $adb -s $Device push $file.FullName $remote | Out-Null
    & $adb -s $Device shell run-as com.bragincad cp $remote "files/regression/$safeName"
    if ($LASTEXITCODE) { throw "Failed to stage $($file.Name)" }
    $cases += [pscustomobject]@{ File=$file; SafeName=$safeName }
}

& $adb -s $Device logcat -c
& $adb -s $Device shell am force-stop com.bragincad
$rows = @()
for ($iteration = 0; $iteration -lt $Iterations; $iteration++) {
    $case = $cases[$iteration % $cases.Count]
    $file = $case.File
    $before = [DateTime]::UtcNow
    & $adb -s $Device shell am start -n com.bragincad/.MainActivity --es regression_file $case.SafeName | Out-Null
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    $ready = $false
    do {
        Start-Sleep -Milliseconds 500
        $log = (& $adb -s $Device logcat -d -v epoch -t 500 BraginCADRender:I BraginCAD:I AndroidRuntime:E "*:S") -join "`n"
        $ready = $log -match "firstRenderMs=.*primitives=" -and $log -match [regex]::Escape($case.SafeName)
    } while (-not $ready -and [DateTime]::UtcNow -lt $deadline)
    if (-not $ready) { throw "Timed out opening $($file.Name) at iteration $iteration" }
    $appProcessId = (& $adb -s $Device shell pidof com.bragincad).Trim()
    $mem = (& $adb -s $Device shell dumpsys meminfo $appProcessId) -join "`n"
    $pss = [regex]::Match($mem, "TOTAL\s+(\d+)").Groups[1].Value
    $fds = @(& $adb -s $Device shell run-as com.bragincad ls "/proc/$appProcessId/fd").Count
    $temps = @(& $adb -s $Device shell run-as com.bragincad ls cache/opened 2>$null).Where({$_}).Count
    $rows += [pscustomobject]@{ iteration=$iteration+1; file=$file.Name; elapsedMs=[math]::Round(([DateTime]::UtcNow-$before).TotalMilliseconds); pssKb=[int]$pss; fileDescriptors=$fds; temporaryFiles=$temps }
    & $adb -s $Device logcat -c
}

$outputPath = [IO.Path]::GetFullPath((Join-Path $project $Output))
New-Item -ItemType Directory -Force (Split-Path -Parent $outputPath) | Out-Null
$rows | Export-Csv -NoTypeInformation -Encoding utf8 $outputPath
$rows | Format-Table -AutoSize
Write-Output "Stress report: $outputPath"
