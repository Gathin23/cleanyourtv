# Skyworth 8N05 Q30 / 65Q30 (Coocaa CCOS, Android 8.0): Home cannot be replaced, and a debloat does not survive a reboot

From @linzhijie30618's session in [#2337](https://github.com/Gathin23/cleanyourtv/issues/2337) on 2026-11-04. The owner
asked for the set to be put back the way it was, and the report named no packages at the time, so this page is a record
of what the firmware does. The 18 packages came in on 2026-11-26 and ship as
[`examples/skyworth-8n05-q30/`](../../examples/skyworth-8n05-q30/), with the reboot caveat below written into that
list's header and its README.

## Device

| Field | Value |
| :--- | :--- |
| Retail model | Skyworth 8N05 Q30, sold as the 65Q30 |
| `ro.product.model` / device | `8N05_Q30` / `nt72671_a32`, manufacturer Skyworth |
| SoC | Novatek NT72671, 2 GB RAM |
| Android / SDK | 8.0.0 / 26 |
| Build | `Skyworth/sky8N05/nt72671_a32:8.0.0/OPR6.170623.012/022.007.270:user/dev-keys` |
| Firmware | Coocaa CCOS (`com.tianci.*`, `com.coocaa.*`), no Google Play services |
| Stock Home | `com.tianci.movieplatform` |

The launcher's `minSdk` is 26, so the APK installs on this build; what is closed here is Home, not the API level.

## Home cannot be replaced on this firmware

| Attempt | Result |
| :--- | :--- |
| `cmd package set-home-activity` | Refused: `cannot be home on user 0` |
| Pointing `persist.service.homepage.pkg` at another launcher | No effect at runtime; reverted at boot to `com.tianci.movieplatform` from `DEFAULT_HOMEPAGE` in `/system/pcfg/8N05_Q30/config/general_config.xml`, which is read-only |
| Disabling `com.tianci.movieplatform` | Not attempted: an owner on the same firmware family got a black screen at boot with `sys.boot_completed` never set |

The Clean Your TV app's own report agrees with the first route: `Home switch: blocked UNKNOWN_HOME`, `Resolves to:
nothing (or the chooser)`, `Claimants: none`.

## This CCOS build binds no third-party accessibility service

The redirect route (the `stock_home_redirect` half of [#1455](https://github.com/Gathin23/cleanyourtv/pull/1455), with
`HOME_KEY_SERVICE=1`) stops at the system rather than at the helper:

- `settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService` is written successfully.
- `dumpsys accessibility` reports `services:{}` both after the write and after a reboot.
- The firmware has no accessibility settings screen at all: `resolve-activity -a android.settings.ACCESSIBILITY_SETTINGS`
  answers "No activity found".
- The package was not stopped, its `BIND_ACCESSIBILITY_SERVICE` declaration was in place and its process was running.

That is also why `tools/tv.py 'logcat -d -s HomeKeyService'` prints nothing: `HomeKeyService.java` has no `Log.*` calls,
and the service was never started. An empty log here means the system never bound it, not that it started and failed.

## Two things a debloat runs into

- **Disabling `com.tianci.appstore` breaks My Apps.** The CCOS home screen's My Apps entry opens
  `AppStoreHomeActivityV3` in that package, so the button does nothing while it is off. It was the only disable in the
  session that took away a feature the owner uses.
- **The firmware re-enables packages by itself.** After two reboots, 17 of the 18 disabled packages were back on;
  only `com.tianci.appstore` was still disabled. A debloat on this set does not survive a power cycle, which is worth
  knowing before planning one.

The boot ad is a file rather than a package: `persist.sys.bootvideo.time` was set to 1 and the firmware wrote 15 back
itself, and `/data/local/bootvideo.ts` (6.8 MB, owned by system) cannot be removed without root.

## What has to stay

The device-info / factory page is the only place ADB can be turned on, and it stayed usable throughout:
`am start -a coocaa.intent.action.TIANCI_FACTORY` opens it normally. These carry it or the home screen:

`com.tianci.de`, `com.skyworthdigital.stb.dataprovider`, `com.coocaa.sky.ccapi` (these three carry the device-info page),
`com.coocaa.factory`, `com.tianci.movieplatform`, `com.skyworth.tv`, `com.tianci.localmedia`.

## Limits

- One session on one set, reverted afterwards: `pm list packages -d` is empty, the stock Home is untouched and the
  launcher was uninstalled.
- The 18 packages were written down on 2026-11-26 and are the list in
  [`examples/skyworth-8n05-q30/`](../../examples/skyworth-8n05-q30/): one session rather than a several-day run, and 17
  of the 18 came back at the next boot.
- Nothing above was retested after the revert, and no other CCOS build was checked: the accessibility finding is this
  firmware's, and the way to tell is to write `enabled_accessibility_services` and read `dumpsys accessibility` back.
