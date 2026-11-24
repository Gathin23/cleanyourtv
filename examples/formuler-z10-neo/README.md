# Formuler Z10 Neo

**Android TV 10**, firmware `QT 2606261306 release-keys` (June 2026 security patch).

## What was disabled

The debloat list disables **11 packages**: Formuler stock launcher (already disabled), Formuler help center, product service, upgrade services (3), network storage service, AOSP dreams/wallpaper/userdictionary, Google feedback.

**Kept (not in list):**
- `tv.formuler.mytvonline.real` — MyTVOnline (IPTV app), kept as dock item
- `tv.formuler.mytvonline.tunerservice.real` — MyTVOnline tuner service
- `com.mediatek.wwtv.tvcenter` — Live TV / tuner for antenna/HDMI (if present)
- Google Play Store, Play Services, Gboard, Chromecast, Assistant

## Results

- Freed **~1 GB** internal storage
- System updates can proceed (previously may have failed with low space)
- All disabled packages held across reboots
- Real remote works with no accessibility key helper
- Launcher (`com.gathin.tvhome`) confirmed as Home via `dumpsys activity activities`

## Apply / Undo

```bash
# Apply
TV_HOST=<tv ip> tools/disable.sh examples/formuler-z10-neo/debloat.txt

# Undo
TV_HOST=<tv ip> tools/restore.sh examples/formuler-z10-neo/debloat.txt
```

## Launcher config

The launcher's `config.xml` already includes Formuler's MyTVOnline in `dock_apps` and the stock launcher package (`tv.formuler.siptv.leanbacklauncher.real`) in `stock_home_redirect` for Home-guard.

## Note

This device runs **Android TV 10** (SDK 29), not Android TV 14. Many common streaming apps (Netflix, Prime Video, Disney+, etc.) are not preloaded on this firmware. The list only contains packages actually present on this device.