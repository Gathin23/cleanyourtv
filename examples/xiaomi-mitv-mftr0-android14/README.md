# Xiaomi MiTV-MFTR0 on HyperOS TV 3 (OS3.0.101.0, Android 14)

Companion to `debloat.txt` here. The same model's Android 11 build is
[`../xiaomi-mitv-mftr0/`](../xiaomi-mitv-mftr0/), and the two OS3 sets this one behaves like are
[`../xiaomi-mitv-astr0/`](../xiaomi-mitv-astr0/) (OS3.0.106) and
[`../xiaomi-mitv-mffu0/`](../xiaomi-mitv-mffu0/) (OS3.0.115). Input routes and settings intents for this
model are in [`docs/devices/xiaomi-mitv-mftr0-freeguy.txt`](../../docs/devices/xiaomi-mitv-mftr0-freeguy.txt).
The OS3.0.28 unit that could not move Home is
[`docs/devices/xiaomi-mitv-mftr0-hyperos3.md`](../../docs/devices/xiaomi-mitv-mftr0-hyperos3.md); this unit is a
third build (`OS3.0.101.0`) and the allowlist route below did take Home here.

Everything below was done over network ADB on one unit, on 2026-11-10, and re-read after a reboot.

## Home: the launcher has to be installed as `com.ts.launcher`

`cmd package set-home-activity` and `cmd role add-role-holder` both answer `Success` on this build and
change nothing: HOME is decided by a package-name allowlist, as on the other China-firmware Xiaomi sets
([device doc](../../docs/devices/xiaomi-mitv-mftr0-debloat-home.md)). The sequence that works here, in this
order:

```shell
cd launcher
./gradlew assembleRelease -PlauncherApplicationId=com.ts.launcher
# then push app/build/outputs/apk/release/app-release.apk and install it, e.g.
#   tools/tv.py 'pm install -r /data/local/tmp/tvhome.apk'
# its HOME component is com.ts.launcher/com.gathin.tvhome.MainActivity
cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
pm disable-user --user 0 com.mitv.tvhome
sync                       # let the state land before rebooting
```

HOME then resolves to `com.ts.launcher/com.gathin.tvhome.MainActivity`, and still does after a reboot. The
same APK under its default `com.gathin.tvhome` falls to `com.xiaomi.mitv.settings/.entry.FallbackHome` with
a black picture, so the identity is part of the recipe, not a preference.

Two things that cost time here:

- **Wait before rebooting.** `pm disable-user` returns before PackageManager writes the state down; a reboot
  inside that window brings PatchWall back and looks like the firmware re-enabling it. `sync` plus 15-30 s
  was enough.
- **`tools/theme.sh` cannot push a theme to this identity.** It hardcodes `com.gathin.tvhome` for the files
  directory, the `ThemeProvider` authority and the restart, so a launcher installed under `com.ts.launcher`
  gets the files in a directory it never reads and no restart. Until that is fixed, push by hand:

  ```shell
  adb push themes/<name>/config.json /sdcard/Android/data/com.ts.launcher/files/config.json
  tools/tv.py 'am force-stop com.ts.launcher'
  tools/tv.py 'am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n com.ts.launcher/com.gathin.tvhome.MainActivity'
  ```

  `launcher/build-and-install.sh` has the same assumption in its start/verify steps (`am force-stop`, the
  `am start -n`, the `set-home-activity` line and its `Home now resolves to ...` check all name
  `com.gathin.tvhome`), so on this set the handover was done by hand with the commands above.

## Inputs: the rows need `input_action` and the port numbers

Out of the box the Inputs pill opens its menu and every row does nothing. On this firmware no activity
answers `ACTION_VIEW` with the passthrough channel URI, so the launcher shows "Not available on this TV"
even though the hardware switches fine. The vendor entry is `com.xiaomi.mitv.tvplayer/.ExternalSourceActivity`
with an INTEGER extra `input`; the mapping is the one in
[`docs/devices/xiaomi-mitv-mftr0-freeguy.txt`](../../docs/devices/xiaomi-mitv-mftr0-freeguy.txt):

```json
{
  "input_action": "com.xiaomi.mitv.tvplayer/.ExternalSourceActivity",
  "input_sources": ["HW5=23", "HW6=24", "HW7=25", "HW8=-", "HW2=2"]
}
```

`HW5/6/7` are HDMI 1/2/3 (`input=23/24/25`), `HW2` is the AV/composite jack (`input=2`), and `HW8` is a
fourth input the framework registers with no physical connector: `=-` keeps it out of the menu. With this
in `config.json`, selecting HDMI 2 lands on `com.xiaomi.mitv.tvplayer/.ExternalSourceActivity` and Home
still comes back.

## What else is specific here

- Standby ads are `com.xiaomi.mitv.hyper.screensaver` itself (a plain Activity with its own downloaded
  carousel), so clearing the content sources does not stop it. `com.mitv.screensaver` and
  `com.xiaomi.mitv.advertise` do not exist on this build, and `boot_ad` is `0`.
- `com.mitv.overseaservice` exists here (it does not on the Android 11 build) and is in the list.
- Android 14 brings the AOSP ad-services modules (`com.android.adservices.api`,
  `com.android.ext.adservices.api`, `com.android.ondevicepersonalization.services`,
  `com.android.federatedcompute.services`), which the OS2 list cannot have.
- Kept on purpose: `com.xiaomi.mitv.smartpanel` (Menu-button panel), `com.xiaomi.voicecontrol` and
  `com.xiaomi.wakeupservice` (XiaoAI), `com.xiaomi.miplay`/`com.xiaomi.mitv.smartshare`/`com.xiaomi.mi_connect_service`
  (casting), `com.xiaomi.mitv.tvplayer` and `mitv.service` (TV app, HDMI, tuner) and `com.xiaomi.mitv.tvmanager`
  (XiaoAI "open <app>" runs through its provider — see the MFFU0 note).
- **ADB closes on every reboot** on this set: Settings → 账号与安全 → ADB 调试 re-opens it.
