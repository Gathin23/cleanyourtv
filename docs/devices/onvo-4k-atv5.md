# ONVO 4K TV (Expressluck sepolia, Google TV 14): same-day notes, Google TV home kept and redirected

Observed on one television on 2026-11-14. This is a single-session report with one reboot, not a several-day
debloat list. It is the same Expressluck `sepolia` board as
[`examples/tesla-q55e655gus`](../../examples/tesla-q55e655gus/debloat.txt) (Google TV 14, UKN6) and
[`examples/aipro4k`](../../examples/aipro4k/debloat.txt) (Android TV 14, UKN7). Its firmware build differs from
both, and this owner kept the stock home and the setup wizard enabled.

| Field | Observed value |
|---|---|
| Retail brand / manufacturer | ONVO / Expressluck |
| Android model / product / device | ONVO TV / `ONVO_4K_ATV5` / `sepolia`, MediaTek |
| System | Google TV on Android 14, SDK 34 |
| Firmware build | `UKN6.260116.014`, security patch 2026-01-01 |
| RAM / display | MemTotal 1840784 kB; panel 3840 × 2160, UI 1920 × 1080 at 320 dpi (`wm size` override, left alone) |
| Stock Home | `com.google.android.apps.tv.launcherx` |
| Live TV | `com.mediatek.tv.oneworld.tvcenter` (set as `live_tv_package`) |

## Connecting

Network ADB is Wireless debugging only: port 5555 refuses, `ro.adb.secure` is `1`. The set advertises
`_adb-tls-connect._tcp` over mDNS. After one `adb pair`, the tools reached it through the paired connection with
`TV_ADB_BINARY=adb` ([`tools/native-adb.md`](../../tools/native-adb.md)), without `adb tcpip`. Wireless
debugging was off again after the reboot, and its port was new when the owner switched it back on.

## Saved Wi-Fi and Cast

- `dumpsys wifi` shows the saved network owned by `com.google.android.tungsten.setupwraith`, so `tools/disable.sh`
  would skip the wizard.
- `dumpsys package com.google.android.tungsten.setupwraith` lists a
  `...setupwraith.libs.castinfo.CastInfoProvider`, as on the Chromecast and Streamer builds in
  [Troubleshooting](../TROUBLESHOOTING.md).
- The Cast receiver here signs with `jni_client_auth_creds_mediadrm` (logcat `cast_shell`), the path the TCL Q7C
  casts with after the wizard is off. Whether this set casts with setupwraith disabled was **not** tested.

## Packages disabled in this session

Applied in one batch with [`tools/disable.sh`](../../tools/disable.sh), all to `disabled-user`, read back after a
reboot. Nothing was uninstalled. This is a record of one unit, not a validated list:

```text
tv.anoki.acr.anokiacroptin
com.android.adservices.api
com.android.ondevicepersonalization.services
com.android.federatedcompute.services
com.google.android.feedback
com.android.tv.feedbackconsent
com.google.android.partnersetup
com.mediatek.tv.launcher.partner
com.mediatek.tv.retaildemo
com.google.android.play.games
com.google.android.syncadapters.calendar
```

Kept by the owner's choice: voice search `com.google.android.katniss`, Cast `com.google.android.apps.mediashell`
and `com.mediatek.dialservice`, `com.mediatek.miracast`, the karaoke services `com.audiocn.karaoke.global.micmanager`
and `com.loostone.tuning`, YouTube Music and the `com.google.android.apps.tv.dreamx` screensaver. Also kept: the
updater `com.mediatek.tv.systemupdater`, the TV middleware and inputs, remote and Bluetooth pairing, and every
factory or test package (none was opened).

Already disabled before the session, left as found: `com.cltv.hybrid`, `com.cltv.mal`,
`com.at.factory.androidtvaidlservice`, `com.expressluck.factorymanager`, `com.ctv.factorytest`,
`com.mediatek.dtv.tvinput.isdbtuner`, `com.mediatek.dtv.tvinput.atsctuner`, `com.mediatek.opapp`, and
`com.netflix.ninja` as `disabled-user`. Netflix read **enabled** (state 1) after the reboot, although nothing in
the session enabled it.

## Home: launcherx kept, redirected

To keep the saved network and Cast without the rejoin, launcherx and setupwraith stayed enabled. The launcher took
Home through the redirect in [`examples/sony-bravia-vu3/README.md`](../../examples/sony-bravia-vu3/README.md):
`stock_home_redirect` set to launcherx, `key_filter` false, `restore_key_services` true, plus `live_tv_package`
above. It was installed with
`STOCK_LAUNCHER=none RESTORE_KEY_SERVICES=1 HOME_KEY_SERVICE=1` [`launcher/build-and-install.sh`](../../launcher/build-and-install.sh).

- `cmd package set-home-activity` printed `Success` and Home kept resolving to launcherx, as on the Tesla. The
  script stopped at its Home check, as the VU3 notes expect, before it enables the helper. `HomeKeyService` was then
  enabled by hand, and `WRITE_SECURE_SETTINGS` granted by hand for `restore_key_services`.
- A later reinstall the same way left `enabled_accessibility_services` at `null`: the helper had to be listed again
  by hand.
- From an app, Home landed on `com.gathin.tvhome/.MainActivity` after a brief frame of launcherx. After the
  reboot, `wm_set_resumed_activity` went `FallbackHome`, launcherx, then the launcher. The Inputs menu listed HDMI 1–3,
  AV and Live TV.
- With `app_library` on, a sideloaded phone browser (no `LEANBACK_LAUNCHER` activity) was added to Home and opened from its tile.
- Confirmed by the owner on the physical remote and a phone: Home from inside an app opens the launcher, held
  Volume keeps repeating (`key_filter` false, so no key filtering), the remote's app buttons still open their apps
  (launcherx answers them), and casting from a phone works with setupwraith enabled.

A second Android user (id 10, not running) exists on this set. The session only changed user 0.
