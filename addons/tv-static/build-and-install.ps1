# Windows version of build-and-install.sh: build TV Static, install it on the TV, let it open over other apps
# and start the screen watcher, which plays the effect once so you can see it.
#
# Usage: addons\tv-static\build-and-install.ps1 -TvHost 192.168.1.50
#        addons\tv-static\build-and-install.ps1 -TvHost 192.168.1.50 -NoPlay      # install without playing it now
#        addons\tv-static\build-and-install.ps1 -TvHost 192.168.1.50 -Uninstall
param(
    [string]$TvHost = $env:TV_HOST,
    [switch]$NoPlay,
    [switch]$Uninstall
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
$here = $PSScriptRoot
$tools = Join-Path (Split-Path (Split-Path $here -Parent) -Parent) 'tools'
$py = Join-Path $tools 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'io.github.luyzfernando08.tvstatic'
function Tv([string]$cmd) { & $py -I (Join-Path $tools 'tv.py') $cmd; if ($LASTEXITCODE) { throw "tv.py failed: $cmd" } }

if ($Uninstall) {
    Tv "pm uninstall $pkg"
    exit 0
}

Push-Location $here
# javac notes go to stderr, which Windows PowerShell 5.1 turns into errors under 'Stop'.
$ErrorActionPreference = 'Continue'
try {
    & .\gradlew.bat assembleRelease -q
} finally {
    Pop-Location
}
if ($LASTEXITCODE) { throw 'build failed (JDK 17 and Android SDK 34 needed; see launcher\README)' }
$ErrorActionPreference = 'Stop'
& $py -I (Join-Path $tools 'b64push.py') (Join-Path $here 'app\build\outputs\apk\release\app-release.apk') /data/local/tmp/tvstatic.apk | Out-Null
if ($LASTEXITCODE) { throw 'sending the APK failed; run this again' }
Tv 'pm install -r /data/local/tmp/tvstatic.apk; rm -f /data/local/tmp/tvstatic.apk'
# Android 10 and later let an app open a screen from the background only when it may draw over other apps.
Tv "appops set $pkg SYSTEM_ALERT_WINDOW allow"
# TCL's TclAppBoot keeps apps from starting at boot unless they may auto-start (AUTO_START on G08 builds,
# APP_AUTO_START on G10); elsewhere the op doesn't exist. write-settings saves it before the next reboot.
Tv "appops set $pkg AUTO_START allow 2>/dev/null; appops set $pkg APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; true" | Out-Null
# Starting the watcher once takes the app out of the stopped state, without which Android withholds BOOT_COMPLETED.
Tv "am start-foreground-service -n $pkg/.WatchService --ez play $(if ($NoPlay) { 'false' } else { 'true' })"
Write-Host 'Installed. The static plays whenever the screen turns on; its settings are on the TV Static card.'
