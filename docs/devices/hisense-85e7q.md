# Hisense 85E7Q (China, VIDAA on Android 14): how Home is resolved on an MT9655 set

Owner report for a China-market Hisense 85E7Q. The tested package list lives in
[`examples/hisense-85e7q/debloat.txt`](../../examples/hisense-85e7q/debloat.txt); what is here is the
firmware behaviour around it, in particular the four things that decide which app answers Home on this
set. None of them can be worked around from a package list.

## Observed firmware

| Field | Observed value |
|---|---|
| Retail model | Hisense 85E7Q (85", 4K) |
| Manufacturer | `Hisense` |
| `ro.product.model` / `.name` / `.device` | `VIDAA_TV` / `VIDAA_TV` / `MT9655` |
| Board | `mt5877` (MediaTek MT9655) |
| Android / API | `14` / `34` |
| Build display id | `UQ1A.240105.004.A1 release-keys` |
| Build fingerprint | `Hisense/VIDAA_TV/MT9655:14/UQ1A.240105.004.A1/07.02.70.202605250010:user/release-keys` |
| Build description | `E7Q-user 14 UQ1A.240105.004.A1 07.02.70.202605250010 release-keys` |
| Security patch | `2024-08-05` |
| ABI | `arm64-v8a` |
| RAM | 3.7 GB (`MemTotal` 3887968 kB) |
| `ro.adb.secure` | `0` |
| `service.adb.tcp.port` | `5555` |
| Installed packages | 139 |
| Disabled at the factory | none |

`ro.product.model` is `VIDAA_TV` and says nothing about the size or the model year: on this family the
only place the retail name survives is `ro.build.description`, so tell two sets apart by the build id
or the fingerprint.

## ADB access

The **USB debugging switch also opens TCP port 5555**, and `ro.adb.secure=0` means no "Allow USB
debugging?" prompt is ever shown: any device on the LAN gets a shell. Turn that switch off when the
work is done and confirm the port refuses connections (`tools/adb-closed.py <tv ip>`). There is no
separate network-debugging row to rely on, and `adb tcpip` is unnecessary.

## Home: four candidates, and only one of them wins at boot

`cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME`
listed four usable Home activities before the swap, and the market name of each matters:

| Package | Activity | Priority |
|---|---|---|
| `com.jamdeo.tv.vod` (聚好看, the stock launcher) | `com.ju.main.launcher.LauncherActivity` | `50` |
| `com.keylab.speech.view.vidaa` (JuUI "second screen") | `com.ju.secondscreen.HomeActivity` | `-150` |
| `com.hisense.widget.pro` (card home, same second-screen activity) | `com.ju.secondscreen.HomeActivity` | `-150` |
| `com.android.settings` (fallback) | `.FallbackHome` | `-1000` |

`com.meetingroom/.MeetingRoomActivity` answers Home too at `-100` until the list disables it.

### `cmd package set-home-activity` is accepted and ignored

```
$ adb shell cmd package set-home-activity <package>
Success
$ adb shell settings list secure | grep -i home
$
$ adb shell settings list global | grep -i home
$
```

`Success`, and no `home_activity` key anywhere afterwards. A reboot resolves Home again from scratch,
which is why the same command appeared to work on other TVs and did nothing here. Do not build a
handover on its exit code.

### Resolution is pure priority order, from the manifest

With no key to read, the framework sorts the candidates by the `android:priority` of the
`intent-filter` that declares `MAIN` + `HOME`. Default is `0`, so a launcher that declares nothing
loses to `com.jamdeo.tv.vod` at `50` and to nothing else. The fix is one attribute in the manifest, and
the value does not appear in `pm` or `dumpsys package` output afterwards: the launcher that answers Home
reports `priority=0` even when its filter says otherwise.

### The main activity has to be `android:directBootAware="true"`

This is the one that costs an afternoon. With the filter priority fixed and everything else right, a
**cold boot** (mains power cut, not standby wake) still came up on
`com.keylab.speech.view.vidaa/com.ju.secondscreen.HomeActivity` — the JuUI second-screen home, with a
hot-list row of promos at the top of the screen. Home pressed after boot went to the new launcher
correctly, so it looked like a timing problem and was not one: at that point in boot the user is still
locked, an activity that is not direct-boot aware cannot be started, and the resolver moves to the next
candidate. Adding `android:directBootAware="true"` to the launcher's main activity fixed cold boot with
no other change. A launcher that does this has to work without access to credential-encrypted storage
during that window.

### The other two Home candidates cannot be disabled one component at a time

`pm disable-user com.keylab.speech.view.vidaa/com.ju.secondscreen.HomeActivity` is refused with a
`SecurityException` ("Shell cannot change component state ... to 3"), and `pm hide` fails for lack of
`android.permission.MANAGE_USERS`. It is the whole package or nothing, and on this set the whole
package also takes the voice assistant with it. That is why `keylab` and `hisense.widget.pro` stay
enabled in the example list: two launchers always remain, so the list cannot leave the TV without a
Home even if the new launcher is uninstalled later.

## The settings app that is not the settings app

`com.android.tv.settings/com.hisense.systemsettings.MainActivity` is exported and starts, then finishes
itself immediately: a black frame and back to where you were, with no error in `logcat` worth reading.
It is not the settings app on this build. `com.android.settings` is, and it has the ordinary Android
pages (apps, network, about). A shortcut built on the first component looks like it works until someone
presses it.

## The boot animation cannot be removed without root

`/system/media/default_bootanimation.zip` is 86 frames, about 6.6 s, and `/system` is erofs and read
only. There is no root on this set, `/data/local` is not writable, no package owns the animation, and
`persist.sys.str_on` is `0`. Nothing reachable over ADB shortens it, and deleting the zip would only
make the same 6.6 s black. The honest options are the vendor's own fast-boot/standby switch in the TV
settings, or living with it: on this unit a cold boot reached the new Home 16 s after the power button.

## List

[`examples/hisense-85e7q/debloat.txt`](../../examples/hisense-85e7q/debloat.txt) — 19 packages, applied
and lived with, with the packages that must stay named at the bottom of the file and why.
