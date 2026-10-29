# Windows version of disable.sh: disable every package listed in a debloat file on the TV
# (one package per line, # comments allowed). Packages are disabled for user 0, never uninstalled.
# Undo with tools\restore.ps1 <same file>.
#
# Usage: tools\disable.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$List,
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
# Pipe plain UTF-8 to native commands; the default here prepends a BOM that corrupts the first command.
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$repo = Split-Path -Parent $PSScriptRoot
$List = (Resolve-Path $List).Path
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost

$pkgs = Get-Content $List | ForEach-Object { ($_ -replace '#.*$', '').Trim() } | Where-Object { $_ } | ForEach-Object { ($_ -split '\s+')[0] }
# Lists come from contributors and each name is pasted into a shell command on the TV (#165).
$bad = @($pkgs | Where-Object { $_ -notmatch '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$' })
if ($bad.Count -gt 0) {
    throw "refusing $List, not a package name: $($bad -join ', ')"
}
# Nothing to do: a list whose entries are all commented out is what CONTRIBUTING asks for while one
# is untested. Say it here, before a query goes to the TV, rather than letting recovery.py stop the run
# after them with a message about the record.
if (-not $pkgs -or $pkgs.Count -eq 0) {
    throw "refusing ${List}: no package names in it (every line is a comment or blank), so nothing was sent to the TV and no recovery record was written."
}
# Core Android packages, the same block as disable.sh: with one of these disabled Android itself can stop
# booting (#884, an original Mi TV Stick). No switch overrides it. Keep both patterns in step with
# disable.sh and recovery.py (tests/test_core_packages.py).
$coreRe = '^(android|com\.android\.systemui|com\.android\.shell|com\.android\.phone|com\.android\.settings|com\.android\.tv\.settings|com\.(google\.)?android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata)|com\.google\.android\.overlay\.modules.*|com\.android\.providers\..*)$'
$coreOk = '^com\.android\.providers\.(calendar|contacts|userdictionary|blockednumber)$'
$core = @($pkgs | Where-Object { $_ -cmatch $coreRe -and $_ -cnotmatch $coreOk })
if ($core.Count -gt 0) {
    throw "refusing ${List}: it disables core Android packages, and without them the TV may not boot again (#884): $($core -join ', '). Take these lines out of the list (or comment them) and run it again. Nothing was changed on the TV."
}
# A Wi-Fi network saved during the first-run wizard belongs to setupwraith, and Android deletes it when
# setupwraith is disabled. Skip it while it owns the saved network; `$env:FORCE_SETUPWRAITH=1` overrides.
$setup = 'com.google.android.tungsten.setupwraith'
if ($pkgs -contains $setup -and -not $env:FORCE_SETUPWRAITH) {
    $wifi = (& $py -I (Join-Path $repo 'tools\tv.py') 'dumpsys wifi') -join "`n"
    # Be safe: if we cannot read the owners (tv.py failed, timed out, or showed no network at all),
    # do not disable setupwraith, so the TV cannot lose the network it is on.
    if ($LASTEXITCODE -ne 0 -or $wifi -notmatch 'cname=') {
        Write-Warning "Skipping ${setup}: could not check which app owns the saved Wi-Fi networks (dumpsys wifi failed or showed none). Try again, or set `$env:FORCE_SETUPWRAITH=1 if the TV uses Ethernet."
        $pkgs = @($pkgs | Where-Object { $_ -ne $setup })
    } elseif ($wifi -match 'cname=[^ ]*setupwraith') {
        Write-Warning "Skipping ${setup}: the saved Wi-Fi network belongs to it, and disabling it would delete that network and take the TV off Wi-Fi. Forget the network and join it again in Settings > Network & Internet, then run this again (or set `$env:FORCE_SETUPWRAITH=1 on Ethernet)."
        $pkgs = @($pkgs | Where-Object { $_ -ne $setup })
    }
}
# Same as disable.sh: on firmware with ro.adb.secure=0, port 5555 gives a shell to any device on the
# network without the "Allow" prompt. Read it now and say so once the list is done; a failed read only
# skips the note. Windows PowerShell turns redirected stderr into an error that 'Stop' would throw.
$ErrorActionPreference = 'Continue'
$adbSecure = ((& $py -I (Join-Path $repo 'tools\tv.py') 'getprop ro.adb.secure' 2>$null) -join '').Trim()
if ($LASTEXITCODE -ne 0) { $adbSecure = '' }
$ErrorActionPreference = 'Stop'

# Home guard (#239), the same check as disable.sh: a list that disables the app owning Home leaves the
# TV with nothing to start Home with. Skip the owner while nothing else on the TV can answer HOME;
# `$env:FORCE_HOME=1` disables it anyway, for a TV reachable another way. With no single owner (Android's
# chooser "android", or a failed/empty answer) the guard fails closed instead of being skipped.
$tvPy = Join-Path $repo 'tools\tv.py'
$claimQuery = 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
# The setup wizard and the settings apps answer HOME too, but they are not launchers (disable.sh).
$notLaunchers = @('com.google.android.tungsten.setupwraith', 'com.android.tv.settings', 'com.android.settings', 'com.android.provision')
$knownLaunchers = @('com.google.android.tvlauncher', 'com.google.android.apps.tv.launcherx', 'com.google.android.leanbacklauncher', 'com.mitv.tvhome')
# Usable HOME claimants the TV can start now; Ok is false when the TV could not list any.
function Read-HomeLaunchers {
    # Windows PowerShell turns redirected stderr into an error that 'Stop' would throw (see getprop).
    $ErrorActionPreference = 'Continue'
    $lines = @(& $py -I $tvPy $claimQuery 2>$null)
    $answered = $LASTEXITCODE -eq 0
    # Keep the class: a vendor fallback is named FallbackHome and is not a usable launcher
    # (TCL Android TV sets register com.tcl.keycustomfunctionservice/.FallbackHome).
    # Trim first: a trailing CR from the TV would stop the `$` anchor from matching any line.
    $parsed = @($lines | ForEach-Object {
        if ("$_".Trim() -match '^([A-Za-z0-9_.]+)/([A-Za-z0-9_.$]+)$') { [pscustomobject]@{ Pkg = $Matches[1]; Cls = $Matches[2] } }
    })
    # Nor is a factory-test app (com.duokan.factorytest/.FactoryLauncher answers HOME on a Mi TV 4A).
    $usable = @($parsed | Where-Object { $_.Cls -notmatch 'FallbackHome$' -and $notLaunchers -notcontains $_.Pkg -and $_.Pkg -notmatch 'factorytest|factory_test|factorymode' } |
        ForEach-Object { $_.Pkg } | Sort-Object -Unique)
    [pscustomobject]@{ Ok = ($answered -and $parsed.Count -gt 0); Launchers = $usable }
}
if (-not $env:FORCE_HOME -and $pkgs.Count -gt 0) {
    $homeQuery = 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
    $ErrorActionPreference = 'Continue'
    $homeLine = @(& $py -I $tvPy $homeQuery 2>$null) | Where-Object { $_ -match '\S' } | Select-Object -Last 1
    if ($LASTEXITCODE -ne 0) { $homeLine = '' }
    $ErrorActionPreference = 'Stop'
    $homePkg = "$homeLine".Trim() -split '/' | Select-Object -First 1
    # "android" is the chooser: the TV did answer, Home just has no default. Kept for the warning below,
    # which runs after $homePkg is cleared.
    $homeChooser = $homePkg -eq 'android'
    # `cmd` arrived with Android 7 (Nougat): on an older set this query answers nothing, or the shell's
    # own "cmd: not found", and the guard was skipped without a word (disable.sh, #795). `dumpsys
    # activity` prints the package holding Home on those sets: mHomeProcess: ProcessRecord{... pkg/u0a56}
    $pkgShape = '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$'
    if (-not ($homePkg -and ($homePkg -match $pkgShape))) {
        $ErrorActionPreference = 'Continue'
        $fallback = @(& $py -I $tvPy 'dumpsys activity' 2>$null | ForEach-Object {
            $line = "$_".Trim()
            if ($line -match 'mHomeProcess:.*[: ]([A-Za-z0-9_.]+)/[A-Za-z0-9_.]+') { $Matches[1] }
        } | Select-Object -First 1)
        $ErrorActionPreference = 'Stop'
        $homePkg = if ($fallback.Count -gt 0 -and "$($fallback[0])" -match $pkgShape) { "$($fallback[0])" } else { '' }
    }
    if ($homePkg) {
        if ($pkgs -contains $homePkg) {
            $found = Read-HomeLaunchers
            $others = @(@($found.Launchers) | Where-Object { $_ -ne $homePkg -and $pkgs -notcontains $_ })
            if (-not $found.Ok -or $others.Count -eq 0) {
                Write-Warning "Skipping ${homePkg}: it owns Home, and no other app that can answer Home is installed and staying enabled. Make the new launcher Home first (launcher\build-and-install.ps1), then run this list again to disable the old one; or set `$env:FORCE_HOME=1 if you can still reach the TV."
                $pkgs = @($pkgs | Where-Object { $_ -ne $homePkg })
            }
        }
    } else {
        # No single owner: Android's chooser ("android", launchers tie), or neither query named one.
        # Fail closed: judge by the HOME claimants, and without those keep the known stock launchers.
        $found = Read-HomeLaunchers
        if ($found.Ok) {
            $listed = @(@($found.Launchers) | Where-Object { $pkgs -contains $_ })
            $others = @(@($found.Launchers) | Where-Object { $pkgs -notcontains $_ })
            if ($listed.Count -gt 0 -and $others.Count -eq 0) {
                foreach ($p in $listed) {
                    Write-Warning "Skipping ${p}: Home has no default, and once the list is applied no other app could answer Home. Make the new launcher Home first, or set `$env:FORCE_HOME=1."
                    $pkgs = @($pkgs | Where-Object { $_ -ne $p })
                }
            }
        } else {
            foreach ($p in $knownLaunchers) {
                if ($pkgs -contains $p) {
                    Write-Warning "Skipping ${p}: could not ask the TV which apps answer Home, and it is a stock launcher. Run again once the TV answers, or set `$env:FORCE_HOME=1 if you can still reach the TV."
                    $pkgs = @($pkgs | Where-Object { $_ -ne $p })
                }
            }
        }
    }
    # Neither query named Home, so the guard could only go by the apps that answer Home. Say so (disable.sh, #795).
    if (-not $homePkg -and $pkgs.Count -gt 0) {
        $why = if ($homeChooser) { 'Home has no default on this TV (resolve-activity answered "android", the chooser), so the Home guard went by the apps that answer Home instead.' } else { 'Could not tell which app owns Home on this TV, so the Home guard went by the apps that answer Home instead: neither "cmd package resolve-activity" (added in Android 7) nor "dumpsys activity" named one.' }
        Write-Warning "$why Check by hand that the list does not hold your launcher before running it: the replacement launcher needs Android 8.0 (minSdk 26), so on an older set the stock launcher is the only Home there is, and a set with no recovery has no way back (#795)."
    }
}
# Same as disable.sh (#321): a package that asks for android.permission.TV_INPUT_HARDWARE is the software
# layer under the HDMI, AV and tuner inputs. Disable one and the port still notices the cable but shows
# "No Signal" for good; re-enabling the pieces in batches does not bring the picture back, a full restore
# and a cold boot do. Keep them; `$env:TVINPUT_ALLOW=1` disables them anyway.
if (-not $env:TVINPUT_ALLOW -and $pkgs.Count -gt 0) {
    # Trailing `; true`: the loop's status is the last package's `grep -q`, which is nonzero whenever the
    # last listed package is not an input driver, and would look like a failed check.
    $loop = 'for p in ' + ($pkgs -join ' ') + '; do dumpsys package $p | sed -n -E ''/requested permissions:/,/(install|runtime) permissions:|User [0-9]+:/p'' | grep -q TV_INPUT_HARDWARE && echo $p; done; true'
    # Windows PowerShell turns redirected stderr into an error that 'Stop' would throw (see getprop).
    $ErrorActionPreference = 'Continue'
    $tvhw = @(& $py -I (Join-Path $repo 'tools\tv.py') $loop 2>$null)
    $checked = $LASTEXITCODE -eq 0
    $ErrorActionPreference = 'Stop'
    if (-not $checked) {
        Write-Warning 'Could not check which listed packages drive the HDMI/AV inputs (tv.py failed). Check every input after this run: a port that notices the cable and stays black means one of them was disabled, and only a full restore and a cold boot bring it back (#321).'
    } else {
        foreach ($p in ($tvhw | ForEach-Object { $_.Trim() } | Where-Object { $_ })) {
            if ($pkgs -contains $p) {
                Write-Warning "Skipping ${p}: it asks for android.permission.TV_INPUT_HARDWARE, so it is the software layer under the HDMI/AV/tuner inputs. With it disabled those inputs still notice the cable but stay black (No Signal), and re-enabling the pieces in batches does not restore the picture: a full restore plus a cold boot does (#321). Set `$env:TVINPUT_ALLOW=1 to disable it."
                $pkgs = @($pkgs | Where-Object { $_ -ne $p })
            }
        }
    }
}
# Same as disable.sh: keep the apps the TV opens its HDMI inputs (VIEW on a passthrough channel) and
# Settings with, asked from the TV rather than guessed by name. `$env:HANDLERS_ALLOW=1` disables them anyway.
if (-not $env:HANDLERS_ALLOW -and $pkgs.Count -gt 0) {
    $q = 'cmd package query-activities --brief'
    $ErrorActionPreference = 'Continue'
    $handlers = @(& $py -I (Join-Path $repo 'tools\tv.py') "$q -a android.intent.action.VIEW -t vnd.android.cursor.item/channel 2>/dev/null; echo ==; $q -a android.settings.SETTINGS 2>/dev/null; true" 2>$null)
    $checked = $LASTEXITCODE -eq 0
    $ErrorActionPreference = 'Stop'
    if (-not $checked) {
        Write-Warning 'Could not check which listed packages open the TV''s inputs and Settings (tv.py failed). After this run, open an HDMI input and Settings from the launcher before you put the remote down.'
    } else {
        # `@()` so an empty or one-line answer is still an array: IndexOf throws on $null.
        $lines = @($handlers | ForEach-Object { $_.Trim() })
        $split = [array]::IndexOf($lines, '==')
        for ($i = 0; $i -lt $lines.Count; $i++) {
            if ($lines[$i] -notmatch '^([A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+)/') { continue }
            $p = $Matches[1]
            if ($pkgs -notcontains $p) { continue }
            if ($split -lt 0 -or $i -lt $split) {
                Write-Warning "Skipping ${p}: the TV opens its HDMI/AV inputs and channels with it, so with it disabled the launcher's input tiles do nothing. Set `$env:HANDLERS_ALLOW=1 to disable it anyway."
            } else {
                Write-Warning "Skipping ${p}: the TV opens Settings with it, so with it disabled Settings does not open (and with it the way back to Developer options). Set `$env:HANDLERS_ALLOW=1 to disable it anyway."
            }
            $pkgs = @($pkgs | Where-Object { $_ -ne $p })
        }
    }
}
# Same as disable.sh: the state undo needs is recorded before the first command reaches the TV, and
# a failed save leaves the TV untouched (#239).
& $py -I (Join-Path $repo 'tools\recovery.py') --save $List
if ($LASTEXITCODE -ne 0) { throw 'no recovery record was written, so nothing was disabled' }

$cmds = @($pkgs | ForEach-Object { "pm disable-user --user 0 $_" }) + 'pm list packages -d'
$cmds | & $py -I (Join-Path $repo 'tools\tv.py') --batch

if ($adbSecure -eq '0') {
    Write-Warning "This TV reports ro.adb.secure=0, so network ADB on port 5555 accepts any device on your network without the `"Allow`" prompt. When you are done, turn USB debugging and Network/ADB debugging off on the TV and reboot it (README: `"Isn't ADB access a security hole?`")."
}
