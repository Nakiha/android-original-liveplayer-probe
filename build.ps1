param(
    [string]$AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$JavaHome = 'C:/Program Files/Android/Android Studio/jbr',
    [string]$Gradle = '',
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
if (-not $Gradle) { $Gradle = Join-Path $PSScriptRoot 'gradlew.bat' }
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidSdk
Push-Location -LiteralPath $PSScriptRoot
try {
    $buildArgs = @('--console=plain', '--no-daemon', ':app:assembleDebug', ':app:lintDebug')
    if ($Offline) { $buildArgs = @('--offline') + $buildArgs }
    & $Gradle @buildArgs
    if ($LASTEXITCODE -ne 0) { throw 'Gradle build or lint failed.' }
} finally { Pop-Location }
