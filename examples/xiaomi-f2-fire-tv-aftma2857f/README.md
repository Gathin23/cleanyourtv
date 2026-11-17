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

## Second unit: Xiaomi TV F Pro 32 (2026) (`L32MB-FPEU`)

Spanish retail unit (`ro.product.oemmodel=Xiaomi_L32MB-FPEU`, marketed as **Xiaomi TV F Pro 32 (2026)**) on the
same `AFTMA2857F` / `shine` platform (Amlogic), Fire OS 7.7.1.7 / Android 9 (SDK 28), build
`PS7717.5741N.0036408028416` (`incremental 0036408028548`). Tested across multiple reboots (2026-11-07 to
2026-11-15); TDT (`com.amazon.tv.livetv`), HDMI1, HDMI2, Composite, Settings, casting (Miracast, Fling, DIAL,
AirPlay), Xiaomi TV+ and streaming apps stay working.

### Differences on this unit

- **Kept enabled on the ES unit:** `com.amazon.livedeviceservice`, `com.amazon.franktvinput` (with
  `com.droidlogic.tvinput` for TDT / Live TV) and `com.amazon.cloud9` (Silk) were kept enabled on the set;
  comment those lines out before applying `debloat.txt` if you want the same behaviour.
- **Additional verified packages (commented in `debloat.txt`):** extra Amazon telemetry/diagnostic services,
  recommendation rows, unused dummy placeholder stubs, Alexa voice helpers, Amazon Photos/Luna, profile
  picker, screensaver, printing, NFC and `com.amazon.tv.forcedotaupdater.v2` were also tested disabled across
  reboots on this set and are included under `# This set has these off; uncomment if you don't use them`
  (keep `com.amazon.tv.forcedotaupdater.v2` commented out if you want OTA firmware updates).
