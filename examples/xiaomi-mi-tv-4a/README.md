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

- `com.xiaomi.floatingframe` is not installed on this build. On `RTM4.220307.164.8277` (below) the package is
  on the system image but not installed for user 0: only `pm list packages -u` lists it.
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

## A third unit: Android TV 11, firmware `RTM4.220307.164.8277`

MiTV-MOOQ1 on SDK 30, `release-keys`, on Ethernet. The whole subset above was applied, plus the packages
below, in five batches with a screenshot of Home after each. It survived one reboot: `pm list packages -d` was
identical before and after (29 packages, counting the three that were already off). Provisional as well: applied
and reboot-checked in one session. Alexa, Prime Video and Netflix were already disabled on this unit too.

```
# Xiaomi preloads
com.xiaomo.tv.milegal                         # Xiaomi legal / privacy screens
# stock launcher plumbing, disabled after com.gathin.tvhome was confirmed as Home
com.google.android.tvrecommendations
# by the owner's choice; keep them for a set you share
com.mitv.tvlock                               # Xiaomi TV lock / parental PIN
com.google.android.apps.mediashell            # Chromecast built-in
com.xiaomi.mitv.updateservice                 # firmware updates
com.google.android.youtube.tvmusic
com.google.android.videos                     # Play Movies & TV
com.google.android.play.games
com.xiaomi.mimusic2
```

- Kept by the owner: Google Assistant (`com.google.android.katniss`), the official YouTube app and the USB media
  players (`com.xiaomi.mitv.mediaexplorer`, `com.mitv.videoplayer`, `com.mitv.gallery`).
- `android.autoinstalls.config.xiaomi.amelie` and `com.mstar.android.tv.disclaimercustomization` request no
  permissions, so `tools/disable.sh` skips both ("neither a requested nor a granted permission block",
  [#2398](https://github.com/Gathin23/cleanyourtv/issues/2398)). Neither
  serves a TV input: `pm query-services --components -a android.media.tv.TvInputService` names only
  `com.mediatek.tvinput`, `com.mediatek.tv.service` and the preview inputs of `com.google.android.videos` and
  `com.vk.tv`. They were disabled with `TVINPUT_ALLOW=1` once HDMI showed a picture after the reboot.
- Only `com.google.android.tvlauncher` and `com.android.tv.settings/.system.FallbackHome` claim HOME, as on
  `.9021`.
- At power-on before the debloat, the first resumed activity was PatchWall's `com.mitv.tvhome.atv/.app.BannerLogoB`,
  then the stock launcher. Afterwards `logcat -b events | grep wm_set_resumed_activity` goes from `FallbackHome`
  straight to `com.gathin.tvhome`; the set does not open Live TV by itself, so neither `power_on_home` nor
  `boot_to_live_tv` is needed.
- With the real remote after the debloat and the launcher, with neither key helper enabled (no
  `HOME_KEY_SERVICE` or `INPUT_KEY_SERVICE`): Home, the microphone, the Input/Source button and holding volume
  (it keeps repeating) all worked, and HDMI showed a picture from the launcher's Inputs menu, which lists HDMI 1-3,
  AV and Live TV.
