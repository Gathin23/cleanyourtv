# Wanbo Dali 1 projector (WPD11, HiSilicon Hi3751V350)

Android 9 (SDK 28), firmware `wanbo-WPD11-KCHE-V1.0.7-20241021`, about 1 GB of RAM (`MemTotal` 1,005,004 kB),
1280x720 at 160 dpi. `ro.product.model` is `WPD11`, `ro.product.device` is `Hi3751V350` and
`ro.product.manufacturer` is `Hisilicon`. The stock home is Zeasn's (`com.zeasn.launcher`), with Google Play Store
and Play Services, not Google TV.

This is a one-session report, not a several-day list. Twelve packages were disabled in four steps, the launcher
took Home, and both held after a reboot (`pm list packages -d` was the same before and after).

## ADB

- Developer options has USB debugging but no Network debugging switch. USB debugging alone opens port 5555, so
  `TV_HOST=<tv ip> tools/tv.py --probe` works with no other step.
- `ro.adb.secure=1`: the projector shows "Allow USB debugging?" on the first connection.
- Close it with the same USB debugging switch, then check with `tools/adb-closed.py <tv ip>`.

## Home

- Stock launcher: `com.zeasn.launcher` (banner carousel and "Top picks" rows). The only other HOME claimant is
  `com.android.tv.settings` (`FallbackHome`).
- `launcher/build-and-install.sh` does not know this launcher. `STOCK_LAUNCHER=com.zeasn.launcher` worked: the
  script set the new launcher as Home, then disabled the stock one. Pass `com.zeasn.launcher` to
  `tools/restore.sh` when undoing.
- The Netflix app on this unit is the phone app `com.netflix.mediaclient`, with no leanback entry. It shows as a
  tile, and in `dock_apps`, only when it is also in `library_apps` in `config.xml`.
- Two apps answer `android.settings.SETTINGS`: `com.android.tv.settings` and the projector's own
  `com.kystar.projector.zeasn.settings` (keystone, focus, picture). The Settings pill opens Android's chooser;
  select one and push "Always".

## HDMI input

`dumpsys tv_input` lists no inputs. HDMI is the HiSilicon player `com.hisilicon.tvui/.MainActivity`. Its `MAIN`
filter has no `DEFAULT` category and the app has no HDMI action, so `live_tv_package` with `live_tv_action` cannot
start it. The source panel keys start it by component, and the Inputs pill then opens HDMI directly:

```xml
<string name="live_tv_package" translatable="false"></string>
<string name="source_panel_service" translatable="false">com.hisilicon.tvui/.MainActivity</string>
<string name="source_panel_action" translatable="false">android.intent.action.MAIN</string>
```

`com.hisilicon.tvui` also goes in `hidden_apps`. These keys are `config.xml` only, so set them before building.

At every power-on the firmware opens the HDMI player over Home by itself (`am_create_activity` for
`com.hisilicon.tvui/.MainActivity` about one second after Home). This owner kept it. `power_on_home_always` with
`HOME_KEY_SERVICE=1` is the launcher's way to land on Home instead; it was not tried here.

## Screensaver and sleep

- The screensaver is `com.kystar.dream/.DreamSceneService` (`screensaver_enabled=1`). It has `INTERNET` and was
  disabled below. Then turn the screensaver off, or choose another one:
  `settings put secure screensaver_enabled 0` (undo: `1`).
- `setting_standby_mode=shutdown` and `persist.sys.suspend.mode=shutdown`: sleep on this firmware is probably a
  full shutdown. Not checked yet.
- The HDMI player holds a `SCREEN_BRIGHT_WAKE_LOCK` while it shows "No Signal or Weak Signal", so
  `screen_off_timeout` never starts on that screen.

## Packages disabled (one session)

```text
# Tracking: TopAppTrackingService, starts at boot with PACKAGE_USAGE_STATS, GET_TASKS and INTERNET
com.kystar.middleware.track
# Network screensaver (see above)
com.kystar.dream
# Casting this owner does not use (AirPlay, com.ecloud.eairplay, kept)
com.hisilicon.miracast
com.ecloud.eshare.server
com.ecloud.emedia
# Bluetooth speaker mode and the Zeasn app store
net.kystar.btspeaker
zeasn.open.technical.tv.store
# Preloaded apps
com.android.browser
com.android.soundrecorder
com.android.bips
# Zeasn device portal and firmware updater ("ZxOta"): starts at boot with INTERNET, REBOOT and INSTALL_PACKAGES.
# Disabled at the owner's request: no firmware update came in years. Leave it on to keep getting updates.
com.zeasn.deviceportal.asdprovider.kystar_hisi352
# Stock launcher, disabled by launcher/build-and-install.sh after the new one was confirmed as Home
com.zeasn.launcher
```

## Kept on purpose

- HDMI and picture: `com.hisilicon.tvui`, `com.hisilicon.tv.service`, `com.hisilicon.tvsetting`.
- Projector: `com.kystar.projector.focus` (autofocus and keystone), `com.kystar.focus.movecheck` (refocus after
  the projector moves), `com.kystar.projector.zeasn.settings` (quick menu, keystone, power-off screen).
- Remote: `com.kystar.bleconnect` and `com.kystar.projector.zeasn.setupwizard` (it holds the remote pairing
  screen).
- Not clear from `dumpsys package`, so left on: `com.ksd.framework` (`KsdSystemService`), `com.hisilicon.rmservice`,
  `com.kystar.wifiap` (hotspot service).
- Factory and test packages, never opened: `com.hisilicon.tv.menu`, `com.kystar.projector.test.facteory`,
  `com.sdt.factorytest`.
