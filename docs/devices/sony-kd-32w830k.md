# Sony KD-32W830K (BRAVIA VU3, 2K): inventory notes

This is a one-session owner report from 2026-11-13, not a tested debloat list. **Nothing was disabled
on this TV and no launcher was installed.** It records what the 32-inch set reports, so that an owner
can tell whether the [`sony-bravia-vu3` list](../../examples/sony-bravia-vu3/debloat.txt), recorded on a
55-inch KD-55X75K, describes their TV too. Everything below was read over ADB with read-only commands
(`getprop`, `pm list packages`, `cmd package query-activities`, `dumpsys`).

## Observed firmware

| Field | Observed value |
|---|---|
| Retail model | Sony `KD-32W830K` (Settings, Status & Diagnostics; the TV's mDNS records also carry `model=KD-32W830K`) |
| Android model | `BRAVIA VU3` |
| Product / device | `BRAVIA_VU3` / `BRAVIA_VU3_2K` |
| Android / API | `14` / `34`, security patch `2026-04-01` |
| Fingerprint | `Sony/BRAVIA_VU3/BRAVIA_VU3_2K:14/UKR1.240726.001/386-60842:user/release-keys` |
| Sony software version | `60842000` |
| Board / SoC | `rtd2885m` / Realtek `RTD2873A` |
| Memory | `MemTotal` 1572864 kB, zram swap 921596 kB |
| Display | `wm size`: physical 1920x1080, override 1280x720; density 213 |
| Remote | `SONY TV VRC 001` (Bluetooth) and `IR_sony` |

Android never names the retail model: `ro.product.model` is `BRAVIA VU3` here as on the 55-inch set.
The device name is what tells the two apart, `BRAVIA_VU3_2K` against `BRAVIA_VU3_4K`.

## Compared with the VU3 list

- Same product, same build id and the same incremental (`UKR1.240726.001` / `386-60842`).
- All 21 packages in `examples/sony-bravia-vu3/debloat.txt` are installed.
- The packages its header says to keep are installed: `com.android.tv`, `com.realtek.dtv`,
  `com.realtek.tv.atv`, `com.realtek.tv.passthrough`, `com.sony.dtv.smartui`, and
  `com.realtek.advancesetting`.
- Out of the box only `com.sony.dtv.b2b.vendorprotocol` was in `pm list packages -d`, as on that
  list's second unit. `com.android.camera2` is installed and enabled.
- 152 system packages.
- Hardware TV inputs: `com.realtek.dtv/.tvinput.DTVTvInputService`,
  `com.realtek.tv.atv/.atvinput.AtvInputService`, `com.realtek.tv.passthrough/.avinput.AVTvInputService`
  and `com.realtek.tv.passthrough/.hdmiinput.HDMITvInputService`.

None of this shows that the list behaves the same on the 2K set. It shows the same packages are there.

## Home candidates

`cmd package query-activities` for `MAIN` / `HOME`, with nothing changed:

| Priority | Activity |
|---|---|
| 2 | `com.google.android.apps.tv.launcherx/.home.HomeActivity` |
| 1 | `com.google.android.tungsten.setupwraith/.RecoveryActivity` |
| -999 | `com.realtek.advancesetting/.FallbackHome` |
| -1000 | `com.android.tv.settings/.system.FallbackHome` |

## Global keys

`dumpsys window policy` routes the remote's keys as on the 55-inch set: `BUTTON_2`, `BUTTON_3`,
`BUTTON_4`, `BUTTON_5`, `BUTTON_9`, `BUTTON_11`, `BUTTON_13`, `BUTTON_14`, `BUTTON_15` and
`TV_INPUT_HDMI_1` to `_3` go to `com.realtek.advancesetting/.GlobalKeyReceiver`, `TV_INPUT` and
`BUTTON_10` to `com.sony.dtv.smartui`, `GUIDE` to `launcherx`, and `BUTTON_7` and `BUTTON_8` to
`com.sony.dtv.remotecontrolsettings` (its `MicKeyReceiver`).

Two receivers belong to packages the VU3 list switches off: `HELP` goes to `com.sony.dtv.smarthelp`,
and `BUTTON_6` and `BUTTON_12` go to `com.sony.dtv.multiscreendemo`. Which physical buttons send
those keycodes, and what they do once the packages are off, was not tested here.

## ADB

With only USB debugging switched on, and Wireless debugging off (`adb_wifi_enabled=0`), TCP port 5555
answered `adb connect` and showed the authorisation prompt. No pairing and no `adb tcpip` were run on
this unit; `persist.adb.tcp.port` and `service.adb.tcp.port` already read `5555` at the first
connection. Whether the port is still open after a reboot was not checked.

## Memory on the 1.5 GB set

Read about 15 minutes after boot, every package in its factory state, the stock Google TV home screen
in its normal mode:

- `dumpsys meminfo`: `ZRAM: 120,328K physical used for 504,688K in swap`; `MemAvailable` 437324 kB.
- `dumpsys procstats --hours 24`, minimum-average-maximum PSS and share of time in memory:

| Process | PSS | In memory |
|---|---|---|
| `com.google.android.tts` | 41MB-72MB-127MB | 91% |
| `com.google.android.katniss:interactor` | 32MB-41MB-53MB | 100% |
| `com.google.android.apps.tv.launcherx:coreservices` | 22MB-29MB-36MB | 100% |
| `com.sony.dtv.airplayapp:airplayserver` | 8.5MB-13MB-20MB | 100% |
| `com.sony.dtv.airplayapp` | 5.2MB-12MB-35MB | 100% |
| `com.sony.dtv.sonyselect` | 7.1MB-9.9MB-12MB | 100% |
| `com.sony.dtv.multiscreendemo` | 5.2MB-5.7MB-11MB | 100% |

Of the packages in the VU3 list, only `com.sony.dtv.sonyselect` and `com.sony.dtv.multiscreendemo`
had a process in memory at the time. The largest optional resident was `com.google.android.tts`, which
the [`sony-bravia-vu31` list](../../examples/sony-bravia-vu31/debloat.txt) switches off and the VU3
list does not name.

## Not tested

No package was disabled, so nothing here says what the list changes on this set: not the memory it
frees, not Home, not the remote's keys, not casting, and not what a reboot keeps.
