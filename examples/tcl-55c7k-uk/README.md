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

## Compatibility report: TCL 55P8K, Ethernet

Confirmed on 29 October 2026: TCL 55P8K, device `G08`, model `Smart TV Pro`, MediaTek `tcl9618`,
Google TV 14 (SDK 34), firmware `UTT2.250416.001 AU02`, on its built-in Ethernet port. The reference
launcher installed successfully, took Home from `launcherx` after `setupwraith` was disabled, and
remained Home after a reboot and a final restart with debugging disabled. Its six-column dock and
D-pad navigation were checked from the dock into the app grid and up to the top bar.

Thirty packages remained disabled after reboot. Choices different from the first unit: `com.tcl.ttvs`
and `com.tcl.channelplus` were kept for firmware updates and TCL Live's TV input; Chromecast and
AirPlay were kept; Assistant, Miracast/T-Cast, Game Bar, the USB media apps, Play Movies, Play Games,
YouTube Music and Spotify were disabled. `dreamx` stayed enabled. AdGuard Private DNS validated and
YouTube opened with it active.

`dumpsys wifi` showed no saved-network owner, and Ethernet remained up when `FORCE_SETUPWRAITH=1`
disabled setupwraith. The real remote's Input button kept opening TCL's source list. Netflix, YouTube
and Settings shortcut buttons stopped working with `launcherx` off; the owner left the key helper
disabled because it costs held-volume repeat on this firmware, so holding Volume still ramps normally.
Network ADB port 5555 was confirmed closed both before and after the final restart. This is a
single-session compatibility report, not confirmation that the full list above was applied unchanged
or tested for several days.

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

## Compatibility report: TCL 55C61K, Portugal, Wi-Fi

TCL 55C61K, model "Smart TV Pro", device `G08`, product `G08_4K_GB`, Google TV 14 (SDK 34), firmware
`UTT2.250416.001 AU02`, on 5 GHz Wi-Fi. Set up on 29 October 2026 in one session: this list and the 65C6K list
as the starting point, applied in five batches with screenshots between them, then the launcher with
`build-and-install.sh`. Rebooted twice: all 38 packages stayed disabled, nothing re-enabled itself, and Home and
Wi-Fi came back. `tools/check.py` reported no drift afterwards. This is a single-session report, not several
days of use.

**Different choices from the UK unit:** every preloaded streaming app off (Netflix, Prime Video, YouTube, YouTube
Music and the partner apps the set ships with), Assistant (`katniss`), Miracast and T-Cast off; Chromecast,
AirPlay, the game bar, the USB media player and gallery, T-Solo and the soundbar updater kept. `com.tcl.ttvs`
disabled, as on the UK unit, and `com.tcl.dashboard` left enabled.

Notes from this unit:

- **Network ADB**: switching on USB debugging alone opened port 5555 on Wi-Fi, as on the C6K.
- **setupwraith owned the saved Wi-Fi network** (`cname=com.google.android.tungsten.setupwraith`). Forgetting
  the network and joining it again in Settings moved it to `com.android.tv.settings`; setupwraith was then
  disabled without `FORCE_SETUPWRAITH` and the TV stayed on Wi-Fi.
- **32-bit only**: `ro.product.cpu.abilist` is `armeabi-v7a,armeabi`, so sideloaded apps need their
  `armeabi-v7a` (or universal) APK; an `arm64-v8a` build does not install.
- **Settings button**: keycode 83 on scancode 561 here, read from `InputReader` (`TIDD:` lines at the default log
  level). With launcherx off it did nothing inside apps; `HomeKeyService` with `settings_key_keycodes: [83]` and
  `settings_key_panel: true` brought it back. The Netflix (4062) and YouTube (190) buttons were remapped with
  `app_keys` to other apps and worked on the real remote.
- **`com.tcl.dashboard/.MainActivity`** started (`topResumedActivity`) but showed nothing on screen, and its window
  blocks `screencap`.
- `wm size` shows the same factory override of 1920x1080, density 320.

## Compatibility report: TCL 50C71K, Netherlands, Ethernet

TCL 50C71K QD-Mini LED (2025), model "Smart TV Pro", device `G08`, MediaTek `tcl9618`, Google TV 14 (SDK 34),
firmware `UTT2.250416.001 AU02`, about 2.4 GB RAM, 32-bit only (`armeabi-v7a,armeabi`), on its built-in Ethernet
port with no saved Wi-Fi. Set up on 30 October 2026 in one session: this list as the starting point, applied in
six batches with screenshots between them, then the launcher with `build-and-install.sh` (built in Docker with
`tools/docker-build.sh`, then `SKIP_BUILD=1 HOME_KEY_SERVICE=1`). Rebooted once: every package stayed disabled,
nothing re-enabled itself, Home came back as the first resumed activity after `FallbackHome`, and Ethernet came
back. This is a single-session report, not several days of use.

**Different choices from the UK unit:** `com.tcl.ttvs` kept, for firmware updates. Netflix, YouTube, Chromecast
and Assistant (`katniss`) kept; Prime Video, AirPlay (all three packages) and the soundbar updater disabled.
Also disabled, as on the C755 and G08 US lists: `com.android.adservices.api`,
`com.android.federatedcompute.services` and `com.android.ondevicepersonalization.services`.

What was checked on the TV: Home and the Input button's source panel after each batch, the Inputs menu (HDMI 1-4
and Live TV), switching to HDMI 1 (`com.tcl.tv/.TVActivity` on top), D-pad from the dock into the grid, up to the
top bar and against both ends of it, the Clock screensaver setting, AdGuard Private DNS validating, and YouTube
playback with it on.

Notes from this unit:

- **setupwraith and Ethernet**: as on the 65P89L, `disable.sh` skipped setupwraith because `dumpsys wifi` printed
  no `cname=`. `cmd wifi list-networks` said "No networks" and the TV was on `eth0`, so `FORCE_SETUPWRAITH=1`
  disabled it and ADB stayed up.
- **Network ADB**: on this firmware, switching on USB debugging alone opens port 5555 (Wireless debugging stayed off). Switch USB debugging off again when you are done.
- **Remote buttons with `HomeKeyService`**: the Settings (83), Netflix (4062) and YouTube (190) keys from
  `config.json` worked on the real remote, and so did Input and the mic. The helper was bound after the reboot
  with no `TclAppBoot` refusal. The owner sets volume on an optical-connected sound system's own remote, so the
  held-volume trade-off did not apply.
- `wm size` shows the same factory override of 1920x1080, density 320.

## Compatibility report: TCL 55Q6C, Netherlands, Wi-Fi

TCL 55Q6C QD-Mini LED (2025), product `G08_4K_GB`, Google TV 14, `UTT2.250416.001 AU02`, on Wi-Fi. Set up on
27 October 2026 from the [G08 US list](../tcl-g08-us-google-tv/debloat.txt): 31 packages in four batches, all
still disabled after a reboot, then setupwraith and the launcher. Kept: `ttvs`, `interactive`, Chromecast,
AirPlay, the game bar and the main streaming apps. Off by choice: Assistant, `com.tcl.eva`, Miracast, Alexa,
MagiConnect, Disney+.

- **ADB** only over Wireless debugging (`tools/native-adb.md`); the port changes after every reboot.
- **setupwraith owned all four saved networks.** Re-joining the current one in Settings moved all four to
  `com.android.tv.settings`, and the TV stayed on Wi-Fi after setupwraith was disabled.
- **Remote** (scancode 240, launcherx off): Settings 83, Netflix 4062 and YouTube 190 do nothing; Prime Video
  4099 and the media player 4057 are opened by TCL itself; TCL Channel 4020 and Disney+ 4141 try their
  (disabled) apps. `app_keys` with `INPUT_KEY_SERVICE=1` brings back the first three.
- **`InputKeyService` alone costs the volume ramp**: held volume ramped with no helper, one step per press with
  it, on the real remote.
