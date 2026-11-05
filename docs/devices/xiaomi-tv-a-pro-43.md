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
was bound, and the owner confirmed the physical YouTube button worked.

## Another unit on the same firmware (November 2026)

A third unit, same `MiTV-MZTU0` / `river` model and the same `V816.0.26.6.2.6.URTMIXM` build, with
1.8 GB of RAM. It was cleaned on 2 November 2026 with `PROMPT.md`. Everything below was checked across
reboots. This is not a claim of several-day validation of every function, and no new list is added here.

Seven batches disabled 31 packages, plus launcherx after the new launcher was Home: telemetry and feedback,
PatchWall and Mi channels, unused Xiaomi and Google apps, Privacy Sandbox, setupwraith, `com.google.android.apps.tv.dreamx`
and `com.mediatek.calman`. Calman Ready is the service for picture calibration from a PC; nothing else
missed it here. All three firmware updaters, Live TV and the tuners, AirPlay, the Assistant, the remote
microphone, Chromecast and the Play Store stayed enabled.

- **Setup wizard:** on this unit `dumpsys wifi | grep -o 'cname=[^ ]*'` named `android.uid.system`, so setupwraith
  was disabled while on Wi-Fi and Wi-Fi stayed up. The check still matters: the first report above found
  setupwraith as the owner. With setupwraith off, "Usage and diagnostics" in Settings no longer opens.
- **Netflix:** after a reboot, 31 of the 32 listed packages were still off. The firmware had turned
  `com.netflix.ninja` back on and reset its appops to allow. It was left on, kept off Home and out of the
  library (`hidden_apps`, `library_excluded_apps`) and stopped from running in the background, as
  [TROUBLESHOOTING](../TROUBLESHOOTING.md) describes: `cmd appops set com.netflix.ninja RUN_ANY_IN_BACKGROUND ignore`,
  the same for `RUN_IN_BACKGROUND`, then `cmd appops write-settings`. A reboot confirmed that it stayed.
  `tools/restore.sh` does not undo this: set both back to `default` and write the settings again.
- **A USB stick as internal memory:** this unit had one set up as adoptable storage and felt slow before
  the cleaning. That is the likely cause, not a proven one. The load average of 19 is not a busy CPU: about
  20 MediaTek driver threads wait in the kernel and the CPU was 94 % idle at that reading.
- **Storage:** system apps live on the read-only part, so disabling them frees nothing. Clearing speech data
  and trimming caches took `/data` free from 2.3 GB to 2.6 GB.
- **Wallpaper after a reboot:** Home showed no wallpaper until the theme was pushed again. Home starts before
  shared storage is mounted, so the launcher finds no wallpaper files at that moment.
- **Factory reset:** not tried. As a precaution, run `tools/restore.sh` first, with launcherx named too, so
  that the setup wizard and the stock launcher are on when the reset starts.
