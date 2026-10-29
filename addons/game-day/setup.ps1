# Windows version of setup.sh: send Game Day its calendars from a computer, instead of typing long links with the
# remote. One calendar per line in calendars.txt beside this script (or the file you name): "sport | name | link",
# optionally "| colour". It replaces the list on the TV, waits while the TV fetches each calendar, and prints how
# each one loaded. Links are never printed. Install the add-on first (README.md).
#
# Usage: addons\game-day\setup.ps1 -TvHost 192.168.1.50                      # calendars.txt from this folder
#        addons\game-day\setup.ps1 -TvHost 192.168.1.50 D:\my-teams.txt      # any calendars file
param(
    [Parameter(Position = 0)][string]$Calendars,
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
if (-not $Calendars) { $Calendars = Join-Path $PSScriptRoot 'calendars.txt' }
if (-not (Test-Path $Calendars -PathType Leaf)) { throw "no calendars file $Calendars (copy calendars.example.txt to calendars.txt and edit it)" }
$Calendars = (Resolve-Path $Calendars).Path
Set-Location (Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) 'tools')
$py = 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'io.github.justinnewman.gameday'
$uri = "content://$pkg.files"
$tmp = '/data/local/tmp/gameday-calendars.txt'
function Tv([string]$cmd) { & $py -I tv.py $cmd; if ($LASTEXITCODE) { throw "tv.py failed: $cmd" } }

if (-not ((Tv "pm path $pkg; true") -match 'package:')) { throw 'Game Day is not on the TV: build and install it first (README.md)' }
Write-Host 'Game Day is installed.'
# TCL firmware (TclAppBoot) refuses to start an app for another app's request without its AUTO_START app-op, so
# neither this script nor Home could reach Game Day. Allow it where the op exists; elsewhere this does nothing.
Tv "appops set $pkg AUTO_START allow 2>/dev/null; true" | Out-Null
& $py -I b64push.py $Calendars $tmp | Out-Null
if ($LASTEXITCODE) { throw 'sending the calendars failed' }
Tv "content write --uri $uri/calendars.txt < $tmp; rm -f $tmp" | Out-Null

Write-Host 'Checking the calendars on the TV:'
$lines = @()
for ($i = 0; $i -lt 45; $i++) {
    Start-Sleep -Seconds 2
    $lines = @((Tv "content read --uri $uri/result.txt 2>/dev/null; true") -split "`r?`n" | Where-Object { $_ -ne '' })
    if ($lines.Count -gt 0 -and $lines[-1] -eq 'done') { break }
}
if ($lines.Count -eq 0 -or $lines[-1] -ne 'done') { throw 'the TV did not finish in 90 seconds: open Game Day on the TV to see each calendar' }

$failed = $false
$sent = 0
$bad = $false
foreach ($line in $lines) {
    $f = $line -split '\|', 5
    switch ($f[0]) {
        'ok' { Write-Host ('  {0,-22} {1,-14} ' -f $f[1], $f[2]) -NoNewline; Write-Host 'ok' -ForegroundColor Green -NoNewline; Write-Host "  $($f[3]) $(if ($f[3] -eq '1') { 'entry' } else { 'entries' }), $(if ($f[4] -like 'nothing*') { $f[4] } else { "next $($f[4])" })"; $sent++ }
        'fail' { Write-Host ('  {0,-22} {1,-14} ' -f $f[1], $f[2]) -NoNewline; Write-Host $f[3] -ForegroundColor Red; $sent++; $failed = $true }
        'error' { Write-Host ('  ' + ($line -replace '^error\|[^|]*\|', '')) -ForegroundColor Red; $failed = $true; $bad = $true }
    }
}
if ($bad) { Write-Host 'Nothing changed on the TV.' } else { Write-Host "Sent $sent calendars to the TV. Links are not printed." }
if ($failed) { exit 1 }
