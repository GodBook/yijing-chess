param(
    [Parameter(Mandatory = $true)][string]$Name,
    [string]$DeviceSerial,
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\artifacts\qa')
)

$ErrorActionPreference = 'Stop'
if ($Name -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Snapshot name must contain letters, digits, underscores or hyphens.' }
if (-not $DeviceSerial) {
    $connected = @(adb devices | Select-String '^\S+\s+device$' | ForEach-Object { ($_.Line -split '\s+')[0] })
    if ($connected.Count -ne 1) { throw 'Connect exactly one Android device or pass DeviceSerial.' }
    $DeviceSerial = $connected[0]
}
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $resolvedOutput -Force | Out-Null
$imagePath = Join-Path $resolvedOutput "$Name.png"
adb -s $DeviceSerial shell uiautomator dump /data/local/tmp/yijing-qa-ui.xml | Out-Null
if ($LASTEXITCODE -eq 0) {
    adb -s $DeviceSerial pull /data/local/tmp/yijing-qa-ui.xml (Join-Path $resolvedOutput "$Name.xml") | Out-Null
}
$process = [System.Diagnostics.Process]::new()
$process.StartInfo.FileName = (Get-Command adb).Source
$process.StartInfo.Arguments = "-s $DeviceSerial exec-out screencap -p"
$process.StartInfo.UseShellExecute = $false
$process.StartInfo.CreateNoWindow = $true
$process.StartInfo.RedirectStandardOutput = $true
$process.StartInfo.RedirectStandardError = $true
$null = $process.Start()
$stream = [System.IO.File]::Create($imagePath)
try { $process.StandardOutput.BaseStream.CopyTo($stream) } finally { $stream.Dispose() }
$process.WaitForExit()
if ($process.ExitCode -ne 0) { throw $process.StandardError.ReadToEnd() }
Write-Output $imagePath
