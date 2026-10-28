# Xiaomi Mi TV 4A (MiTV-MOOQ1, tarzan): Android TV 11

`debloat.txt` in this folder was written for Android TV 10 (firmware `QTG3.200305.006.2591`). A second unit
runs Android TV 11, SDK 30, firmware `RTM4.220307.164.9021` (`release-keys`), the same `RTM4.220307.164`
line as the [MOOQ0](../xiaomi-mitv-mooq0/debloat.txt) and [MOOQ2](../xiaomi-mitv-mooq2/debloat.txt) Android 11
lists. The Android 10 list was not applied whole to it. The subset below was applied in one session, in four
batches with a screenshot and a check of Home, Wi-Fi, Bluetooth and the TV inputs after each, and survived one
reboot: `pm list packages -d` was identical before and after. Treat it as provisional until someone has lived
with it for longer.

To use it on Android TV 11, copy the list into its own file and apply that with `tools/disable.sh`, or comment
out the lines of `debloat.txt` that are not here.

## What was disabled on Android TV 11

```
# telemetry
com.miui.tv.analytics
com.xiaomi.statistic
com.google.android.feedback
# Xiaomi promo
com.xm.webcontent
android.autoinstalls.config.xiaomi.amelie
com.mitv.tvhome.michannel                     # not on the Android 10 list
# background services and vendor preloads (none of these is on the Android 10 list)
com.xiaomi.mitv.tvmanager
com.mitv.dream                                # Mi screensaver; the active one was Google Backdrop
fusion.android.tv.demo
com.mstar.android.tv.disclaimercustomization
com.mediatek.wwtv.webview
# by the owner's choice
com.mitv.tvhome.atv                           # PatchWall: the remote's PatchWall button then does nothing
com.mitv.tvhome.mitvplus
com.mitv.milinkservice                        # Mi Cast; Chromecast (mediashell) is separate and kept
com.xiaomi.mitv.smartshare                    # on this build it is the Miracast receiver (WfdActivity)
com.xiaomi.wfdsinkhelperservice
```

Alexa, Prime Video and Netflix were already disabled before this work. `com.google.android.tvlauncher` was
disabled by `launcher/build-and-install.sh` after `com.gathin.tvhome` was confirmed as Home.

## Differences from the Android 10 list

- `com.xiaomi.floatingframe` is not installed on this build.
- `com.xiaomi.mitv.updateservice` was kept enabled (firmware updates).
- `com.google.android.tvrecommendations`, `com.google.android.play.games`, `com.xiaomi.mimusic2` and
  `com.google.android.apps.tachyon` (not installed here) were left alone, as were the USB media players
  (`com.xiaomi.mitv.mediaexplorer`, `com.mitv.videoplayer`, `com.mitv.gallery`).
- Only `com.google.android.tvlauncher` and `com.android.tv.settings/.system.FallbackHome` claim HOME; setupwraith
  does not, so it was left enabled and the saved Wi-Fi network is untouched.

## Kept deliberately, checked with `dumpsys package`

| Package | Why |
|---|---|
| `com.mediatek.tvinput`, `com.mediatek.tv.service`, `com.mediatek.wwtv.tvcenter`, `mitv.service`, `com.mitv.livetv.ci_mediatek` | HDMI 1-3, AV, the tuners and the CI slot |
| `com.xiaomi.android.tvsetup.partnercustomizer` | `BleRcActivity`, the Bluetooth remote pairing |
| `com.mi.bluetooth`, `com.android.bluetooth` | Bluetooth |
| `com.mitv.overseaservice` | hosts `netflixService`, the Netflix key |
| `com.xiaomi.tvqs.overseas` | quick-settings receivers |
| `com.google.android.apps.mediashell`, `com.google.android.tv.remote.service` | Chromecast and the phone remote |
| `com.google.android.katniss` | voice assistant, kept by the owner's choice |
| `mediatek.factorymenu.ui` | factory menu: neither launched nor disabled |

## Tested after the debloat

With the real remote: navigation, volume, the microphone button and switching to HDMI. Home was
`com.gathin.tvhome` after the launcher install, and D-pad Up and Down moved between the dock, the grid and the
top bar as expected. Chromecast and Miracast were not separately re-tested.
