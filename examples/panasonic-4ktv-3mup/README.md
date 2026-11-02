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

20 packages, all disabled, none uninstalled; 14 more from a second unit (below) are in the list commented out until the HDMI inputs are checked. The header of [`debloat.txt`](debloat.txt) says what each group is,
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

## Second unit: TH-65MX660DX (India)

Retail Panasonic TH-65MX660DX 65", which reports itself as `4KTV-3MUP` (`PANASONIC_NFF`, `R4_GTV`) on the older
build `UKR1.250803`, 2 GB. Done over network ADB on 2026-10-26 in six batches with a screenshot after each, then a
cold boot, and lived with for 6 days.

- The 14 packages in the list's last group stayed disabled after the cold boot and since. Of the first unit's 20,
  ten were disabled here too and stayed off, and `feedbackconsent` was already off from the factory. The Android
  ad services, Health Connect and `backupconfirm` are installed but were not tried on this set, and the four
  Taiwan apps are not installed.
- More came back at boot than the R4G note lists: besides its boot list, `onetimeinitializer`,
  `presetup.bluetoothpair`, `initsetup`, `talkback`, `partnersetup`, calendar sync, `mediashell` (Chromecast) and
  `com.apps.biz.ds` were enabled again, so none of them are in the list.
- The same launcher setup works: launcherx and setupwraith enabled, `HomeKeyService` with `stock_home_redirect`.
  The Home button on the real remote returns to the launcher, also from inside apps. The HDMI inputs were not
  checked on this unit with these packages off.
- Several of the apps the firmware turns back on have tiles on Home: AudioLink, FastCast, Meeting Mode, System
  Upgrade, TV Manager, World Clock, Play Games and Play Movies. `hidden_apps` keeps them off the grid
  (`com.apps.function.audiolink`, `com.creative.fastscreen.tv`, `com.apps.biz.mt`, `com.apps.ota`,
  `com.apps.tvmanager`, `com.mk.tv.timeclock`, `com.google.android.play.games`, `com.google.android.videos`).
- The remote's Settings (gear) button sends keycode 83 (`KEYCODE_NOTIFICATION`, scancode 501 on the
  `IR_panasonic` remote), which only the stock Google TV home answered. `"settings_key_keycodes": [83]` (a theme's
  `config.json` or `config.xml`) makes `HomeKeyService` open TV Settings with it; checked with the real remote.
- A force stop or Clear data of the launcher switches `HomeKeyService` off, and Home goes to launcherx until it is
  back on; `restore_key_services` with `WRITE_SECURE_SETTINGS` granted puts it back when Home starts.

## Undo

```bash
tools/restore.sh examples/panasonic-4ktv-3mup/debloat.txt
tools/theme.sh --reset
```

Uninstall the launcher (`pm uninstall com.gathin.tvhome`) to give the Home button back to launcherx.
