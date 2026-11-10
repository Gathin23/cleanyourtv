# Windows version of restore.sh: put the TV back the way it was before disable.ps1, from the state
# recorded at that time - packages that were already disabled stay disabled, Home goes back to the
# component that owned it and the animation scales go back to their recorded values. Pass the same
# list you used with disable.ps1.
#
# Usage: tools\restore.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt [-Stock stock.launcher.package]
# The list path is taken as typed: a folder like "tv [old]" needs no backtick escaping.
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$List,
    [string]$Stock = 'com.google.android.tvlauncher',
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
# Pipe plain UTF-8 to native commands; the default here prepends a BOM that corrupts the first command.
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$repo = Split-Path -Parent $PSScriptRoot
$List = (Resolve-Path -LiteralPath $List).Path
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost

# Same as restore.sh: replay the recorded states instead of enabling the whole list, so packages that
# were already disabled stay disabled (#239). recovery.py reads and validates the list itself, warns
# when no record exists and then falls back to the old behaviour.
$cmds = & $py -I (Join-Path $repo 'tools\recovery.py') --commands $List $Stock
if ($LASTEXITCODE -ne 0) { throw 'no restore plan was built, the TV was not changed' }
$tvPy = Join-Path $repo 'tools\tv.py'
$cmds | & $py -I $tvPy --batch
if ($LASTEXITCODE -ne 0) { throw "tv.py --batch failed (exit code $LASTEXITCODE): the restore may be only partly applied; run it again once the TV answers" }

# Same as restore.sh: read the TV back, since tv.py --batch exits 0 when the TV refuses a line. A package
# the plan enables that is still disabled for user 0, or a Home that does not resolve to the planned
# component, fails the run; a read that fails or gives no component only says the check did not run.
$failed = $false
$wantOn = @($cmds | ForEach-Object { if ("$_" -match '^pm enable (\S+)$') { $Matches[1] } })
if ($wantOn.Count -gt 0) {
    # Windows PowerShell turns redirected stderr into an error that 'Stop' would throw.
    $ErrorActionPreference = 'Continue'
    $answer = @(& $py -I $tvPy 'pm list packages -d --user 0' 2>$null)
    $read = $LASTEXITCODE -eq 0
    $ErrorActionPreference = 'Stop'
    if ($read) {
        $off = @($answer | ForEach-Object { if ("$_".Trim() -match '^package:(\S+)') { $Matches[1] } })
        $stillOff = @($wantOn | Where-Object { $off -ccontains $_ } | Select-Object -Unique)
        if ($stillOff.Count -gt 0) {
            Write-Warning "Still disabled after the restore, although the plan enables them: $($stillOff -join ', '). The TV did not take pm enable for these (its answer is in the output above). Run tools\tv.py 'pm enable <package>' once by hand for each."
            $failed = $true
        }
    } else {
        Write-Warning "Could not check which packages are still disabled: compare 'pm list packages -d --user 0' with the list by hand."
    }
}
# Only a component the plan names; the no-record fallback asks the TV to resolve the stock launcher.
$wantHome = @($cmds | ForEach-Object { if ("$_" -match '^cmd package set-home-activity --user 0 ([^$\s]\S*)$') { $Matches[1] } }) | Select-Object -Last 1
if ($wantHome) {
    $ErrorActionPreference = 'Continue'
    $now = "$(@(& $py -I $tvPy 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' 2>$null) | Select-Object -Last 1)".Trim()
    $ErrorActionPreference = 'Stop'
    # pkg/pkg.Activity and pkg/.Activity are the same component.
    $short = { param($c) $c -creplace '^([^/]+)/\1\.', '$1/.' }
    if ($now -cnotmatch '^[A-Za-z_][A-Za-z0-9_.]*/[A-Za-z0-9_.$]+$') {
        Write-Warning "Could not check Home: the TV did not name the activity that answers it. Check it with tools\tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'."
    } elseif ((& $short $now) -cne (& $short $wantHome)) {
        Write-Warning "Home did not go back: the TV resolves $now, and the record has $wantHome. Run tools\tv.py 'cmd package set-home-activity --user 0 $wantHome' once by hand."
        $failed = $true
    }
}
if ($failed) { exit 1 }
