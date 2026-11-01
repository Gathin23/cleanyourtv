# waipu.tv Stick (SEI700WP, Amlogic): `pm disable*` is refused, so the list is suspended

One session, same-day results plus one cold-boot and one reboot check. That is why this is recorded here
and not in `examples/`: CONTRIBUTING asks for a few days of living with a list.

## Identify the device

| Field | Value |
|---|---|
| Retail name | waipu.tv Stick (`ro.product.model` `waipu tv stick`) |
| Maker | SEI Robotics (`ro.product.manufacturer`), device `SEI700WP`, board `SEI700WP`, hardware `amlogic` |
| Android | 11 (SDK 30), build `RTT0.211009.001.6958 release-keys` |
| Fingerprint | `waiputv/SEI700WP/SEI700WP:11/RTT0.211009.001/6958:user/release-keys` |
| Screen | 1920x1080, density 320, no override |
| Network | Wi-Fi; no USB Ethernet adapter, nothing in the USB port |
| ADB | port 5555 after one prompt; `ro.adb.secure=1` |

## `pm disable-user` is refused

Every `pm disable-user`, and `pm disable <component>`, answers `Warning! This command is illegal!`, so
`tools/disable.sh` records the packages and disables none (`pm list packages -d` stays empty).
`/system/bin/pm` is a two-line script (`cmd package "$@"`): the refusal is in the firmware's package
service. It is the same refusal as the [Nokia Streaming Box 8000](../../examples/nokia-streaming-box-8000/README.md)
and the TiVo Stream 4K, which share the `com.nes.*` services.

What works, as on the Nokia box: `pm suspend --user 0 <pkg>` and
`cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore`, and `am force-stop`. Eight packages, one per
shell call, `Package ... new suspended state: true` each; `dumpsys package <pkg> | grep suspended` showed
`suspended=true` for all of them after a reboot:

| Package | What it is |
|---|---|
| `com.google.android.katniss` | voice search / Assistant |
| `com.google.android.tvrecommendations` | recommendation rows |
| `com.google.android.apps.mediashell` | Chromecast receiver |
| `com.google.android.play.games` | Play Games |
| `com.google.android.videos` | Play Movies |
| `com.google.android.feedback` | Google feedback |
| `com.google.android.backdrop` | ambient mode / screensaver |
| `com.nes.tvbugtracker` | vendor bug tracker |

Undo, per package: `pm unsuspend --user 0 <pkg>` and `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND allow`.

Left alone on purpose: `de.exaring.waipu` (the waipu app, see Home), `com.google.android.tungsten.setupwraith`
(owns the saved Wi-Fi network, `dumpsys wifi | grep -o 'cname=[^ ]*'`), the `com.nes.*` remote, OTA,
core-service and key-handler packages, `com.standard.irtvcontrol`, and `com.utsmta.app` (a factory-test
installer, never started).

## Home

The waipu app is the stock launcher: `de.exaring.waipu/.MainActivity` and Settings' `FallbackHome` are the
only two HOME claimants, and there is no separate vendor launcher to disable. The new launcher was
installed with `STOCK_LAUNCHER=none launcher/build-and-install.sh` (the script does not know the waipu app
as a stock launcher and stops without that), and `set-home-activity` really took:
`cmd package resolve-activity` and `mFocusedApp` both named `com.gathin.tvhome`. It was still installed and
still Home after a reboot, so `com.nes.coreservice` did not remove it here, unlike what the TiVo note
describes for that box.

**After a cold boot the waipu app comes up over the launcher.** The waipu app has a `BOOT_COMPLETED`
receiver, `de.exaring.waipu/.entrypoint.RichBootReceiver`, and the permission
`START_ACTIVITIES_FROM_BACKGROUND`, so it opens its own screen a moment after Home. One press on Home brings
the launcher back. What did not help:

- the app's own switch "App startet mit Startseite": it only chooses the first screen inside waipu (home
  page or live stream); the app still starts;
- `RUN_IN_BACKGROUND` and `RUN_ANY_IN_BACKGROUND` set to `ignore` for the waipu app, then a reboot: waipu still
  came up over the launcher (reverted);
- `pm disable` of the receiver (illegal) and `pm revoke` of the permission (`not a changeable permission
  type`: it is `signature|privileged`).

`stock_home_redirect` set to the waipu app would send you back to the launcher, but it also does that every
time you open waipu on purpose, since its main screen is its HOME activity, so it was not tried.

## Remote buttons

The remote (`B18F Consumer Control`, `Vendor_2d01_Product_c006.kl`) has three app buttons that report
`KEY_UNKNOWN` with consumer usages `0x0c00d2` (waipu-thek), `0x0c00d1` (Netflix) and `0x0c00d3` (YouTube),
which the key layout maps to `NES_SHORTCUT_02`, `STEM_1` and `NES_SHORTCUT_04`. The vendor's
`com.nes.tvglobalkeyhandler` answers them (it launches Netflix and YouTube itself), so with the stock launcher
gone all three still open their apps and Home returns to the new launcher. No remote-key helper was needed.

## Notes for the next owner

- Build with a full JDK 17. On macOS the only 17 `java_home` found was Temurin's JRE, which has no `jlink`, and
  Gradle stops with `jlink executable ... does not exist`; Homebrew's `openjdk@17` as `JAVA_HOME` fixed it.
- `app_process` is killed for the shell user here (`Error changing dalvik-cache ownership`, then `Killed`), so
  a small Java helper that calls the package service cannot be run over ADB.
- The unset Private DNS setting was changed to `dns.adguard-dns.com` (`private_dns_mode hostname`) and
  resolution kept working; the undo is `private_dns_mode opportunistic`.
- Close ADB afterwards: USB debugging off, authorisations revoked, reboot, then `tools/adb-closed.py <tv ip>`
  reported the port refused.
