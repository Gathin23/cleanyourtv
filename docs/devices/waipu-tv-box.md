# waipu.tv Box (SEID00BWP, Android 14): `pm disable*` is refused, the launcher replaces the waipu app as Home

One session on 2026-10-29, same-day results only. That is why this is recorded here and not in `examples/`:
CONTRIBUTING asks for a few days of living with a list. The box is the Android 14 successor of the
[waipu.tv Stick](waipu-tv-stick.md) and behaves the same way in most of what follows.

## Identify the device

| Field | Value |
|---|---|
| Retail name | waipu.tv Box (`ro.product.model` `waipu.tv Box`) |
| Maker | SEI (`ro.product.manufacturer`), device and board `SEID00BWP`, hardware `amlogic` |
| Android | 14 (SDK 34), build `UKG3.250115.001.2396` |
| Fingerprint | `waiputv/SEID00BWP/SEID00BWP:14/UKG3.250115.001/2396:user/release-keys` |
| Screen | 1920x1080, density 320 |
| ADB | port 5555 after one prompt; `ro.adb.secure=1` |
| Vendor services | `com.nes.coreservice`, `com.nes.tvglobalkeyhandler`, `com.nes.blerc`, `com.nes.remoteota.waipu`, `com.nes.skywayclient`, `com.nes.leanbacklauncher.partnercustomizer` |

## `pm disable-user` is refused

`tools/disable.sh` with five harmless packages (`android.autoinstalls.config.sei`,
`com.android.adservices.api`, `com.android.backupconfirm`, `com.android.cameraextensions`,
`com.android.cts.ctsshim`) got `Warning! This command is illegal!` for every one, and
`pm list packages -d` stayed empty, as on the stick and the
[Nokia Streaming Box 8000](../../examples/nokia-streaming-box-8000/README.md).

`pm uninstall --user 0` is refused too for the preinstalled apps:
`Warning! com.google.android.play.games is a core application in the system, it can not be uninstalled!`,
and the same for `com.zdf.android.mediathek`.

What works, as on the stick: `pm suspend --user 0 <pkg>` (`new suspended state: true`) together with
`cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore` and `cmd appops write-settings`. Suspended here, by
the owner's choice: `com.google.android.play.games` and `com.zdf.android.mediathek`. Not yet checked
after a reboot on this box.

Undo, per package: `pm unsuspend --user 0 <pkg>` and `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND allow`.

## Home

As on the stick, the waipu app is the stock launcher. Before the install, `de.exaring.waipu/.MainActivity` and
Settings' `com.android.tv.settings/.system.FallbackHome` (priority -1000) were the only HOME claimants, so there
is nothing to disable, and the waipu app must stay enabled. Install with:

```sh
STOCK_LAUNCHER=none HOME_KEY_SERVICE=1 TV_HOST=<box ip> launcher/build-and-install.sh
```

`set-home-activity` took: `cmd package resolve-activity` and `mFocusedApp` both named
`com.gathin.tvhome/.MainActivity`, and the Home button opens the launcher. The waipu app starts normally
from the dock or the app grid.

The waipu app still has the boot receiver `de.exaring.waipu/.entrypoint.RichBootReceiver` and
`START_ACTIVITIES_FROM_BACKGROUND` (`granted=true`), which on the stick bring it up over the launcher after
a cold boot. This box runs the launcher with `HomeKeyService` and this in the theme's `config.json`:

```json
"live_tv_package": "de.exaring.waipu",
"power_on_home": true
```

so that a waipu window that opens by itself after boot or screen-on goes back to Home until the first key
press. Unlike `stock_home_redirect`, this does not get in the way when you open waipu on purpose.
**Not yet checked after a cold boot**, and also not yet checked whether the launcher survives a reboot:
`com.nes.coreservice` is on this box, which on the TiVo Stream 4K removes sideloaded HOME apps at boot
(see the [TiVo notes](../../examples/tivo-stream-4k/README.md#the-launcher)); on the stick it did not.

## Clock

The box came with `time_12_24` set to 12 hours although the UI is German, so the launcher showed `6:54 PM`.
The launcher clock follows the system setting:

```sh
TV_HOST=<box ip> tools/tv.py 'settings put system time_12_24 24'
```
