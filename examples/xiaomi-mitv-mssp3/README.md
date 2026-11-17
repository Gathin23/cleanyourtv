# Xiaomi MiTV-MSSP3 (machuca): a second unit on the Android 11 firmware

`debloat.txt` in this folder was written on this firmware. This page is a second unit on the same build:
`tools/tv.py --probe` reports `ro.product.model = MiTV-MSSP3`, `ro.product.device = machuca`, Android 11
(SDK 30), `ro.build.display.id = RTT2.211214.001.1065 release-keys`, `ro.adb.secure = 1`, 1.8 GB RAM, on Ethernet.

**Status:** the launcher was installed on 2026-11-03 with the Windows twin of `launcher/build-and-install.sh`,
then checked the same day: Home after a reboot, the Inputs pill opening HDMI 1, an app and a video started from
Home, and the Input and Settings keys below, sent over ADB. Not yet several days of daily use.

## The remote's Input button needs `handle_view_inputs`

Once the stock launcher is disabled, the Input button does nothing until `handle_view_inputs` is on.

- `dumpsys window policy` (`mKeyMapping`) routes `KEYCODE_TV_INPUT` (178) and `KEYCODE_SETTINGS` to
  `com.xiaomi.android.tvsetup.partnercustomizer/.GlobalKeyReceiver`.
- On this build the receiver answers 178 with an implicit `com.android.tv.action.VIEW_INPUTS`: logcat shows
  `GlobalKey: receive KEYCODE_TV_INPUT start activity`, then `START u0 {act=com.android.tv.action.VIEW_INPUTS}`.
  The Android 9 build PI.7524 starts the stock launcher's input panel instead, see the Input button section in
  `examples/xiaomi-mitv-mssp3-android9/debloat.txt`.
- With `com.google.android.tvlauncher` disabled, nothing else on this firmware answers the action:
  `cmd package query-activities -a com.android.tv.action.VIEW_INPUTS` prints `No activities found`.
- With `handle_view_inputs` set to `true` in `launcher/app/src/main/res/values/config.xml` (or a theme), the
  launcher's `InputsRequestActivity` takes the action and the Inputs menu opens from any app. `INPUT_KEY_SERVICE`
  is not needed on this build, so the held Volume Up trade-off described for PI.7524 does not apply.
- Checked by sending `input keyevent 178` while another app was in front: `GlobalKeyReceiver` logged the key and
  Home came up with the Inputs menu open. The physical Input button was not pressed on this unit.
- The Settings button needs nothing: `input keyevent 176` (`KEYCODE_SETTINGS`) still opens TV Settings with the
  new Home.

## Other notes from this unit

- After a reboot the TV shows `FallbackHome` and then the Live TV app, not Home (`logcat -b events`,
  `wm_set_resumed_activity`: `com.mediatek.wwtv.tvcenter/com.mitv.livetv.nav.TurnkeyUiMainActivity`). Home is one
  press away. `boot_to_live_tv` and `power_on_home` were left off.
- Unlike what the header of `debloat.txt` reports, Netflix and Prime Video did not come back here: with
  `com.mstar.netflixobserver`, `com.netflix.ninja` and `com.amazon.amazonvideo.livingroom` all disabled, all three
  were still in `pm list packages -d` after a reboot. One reboot was checked.
