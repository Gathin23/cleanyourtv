# COOP COTV-0001 (Realtek rtd298x, Android 4.4.2, Argentina)

Report for a KitKat-era set sold by Cooperativa RENACER (Ushuaia) as "TV LED HD SMART". Source:
issue [#795](https://github.com/Gathin23/cleanyourtv/issues/795). The first report ended with the
cause unknown and the set written off; the same reporter then isolated the cause, recovered the TV and
re-applied the list in batches. That second half is why this note exists — the merged bullet in
`docs/TROUBLESHOOTING.md` had said a set stuck this way was unrecoverable.

> **Warning:** `com.haiersoft.launcherproject2995hk` is the only Home app on this build, and the repo
> launcher needs Android 8.0 (minSdk 26) where this set is SDK 19, so nothing can replace it. Disable
> it and every boot stops on `INICIALIZANDO`. `com.tsb.tv` looks unrelated and is a dependency of that
> launcher: disabling it crash-loops the launcher instead.

## Device

| Field | Value |
| :--- | :--- |
| Retail model | COOP COTV-0001 ("TV LED HD SMART"), serial 001909 |
| Product / board | `rtd298x_tv001` |
| SoC | Realtek `rtd298x` |
| Kernel | 3.7.2-298x |
| Android / SDK | 4.4.2 / 19 |
| Build | `rtd298x_tv001-eng 4.4.2 KOT49H eng.yeh.20180522.224300 test-keys` (eng build, test-keys) |
| Bootloader | 0000.0000.0002 |
| SELinux | Enforcing |
| Packages | 58 |
| Ports | 2× USB-A host, Ethernet, Wi-Fi; no visible recovery button or service port |
| Developer options | USB debugging only (KitKat has no network-ADB toggle) |

## How the set was reached

- Wi-Fi (192.168.18.3): port 5555 closed, `nmap -Pn -p 1-65535` found no open TCP port at all.
- Ethernet (192.168.18.32): 5555 open, and that is the only way in. `tools/tv.py` was used because no
  `adb` binary was installed on the reporter's machine.
- The first connection raised `ConnectionResetError`; a retry a few seconds later worked. The set
  seems to accept one ADB session at a time, so a failed connect is not the end of it.

## What went wrong

17 packages were disabled with `pm disable-user --user 0`, each one echoing `new state: disabled-user`,
and `pm list packages -d` read back all 17. The factory launcher
`com.haiersoft.launcherproject2995hk` was among them and no replacement was installed, because the
repo launcher cannot run below Android 8.0. After a power cycle the set sat on the green Android robot
with `INICIALIZANDO` for 20+ minutes, and stayed there across a hard power cut and another off/on from
the remote.

## Cause, isolated

Disabling `com.haiersoft.launcherproject2995hk` alone reproduces the hang; re-enabling it and
rebooting brings the set back. The hard power cut during the first boot was **not** the cause, and
neither was the batch size: 15 of the same 17 packages are disabled today on a working set.

## The second trap: `com.tsb.tv`

Disabling `com.tsb.tv` makes the launcher crash in a loop with "Se ha detenido la aplicación
lancherProject", and re-enabling it clears the dialog. Nothing in a package list says that a
seemingly unrelated vendor package is what the launcher runs on, which is the argument for applying a
list in batches with a reboot and a look at the screen between them, rather than for any particular
package name.

## Recovery from `INICIALIZANDO`

- **The stuck screen can still answer ADB.** After the set was written off as unreachable, ping and
  port 5555 answered again on the Ethernet address while the display still said `INICIALIZANDO`. The
  reporter suspects the Ethernet cable was loose during the earlier checks and could not confirm it,
  but check the link and retry before concluding that a KitKat set is lost.
- Re-enable every package from the pre-change backup, confirm the disabled list is empty, then reboot:

  ```sh
  tools/tv.py 'pm list packages -d'
  tools/tv.py 'pm enable com.haiersoft.launcherproject2995hk'
  # ...once per package that was disabled, or once per package in the backup
  tools/tv.py 'pm list packages -d'      # must print nothing
  tools/tv.py --reboot                   # returns once ADB accepts it; wait for the TV to come back
  ```

  This set showed `INICIALIZANDO` once more on the reboot and then booted normally. Boots take 33–73 s.
- A USB keyboard or a service port is not available on this board, and there is no public firmware, so
  `tools/restore.sh` is only reachable while ADB answers — which is exactly why the two minutes spent
  re-checking the Ethernet link matter.

## The list that worked

15 of the original 17, applied in four groups with a reboot and a screen check after each, and the
last two tried on their own once the set had been stable for a while:

| Group | Packages | Result |
| :--- | :--- | :--- |
| 1 | `com.android.galaxy4`, `com.android.noisefield`, `com.android.dreams.phototable`, `com.android.dreams.basic`, `com.android.soundrecorder`, `com.android.speechrecorder` | boots |
| 2 | `com.realtek.camera`, `com.rtk.android.miracast`, `com.svox.pico` | boots |
| 3 | `com.adups.fota`, `com.adups.fota.sysoper`, `cn.haiqin.realtek.other.vstoresubclient` | boots, menu and media playback work |
| 4 | `com.cvte.taskmanager` | boots |
| later | `com.rtk.mediabrowser`, `com.hikeen.xymenu` | boots; media playback and the TV menu still work |

**Must stay enabled:** `com.haiersoft.launcherproject2995hk` (the only Home app) and `com.tsb.tv`
(what that launcher runs on). Neither has a replacement on SDK 19.

An earlier idea in the report — skipping `com.adups.*` on old builds — was withdrawn by the reporter:
both adups packages disabled cleanly and the set booted, so the trap is the Home package, not the
updater.

## Limits

- One unit, one eng/test-keys build, one evening. The groups were checked with a reboot and a look at
  the menu and media playback, not package by package; only the launcher and `com.tsb.tv` were
  isolated on their own.
- No cold boot after the final list, and no second power cycle, so a delayed failure is still
  possible.
- Nothing here says other `rtd298x` builds behave the same, and nothing was tested about
  `com.realtek.*` packages beyond the two above.
- `com.rtk.engmenu` was deliberately left untouched.
- The launcher half is impossible on this set: minSdk 26 against SDK 19. Lowering minSdk is a source
  change, and an APK rebuilt elsewhere is not something this repo verifies.

## What to do differently

- Keep the stock launcher. `tools/disable.sh` skips the package that owns Home when no other installed
  app can answer HOME, and on Android 6 and older it asks `dumpsys activity` when `cmd package` is
  missing (see `docs/TROUBLESHOOTING.md`). On this set that check would have to name
  `com.haiersoft.launcherproject2995hk` through `dumpsys activity`; the reporter disabled the packages
  by hand, so it never ran, and whether it names this launcher here is unverified.
- Keep the ADB path alive: Ethernet, since this firmware has no network-debugging toggle, and one
  session at a time.
- Batches with a reboot between them, not one long list: that is how `com.tsb.tv` was caught.
