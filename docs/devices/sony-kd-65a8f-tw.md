# Sony KD-65A8F (Taiwan firmware): compatibility report

Successful initial cleanup and reference-launcher handover on one Taiwan-market
65-inch OLED TV, tested on October 24, 2026. This is a same-day report, not a
several-day tested debloat list. The retail model was supplied by the owner;
Android reports a generic platform model.

## Original Sony firmware

The firmware was recorded before any package changes. No firmware was flashed
or updated during this session.

| Field | Recorded value |
|---|---|
| Owner-reported retail model | Sony `KD-65A8F`, Taiwan |
| Android model | `BRAVIA 4K GB ATV3` |
| Product / device | `BRAVIA_ATV3_4K_TW` / `BRAVIA_ATV3_4K` |
| Android / API | `9` / `28` |
| Original Sony build display ID | `BRAVIA_ATV3_4K_TW-user 9 PTT1.190515.001.S54 672201 release-keys` |
| Build incremental | `672201` |
| Security patch | `2022-01-05` |
| Platform | MediaTek `mt5891` |

The full recorded fingerprint is:

```text
Sony/BRAVIA_ATV3_4K_TW/BRAVIA_ATV3_4K:9/PTT1.190515.001.S54/672201:user/release-keys
```

These are the exact system-reported firmware identifiers. A separate Sony
`PKG...` version from the TV's Help screen was not captured and is not inferred
from the build number. The Taiwan OLED variant is not established as equivalent
to the US/Canada or EU LCD models that share the S54 / 672201 build family.

## Launcher handover and reboot result

The reference launcher was built from commit
`cc4c7c596c089c4a16f39480fef75c3349469bf9`, version 2.7 / versionCode 8. Only
TV-specific resource configuration was changed; Java sources, manifest,
permissions, and SDK levels were unchanged. The build succeeded on a Raspberry
Pi ARM64 host using a user-local JDK 21 and Debian's native AAPT2 override.

The original Home component was:

```text
com.google.android.tvlauncher/.MainActivity
```

The replacement was installed and explicitly opened while stock Home remained
enabled. Pressing Home still returned to stock during this trial, as expected.
After the owner approved handover:

- Stock Home answered HOME at priority 2, the replacement at priority 0, and Sony
  Settings' `FallbackHome` at priority -1000. No setup wizard claimed HOME.
- `set-home-activity` reported `Success` with stock Home enabled, but both the
  HOME resolver and the focused window still selected stock Home.
- Disabling only `com.google.android.tvlauncher` and setting
  `com.gathin.tvhome/.MainActivity` again made both checks select the replacement.
  No setup wizard package was disabled.
- A subsequent reboot changed the kernel boot ID and completed normally. ADB
  reconnected, the replacement was already focused before a Home key was
  injected, and both the HOME resolver and injected Home selected it.
- The original disabled-package set plus the seven initial cleanup packages
  and stock Home remained disabled after that reboot. Other enabled packages,
  input-service registrations, display values, screensaver, timeout, keyboard,
  and accessibility settings were unchanged in the recorded comparisons.

The optional Home/Input accessibility helpers were not enabled. The owner
reported that the physical Home, Input, Settings, app shortcuts, and held-volume
checks were normal before the final launcher reboot. These were grouped owner
checks, not separate instrumented tests of every button.

## Sony inputs and OLED settings

The local configuration used `com.sony.dtv.tvx` as `live_tv_package`, and hid
`com.sony.dtv.tvx` and `com.sony.dtv.tvxlauncher.programguide` only from the app
grid. Neither package nor the HDMI/tuner services were disabled.

The UI reported a physical size of 1920 x 1080 and density 320, with no overrides.
Those values were left unchanged; they describe the UI, not the OLED panel's
native resolution. The existing system screensaver and standby settings were
preserved. The launcher's clock screensaver was not selected, and its optional
Home-idle clock remained off.

## Recorded package changes, not a recommended list

All changes used `pm disable-user --user 0`; nothing was uninstalled and no app
data was cleared. The following seven packages were the initial cleanup batch
and were checked across reboot:

| Package | Recorded purpose |
|---|---|
| `tv.samba.ssm` | Samba TV viewing recognition |
| `com.sony.dtv.sonyselect` | Sony Select content/promotional entry point |
| `com.sony.dtv.discovery` | Sony Discover content interface; persistent-process caveat below |
| `com.google.android.tvrecommendations` | Google recommendation services |
| `com.sony.dtv.demomode` | Retail demo mode |
| `com.sony.dtv.demosupport` | Retail demo support |
| `com.sony.dtv.multiscreendemo` | Retail multi-screen demo |

Stock Home was a separate eighth change, made only after the replacement had
been tested. A later, separately backed-up batch disabled five more packages:

| Package | Recorded purpose / tradeoff |
|---|---|
| `com.sony.dtv.sonybugreportsys` | Sony bug-report integration |
| `com.google.android.tv.bugreportsender` | Google TV bug-report submission |
| `com.google.android.feedback` | Market Feedback Agent |
| `com.sony.dtv.da.service` | APK label `Remote Support`; app-activity, power, and audio status collectors; loses Sony remote support |
| `com.qterics.da.product` | Qterics Product Kit provider used by that remote-support agent |

The later five package states were read back as disabled and the owner reported
normal operation, but **that later batch was not reboot-tested**. There is no
several-day stability claim for either batch, and this report does not claim
that all telemetry embedded in streaming apps or Google services was blocked.

Two packages were already disabled before this work and were left alone:
`com.sony.dtv.smarthomesettings` and `com.google.android.inputmethod.latin`.
The original states of each changed package were recorded as enabled=0
(manifest default), with per-component overrides left intact. Recovery plans
preserve that distinction rather than blindly enabling every package. Actual
restoration was not exercised.

## Important retained components and observations

- `com.sony.dtv.bivlinfo` was retained: its live package dump registers software
  search/download result events (`SW_SEARCH_*`, `SW_MANUAL_SEARCH_*`, and
  `SW_*_DL_*`). Its recommendation role alone was not a reason to disable it
  while preserving firmware updates.
- Chromecast, screen mirroring, voice, phone remote, Sony IP control, firmware
  updates, Play Store/Services, HDMI/CEC, audio, Bluetooth, keyboard, and
  Sony/MediaTek infrastructure were retained. Preservation is not a claim that
  every feature was individually exercised.
- Despite `com.sony.dtv.discovery` retaining user-0 enabled=3 after reboot,
  ActivityManager still showed its own process with
  `packageList={com.sony.dtv.discovery}`, `persistent=true`, and a persistent
  system process state. The APK flags include `PERSISTENT`. Successful package
  disablement did **not** establish that its process stopped running. The
  firmware startup path was not investigated or modified.
- The owner explicitly confirmed Netflix playback and later reported the
  grouped app/navigation checks as normal. HDMI signal/audio, ARC/CEC, tuner
  reception, each casting protocol, voice, updates, long standby/wake, and OLED
  maintenance were not independently verified.
- Screenshots confirmed that the replacement Home rendered and its configured
  app tiles were present. No measured performance or RAM-savings claim is made.

## Debugging closure and follow-up limits

The TV used its built-in Ethernet port, with no external USB accessories.
`ro.adb.secure` was 1, and the saved ADB identity was reused. After the owner
reported switching debugging off, three TCP-only checks of the previously
reachable ADB port returned `ECONNREFUSED`, rather than timing out. No ADB
authentication or TV interaction was attempted during those closure checks.
Authorization revocation in the TV UI was not independently confirmed, and no
additional reboot was performed after the later package batch or ADB shutdown.

This note deliberately omits IP addresses, device identifiers, account details,
raw dumps, private keys, screenshots, APKs, and local recovery scripts. Per the
TV-list workflow, a several-day list can be contributed later after additional
owner observation and explicit validation of the remaining limitations.
