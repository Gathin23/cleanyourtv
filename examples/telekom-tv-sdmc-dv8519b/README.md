# Magyar Telekom TV box (SDMC DV8519B)

Magyar Telekom's operator IPTV set-top box: SDMC DV8519B, Amlogic, 2 GB RAM, Android TV 12 (SDK 31),
firmware `TelekomTV/TelekomTV-STB/DV8519B-Telekom:12/ST/12.1.153`. Built-in Ethernet. Network ADB on
port 5555, enabled from Developer options.

## Only on a box you own

This is operator hardware: Magyar Telekom supplies the box to subscribers rather than selling it. If the
box is not yours to keep, changing it may breach your subscriber agreement, and the operator could hold
you responsible for hardware returned damaged or not in its original state. Only debloat a box you own
outright. Everything here is disabled, never uninstalled, and
`tools/restore.sh examples/telekom-tv-sdmc-dv8519b/debloat.txt` puts it all back; back the box up first and
restore it fully before returning leased hardware. This is not legal advice.

## Apply

```sh
TV_HOST=<tv ip> tools/disable.sh examples/telekom-tv-sdmc-dv8519b/debloat.txt
```

Undo any time:

```sh
TV_HOST=<tv ip> tools/restore.sh examples/telekom-tv-sdmc-dv8519b/debloat.txt
```

Everything is disabled, never uninstalled.

## What it disables

Google feedback and recommendation rows, Play Games, the print spooler, the Ambient and basic screensavers, calendar sync and its provider, the SDMC partner auto-installer,
and the Telekom extras: the TV tester, the self-care app, and `com.tataelxsi.icx` (iCX device management and
telemetry over Firebase/Kinesis).

## Kept

- Every `com.sdmc.*` hardware service: remote and Bluetooth pairing, LED, standby, EDID, global keys.
- `com.dt.onetv.hook`, the first-time setup hook.
- The firmware and Telekom app updaters (`com.dt.dmsapps.updater` can re-enable a disabled Telekom app).
- Chromecast.
- YouTube Music and the Assistant: left commented out in the list (the author has them off by choice).
- `com.telekom.onetv.tv` — this is both the Live TV app (live TV, guide, recordings) and the stock launcher.
  The list leaves it commented out: disable it only on a streaming-only box, since turning it off leaves no
  live TV and no stock home screen to fall back to.

## Notes

- Reboot-checked: nothing re-enabled itself across a reboot.
- AdGuard Private DNS works on this box.
