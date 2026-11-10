# Windows version of install.sh: build Tatami, install it on the TV, make it the TV's screensaver and open it once.
#
# Usage: .\install.ps1 -TvHost 192.168.1.50               build, install, make it the screensaver, show it
#        .\install.ps1 -TvHost 192.168.1.50 -NoShow       the same, without opening Tatami at the end
#        .\install.ps1 -TvHost 192.168.1.50 -SkipBuild    install the APK already built (tools/docker-build.sh addons/tatami)
#        .\install.ps1 -TvHost 192.168.1.50 -Uninstall    previous screensaver back, app removed
# $env:TV_HOST and $env:SKIP_BUILD = '1' work as well.
#
# In a cleanyourtv checkout this is addons\tatami\install.ps1 and uses that checkout's tools\. Anywhere else it uses
# the cleanyourtv checkout in $env:CLEANYOURTV (default: ..\cleanyourtv next to this folder).
param(
    [string]$TvHost = $env:TV_HOST,
    [switch]$Uninstall,
    [switch]$NoShow,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
if ($env:SKIP_BUILD) { $SkipBuild = [switch]$true }
$here = $PSScriptRoot
$tools = Join-Path (Split-Path (Split-Path $here -Parent) -Parent) 'tools'
if (-not (Test-Path (Join-Path $tools 'tv.py'))) {
    if ($env:CLEANYOURTV) { $cyt = $env:CLEANYOURTV } else { $cyt = Join-Path (Split-Path $here -Parent) 'cleanyourtv' }
    $tools = Join-Path $cyt 'tools'
}
if (-not (Test-Path (Join-Path $tools 'tv.py'))) {
    throw "no cleanyourtv tools at ${tools}: clone https://github.com/Gathin23/cleanyourtv next to this folder, or set `$env:CLEANYOURTV to your checkout"
}
$py = Join-Path $tools 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$tvpy = Join-Path $tools 'tv.py'
$pkg = 'io.github.pbanddev.tatami'
$dream = "$pkg/.FloorPlanDream"
# The screensaver the TV had before the first run, so -Uninstall can put it back.
$before = '/data/local/tmp/cyt-tatami-before'
function Tv([string]$cmd) { & $py -I $tvpy $cmd; if ($LASTEXITCODE) { throw "tv.py failed: $cmd" } }

if ($Uninstall) {
    # Put the screensaver from before the first run back, then remove the app.
    Tv "if [ -f $before ]; then { read c; read e; } < $before; if [ `"`$c`" = null ]; then settings delete secure screensaver_components >/dev/null; else settings put secure screensaver_components `"`$c`"; fi; [ `"`$e`" = null ] || settings put secure screensaver_enabled `"`$e`"; rm -f $before; fi; pm uninstall $pkg; echo screensaver: `$(settings get secure screensaver_components)"
    exit 0
}

if (-not $SkipBuild) {
    Push-Location $here
    try {
        & .\gradlew.bat assembleRelease -q
        if ($LASTEXITCODE) { throw 'build failed (JDK 17 and Android SDK 34 needed; or build with tools/docker-build.sh addons/tatami and pass -SkipBuild)' }
    } finally { Pop-Location }
}
$apk = Join-Path $here 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apk)) { throw "no ${apk}: build it first (gradlew.bat assembleRelease, or tools/docker-build.sh addons/tatami)" }

# Wake first: on some sets an install waits while a screensaver is up.
Tv 'input keyevent WAKEUP' | Out-Null
& $py -I (Join-Path $tools 'b64push.py') $apk /data/local/tmp/tatami.apk | Out-Null
if ($LASTEXITCODE) { throw 'sending the APK failed' }
$out = (Tv 'pm install -r /data/local/tmp/tatami.apk; rm -f /data/local/tmp/tatami.apk') -join "`n"
if ($out -notmatch 'Success') { throw "install failed: Tatami is not on the TV. $out" }
# Compiled ahead of time, so the first frames are smooth. Not every Android version has the command.
try { & $py -I $tvpy "cmd package compile -m speed -f $pkg" | Out-Null } catch { }
Tv "[ -f $before ] || { settings get secure screensaver_components; settings get secure screensaver_enabled; } > $before; settings put secure screensaver_components $dream; settings put secure screensaver_enabled 1" | Out-Null
# TCL's TclAppBoot keeps sideloaded apps from starting, a screensaver too, unless they may auto-start (AUTO_START on
# G08 builds, APP_AUTO_START on G10); elsewhere the op doesn't exist. write-settings saves it before the next reboot.
Tv "appops set $pkg AUTO_START allow 2>/dev/null; appops set $pkg APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; true" | Out-Null

# When the screensaver starts is the TV's own setting. Say what it is and how to change it; never change it here.
$ms = ''
try { $ms = "$((Tv 'settings get system screen_off_timeout') | Select-Object -Last 1)".Trim() } catch { }
[long]$n = 0
if ([long]::TryParse($ms, [ref]$n)) {
    if ($n -lt 0 -or $n -ge 2147483647) {
        Write-Host 'The TV never starts its screensaver by itself (Settings > Device Preferences > Screen saver > When to start).'
    } else {
        $min = [long][math]::Ceiling($n / 60000)
        if ($min -ge 60 -and $min % 60 -eq 0) { $when = "$($min / 60) h" } else { $when = "$min min" }
        Write-Host "The screensaver starts after $when with no key press (Settings > Device Preferences > Screen saver > When to start)."
    }
    Write-Host "To make it 5 min, from your cleanyourtv checkout: `$env:TV_HOST = '$TvHost'; tools\venv\Scripts\python.exe -I tools\tv.py 'settings put system screen_off_timeout 300000'"
}

if (-not $NoShow) { Tv "am start -n $pkg/.PreviewActivity" | Out-Null }
Write-Host "Installed: Tatami is the screensaver. Undo with: $PSCommandPath -Uninstall"
