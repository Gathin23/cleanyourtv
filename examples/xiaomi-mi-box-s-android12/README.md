# Xiaomi Mi Box S (MIBOX4), Android TV 12

The same box as [`xiaomi-mi-box-s`](../xiaomi-mi-box-s/) after Xiaomi's Android 12 update: build
`Xiaomi/oneday/oneday:12/STTC.230106.001/4560:user/release-keys`, security patch 2023-09-05, SDK 31, 2 GB RAM,
1920x1080. In daily family use since 2026-10-22 with a custom launcher as Home and the packages in
[`debloat.txt`](debloat.txt) disabled.

## The remote

The bundled Bluetooth remote, as Android 12 sees it (`dumpsys input`):

| | |
|---|---|
| Name | `Xiaomi RC` |
| Bus | Bluetooth HID (`bus=0x0005`) |
| Vendor / product | `0x2717` / `0x32b9` |
| Key layout | `/vendor/usr/keylayout/Vendor_2717_Product_32b9.kl` (with a matching `.idc`) |
| Classes | keyboard, D-pad, cursor, mic |

Pairing is held by `com.google.android.tv.remote.service` (it scans for the name `Xiaomi RC`), so leave that
package enabled. HDMI-CEC from the TV's own remote comes in as a separate device (`cec_input`).

The buttons, from the `.kl` on this build:

| Button | Scancode | Android key | Notes |
|---|---|---|---|
| Power | 116 | `POWER` | |
| D-pad, OK | 103/108/105/106, 28 and 353 | `DPAD_*`, `DPAD_CENTER` | |
| Back | 158 (also 173, 174) | `BACK` | |
| Home | 172 (also 102) | `HOME` | |
| ☰ (menu) | 127, 139 | `MENU` | Checked with the real remote: reaches the app in front, so a launcher can use it. |
| Apps | 580 (also 216) | `ALL_APPS` | As on Android 9: Android hands it to `com.android.tv.settings`, which ignores it. On a second unit the log shows `START u0 {act=android.intent.action.ALL_APPS pkg=com.android.tv.settings}` on every press, and neither an activity nor a receiver on the build answers that action. |
| Mic | 582 (also 144) | `ASSIST` | Checked with the real remote: goes to whatever holds the Assistant role (Google Assistant by default) through the remote service, not to the app in front. A launcher that holds the role gets it. |
| Volume +/− | 115, 114 | `VOLUME_UP`, `VOLUME_DOWN` | Xiaomi's SystemUI draws its own volume pill on the right; it cannot be hidden without root. |
| Netflix | 393, 240, 416 | `BUTTON_6` | Changed from Android 9, where 393 was `NETFLIX`. Checked with the real remote on a second unit: `getevent` shows `KEY_VIDEO` (393), and `com.xiaomi.android.tvsetup.partnercustomizer`'s `GlobalKeyReceiver` (a global key in `dumpsys window policy`) starts Netflix with `com.netflix.action.NETFLIX_KEY_START`. |
| Prime Video | see the note | `BUTTON_7` (194) | Checked with the real remote on a second unit: `getevent` shows `KEY_YELLOW` (398, usage `0x000c006c`), but the framework reports scan code 400 for it. The same receiver sends `com.amazon.amazonvideo.livingroom.AMAZON_BUTTON` to Prime Video, so with Prime Video disabled the button does nothing. In `app_keys` it is `scan:400`; `scan:398` never matches. |

To see a press yourself: `TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'getevent -lt'`, then press the
button. `getevent` prints the kernel's code, which is not always the scan code Android reports (Prime Video
above); the window manager logs the Android key code of every press at debug level on this build:
`tools/tv.py 'logcat -v time -s WindowManager:D'` prints `interceptKeyTi keyCode=194 down=true repeatCount=0`.

## Remote buttons with a key helper: the volume ramp

A second unit with the same build, with this launcher as Home and `com.google.android.tvlauncher` disabled
(2026-11-05), measured the trade in [`tcl-55p71b-g09-key-repeat.md`](../../docs/devices/tcl-55p71b-g09-key-repeat.md)
on this box:

- With `HomeKeyService` bound and `app_keys` set to `scan:400:com.google.android.youtube.tv` and
  `scan:580:library`, Prime Video opened YouTube and Apps opened the App library, from Home and from inside
  Netflix. A held Volume Up then moved one step per press: one `adjustStreamVolume` in the log for a 4.6 s hold.
- `"consume_volume_keys": true` did not bring the ramp back. The log still showed the window manager's
  `interceptKeyTq keycode=24` and `AudioService` changing the volume once per press, and no adjustment from the
  launcher's own repeat, so the helper did not seem to receive the volume keys: the G08 result in that note.
- With no service bound (`enabled_accessibility_services` empty, `accessibility_enabled 0`) a held key
  repeated again (55 repeated key events in one capture), and the owner confirmed the ramp on the real remote.
- Both at once worked with a log reader like the one in [`tcl-g10-app-keys.md`](../../docs/devices/tcl-g10-app-keys.md):
  an accessibility service that asks for no key filtering (bound with `capabilities=0`), `READ_LOGS` granted over
  ADB and the `SYSTEM_ALERT_WINDOW` app op allowed, reading `logcat -s WindowManager:D` for
  `interceptKeyTi keyCode=194 down=true repeatCount=0` (and 284 for Apps). It opened YouTube from inside other
  apps, the volume ramp stayed, and it came back by itself after a reboot. That app is not in this repository.
  Because nothing is consumed, Xiaomi's receiver still sends its intent to the disabled Prime Video, which
  starts nothing.

## What differs from the Android 9 list

- Android 12 has no Backdrop screensaver; Xiaomi's is `com.mitv.dream`. Set `screensaver_components` to
  another dream (the launcher's clock, or `com.android.dreams.basic/.Colors`) before disabling it.
  A second unit on the same build did have `com.google.android.backdrop` installed, and
  `screensaver_components` already pointed to `com.google.android.backdrop/.Backdrop`: read the setting first,
  since `com.mitv.dream` can then go without touching it.
- The firmware updater (`com.xiaomi.mitv.updateservice`) and its download helper are commented out in the list,
  as upstream prefers. This unit has both disabled by the family's choice.
- Mi analytics and the Xiaomi stats uploader were removed for user 0 on this unit before the list existed; the
  list disables them instead, which `tools/restore.sh` can undo.
