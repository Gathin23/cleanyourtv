# Hisense 65E5H: ADB through the factory menu, and what this list changes

Owner report for a China-market Hisense 65E5H running JuUI 9 on Android 9. The tested list is
[debloat.txt](debloat.txt); it keeps the stock Home and Live TV, so it is debloat only.

| Item | Value |
|---|---|
| Retail model | Hisense 65E5H, China |
| `ro.product.model` / `ro.product.device` | `VIDAA_TV` / `MTK9652` |
| Board | `hisense_m7642_cn` |
| Android / API | 9 / 28 |
| Build | `PPR2.180905.006.A1 release-keys`, incremental `02.02.60.01` |
| Panel | reported 1920x1080, density 320 |
| ABIs | `armeabi-v7a` only |
| `ro.adb.secure` | `1` |
| `service.adb.tcp.port` | `5555` |
| Packages | 126 installed, 0 disabled at the factory |
| Stock Home / Live TV | `com.jamdeo.tv.vod` / `com.jamdeo.tv.livetv` |

| File | Contents |
|---|---|
| `debloat.txt` | The 29 packages that were disabled, grouped and commented |

## Enabling ADB

The native Android Settings are hidden behind the factory menu on this set, the same route the
[E5Q Pro](../hisense-e5q-pro/README.md) documents for its firmware:

1. **Settings → Sound → Advanced settings → Sound Balance** (设置 → 声音 → 高级设置 → 音量平衡).
   If Dolby Atmos is enabled, Sound Balance cannot be opened: turn the Dolby effect off first and
   restore your sound settings afterwards.
2. Alternate the remote's OK and Menu/Settings keys until the factory screen with a large **M**
   appears, press Menu to open the factory menu, and set **Options (选项) → ToFac** from U to M.
3. Reboot. Open the version/device information page (本机信息) and press the Menu key about five times
   to reach the native Android Settings: **About → Build number** repeatedly, then
   **Developer options → USB debugging**.
4. When the work is done, set ToFac back from M to U and reboot. M mode is what the E5Q Pro notes name
   as blocking the OTA check, and leaving the TV in it serves no purpose; the authorised ADB key
   survives the switch back. Switch USB debugging off as well: on this firmware it also leaves ADB
   open on port 5555 over Wi-Fi.

`service.adb.tcp.port` is already 5555, so switching USB debugging on opens the port over Wi-Fi;
there is no separate network-debugging toggle and no cable is needed.

`ro.adb.secure=1`, so the computer has to be authorised once. This unit is slow to show the prompt:

- The dialog is drawn by the Android layer. If the TV is showing HDMI or the live-TV picture, the
  prompt exists but cannot be seen; stay on the JuUI home or inside the native Settings pages.
- If no prompt appears, dismiss anything stacked on screen, use **Revoke USB debugging
  authorizations** (撤销 USB 调试授权), toggle USB debugging off and on, then reconnect.
- If `adb connect` says `failed to authenticate` within a second and nothing ever appears on the TV,
  check that the computer has a key at all (`~/.android/adbkey`). An adb with no key sends no public
  key, so the TV has nothing to prompt about. `adb keygen <file>` writes one, or let a normal first
  run create it.

## One firmware quirk worth knowing

Rebooting right after `pm disable-user` can lose the disable: the first batch applied here came back
enabled after a reboot about 30 s later, and the same packages stuck once the reboot waited a minute.
Give it a minute, reboot, then confirm with `pm list packages -d`.

## What the list leaves alone

Home (`com.jamdeo.tv.vod`), Live TV (`com.jamdeo.tv.livetv`), `com.jamdeo.tv.service`,
`com.hisense.tv.inputs`, `com.hisense.tv.baseservice` and `com.hisense.tv.tiletransition` (without
the last one the source panel opens but switching inputs silently does nothing), Bluetooth pairing
(`com.hisense.bluetoothpair`), the Sogou TV keyboard, OTA (`com.jamdeo.updateservice`,
`com.hisense.usbupgrade`), the voice stack, casting, `com.hisense.childlock`, the NAS packages and
`com.ju.miguvideoTV.cmcc` (owner's choice). `com.hisense.aiot` is deliberately not touched: the aiot
family sent another Hisense board to Android Recovery.

## Result

Applied in six batches with a cold reboot after each: 29 packages stay disabled, Home starts, Wi-Fi
reconnects, the input/source panel and the Bluetooth pairing service keep running, and the crash
buffer stays empty. The sponsored rows on the JuUI home are fetched by `com.jamdeo.tv.vod` itself, so
they remain - only a launcher swap removes them, and that was not attempted on this firmware.
