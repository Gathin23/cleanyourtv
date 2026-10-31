# Xiaomi TV A Pro 43-inch (`MiTV-MZTU0` / `river`): original report and independent 2026 audit

Owner-reported 43-inch Xiaomi TV A Pro. Android reports the same `MiTV-MZTU0` model and `river`
codename as the existing 75-inch 2026 profile ([examples/xiaomi-tv-a-pro-2026](../../examples/xiaomi-tv-a-pro-2026/debloat.txt)), but this unit has a different firmware build. This is
an initial compatibility report, not a multi-day debloat list.

| Field | Observed value |
|---|---|
| Retail model | Xiaomi TV A Pro 43-inch (owner-reported) |
| System model / codename | `MiTV-MZTU0` / `river` |
| Board platform | `merak` |
| System | Google TV on Android 14 (SDK 34) |
| Firmware | `UKN1.240806.017.V816.0.26.6.2.6.URTMIXM` |
| Stock launcher | `com.google.android.apps.tv.launcherx` |
| Live TV app | `com.mitv.livetv` |

## Original report (#1285 / #1401)

The observations in this section and the package list below are from `doriel1243`'s
[original report](https://github.com/Gathin23/cleanyourtv/issues/1285), merged in
[PR #1401](https://github.com/Gathin23/cleanyourtv/pull/1401). That report did not establish the retail model year.

### Setup wizard and Home

CleanYourTV Setup left the stock Home screen alone and reported that the setup wizard owned the saved
Wi-Fi network. The owner had already forgotten Wi-Fi and connected Ethernet, but the warning remained.
ADB inspection showed `com.google.android.tungsten.setupwraith` as the Wi-Fi config owner. After
Ethernet was confirmed as the active default network, the setup wizard was disabled; the launcher
build-and-install script installed and started CleanYourTV, made it Home, and only then disabled the
stock launcher. After one reboot, Home still resolved to `com.gathin.tvhome/.MainActivity`.

Do not disable the setup wizard while the TV is still relying on its saved Wi-Fi network. Confirm
that Ethernet (or another network not owned by the wizard) is active first. Disabling it can also stop phone casting after a reboot, since the Cast receiver reads its credentials from setupwraith.

### Package state observed

This subset was adapted from the 75-inch `river` profile. Ten entries were already disabled before
this session; `com.google.android.tungsten.setupwraith` was the only newly disabled package in this
list. The stock launcher was disabled separately by `build-and-install` after the replacement Home
started successfully.

```text
# Xiaomi and Google telemetry
com.miui.tv.analytics
com.xiaomi.statistic
com.xiaomi.tvqs.overseas.y24
com.google.android.feedback

# PatchWall and sponsored/content rows
com.mitv.tvhome.atv
com.mitv.tvhome.michannel
com.mitv.tvhome.oemtab
com.xm.webcontent
android.autoinstalls.config.google.gtvpai
com.xiaomi.mitv.tvmanager

# Newly disabled for Home handover
com.google.android.tungsten.setupwraith
```

Streaming apps, voice search, Chromecast/casting and firmware updates were kept enabled by owner
request. SmartTube and TV Bro were pinned in the launcher's dock. The package state and launcher were
checked in one session and after one reboot; use this report as provisional until the owner has lived
with the setup for several days.


## Independent owner-reported 43-inch 2026 unit (31 October 2026)

This is a separate unit from the original report above. Its owner identifies it as **Xiaomi TV A Pro
43 2026**. Xiaomi has an [Indonesian product page for that retail model](https://www.mi.co.id/id/product/xiaomi-tv-a-pro-43-2026/specs/),
but this unit's rear-label SKU, purchase record and market variant have not been independently checked.
The Android model alone does not establish its retail year or screen size.

| Field | Observed value on the independent unit |
|---|---|
| Android model / device / board | `MiTV-MZTU0` / `river` / `river` |
| Board platform / boot hardware | `merak` / `mt5896` |
| Android / SDK | 14 / 34 |
| Build | `UKN1.240806.017.V816.0.26.6.2.6.URTMIXM` |
| Security patch | 5 May 2026 |
| Kernel | `5.15.192-android14-11-ga8e52546df5f-dirty`, build dated 2 July 2026 |
| Memory reported by the kernel | `MemTotal: 1827500 kB` |
| Display state, observed rather than changed for this report | Physical 3840x2160; existing override 1920x1080; density 320 |
| Resolved Home | `com.gathin.tvhome/.MainActivity` |

The [55-inch second-unit report](https://github.com/Gathin23/cleanyourtv/pull/1971) has the same full
build string, while the 75-inch report in [issue #90](https://github.com/Gathin23/cleanyourtv/issues/90)
has `V816.0.25.3.10`. Shared identifiers or firmware do not prove that panels, tuners, remote behavior,
Home handling or middleware are interchangeable. No 55- or 75-inch hardware was available for a direct
comparison. Do not apply their full list blindly to this 43-inch unit.

### State and verification limits

The pre-change backup recorded four disabled packages: `com.iwedia.tvinput`,
`com.mediatek.dtv.tvinput.atsctuner`, `com.mediatek.dtv.tvinput.isdbtuner` and
`com.xiaomi.floatingframe`. On 31 October the first three were still disabled. Floatingframe was absent from both the installed
and disabled inventories; this does not prove it was re-enabled. Its per-user installation state and
the cause of that difference remain unverified.

Fourteen additional packages were disabled during this unit's customization: the four telemetry
packages, the five PatchWall/content packages (including `com.mitv.tvhome.mitvplus`), TV Manager,
Play Games, Mi Music, setupwraith and launcherx. The state was rechecked after a reboot on 30 October
and by a read-only audit on 31 October. This is not a claim of several-day validation of every function,
and no new active debloat list is added here.

Unlike the 75-inch example, all three firmware updater packages remain enabled, as do Prime Video,
YouTube Music, `android.autoinstalls.config.google.gtvpai` and `com.google.android.apps.mediashell`.
An enabled package is not proof that its feature works: OTA installation, HDMI, tuner reception, Ethernet and USB media have not been functionally
validated on this unit.
The owner confirmed AirPlay works. Samsung Smart View and Windows `Win + K` initially did not find
the TV; after opening the built-in Miracast receiver app on the TV, both discovered it and connected
successfully. The owner separately tested Google Cast from Chrome after returning the TV to Home: the TV was
discovered and both picture and sound worked. AirPlay, Miracast and Google Cast are recorded as
separate tests, not inferred from their packages being enabled.

The owner forgot and rejoined Wi-Fi through TV Settings before setupwraith was disabled. Wi-Fi and
ADB remained available after the tested reboot. Both setupwraith and launcherx are currently disabled;
the replacement Home was resolved and observed after reboot. This report does not intentionally
reproduce the original crash-loop or claim to diagnose identical firmware behavior across sizes.

The physical YouTube button required the launcher's `InputKeyService` after launcherx was disabled.
The owner confirmed that YouTube opened, but holding a volume button became one step per press while
the helper was active; the owner chose to keep the YouTube mapping. After the latest reboot the helper
was bound, and the owner confirmed the physical YouTube button and short/long OK on the Settings icon.
The latter long press is part of a private customized launcher, not a new upstream feature.

The custom build includes additional network widgets and permissions and is **not** evidence that the
unmodified reference launcher has those features or its performance. No private configuration,
wallpapers, screenshots, raw dumps, keys or install scripts are included in this documentation change.


### Private customization and possible add-on boundaries

The owner also tested a personal fork with an uncluttered five-slot dock, local hourly wallpaper
rotation, a compact icon bar and a long-press Settings sidebar. This is a report of working private
customizations, not a claim that the reference launcher ships these features or that installable
add-ons have been published.

| Customization tested on this unit | Possible reusable form; remaining work |
|---|---|
| Current weather with an icon, condition, temperature and approximate area | Extend the [existing Weather add-on](../../addons/weather/README.md); disclose any added location/network use and keep the core offline. |
| Prayer timetable, next-prayer countdown and underline | Propose an official Indonesian timetable provider and date/region-validated offline cache for the [existing Prayer Times add-on](../../addons/prayer-times/README.md); calculations elsewhere remain estimates. |
| Capacity and widget status summaries | Build on [Capacity Stats](../../addons/capacity-stats/README.md) and existing diagnostics; dynamic Home captions need separate module-contract discussion. No RAM-cleaning service. |
| Personal appearance and app-order backup | A separately scoped feature with bounded validation and recovery tests; the private JSON backup excludes wallpaper image bytes and is not a whole-device backup. |
| Wallpaper, dock and icon styling | A generic theme using existing supported keys, with licensed images and a real-TV screenshot stripped of personal details. |

The private fork's network access, additional dependency and permissions, hard-coded personal layout
and larger APK do not meet the core launcher's contribution constraints as-is. No private source,
wallpaper collection, branded artwork, personal configuration or prebuilt APK is distributed by this
note. These are extension ideas and tested behavior, not ready-to-use add-on downloads. Any future
implementation should extend the matching add-on with its maintainer, or use an appropriate independent
project, with its own
license, permission disclosure and reproducible source.
