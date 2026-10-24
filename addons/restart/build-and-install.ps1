# Windows version of build-and-install.sh: build Restart, install it on the TV and switch on its accessibility
# service (it opens the power menu and presses Restart, only after you chose Restart in the app).
#
# Usage: addons\restart\build-and-install.ps1 -TvHost 192.168.1.50
#        addons\restart\build-and-install.ps1 -TvHost 192.168.1.50 -Uninstall
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
$pkg = 'io.github.jemychao.restart'
$svc = "$pkg/$pkg.RestartService"
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
& $py -I (Join-Path $tools 'b64push.py') (Join-Path $here 'app\build\outputs\apk\release\app-release.apk') /data/local/tmp/restart.apk | Out-Null
if ($LASTEXITCODE) { throw 'sending the APK failed; run this again' }
Tv 'pm install -r /data/local/tmp/restart.apk; rm -f /data/local/tmp/restart.apk'
# Take the service out first: after an update Android 9 can keep it bound to the replaced APK.
Tv ('s=$(settings get secure enabled_accessibility_services); s=$(echo :$s: | sed s#:SVC:#:#); s=${s#:}; s=${s%:}; if [ -z $s ] || [ $s = null ]; then settings delete secure enabled_accessibility_services; s=SVC; else settings put secure enabled_accessibility_services $s; s=$s:SVC; fi; sleep 1; settings put secure enabled_accessibility_services $s; settings put secure accessibility_enabled 1'.Replace('SVC', $svc))
Write-Host 'Installed. Press Home and answer Show card for the Restart card, or open Restart from the apps.'
