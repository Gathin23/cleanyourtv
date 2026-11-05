# Xiaomi MiTV-MOSR4 (irobot): second unit, EU firmware

Google TV / Android 11, build `RTM5.220609.003/1585` (same as the first unit), 1.4 GB RAM.
List: [`examples/xiaomi-mitv-mosr4/debloat.txt`](../../examples/xiaomi-mitv-mosr4/debloat.txt).
First-day report, two reboots.

## Takeover order

Disabling launcherx while setupwraith was still enabled left Home on
`setupwraith/.RecoveryActivity`. ADB was lost while the Wi-Fi was being re-saved, the TV was
rebooted in that state, setup ran again and the TV was **factory reset**.

If `mFocusedApp` shows `.RecoveryActivity`, re-enable launcherx first. Do not reboot there.

What worked, reboot-checked:

1. Re-save the Wi-Fi (below), switch USB debugging back on.
2. Disable setupwraith while launcherx is still enabled.
3. Disable launcherx, `set-home-activity`, press Home, check `mFocusedApp`.

## Wi-Fi

The network saved during setup belongs to setupwraith (`dumpsys wifi`, `cname=`). Reconnecting
keeps that owner (same network ID). Forget it and join again; it then belongs to
`com.android.tv.settings`. Forgetting it switched USB debugging off on this TV.

## Remote buttons

| Button | Scancode | Keycode |
| --- | --- | --- |
| Input | `000c00b2` | 178 `TV_INPUT` |
| Settings | `000c018f` | 176 `SETTINGS` |
| YouTube | `000c0188` | 190 `BUTTON_3` |
| Netflix | `000c008e` | 193 `BUTTON_6` |
| Prime Video | `000c00b0` | 194 `BUTTON_7` |

With launcherx disabled all five do nothing on this build (`1585`). They are global keys for
`com.xiaomi.android.tvsetup.partnercustomizer/.GlobalKeyReceiver`, which starts launcherx
activities and crashes. After a few crashes Android stops starting it:

```
Unable to launch app com.xiaomi.android.tvsetup.partnercustomizer/1000 for broadcast
  Intent { act=android.intent.action.GLOBAL_BUTTON ... }: process is bad
```

Which of them die is a build difference, so read logcat before installing anything. On
build `RTM5.220609.003.1493` (launcher 3.0, [#1983](https://github.com/Gathin23/cleanyourtv/issues/1983))
only the YouTube button was dead: the Netflix and Prime Video buttons still opened their apps with
no helper installed. The YouTube press stopped in the firmware's own key handler -
`WindowManager: interceptKeyTq keycode=190` and
`MiTVWindowPolicyInjector: mKeyHandlerManager.handleAction190`, with no `GLOBAL_BUTTON` line and no
activity start - while Netflix logged a `GlobalKey` line (`start netflix by key`). A `GLOBAL_BUTTON`
line means the key reached the Xiaomi receiver and died with launcherx; a press that stops at
`handleAction<N>` is one the key helpers have to take, and 190 is the key `app_keys` carries below.
On that unit the helpers were not tried for 190, because they cost the volume's live repeat (below).

Catch the keys before they get there:

```sh
INPUT_KEY_SERVICE=1 HOME_KEY_SERVICE=1 TV_HOST=<tv ip> launcher/build-and-install.sh
TV_HOST=<tv ip> tools/theme.sh examples/xiaomi-mitv-mosr4
```

`config.json` maps 190/193/194. A mapped button whose app is disabled is swallowed. Checked with
the real remote (no `GLOBAL_BUTTON` lines, no crashes), using an equivalent key filter in a
stand-alone launcher; not yet with `com.gathin.tvhome`. The Netflix button went to this receiver,
not to overseaservice. The mic button keeps working.

Both helpers cost the remote's live key repeat on this set: holding volume up or down moves one step
per press, with `InputKeyService` alone as well as with both, and `dumpsys accessibility` shows the bound
service at `capabilities=8` (`CAPABILITY_CAN_FILTER_KEY_EVENTS`) - [#944](https://github.com/Gathin23/cleanyourtv/issues/944).
The bisect, the ways back to either behaviour and the way the repeat is lost are in
[the G09 note](tcl-55p71b-g09-key-repeat.md). The empty-list control was not tried here: clearing the
list unhooks the buttons this section installs the helpers for, so "the ramp returns with nothing bound"
is that note's measurement, not this set's.

## Other

- `com.mitv.tvhome.mitvplus` is re-enabled at every boot.
- `com.xiaomi.mitv.manualhelp` exists here (`/system/customer-app`).
- HDMI switching: `ACTION_VIEW` on a passthrough URI opens `com.mitv.livetv/.tv.MainActivity`.
- `screencap -p` prints a banner before the PNG; capture to `/sdcard` and pull.
- The Wi-Fi name in the top bar needs Location on.
