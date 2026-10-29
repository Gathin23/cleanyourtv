# XGIMI MoGo 4 (Google TV 14 portable projector)

Model `WK08K`, device `altai`, MediaTek, 1.8 GB RAM, firmware `UKNV.260612.001.15630599`, French
unit. Both halves work: the debloat list and the Clean Your TV launcher as Home.

**Tested on 24 October 2026, one session of a few hours including one reboot** — not the several
days [CONTRIBUTING](../../CONTRIBUTING.md) asks for. Every entry below was applied and checked on
the real unit; what is not proven is that nothing surfaces after days of use.

Same board as [`xgimi-mogo-3-pro`](../xgimi-mogo-3-pro/), one firmware build older, so that list
applies closely. Differences on this unit: `com.nbcast.wirelessscreen` (MagiCast) is replaced by
`com.xgimi.starmirror`, `com.xgimi.screensaver` and `com.google.android.videos` are not present, and
nothing shipped disabled from the factory (`com.google.android.youtube.tvmusic` is enabled here).

| File | What it is |
|---|---|
| `debloat.txt` | The 22 packages that were disabled, grouped and commented, with what to keep and why |
| `config.json` | Dock order, 5 columns, no tuner, the gear and app buttons, and `stock_home_redirect`; push it with `tools/theme.sh` |

All commands run from the repo root, with the projector's IP in `TV_HOST`.

## Turning ADB on

Settings → System → About → press OK on **Build** 7 times, then Developer options. This firmware
offers **only "Wireless debugging"** — there is no separate network-ADB switch, and USB debugging
alone does not open port 5555. Turning Wireless debugging on also reopens 5555, which is what
`tools/tv.py` uses:

```bash
TV_HOST=<projector-ip> tools/tv.py --probe
```

Google's own `adb` is answered `unauthorized` unless you pair it; `tools/tv.py` authenticates with
its own key and needs no pairing after the first "Allow USB debugging?" prompt.

**Network ADB does not survive a reboot here.** After every restart, Wireless debugging is off again
and the port is closed. Plan the work in one sitting, or expect to walk to the projector.

## Install

1. **Check who owns the Wi-Fi network you are actually on**, before trusting `disable.sh`'s skip
   message. It refuses setupwraith whenever *any* saved network belongs to it, which on this unit was
   a phone hotspot, not the network in use:

   ```bash
   TV_HOST=<projector-ip> tools/tv.py "dumpsys wifi | grep -E '^(\*? ?ID: [0-9])'"
   ```

   The entry marked `Currently Connected: true` is the one that matters. If its `cname` is
   `android.uid.system`, disabling setupwraith is safe and `FORCE_SETUPWRAITH=1` is the right
   override. If it is `com.google.android.tungsten.setupwraith`, forget the network and join it again
   on the projector first.

2. Run:

```bash
TV_HOST=<projector-ip> FORCE_SETUPWRAITH=1 tools/disable.sh examples/xgimi-mogo-4/debloat.txt
TV_HOST=<projector-ip> HOME_KEY_SERVICE=1 launcher/build-and-install.sh
TV_HOST=<projector-ip> tools/theme.sh examples/xgimi-mogo-4
```

`HOME_KEY_SERVICE=1` is not optional on this projector — see below. Pass it on every reinstall;
Android drops the service each time.

## The stock launcher does not stay disabled

`com.google.android.apps.tv.launcherx` re-enabled itself **across the reboot and again within
minutes of being disabled during use**. XGIMI's `com.xgimi.xrmservice` does this on its remote keys,
as the MoGo 3 Pro header already notes.

Do not re-disable it after every boot. `stock_home_redirect` (in `config.json`, needing
`HOME_KEY_SERVICE=1`) sends Home straight back to the new launcher whenever launcherx surfaces, and
that is what actually holds the home screen. Measured: with `HomeKeyService` unbound, one press of
Home landed on the stock Google TV launcher.

Disabling only the home activity, so the package could stay enabled and keep `xrmservice` happy,
does not work on this firmware:

```bash
pm disable --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity   # SecurityException
```

Same refusal as the Hisense set in
[`docs/devices/tcl-55p71b-g09-key-repeat.md`](../../docs/devices/tcl-55p71b-g09-key-repeat.md).

## The helper costs volume-key auto-repeat

With `HomeKeyService` bound, **holding volume up or down moves one step instead of ramping**. The
service binds with `CAPABILITY_CAN_FILTER_KEY_EVENTS` (`capabilities=8` in `dumpsys accessibility`),
and this firmware then drops key repeats — the same mechanism as the TCL, Xiaomi and Hisense sets in
[`docs/devices/tcl-55p71b-g09-key-repeat.md`](../../docs/devices/tcl-55p71b-g09-key-repeat.md).

What the A/B test on this unit showed:

| `enabled_accessibility_services` | Hold volume | Hold D-pad | Home press keeps the new launcher |
| --- | --- | --- | --- |
| `com.gathin.tvhome/.HomeKeyService` | one step per press | repeats normally | yes |
| `""`, with `accessibility_enabled 0` | ramps continuously | repeats normally | **no**, the stock launcher takes over |

Two things differ from that note. Only the **volume** keys lost repeat; a held D-pad direction still
repeats, so navigating the grid is unaffected. And the choice is not the one that note describes: on
those sets dropping the helper costs the gear and app buttons, while here it costs the launcher
itself, because of `xrmservice` above. The owner kept the helper and lives with stepwise volume.

To switch, two `settings` writes either way:

```bash
# helper off: volume ramps, the stock launcher will take Home back
tools/tv.py "settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0"
# helper on
tools/tv.py "settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService; settings put secure accessibility_enabled 1"
```

`am force-stop com.gathin.tvhome`, a cleaner app and `tools/theme.sh` all drop the helper; `theme.sh`
puts it back.

## Remote buttons

All of these were checked by pressing the real remote, not by injecting keycodes.

| Button | Arrives as | Needs |
| --- | --- | --- |
| Gear / Settings | `KEYCODE_NOTIFICATION` 83 | `settings_key_keycodes`, `HOME_KEY_SERVICE=1` |
| YouTube | `KEYCODE_F4` | `app_keys` |
| Netflix | `KEYCODE_F9` | `app_keys` |
| Prime Video | `KEYCODE_F8` | `app_keys` |
| Input / Source | — | works without a helper |
| Home | — | works; `stock_home_redirect` keeps it on the new launcher |

The keycodes are the MoGo 3 Pro's and were confirmed to work unchanged on this remote.

## Inputs and hardware

Only HDMI 1 exists and it opens through `com.google.android.tv.inputplayer` straight from the Inputs
menu, so no `input_action` is needed (unlike the Horizon S Pro). No tuner, hence
`"live_tv_package": ""`.

Autofocus, trapezoidal correction and the remote were checked by hand after every batch and were
unaffected throughout. The XGIMI packages behind them are listed under "DO NOT DISABLE" in
`debloat.txt`.

## Notes on `config.json`

The dock here is Netflix, YouTube, Prime Video and Apple TV. The tested unit had a fifth dock app
(an IPTV client) that is too personal to ship; with `columns` 5 the free slot fills itself from the
grid, so the dock has no gap. Reorder from the projector with a long press on the Settings pill.
