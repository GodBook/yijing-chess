param([string]$DeviceSerial)

$ErrorActionPreference = 'Stop'
if (-not $DeviceSerial) {
    $connected = @(adb devices | Select-String '^\S+\s+device$' | ForEach-Object { ($_.Line -split '\s+')[0] })
    if ($connected.Count -ne 1) { throw 'Connect exactly one Android device or pass DeviceSerial.' }
    $DeviceSerial = $connected[0]
}
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$permission = (adb -s $DeviceSerial shell cmd appops get com.yijing.chess REQUEST_INSTALL_PACKAGES) -join "`n"
$permissionMatch = [regex]::Match($permission, 'REQUEST_INSTALL_PACKAGES: (\w+)')
$originalMode = if ($permissionMatch.Success) { $permissionMatch.Groups[1].Value } else { 'default' }
if ($originalMode -notin @('allow', 'ignore', 'deny', 'default', 'foreground')) { throw 'Unknown original install permission mode.' }
$originalFont = (adb -s $DeviceSerial shell settings get system font_scale).Trim()
$originalRotation = (adb -s $DeviceSerial shell settings get system user_rotation).Trim()
$originalAutoRotation = (adb -s $DeviceSerial shell settings get system accelerometer_rotation).Trim()
$density = (adb -s $DeviceSerial shell wm density) -join "`n"
$densityMatch = [regex]::Match($density, 'Override density: (\d+)')
Push-Location $projectRoot
try {
    & .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Test build failed.' }
    adb -s $DeviceSerial install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'App installation failed.' }
    adb -s $DeviceSerial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'Test installation failed.' }
    # Revoking this permission can kill the app, so prepare and restore it outside instrumentation.
    adb -s $DeviceSerial shell cmd appops set com.yijing.chess REQUEST_INSTALL_PACKAGES deny
    adb -s $DeviceSerial shell input keyevent KEYCODE_WAKEUP
    adb -s $DeviceSerial shell wm dismiss-keyguard
    New-Item -ItemType Directory -Force artifacts/qa | Out-Null
    adb -s $DeviceSerial shell am instrument -w -r com.yijing.chess.test/androidx.test.runner.AndroidJUnitRunner |
        Tee-Object -FilePath artifacts/qa/device-tests.txt | Tee-Object -Variable qaRunOutput
    $result = $qaRunOutput -join "`n"
    if ($LASTEXITCODE -ne 0 -or $result -notmatch 'OK \(\d+ tests?\)' -or $result -match 'FAILURES|Process crashed') {
        throw 'Device tests failed; inspect artifacts/qa/device-tests.txt.'
    }
} finally {
    adb -s $DeviceSerial shell cmd appops set com.yijing.chess REQUEST_INSTALL_PACKAGES $originalMode
    adb -s $DeviceSerial shell settings put system font_scale $originalFont
    adb -s $DeviceSerial shell settings put system accelerometer_rotation $originalAutoRotation
    adb -s $DeviceSerial shell settings put system user_rotation $originalRotation
    if ($densityMatch.Success) { adb -s $DeviceSerial shell wm density $densityMatch.Groups[1].Value }
    else { adb -s $DeviceSerial shell wm density reset }
    Pop-Location
}
