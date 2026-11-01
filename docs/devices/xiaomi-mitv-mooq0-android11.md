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
