# TCL C7K (G08, Google TV, Android 14), UK and EU firmware

TCL C7K QD-Mini LED, device `G08`, model "Smart TV Pro", firmware `UTT2.250416.001 AU02`, MediaTek, about
2.4 GB RAM. Three units have run this list: a 55C7K-UK on Wi-Fi (the first, see the header of `debloat.txt`), a
50C7K on Ethernet in Poland and a C6K on Ethernet in Brazil (both below). Both halves work on this set: the
debloat list and the Clean Your TV launcher as Home.

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

## Third unit: C6K, Brazil, Ethernet

TCL C6K, model "Smart TV Pro", device `G08`, product `G08_4K_GB`, same `UTT2.250416.001 AU02`, pt-BR market, on
the built-in Ethernet port. Set up on 24 October 2026 in five batches with screenshots between them, then the
launcher; rebooted twice. After the first reboot every package on the list was still disabled and Home, Ethernet
and Private DNS came back. One day of use, not weeks.

**Different choices from the UK unit:** `com.tcl.ttvs` kept, for firmware updates, at the price of the Samba TV
tracking it carries. Kept on this unit: Netflix, Prime Video, YouTube Music, Assistant (`katniss`), Chromecast,
AirPlay, the game bar, the USB media player and gallery, and dreamx. Off by choice besides the UK ones: the Google
TV movie app (`com.google.android.videos`). `com.tcl.channelplus` (TCL Live) was disabled and then re-enabled: it
registers as a TV input (`com.tcl.iptv.service.InputService`) and has no launcher entry, so its channels play only
through Live TV. `tv.mopa.ginga` was left alone, as on the 65C7K list from Brazil.

What was checked on the TV: Home after each batch, the Inputs menu (HDMI 1-4 and Live TV), switching to HDMI 1
(`com.tcl.tv/com.tcl.player.TVActivity` on top), TCL Live in Live TV after re-enabling it, D-pad from every edge,
and a Home Assistant Android TV Remote key still reaching the TV.

Notes from this unit:

- **Network ADB without Wireless debugging**: with the TV on Ethernet, the Wireless debugging toggle flipped
  straight back off, and switching on USB debugging alone opened port 5555. `ro.adb.secure` is 1, so the
  authorisation prompt still appears.
- **setupwraith owned no network** here (no Wi-Fi was ever saved), so `disable.sh` had nothing to skip. With
  launcherx off, Home resolved to setupwraith's `RecoveryActivity` until setupwraith went too.
- **Remote buttons without the helper**: with launcherx off, Settings, Input, the GloboPlay button and the mic kept
  working; Netflix, YouTube, Prime, TCL and Disney did nothing. The owner kept the volume ramp and enabled no key
  helper; held volume ramps normally that way.
- `wm size` shows the same factory override of 1920x1080, density 320.

## Compatibility report: TCL 65P8L-UK, Wi-Fi

Confirmed on 28 October 2026: TCL 65P8L-UK, device `G08`, product `G08_4K_GB`, Google TV 14
(SDK 34), firmware `UTT2.250416.001 AU02`. The reference launcher installed successfully and remained
Home after reboot, including a final restart with debugging disabled. The owner confirmed streaming
playback, Google Cast, AirPlay, Miracast, the Google TV phone remote, Assistant, Live TV and HDMI worked.

The saved Wi-Fi belonged to setupwraith; forgetting and rejoining it through Settings transferred
ownership before the launcher handover. `InputKeyService` restored Netflix (4062), Prime Video (4099),
YouTube (190) and Settings (83), at the cost of held-volume repeat; the owner accepted that tradeoff.
Network ADB was confirmed closed after the final restart. This is a single-session compatibility
report, not confirmation that the full debloat list above was applied or tested for several days.

## Fourth unit: 65P89L, EU, Ethernet

TCL 65P89L, product `G08_4K_GB`, MediaTek `tcl9618` / `mt5896`, SoC MT9689, same `UTT2.250416.001 AU02`
(software `V8-T653T01-LF1V641`, client type `TCL-EU-MT9653-S1`), about 2.4 GB RAM, on Ethernet. Set up on
27 October 2026 with this list as the starting point, then the launcher with `build-and-install.sh`, and rebooted
three times: Home, Ethernet and the Clock screensaver came back, the Input button opened TCL's source panel, and
logcat showed no `FATAL EXCEPTION`.

**Different choices from the UK unit:** Google Assistant kept (`katniss` commented out). No `HomeKeyService`.

Notes from this unit:

- **setupwraith and Ethernet**: `disable.sh` skipped setupwraith with "could not check which app owns the saved
  Wi-Fi networks": `dumpsys wifi` printed no `cname=` on this build. `cmd wifi list-networks` said "No networks"
  and the TV was on `eth0`, so `FORCE_SETUPWRAITH=1` disabled it and ADB stayed up.
- **dreamx came back after a reboot**: twice the TV was rebooted within seconds of `pm disable-user` on
  `com.google.android.apps.tv.dreamx`, and both times it booted enabled (`enabled=0`) and ran from
  `BackdropBootReceiver`. Disabled again, waited two minutes, then rebooted: it stayed off. The setting had
  not been written to disk yet. Nothing on the TV re-enables it.
- **logkit's boot service**: `GeorgedebugLogkitInputEvent` (`/system_ext/etc/init/hw/logkit.rc`, runs
  `/system_ext/bin/LogkitInputEvent` as root) runs at every boot with `com.tcl.logkit` disabled. It watches
  `/dev/input` for the key sequence that opens `com.tcl.logkit/.MainActivity`. `logkit.sh` has no network
  code (it copies to USB or `/data/tcl_log`), `persist.sys.normallog`, `persist.sys.usblog` and
  `persist.sys.perfettolog` are unset, and `ro.feature.user_logkit` is `false`.
