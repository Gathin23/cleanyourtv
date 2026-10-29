# ZTE ZXV10 B866F V1.1 (Amlogic `oppen`, Android TV 14): disabling every system launcher triggers a factory reset on the next boot

Android TV 14 (SDK 34), build `EBS.UKG3.250115.001` on the ZTE `oppen` board, reported in
[#2318](https://github.com/Gathin23/cleanyourtv/pull/2318). This is a hazard note, not an `examples/`
list: on this box, disabling every `FLAG_SYSTEM` launcher makes ZTE's `com.control.device` watchdog
broadcast `android.intent.action.FACTORY_RESET` about a minute after the next boot.

## Identify the firmware

| Field | Observed value |
|---|---|
| Retail model | ZTE ZXV10 B866F V1.1 |
| `ro.product.manufacturer` / `ro.product.model` | `ZTE` / `ZXV10 B866F` |
| `ro.product.device` / board | `oppen` (Amlogic, 32-bit `armeabi-v7a`) |
| Android / SDK | 14 / 34 |
| `ro.build.display.id` | `EBS.UKG3.250115.001` |
| System `HOME` packages (`FLAG_SYSTEM`) | `com.google.android.tvlauncher`, `com.ebsproject.drawer` |
| Saved Wi-Fi owner | `com.google.android.tungsten.setupwraith` (`cname=com.google.android.tungsten.setupwraith` in `dumpsys wifi`) |

Stock carrier builds on the same `oppen` chassis ship `com.google.android.tvlauncher` (or a ZTE iptv
home) without `com.ebsproject.drawer`; the ZTE framework and `ControlDevice` checks below come from the
vendor partition and `/system_ext` and apply to both.

## Never disable both `com.google.android.tvlauncher` and `com.ebsproject.drawer`

`/system_ext/app/ControlDevice/ControlDevice.apk` (package `com.control.device`, running as
`android.uid.system`) registers `com.control.device.receiver.ZteKeyInterceptorReceiver` (and a wrapper
receiver `com.control.device.recevier.ZteKeyInterceptorReceiver`) for
`android.intent.action.BOOT_COMPLETED`.

On boot completion, `ZteKeyInterceptorReceiver` schedules a `TimerTask`
(`com.control.device.receiver.A.run()`) that runs about 60 seconds after boot and calls
`ZteKeyInterceptorReceiver.Q(Context)`:

1. `Q(Context)` queries `PackageManager.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)`.
2. It ignores `com.google.android.tungsten.setupwraith` and `com.android.tv.settings`, and checks
   whether any remaining enabled `HOME` activity has `ApplicationInfo.FLAG_SYSTEM` (`(flags & 1) != 0`).
3. A sideloaded launcher (`com.gathin.tvhome`) is installed in `/data/app` without `FLAG_SYSTEM`. If
   both `com.google.android.tvlauncher` and `com.ebsproject.drawer` (or `com.google.android.tvlauncher`
   alone on firmware where it is the only system launcher) have been disabled with
   `pm disable-user --user 0`, `Q(Context)` returns `null`.
4. `A.run()` logs `"[Broadcast] null is normal? false"` and immediately broadcasts a factory reset to
   `system_server`:

```java
Intent intent = new Intent("android.intent.action.FACTORY_RESET");
intent.setPackage("android");
intent.addFlags(268435456);
intent.putExtra("android.intent.extra.REASON", "ResetButon");
context.sendBroadcast(intent);
```

`MasterClearReceiver` accepts the broadcast and reboots the box into recovery
(`boot.reason = reboot,factory_reset`), wiping `/data`.

## Why `com.control.device` cannot be disabled without root, and how `HOME` is resolved

`/system/framework/FrameworkCustomization.jar` (`com.zte.framework.services.pm.PMSImpl`, loaded into
`system_server` via `android.content.FrameworkAdapter`) adds two further restrictions:

- **Package-level disable guard.** `PMSImpl` intercepts `pm disable-user --user 0` for
  `com.control.device`, `com.ztestb.basecomp` and `tv.upgrade.service` and forces their package state
  back to `default`. Disabling the receiver component directly
  (`pm disable com.control.device/.receiver.ZteKeyInterceptorReceiver`) is refused to `shell` without
  root (`Shell cannot change component state`).
- **`HOME` resolution override (`PMSImpl.getBestActivity()`).** Whenever `PMSImpl.isSetupComplete(Context)`
  returns `true` (`Settings.Secure.tv_user_setup_complete == 1 && Settings.Secure.user_setup_complete == 1`),
  `PMSImpl.getBestActivity()` overrides Android's standard `PreferredActivities` and forces `HOME` to
  resolve exclusively to a `FLAG_SYSTEM` activity (falling back to
  `com.android.tv.settings/.system.FallbackHome` if no system launcher is enabled).

## What that means for a root-free debloat

- **Keep at least one `FLAG_SYSTEM` launcher enabled** (`com.google.android.tvlauncher`, or
  `com.ebsproject.drawer` on an EBS build) so `ZteKeyInterceptorReceiver.Q(Context)` finds an enabled
  system launcher on every boot and never fires `FACTORY_RESET`.
- **Keep `com.google.android.tungsten.setupwraith` enabled** unless the saved Wi-Fi network has been
  forgotten and re-joined in Settings first (`dumpsys wifi` shows `cname=com.google.android.tungsten.setupwraith`).
- **Keep ZTE's protected middleware untouched** (`com.control.device`, `com.ztestb.basecomp`,
  `tv.upgrade.service`, `com.zte.TVDaemon`, `com.zte.tv.ydaemon` and `com.BluetoothRemote.Service`).
- In a single-session test on this unit, these 16 non-launcher packages were disabled without
  affecting Ethernet/Wi-Fi, Bluetooth remote pairing or audio/video playback:

```text
com.android.printspooler
com.android.providers.calendar
com.android.providers.contacts
com.android.tv.feedbackconsent
com.EBS.boot
com.google.android.feedback
com.google.android.katniss
com.google.android.marvin.talkback
com.google.android.play.games
com.google.android.syncadapters.calendar
com.google.android.tts
com.google.android.tvrecommendations
com.google.android.videos
com.google.android.youtube.tv
com.limit.NetSpeed
com.valor.mfc.droid.tvapp.generic
```

The owner went further than this project's no-root rule to disable `ZteKeyInterceptorReceiver` and run
a replacement launcher as `FLAG_SYSTEM` on custom firmware; those steps cannot be undone by
`tools/restore.sh` and are not documented here.
