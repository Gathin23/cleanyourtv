# Vityas "Hi TV" 50 (KTC/KGZN build, board m7332, MediaTek)

A budget "Hi TV" set on an MTK m7332 board with a KGZN/KTC vendor stack. Android TV 11 on userdebug
firmware. Other m7332 re-badges with the same KGZN packages can likely start from the same list.

## What is different on this set

- Debug firmware: ro.adb.secure is unset, so network ADB on port 5555 accepts any device on the LAN
  without the "Allow" prompt. Turn USB debugging off when you are done and check the port is closed
  with `tools/adb-closed.py`.
- The vendor setup wizards (com.kgzn.oobe, com.mediatek.wwtv.setupwizard) do not own the saved Wi-Fi
  network here: dumpsys wifi shows the network belongs to android.uid.system, so disabling them does
  not take the TV off the network. Re-check that on your unit before disabling them.
- The three factory packages (kgzn.factorymenu.ui, mediatek.factorymenu.ui, com.kgzn.tv.factory) were
  disabled, never launched.
- com.kgzn.assistant is kept: it holds CAMERA, INTERNET and boot permissions, but it was only read
  with dumpsys package, not tested disabled. Disable it if you do not use the vendor assistant.
- The vendor settings app com.kgzn.setting also claims HOME and must stay enabled: it is the settings
  entry point. The stock launcher is com.kgzn.mslauncher, the last entry of the list.

## The launcher

The 20 packages the list disables went out in one session with the reference launcher already Home:
4 columns, a dock of Play Store and Spotify, and live_tv_package set to com.kgzn.tvplayer in
`launcher/app/src/main/res/values/config.xml` before building with
`launcher/build-and-install.sh` (Windows: `launcher/build-and-install.ps1`). Everything held across
a reboot, Wi-Fi included.

## Restore

```
TV_HOST=<tv ip> tools/restore.sh examples/vityas-hitv-50-m7332/debloat.txt com.kgzn.mslauncher
```

The launcher itself stays installed; remove it in Settings → Apps if you want it gone.
