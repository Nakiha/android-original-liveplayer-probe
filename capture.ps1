param(
    [string]$Adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe",
    [string]$Serial = '',
    [int]$Seconds = 60,
    [string]$OutputDirectory = "$PSScriptRoot/captures"
)
$ErrorActionPreference = 'Stop'
if ($Seconds -lt 1 -or $Seconds -gt 600) { throw 'Seconds must be between 1 and 600.' }
$adbArgs = @()
if ($Serial) { $adbArgs = @('-s', $Serial) }
$deviceState = & $Adb @adbArgs get-state 2>&1
if ($LASTEXITCODE -ne 0 -or $deviceState -notcontains 'device') { throw 'Connect and authorize one ARM64 Android device, or specify -Serial.' }
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$captureRoot = Join-Path $OutputDirectory (Get-Date -Format 'yyyyMMdd-HHmmss')
New-Item -ItemType Directory -Path $captureRoot | Out-Null
& $Adb @adbArgs shell getprop ro.product.cpu.abilist | Out-File -LiteralPath (Join-Path $captureRoot 'abis.txt') -Encoding utf8
& $Adb @adbArgs shell dumpsys package com.ss.android.ugc.aweme |
    Select-String -Pattern 'versionCode=|versionName=|lastUpdateTime=' |
    Out-File -LiteralPath (Join-Path $captureRoot 'installed-douyin-version.txt') -Encoding utf8
# Keep all probe UID processes; do not clear or request unrelated device log buffers.
$uidLine = (& $Adb @adbArgs shell cmd package list packages -U local.originalplayer.probe | Select-String 'package:local.originalplayer.probe\s' | Select-Object -First 1).Line
if ($uidLine -notmatch 'uid:(\d+)') { throw 'Install the probe APK first.' }
$probeUid = $Matches[1]
Write-Host "Recording probe UID $probeUid for $Seconds seconds. Run the probe stages on the phone now."
$logArgs = $adbArgs + @('logcat', '-v', 'threadtime', "--uid=$probeUid")
$process = Start-Process -FilePath $Adb -ArgumentList $logArgs -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $captureRoot 'logcat.txt') -RedirectStandardError (Join-Path $captureRoot 'logcat-error.txt')
try { Start-Sleep -Seconds $Seconds }
finally { if (-not $process.HasExited) { Stop-Process -Id $process.Id } }
& $Adb @adbArgs shell run-as local.originalplayer.probe cat files/probe.jsonl |
    Out-File -LiteralPath (Join-Path $captureRoot 'probe.jsonl') -Encoding utf8
Write-Host "Saved: $captureRoot"
