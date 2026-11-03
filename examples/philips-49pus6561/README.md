# Philips 49PUS6561/12 (QM16XE_U, Android TV 8.0)

Notes from one set, on build `OPR5.170623.014` (incremental 026701). The list and its header are in
[`debloat.txt`](debloat.txt). This is the 2016 TP Vision platform (model `QM163E`, device `QM16XE_U`), not the
TPM171E board of the [55PUS7503](../philips-55pus7503/debloat.txt): the Live TV app is `org.droidtv.zapster` and
the NetTV preloads differ.

## Connecting

USB debugging alone opens ADB on TCP 5555. The "Allow USB debugging?" prompt appeared at the first connection.

## Launcher

The stock Home is `com.google.android.tvlauncher`; `com.google.android.leanbacklauncher` is also installed but
claims no HOME intent, so it was left alone. `launcher/build-and-install.sh` disabled the stock launcher after
the new Home was confirmed, and Home resolved to `com.gathin.tvhome/.MainActivity` across three reboots.

The Inputs pill lists HDMI 1-4, SCART, Y Pb Pr and Live TV. [`config.json`](config.json) sets the Live TV package and
hides three tiles: Live TV (the Inputs menu covers it), `org.droidtv.eum` (the "How to..." videos) and
`org.droidtv.nettvbrowser` (the "Internet" tile). Both are kept enabled: `eum` is what the Sources menu on the
50PUS7303 needed, and `nettvbrowser` is the HbbTV handler.

## Power-on and the remote

The TV powers on to the last used source (Live TV), not to Home, as Philips sets do. The owner kept it that way, so
`power_on_home` was not tried.

On the real remote with the list applied: Home opens the new launcher, Sources opens the Philips source menu,
Settings opens the Philips quick settings, Ambilight opens the Ambilight menu. If you disable Netflix (it is
commented out in the list), the Netflix button does nothing; the [55POS9002 note](../philips-55pos9002/README.md) describes the scancode 632 remap, which was
not tried here.

## The two input-permission packages

`org.droidtv.usagelogger` and `org.droidtv.demome` declare `TV_INPUT_HARDWARE`, so `tools/disable.sh` skips them
without `TVINPUT_ALLOW=1`. Neither is a TV input service. They were disabled last, after a reboot and a Live TV
check; Live TV started and the TV came back on Live TV after the next reboot.

## Second unit: 55PUS6581/12

A 55-inch set from the same range, in Turkey, on Ethernet: same model `QM163E`, device `QM16XE_U` and firmware
`QM163E.0.26.70.1`. Its owner applied part of this list in nine small batches, with a check on the real remote after
each, and added three packages that are now in the list: `com.google.android.tv.bugreportsender`,
`com.google.android.onetimeinitializer` and `org.droidtv.dms` (the Philips UPnP media server, started by a boot
receiver; `org.droidtv.dlna` and `org.droidtv.contentexplorer` were kept). Every disabled package was still off
after a mains power cycle and again a week later.

- `org.droidtv.usagelogger` and `org.droidtv.demome` are the same `TV_INPUT_HARDWARE` false positives here and
  went in a batch of their own with `TVINPUT_ALLOW=1`.
- The remote's Top Picks button does nothing once `org.droidtv.nettvrecommender` is disabled. The owner kept it
  that way.
- The TV powers on to Live TV here too, and the owner kept that.
- The other lines (the rest of the NetTV store, the web apps, phone setup and the third-party preloads) were not
  run on this set.
- Hiding Developer options did not close ADB. After authorisations were revoked from the TV and the
  Developer options menu was hidden, port 5555 still accepted the computer used for the list as an authorised
  device after a mains power cycle. What closed it, a week later: turn Developer options on again, choose
  "Revoke USB debugging authorisations", then turn Developer options off. Straight after that,
  `tools/adb-closed.py` reported the connection refused while the TV was on. Confirm the port refuses the
  connection rather than trusting the menu.

## Undo

    TV_HOST=<tv ip> tools/restore.sh examples/philips-49pus6561/debloat.txt com.google.android.tvlauncher
