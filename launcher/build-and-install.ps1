# Windows version of build-and-install.sh: build the launcher APK and install it on the TV as the
# default home screen. Uses Android Studio's bundled JDK and the default SDK location unless
# JAVA_HOME / ANDROID_HOME are set.
#
# Usage: launcher\build-and-install.ps1 -TvHost 192.168.1.50 [-StockLauncher <package>|none]
#        The stock launcher is detected from the TV when -StockLauncher is not given.
#        [-InputKeyService]   remote's Input button opens the input switcher (Google TV), plus config.xml
#                             input_key_keycodes and app_keys; re-pass on every install
#        [-HomeKeyService]    remotes whose Home button sends KEYCODE_ALL_APPS (Xiaomi sticks)
#        [-RestoreKeyServices] grants WRITE_SECURE_SETTINGS for restore_key_services (helpers back after a force stop)
param(
    [string]$TvHost = $env:TV_HOST,
    [string]$StockLauncher = '',
    [switch]$InputKeyService,
    [switch]$HomeKeyService,
    [switch]$RestoreKeyServices
)
$ErrorActionPreference = 'Stop'
# Pipe plain UTF-8 to native commands; the default here prepends a BOM that corrupts the first command.
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
Set-Location $PSScriptRoot

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $jbr = Get-ChildItem 'C:\Program Files\Android' -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'jbr' } | Where-Object { Test-Path "$_\bin\java.exe" } | Select-Object -First 1
    if (-not $jbr) { throw 'Set JAVA_HOME to a JDK 17+ (Android Studio ships one in its jbr folder)' }
    $env:JAVA_HOME = $jbr
}
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
if (-not (Test-Path local.properties)) {
    "sdk.dir=$($sdk -replace '\\', '\\')" | Out-File -Encoding ascii local.properties
}

.\gradlew.bat assembleRelease -q --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed' }

$py = '..\tools\venv\Scripts\python.exe'
# Which app owns Home differs by build; ask the TV, but only accept a launcher we recognise (the answer
# can also be Android's chooser "android", the setup wizard, or another third-party launcher).
$knownStock = @('com.google.android.tvlauncher', 'com.google.android.apps.tv.launcherx', 'com.google.android.leanbacklauncher', 'com.mitv.tvhome')
if (-not $StockLauncher) {
    $homePkg = & $py -I ..\tools\tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | Select-Object -Last 1
    if ($LASTEXITCODE -ne 0 -or -not $homePkg) { throw 'could not ask the TV which launcher owns Home; check -TvHost and the debugging prompt, or pass -StockLauncher <package>' }
    $homePkg = "$homePkg".Trim().Split('/')[0]
    if ($knownStock -contains $homePkg) { $StockLauncher = $homePkg }
    elseif ($homePkg -eq 'com.gathin.tvhome') { $StockLauncher = 'none'; Write-Host 'Home already belongs to this launcher; nothing will be disabled.' }
    elseif ($homePkg -eq 'android') {
        # Android's chooser: launchers tie and no default is set (a restore followed by a re-applied list
        # ends here). Take the one known stock launcher among the HOME claimants, if there is exactly one.
        $claimants = (& $py -I ..\tools\tv.py 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME') -join "`n"
        if ($LASTEXITCODE -ne 0) { throw 'could not query Home claimants; stock launcher was left untouched' }
        $found = @($knownStock | Where-Object { $claimants -match ([regex]::Escape($_) + '/') })
        if ($found.Count -eq 1) { $StockLauncher = $found[0]; Write-Host "Home is Android's chooser; the stock launcher among the candidates is $StockLauncher." }
        else { throw "Home is Android's launcher chooser and $($found.Count) known stock launchers claim Home: pick a default on the TV first, or run again with -StockLauncher <package to disable> or -StockLauncher none." }
    }
    else { throw "Home currently resolves to '$homePkg', which is not a stock launcher this script knows (android = launcher chooser, com.google.android.tungsten.setupwraith = setup wizard, see README Troubleshooting; anything else = another launcher is the default). Run again with -StockLauncher <package to disable> or -StockLauncher none." }
}
Write-Host "Stock launcher: $StockLauncher"
$disableStock = if ($StockLauncher -eq 'none') { 'echo stock launcher left alone' } else { "pm disable-user --user 0 $StockLauncher" }

$apk = 'app\build\outputs\apk\release\app-release.apk'
& $py -I ..\tools\b64push.py $apk /data/local/tmp/tvhome.apk
if ($LASTEXITCODE -ne 0) { throw 'APK upload failed' }
$out = & $py -I ..\tools\tv.py 'pm install -r /data/local/tmp/tvhome.apk; rm -f /data/local/tmp/tvhome.apk'
Write-Host $out
if ($LASTEXITCODE -ne 0 -or "$out" -notmatch 'Success') { throw 'pm install failed; the stock launcher was left untouched' }
# Stop the instance Android restarted mid-update, which can come up non-occluding (see build-and-install.sh,
# #1101), and write back a key helper the force stop took off the list. Through stdin: Windows PowerShell 5.1
# drops the inner double quotes from a native argument.
's=$(settings get secure enabled_accessibility_services); a=$(settings get secure accessibility_enabled); am force-stop com.gathin.tvhome; case "$s" in *com.gathin.tvhome*) settings put secure enabled_accessibility_services ""; settings put secure enabled_accessibility_services "$s"; settings put secure accessibility_enabled "$a" ;; esac' |
    & $py -I ..\tools\tv.py --batch | Out-Null
# Android 9 only accepts a component here; Android 10+ also takes a bare package name.
$out = & $py -I ..\tools\tv.py 'cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity'
Write-Host $out
if ($LASTEXITCODE -ne 0 -or "$out" -match 'Error') { throw 'could not set the new launcher as home; the stock launcher was left untouched' }
# Start the launcher on its own, before the stock Home is touched (see build-and-install.sh, #239):
# in the batch a failed start only prints an Error line and the stock launcher would be disabled anyway.
# With -StockLauncher none the stock launcher stays Home (stock_home_redirect): a MAIN+HOME start would make this
# launcher the home process until a restart, and Android then blocks the stock launcher's button launches (#1274).
$startCmd = if ($StockLauncher -eq 'none') { 'am start -W -n com.gathin.tvhome/.MainActivity' }
            else { 'am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n com.gathin.tvhome/.MainActivity' }
$start = (& $py -I ..\tools\tv.py $startCmd) -join "`n"
Write-Host $start
if ($LASTEXITCODE -ne 0 -or $start -notmatch 'Status: ok' -or $start -match '(?m)^Error') { throw "the new launcher did not start (see above); $StockLauncher was left enabled" }
@(
    'pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION 2>/dev/null; echo location-granted',
    'cmd package compile -m speed -f com.gathin.tvhome',
    $disableStock,
    'cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity'
) | & $py -I ..\tools\tv.py --batch
# A failed batch is not fatal here: the stock launcher may already be disabled, so fall through to the
# Home check, which rolls back when Home is not ours.
if ($LASTEXITCODE -ne 0) { Write-Warning "tv.py --batch failed (exit code $LASTEXITCODE); checking where Home ended up" }

$homeQuery = 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
# Home's owner, or '' when the TV cannot be reached: $null.Trim() would throw past the rollback.
function Get-HomeNow {
    $ErrorActionPreference = 'Continue'
    $line = @(& $py -I ..\tools\tv.py $homeQuery 2>$null) | Where-Object { $_ -match '\S' } | Select-Object -Last 1
    if ($LASTEXITCODE -ne 0) { return '' }
    "$line".Trim()
}
# Put the stock launcher back and check that it took. A TV whose new Home does not start can only be
# rescued over ADB, so the commands also go into a local file (build-and-install.sh does the same).
function Undo-Handover {
    if ($StockLauncher -eq 'none') {
        Write-Warning 'Home is not the new launcher, and no stock launcher was disabled; nothing to roll back.'
        return $false
    }
    $ErrorActionPreference = 'Continue'
    & $py -I ..\tools\tv.py "pm enable --user 0 $StockLauncher" 2>$null | Out-Null
    $enableExit = $LASTEXITCODE
    if ($enableExit -ne 0) { Write-Warning "stock launcher enable failed (exit code $enableExit); checking recovery" }
    # set-home-activity needs pkg/activity on Android 9, so resolve the stock component on the TV.
    & $py -I ..\tools\tv.py "cmd package set-home-activity --user 0 `$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $StockLauncher | tail -n 1)" 2>$null | Out-Null
    $setHomeExit = $LASTEXITCODE
    if ($setHomeExit -ne 0) { Write-Warning "stock Home restore failed (exit code $setHomeExit); checking recovery" }
    $back = Get-HomeNow
    if ($enableExit -eq 0 -and $setHomeExit -eq 0 -and $back -like "$StockLauncher/*") { Write-Warning "$StockLauncher owns Home again."; return $true }
    $homeDir = if ($env:USERPROFILE) { $env:USERPROFILE } elseif ($env:HOME) { $env:HOME } else { (Get-Location).Path }
    $rec = Join-Path $homeDir ("cleanyourtv-handover-" + ($TvHost -replace '[^A-Za-z0-9._-]', '_') + '.txt')
    $lines = @(
        "# cleanyourtv: the launcher handover on $TvHost did not finish. From the repo root:",
        "tools\venv\Scripts\python.exe tools\tv.py 'pm enable --user 0 $StockLauncher'   # with `$env:TV_HOST='$TvHost'",
        "tools\venv\Scripts\python.exe tools\tv.py 'cmd package set-home-activity --user 0 `$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $StockLauncher | tail -n 1)'",
        "# then check: tools\venv\Scripts\python.exe tools\tv.py '$homeQuery'"
    )
    try { $lines | Out-File -Encoding ascii $rec } catch { $rec = '' }
    Write-Warning "recovery is incomplete; Home still resolves to '$(if ($back) { $back } else { 'nothing' })'."
    if ($rec) { Write-Warning "The commands that finish it are in $rec; the TV still needs ADB for them." }
    else { Write-Warning "Re-run once the TV answers: pm enable --user 0 $StockLauncher, then set its Home activity." }
    return $false
}
# Order matters, see build-and-install.sh: start the new launcher, disable the stock one, set the
# preference last, then check where Home ended up and put the stock launcher back if it is not ours.
$homeNow = Get-HomeNow
if ($homeNow -notlike 'com.gathin.tvhome/*') {
    $reason = if ($homeNow) { "Home resolves to '$homeNow', not to the new launcher" } else { 'the TV stopped answering while Home was being switched, so the outcome is unknown' }
    $restored = Undo-Handover
    $hint = if ($homeNow) { ' On Google TV this usually means com.google.android.tungsten.setupwraith also claims Home: add it to your debloat list (see README, Troubleshooting), apply it, then run this script again.' } else { '' }
    throw "$reason; $(if ($restored) { "$StockLauncher was re-enabled." } else { 'recovery is incomplete (see above).' })$hint"
}
Write-Host "Home now resolves to $homeNow"
# Optional accessibility services, appended to enabled_accessibility_services without dropping others.
function Enable-Service([string]$Name) {
    $svc = "com.gathin.tvhome/.$Name"
    # Through stdin: Windows PowerShell 5.1 drops the inner double quotes from a native argument, and the
    # TV's shell then fails with "no closing quote" while the service stays off.
    # TCL names the op AUTO_START or APP_AUTO_START; without write-settings the grant is lost at the next reboot.
    ('cmd appops set com.gathin.tvhome AUTO_START allow 2>/dev/null; cmd appops set com.gathin.tvhome APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; s=$(settings get secure enabled_accessibility_services); case "$s" in *' + $Name + '*) settings put secure enabled_accessibility_services ""; settings put secure accessibility_enabled 0; sleep 1; settings put secure enabled_accessibility_services "$s" ;; null|"") settings put secure enabled_accessibility_services ' + $svc + ' ;; *) settings put secure enabled_accessibility_services "$s:' + $svc + '" ;; esac; settings put secure accessibility_enabled 1; echo ' + $Name + ' enabled') |
        & $py -I ..\tools\tv.py --batch
    if ($LASTEXITCODE -ne 0) { throw "could not enable $Name (tv.py exit code $LASTEXITCODE); Home is already the new launcher" }
}
if ($InputKeyService) { Enable-Service 'InputKeyService' }
if ($HomeKeyService) { Enable-Service 'HomeKeyService' }
if ($RestoreKeyServices) {
    'pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS && echo restore-granted' | & $py -I ..\tools\tv.py --batch
}
Write-Host 'Installed. Press Home on the remote to see it.'
