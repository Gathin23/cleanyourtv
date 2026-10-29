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
| Apps | 580 (also 216) | `ALL_APPS` | As on Android 9: Android hands it to `com.android.tv.settings`, which ignores it. |
| Mic | 582 (also 144) | `ASSIST` | Checked with the real remote: goes to whatever holds the Assistant role (Google Assistant by default) through the remote service, not to the app in front. A launcher that holds the role gets it. |
| Volume +/− | 115, 114 | `VOLUME_UP`, `VOLUME_DOWN` | Xiaomi's SystemUI draws its own volume pill on the right; it cannot be hidden without root. |
| Netflix, Prime Video | 393, 240, 416 | `BUTTON_6` | Changed from Android 9, where 393 was `NETFLIX`. Not checked with a press on this unit. |

To see a press yourself: `TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'getevent -lt'`, then press the
button.

## What differs from the Android 9 list

- Android 12 has no Backdrop screensaver; Xiaomi's is `com.mitv.dream`. Set `screensaver_components` to
  another dream (the launcher's clock, or `com.android.dreams.basic/.Colors`) before disabling it.
- The firmware updater (`com.xiaomi.mitv.updateservice`) and its download helper are commented out in the list,
  as upstream prefers. This unit has both disabled by the family's choice.
- Mi analytics and the Xiaomi stats uploader were removed for user 0 on this unit before the list existed; the
  list disables them instead, which `tools/restore.sh` can undo.
