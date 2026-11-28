# Hisense VIDAA_TV / MTK9255 (China, Android 9): HDMI, casting and the boot animation

Owner-observed notes for the China-market Vidaa set whose debloat list ships as
[`examples/hisense-40e2f/debloat.txt`](../../examples/hisense-40e2f/debloat.txt): 23 packages applied on
2026-11-28, reboot-checked four times, with the owner's HDMI, Live TV, casting and remote checks passing
afterwards. Everything below was read off the set with ADB, apart from the two remote-controlled steps.

| Field | Observed value |
|---|---|
| Android model / device | `VIDAA_TV` / `MTK9255` (board `mt5862`, MediaTek MStar) |
| Retail model | `40E2F` (`E2FWASU`) |
| Android / API | `9` / `28` |
| Build fingerprint | `Hisense/VIDAA_TV/MTK9255:9/PPR2.180905.006.A1/02.02.60.01:user/release-keys` |
| TV software version | `02.02.60.01`, built 2021-06-04 |
| Application ABIs | `armeabi-v7a` (32-bit only) |
| ADB | `ro.adb.secure=1`; Network debugging, the set has no USB port wired for ADB |

## HDMI: `com.hisense.tv.tiletransition` is not only animation

`dumpsys tv_input` on this build lists **no inputs at all** — `mInputMap`, `mHwInputIdMap` and
`mHdmiDeviceList` are empty — so there is no HDMI list for an Inputs menu to read. The stock Live TV app's
own source panel is the way to switch, which is what the launcher's `source_panel_service` points at.

Disabling `com.hisense.tv.tiletransition` therefore looks harmless and is not: with it off, HDMI 1 and
HDMI 2 stopped switching (`AppTransitionManager.startSourceFromLauncher` binds to its
`TileAnimationService`, in `DuraSpeed`-style vendor code). Re-enabling it brought both ports back, on the
same boot, with no other change. It is kept on in the list and written up there as well.

## Casting: `MultiScreen_Switch` is derived from `persist.sys.tofac_mode`

Symptom: the phone's Cast/AirPlay screen does not find the TV, and `logcat` says why:

```text
hiservice: <BootReceiver>multiscreen switch is down,not start service!
AirPlayReceiver: multiscreen switch is down,not start LeboService!
```

The switch is `Settings.System.MultiScreen_Switch`, and on this build it is **0**. Its value is not stored
independently: `SettingsProvider`'s `DatabaseHelper.loadMultiscreenSwitchSetting()` writes it from a
property,

```text
MultiScreen_Switch = (persist.sys.tofac_mode == "U") ? "1" : "0"
```

and the set ships with `persist.sys.tofac_mode` = **`M`**, so the switch is 0 and the casting stack never
starts. Set the flag to user mode **from the TV's own Settings with the remote** (M → U). After a reboot the
switch reads 1 and casting comes up by itself, with no ADB involved:

```text
adb shell getprop persist.sys.tofac_mode            # U
adb shell settings get system MultiScreen_Switch    # 1
adb shell dumpsys activity services | grep -i hpplay
```

Two things do **not** work, and both are worth knowing before trying them:

- `adb shell setprop persist.sys.tofac_mode U` is accepted while the set runs, and the firmware writes `M`
  back over it at the next boot. The remote's own path is the one it keeps.
- An app cannot write the setting either. `SettingsProvider`'s
  `enforceRestrictedSystemSettingsMutationForCallingPackage` exempts only uid 0, 1000 and 2000 (and
  priv-apps through `privateFlags & 8`): any other package may write only the names in
  `Settings.System.PUBLIC_SETTINGS`, and everything else throws
  `IllegalArgumentException: You cannot keep your settings in the secure settings.` A boot-time helper app
  is therefore no substitute for the remote step.

## Home: the firmware starts the stock launcher at cold boot, not on wake

`com.jamdeo.tv.vod` stays enabled here, and the replacement launcher is set as Home. At a **cold boot** the
firmware starts the stock launcher itself, from `uid 0`, before anything else is on screen:

```text
ActivityManager: initAppWhiteList, App first white list for booting: com.jamdeo.tv.vod
ActivityManager: START {act=android.intent.action.MAIN cat=[HOME] cmp=com.jamdeo.tv.vod/com.ju.main.launcher.LauncherActivity} from uid 0
```

The replacement Home then comes forward a few seconds later, so 聚好看 is visible briefly on a cold boot
and on a power cut. The remote's power button is **standby**, not a reboot: the panel goes off with Android
still running, and switching it back on sends `ACTION_PANEL_ON`, which starts Home directly. Nothing starts
the stock launcher then, so no flash is seen on a normal on/off cycle. Disabling `com.jamdeo.tv.vod` would
remove the flash, but it is the set's input-source fallback and the list leaves it alone.

## The boot animation ad (outside the PROMPT flow)

The set plays a ~11.5 s 聚好看 animation before Home on every cold boot. It is not a video and not a
network fetch: it is 54 JPEG frames inside `/system/media/default_bootanimation.zip`, played by
`/system/bin/bootanimation` between the kernel logo and the first window. Nothing in the debloat flow
changes it, and removing it needs no root: `/bootad/local/misc/` is world-writable to the shell, and
`bootanimation` prefers a `third_party_bootanimation.zip` placed there over the one in `/system`.

```text
adb shell ls -l /system/media/default_bootanimation.zip
adb shell ls -l /bootad/local/misc/
```

A replacement archive is `Stored` entries only (no compression), `desc.txt` = `1920 1080 15` then
`p 1 0 part0`, and one `part0/01.jpg` at 1920×1080. `adb push` into that directory fails with
`remote fchown failed: Operation not permitted`; push to `/data/local/tmp` and `adb shell cp` it into place
instead. Deleting the file restores the original animation. This is an observation about the build, not a
step in `PROMPT.md`, and it is left out of the list on purpose.
