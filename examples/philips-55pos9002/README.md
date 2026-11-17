# Philips 55POS9002/12 (TPM171E, Android TV 8.0)

Notes from one set, on firmware `TPM171E_R.107.001.143.000` (build 4718). The list and its header are in
[`debloat.txt`](debloat.txt).

## Connecting

USB debugging alone opens ADB on TCP 5555. The "Allow USB debugging?" prompt did not appear at first:
`tools/tv.py` reached the RSA public-key step of the handshake, but nothing showed on screen. Revoking USB
debugging authorisations, switching debugging off and on, and pressing Home were not enough. A power cycle at
the mains (not standby) made the prompt appear, and `tools/tv.py` then connected. This is the same step as on the
[65PUS8102](../philips-65pus8102/debloat.txt).

## Netflix button

The Netflix button sends scancode 632 (`key 632 NETFLIX` in `/system/usr/keylayout/TPV_MutilRC.kl`), which
`org.droidtv.GlobalKey` turns into a `NETFLIX_KEY` broadcast. With Netflix disabled the button does nothing.
`HomeKeyService` does see the key, so an `app_keys` entry by scancode sends it to another app, from Home and from
inside other apps:

```json
"app_keys": ["scan:632:org.jellyfin.androidtv"]
```

Install with `HOME_KEY_SERVICE=1` (`-HomeKeyService` on Windows). `InputKeyService` isn't needed; on this set it
would also take the Source button away from the Philips source menu, which works on its own. With the helper
bound, holding volume up still ramped.

## The remote's QWERTY keyboard

The remote has a keyboard on its back. When it is turned over, the TV logs `KEYBOARD SIDE` and scancodes
246/245 over infrared, and the remote connects as a Bluetooth HID keyboard named `PHLRC`, which uses
`/system/usr/keylayout/TPV_SMTRC.kl` with a full letter layout. On this set the letter keys sent no events at
all on that device (`getevent` on its node stayed silent while typing), so nothing typed. That points at the
remote's pairing, not at the keymaps or the input method. Re-pairing the remote is under Settings > Wireless and
networks > Remote control. This was not caused by the list: it had stopped working after an earlier firmware
update.

## A stale accessibility entry

`enabled_accessibility_services` already named
`org.droidtv.nettvadvert/...AdvertisementImpressionAccessibilityService` before anything was changed, but no
`org.droidtv.nettvadvert` package is installed on this firmware. The entry does nothing. A launcher key helper
is added after it in the same setting.
