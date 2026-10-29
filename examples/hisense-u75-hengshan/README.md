# Hisense U75 / 75U75N (hengshan), Google TV 12

Notes for the `hengshan` board, firmware `STT2.230310.002`. The first list came from a Latin America U75; the
75U75N notes are from a US unit (one owner, from 2026-10-22).

## Live TV and inputs

The Live TV app is `com.vt.tv.tvcenter`, and it also plays the HDMI inputs: the launcher's Inputs menu lists
HDMI 1 to 4, AV and Live TV, and an HDMI row opens `com.vt.tv.tvcenter/.LiveTVMainActivity` on that port. HDMI and
Live TV give empty screenshots, as on most sets. `config.json` here sets it as `live_tv_package` and hides its tile.

## Display

`wm size` reports a 3840x2160 panel with a 1920x1080 override, and density 320: that is the firmware's own UI
scale, not a leftover experiment. Leave it.

## The remote

The 75U75N remote ("SmartRC") pairs over Bluetooth and falls back to infrared: with Bluetooth off its key presses
come from the TV's IR receiver and the D-pad keeps working. Its buttons, read with `getevent -lt` and `logcat`:

| Button | Keycode | Without a key helper, once launcherx is disabled |
|---|---|---|
| Netflix | not captured | works |
| Input | not captured | works |
| Gear | 83 (`KEY_NOTIFICATION`, scancode 598) | nothing: a global key for launcherx |
| YouTube | 190 (`KEY_FN_F4`) | nothing: a global key for launcherx |
| Prime Video | 295 (`KEY_FN_F3`) | nothing |
| Disney+ | 607 (`KEY_ZOOMIN`) | nothing |
| Tubi | 606 (`KEY_ZOOMOUT`) | nothing |

For Prime Video, Disney+ and Tubi the window policy consumes the key and `com.vt.tv.smartsense` asks launcherx
to open the app with `com.google.global_button.ACTION_LAUNCH_APP`, addressed to launcherx, so no other app can
answer it. `config.json` maps all five for `HomeKeyService` (`HOME_KEY_SERVICE=1`): the gear opens the quick
settings panel and the app buttons their apps.

That costs the volume ramp. With the helper bound, holding volume steps once per press. With a CEC soundbar
the log shows why: the accessibility filter hands volume keys to the window policy, which sends the CEC step and
consumes them, so nothing repeats. The log and the choices are in
[the key-repeat note](../../docs/devices/tcl-55p71b-g09-key-repeat.md). Re-enabling launcherx to answer the
buttons takes Home back on this firmware. The 75U75N owner kept the ramp and runs without the helper.

## Sound through a soundbar

With a soundbar on HDMI ARC/eARC and HDMI-CEC volume control on, the TV forwards volume presses to the
soundbar, and its volume overlay shows only + and −. The soundbar reports its level back over CEC
(`<Report Audio Status>` in `dumpsys hdmi_control`), but this firmware does not show a number for it.
