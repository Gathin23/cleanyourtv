# Cellecor smart TV (MediaTek MT9255T board), India

Android 9 (SDK 28), firmware `mt5862-userdebug 9 PPR2.180905.006.A1 TVOS-04.16.031.01.12 dev-keys`,
`ro.product.model` `mt9255t_ek_zsn3`, `ro.product.device` `mt5862`, `ro.product.manufacturer` `MediaTek`; nothing in
`getprop` says Cellecor, and the owner could not find a model number. About 925 MB of RAM, 1280x720 at 240 dpi. The
firmware line is the same as the [Wanbo T6R Max](wanbo-t6r-max.md) projector's (`TVOS-04.16.031.01.12`, Zeasn
Whale launcher), with the TV vendor's own `com.vstv.*` apps around it.

This is a one-session report, not a several-day list. Eight packages were disabled in four batches, the launcher
took Home, and all of it held through two reboots.

## Network ADB

Port 5555 (`service.adb.tcp.port` and `persist.adb.tcp.port` both `5555`), not 5877 as on the Wanbo. Before USB
debugging was on, the TV answered ping and nothing on 5555; after, it advertised `_adb._tcp` over mDNS as
`adb-unidentified` on `Android.local`, which is the quickest way to find it. `ro.adb.secure=0`: there is no
"Allow USB debugging?" prompt. With USB debugging switched off, `tools/adb-closed.py <tv ip>` reported 5555 closed.

The set dropped off Wi-Fi for a few seconds every few minutes during the session (ping fails, then it is back);
commands sent in that window time out. Retry rather than read it as a refused command.

## Home

- Stock launcher: `com.zeasn.whale.saas` (Zeasn Whale, a banner carousel and sponsored rows fetched from Zeasn on
  every Home). The only other HOME claimant is `com.vstv.fbhome/.FallbackHome`.
- `launcher/build-and-install.sh` does not know this launcher, so install with `STOCK_LAUNCHER=none`. Home resolved
  to `com.gathin.tvhome` at once, but while Zeasn was enabled it started its own `MainActivity` (from its own uid)
  about 30 ms after every Home press and covered the new launcher. Disabling `com.zeasn.whale.saas` with
  `tools/disable.sh` ended that; Home held from inside other apps and after both reboots, and the set powers on to
  Home (`am_set_resumed_activity` on this Android 9 build), not to Live TV.
- Remote buttons: the owner pressed Home, Input and the app buttons on the real remote after the handover and all
  worked with neither key helper enabled. `com.vstv.global.service/com.vstv.accessibility.RMService` is an enabled
  accessibility service on the stock set; it was left alone.

## Settings

`android.settings.SETTINGS` resolves to the vendor app `com.vstv.septembersettings/.TheActivity` ("System
Setting": network, picture, sound), so the Settings pill opens that. The AOSP TV settings
`com.android.tv.settings/.MainSettings` is installed and enabled but has no filter for the standard action, and
the owner wanted it reachable. Adding it to `settings_fallback_components` in
`launcher/app/src/main/res/values/config.xml` (a `config.xml`-only key) gives the held-Settings menu a **Native
settings** row that opens it; a short press stays on the vendor app.

```xml
<item>com.android.tv.settings/com.android.tv.settings.MainSettings</item>
```

## Apps without a leanback entry

The preinstalled Prime Video (`com.amazon.avod.thirdpartyclient`), the vendor's USB player
(`com.vstv_india.mediacenter`) and the Netflix build on this set (`com.netflix.mediaclient`) declare only
`LAUNCHER`, so they get no tile until `app_library` is on and they are listed in `library_apps`; they can then go in
`dock_apps`.

## Wi-Fi name in the launcher

As on the Wanbo, location is off and the network pill says "Wi-Fi". Here `settings put secure location_mode 3`
alone changed nothing: `location_providers_allowed` stayed empty. `settings put secure location_providers_allowed
+gps` and then `+network` turned it on and the pill showed the network name (no location provider is installed, so
nothing is located). Undo with `-gps`, `-network` and `location_mode 0`.

## Testing a held OK

`input keyevent --longpress` sends the down and up in the same instant, so the launcher reads it as a click. The
shell user is in the `input` group here, and writing the IR receiver's own scancode holds the key for real. OK is
scancode 28 in this set's layout (`/vendor/usr/keylayout/Vendor_3697_Product_0001.kl`, `key 28 DPAD_CENTER`), not
352 or 353 as in `Generic.kl`:

```bash
tools/tv.py 'sendevent /dev/input/event0 1 28 1; sendevent /dev/input/event0 0 0 0; sleep 1.2; sendevent /dev/input/event0 1 28 0; sendevent /dev/input/event0 0 0 0'
```

## Packages disabled (one session, two reboots)

```text
# Batch 1
com.android.browser                 # stock browser, ran in the background
com.android.soundrecorder
com.vstv.tv.assistant               # "TV Assistant" preload; nothing else uses it
# Batch 2 (owner does not use them)
com.vstv.mcast                      # Miracast / screen mirroring
com.vs.phoneremote.google           # phone-as-remote service; PERSISTENT (see below)
# Batch 3, after the new launcher was confirmed as Home
com.zeasn.whale.saas                # stock launcher
# Batch 4
in.startv.hotstar.dplus.tv          # 2023 Disney+ Hotstar preload (Google Mobile Ads, Firebase); the current JioHotstar is a separate package
zeasn.open.technical.tv.store       # Zeasn app store: starts at boot, network receiver, updated itself silently
```

`com.vs.phoneremote.google` carries the `PERSISTENT` flag: `pm disable-user` records it as `disabled-user`, but its
process was running again after the reboot. `com.google.android.tv.remote.service` ships disabled.

## Kept on purpose

- Load-bearing, checked with `dumpsys package`: `com.vstv.septembersettings` (the settings app, Wi-Fi included),
  `com.vstv.global.service` (remote keys, hotkeys, the accessibility service above), `com.vstv.input` (the Input
  menu, `TV_INPUT_BUTTON`), `com.vstv.fbhome` (FallbackHome), `com.mediatek.autopair` (Bluetooth remote pairing,
  persistent), `com.actions.hidaudio` (the remote's mic), `com.mediatek.tvinput`,
  `com.mediatek.tvinputservice.arbitratorservice`, `com.mediatek.wwtv.tvcenter`.
- Firmware and app updates: `com.vstv.ota` (firmware) and `com.vstv.upgradecenter`, which starts at boot and pushes
  updates to the vendor's system-uid apps (`com.vstv.septembersettings`, `com.vstv.global.service` and
  `com.vstv_india.mediacenter` are all `UPDATED_SYSTEM_APP`).
- Voice search: `com.google.android.katniss`, `com.vs.phone.remote.search.assistants`.
- Factory: `com.vstv.factory` and `com.vstv.factory.test` (factory menu, factory test with a TV input of its own).
  Never opened, not disabled.

No dedicated vendor telemetry package (a stats or log uploader) was found; that check reads the declared
components, not the code.

## Inputs

The Inputs pill lists HDMI 1, HDMI 2 and two rows called AV: the board registers two composite inputs
(`TvInputHardwareInfo` type 3), so both are the set's own, not a launcher duplicate.
