# Xiaomi MiTV-MSSP2 (volver) notes

## Initial Android TV 9 / PI.6305 verification

An additional MiTV-MSSP2 (`volver`, Xiaomi), Android TV 9 / SDK 28, firmware
`PI.6305 release-keys`, was tested during one setup session on 2026-11-17, over Wi-Fi with
no USB devices or USB Ethernet adapter. These are initial observations, not several days of
normal-use validation. Cleanup used small device-specific batches rather than applying
this folder's `debloat.txt` unchanged, so this report does not validate every entry in that list.

- The stock Home was `com.google.android.tvlauncher/.MainActivity`.
  The reference launcher 3.5 built from source, installed successfully and became Home.
  After reboot, both the resolved Home component and the focused window were
  `com.gathin.tvhome/.MainActivity`.
- Disabled-package state was read back after reboot. Wi-Fi and the remote continued working.
  The launcher showed the selected dock apps, navigated between the dock and app grid,
  opened the HDMI/AV/Live TV input dropdown, opened TV Settings and returned Home.
  Actual HDMI playback, tuner channel controls and each dedicated remote app button were
  not individually verified in this session.
- Assistant (`com.google.android.katniss`), Chromecast (`com.google.android.apps.mediashell`),
  the firmware updater (`com.xiaomi.mitv.updateservice`), Play Store and Play Games were left
  enabled, and their enabled states were checked. This is not a separate functional test of
  each feature.
- PatchWall (`com.mitv.tvhome.atv`) became enabled again following an app update during the
  session. It was disabled once more, using the original recovery record, and remained
  disabled through the next reboot. The firmware updater was kept enabled; this observation
  does not establish which updater initiated the change, or that every reboot re-enables it.
- The built-in `show_stats` theme option displayed used/total RAM and free storage on Home
  with the ambient-glass theme. It did not require a live Capacity Stats module card.

Longer-term stability and recovery by actually running a full restore remain unverified.

### Exact package state recorded after reboot

The following packages were observed disabled after the final reference-launcher reboot.
This records the choices made on this unit; it is not a replacement for this folder's shipped
debloat list and is not a recommendation to disable apps another owner uses.

| Purpose in this session | Disabled packages |
|---|---|
| Xiaomi analytics | `com.miui.tv.analytics`, `com.xiaomi.statistic` |
| Feedback and bug reports | `com.google.android.feedback`, `com.google.android.tv.bugreportsender` |
| PatchWall, promoted web content and partner auto-installs | `com.mitv.tvhome.atv`, `com.xm.webcontent`, `android.autoinstalls.config.xiaomi.amelie` |
| Recommendation services | `com.google.android.tvrecommendations`, `com.google.android.leanbacklauncher.recommendations` |
| Cleaner and print service | `com.xiaomi.mitv.tvmanager`, `com.android.printspooler` |
| Owner-selected unused apps | `com.netflix.ninja`, `com.amazon.amazonvideo.livingroom`, `com.google.android.videos`, `com.google.android.youtube.tvkids`, `com.google.android.youtube.tvmusic`, `com.google.android.apps.tachyon`, `com.airconsole.androidtv` |
| Original Home, after the replacement was installed and verified | `com.google.android.tvlauncher` |

The seven owner-selected unused apps above were disabled only because this owner did not use
them. YouTube (`com.google.android.youtube.tv`), Spotify (`com.spotify.tv.android`), Play Store
(`com.android.vending`) and Play Games (`com.google.android.play.games`) were kept enabled.
The older `com.google.android.leanbacklauncher` package was not disabled in this session;
the Home checks still resolved to the new launcher. Input/TV middleware, remote and Bluetooth
services, keyboard, Play Services, Assistant, Chromecast and firmware updates were preserved.

## Live TV: channel buttons only work on a tuner picked in Xiaomi's source menu

Seen on a second unit, Android TV 11, firmware `RTT2.211214.001.933`, Italy, with the real remote. That unit
was debloated in batches of its own rather than with this folder's list, so these are notes on the set, not on
the list.

The Live TV app is `com.mediatek.wwtv.tvcenter` (`com.mitv.livetv.nav.TurnkeyUiMainActivity`). It has two
tuner inputs, `com.mediatek.tvinput/.tuner.TunerInputService/HW0` and `.../HW5`; on this unit the
channels were on HW0.

Opening that app from the launcher reopens the source it remembers. If that is an HDMI port, Channel Up/Down
do nothing, so a "Live TV" tile seems to open the wrong input.

Don't fix that with a direct tuner switch: neither a `launch_actions` entry
`com.mediatek.wwtv.tvcenter=input:com.mediatek.tvinput/.tuner.TunerInputService/HW0` nor `live_tv_input_id`
set to that id. Both open the tuner, and the channels play, but the app does not take a tuner started from
outside as its own source:

- Channel Up/Down and the number buttons do nothing.
- The app's own remembered source stays on HDMI.

The keys still reach the app (`mCurrentFocus` is `TurnkeyUiMainActivity`, and logcat shows
`MiTVWindowPolicyInjector: mKeyHandlerManager.handleAction166` followed by a `PWDShow` check). It just
ignores them.

What works:

1. Leave `launch_actions` and `live_tv_input_id` unset for `com.mediatek.wwtv.tvcenter`.
2. In Live TV, press the remote's source button (`KEYCODE_TV_INPUT` opens the same panel) and pick the TV
   entry there once.

From then on the tile opens the channels, and Channel Up/Down and the number buttons work. Switch to HDMI and
back from that same panel, so the app's remembered source stays correct.

The special `live_tv_input_id` value `content://android.media.tv/channel` that MiTV-MSSP1 uses (#1941) was not
tried here.

Not the cause: turning `com.mitv.tvhome.michannel` or `com.mitv.tvhome.atv` (PatchWall) back on did not bring
the channel buttons back. Both were disabled again afterwards.

Live TV screenshots come back empty on this set, so ask the person what the screen shows. A key sent with
`input keyevent` can look like it worked in logcat (audio and program-info lines) when the channel did not
change.
