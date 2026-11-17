# Panasonic ATV R2 (SCBC R2, Android TV 11)

Notes from replacing the stock home screen on this set with the reference launcher, after applying
`examples/panasonic-atv-r2/debloat.txt`.

## Power-on lands on the last input

After the launcher became Home, the TV opened `com.android.tv` at the next power-on, because it had been on
HDMI 1 before the reboot. The boot log showed it:

```
logcat -b events -d | grep wm_set_resumed_activity
  ... com.realtek.tv.settings/.FallbackHome
  ... com.android.tv/.TvActivity
  ... com.android.tv/.MainActivity
```

HDMI and the tuner both play inside `com.android.tv`, so `power_on_home` covers it. Set `power_on_home` to
`true` in `launcher/app/src/main/res/values/config.xml` (or in a theme's `config.json`), and install with the
Home-button helper:

```bash
TV_HOST=<tv ip> HOME_KEY_SERVICE=1 launcher/build-and-install.sh
```

With that, the next boot showed Live TV for about four seconds before Home came back, and the helper was still
bound after the reboot. Leave both off if you would rather land on the last input.

## The helper's cost: held volume steps once

With HomeKeyService bound, holding Volume Up on the real remote moved the volume one step instead of ramping.
Without the helper it ramped. This is the same trade as `docs/devices/tcl-55p71b-g09-key-repeat.md`; the owner
kept the helper.

## Remote buttons

With the stock launcher disabled, the remote's Home, Input/Source and app buttons (Netflix, YouTube) all still
worked on the real remote with no key helper: on this Android TV build the firmware handles them, not the
stock launcher.

## Undo

```bash
TV_HOST=<tv ip> tools/restore.sh examples/panasonic-atv-r2/debloat.txt
```

Before that, put the Ambient screensaver back if you want it (`settings put secure screensaver_components
com.google.android.backdrop/.Backdrop`, and the same for `screensaver_default_component`).
