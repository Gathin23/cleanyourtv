# NRK Radio on Android TV

NRK Radio is an Android phone/tablet app with live NRK radio, podcasts and
programme recordings. Its [official Google Play listing](https://play.google.com/store/apps/details?id=no.nrk.mobil.radio)
does not offer it to this Android TV device, but the app can still run when
sideloaded.

## Tested package

| Field | Result |
|---|---|
| Package | `no.nrk.mobil.radio` |
| Version tested | `2026.10.1` |
| Version code | `106278` |
| Minimum SDK | `29` |
| Target SDK | `36` |
| Tested architecture | `armeabi-v7a` |
| Tested TV | Nokia UNE43GV210, Android 11 / SDK 30 |
| Result | Installed and launched successfully |

The TV is 32-bit ARMv7 and Android 11, so the app's minimum SDK and ABI are
compatible. Google Play's rejection is therefore a TV/device compatibility
declaration rather than an Android-version failure.

## Installation

The tested package was an ARMv7 XAPK containing these three APKs:

```text
no.nrk.mobil.radio.apk
config.armeabi_v7a.apk
config.mdpi.apk
```

Install all splits together; installing only the base APK is incomplete:

```sh
adb -s <tv-ip>:5555 push no.nrk.mobil.radio.apk /data/local/tmp/nrk-radio-base.apk
adb -s <tv-ip>:5555 push config.armeabi_v7a.apk /data/local/tmp/nrk-radio-abi.apk
adb -s <tv-ip>:5555 push config.mdpi.apk /data/local/tmp/nrk-radio-density.apk
adb -s <tv-ip>:5555 shell pm install -r \
  /data/local/tmp/nrk-radio-base.apk \
  /data/local/tmp/nrk-radio-abi.apk \
  /data/local/tmp/nrk-radio-density.apk
```

The launcher activity is:

```text
no.nrk.mobil.radio/.MainActivitySwitch
```

Launch it with:

```sh
adb -s <tv-ip>:5555 shell am start -n no.nrk.mobil.radio/.MainActivitySwitch
```

## TV behaviour and limitations

The app opened successfully to its Norwegian home screen and displayed Home,
Explore, Live and Login sections. The app exposes a normal launcher activity,
but it is not a TV-optimized app and may not appear in a launcher row that only
lists `LEANBACK_LAUNCHER` applications. It may still be available through the
full app library or by launching the activity directly.

The remote-control navigation was not fully tested. Casting from the phone or
using the TV's Bluetooth/USB keyboard remains a fallback if a screen control
does not accept D-pad focus.

## Plume icon shortcut

Plume did not show the installed app because NRK Radio has no
`LEANBACK_LAUNCHER` activity. The repository now contains a small wrapper at
[`addons/nrk-radio-shortcut`](../../addons/nrk-radio-shortcut/). It supplies a
TV launcher category and an NRK Radio-style icon, then opens the real
`no.nrk.mobil.radio` app. It does not copy, modify or replace NRK Radio.

The shortcut was built and installed as:

```sh
./gradlew assembleDebug
adb -s <tv-ip>:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

The shortcut package is `no.cleanyourtv.nrkradioshortcut`. It uses the real NRK
Radio icon extracted from the installed NRK package. It was verified on the
Nokia TV: Plume displayed **NRK Radio** at the far-right end of the app row
(initially just beyond the visible edge), and launching the shortcut opened
`no.nrk.mobil.radio/.ComposeMainActivity`.

After installation, remove the temporary APKs and disable ADB debugging again:

```sh
adb -s <tv-ip>:5555 shell rm -f /data/local/tmp/nrk-radio-*.apk
adb -s <tv-ip>:5555 shell settings put global adb_enabled 0
```

This note records a sideload workaround, not a Play Store compatibility fix;
future NRK Radio updates may need the same process or may change compatibility.
