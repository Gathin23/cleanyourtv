# NVIDIA SHIELD Android TV (2019) — codename `sif`

| | |
|---|---|
| OS | Android TV 11 / SDK 30 (not Google TV) |
| Firmware | `RQ1A.210105.003.7825199_4387.0822` |
| Hardware | ~1.9 GB RAM (`MemTotal`), 5 GB `/data` — the 2 GB / 8 GB tube, not the 3 GB / 16 GB Pro |
| Stock launcher | `com.google.android.tvlauncher` (Android TV) |
| Packages | 139 installed, only `com.nvidia.benchmarkblocker` disabled out of the box |

`config.json` is the dock this unit runs — Plex, VidHub, Hami Video, YouTube, with
`com.google.android.tv` (Live Channels) as the Live TV entry. Push it without rebuilding the launcher:

```bash
TV_HOST=<tv ip> tools/theme.sh examples/nvidia-shield-tv-2019
```

`debloat.txt` in this folder disables `com.google.android.tv` (Live Channels). Comment that line out
if you use this dock, since `config.json` sets it as the Live TV entry.

## Replacing the Home key is one step here

`launcher/build-and-install.sh` does it: it installs the launcher, then disables the stock one,
which is all the handover needs on this box:

```bash
pm disable-user --user 0 com.google.android.tvlauncher
```

`com.google.android.tungsten.setupwraith` **is** installed on this firmware but does not declare
`CATEGORY_HOME`, so — unlike the TCL G08 — it never competes for the Home key and does not need to be
disabled. Once the stock launcher is disabled the custom launcher is the only HOME candidate and wins
immediately. `cmd package set-home-activity` is a no-op here too: it prints `Success` and changes nothing,
so `pm disable-user` is the mechanism that actually works.

## Notes

- **VidHub's package name gives nothing away: it is `com.oumi.utility.media.hub`.** Nothing in the package
  name, the APK path or `dumpsys` says "VidHub" — it was identified by launching the activity and looking
  at the screen. Don't go hunting for a `*vidhub*` package; there isn't one.
- **The Wi-Fi pill shows `Ethernet`** when the box is on a wired connection — the launcher reports the
  active connection type, not always a network name.
- **The Inputs menu is nearly empty** — the SHIELD has no HDMI inputs, so only Live Channels
  (`com.google.android.tv`, set as `live_tv_package`) appears.
- The `com.nvidia.*` packages are hardware features (controller pairing, remote locator, OTA, audio, CEC,
  SMB). Leave them alone.

## Tweaks applied

- **Animation scales set to `0.5`** — `window_animation_scale`, `transition_animation_scale` and
  `animator_duration_scale`. The UI and remote feel noticeably snappier:

  ```bash
  settings put global window_animation_scale 0.5
  settings put global transition_animation_scale 0.5
  settings put global animator_duration_scale 0.5
  ```

  `tools/restore.sh` puts these back to `1.0` (stock), so re-apply after a restore.

- **Deliberately not done — `match_content_frame_rate`.** The SHIELD exposes this in
  Settings → Display & Sound, and writing the secure setting directly can be overwritten by NVIDIA's own
  UI. Change it there if 24p judder bothers you.
- **Deliberately not done — display scaling.** `wm size` already reports
  `Physical size: 3840x2160 / Override size: 1920x1080`, which is NVIDIA's normal design: a 1080p UI that
  switches the output to 4K for content. Forcing a 4K UI makes the interface slower and smaller, so presets
  that do that are a downgrade, not a tweak.

## Restore

```bash
TV_HOST=<tv ip> tools/restore.sh examples/nvidia-shield-tv-2019/debloat.txt com.google.android.tvlauncher
```

Then press Home — the stock Android TV launcher comes back.
