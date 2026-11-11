# Xiaomi MiTV-MSSP2 (volver) notes

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
