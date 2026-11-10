# Durabrand DB Android TV 4K (`DURAT32ISDB`, dupont): two owner reports

The tested list for this set is [`examples/durabrand-durat32isdb/`](../../examples/durabrand-durat32isdb/debloat.txt),
from the second unit below, run for several days with a launcher swap. This note keeps what the
list header has no room for: the first owner's report, the packages the firmware turns back on and
the factory-menu trap.

The first report came from
[#470](https://github.com/Gathin23/cleanyourtv/issues/470): one session and one day of use, no
launcher installed. On its own it was **not** a tested list in the `examples/` sense - CONTRIBUTING
asks for several days of living with a list before it goes there - so its packages and the two traps
that came with them were recorded here first.

## Identify the firmware

| Field | Reported value |
|---|---|
| Retail model | Durabrand DB Android TV 4K |
| Product | `DURAT32ISDB` |
| Device | `dupont` |
| SoC | `m7332` (Haier / CVTE board) |
| Android / SDK | Android TV 11 / 30 |
| `ro.build.display.id` | `RTM6.230109.232` |
| Security patch | 2023-08-01 |

The board is the same Haier/CVTE family as
[`examples/haier-android-tv-dvb2k/`](../../examples/haier-android-tv-dvb2k/debloat.txt) and
[`examples/haier-h32s80efx/`](../../examples/haier-h32s80efx/debloat.txt), but the package set
differs: this unit has an mstar disclaimer app, a MediaTek input panel, and a system content
launcher that the firmware restarts at every boot. Apply those lists here only after checking each
name with `dumpsys package`.

## What the owner disabled

Ten packages, `pm disable-user --user 0`, one day with no breakage reported.

| Package | What it is |
|---|---|
| `fusion.android.tv.demo` | Retail demo (`HRetailDisplay_2K`) |
| `com.haier.apps` | Store shortcuts |
| `com.haier.apps.evoSmart` | Store / partner promo |
| `com.mstar.android.tv.disclaimercustomization` | Store disclaimer screen |
| `com.google.android.feedback` | Google feedback |
| `com.google.android.videos` | Play Movies |
| `com.google.android.play.games` | Play Games |
| `com.google.android.youtube.tvmusic` | YouTube Music |
| `com.android.printspooler` | Printing (no printer on a TV) |
| `com.google.android.syncadapters.calendar` | Calendar sync for an account nobody signs in with |

The owner also set the three animation scales to `0.5` and reports free RAM going from 495 MB to
550-651 MB of 1.34 GB, plus no retail demo, store disclaimer or Haier/Okko promos at boot.
Netflix, YouTube, Disney+, Max, Prime Video, Jellyfin, HDMI, CEC and casting still worked at the
end of that session.

## Left alone, and why

- `com.google.android.tvrecommendations` and `com.haier.launcher.haiermatrix` come back on every
  boot: `com.at.factory.factorymanager` calls `enableApp` for both, and the second is a system app
  re-enabled during the boot package scan. Disabling them looks like it worked until the next
  restart. ([PR #434](https://github.com/Gathin23/cleanyourtv/pull/434) is the tool that reports
  this kind of drift; until it is merged, check by hand with
  `tools/tv.py 'pm list packages -d | grep -E "tvrecommendations|haiermatrix"'` after a reboot.)
- `com.haier.tv.hmagicboard` receives the remote's hot keys, and `com.cvte.tv.hkcfac` is the
  factory app that also receives them - see the trap below before touching either.
- `com.google.android.tungsten.setupwraith` owns a saved Wi-Fi network on this firmware. Forget and
  rejoin that network before disabling it, or the TV goes offline for good:
  see [the setupwraith Wi-Fi entry](../TROUBLESHOOTING.md).
- `*partnercustomizer` and `com.android.location.fused` were kept.

## The Input key belongs to the MediaTek TV app, not to `tvlauncher`

The panel behind the remote's Input button is provided by `com.mediatek.wwtv.tvcenter`, not by
`com.google.android.tvlauncher`. After a launcher swap the Input button therefore keeps working
even with `tvlauncher` disabled, but the panel is the TV app's own overlay: the reference launcher's
`InputKeyService` is not needed, and `INPUT_KEY_SERVICE=1` should not be set on this unit on the
strength of another Haier list.

## The hidden production menu is the expensive trap

Opening `com.cvte.tv.hkcfac` (a "production" app with a launcher icon nobody uses) sets
`customer_factory_mode=true` in `/vendor/tvcertificate/fdata.ini` and
`vendor.mtk.factory.disable.input=1`. Three things follow:

- the Input key stops working and CEC switches off;
- on the next boot `factorymanager` disables Play services, Netflix, `com.google.android.katniss`
  and `com.google.android.apps.mediashell`, and the shell cannot re-enable those packages;
- casting and the store stay dead until the factory mode is cleared.

The way out without root, as reported: start the app, send factory-exit keycode 218 twice **while it
is in front**, reboot, then turn CEC back on:

```sh
tools/tv.py 'am start -n com.cvte.tv.hkcfac/.MainActivity'
tools/tv.py 'input keyevent 218; input keyevent 218'
tools/tv.py --reboot               # returns once ADB accepts it; wait for the TV to come back
tools/tv.py 'settings put global hdmi_control_enabled 1'
```

The exit keycode only works with the app in the foreground, so the two key events must not be sent
before it has drawn. The same trap in prose, next to the other "commands that break things" entries,
is in [TROUBLESHOOTING.md](../TROUBLESHOOTING.md).

## Validation scope

- One day of use, one owner, no multi-day soak and no reboot campaign beyond the factory-mode exit.
- No custom launcher was installed: Projectivy worked, the owner went back to the stock launcher by
  preference. Home handover, the launcher's Input/Home helpers and `restore.sh` were not exercised
  on this unit.
- The package list is the owner's, reproduced here as reported; it was not re-read from the TV by
  this note's author, and no screenshot of `dumpsys package` accompanied it.
- The RAM figures are the owner's, measured before and after in one session.

## A second unit, with a launcher swap

A second owner's set, the same product, device, board, firmware and security patch as above, read
from the TV with `getprop`. It adds what the first report could not cover: the Home handover, the
reboots after it, and a third package the firmware turns back on. Its list, after four days of use,
is [`examples/durabrand-durat32isdb/`](../../examples/durabrand-durat32isdb/debloat.txt).

Disabled with `tools/disable.ps1` in five small batches, each checked with `dumpsys package` first,
then one reboot:

- the nine from the first report other than the store disclaimer: `fusion.android.tv.demo`,
  `com.haier.apps`, `com.haier.apps.evoSmart`, `com.google.android.feedback`,
  `com.google.android.videos`, `com.google.android.play.games`,
  `com.google.android.youtube.tvmusic`, `com.android.printspooler` and
  `com.google.android.syncadapters.calendar`;
- `com.haier.tvhousekeeper`, Haier's "TV Assistant" cleaner (`/product/app/TV_Assistant`), which
  the Haier lists in `examples/` disable too;
- `com.google.android.tvlauncher`, at the Home handover below.

`com.mstar.android.tv.disclaimercustomization` was not in this unit's batches and is still enabled;
nothing here says whether it is needed. Two apps installed under `/data/app` rather than shipped in the
firmware were disabled as well and are not part of this note.

What happened:

- `com.google.android.katniss` (the Assistant) was disabled and was enabled again at the next boot,
  like `tvrecommendations` and `haiermatrix` above. The cause was not traced on this unit; disabling
  it again only lasts until the next restart, so it was left enabled.
- The Home handover needed the stock home disabled: with the reference launcher holding the HOME
  role, the Home button still opened `com.google.android.tvlauncher` until it was disabled, which is
  what `launcher/build-and-install.sh` does by default. After a reboot Home came back to the new
  launcher, and it has stayed there since. The launcher on this set is a fork of this repo's (the
  [CleanYourTV Editorial](../../SHOWCASE.md) row), with the same package and Home handover.
- The Input button kept opening the MediaTek panel after the swap, as described above.
- Reinstalling only `base.apk` of an app that ships as four split APKs failed with
  `INSTALL_FAILED_VERIFICATION_FAILURE`; installing all four together with `adb install-multiple`
  succeeded with Play Protect left on. The failure was the incomplete install, not a block on
  installing apps from ADB.
- `com.google.android.tungsten.setupwraith`, `com.haier.tv.hmagicboard` and `com.cvte.tv.hkcfac`
  were left alone and the factory menus were never opened.

Validation scope for this unit: one owner, four days, two reboots (one after the
cleanup, one after the launcher). The owner confirmed playback in YouTube, Stremio and an IPTV app,
and the remote's Home, Back and volume keys after the swap. HDMI-CEC, a real Chromecast
session and a firmware update were not tried.
