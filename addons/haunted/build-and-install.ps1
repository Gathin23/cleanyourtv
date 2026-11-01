# Windows version of build-and-install.sh: build Haunted, install it on the TV and make it the screensaver.
#
# Usage: addons\haunted\build-and-install.ps1 -TvHost 192.168.1.50
#        addons\haunted\build-and-install.ps1 -TvHost 192.168.1.50 -Start
#        addons\haunted\build-and-install.ps1 -TvHost 192.168.1.50 -Uninstall
param(
    [string]$TvHost = $env:TV_HOST,
    [switch]$Uninstall,
    [switch]$Start
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
$here = $PSScriptRoot
$tools = Join-Path (Split-Path (Split-Path $here -Parent) -Parent) 'tools'
$py = Join-Path $tools 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'com.gathin.haunted'
$dream = "$pkg/.HauntedDream"
$before = '/data/local/tmp/cyt-haunted-before'
function Tv([string]$cmd) { & $py -I (Join-Path $tools 'tv.py') $cmd; if ($LASTEXITCODE) { throw "tv.py failed: $cmd" } }

if ($Uninstall) {
    # Put the screensaver from before the first run back, then remove the app.
    Tv "if [ -f $before ]; then { read c; read e; } < $before; if [ `"`$c`" = null ]; then settings delete secure screensaver_components >/dev/null; else settings put secure screensaver_components `"`$c`"; fi; [ `"`$e`" = null ] || settings put secure screensaver_enabled `"`$e`"; rm -f $before; fi; pm uninstall $pkg"
    exit 0
}

Push-Location $here
try {
    & .\gradlew.bat assembleRelease -q
    if ($LASTEXITCODE) { throw 'build failed (JDK 17 and Android SDK 34 needed; see launcher\README)' }
} finally { Pop-Location }
$apk = Join-Path $here 'app\build\outputs\apk\release\app-release.apk'
& $py -I (Join-Path $tools 'b64push.py') $apk /data/local/tmp/haunted.apk | Out-Null
if ($LASTEXITCODE) { throw 'sending the APK failed' }
Tv 'pm install -r /data/local/tmp/haunted.apk; rm -f /data/local/tmp/haunted.apk'
Tv "[ -f $before ] || { settings get secure screensaver_components; settings get secure screensaver_enabled; } > $before; settings put secure screensaver_components $dream; settings put secure screensaver_enabled 1"
Write-Host 'Installed: Haunted is the screensaver. Undo with: addons\haunted\build-and-install.ps1 -Uninstall'
if ($Start) { Tv 'am start -n com.android.systemui/.Somnambulator' }
