# CANDY Android TV DVB (Android TV 14)

A Haier-made Candy set on a CVTE board with a MediaTek m7332, Android TV 14, firmware UKNV.260408.001, about
1.4 GB of RAM. It shares most of its Haier and CVTE packages with
[the DVB2K](../haier-android-tv-dvb2k/debloat.txt), which runs Android 11 on a different board, and that
list's notes on the remote and factory packages hold here too.

## What was different on this set

- **A Haier app manager turns apps back on at boot.** Netflix, the FAST channels app, Chromecast, calendar sync,
  the recommendation rows and the Haier content launcher all came back after every reboot while
  `com.cvte.haier.tv.appmanager` was enabled. With it disabled, the rest of the list held through a
  reboot. Apply it in the first batch.
- **The stock home lost its Wi-Fi icon** after `com.mediatek.android.leanbacklauncher.partnercustomizer` was
  disabled. Connectivity was unaffected, and the launcher's own network pill replaces it.
- **Memory:** MemAvailable went from about 450 MB to about 485 MB. Most of the RAM belongs to YouTube, the
  system and Play services.

## Launcher

`launcher/build-and-install.sh` found `com.google.android.tvlauncher` as Home, started the new launcher,
disabled the stock one and set Home with no prompt. A reboot came back to the new launcher. The `dock-glass`
theme with Live TV (`com.mediatek.wwtv.tvcenter`) as the first dock app and `boot_to_live_tv` on worked.

## Undo

`TV_HOST=<tv ip> tools/restore.sh examples/candy-android-tv-dvb/debloat.txt com.google.android.tvlauncher`
