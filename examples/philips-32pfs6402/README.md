# Philips 32PFS6402/12 (QM16XE_F, Android TV 7.1.2)

Notes from one set in France, on firmware `QM164E.0.16.102.24` (build `N2G48H`). The list and its header are in
[`debloat.txt`](debloat.txt). This is the 2016 TP Vision platform of
[`philips-49pus6561`](../philips-49pus6561/debloat.txt), on the FHD board (model `QM164E`, device `QM16XE_F`,
MediaTek MT5861), which stayed on Android 7.1.2.

## Debloat only

The set is API 25 and the reference launcher needs API 26, so the stock home
(`com.google.android.leanbacklauncher`) stays and is not in the list. Private DNS does not exist before
Android 9 either.

## Screenshots

`screencap` writes 0 bytes on every screen here, the stock home and Settings included, not only on Live TV.
Check what is on screen with `dumpsys activity activities | grep mResumedActivity` and look at the TV.

## The two input-permission packages

`org.droidtv.usagelogger` and `org.droidtv.demome` declare `TV_INPUT_HARDWARE`, so `tools/disable.sh` skips them
without `TVINPUT_ALLOW=1`. The TV's inputs come from `org.droidtv.tunerservice`, `hdmiService` (HDMI 1-4),
`scartService` and `componentService`. They were disabled last, after a reboot and a Live TV check on the real
remote, and the TV came back on Live TV after the next reboot.

Play Movies (`com.google.android.videos`), VLC and Molotov each declare a `TvInputService` for their preview
channels; none of them is a hardware input.

## Closing ADB: port 5555 stays open

USB debugging alone opens network ADB on 5555. After the session the owner revoked the USB debugging
authorisations, switched USB debugging and Developer options off and power-cycled the TV:
`tools/adb-closed.py` still reported 5555 open once the TV had booted. `settings get global adb_enabled` still read
`1` with the switch shown off, as on the [TCL Q7C](../tcl-q7c-google-tv/README.md). Setting it to `0` over ADB
dropped the connection, but after a further mains power cycle the port was open again.

What is left: `ro.adb.secure=1` and no authorised key, so a connection only raises "Allow USB debugging?" on the
TV and gets nothing unless someone accepts it there. Refuse that prompt when you did not start it, and block
tcp/5555 to the TV on the router, or put the TV on a guest network, if that is not enough.

## Undo

    TV_HOST=<tv ip> tools/restore.sh examples/philips-32pfs6402/debloat.txt
