# Hisense 65U75K (hengshan) on Google TV 14

Companion to [`debloat.txt`](debloat.txt). Same MediaTek **hengshan** board as
[`../hisense-u75-hengshan/`](../hisense-u75-hengshan/) (Google TV 12 / `STT2.230310.002`); this folder
is one US **65U75K** on Android 14 build `UKN4.250714.107` (`00.00.00.193`). Model string is still
`SmartTV 4K FFM`. About 2.4 GB RAM. Display is 3840×2160 with a 1920×1080 override at density 320 —
leave that alone.

## Volume ramp + custom Home

Same trade as the Colombia 65U7ESG note on the GTV12 page: binding `HomeKeyService` with
`key_filter: true` and leaving `com.google.android.apps.tv.launcherx` **enabled**, then setting
`stock_home_redirect` to launcherx, keeps a held Volume key ramping while Home is redirected to
`com.gathin.tvhome`. Install with:

```bash
STOCK_LAUNCHER=none HOME_KEY_SERVICE=1 RESTORE_KEY_SERVICES=1 launcher/build-and-install.sh
TV_HOST=<tv ip> tools/theme.sh examples/hisense-u75-hengshan-android14
```

`tools/restore.sh` / `tools/check.py` should name launcherx as the second argument when needed,
because this list leaves it out on purpose.

## Screensaver (Photos Ambient)

`com.google.android.apps.tv.dreamx` (Backdrop) works. This firmware has no
`com.android.systemui.Somnambulator`, and a sideloaded app cannot call `IDreamManager.dream()`
(`ACCESS_DREAM_MANAGER`). Starting the configured dream via
`am start -a com.google.android.pano.action.SLEEP` (TvSettings `DaydreamVoiceAction`) works.
`com.google.android.apps.mediashell` must stay **enabled** for Google Photos Ambient albums; it is
not in this list. Disabling casting extras (Miracast / DLNA / AirPlay packages) is fine beside that.

Ambient has no dark-theme switch; Dynamic slideshow uses pastel collage mats — Basic is full-screen.

## setupwraith and Wi‑Fi

Before disabling `com.google.android.tungsten.setupwraith`, forget and rejoin Wi‑Fi in Settings so
the saved network’s `cname` is `android.uid.system`, not setupwraith. Otherwise the set forgets the
network and network ADB dies mid-session. `tools/disable.sh` checks this.

## AirPlay

`com.mediatek.partner.airplay_sys` is a sticky `system_ext` priv-app: `pm disable-user` does not
always keep related processes dead across reboot without root. Best-effort only.

## HDMI‑ARC / soundbar

With a soundbar on HDMI port 3 (ARC), intermittent audio dropouts and app “playback error” flashes
correlated with CEC `HotPlugDetectionAction` chatter and occasional hotplug disconnects on that
port — not with Wi‑Fi score. Reseating the ARC cable fixed it on this unit. Temporary escape hatch:
`cmd hdmi_control setsam off` routes `STREAM_MUSIC` to TV speakers (`Devices: speaker`);
`cmd hdmi_control setsam on` (or the soundbar renegotiating) brings ARC back.

## SmartTube (optional)

This unit sideloaded SmartTube stable (`org.smarttube.stable`) and disabled stock
`com.google.android.youtube.tv`. That line is a comment in `debloat.txt`, beside the other unused
streaming apps: uncomment it to turn stock YouTube off as this unit did. `config.json` docks SmartTube
and hides stock YouTube; edit to taste.

## Apply / undo

```bash
TV_HOST=<tv ip> tools/disable.sh examples/hisense-u75-hengshan-android14/debloat.txt
TV_HOST=<tv ip> tools/restore.sh examples/hisense-u75-hengshan-android14/debloat.txt com.google.android.apps.tv.launcherx
```
