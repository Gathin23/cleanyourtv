# Xiaomi MiTV-MOSR1: Android TV 11

Firmware `RTT8.210119.001.1239`, Android 11 / SDK 30. One unit, initial setup on
26 October 2026 and an owner follow-up on 27 October. These are short-session observations,
not a several-day debloat list or a recommendation to apply another model's list.

## Home handover

The stock Home component was `com.google.android.tvlauncher/.MainActivity`. The reference launcher,
`com.gathin.tvhome/.MainActivity`, installed and started successfully. It was checked with a TV
screenshot and `mFocusedApp` while the stock launcher was still enabled.

With stock Home enabled, `cmd package set-home-activity` printed `Success`, but resolving Home
still returned `com.google.android.tvlauncher/.MainActivity`. After the stock launcher was
disabled through `tools/disable.sh` and the preference set again, a Home key press brought up
the reference launcher and `mFocusedApp` confirmed it.

Other Home candidates included `com.duokan.factorytest/.FactoryLauncher` and
`com.android.tv.settings/.system.FallbackHome`. Neither is a replacement launcher; the factory
activity was never started. `com.google.android.tungsten.setupwraith` was installed but did not
appear among the Home candidates, so it was left enabled.

## Package changes and reboot

The original disabled-package list was empty. These nine packages were disabled for user 0,
in small batches with screenshots between them, and all nine remained disabled after one reboot:

| Package | Reason in this session |
| --- | --- |
| `com.miui.tv.analytics` | Xiaomi analytics |
| `com.xiaomi.statistic` | Xiaomi statistics / log upload |
| `com.mitv.tvhome.michannel` | Xiaomi recommendation rows |
| `com.xm.webcontent` | Xiaomi promo WebView |
| `com.xiaomi.mitv.tvmanager` | TV Manager cleaner |
| `com.google.android.tvlauncher` | Only after the replacement Home was installed and running |
| `com.google.android.videos` | Google Play Movies, off by owner choice |
| `com.jio.media.stb.ondemand` | JioCinema, off by owner choice |
| `com.google.android.play.games` | Play Games, off by owner choice |

Netflix, Prime Video, Hotstar, Crunchyroll and YouTube were kept. The owner reported working
streaming playback and normal controls after setup. Voice, Chromecast, Miracast, firmware
updates, the keyboard, Play Store, Play Services, Bluetooth and the TV/input services were kept.
No system packages were uninstalled.

Home still resolved to the reference launcher after reboot, but the foreground activity was
`com.mitv.tvhome.atv/.app.BannerLogoA`: the TV had opened PatchWall. A screenshot confirmed
PatchWall on screen. Home ownership alone therefore did not establish the power-on screen.

## PatchWall and inputs

`com.mitv.livetv` was kept and used as the launcher's `live_tv_package`.
The Inputs dropdown listed HDMI 1, HDMI 2, AV and Live TV. The MediaTek input services stayed
enabled. This report does not establish a separate picture test on every physical input.

PatchWall, `com.mitv.tvhome.atv`, was kept enabled because package inspection found
`.app.tv.InputSourcePopupActivity`, and its input-source intent still resolved to that activity.
The package was hidden from the replacement launcher's tiles with `hidden_apps` instead.
Disabling the whole package was not tested, so this is a precaution rather than evidence that
PatchWall is required for every input path on this model.

An attempt to disable only `com.mitv.tvhome.atv/.app.BannerLogoA` was refused with
`SecurityException: Shell cannot change component state`; it changed no component state.
`com.mitv.tvhome.mitvplus` was also retained because it registered a `LiveTVInputService`.

## Key helper and held volume

The owner initially reported that the available Home, input/settings and streaming shortcut
buttons worked, and holding volume repeated normally. To address PatchWall appearing at startup,
`power_on_home_always` was enabled with `HOME_KEY_SERVICE=1`.

`dumpsys accessibility` showed `HomeKeyService` enabled and bound, with `capabilities=8`.
With that service bound, the owner tested the real remote and holding volume up or down moved
only one step per press. The service was then disabled and `power_on_home_always` returned to
false. `dumpsys accessibility` showed no enabled or bound services, and the owner confirmed
held-volume operation worked again. No key helper was left enabled.

This is the same observed trade-off as the
[TCL G09 key-repeat report](tcl-55p71b-g09-key-repeat.md). Automatic power-on redirection was not
reboot-tested with the helper enabled, and `InputKeyService` was not tested on this unit.
The retained behavior is to press Home if PatchWall appears after a full reboot.

## Physical Apps button

The Apps button had no visible action after the stock launcher was disabled. A capture of that
button alone showed scancode `000c0199`, with Linux `EV_KEY KEY_CHAT` down/up events. That Linux
label is not a verified Android keycode; the remote's key-layout file was not readable by shell.
No working remap was established. The owner kept key helpers off to preserve held-volume repeat
and used Home, then Down from the dock, to reach the app grid.

## Limits

This is one short setup session and a next-day owner report, with one package-persistence reboot
check. It does not establish long-term stability, every HDMI/tuner path, a successful auto-Home
boot, or behavior on another firmware or remote. A tested list under `examples/` should wait for
several days of ordinary use, as required by [CONTRIBUTING.md](../../CONTRIBUTING.md).
