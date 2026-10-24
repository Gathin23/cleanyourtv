# TCL G08_4K_GB — "Smart TV Pro" (4K)

| | |
|---|---|
| OS | Google TV, Android 14 / SDK 34 |
| Firmware | `tcl9618-user 14 UTT2.250416.001 AU02 release-keys` |
| Region | zh-TW |
| Stock launcher | `com.google.android.apps.tv.launcherx` — Google TV, not Android TV's `com.google.android.tvlauncher` |
| Live TV app | `com.tcl.tv` (`com.tcl.player.TVActivity`) |
| Packages | 154 installed, none disabled out of the box |

`debloat.txt` is the exact list applied, 36 packages. `config.json` is the dock that was built into the
launcher. Two other G08 folders exist; this one is the `G08_4K_GB` board on the **Google TV 14** branch
(`UTT2.250416.001`), which is neither the Android TV 12 (`STT2.221228.001`) EU/UK build nor the
`G08_4K_US` one.

## Replacing the Home key takes two packages, and `setupwraith` is the one people miss

```bash
pm disable-user --user 0 com.google.android.apps.tv.launcherx        # the Google TV launcher
pm disable-user --user 0 com.google.android.tungsten.setupwraith     # the blocker — see below
```

`setupwraith`'s `RecoveryActivity` declares `CATEGORY_HOME` too, with `priority=1` — deliberately just
above the default (`0`) — so it beats any third-party launcher the moment the stock one is disabled. The
TV then boots into a black "setup recovery" screen with a broken Home key. Disabling the whole package is
the only way to make the custom launcher the sole HOME candidate.

**Re-enable it (`pm enable com.google.android.tungsten.setupwraith`) before a factory reset** or before
running the Google TV setup wizard again.

## Four other routes to the Home key do not work on this firmware

All four were tried on a live set; don't spend time on them.

| Route | Result |
|---|---|
| `cmd package set-home-activity --user 0 <component>` | Prints `Success`, but nothing changes: `resolve-activity` still returns the old launcher and no preferred activity is recorded. A no-op on this build. |
| `android:priority` on the HOME `<intent-filter>` | Ignored for third-party apps. The installed APK was pulled back and verified to carry `priority="2"`, then `"1000"`; the package manager still reported `priority=0` and kept resolving to `setupwraith`. |
| `cmd role add-role-holder android.app.role.HOME <pkg>` | Succeeds — `dumpsys role` shows `holders=<pkg>` — but HOME resolution ignores it. |
| `pm disable-user <setupwraith>/<...>.RecoveryActivity` | Refused: `SecurityException: Shell cannot change component state for ...` |

## The Inputs menu and this set's TV app

Tapping an HDMI entry in the launcher's Inputs menu once killed the TV app outright:

```
NumberFormatException: For input string: "com.tcl.tvinput/.TvPassThroughService/HDMI110008"
    at com.tcl.player.intentprocess.processor.TuneChannelIntentProcessor.process
```

`TvContract.buildChannelUriForPassthroughInput()` is the documented way to tune a passthrough input, but
TCL's TV app runs `ContentUris.parseId()` over the URI's last path segment — which for a passthrough input
is the input id, not a number. It throws in `onNewIntent`, the app dies, and the input switch dies with it.
That intent has exactly **one** handler on this set (`com.tcl.tv/.player.TVActivity`, for both
`vnd.android.cursor.item/channel` and `...dir/channel`), so there is no alternative route to fall back to:

- `android.media.tv.action.SETUP_INPUTS` opens TCL's *streaming channels* setup screen, not the HDMI list.
- `com.tcl.action.HDMI_ACTIVE_SOURCE` (handled by `com.tcl.tv/.receiver.HdmiControllerReceiver`) is what the
  CEC stack broadcasts when a device becomes the active source. Its extra is a Parcelable device info, so
  it cannot be forged from an app or from the shell.

The same intent (ACTION_VIEW with the channel URI and `vnd.android.cursor.item/channel`) switched cleanly to
HDMI 1 and HDMI 2 on a later run of the same set with launcher 2.6, with no crash, so the failure depends on
something we did not reproduce. Recorded because it was seen; switch inputs with the remote's input key
either way.

## Windows notes

- **Piping a command list into `tv.py --batch` from PowerShell** prepends a UTF-8 BOM that the device shell
  rejects (`sh: pm: inaccessible or not found`) — the *first* command silently never runs. Redirect from
  `cmd` or bash instead. Git Bash has the opposite trap: MSYS rewrites `/sdcard/...` arguments into Windows
  paths, so `tv.py --pull /sdcard/x.png` fails with `open failed: No such file or directory`. Use
  PowerShell for pulls.
- The repo's `.sh` helpers hard-code `tools/venv/bin/python`; a Windows venv keeps it in
  `tools/venv/Scripts/`. Use the PowerShell twins (`disable.ps1`, `restore.ps1`) or drive `tools/tv.py`
  directly.

## Other notes

- **After debloating, the launcher's app grid is empty** — all remaining apps with a Leanback launcher
  intent are either in the dock or in `hidden_apps`. The grid fills up again as apps are installed.
- **Kept on purpose**: `com.tcl.airplay2`, `com.mediatek.AirplayAPK`, `com.mediatek.airplaydaemon`
  (AirPlay), `com.tcl.UpdatePeripheral` (accessory firmware), `com.tcl.systemserver` (system update path,
  must not be disabled).
- **Casting**: AirPlay is kept, Miracast (`com.tcl.miracast`) and TCL MagicConnect
  (`com.tcl.magiconnectfree`) are off. Google Cast is not available on this set: `mediashell` ships
  disabled and the shell cannot enable it (`Shell cannot change component state for null to 1`).
- **No standalone system-OTA package exists** on this firmware. The only update package is
  `com.tcl.UpdatePeripheral` (peripherals).

## Restore

```powershell
.\tools\restore.ps1 -TvHost <tv ip> examples\tcl-g08-4k-gb\debloat.txt -Stock com.google.android.apps.tv.launcherx
```

Then press Home — the Google TV launcher comes back.
