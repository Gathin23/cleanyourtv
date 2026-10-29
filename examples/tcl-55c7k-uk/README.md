# TCL C7K (G08, Google TV, Android 14), UK and EU firmware

TCL C7K QD-Mini LED, device `G08`, model "Smart TV Pro", firmware `UTT2.250416.001 AU02`, MediaTek, about
2.4 GB RAM. Two units have run this list: a 55C7K-UK on Wi-Fi (the first, see the header of `debloat.txt`) and a
50C7K on Ethernet in Poland (below). Both halves work on this set: the debloat list and the Clean Your TV
launcher as Home.

| File | What it is |
|---|---|
| `debloat.txt` | The packages that were disabled, grouped and commented |
| `config.json` | Hidden TCL tiles, the Live TV app, and the Settings, Netflix and YouTube buttons; push it with `tools/theme.sh` |

All commands run from the repo root with the TV's IP in `TV_HOST`, and USB debugging on.

## Install

The order matters: setupwraith goes while Google TV's home still owns Home, then `build-and-install.sh` starts the
new launcher and only then disables launcherx.

```bash
TV_HOST=<tv ip> tools/disable.sh examples/tcl-55c7k-uk/debloat.txt
HOME_KEY_SERVICE=1 TV_HOST=<tv ip> launcher/build-and-install.sh
TV_HOST=<tv ip> tools/theme.sh examples/tcl-55c7k-uk
tools/tv.py "cmd appops set com.gathin.tvhome AUTO_START allow; cmd appops write-settings; settings put secure accessibility_enabled 1"
```

`disable.sh` skips launcherx on the first run (its Home guard), and dreamx should go only after the screensaver
is switched to `com.gathin.tvhome/.ClockDream` (`settings put secure screensaver_components ...`). The last line is
needed for the remote buttons to keep working after a restart; see below. On the 50C7K the keys in `config.json`
were set in `launcher/app/src/main/res/values/config.xml` instead and the theme push was not run; the keys are the
same.

## Second unit: 50C7K, Poland, Ethernet

TCL 50C7K, product `G08_4K_GB`, MediaTek `mt5896`, same `UTT2.250416.001 AU02`, EU region, on the built-in
Ethernet port. Set up on 23 October 2026, in six batches with screenshots between them, and rebooted twice
afterwards: all 34 packages stayed disabled, nothing re-enabled itself, and Home, Ethernet, the Clock screensaver
and the remote buttons came back. That is one day of use, not weeks.

**Different choices from the UK unit:** Netflix (both lines) and the game bar kept (their lines in "Off by choice"
commented out), and Prime Video, Chromecast and AirPlay disabled (the "Kept on this unit" lines uncommented, the soundbar
updater and YouTube still kept). The local apps (Canal+, TVP Sport) were installed by the owner and are not in
the list. `tv.mopa.ginga` and `com.tcl.android.webview` are on this build too and were left alone.

What was checked on the TV: the Input button's source panel after each batch, Home, the Inputs menu (HDMI 1-4
with the connected ports marked, and Live TV), switching to HDMI 1 (`com.tcl.tv/.TVActivity` on top), D-pad from
every edge, the Clock screensaver, YouTube playback with AdGuard Private DNS on.

Notes from this unit:

- **setupwraith owned a saved Wi-Fi network** even though the set runs on Ethernet. `disable.sh` skips it; with
  the TV on Ethernet and the owner fine with losing that network, `FORCE_SETUPWRAITH=1` disabled it and ADB stayed
  up.
- **Remote buttons**: all of them arrive on scancode 240 with distinct keycodes: Settings 83
  (`KEYCODE_NOTIFICATION`), Netflix 4062, YouTube 190. Read from `logcat` (`TclKeyLayoutMap` and `InputReader`
  print every key at the default log level on this build). With launcherx off they do nothing until
  `HomeKeyService` maps them (`config.json`). The Input button keeps opening TCL's source panel either way.
- **The helper costs the volume ramp**: with `HomeKeyService` bound, holding volume moves one step per press
  instead of ramping, as in [the G09 note](../../docs/devices/tcl-55p71b-g09-key-repeat.md). The owner kept the
  buttons. To swap, clear `enabled_accessibility_services` and set `accessibility_enabled 0`.
- **The helper was not bound after a restart**: `[TclAppBoot]:forbid bind service ...HomeKeyService` with
  `callee_does't_have_OP_AUTO_START_permission`, then `com.tcl.tv`'s `TalkbackManager` set `accessibility_enabled`
  to 0. The `AUTO_START` op that `build-and-install.sh` grants had not reached the disk. Allowing it again with
  `appops write-settings` (as on the C89B, same build) and writing `accessibility_enabled 1` made it bind after
  two further restarts, the last one with USB debugging off.
- `wm size` reports an override of 1920x1080 on the 3840x2160 panel at density 320 straight from the factory. It
  is not a leftover; leave it.
