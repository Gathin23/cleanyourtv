# FPD CG50-C3: a one-day list, and a remote with two buttons Android never maps

The `FPD CG50-C3` is a 50-inch set sold in Canada. The brand is not the builder: `ro.product.model`
is `EcoTV Pro A`, device `hongkong`, product `FPD_4KHA0`, on a MediaTek SoC. The owner ran
[`PROMPT.md`](../../PROMPT.md) with Claude Code on Windows 11 (the PowerShell scripts and `tools/tv.py`)
and reports that the debloat, the launcher install, the HOME handover, the remote remap and AdGuard
Private DNS all worked, with ADB closed at the end.

He also says when: the list was applied the day he wrote #475. So **this is not an `examples/` list**.
[`CONTRIBUTING.md`](../../CONTRIBUTING.md) asks for a few days of living with a list before it goes there,
and nothing here was reboot-soaked or repeated on a second unit. The package block below is his, quoted
as he posted it; the sections after it are the parts that carry to another set, which is why the file
exists at all.

## Identify the firmware, not the brand

A retail name on a KTC board tells you almost nothing about the software, so record what
`getprop` answers:

| Field | Observed value |
|---|---|
| Retail model | `FPD CG50-C3`, 50 inch |
| Market | Canada |
| `ro.product.model` | `EcoTV Pro A` (KTC) |
| `ro.product.device` | `hongkong` |
| `ro.product.name` (product) | `FPD_4KHA0` |
| SoC | MediaTek |
| Android / SDK | 11 / 30, Google TV |
| `ro.build.display.id` / firmware | `RTM9.241015.016` |
| Usable RAM | 1.8 GB |
| UI | 1080p |
| `ro.adb.secure` | `1` |

## Reaching ADB

USB debugging alone opened network ADB on port 5555; `ro.adb.secure=1` means the first connection
shows the RSA prompt on the TV, which the owner accepted. At the end of the session port 5555 was
refused after a reboot, i.e. the debugging toggle did not survive it.

## The list as applied

Transcribed from #475. One day of use, one reboot, no soak - read it as a starting point and comment
out what you need.

```text
# Ads, auto-installs, telemetry
com.mediatek.android.leanbacklauncher.partnercustomizer   # partner rows injected into the stock home
android.autoinstalls.config.google.gtvpai                 # Play auto-installs
com.google.android.feedback                               # feedback reports to Google
com.google.android.apps.tv.dreamx                         # Ambient screensaver with recommendations

# Manufacturer preloads
fusion.android.tv.demo                                    # retail store demo loop
com.mstar.android.tv.disclaimercustomization              # first-run disclaimer screen
com.electronic.manual                                     # e-manual
com.mediatek.androidbox                                   # Miracast / screen sharing (WFDSinkTest); keep if you mirror

# Preloaded apps, off by choice
com.google.android.videos                                 # Google TV movies & shows store
com.google.android.play.games                             # Google Play Games
com.google.android.youtube.tvmusic                        # YouTube Music

# Home handover: re-enable setupwraith before a factory reset (restore does it)
com.google.android.tungsten.setupwraith                   # first-run wizard, takes Home otherwise
com.google.android.apps.tv.launcherx                      # stock Google TV home (sponsored rows); replaced by launcher/
```

Stock Home is `com.google.android.apps.tv.launcherx`, Live TV is `com.mediatek.wwtv.tvcenter`, and the
launcher's default `live_tv_package` already matches it. The Sources list shows HDMI 1-3, Composite and
Live TV, so the Inputs side needs no `input_action`.

## setupwraith owned a saved network, but not the one in use

`com.google.android.tungsten.setupwraith` claims HOME here through `.RecoveryActivity` at priority 1,
the usual Google TV situation, and the handover needed the usual package-level disable.

The Wi-Fi question had a different answer than on most sets. This unit has several saved networks and
setupwraith created one of them - not the network the TV was on. Disabling it therefore did not take
the TV offline, and `FORCE_SETUPWRAITH=1` was safe. The check is two commands rather than one, because
the first only names the creators:

```sh
tools/tv.py "dumpsys wifi | grep -o 'cname=[^ ]*'"
tools/tv.py "cmd wifi status"
```

If the network in `cmd wifi status` is the one whose `cname=` is setupwraith, forget and rejoin it
before the install instead of forcing. If the creators include setupwraith but the active SSID is
another one, forcing costs nothing today - and costs the unused network tomorrow, which is fine.

`logcat -b crash` stayed clean after the handover: no `FATAL EXCEPTION IN SYSTEM PROCESS` and no
`setupwraith/.RecoveryActivity` restart loop. That is worth checking rather than assuming, since the
same disable is what starts the loop on some Google TV 14 builds.

## The RC-36 remote: two buttons Android never maps

The remote is an RC-36 over Bluetooth (vendor `0x0b7a`, product `0x0001`). `dumpsys window policy`
shows the firmware routing its keys to apps that survive the debloat: `KEYCODE_SETTINGS` and the app
buttons (Netflix and friends) go to `com.mediatek.tv.service`, `KEYCODE_TV_INPUT` goes to
`com.mediatek.wwtv.tvcenter`. So Input and the app buttons keep working once launcherx is gone, which
is not true on every Google TV set.

Two buttons died with launcherx, and neither can be fixed by keycode:

| Button | Scancode | Keycode Android reports |
|---|---|---|
| gear (Settings) | 240 | `KEY_UNKNOWN` (HID usage `0x0c009f`) |
| notifications (program guide) | 362 | `KEY_PROGRAM` |

`KEY_UNKNOWN` is not a code you can list - every unmapped button shares it - so the match has to be
on the Linux scancode:

```sh
INPUT_KEY_SERVICE=1 HOME_KEY_SERVICE=1 ./launcher/build-and-install.sh
# config.xml (or the theme's config.json copy of it):
#   settings_key_scancodes  240, 362
#   settings_key_panel      true      # gear opens the small panel instead of the full Settings app
```

`settings_key_scancodes` is read by `HomeKeyService`, hence `HOME_KEY_SERVICE=1`, and only from
`config.xml`: the theme can set `settings_key_keycodes`, not scancodes. Read the scancode with
`tools/tv.py 'getevent -t'` while pressing the real button - an injected `input keyevent` never
reaches an accessibility service, so it cannot be used to test this. `-t` rather than `-lt` because
`-l` prints the key's name in place of the number, and the entry takes the hex that line prints in
decimal: `00f0` is 240.

`HomeKeyService` bound on its own here, with no `OP_AUTO_START` denial in `logcat`, so the TCL-style
auto-start block does not apply to this firmware.

## What was kept, and why

- `com.ktc.tv.factory` and `ktc.factorymenu.ui`: both have `HotKeyReceiver` / `FactoryKeyReceiver`
  registered for remote global keys. They look like bloat and they are reachable, but disabling them
  takes remote buttons with them.
- `com.mediatek.autopair`: re-pairs the remote. Losing it means no way back into the TV without a
  USB keyboard.
- `com.mediatek.wwtv.*` and the TV input packages: Live TV and the input switcher.
- `com.ktc.serialport`: a factory serial console whose purpose the owner could not establish. Kept
  because nothing was known about what stops working without it.
- Assistant, Chromecast, Netflix: kept by choice.

The active screensaver was `com.google.android.apps.tv.dreamx`, so the list disables it and the launcher
supplies `com.gathin.tvhome/.ClockDream` instead. No manufacturer OTA package was found on the box,
so nothing in the list is about firmware updates.

AdGuard Private DNS was set up alongside the debloat and YouTube still loads and plays; its ads stay,
which is expected - DNS blocking does not touch same-origin ad requests.

## Limits

- One session, one set, one reboot. No multi-day test, no second unit, no screenshots.
- The list is the owner's; the package annotations are his. Only the ordering and the surrounding
  sections are this file's.
- Nothing here says the same firmware exists on other FPD sizes: check the `ro.product.device` and
  build first.
- Promote this to `examples/fpd-cg50-c3/` once someone has lived with it for a few days and can say
  what came back after the next firmware update.
