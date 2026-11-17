# Windows version of build-and-install.sh: build Open on wake, install it on the TV, let it open apps over
# other apps and start the screen watcher.
#
# Usage: addons\wake-app\build-and-install.ps1 -TvHost 192.168.1.50
#        addons\wake-app\build-and-install.ps1 -TvHost 192.168.1.50 -App com.google.android.youtube.tv
#        addons\wake-app\build-and-install.ps1 -TvHost 192.168.1.50 -App com.google.android.youtube.tv -Link 'https://www.youtube.com/watch?v=...'
#        addons\wake-app\build-and-install.ps1 -TvHost 192.168.1.50 -Uninstall
# -App and -Link set the choice from here; leave them out to pick the app on the TV (the Open on wake card).
param(
    [string]$TvHost = $env:TV_HOST,
    [string]$App,
    [string]$Link,
    [switch]$Uninstall
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
$here = $PSScriptRoot
$tools = Join-Path (Split-Path (Split-Path $here -Parent) -Parent) 'tools'
$py = Join-Path $tools 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'io.github.luyzfernando08.wakeapp'
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
& $py -I (Join-Path $tools 'b64push.py') (Join-Path $here 'app\build\outputs\apk\release\app-release.apk') /data/local/tmp/wakeapp.apk | Out-Null
if ($LASTEXITCODE) { throw 'sending the APK failed; run this again' }
Tv 'pm install -r /data/local/tmp/wakeapp.apk; rm -f /data/local/tmp/wakeapp.apk'
# Android 10 and later let an app open another from the background only when it may draw over other apps.
Tv "appops set $pkg SYSTEM_ALERT_WINDOW allow"
# TCL's TclAppBoot keeps apps from starting at boot unless they may auto-start (AUTO_START on G08 builds,
# APP_AUTO_START on G10); elsewhere the op doesn't exist. write-settings saves it before the next reboot.
Tv "appops set $pkg AUTO_START allow 2>/dev/null; appops set $pkg APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; true" | Out-Null
# Starting the watcher once takes the app out of the stopped state, without which Android withholds BOOT_COMPLETED.
$extras = ''
if ($App) { $extras += " --es app '$App'" }
if ($Link) { $extras += " --es link '$Link'" }
Tv "am start-foreground-service -n $pkg/.WatchService$extras"
Write-Host 'Installed. Pick the app on the Open on wake card (or pass -App).'
