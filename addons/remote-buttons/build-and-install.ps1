# Windows version of build-and-install.sh: build Remote buttons, install it on the TV, let it read the key log and
# open apps over other apps, and switch its service on. Pick the buttons afterwards on the Remote buttons card.
#
# Usage: addons\remote-buttons\build-and-install.ps1 -TvHost 192.168.1.50
#        $env:SKIP_BUILD = '1'; addons\remote-buttons\build-and-install.ps1 -TvHost 192.168.1.50   # APK built in Docker
#        addons\remote-buttons\build-and-install.ps1 -TvHost 192.168.1.50 -Uninstall
param(
    [string]$TvHost = $env:TV_HOST,
    [switch]$Uninstall
)
$ErrorActionPreference = 'Stop'
# Pipe plain UTF-8 to native commands; the default here prepends a BOM that corrupts the first command.
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
$here = $PSScriptRoot
$tools = Join-Path (Split-Path (Split-Path $here -Parent) -Parent) 'tools'
$py = Join-Path $tools 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'io.github.knochango.remotebuttons'
$svc = "$pkg/.KeyLogService"
function Tv([string]$cmd) { & $py -I (Join-Path $tools 'tv.py') $cmd; if ($LASTEXITCODE) { throw "tv.py failed: $cmd" } }
# Through stdin: Windows PowerShell 5.1 drops the inner double quotes from a native argument.
function TvBatch([string]$cmd) { $cmd | & $py -I (Join-Path $tools 'tv.py') --batch | Out-Null; if ($LASTEXITCODE) { throw 'tv.py --batch failed' } }

# Takes the service off the accessibility list and leaves every other service on it.
$serviceOff = 's=$(settings get secure enabled_accessibility_services); n=$(echo "$s" | tr ":" "\n" | grep -v "^' + $pkg + '/" | grep -v "^null$" | paste -sd: -); settings put secure enabled_accessibility_services "$n"; [ -z "$n" ] && settings put secure accessibility_enabled 0; true'

if ($Uninstall) {
    TvBatch $serviceOff
    Tv "pm uninstall $pkg"
    exit 0
}

if ($env:SKIP_BUILD -ne '1') {
    Push-Location $here
    # javac notes go to stderr, which Windows PowerShell 5.1 turns into errors under 'Stop'.
    $ErrorActionPreference = 'Continue'
    try {
        & .\gradlew.bat assembleRelease -q
    } finally {
        Pop-Location
    }
    if ($LASTEXITCODE) { throw 'build failed (JDK 17 and Android SDK 34 needed; or build in Docker: tools\docker-build.ps1 -Project addons\remote-buttons, then SKIP_BUILD=1)' }
    $ErrorActionPreference = 'Stop'
}
$apk = Join-Path $here 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apk)) { throw "no $apk; build it first" }
& $py -I (Join-Path $tools 'b64push.py') $apk /data/local/tmp/remotebuttons.apk | Out-Null
if ($LASTEXITCODE) { throw 'sending the APK failed; run this again' }
Tv 'pm install -r /data/local/tmp/remotebuttons.apk; rm -f /data/local/tmp/remotebuttons.apk'
# The window manager's key lines are how a press is seen; without this logcat shows the app only its own lines.
Tv "pm grant $pkg android.permission.READ_LOGS"
# Android 10 and later let an app open another from the background only when it may draw over other apps.
Tv "appops set $pkg SYSTEM_ALERT_WINDOW allow"
# TCL's TclAppBoot keeps apps from starting at boot unless they may auto-start (AUTO_START on G08 builds,
# APP_AUTO_START on G10); elsewhere the op doesn't exist. write-settings saves it before the next reboot.
Tv "appops set $pkg AUTO_START allow 2>/dev/null; appops set $pkg APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; true" | Out-Null
# Written straight after an install, the service is dropped from the list before Android has registered it.
Start-Sleep 5
TvBatch ('s=$(settings get secure enabled_accessibility_services); case "$s" in *' + $svc + '*) ;; null|"") settings put secure enabled_accessibility_services ' + $svc + ' ;; *) settings put secure enabled_accessibility_services "$s:' + $svc + '" ;; esac; settings put secure accessibility_enabled 1')
Write-Host 'Installed. Open the Remote buttons card, choose Add a button and press the button on the remote.'
