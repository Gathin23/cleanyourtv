# Mtek MK32CS1NB 32" (CVTE TP.MT5510I.PB801, MT5863)

Android 7.0 (SDK 24), firmware `20171230_042214`, 1 GB of RAM and 8 GB of flash, stock launcher
`com.cvte.tv.launcher`. The board is CVTE's `TP.MT5510I.PB801` on a MediaTek MT5863.

This is one session on one set: the launcher ran there from a local build, and no debloat list was
kept. The repo's launcher stays at Android 8.0 - `minSdk 26` in `launcher/app/build.gradle` - so an
Android 7 set answers `INSTALL_FAILED_OLDER_SDK`, and `PROMPT.md` says to offer the debloat half and
say the launcher cannot be installed. What follows is the fork the owner built instead, kept here
because it is the part another owner of this board would have to redo.

## ADB, with no Developer options

This firmware has no Developer options screen. ADB is in CVTE's factory menu, the route
[`examples/super-general-cvte-t972`](../../examples/super-general-cvte-t972/README.md) records for the
same MT5863 board: with a source in front (TV or HDMI, not the launcher), press MENU and then 1 1 4 7
on the remote, and set DEBUG > ADB ENABLE.

The "Allow USB debugging?" prompt only appeared after unplugging the set from the wall and powering it
on again; standby was not enough.

AGENTS.md's rule applies as it does everywhere else: the factory menu is opened with the remote to get
ADB and turned off the same way when the session ends, and no test package in it is started, from the
shell or by hand. Close the session as usual, then `tools/adb-closed.py <tv ip>` must report the port
closed.

## The USB port is adb's while debugging is on

With ADB ENABLE on from that menu, `sys.usb.config` and `sys.usb.state` stay on `adb`, so the port
serves debugging only: a Logitech K400 Plus receiver was never listed (`/proc/bus/input/devices` showed
the remote and one internal device, `dumpsys usb` only the MediaTek Wi-Fi module) and the same receiver
works on a computer. Switching DEBUG > ADB ENABLE off, then unplugging the set from the wall before
powering it on, brings the keyboard back. Do the whole session with ADB on and plug peripherals in
afterwards.

## A local build for Android 7

`minSdk 24` alone installs and then crashes on start with `NoClassDefFoundError: java.time.LocalDate`:
the launcher reads `java.time` in `MainActivity.java`, `Sun.java`, `Festival.java`, `SkyView.java` and
`ClockScene.java`. Two changes made the fork run on this set:

- core library desugaring - `coreLibraryDesugaringEnabled true` in `compileOptions` and a
  `coreLibraryDesugaring` dependency on `com.android.tools:desugar_jdk_libs`;
- the four API-26+ calls `lintRelease` reports, each replaced:

| in | call | replaced with |
|---|---|---|
| `MainActivity.java` | `PackageManager.getChangedPackages` | compare the installed package count |
| `UiFont.java` | `Resources.getFont` | `ResourcesCompat.getFont` |
| `WallpaperCaptions.java` | `Files.readAllLines(file.toPath())` | a plain `BufferedReader` |
| `WallpaperPlayer.java` | `Bitmap.Config.HARDWARE` | the same bitmap without it, `HARDWARE` being Android 8 and up |

`lintRelease` then reports no NewApi errors on the fork. Home, the dock, the tile menu, the app
library, and the Settings and network buttons work, a reboot comes back to the launcher, and it takes
about 28 MB of heap.

## Home, and the CVTE rule

The stock launcher is `com.cvte.tv.launcher`. On a `com.cvte.*` board AGENTS.md keeps that launcher
enabled: the new one is made Home, the set is rebooted once, and Home and ADB are confirmed before
anything else. The session ended with Home on the new launcher and the stock one still installed, and
the report does not record which command moved it, so treat that step as unverified here and check it
on the set with `dumpsys activity activities | grep mFocusedApp`.

## HDMI inputs need CVTE's own activity and extra

The CVTE TV app ignores the standard passthrough `ACTION_VIEW` on a channel URI, so the Inputs menu
lists ports whose rows do nothing. It switches when the intent names
`com.cvte.tv.androidsetting/.TvSettingActivity` and carries the input's id as the int extra
`MTK_EXTRA_INPUT_ID`.

The launcher's own vendor route - `input_sources` in [`themes/README.md`](../../themes/README.md) -
sends the configured `input_action` with the source number as an int extra named `input`
(`MainActivity.openInput`), which is not what this app answers, so that route needs the fork's change
on this set. With it, the Inputs menu lists HDMI 1 to 3 as `HW4` to `HW6`, and all three switch with a
device plugged in.

## Limits

- The remote's SOURCE key belongs to CVTE's key service and works only while its own TV screen is in
  front, so it does nothing from the launcher's home.
- AV, YPbPr and VGA are not in the Inputs menu. The Live TV row opens the TV screen, whose SOURCE key
  lists them.

Recorded from [#1664](https://github.com/Gathin23/cleanyourtv/issues/1664).
