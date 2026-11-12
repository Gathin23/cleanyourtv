param(
    [string]$TvHost = $env:TV_HOST,
    [string]$Config,
    [switch]$Restore,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object Text.UTF8Encoding $false
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$py = Join-Path $repo 'tools/venv/Scripts/python.exe'
$record = Join-Path $repo 'backup/home-dashboard-original.json'
if (-not $TvHost) { throw 'Pass -TvHost <current TV IP:port>' }
$env:TV_HOST = $TvHost
function Invoke-DashboardTv {
    $result = & $py -I "$repo/tools/tv.py" @args
    if ($LASTEXITCODE -ne 0) { throw 'TV command failed; stop and check the connection' }
    $result
}
if ($Restore) {
    $before = Get-Content -Raw -LiteralPath $record | ConvertFrom-Json
    foreach ($key in @('screensaver_components','screensaver_enabled')) {
        $value = [string]$before.$key
        if ($value -eq 'null') { Invoke-DashboardTv "settings delete secure $key" }
        elseif ($value -match '^[A-Za-z0-9_./:,]+$') { Invoke-DashboardTv "settings put secure $key $value" }
        else { throw 'Invalid original screensaver record' }
    }
    Write-Output 'Original screensaver restored. The dashboard remains installed.'
    return
}
if (-not $Config) { throw 'Pass -Config <private config.json>' }
$configPath = (Resolve-Path -LiteralPath $Config).Path
try {
    $c = Get-Content -Raw -LiteralPath $configPath | ConvertFrom-Json
    foreach ($key in @('haUrl','haToken','batteryEntity','solarEntity','frigateUrl')) {
        if (-not $c.$key) { throw 'Missing field' }
    }
} catch { throw 'Private configuration is invalid; check required fields. Contents were not printed.' }
$sdkLevel = (Invoke-DashboardTv 'getprop ro.build.version.sdk') -join ''
if ($sdkLevel -notmatch '^\d+$' -or [int]$sdkLevel -lt 26) { throw 'Android API 26 or newer required' }
if (-not $SkipBuild) {
    Push-Location $PSScriptRoot
    try { & ./gradlew.bat --no-daemon assembleRelease --console=plain; if ($LASTEXITCODE -ne 0) { throw 'Build failed' } }
    finally { Pop-Location }
}
if (-not (Test-Path -LiteralPath $record)) {
    $before = [ordered]@{}
    foreach ($key in @('screensaver_components','screensaver_enabled')) {
        $before[$key] = (Invoke-DashboardTv "settings get secure $key") -join ''
    }
    [IO.File]::WriteAllText($record,($before | ConvertTo-Json),(New-Object Text.UTF8Encoding $false))
}
Invoke-DashboardTv --push "$PSScriptRoot/app/build/outputs/apk/release/app-release.apk" /data/local/tmp/home-dashboard.apk
try {
    $installed = Invoke-DashboardTv 'pm install -r /data/local/tmp/home-dashboard.apk'
    if (($installed -join '') -notmatch 'Success') { throw 'Install refused; nothing changed in the screensaver selection' }
} finally { Invoke-DashboardTv 'rm -f /data/local/tmp/home-dashboard.apk' }
Invoke-DashboardTv 'am force-stop com.gathin.homedashboard'
Invoke-DashboardTv --push $configPath /data/local/tmp/home-dashboard-config.json
try { Invoke-DashboardTv 'content write --uri content://com.gathin.homedashboard.config/config.json < /data/local/tmp/home-dashboard-config.json' }
finally { Invoke-DashboardTv 'rm -f /data/local/tmp/home-dashboard-config.json' }
Invoke-DashboardTv 'am start -W -n com.gathin.homedashboard/.SettingsActivity'
Write-Output 'Installed. Check Open dashboard, choose cameras and add feeds. Screensaver selection is unchanged until verified.'
