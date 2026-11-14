# Qilive FHD Android TV (AiPlus2K, Android TV 11)

Owner-observed report from 14 November 2026 on one Qilive FHD Android TV. CleanYourTV 3.5
was installed and used on this set. The package change and launcher handover were checked
in the same session and after a reboot. This is **not** a several-day-tested debloat list;
there is no `examples/` list for this unit yet.

## Verified device identity

| Field | Observed value |
|---|---|
| Retail / Android model | Qilive FHD Android TV |
| Product / device | `Qilive_FHD_ATV` / `tamachi` |
| Manufacturer / board | Expressluck / MediaTek `m5621` |
| Android TV / API | 11 / 30 |
| Build | `RTO3.260823.001` |
| Security patch | 2025-11-01 |
| Application ABI | `armeabi-v7a` (32-bit) |
| Stock Home | `com.google.android.tvlauncher/.MainActivity` |
| Live TV | `com.mediatek.wwtv.tvcenter` |

The firmware also contains `com.cvte.*` packages, including TV settings and Live TV
overlays. Its factory and test packages were not opened. The CVTE board precaution applies:
keep the stock launcher enabled unless an exact-board tested list establishes that it is
safe to disable. This unit's stock launcher was kept enabled.

## Package change and components kept

`tv.anoki.acr.controller` (Anoki automatic content recognition) was disabled for user 0
with the project's reversible disable workflow. Its original enabled state was recorded
for restoration. The change was checked in this session and after a reboot; it has not
been observed for the several days required by `CONTRIBUTING.md` for an `examples/` list.
The locally drafted first batch also named a partner auto-install configuration and
Google feedback, but that draft is not a tested public list.

The stock Home, `com.google.android.tungsten.setupwraith`, MediaTek TV/input/HDMI
components, CVTE components, remote and Bluetooth services, keyboard, Play Store and
Play Services were deliberately kept. Chromecast, streaming apps and firmware update
components were kept as well. No package was uninstalled.

## Home on this firmware

The conventional HOME selection methods did not make CleanYourTV the persistent screen
opened by the physical Home button on this unit. The owner tested the normal Home
handover before using CleanYourTV's `HomeKeyService`. Disabling the stock launcher was
avoided on this CVTE firmware.

With the stock launcher still enabled, `HomeKeyService` redirected its Home screen to
CleanYourTV. The working device-specific theme settings were:

```json
{
  "stock_home_redirect": "com.google.android.tvlauncher",
  "key_filter": true,
  "live_tv_package": "com.mediatek.wwtv.tvcenter"
}
```

`stock_home_redirect` names the stock Home package actually present on this TV. The
`key_filter` setting reflects the tested configuration; it is not proposed as a default
for other TVs. The owner checked the Home redirect, launcher navigation and Live TV access
after a reboot. This was a recent functional check, not a multi-day stability test.

With `HomeKeyService` filtering keys, holding a volume button did not repeat volume
steps normally: a long hold could move only one step. Short volume presses still worked.
This is a limitation of the tested configuration. Changing `key_filter` may affect the
helper's key handling and has not been documented here as a tested fix on this unit.

The pre-change backups, recovery records, logs and screenshots remain local; they can
contain private device or network information and are not part of this contribution.
