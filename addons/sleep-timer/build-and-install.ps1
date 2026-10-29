# Windows version of build-and-install.sh: build Sleep Timer, install it on the TV and switch on its
# accessibility service (only the lock-screen action, which puts the TV to standby) and the right to open its
# warning over other apps.
#
# Usage: addons\sleep-timer\build-and-install.ps1 -TvHost 192.168.1.50
#        addons\sleep-timer\build-and-install.ps1 -TvHost 192.168.1.50 -Uninstall
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
$pkg = 'io.github.luyzfernando08.sleeptimer'
$svc = "$pkg/$pkg.SleepService"
function Tv([string]$cmd) { & $py -I (Join-Path $tools 'tv.py') $cmd; if ($LASTEXITCODE) { throw "tv.py failed: $cmd" } }

# No double quotes inside these commands: Windows PowerShell 5.1 mangles them on the way to python, and the
# values have no spaces.
if ($Uninstall) {
    Tv ('s=$(settings get secure enabled_accessibility_services); s=$(echo :$s: | sed s#:SVC:#:#); s=${s#:}; s=${s%:}; if [ -z $s ]; then settings delete secure enabled_accessibility_services; else settings put secure enabled_accessibility_services $s; fi; pm uninstall PKG'.Replace('SVC', $svc).Replace('PKG', $pkg))
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
& $py -I (Join-Path $tools 'b64push.py') (Join-Path $here 'app\build\outputs\apk\release\app-release.apk') /data/local/tmp/sleeptimer.apk | Out-Null
if ($LASTEXITCODE) { throw 'sending the APK failed; run this again' }
Tv 'pm install -r /data/local/tmp/sleeptimer.apk; rm -f /data/local/tmp/sleeptimer.apk'
Tv ('s=$(settings get secure enabled_accessibility_services); case :$s: in *:SVC:*) ;; *) if [ -z $s ] || [ $s = null ]; then s=SVC; else s=$s:SVC; fi; settings put secure enabled_accessibility_services $s;; esac; settings put secure accessibility_enabled 1'.Replace('SVC', $svc))
Tv "appops set $pkg SYSTEM_ALERT_WINDOW allow; appops set $pkg SCHEDULE_EXACT_ALARM allow 2>/dev/null; true"
# TCL's TclAppBoot refuses to bind SleepService unless the app may auto-start; write-settings keeps it past a reboot.
Tv "appops set $pkg AUTO_START allow 2>/dev/null; appops set $pkg APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; true" | Out-Null
Write-Host 'Installed. Pick a time on the Sleep Timer card.'
