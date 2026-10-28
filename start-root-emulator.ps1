param(
    [string]$AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk",
    [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$AvdName = 'LivePlayerRoot35',
    [ValidateRange(5554,5680)][int]$Port = 5556,
    [string]$FridaServer = "$PSScriptRoot/../inspection/tools/frida-server-17.24.3-android-x86_64",
    [switch]$Headless
)
$ErrorActionPreference = 'Stop'
if ($Port % 2 -ne 0) { throw 'Use an even emulator console port.' }
$adb = Join-Path $AndroidSdk 'platform-tools/adb.exe'
$emulator = Join-Path $AndroidSdk 'emulator/emulator.exe'
if (!(Test-Path -LiteralPath $adb) -or !(Test-Path -LiteralPath $emulator)) { throw 'Android SDK emulator/platform-tools are missing.' }
$serial = "emulator-$Port"
function Invoke-AdbProbe([string[]]$Arguments) {
    $savedPreference = $ErrorActionPreference
    try {
        # Windows PowerShell 5 converts native stderr into terminating errors under Stop.
        $ErrorActionPreference = 'Continue'
        $lines = & $adb @Arguments 2>$null
        return @{ Code = $LASTEXITCODE; Lines = @($lines) }
    } finally { $ErrorActionPreference = $savedPreference }
}
$state = Invoke-AdbProbe -Arguments @('-s', $serial, 'get-state')
if ($state.Code -eq 0 -and $state.Lines -contains 'device') {
    $runningName = & $adb -s $serial emu avd name
    if ($runningName -notcontains $AvdName) { throw "Port $Port belongs to a different AVD; leave it untouched." }
} else {
    $available = & $emulator -list-avds
    if ($available -notcontains $AvdName) { throw "Create the $AvdName AVD first. See README for the tested system image." }
    $arguments = @('-avd', $AvdName, '-port', "$Port", '-no-snapshot-save', '-gpu', 'host')
    if ($Headless) { $arguments += @('-no-window', '-no-audio') }
    $logRoot = Join-Path $PSScriptRoot ('captures/emulator-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
    $window = if ($Headless) { 'Hidden' } else { 'Normal' }
    Start-Process -FilePath $emulator -ArgumentList $arguments -WindowStyle $window `
        -RedirectStandardOutput (Join-Path $logRoot 'emulator.txt') `
        -RedirectStandardError (Join-Path $logRoot 'emulator-error.txt') | Out-Null
}
Write-Host "Waiting for $serial ($AvdName)..."
$deadline = (Get-Date).AddMinutes(3)
$booted = $false
do {
    $boot = Invoke-AdbProbe -Arguments @('-s', $serial, 'shell', 'getprop', 'sys.boot_completed')
    if ($boot.Code -eq 0 -and $boot.Lines -contains '1') { $booted = $true; break }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)
if (!$booted) { throw 'Emulator did not finish booting in three minutes.' }
$runningName = & $adb -s $serial emu avd name
if ($runningName -notcontains $AvdName) { throw 'Unexpected AVD identity; refusing root operation.' }
& $adb -s $serial root
& $adb -s $serial wait-for-device
$identity = & $adb -s $serial shell id
if ($LASTEXITCODE -ne 0 -or "$identity" -notmatch 'uid=0\(root\)') { throw 'This system image does not provide adb root.' }
Write-Host "Root verified: $serial"
if (Test-Path -LiteralPath $FridaServer) {
    $serverPid = (Invoke-AdbProbe -Arguments @('-s', $serial, 'shell', 'pidof', 'probe-frida-server')).Lines
    if (!$serverPid) {
        & $adb -s $serial push $FridaServer /data/local/tmp/probe-frida-server
        if ($LASTEXITCODE -ne 0) { throw 'Frida server copy failed.' }
        & $adb -s $serial shell chmod 755 /data/local/tmp/probe-frida-server
        # Redirect all daemon streams so the ADB shell does not remain open.
        & $adb -s $serial shell '/data/local/tmp/probe-frida-server -D -l 127.0.0.1:27042 </dev/null >/data/local/tmp/probe-frida-server.log 2>&1'
        if ($LASTEXITCODE -ne 0) { throw 'Frida server did not start; inspect its emulator-local log.' }
    }
    $serverPid = & $adb -s $serial shell pidof probe-frida-server
    if (!$serverPid) { throw 'Frida server exited.' }
    Write-Host 'Frida server is running on emulator loopback only. Match the host client version to the server.'
} else { Write-Host 'No local Frida server binary supplied; the root emulator itself is ready.' }
Write-Host 'Open the installed app in the emulator. This script targets the named AVD only.'
