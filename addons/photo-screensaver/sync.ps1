# Windows version of sync.sh: make the photo screensaver the TV's screensaver and sync a folder of pictures
# and MP4 clips to it. Only new or changed files are sent and files no longer in the folder are removed from
# the TV, so a sync that broke off halfway is finished by running it again. Each picture stays up for
# -Seconds (20 by default). Install the app first with build-and-install.ps1.
#
# Usage: addons\photo-screensaver\sync.ps1 -TvHost 192.168.1.50                   # this folder's pictures\
#        addons\photo-screensaver\sync.ps1 -TvHost 192.168.1.50 D:\Pictures\tv     # any folder
#        addons\photo-screensaver\sync.ps1 -TvHost 192.168.1.50 -Seconds 30      # 30 s per picture
#        addons\photo-screensaver\sync.ps1 -TvHost 192.168.1.50 -IdleMinutes 5   # start it after 5 idle minutes
#        addons\photo-screensaver\sync.ps1 -TvHost 192.168.1.50 -Start           # and show it now
#        addons\photo-screensaver\sync.ps1 -TvHost 192.168.1.50 -Off             # previous screensaver back, pictures removed
param(
    [Parameter(Position = 0)][string]$Folder,
    [int]$Seconds = 20,
    [int]$IdleMinutes = 0,
    [switch]$Start,
    [switch]$Off,
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
if (-not $Folder) { $Folder = Join-Path $PSScriptRoot 'pictures' }
if (Test-Path $Folder) { $Folder = (Resolve-Path $Folder).Path }
Set-Location (Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) 'tools')
$py = 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'com.gathin.photodream'
$dream = "$pkg/.PhotoDream"
$dir = "/sdcard/Android/data/$pkg/files/pictures"
$uri = "content://$pkg.files/pictures"
$tmp = '/data/local/tmp/cyt-screensaver'
# The screensaver the TV had before the first run, so -Off can put it back.
$before = '/data/local/tmp/cyt-screensaver-before'
$types = '^\.(jpg|jpeg|png|webp|mp4)$'
function Tv([string]$cmd) { & $py -I tv.py $cmd; if ($LASTEXITCODE) { throw "tv.py failed: $cmd" } }

if ($IdleMinutes -lt 0 -or $IdleMinutes -gt 1440) { throw '-IdleMinutes takes 1 to 1440' }
if ($Seconds -lt 5 -or $Seconds -gt 3600) { throw '-Seconds takes 5 to 3600' }
if (-not ((Tv "pm path $pkg; true") -match 'package:')) { throw "the photo screensaver is not on the TV: run addons\photo-screensaver\build-and-install.ps1 first" }

if ($Off) {
    Tv "if [ -f $before ]; then { read c; read e; } < $before; if [ `"`$c`" = null ]; then settings delete secure screensaver_components >/dev/null; else settings put secure screensaver_components `"`$c`"; fi; [ `"`$e`" = null ] || settings put secure screensaver_enabled `"`$e`"; rm -f $before; fi; rm -f $dir/* 2>/dev/null; content delete --uri $uri 2>/dev/null; echo screensaver: `$(settings get secure screensaver_components)"
    exit 0
}

if (-not (Test-Path $Folder -PathType Container)) {
    throw "no folder $Folder. Create it and put your pictures (jpg, png, webp) and MP4 clips in it, or pass another folder."
}
$files = @(Get-ChildItem $Folder -File | Where-Object { $_.Extension -match $types } | Sort-Object Name)
foreach ($f in $files) {
    if ($f.Name -notmatch '^[A-Za-z0-9_.-]+$' -or $f.Name.StartsWith('.')) { throw "rename $($f.Name): use letters, digits, - _ and . only" }
    if ($f.Extension -ne '.mp4' -and $f.Length -gt 10MB) { throw "$($f.Name) is over 10 MB; the screensaver skips pictures that big" }
    if ($f.Length -gt 1GB) { throw "$($f.Name) is over 1 GB; the screensaver skips clips that big" }
}
if ($files.Count -gt 100) { throw "$($files.Count) files; the screensaver shows at most 100" }
if ($files.Count -eq 0) { Write-Warning "no pictures in ${Folder}: the screensaver stays black" }

# What the TV has now, by md5, so unchanged files are not sent again.
$onTv = @{}
(Tv "cd $dir 2>/dev/null && md5sum * 2>/dev/null; true") -split "`n" | ForEach-Object {
    if ($_ -match '^([0-9a-f]{32})\s+(\S+)$') { $onTv[$Matches[2]] = $Matches[1] }
}
$names = $files | ForEach-Object { $_.Name }
foreach ($name in @($onTv.Keys)) {
    if ($names -notcontains $name) {
        Tv "rm -f $dir/$name 2>/dev/null; content delete --uri $uri/$name 2>/dev/null" | Out-Null
        Write-Host "removed $name"
    }
}
$sent = 0
foreach ($f in $files) {
    $md5 = (Get-FileHash $f.FullName -Algorithm MD5).Hash.ToLower()
    if ($onTv[$f.Name] -eq $md5) { continue }
    Write-Host "sending $($f.Name) ($([math]::Round($f.Length / 1MB, 1)) MB)"
    $stage = "$tmp-$($f.Name)"
    & $py -I b64push.py $f.FullName $stage | Select-String 'md5'
    if ($LASTEXITCODE) { throw "sending $($f.Name) failed; run the script again to resume" }
    # Written through the app's FilesProvider so the app owns the file (on TCL G10 a copied one gives EACCES).
    Tv "rm -f $dir/$($f.Name) 2>/dev/null; content write --uri $uri/$($f.Name) < $stage 2>/dev/null || { mkdir -p $dir; cp $stage $dir/$($f.Name); }; rm -f $stage" | Out-Null
    $sent++
}

Tv "printf '%s' $Seconds > $tmp-seconds.txt; content write --uri content://$pkg.files/seconds.txt < $tmp-seconds.txt; rm -f $tmp-seconds.txt" | Out-Null
Tv "[ -f $before ] || { settings get secure screensaver_components; settings get secure screensaver_enabled; } > $before; settings put secure screensaver_components $dream; settings put secure screensaver_enabled 1" | Out-Null
if ($IdleMinutes -gt 0) { Tv "settings put system screen_off_timeout $($IdleMinutes * 60000)" | Out-Null }
$count = (Tv "ls $dir 2>/dev/null | wc -l").Trim()
Write-Host "Screensaver: Photos with $count file(s) on the TV, $Seconds s each ($sent sent). Undo with: addons\photo-screensaver\sync.ps1 -TvHost $TvHost -Off"
if ($Start) { Tv 'am start -n com.android.systemui/.Somnambulator >/dev/null; sleep 3; dumpsys dreams | grep mCurrentDreamName' }
