# Windows version of theme.sh: push a theme folder (config.json, and optionally a wallpaper.jpg or a
# wallpapers/ collection) to the launcher on the TV and restart Home. See themes/README.md for the keys.
#
# Usage: tools\theme.ps1 -TvHost 192.168.1.50 themes\aurora
#        tools\theme.ps1 -TvHost 192.168.1.50 -Reset
#
# One argument, and -Reset takes none: both together are refused rather than one of them dropped (below).
# Exit status as in theme.sh: 0 the theme is on the TV, or the reset ran; 1 the theme could not be pushed; 2 the
# command line is not one theme folder or -Reset, so nothing was sent.
param(
    [Parameter(Position = 0)][string]$Theme,
    [switch]$Reset,
    [string]$TvHost = $env:TV_HOST
)
# theme.sh judges its own command line here, and this is the same judgement: -Reset removes the theme from the
# TV and a folder pushes it, so `-Reset themes\aurora` used to take the reset branch and throw the folder away
# (the theme the reader named was removed instead of pushed), while `themes\aurora -Reset` pushed the theme and
# ignored the reset. Nothing is sent for either.
if ($Theme -and $Reset) {
    [Console]::Error.WriteLine("theme.ps1 takes one argument, a theme folder or -Reset: $Theme would not have been used, and -Reset removes the theme from the TV while a folder pushes it, so the two cannot share a command line. Nothing was sent to the TV.")
    exit 2
}
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
# Runs tools\tv.py or b64push.py and stops on a non-zero exit: 'Stop' only covers cmdlets, so a failed
# native call would otherwise fall through to "Theme ... is on the TV".
function Invoke-Tv {
    $out = & $py -I @args
    if ($LASTEXITCODE -ne 0) { throw "$($args[0]) $($args[1]) failed (exit code $LASTEXITCODE); the theme was not fully applied" }
    $out
}
$dir = "/sdcard/Android/data/$pkg/files"
$revision = [guid]::NewGuid().ToString('N')
# One shared recovery path verifies the helpers after Home restarts.

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
$clearWallpapers = "mkdir -p $dir/wallpapers 2>/dev/null; rm -f $dir/wallpapers/*.[jJ][pP][gG] $dir/wallpapers/*.[jJ][pP][eE][gG] $dir/wallpapers/*.[pP][nN][gG] $dir/wallpapers/*.[wW][eE][bB][pP] 2>/dev/null; content delete --uri $uri/wallpapers 2>/dev/null"

if ($Reset) {
    Invoke-Tv tv.py "mkdir -p $dir 2>/dev/null; $(Drop 'config.json'); $(Drop 'wallpaper.jpg'); $(Drop 'wallpapers/captions.txt'); $clearWallpapers; printf '%s' '$revision' > $tmp-theme_revision; $(Put 'theme_revision')" | Out-Null
    & $py -I theme_restart.py
    if ($LASTEXITCODE) { throw 'Home restart recovery failed' }
    Write-Host 'reset'
    exit 0
}
if (-not $Theme) { [Console]::Error.WriteLine('usage: theme.ps1 <theme folder> | -Reset'); exit 2 }
$config = Join-Path $Theme 'config.json'
if (-not (Test-Path $config)) { throw "no config.json in $Theme" }
try { $themeJson = Get-Content $config -Raw | ConvertFrom-Json } catch { throw "$config is not valid JSON" }
# Config.read() answers "no overlay" for anything that is not a JSON object, so an array, a number or a
# string is a theme the launcher drops - and it replaces the theme the set had with the built-in config.
# Judged here, before anything is pushed, so "is on the TV" cannot mean the opposite.
if ($themeJson -isnot [System.Management.Automation.PSCustomObject]) {
    throw "$config is not a JSON object of theme keys, so the launcher would ignore it and the TV would go back to its built-in config (themes/README.md). Nothing was sent to the TV."
}
# Every collection name is checked before anything changes on the TV, as in theme.sh: a name refused halfway
# through the push left the new config.json and part of the collection behind. b64push.py refuses '..' in a path.
$wallpapers = Join-Path $Theme 'wallpapers'
$images = @()
if (Test-Path $wallpapers -PathType Container) {
    $images = @(Get-ChildItem $wallpapers -File | Where-Object { $_.Extension -match '^\.(jpg|jpeg|png|webp)$' })
    foreach ($image in $images) {
        if ($image.Name -notmatch '^[A-Za-z0-9_.-]+$' -or $image.Name.Contains('..')) { throw "unsupported wallpaper filename: $($image.Name)" }
    }
}

# Tolerated, as in theme.sh: a TV that locks Android/data still takes the files through ThemeProvider.
Invoke-Tv tv.py "mkdir -p $dir 2>/dev/null; true" | Out-Null
Invoke-Tv tv.py --push $config "$tmp-config.json" | Out-Null
Invoke-Tv tv.py (Put 'config.json')
$wall = Join-Path $Theme 'wallpaper.jpg'
if (Test-Path $wall) { Invoke-Tv b64push.py $wall "$tmp-wallpaper.jpg"; Invoke-Tv tv.py (Put 'wallpaper.jpg') } else { Invoke-Tv tv.py (Drop 'wallpaper.jpg') | Out-Null }
Invoke-Tv tv.py $clearWallpapers | Out-Null
foreach ($image in $images) {
    Invoke-Tv b64push.py $image.FullName (Stage "wallpapers/$($image.Name)")
    Invoke-Tv tv.py (Put "wallpapers/$($image.Name)") | Out-Null
}
# The collection's names (wallpaper_captions); a theme without them leaves no stale names behind.
$captions = Join-Path $Theme 'wallpapers\captions.txt'
if (Test-Path $captions -PathType Leaf) {
    Invoke-Tv b64push.py $captions (Stage 'wallpapers/captions.txt')
    Invoke-Tv tv.py (Put 'wallpapers/captions.txt') | Out-Null
} else {
    Invoke-Tv tv.py (Drop 'wallpapers/captions.txt') | Out-Null
}
Invoke-Tv tv.py "printf '%s' '$revision' > $tmp-theme_revision; $(Put 'theme_revision')" | Out-Null
& $py -I theme_restart.py
if ($LASTEXITCODE) { throw 'Home restart recovery failed' }
Write-Host 'applied'
Write-Host "Theme $(Split-Path $Theme -Leaf) is on the TV. Undo with: tools\theme.ps1 -TvHost $TvHost -Reset"
