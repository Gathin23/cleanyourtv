# Windows version of theme-catalog.sh: put theme folders on the TV for the Theme row in Launcher settings, and
# keep this TV's own keys apart from every theme. See themes/README.md, "Switching themes on the TV".
#
# Usage: tools\theme-catalog.ps1 -TvHost 192.168.1.50 themes\aurora themes\glass
#        tools\theme-catalog.ps1 -TvHost 192.168.1.50 -Device my-tv\device.json   # keys laid over every theme
#        tools\theme-catalog.ps1 -TvHost 192.168.1.50 -Device none                # remove device.json
#        tools\theme-catalog.ps1 -TvHost 192.168.1.50 -Clear                      # remove every catalog theme
#
# Exit status as in theme-catalog.sh: 0 done; 1 a push or the restart failed on the TV; 2 nothing was sent.
param(
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)][string[]]$Themes,
    [string]$Device,
    [switch]$Clear,
    [string]$TvHost = $env:TV_HOST
)
$usage = 'usage: theme-catalog.ps1 <theme folder>... | -Device <device.json>|none | -Clear'
$modes = @(($Themes.Count -gt 0), [bool]$Device, [bool]$Clear) | Where-Object { $_ }
if (@($modes).Count -ne 1 -or ($Themes | Where-Object { $_.StartsWith('-') })) { [Console]::Error.WriteLine($usage); exit 2 }
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) {
    [Console]::Error.WriteLine('pass -TvHost <tv ip> or set $env:TV_HOST. Nothing was sent to the TV.')
    exit 2
}
$env:TV_HOST = $TvHost
# Resolve folders and files against the caller's directory before moving into tools\.
$Themes = @($Themes | ForEach-Object { if (Test-Path -LiteralPath $_) { (Resolve-Path -LiteralPath $_).Path.TrimEnd('\', '/') } else { $_ } })
if ($Device -and $Device -ne 'none' -and (Test-Path -LiteralPath $Device)) { $Device = (Resolve-Path -LiteralPath $Device).Path }
Set-Location $PSScriptRoot
$py = 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { $py = 'venv/bin/python' }
if (-not (Test-Path $py)) {
    [Console]::Error.WriteLine('run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt. Nothing was sent to the TV.')
    exit 2
}
function Invoke-Tv {
    $out = & $py -I @args
    if ($LASTEXITCODE -ne 0) { throw "$($args[0]) $($args[1]) failed (exit code $LASTEXITCODE)" }
    $out
}
$pkg = 'com.gathin.tvhome'
$dir = "/sdcard/Android/data/$pkg/files"
$uri = "content://$pkg.theme"
$tmp = '/data/local/tmp/cyt-catalog'
# The same write path as theme.ps1: ThemeProvider first, so the file is the launcher's own, and cp as the fallback.
function Stage($name) { "$tmp-$($name -replace '/', '-')" }
function Put($name) { $t = Stage $name; $d = "$dir/$name" -replace '/[^/]*$', ''; "rm -f $dir/$name 2>/dev/null; { o=`$(content write --uri $uri/$name < $t 2>&1) && case `"`$o`" in *'Unsupported operation'*|*'Error while accessing provider'*) false ;; esac; } || { mkdir -p $d 2>/dev/null; cp $t $dir/$name 2>/dev/null; }; rm -f $t" }
function Drop($name) { "content delete --uri $uri/$name >/dev/null 2>&1; rm -rf $dir/$name 2>/dev/null; true" }
function Test-JsonObject($path) {
    if (-not (Test-Path -LiteralPath $path)) { return $false }
    try { return (Get-Content -LiteralPath $path -Raw | ConvertFrom-Json) -is [System.Management.Automation.PSCustomObject] }
    catch { return $false }
}

if ($Clear) {
    Invoke-Tv tv.py (Drop 'themes') | Out-Null
    Write-Host 'Removed the theme catalog. The active theme is unchanged.'
    exit 0
}

if ($Device) {
    if ($Device -eq 'none') {
        Invoke-Tv tv.py (Drop 'device.json') | Out-Null
    } else {
        if (-not (Test-JsonObject $Device)) {
            [Console]::Error.WriteLine("$Device is not a JSON object of theme keys. Nothing was sent to the TV.")
            exit 2
        }
        Invoke-Tv tv.py --push $Device (Stage 'device.json') | Out-Null
        Invoke-Tv tv.py (Put 'device.json') | Out-Null
    }
    & $py -I theme_restart.py
    if ($LASTEXITCODE) { exit 1 }
    Write-Host "device.json is $(if ($Device -eq 'none') { 'removed' } else { 'on the TV' })."
    exit 0
}

# Every folder is checked before anything is sent, so a refused one cannot leave half a catalog behind.
$isPicture = { param($f) $f.Extension -match '^\.(jpg|jpeg|png|webp)$' }
foreach ($theme in $Themes) {
    $name = Split-Path $theme -Leaf
    if ($name -cnotmatch '^[a-z0-9][a-z0-9_-]{0,39}$') {
        [Console]::Error.WriteLine("unsupported theme name: $name (lower-case letters, digits, '-' and '_'; rename the folder). Nothing was sent to the TV.")
        exit 2
    }
    if (-not (Test-JsonObject (Join-Path $theme 'config.json'))) {
        [Console]::Error.WriteLine("no valid config.json in $theme. Nothing was sent to the TV.")
        exit 2
    }
    $wallpapers = Join-Path $theme 'wallpapers'
    if (Test-Path $wallpapers -PathType Container) {
        foreach ($image in Get-ChildItem $wallpapers -File | Where-Object { & $isPicture $_ }) {
            if ($image.Name -notmatch '^[A-Za-z0-9_.-]+$' -or $image.Name.Contains('..')) {
                [Console]::Error.WriteLine("unsupported wallpaper filename: $($image.FullName). Nothing was sent to the TV.")
                exit 2
            }
        }
    }
}

foreach ($theme in $Themes) {
    $name = Split-Path $theme -Leaf
    $t = "themes/$name"
    Invoke-Tv tv.py (Drop $t) | Out-Null
    $wall = Join-Path $theme 'wallpaper.jpg'
    if (Test-Path $wall) { Invoke-Tv b64push.py $wall (Stage "$t/wallpaper.jpg") | Out-Null; Invoke-Tv tv.py (Put "$t/wallpaper.jpg") | Out-Null }
    $wallpapers = Join-Path $theme 'wallpapers'
    if (Test-Path $wallpapers -PathType Container) {
        foreach ($file in Get-ChildItem $wallpapers -File | Where-Object { (& $isPicture $_) -or $_.Name -eq 'captions.txt' }) {
            $rel = "$t/wallpapers/$($file.Name)"
            Invoke-Tv b64push.py $file.FullName (Stage $rel) | Out-Null
            Invoke-Tv tv.py (Put $rel) | Out-Null
        }
    }
    # config.json last: the TV lists a theme only once its config.json is there.
    Invoke-Tv tv.py --push (Join-Path $theme 'config.json') (Stage "$t/config.json") | Out-Null
    Invoke-Tv tv.py (Put "$t/config.json") | Out-Null
    Write-Host $name
}
Write-Host 'Pick one in Launcher settings > Theme. Remove the catalog with: tools\theme-catalog.ps1 -Clear'
