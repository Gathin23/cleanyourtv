# Second unit: Nikkei DVB91NK

A second set on the same platform and firmware as the list above: **Nikkei DVB91NK** (`AI PONT`, built by Changhong,
device `ikebukuro`, MediaTek/MStar `m5621`), Android TV 11, build `RTO2.250804.001`, Italy, Wi-Fi, 0.9 GB RAM, 4.2 GB
data partition, 32-bit ARM. One evening of use, one reboot after the cleanup.

## Also reported: Changhong AI PONT / DVB91

An owner reports a **Changhong AI PONT / DVB91** with the same device (`ikebukuro`) and build (`RTO2.250804.001`,
incremental `13882904`), Android TV 11 / API 30, Italy, 32-bit ARM (`armeabi-v7a`), about 0.9 GB RAM and a 4.2 GB data
partition. Which list was applied there isn't recorded, so read it as a model match for this folder, not a second test of it.

## What was disabled

Batches 1 and 2 of `debloat.txt` were applied (batches 3 and 4 were not), with these differences:

- Kept on purpose (the owner's family uses them): Prime Video, the Assistant,
  Chromecast and Miracast/AirPlay (batch 3, none of it applied) and Mediaset Infinity (`it.mediaset.infinitytv`,
  in batch 4).
- Batch 5 is new, from this unit.
- Also disabled by the owner's choice, not in the list: `com.homwee.dmt.upgrade` (firmware updater; keeping it enabled
  is preferred), `com.google.android.tungsten.setupwraith` and `com.google.android.youtube.tv`.
  - setupwraith: `dumpsys wifi` showed no saved network owned by it; Home stayed on the launcher afterwards and
    after a reboot.
  - `com.google.android.youtube.tv` was disabled only after SmartTube was installed and played a video.
- YouTube Music (`com.google.android.youtube.tvmusic`, in batch 2) is disabled, by the owner's own choice and by
  hand, not as a side effect of disabling `com.google.android.youtube.tv`. It was left disabled.
- 27 packages are disabled in total, 2 of them already off before the cleanup. The list was identical after a reboot.

## Launcher

`launcher/build-and-install.sh` set `com.gathin.tvhome` as Home (checked with `mFocusedApp`), then `tools/theme.sh`
pushed the `scenes` theme. Weather Clock was set as the screensaver and forced with
`am start -n com.android.systemui/.Somnambulator`; the weather emoji draw correctly with the stock
`NotoColorEmoji.ttf`.

- `live_tv_package` stays `com.mediatek.wwtv.tvcenter`. To show it as a dock tile, remove it from `hidden_apps`:
  the default list hides it because the Inputs menu already covers Live TV.
- `advanced_effects` off, on a set this small.

## The remote's Settings button, and held volume

The Settings button arrives as `KEYCODE_SETTINGS` (176, scancode 92). `com.mediatek.tv.service` (`HotKeyDispatcher`)
opens TV Settings itself, so the launcher only receives the key through its key helper: `settings_key_keycodes` `[176]`
with the filter on. `getevent -lt` prints this key as `KEY_HENKAN`; the Android keycode is not 214.

While a key-filtering helper is bound, a held volume key moves one step instead of ramping. That holds with
`key_filter` true and also with `consume_volume_keys` true (the remote sends one down and one up with no repeat events
in between, and the helper's own repeat did not move the volume). `cmd media_session volume --stream 3 --adj raise`
steps the volume normally, so the volume system itself is fine. With `key_filter` false the ramp is back and the
Settings button opens the TV's own Settings. Same symptom as #1680 on another model; the owner chose the ramp.

## Not tested

- A multi-day soak on the Nikkei DVB91NK.
- Whether a cable or antenna scan works with the "TV in diretta" tile: the tile opens `tvcenter`.
- A multi-day soak of the owner's Changhong DVB91 unit.

## Saba SA40S67A9 re-badge unit

**Tested on:** Saba SA40S67A9, Android TV 11, firmware `RTO2.250804.001`,
incremental `13882904`; owner confirms several days of use and all physical remote buttons.

A Saba SA40S67A9 was worked on over ADB on 2026-11-03 and 2026-11-04. The owner confirmed
that everything works after the cleanup. This report records the applied packages and Home handover;
the owner subsequently confirmed several days of use and tested all physical remote buttons.
A separate post-cleanup reboot test has not been recorded.

| Identifier | Observed value |
|---|---|
| Retail model | Saba SA40S67A9 |
| Manufacturer | Changhong |
| ADB product model / product / device | `AI PONT` / `DVB91` / `ikebukuro` |
| Android | Android TV 11, build `RTO2.250804.001`, incremental `13882904` |
| Memory and storage | 929000 kB RAM, 4.2 GB data partition |
| Stock Home | `com.google.android.tvlauncher/.MainActivity` |
| Live TV | `com.mediatek.wwtv.tvcenter/.nav.TurnkeyUiMainActivity` |

This unit shares the device and firmware of this folder. Only the subset documented below
was applied on the Saba. Do not apply the entire list unchanged to reproduce its setup;
Disney+ in batch 4 and Multi Screen Share in batch 3 were kept enabled.

### Home handover

`launcher/build-and-install.sh` installed the launcher built from source and started
`com.gathin.tvhome/.MainActivity`. After the launch succeeded, the script disabled
`com.google.android.tvlauncher` and set the new Home. Home then resolved to
`com.gathin.tvhome/.MainActivity`. `com.google.android.tungsten.setupwraith` stayed enabled.

`tools/theme.sh` applied a four-column layout with Netflix, Prime Video, YouTube and Disney+
in the dock, with Multi Screen Share and Play Store below. Screenshots confirmed the layout and
the Inputs, Wi-Fi and Settings buttons. Advanced effects were disabled and reduced motion enabled.

### Packages disabled during the cleanup

The package state was saved before cleanup. `tools/disable.sh` applied the changes in small batches
and wrote recovery records. Nothing was uninstalled.

- Content recognition and recommendations: `tv.anoki.acr.controller` (Anoki ACR),
  `com.homwee.aipont` (AI PONT portal and channels), `com.google.android.tvrecommendations`,
  `android.autoinstalls.config.homwee` (partner auto-installs).
- Vendor apps and feedback: `com.homwee.store`, `com.homwee.help`, `com.google.android.feedback`.
- Preloads unused by the owner: `com.google.android.play.games`,
  `com.google.android.youtube.tvmusic`, `tv.wuaki.apptv` (Rakuten TV).
- Voice search and casting, unused by the owner: `com.google.android.katniss`
  (the Assistant and voice search stop working), `com.google.android.apps.mediashell`
  (Chromecast built-in stops working).
- Stock Home: `com.google.android.tvlauncher`, disabled by the install script after the new Home started.

Already disabled before cleanup: `com.cltv.fast` and `com.google.android.marvin.talkback`.

Kept: Netflix, Prime Video, YouTube, Disney+, Play Store/Services, Multi Screen Share
(`com.homwee.multiscreenshare`), Live TV (`com.mediatek.wwtv.tvcenter`), TV/input middleware,
remote and Bluetooth services, the keyboard, firmware updates and setupwraith.

### Remote

The owner tested all buttons on the physical remote after the cleanup and reported
that they work without problems.

### Responsiveness and streaming

The three global animation scales (`window_animation_scale`, `transition_animation_scale` and
`animator_duration_scale`) were set to `0.5` and read back successfully. No measured performance
comparison was made.

Intermittent streaming freezes were reported during the session. The owner later confirmed that
everything works, but the affected app, reproduction steps and cause were not established.
The cleanup has not been shown to resolve the freezes.

### Validation limits

The owner confirmed several days of use and working physical remote buttons.
A separate post-cleanup reboot check, HDMI playback, CEC and a dedicated Multi Screen Share
functional test were not recorded. No package was recorded as re-enabled during this cleanup.
