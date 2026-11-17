# Hikeen smart TV (Realtek RTD285O, Android TV 9)

A white-label smart TV: the set itself reports only "Smart" / "SmartTV"; "Hikeen" is the OEM whose
packages (`com.hikeen.*`, `cn.zeasn.overseas.tv.hikeen.orion`) the firmware carries. Zeasn's "orion"
launcher is the stock Home. The debloat list disables 12 packages; 14 more - this owner's preloaded
apps, casting and voice - are commented out for a reader to choose.

## Quirks of this board

- **No screenshots**: `screencap` writes a 0-byte file in every app on this firmware (Live TV, Home,
  system dialogs). Verify a change with `tools/tv.py 'dumpsys window | grep mCurrentFocus'` instead.
- **ADB is open to anyone**: userdebug build, `ro.adb.secure` unset — port 5555 answers with no
  "Allow" prompt while debugging is on. Close debugging when the session is done.
- **Settings** is `com.hikeen.settings`; `com.android.tv.settings` has no launcher entry on this
  build. The folder's config.json seeds the Hikeen settings app into the launcher's library
  (`library_apps`) and pins it in the dock.
- **One line needs a hand**: `tools/disable.sh` skips
  `com.google.android.leanbacklauncher.recommendations` on this firmware, because its dumpsys prints
  no permission block for the input-driver check. After a reboot with the inputs verified, run the
  list again with `TVINPUT_ALLOW=1` to take that line.
- The two Zeasn RRO overlays are not listed: disabling their base apps leaves them inert.
- Never start `com.hikeen.factorymenu` or the `com.factory.*` packages on this board.

Undo:

    TV_HOST=<tv ip> tools/restore.sh examples/hikeen-smart-rtd285o/debloat.txt com.zeasn.oriontv.open.launcher
