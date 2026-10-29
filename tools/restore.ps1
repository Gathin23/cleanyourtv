# Windows version of restore.sh: put the TV back the way it was before disable.ps1, from the state
# recorded at that time - packages that were already disabled stay disabled, Home goes back to the
# component that owned it and the animation scales go back to their recorded values. Pass the same
# list you used with disable.ps1.
#
# Usage: tools\restore.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt [-Stock stock.launcher.package]
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$List,
    [string]$Stock = 'com.google.android.tvlauncher',
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
# Pipe plain UTF-8 to native commands; the default here prepends a BOM that corrupts the first command.
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$repo = Split-Path -Parent $PSScriptRoot
$List = (Resolve-Path $List).Path
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost

# Same as restore.sh: replay the recorded states instead of enabling the whole list, so packages that
# were already disabled stay disabled (#239). recovery.py reads and validates the list itself, warns
# when no record exists and then falls back to the old behaviour.
$cmds = & $py -I (Join-Path $repo 'tools\recovery.py') --commands $List $Stock
if ($LASTEXITCODE -ne 0) { throw 'no restore plan was built, the TV was not changed' }
$cmds | & $py -I (Join-Path $repo 'tools\tv.py') --batch
if ($LASTEXITCODE -ne 0) { throw "tv.py --batch failed (exit code $LASTEXITCODE): the restore may be only partly applied; run it again once the TV answers" }
