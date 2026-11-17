# Windows version of build-and-install.sh: build Koi Pond, install it on the TV and make it the screensaver.
#
# Usage: addons\koi-pond\build-and-install.ps1 -TvHost 192.168.1.50
#        addons\koi-pond\build-and-install.ps1 -TvHost 192.168.1.50 -Start
#        addons\koi-pond\build-and-install.ps1 -TvHost 192.168.1.50 -SkipBuild   # the APK tools/docker-build.sh built
#        addons\koi-pond\build-and-install.ps1 -TvHost 192.168.1.50 -Uninstall
param(
    [string]$TvHost = $env:TV_HOST,
    [switch]$Uninstall,
    [switch]$SkipBuild,
    [switch]$Start
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
$here = $PSScriptRoot
$tools = Join-Path (Split-Path (Split-Path $here -Parent) -Parent) 'tools'
$py = Join-Path $tools 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'io.github.nottoobright.koipond'
$dream = "$pkg/.PondDream"
$before = '/data/local/tmp/cyt-koipond-before'
function Tv([string]$cmd) { & $py -I (Join-Path $tools 'tv.py') $cmd; if ($LASTEXITCODE) { throw "tv.py failed: $cmd" } }

if ($Uninstall) {
    # Put the screensaver from before the first run back, then remove the app.
    Tv "if [ -f $before ]; then { read c; read e; } < $before; if [ `"`$c`" = null ]; then settings delete secure screensaver_components >/dev/null; else settings put secure screensaver_components `"`$c`"; fi; [ `"`$e`" = null ] || settings put secure screensaver_enabled `"`$e`"; rm -f $before; fi; pm uninstall $pkg"
    exit 0
}

if (-not $SkipBuild) {
    Push-Location $here
    try {
        & .\gradlew.bat assembleRelease -q
        if ($LASTEXITCODE) { throw 'build failed (JDK 17 and Android SDK 34 needed, or build in Docker: tools/docker-build.sh addons/koi-pond, then -SkipBuild)' }
    } finally { Pop-Location }
}
$apk = Join-Path $here 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apk)) { throw "no $apk; build it first (tools/docker-build.sh addons/koi-pond)" }
& $py -I (Join-Path $tools 'b64push.py') $apk /data/local/tmp/koipond.apk | Out-Null
if ($LASTEXITCODE) { throw 'sending the APK failed' }
Tv 'pm install -r /data/local/tmp/koipond.apk; rm -f /data/local/tmp/koipond.apk'
Tv "[ -f $before ] || { settings get secure screensaver_components; settings get secure screensaver_enabled; } > $before; settings put secure screensaver_components $dream; settings put secure screensaver_enabled 1"
Write-Host 'Installed: Koi Pond is the screensaver. Undo with: addons\koi-pond\build-and-install.ps1 -Uninstall'
if ($Start) { Tv 'am start -n com.android.systemui/.Somnambulator' }
