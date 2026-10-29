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
