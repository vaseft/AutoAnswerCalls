param(
    [string]$SdkPath = $env:ANDROID_HOME,
    [string]$BuildRoot = (Join-Path $env:TEMP 'LightAnswer-build')
)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
if (!$SdkPath) { $SdkPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (!(Test-Path -LiteralPath $SdkPath)) { throw 'Pass -SdkPath with your installed Android SDK path.' }
$property = 'sdk.dir=' + $SdkPath.Replace('\','/').Replace(':','\:')
$property | Set-Content -LiteralPath (Join-Path $workspace 'local.properties') -Encoding ASCII
Push-Location $workspace
try {
    & '.\gradlew.bat' :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug "-PbuildRoot=$BuildRoot"
    if ($LASTEXITCODE -ne 0) { throw 'Android build or verification failed.' }
    $output = Join-Path $workspace 'output'
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    Copy-Item -LiteralPath (Join-Path $BuildRoot 'outputs\apk\debug\app-debug.apk') -Destination (Join-Path $output '轻接-1.0.0-测试版.apk')
    Write-Output (Join-Path $output '轻接-1.0.0-测试版.apk')
} finally { Pop-Location }
