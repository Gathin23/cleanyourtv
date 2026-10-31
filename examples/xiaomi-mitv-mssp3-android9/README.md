# Xiaomi MiTV-MSSP3 (machuca): a second unit on the older Android 9 firmware PI.4663

`debloat.txt` in this folder was written on PI.7524, and its header carries notes for the older PI.4663 build
from one unit. This page is a second PI.4663 unit: the EU retail **Mi TV 4S 55" (L55M5-5ASP)**, which
`tools/tv.py --probe` reports as `ro.product.model = MiTV-MSSP3`, `ro.product.device = machuca`, Android 9
(SDK 28), `ro.build.display.id = PI.4663 release-keys`, `ro.adb.secure = 1`, 1.8 GB RAM, on Wi-Fi.

**Status:** applied 2026-10-28 in four batches (telemetry; PatchWall and content rows; TV Manager, the voice
assistant and preloads; the old leanback home once `launcher/` owned Home), with a screenshot of Home after
each batch, two reboots with `pm list packages -d` unchanged, HDMI 1, Live TV and every button on the real
remote checked the same day. In daily use only since then: treat it as provisional until the owner reports
back here after a few days.

## What this unit runs

The set below is `debloat.txt` minus the updater, the Ambient screensaver and the packages this build does not
have, plus the old leanback home. To use it, copy it into a file of your own and apply that with `tools/disable.sh`
(`tools\disable.ps1` on Windows), or comment out the lines of `debloat.txt` that are not here.

```
# Xiaomi + Google telemetry
com.miui.tv.analytics
com.xiaomi.statistic
com.google.android.feedback
# PatchWall, content rows, sponsored rows
com.mitv.tvhome.atv                         # PatchWall app and its home-screen rows
com.xm.webcontent
com.xiaomi.floatingframe
com.google.android.tvrecommendations
android.autoinstalls.config.xiaomi.amelie   # Play auto-install of partner apps
# Xiaomi background services and voice assistant
com.xiaomi.mitv.tvmanager                   # "TV Manager" cleaner
com.google.android.katniss                  # voice assistant, by the owner's choice; stayed off across both reboots
# Preloaded apps
com.xiaomi.mimusic2
com.google.android.play.games
com.mitv.gallery                            # Media Player still opens USB photos
# The older Android TV home, disabled only once launcher/ was confirmed as Home
com.google.android.leanbacklauncher
com.google.android.leanbacklauncher.recommendations
```

`com.google.android.tvlauncher` was disabled by `launcher/build-and-install.sh` after `com.gathin.tvhome` was
confirmed as Home.

## Differences from `debloat.txt` and the first PI.4663 notes

- Not installed on this unit: `com.mitv.tvhome.michannel`, `com.mitv.dream` (as the header says for PI.4663),
  and also `com.mitv.tvhome.mitvplus`, `com.google.android.youtube.tvmusic`, `com.xiaomi.mitv.smartshare`,
  `com.mitv.milinkservice` and Alexa. `com.google.android.music` and `com.google.android.videos` were already
  disabled from the factory, so they are not in the set above.
- Kept by the owner's choice: the firmware updater (`com.xiaomi.mitv.updateservice`), the Ambient screensaver
  (`com.google.android.backdrop`, the active screensaver on this build), Chromecast built-in
  (`com.google.android.apps.mediashell`), Netflix, Prime Video and YouTube. `com.mstar.netflixobserver` stays
  with Netflix: it is the remote's Netflix button.
- `com.google.android.katniss` stayed disabled across both reboots, as the header already reports for PI.4663.
  `voice_interaction_service` still names it, which has done no harm.
- Only `com.google.android.tvlauncher` and `com.android.tv.settings/.system.FallbackHome` claim HOME; the setup
  wizard (`setupwraith`) does not, but it owns the saved Wi-Fi network (`dumpsys wifi` shows
  `cname=com.google.android.tungsten.setupwraith`), so it must stay enabled, which `tools/disable.sh` enforces.
- At power-on the TV shows FallbackHome for a few seconds and then the new Home by itself
  (`logcat -b events`: `am_set_resumed_activity` FallbackHome, then `com.gathin.tvhome/.MainActivity`).
- `android.autoinstalls.config.xiaomi.amelie` and `com.google.android.leanbacklauncher.recommendations` have no
  permission block in `dumpsys package`, so `tools/disable.sh` skips both with its input-driver warning. They
  were disabled with `TVINPUT_ALLOW=1` only after HDMI 1 and Live TV had been opened from the new launcher and
  worked.

## Kept deliberately, checked with `dumpsys package`

| Package | Why |
|---|---|
| `com.mediatek.wwtv.tvcenter`, `com.mediatek.tvinput`, `com.mediatek.tvinputservice.arbitratorservice`, `mitv.service`, `com.mediatek.tv.agent`, `com.mediatek.wwtv.setupwizard` | HDMI 1-3, AV, the tuners and the channel setup |
| `com.xiaomi.android.tvsetup.partnercustomizer` | `GlobalKeyReceiver` for the remote's Settings, Input and app buttons (`dumpsys window policy`, `mKeyMapping`), Bluetooth remote pairing |
| `com.mediatek.hotkey.dispatcher`, `com.mstar.netflixobserver` | the other global remote keys |
| `com.mitv.tvlock`, `com.xiaomo.tv.milegal` | parental lock; the legal-notice web view (`mitv.action.VIEW_WEB`) |
| `com.xiaomi.mitv.mediaexplorer`, `com.mitv.videoplayer` | Media Player and the video player it uses for USB files |
| `com.mediatek.androidbox` | `WFDSinkTest`, a MediaTek Miracast test app: neither launched nor disabled |
| `mediatek.factorymenu.ui`, `com.mediatek.tv.factory` | factory menus: never launched, never disabled |
| `com.google.android.tv.remote.service`, `com.google.android.apps.mediashell` | the phone remote app and Chromecast |
| `com.google.android.marvin.talkback`, `com.google.android.tts`, `com.google.android.speech.pumpkin` | accessibility and speech; not touched |

## Launcher and remote buttons

The launcher was installed with `handle_view_inputs` on, as the header's Input-button section recommends, and
without `INPUT_KEY_SERVICE` or `HOME_KEY_SERVICE`. Its Inputs pill lists HDMI 1, HDMI 2 (ARC), HDMI 3, AV and
Live TV; HDMI 1 and Live TV open `com.mediatek.wwtv.tvcenter` (`TurnkeyUiMainActivity`), and HDMI screenshots
come back empty as on every MediaTek set. D-pad from every edge was checked over ADB.

On the real Bluetooth remote with the new Home, the owner reports that Netflix, Prime Video, Home, Back and the
Settings button all do what they should and that a held Volume Up keeps repeating, so no accessibility helper and
no `app_keys` entry were needed on this unit: the app buttons go through
`com.xiaomi.android.tvsetup.partnercustomizer/.GlobalKeyReceiver` and `com.mstar.netflixobserver`, which stay
enabled. The microphone does nothing by design, since the voice assistant is disabled. An injected `KEYCODE_TV`
over ADB does not open Live TV here, which says nothing about the remote; the Inputs pill opens it.
