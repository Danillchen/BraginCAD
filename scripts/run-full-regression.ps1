param(
    [string]$Avd = "Pixel_8",
    [string]$Device = "emulator-5554",
    [string]$InputDirectory = "test-data",
    [string]$OutputDirectory = "build/regression",
    [int]$BootTimeoutSeconds = 180,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$project = Split-Path -Parent $PSScriptRoot
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA "Android/Sdk" }
if (-not $env:ANDROID_USER_HOME) { $env:ANDROID_USER_HOME = Join-Path $env:USERPROFILE ".android" }
$adb = Join-Path $env:ANDROID_HOME "platform-tools/adb.exe"
$emulator = Join-Path $env:ANDROID_HOME "emulator/emulator.exe"
if (-not (Test-Path $adb)) { throw "adb not found at $adb" }
if (-not (Test-Path $emulator)) { throw "emulator not found at $emulator" }
$key = Join-Path $env:ANDROID_USER_HOME "adbkey"
if (Test-Path $key) { $env:ADB_VENDOR_KEYS = $key }

& $adb start-server | Out-Null
$connected = (& $adb devices | Select-String "^$([regex]::Escape($Device))\s+device$")
if (-not $connected) {
    $available = & $emulator -list-avds
    if ($available -notcontains $Avd) { throw "AVD $Avd is not installed" }
    Start-Process -FilePath $emulator -ArgumentList @("-avd",$Avd,"-no-snapshot","-no-window","-no-audio","-gpu","swiftshader_indirect","-skip-adb-auth") -WindowStyle Hidden | Out-Null
}

$deadline = [DateTime]::UtcNow.AddSeconds($BootTimeoutSeconds)
do {
    $state = (& $adb devices 2>&1) -join "`n"
    if ($state -match "$([regex]::Escape($Device))\s+device") {
        $boot = & $adb -s $Device shell getprop sys.boot_completed 2>$null
        if ($boot -eq "1") { break }
    }
    & $adb reconnect offline 2>&1 | Out-Null
    Start-Sleep -Seconds 3
} until ([DateTime]::UtcNow -gt $deadline)
if ($boot -ne "1") { throw "Android target $Device did not boot in $BootTimeoutSeconds seconds" }

if (-not $SkipBuild) {
    $wrapper = Join-Path $project "gradlew.bat"
    & $wrapper --no-daemon :app:assembleDebug
    if ($LASTEXITCODE) { throw "Gradle build failed" }
}

& (Join-Path $PSScriptRoot "run-dwg-regression.ps1") -Device $Device -InputDirectory $InputDirectory -OutputDirectory $OutputDirectory -SkipBuild
exit $LASTEXITCODE
