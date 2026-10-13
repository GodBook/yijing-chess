param([Parameter(Mandatory = $true)][string]$Text, [string]$DeviceSerial, [int]$Index = 0)
$ErrorActionPreference = 'Stop'
if (-not $DeviceSerial) {
    $devices = @(adb devices | Select-String '^\S+\s+device$' | ForEach-Object { ($_.Line -split '\s+')[0] })
    if ($devices.Count -ne 1) { throw 'Connect exactly one device or pass DeviceSerial.' }
    $DeviceSerial = $devices[0]
}
adb -s $DeviceSerial shell uiautomator dump /data/local/tmp/yijing-qa-ui.xml | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect current Android UI.' }
[xml]$tree = (adb -s $DeviceSerial shell cat /data/local/tmp/yijing-qa-ui.xml) -join "`n"
$matches = @($tree.SelectNodes('//node') | Where-Object { $_.text -eq $Text -or $_.'content-desc' -eq $Text })
if ($Index -lt 0 -or $Index -ge $matches.Count) { throw "Visible UI target not found: $Text" }
$bounds = [regex]::Match($matches[$Index].bounds, '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
if (-not $bounds.Success) { throw 'Target has no valid bounds.' }
$x = [int](([int]$bounds.Groups[1].Value + [int]$bounds.Groups[3].Value) / 2)
$y = [int](([int]$bounds.Groups[2].Value + [int]$bounds.Groups[4].Value) / 2)
adb -s $DeviceSerial shell input tap $x $y
if ($LASTEXITCODE -ne 0) { throw 'Android tap failed.' }
Write-Output "Tapped visible target: $Text at ($x,$y)"
