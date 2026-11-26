# Formuler Z10 Neo

**Android TV 10**, firmware `QT 2606261306 release-keys` (June 2026 security patch).

## What was disabled

The debloat list disables **7 packages**: Formuler stock launcher (already disabled), Formuler help center, network storage service, AOSP dreams/wallpaper/userdictionary, Google feedback.

**Kept (not in list):**
- `tv.formuler.real.upgrade` and `tv.formuler.remoteupgrade.z`: firmware updaters, commented out in the list and left enabled
- `tv.formuler.mytvonline.real` — MyTVOnline (IPTV app), kept as dock item
- `tv.formuler.mytvonline.tunerservice.real` — MyTVOnline tuner service
- `tv.formuler.service.real`: MyTVOnline binds its TVService. Disabled, the tuner reports `tvservice_not_connected`
- `tv.formuler.productservice`: MyTVOnline binds its AFR service. Disabled, MyTVOnline crashes on launch with `failed to bind afr service`
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

[`config.json`](config.json) puts MyTVOnline, YouTube and Play Store in the dock, opens the app picked under Open on start at boot and on wake (after up to 30 s waiting for the network), and sends the stock launcher (`tv.formuler.siptv.leanbacklauncher.real`) back to this one through `stock_home_redirect`. Push it with `TV_HOST=<tv ip> tools/theme.sh examples/formuler-z10-neo`.

## Note

This device runs **Android TV 10** (SDK 29), not Android TV 14. Many common streaming apps (Netflix, Prime Video, Disney+, etc.) are not preloaded on this firmware. The list only contains packages actually present on this device.