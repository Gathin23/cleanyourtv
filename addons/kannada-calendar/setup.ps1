# Windows version of setup.sh: give the Kannada Calendar add-on your calendar link. The add-on downloads the
# calendar itself from it, so the link stays on the TV and nothing is saved in this repository. Install the app first
# (README.md, Setup).
#
# Usage: addons\kannada-calendar\setup.ps1 -TvHost 192.168.1.50 '<the link from the Bangalore Press website>'
#        addons\kannada-calendar\setup.ps1 -TvHost 192.168.1.50 -Remove     # forget the link and the saved copy
param(
    [Parameter(Position = 0)][string]$Link,
    [switch]$Remove,
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
if (-not $Link -and -not $Remove) { throw "pass the link from the Bangalore Press website, or -Remove" }
$env:TV_HOST = $TvHost
Set-Location (Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) 'tools')
$py = 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'io.github.gagantk.kannadacalendar'
$dir = "/sdcard/Android/data/$pkg/files"
function Tv([string]$cmd) { & $py -I tv.py $cmd; if ($LASTEXITCODE) { throw "tv.py failed: $cmd" } }

if (-not ((& $py -I tv.py "pm path $pkg; true") -match 'package:')) {
    throw 'the add-on is not on the TV: build and sideload it first (addons\kannada-calendar\README.md)'
}
if ($Remove) {
    Tv "rm -f $dir/feed.txt; pm clear $pkg" | Out-Null
    'link and saved calendar removed'
    return
}
# The link goes inside single quotes on the TV's shell, so only the characters a web address uses are let through.
if ($Link -notmatch '^(https?|webcal)://[A-Za-z0-9:/._%?=&~+@-]+$') {
    throw 'that does not look like a web address (a space or a quote in it?); copy the link again'
}
Tv "mkdir -p $dir && printf '%s\n' '$Link' > $dir/feed.txt" | Out-Null
'link saved on the TV; open the Kannada Calendar card and it downloads the calendar'
