# Xiaomi F2 Fire TV (Fire OS 7.7.1.7)

Xiaomi F2 Fire TV Edition, Amazon model `AFTMA2857F`, codename `shine` (Amlogic), Fire OS 7.7.1.7 on
Android 9 (SDK 28), build `PS7717.5741N.0036408028416` - the same platform and build as
`tcl-32sf560-fire-tv/` and `toshiba-fire-tv-aftha004/`. UK set with a DVB tuner (Freely). Done over Wi-Fi on
20 October 2026 with adb, in batches with a screenshot after each and a reboot at the end.

| File | What it is |
|---|---|
| `debloat.txt` | The 36 packages that stayed disabled, grouped and commented, and 6 preloaded streaming apps as optional lines |

All commands run from the repo root with the TV's IP in `TV_HOST`.

## What worked and what did not

**Debloat: yes.** Amazon ACR (which also reads HDMI input), the metrics and log uploads, Xiaomi's own analytics
app (`com.miui.tv.analytics`, Firebase), the advertising ID and sponsored rows, Fire TV Channels, Shopping, demo
content, the remote tutorials, Amazon Kids, Silk and its shortcuts, and the Amazon device-setup helpers are off.
After every batch and after the reboot: the home screen loads, the Inputs page still lists HDMI1, HDMI2,
Cable/Antenna, Satellite, Freely, Composite and Media Player, HDMI1 switches, Live TV keeps its 182 channels and
YouTube plays. Eight days later every package on the list was still disabled.

**Refused:** six packages are protected on this build (listed in the header of `debloat.txt`) and keep running.

**Streaming apps:** BBC iPlayer, NOW, Sky News, Red Bull TV, Disney+ and Pluto TV come preinstalled. They were
disabled here because they weren't watched, then Disney+ and Pluto TV were turned back on; they are comments in
the list, so applying it leaves them alone unless you uncomment them.

**Home: not by debloat.** `com.amazon.tv.launcher` is protected and keeps HOME. An Apple TV-style home screen
runs on this set over it with the accessibility-watcher approach in SHOWCASE.md ("Fire TV → Apple TV front
end"); the TV's own Settings (`MainSettingsActivity`), Inputs and Live TV stay reachable that way.

## Undo

```bash
TV_HOST=<tv ip> tools/restore.sh examples/xiaomi-f2-fire-tv-aftma2857f/debloat.txt com.amazon.tv.launcher
```
