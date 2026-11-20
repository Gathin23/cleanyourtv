# Philips 65OLED856/12: cleanup, Home handover and power-on Home

Checked on the owner's real TV in one installation session on 20–21 November 2026,
over Wi-Fi. This is **not a several-day tested debloat list**. It records the package
changes and launcher behavior observed on this unit; it does not make another
Philips model's list safe to apply here.

## Identify the firmware

| Field | Observed value |
|---|---|
| Retail model | Philips `65OLED856/12` |
| Android-reported model | `2021/22 Philips UHD Android TV` |
| Android / SDK | Android TV 11 / 30 |
| Build | `RTT2.211108.001 release-keys`, incremental `21029` |
| Original Home | `com.google.android.tvlauncher/.MainActivity` |
| Live TV | `org.droidtv.playtv` |

The retail model was confirmed from the TV, rather than inferred from the generic
Android-reported model. Related Philips observations are in
[`philips-50pus8517`](../../examples/philips-50pus8517/debloat.txt) and
[`philips-65oled807`](../../examples/philips-65oled807/debloat.txt); their broader
package selections were not copied onto this set.

## Changes observed in this session

Package lists, the already-disabled list, properties, all three settings namespaces,
original Home and input information were backed up before changes. The existing
recovery tool recorded original state before the first disable or Home change.
Package roles were inspected with `dumpsys package`; the cleanup used the existing
disable tool in small batches, with screenshots between them and no safety overrides.
Everything was disabled for user 0, never uninstalled.

| Group | Packages disabled during the session |
|---|---|
| Philips analytics, assessed together because they share a process | `org.droidtv.candeebug`, `org.droidtv.gel` |
| Framework reporting and Google feedback | `org.droidtv.tv.frameworklogger`, `com.google.android.feedback` |
| Philips recommendation row and backend, in that order | `org.droidtv.nettvrecommender`, `org.droidtv.recommendation` |
| Original Home, after the replacement was running and focused | `com.google.android.tvlauncher` |

The read-back after reboot matched the original disabled set plus those seven
packages exactly. Already-disabled Aurora and regional Freeview packages were left
as found. No package had to be re-enabled during this session.

`org.droidtv.usagelogger` requested `TV_INPUT_HARDWARE` and was left enabled; the
absence of a declared `TvInputService` was not used to override the safety guard.
Firmware updaters `org.droidtv.oad` and `com.easy.oad` remained enabled, as did the
four streaming apps used here: Netflix, Prime Video, Disney+ and YouTube. TV/input
services, keyboard, remote/Bluetooth, Play Store/Services, Ambilight/Hue and panel
protection components were left alone. Voice, casting, DLNA, IP control and audio
features were retained as found; retention is not a claim that every path was tested.

## Home handover

The reference launcher was built from source checkout `f63a1ebc` (version 3.6,
versionCode 15), with personal changes only to `config.xml`. The stock launcher was
initially kept enabled while the replacement was installed and checked.

On this firmware, `cmd package set-home-activity` reported `Success` while Home still
resolved to the enabled stock launcher. The Home query listed stock Home at priority
2, the replacement at 0 and Android Settings' fallback at -1000; setupwraith was not
a Home claimant. This resembles the observation on
[`65PUG7406/78`](philips-65pug7406-78.md), but was checked separately on this unit.

An explicit MAIN/HOME start of `com.gathin.tvhome/.MainActivity` was confirmed as the
focused and resumed activity, and its screenshot was inspected, before the stock
launcher was disabled with the guarded disable tool. Home then resolved to the new
launcher, and pressing Home displayed it. No install script or safety check was changed.

## Power-on, navigation and owner checks

The launcher used `live_tv_package=org.droidtv.playtv`. Inputs listed HDMI 1–4 and
Live TV, plus analog entries reported by the board; no input-source mapping was
changed. D-pad checks covered the dock and grid edges, top-bar ends and movement
between rows. Live TV screenshots were empty on this unit; activity-state checks
were used when the captured image was empty.

With no helper enabled, the first reboot kept the new launcher as default Home but
Philips opened `org.droidtv.playtv` automatically. At the owner's request, the existing
`power_on_home_always` option was enabled with `HOME_KEY_SERVICE=1`. The helper was
verified enabled and bound after reboot, and the new Home was focused without
injecting a Home key. `boot_to_live_tv` remained off. The launcher did not hold
`WRITE_SECURE_SETTINGS`.

The owner confirmed the physical remote, streaming playback in the four dock apps,
HDMI picture/sound and Ambilight. After the helper was enabled, the owner separately
confirmed that holding Volume Up still ramped normally and standby/wake opened the
new Home. This is a result on this unit, not a guarantee about key-repeat behavior
on other firmware. A force-stop switches accessibility helpers off; see
[Troubleshooting](../TROUBLESHOOTING.md).

## Closing debugging and limits

The owner revoked debugging authorizations, disabled debugging and completed a full
restart. `tools/adb-closed.py <tv ip>` then reported port 5555 closed with a refused
connection. Wireless debugging was off, no TLS debugging service was advertised,
and the owner confirmed the new Home and Wi-Fi returned. The Philips JointSpace
control API also responded after the final restart.

This remains a one-session report. Several-day package retention, a full restore,
every HDMI port, CEC/eARC, tuner reception, HbbTV, USB-media playback and all casting
paths were not established. The restore plan was checked but not executed. Normal
use over several days, with any breakage and re-enabled packages recorded, is needed
before proposing a reusable list under `examples/`.
