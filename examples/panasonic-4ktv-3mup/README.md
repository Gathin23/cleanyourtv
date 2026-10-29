# Panasonic 4KTV-3MUP (Google TV 14): debloated, with the launcher on the Home button

MOKA device `R4_GTV`, Realtek `rtd2851m`, Google TV 14 / SDK 34, build `UKR1.260308`, Taiwan. Done over network
ADB on 2026-10-25, with a screenshot after every batch, a reboot and the real remote.

This is the platform in [`docs/devices/acer-r4g.md`](../../docs/devices/acer-r4g.md): `com.android.tv.settings`'
`FallbackHome` re-enables a fixed list of packages at every boot. After a reboot, 4 of the 24 packages first
disabled were back on (Prime Video, YouTube Music, `partnersetup`, calendar sync), so they are not in the list;
the other 20 stayed disabled.

## Debloat

```bash
tools/disable.sh examples/panasonic-4ktv-3mup/debloat.txt
```

20 packages, all disabled, none uninstalled. The header of [`debloat.txt`](debloat.txt) says what each group is,
what the firmware turns back on and what was kept on purpose.

Do not disable `com.google.android.apps.tv.launcherx` or `com.google.android.tungsten.setupwraith`
here: the firmware turns setupwraith back on at boot and its `RecoveryActivity` takes Home, and setupwraith also
owns the saved Wi-Fi network (`dumpsys wifi | grep -o 'cname=[^ ]*'`).

## Launcher

`set-home-activity` does not stick, so launcherx stays Home and the launcher takes the Home button through
`HomeKeyService` with `stock_home_redirect` ([`config.json`](config.json)).

1. `STOCK_LAUNCHER=none HOME_KEY_SERVICE=1 launcher/build-and-install.sh`. The script stops at its "Home
   resolves to launcherx" check and suggests disabling setupwraith: don't, on this set. Turn `HomeKeyService` on
   instead (Settings > Accessibility, or the `enabled_accessibility_services` lines in the script).
2. `tools/theme.sh examples/panasonic-4ktv-3mup`.

After a reboot the new launcher was back on screen in about 6 seconds, with no `RecoveryActivity`, and the set
did not open Live TV at power-on, so `power_on_home` is not needed. The D-pad, the Home button and the Inputs
menu (HDMI 1-3, AV, Live TV through `com.realtek.tv`) work.

Known trade-off: with `HomeKeyService` on, holding a volume button moves one step only
([`docs/devices/tcl-55p71b-g09-key-repeat.md`](../../docs/devices/tcl-55p71b-g09-key-repeat.md)).

[`config.json`](config.json): dock YouTube, Netflix, Hami Video, Animad, 4 columns, Live TV `com.realtek.tv` for
the Inputs menu; the apps the firmware re-enables (Prime Video, YouTube Music) and other stock entries hidden.

## Undo

```bash
tools/restore.sh examples/panasonic-4ktv-3mup/debloat.txt
tools/theme.sh --reset
```

Uninstall the launcher (`pm uninstall com.gathin.tvhome`) to give the Home button back to launcherx.
