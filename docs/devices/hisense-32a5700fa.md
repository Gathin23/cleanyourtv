# Hisense 32A5700FA: Android TV 11, HiSmart 2K ATV4 (hamamatsucho), EU firmware

One unit, Italy, tested in one session on 2026-11-04 with three reboots. This records observations,
not a several-day debloat list.

| Field | Observed value |
|---|---|
| Retail model | Hisense 32A5700FA |
| `ro.product.model` / device / product | `HiSmart 2K ATV4` / `hamamatsucho` / `fushan_emeu` |
| Android / SDK | Android TV 11 / 30 |
| Firmware (`ro.build.display.id`) | `RTO6.250225.001` |
| RAM | 924 MB, 1280×720 UI |
| ADB | network, port 5555, `ro.adb.secure=1` |
| Live TV app / stock launcher | `com.mediatek.wwtv.tvcenter` / `com.google.android.tvlauncher` |

This is the EU build of the board that
[`examples/hisense-hismart-2k-atv4-hamamatsucho/`](../../examples/hisense-hismart-2k-atv4-hamamatsucho/)
covers for India (firmware `RTO6.250817.001`). Three of that list's 14 entries are absent here:
there is no `tv.anoki.acr.controller` ACR tracker and no `com.cvte.startpartnertv` or
`com.cvte.startpartnertvvod` partner storefront. The preloaded streaming apps are Italian
(RaiPlay, Mediaset Infinity), plus Netflix, Prime Video and Disney+.

The session used source revision `04aec4b7`. The launcher was built with `tools/docker-build.sh`.
The only local change was the dock in `config.xml`, with no Java or permission changes.

## Packages disabled during this session

All 14 were applied with `tools/disable.sh` in four batches, with a screenshot after each.
Disabling them raised no NOCHK or input-driver warning on this firmware.
All 14 were still disabled after each of the three reboots, the stock launcher (disabled by `launcher/build-and-install.sh`) after the last two, and the firmware re-enabled none of them at boot.
`com.google.android.marvin.talkback` was already disabled when the set arrived.

| Package | Group |
|---|---|
| `com.google.android.feedback` | telemetry |
| `com.google.android.tvrecommendations` | recommendation rows of the stock launcher |
| `com.google.android.leanbacklauncher.partnercustomizer` | partner promo rows |
| `android.autoinstalls.config.hisense.laoshan` | silent partner-app auto-install configuration |
| `fusion.android.tv.demo` | retail demo |
| `com.mstar.android.tv.disclaimercustomization` | store disclaimer screen |
| `com.android.printspooler` | printing framework |
| `com.google.android.videos` | owner's choice: Google Play Movies (also two TvInputServices for previews) |
| `com.google.android.youtube.tvmusic` | owner's choice: YouTube Music |
| `com.google.android.play.games` | owner's choice: Google Play Games |
| `com.google.android.backdrop` | owner's choice: Ambient screensaver |
| `com.disney.disneyplus` | owner's choice: Disney+ |
| `com.google.android.katniss` | owner's choice: Google Assistant (the remote's mic button stops working) |
| `com.google.android.apps.mediashell` | owner's choice: Chromecast built-in |

As on the India build, with `partnercustomizer` off the stock launcher loses its Inputs and Wi-Fi icons.
Kept: every `com.mediatek.*` package, `com.cvte.autobacklight`, `com.airoha.android.btd`,
`com.google.android.tv.remote.service`, Play Store and Play services, Gboard, and the streaming apps in use.
The factory packages (`com.at.factory.factorymanager`, `com.cvte.fac.menu`, `com.cvte.tv.at`,
`com.cvte.tv.hisensefac`, `com.cvte.tv.fac.rus_apk`, `com.hisensefac.daemon`, `mediatek.factorymenu.ui`,
`com.mediatek.testpq`) were never opened. `com.at.factory.factorymanager` comes to the front for about a second
at every boot by itself; that is the firmware.

After the session, `pm query-services -a android.media.tv.TvInputService` still listed the six
`com.mediatek.tvinput` inputs (Tuner, HDMI, VGA, Composite, Component, SCART) and `com.mediatek.tv.service`.

## Home handover

Before the handover, HOME was claimed only by `com.google.android.tvlauncher/.MainActivity` and the Settings
`FallbackHome`. `setupwraith` did not claim HOME, and the saved Wi-Fi belonged to `android.uid.system:1000`.
The network ran over Wi-Fi, with no USB device attached.

`launcher/build-and-install.sh` (`SKIP_BUILD=1`, no key services) succeeded in one run.
`set-home-activity` took effect, and `mFocusedApp` named `com.gathin.tvhome/.MainActivity`. After a cold reboot
the boot sequence was `FallbackHome`, then the new launcher, then a moment of the factory manager, then the
launcher again.

Neither `InputKeyService` nor `HomeKeyService` was enabled. The owner confirmed on the physical remote that Home,
the Netflix, YouTube and Prime Video buttons, Input, Settings and held Volume Up all worked. The Inputs menu
listed HDMI 1, HDMI 2, Composite and Live TV.

## Live TV at power-on and on wake

The owner wanted Live TV at every power-on, including wake from standby, with Home still opening the launcher.
That needed no rebuild, only a theme `config.json` holding `"open_on_start_choice": true` and
`"open_on_start_on_wake": true`. It was pushed with `tools/theme.sh`, and then
Launcher settings → Open on start → Live TV was picked.

The checks:

- After a reboot, `wm_set_resumed_activity` showed `com.mediatek.wwtv.tvcenter/.nav.TurnkeyUiMainActivity`
  about three seconds after the launcher.
- `input keyevent SLEEP` from Home, then `WAKEUP`, brought Live TV to the front, and Home then returned to
  the launcher.
- The owner confirmed the same with the remote's Power button.

`TV_HOST=<tv ip> tools/theme.sh --reset`, or Open on start → Off, undoes it.

## Notes and limits

- With Live TV or HDMI in front, `screencap` comes back empty, as on other MediaTek sets.
  Over network ADB some `tools/tv.py` calls stalled while the load average read about 100. That was
  D-state kernel threads, and `top` showed the CPU mostly idle.
- Private DNS (`dns.adguard-dns.com`) validated on this firmware.
- One session only: there was no several-day use, antenna scan, HDMI picture or CEC test, and a full
  `tools/restore.sh` run was not tested.
- At the end, network ADB was switched off. Wireless debugging was already off, with no `_adb-tls-connect`
  service. The owner revoked the authorisations, and after a reboot `tools/adb-closed.py` reported 5555 closed.
