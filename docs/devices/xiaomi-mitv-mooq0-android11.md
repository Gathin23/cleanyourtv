# Xiaomi MiTV-MOOQ0 (croods): P1 43-inch EU firmware

Android TV 11, build `RTM4.220307.164.8835`, board `m7632`, 1.7 GiB RAM. The existing
[MOOQ0 list](../../examples/xiaomi-mitv-mooq0/debloat.txt) targets Android TV 10. We applied a
selected subset on this Android 11 build and the TV remained usable, including after a reboot. This
is not a claim that every list entry was tested: optional apps were kept by choice and some package
names in the list are not installed on this firmware.

## Launcher and TV functions

The owner uses Projectivy as the launcher. The MediaTek TV input and core TV services, Bluetooth remote
pairing, and hotkey service were kept enabled. The owner also kept Miracast, Mi Cast, and the USB
media players available; playback and mirroring were not separately re-tested on this firmware.

## Netflix

Disabling Netflix for user 0 did not survive a reboot on this build: the firmware turns it back on, so leave its line commented out.

`NetflixObserver` is not a separate installed package here: its receiver and launcher components
are inside `/system/priv-app/TvServices/TvServices.apk`, package `com.mediatek.tv.service`. The
Netflix key receiver handles global keycode 265. The boot receiver updates its key count; inspection
of this APK did not find code that re-enables Netflix. Keep `com.mediatek.tv.service` enabled because
it also provides core TV and remote functions.

## Build `8313` with the Russian retail remote

A second P1 43" unit, Android TV 11, build `RTM4.220307.164.8313` (`release-keys`), `ro.config.low_ram=true`,
1.7 GB RAM. The stock Home was `com.google.android.tvlauncher`; `com.mitv.tvhome.atv` (PatchWall) was
launchable but did not answer Home. `com.duokan.factorytest` is not installed on this build.

What was done: 29 packages disabled in total, of which 16 came from the
[MOOQ0 list](../../examples/xiaomi-mitv-mooq0/debloat.txt) in two batches with a screenshot after each
(telemetry and promo, then background services, preloaded apps, Miracast and Mi Cast) and 4 more once the
launcher was Home (PatchWall, `michannel`, the Google ambient screensaver, `tvrecommendations`). Eight more
were already disabled when the work began, among them Netflix and Prime Video, and the stock launcher was
disabled by `launcher/build-and-install.sh` after the new one was confirmed as Home. The owner kept
Chromecast, firmware updates, the USB media players, Mi TV+ and the voice assistant. After one reboot
`pm list packages -d` was identical (29 before, 29 after) and Home still resolved to the new launcher.
Netflix had been disabled by the owner before the list was applied and stayed disabled across that one
reboot, unlike the `8835` unit above.

### The remote has no Input button

The Russian retail remote has Power, the voice assistant, a ring with OK, a PatchWall button (four
squares), Back, Home, four app buttons (Netflix, Okko, ivi, KinoPoisk) and volume. There is no Input or
Source button, so `input_key_keycodes` 130 and `handle_view_inputs` from the section above have nothing
to map on this remote. `getevent -lt` reported five different keys for the five buttons below the ring,
in the order they were pressed: `KEY_WWW`, `KEY_VIDEOPHONE`, `KEY_COFFEE`, `KEY_LOGOFF`,
`KEY_CONTROLPANEL`. Which key belongs to which button is read from that order and was not checked one
button at a time. With PatchWall disabled none of these buttons does anything.

### A key service costs the held volume key

With `INPUT_KEY_SERVICE=1` the launcher's `InputKeyService` was enabled and holding volume up did not
repeat: one step per press. With the service off, holding volume up and down repeated normally. Checked
by hand with the real remote, in that order, on this one unit. It is the same trade-off as on the TCL
G09 (`docs/devices/tcl-55p71b-g09-key-repeat.md`), so leave the service off here unless you would rather
have the app buttons mapped than the held volume key.

### A third 8313 unit (2026-11): the optional set and the reference launcher

Same build `RTM4.220307.164.8313`, Russian retail, one session. 32 packages disabled in all: 28 entries of
the [MOOQ0 list](../../examples/xiaomi-mitv-mooq0/debloat.txt) in five batches with a screenshot after
each, `com.xiaomi.amazon.alexa`, Netflix and Prime Video (those three were already off when the session
began), and the stock launcher through the `launcher/build-and-install` script after the reference launcher was
Home. Every one of them was still disabled after a reboot and a hard power-off. By the owner's choice the
assistant (`com.google.android.katniss`) and Chromecast (`com.google.android.apps.mediashell`) went off on
top of the mirroring/media set the 5135 unit took (header of the list); the firmware updater, the USB
media players and the owner's streaming apps stayed on. Netflix stayed off across the reboot and the hard
power-off on this build, like the first 8313 unit and unlike the `8835` one.

Home went to `launcher/` itself, not Projectivy: the install script disabled the stock launcher only
after `dumpsys activity activities` showed it as the focused app, and it was still Home after the reboot
and the hard power-off. The Inputs pill lists HDMI 1-3, AV and Live TV. The screensaver was pointed at
`com.gathin.tvhome/.ClockDream` before `com.mitv.dream` went off, the order the #81 note in the list
header describes, and the dream stayed off. The dock holds the owner's five apps at five columns, set in the launcher's own
config.xml for this TV, so no dock becomes a default for every MOOQ0 owner.

Remote, checked by hand with the real buttons: the Okko, ivi and KinoPoisk app buttons work; the Netflix
button does nothing with Netflix off; the PatchWall button and the mic do nothing with PatchWall and
katniss off; held volume still repeats with no key helper enabled - the other side of the trade above.
After power-on the TV lands on the last source rather than Home (#81); pressing Home brings the launcher
back.

## Channel Up/Down lands two channels on: HbbTV (Spanish unit, build `8835`)

A Spanish retail MOOQ0 on `RTM4.220307.164.8835`, one session (2026-11), Bluetooth remote. One press of
Channel Up on the real remote showed the next channel's info banner, with no picture, and about 1.5 s later
the TV moved on to the channel after it. Channel Down did the same. The owner first noticed it after the
reference launcher was installed, but it was not checked before, and the launcher was not the cause: Live TV
is in front while zapping, and the launcher never sees the key.

What `logcat` showed for one real press (`com.mediatek.wwtv.tvcenter`, activity `com.mitv.livetv.tv.MainActivity`):

1. `ACTION_DOWN` of `KEYCODE_CHANNEL_UP` (`scanCode=402`, `flags=0x8`): Live TV's `TuneManager` tunes one
   channel on (`tuneToChannel: tune to channel <id>`).
2. `ACTION_UP`: `key is handled in hbbtv`, then `sending native key not handled msg`.
3. About 1.5 s later the tuner service reports a retune it started itself,
   `MtkTvInput(ChannelSelStatusThread)... doNotifyChannelRetuned,channelUri = content://android.media.tv/channel/<next id>`,
   and Live TV follows it (`retune request received and sync is finished`): the second step.

An injected `input keyevent KEYCODE_CHANNEL_UP` (`scanCode=0`) logged the same first two steps but no retune,
and moved exactly one channel. So test with the real remote: an injected key does not reproduce this.

These did not change it, tried in this order: turning the launcher's `InputKeyService` off
(`enabled_accessibility_services` empty, `accessibility_enabled 0`), two full channel scans, and re-enabling
`com.mitv.tvhome.mitvplus` (Mi TV+ adds a TV input of its own). Live TV also logs
`mCurrentNode does not match mCurrentChannel, syncing` on a press, but it logs that for the injected key
too, which stepped one channel only, so that line is not the cause.

The fix: switch HbbTV off in Live TV's own settings menu. Afterwards Channel Up and Down moved one channel
at a time on the real remote, and Mi TV+ went back off with no change. The cost is the broadcasters' HbbTV
("red button") services. The broadcast channels, the guide and subtitles are not affected.

Two more observations from the same unit:

- The remote's Input button opened the input list with the launcher as Home and `InputKeyService` off
  (`handle_view_inputs` true in `config.xml`), so this unit kept the held volume key and the Input button
  without a key helper.
- After a scan, the channels came out in the order the tuner found them (Cuatro on 1), with country set to
  Spain (`persist.sys.country` decodes to `ESP`). Spanish terrestrial broadcasts rarely carry LCN, so the
  list has to be sorted by hand in Live TV's channel editing, and a rescan does not fix the order.
