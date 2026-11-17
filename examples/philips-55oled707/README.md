# Philips OLED family: 65OLED806/12 observations

Observed on one European retail set on 2026-11-01, on Wi-Fi without external USB devices.
**Status: one session, checked after a full restart; no several-day debloat list.**

| Field | Observed value |
|---|---|
| Retail model, `ro.boot.device_name` | `65OLED806/12` |
| `ro.product.model` | `2021/22 Philips UHD Android TV` |
| Product / board | `PH1M_EA_9970A` / `TP1M_EA_9970A` |
| Android / SDK | 11 / 30 |
| Display build / incremental | `RTT2.211108.001 release-keys` / `21029` |
| Security patch | 2025-10-01 |
| Original Home | `com.google.android.tvlauncher/.MainActivity` |
| Live TV | `org.droidtv.playtv` |

Source checkout: `e62aa10a033f715a8ac7116efcf91e01b145adbd`.
The [shared list](debloat.txt) already records a
77OLED806/12 on this product and build. These observations add the 65-inch retail variant;
they do not validate every choice in that shared list. The whole shared list was not applied to
this unit, and no package entries are added or changed here. Several days of normal use are
still needed before proposing any list update for this variant.

## Package changes and retained functions

Original package lists, properties, settings, Home and input registrations were backed up privately.
Package inspection and the repository's existing tools were used in small batches. These packages
were disabled on this unit, without uninstalling anything:

- Analytics/logging: `org.droidtv.candeebug`, `org.droidtv.gel`,
  `org.droidtv.tv.frameworklogger`, `org.droidtv.usagelogger`.
- Feedback/completed-setup helpers: `com.google.android.feedback`,
  `com.google.android.partnersetup`, `com.google.android.onetimeinitializer`.
- Recommendation row, then backend: `org.droidtv.nettvrecommender`, `org.droidtv.recommendation`.
- Partner store/installers: `org.droidtv.nettv.market`, `org.droidtv.nettvapps`,
  `android.autoinstalls.config.tpv.device`.
- Retail demonstration: `org.droidtv.demome`.

This is an observation record, not a list to apply without inspection. Usage logger and demo
request `TV_INPUT_HARDWARE`, but neither provided a `TvInputService` on this unit. Only their
separate two-package batch used `TVINPUT_ALLOW=1`, after that inspection. Hardware-input
registrations stayed unchanged and the owner confirmed HDMI picture after reboot. That exception
needs checking on each unit; a permission name alone does not establish a package's role.

The reference launcher was built from this repository with configuration-only changes and its
focused/resumed Home activity was verified. The installer disabled the original Home separately
after that handover. All fourteen new disabled states survived a full restart. Seventeen packages,
including Aurora and regional Freeview apps, were already disabled and left as found.

Voice/Alexa, Chromecast, AirScreen/Miracast, both firmware updaters (`org.droidtv.oad`,
`com.easy.oad`), Play Store/Services, remote/Bluetooth, Ambilight/Hue and picture/sound/input
services were retained. Setupwraith, EUM, XTV, the Philips options drawer and
`com.google.android.tvrecommendations` were left as found. DNS was unchanged. Retention is not a
claim that every casting, tuner or audio path was exercised.

## Inputs, remote and startup

- The board reports analog inputs HW1 through HW4 that this set does not physically offer.
  The existing `input_sources` setting, with `HW1=-` through `HW4=-`, hid those rows;
  the menu then showed HDMI 1-4 and Live TV. This filters the launcher's menu, not TV services.
- The owner confirmed physical Home, source selection with HDMI picture, Settings, app buttons,
  held-volume repeat and Plex/Netflix/Prime playback after the changes.
- Voice failed during the post-change check, including no microphone LED on the remote.
  Re-pairing the remote restored both its microphone button and YouTube voice search. No disabled
  package was re-enabled; the cause of the pairing problem was not established.
- The firmware opened Live TV on the measured full restart. The owner kept Philips startup
  behavior. No Home/key accessibility helper was enabled; standby resume was not separately tested.
- OLED protection remained enabled. Its moving Philips logo was dismissed by the physical
  remote; injected ADB keys did not reliably dismiss it.
- After authorizations were revoked, debugging disabled and the TV restarted, the repository's
  closed-port checker confirmed a refused connection on port 5555. Timeouts while Wi-Fi was
  reconnecting were not counted as proof of closure.

## Limits

One unit and one session. No several-day use, second-unit deployment, full restore execution,
broad tuner/CEC/eARC matrix, universal casting test or measured performance claim. Record normal
use over several days and any packages that must be re-enabled before proposing a regular list.
