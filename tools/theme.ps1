# Windows version of theme.sh: push a theme folder (config.json, and optionally a wallpaper.jpg or a
# wallpapers/ collection) to the launcher on the TV and restart Home. See themes/README.md for the keys.
#
# Usage: tools\theme.ps1 -TvHost 192.168.1.50 themes\aurora
#        tools\theme.ps1 -TvHost 192.168.1.50 -Reset
param(
    [Parameter(Position = 0)][string]$Theme,
    [switch]$Reset,
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
# Resolve the theme folder against the caller's directory before moving into tools\.
if ($Theme) { $Theme = (Resolve-Path $Theme).Path }
Set-Location $PSScriptRoot
$py = 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'com.gathin.tvhome'
$dir = "/sdcard/Android/data/$pkg/files"
$revision = [guid]::NewGuid().ToString('N')
# Restart Home so it rereads the theme. Android drops a force-stopped app's accessibility services from the
# enabled list (asynchronously, so wait a second), which would switch off InputKeyService/HomeKeyService;
# put the list back if it had ours. Writing the same list back is not enough: the force-stop also turns
# accessibility_enabled off, and the service only binds again once the list changes. So restart first, then
# clear the list, switch accessibility off (some TVs keep the service "crashed" and unbound otherwise), write
# the list back and switch accessibility on again. On some TVs (Xiaomi MiTV-MOOR2, Android 11) Android has already
# emptied the list and switched accessibility off by then, so clearing it changes nothing and the services stay
# crashed: write the list back first, so the clear is a change (the same order as theme.sh). When the launcher is
# already Home it comes back through the Home
# intent, since `am start -n` from the shell leaves a second MainActivity behind the Home one; before the handover
# it is started by name as before.
$restart = "a=`$(settings get secure enabled_accessibility_services); am force-stop $pkg; case `"`$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1)`" in $pkg/*) am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null ;; *) am start -n $pkg/.MainActivity >/dev/null ;; esac; case `"`$a`" in *$pkg*) sleep 1; settings put secure enabled_accessibility_services `"`$a`"; settings put secure accessibility_enabled 1; sleep 1; settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; sleep 1; settings put secure enabled_accessibility_services `"`$a`"; settings put secure accessibility_enabled 1 ;; esac"

# Some firmware (TCL G07, Android 11) locks the shell out of Android/data. Files are staged in
# /data/local/tmp and copied in, or written through the launcher's ThemeProvider when that is refused.
# The provider has to be tried first: on Android 14 the shell can write into Android/data, so cp "succeeds",
# but the file it creates belongs to the shell (uid 2000) and the launcher then gets EACCES opening it, which
# silently ignores the theme whenever the file did not exist yet - a fresh install, or after a reinstall.
# A provider write creates the file as the launcher, and it cannot truncate a file the shell owns either, so
# remove our copy first. cp stays as the fallback for firmware without the provider.
$uri = "content://$pkg.theme"
$tmp = '/data/local/tmp/cyt-theme'
# A file's staging path; wallpapers/a.jpg is staged as $tmp-wallpapers-a.jpg.
function Stage($name) { "$tmp-$($name -replace '/', '-')" }
# Android 8 and 9 have no `content write`: they print "[ERROR] Unsupported operation: write" but exit 0, so
# that message counts as a failure and cp runs.
function Put($name) { $t = Stage $name; "rm -f $dir/$name 2>/dev/null; { o=`$(content write --uri $uri/$name < $t 2>&1) && case `"`$o`" in *'Unsupported operation'*) false ;; esac; } || cp $t $dir/$name 2>/dev/null; rm -f $t" }
function Drop($name) { "rm -f $dir/$name 2>/dev/null; content delete --uri $uri/$name 2>/dev/null" }

# The collection a theme can push. A push empties it before writing the theme's own pictures, and -Reset has to
# empty it too: the launcher's wallpaper picker lists that folder itself, and an on-TV pick of one of its
# pictures is read back from it, so a reset that left the pictures behind still shows the theme it just removed.
# Both paths use this one string (the same one theme.sh has).
$clearWallpapers = "mkdir -p $dir/wallpapers 2>/dev/null; rm -f $dir/wallpapers/*.jpg $dir/wallpapers/*.jpeg $dir/wallpapers/*.png $dir/wallpapers/*.webp $dir/wallpapers/*.JPG $dir/wallpapers/*.JPEG $dir/wallpapers/*.PNG $dir/wallpapers/*.WEBP 2>/dev/null; content delete --uri $uri/wallpapers 2>/dev/null"

if ($Reset) {
    & $py -I tv.py "mkdir -p $dir 2>/dev/null; $(Drop 'config.json'); $(Drop 'wallpaper.jpg'); $(Drop 'wallpapers/captions.txt'); $clearWallpapers; printf '%s' '$revision' > $tmp-theme_revision; $(Put 'theme_revision'); $restart; echo reset"
    exit 0
}
if (-not $Theme) { throw 'usage: theme.ps1 <theme folder> | -Reset' }
$config = Join-Path $Theme 'config.json'
if (-not (Test-Path $config)) { throw "no config.json in $Theme" }
try { Get-Content $config -Raw | ConvertFrom-Json | Out-Null } catch { throw "$config is not valid JSON" }

& $py -I tv.py "mkdir -p $dir 2>/dev/null" | Out-Null
& $py -I tv.py --push $config "$tmp-config.json" | Out-Null
& $py -I tv.py (Put 'config.json')
$wall = Join-Path $Theme 'wallpaper.jpg'
if (Test-Path $wall) { & $py -I b64push.py $wall "$tmp-wallpaper.jpg"; & $py -I tv.py (Put 'wallpaper.jpg') } else { & $py -I tv.py (Drop 'wallpaper.jpg') | Out-Null }
& $py -I tv.py $clearWallpapers | Out-Null
$wallpapers = Join-Path $Theme 'wallpapers'
if (Test-Path $wallpapers -PathType Container) {
    Get-ChildItem $wallpapers -File | Where-Object { $_.Extension -match '^\.(jpg|jpeg|png|webp)$' } | ForEach-Object {
        if ($_.Name -notmatch '^[A-Za-z0-9_.-]+$') { throw "unsupported wallpaper filename: $($_.Name)" }
        & $py -I b64push.py $_.FullName (Stage "wallpapers/$($_.Name)")
        & $py -I tv.py (Put "wallpapers/$($_.Name)") | Out-Null
    }
}
# The collection's names (wallpaper_captions); a theme without them leaves no stale names behind.
$captions = Join-Path $Theme 'wallpapers\captions.txt'
if (Test-Path $captions -PathType Leaf) {
    & $py -I b64push.py $captions (Stage 'wallpapers/captions.txt')
    & $py -I tv.py (Put 'wallpapers/captions.txt') | Out-Null
} else {
    & $py -I tv.py (Drop 'wallpapers/captions.txt') | Out-Null
}
& $py -I tv.py "printf '%s' '$revision' > $tmp-theme_revision; $(Put 'theme_revision')" | Out-Null
& $py -I tv.py "$restart; echo applied"
Write-Host "Theme $(Split-Path $Theme -Leaf) is on the TV. Undo with: tools\theme.ps1 -TvHost $TvHost -Reset"
