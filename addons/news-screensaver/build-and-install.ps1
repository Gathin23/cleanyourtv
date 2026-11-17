# Windows version of build-and-install.sh: build the news screensaver and install it on the TV.
# Then run setup.ps1 to choose the feeds and make it the TV's screensaver.
#
# Usage: addons\news-screensaver\build-and-install.ps1 -TvHost 192.168.1.50
#        addons\news-screensaver\build-and-install.ps1 -TvHost 192.168.1.50 -Uninstall
param(
    [string]$TvHost = $env:TV_HOST,
    [switch]$Uninstall
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
$here = $PSScriptRoot
$tools = Join-Path (Split-Path (Split-Path $here -Parent) -Parent) 'tools'
$py = Join-Path $tools 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'com.gathin.newsdream'
function Tv([string]$cmd) { & $py -I (Join-Path $tools 'tv.py') $cmd; if ($LASTEXITCODE) { throw "tv.py failed: $cmd" } }

if ($Uninstall) {
    # Put the screensaver from before setup.ps1 back first, then remove the app.
    & (Join-Path $here 'setup.ps1') -TvHost $TvHost -Off
    Tv "pm uninstall $pkg"
    exit 0
}

Push-Location $here
try {
    & .\gradlew.bat assembleRelease -q
    if ($LASTEXITCODE) { throw 'build failed (JDK 17 and Android SDK 34 needed; see launcher\README)' }
} finally { Pop-Location }
$apk = Join-Path $here 'app\build\outputs\apk\release\app-release.apk'
& $py -I (Join-Path $tools 'b64push.py') $apk /data/local/tmp/newsdream.apk | Out-Null
if ($LASTEXITCODE) { throw 'sending the APK failed' }
Tv 'pm install -r /data/local/tmp/newsdream.apk; rm -f /data/local/tmp/newsdream.apk'
# TCL's TclAppBoot blocks the app's provider until it may auto-start (AUTO_START on G08 builds, APP_AUTO_START
# on G10); elsewhere the op doesn't exist. write-settings saves it before the next reboot.
Tv "appops set $pkg AUTO_START allow 2>/dev/null; appops set $pkg APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; true" | Out-Null
Write-Host "Installed. Now: addons\news-screensaver\setup.ps1 -TvHost $TvHost"
