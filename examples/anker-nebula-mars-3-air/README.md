# Anker Nebula Mars 3 Air (Google TV 14): debloated, with an Apple TV style home

Model D2325 (firmware name `Nebula_Mars_2_V2`, device `taihang`, ANKER board, MediaTek m7332),
Google TV 14 / Android 14, SDK 34, build `UKNV.260210.001`. Done over network ADB on 2026-10-22
and 2026-10-23, with a screenshot after every step, one reboot in between and the real remote.

All commands below run from the repository root. Replace `<projector ip>` with the projector's
address (Settings > Network on the projector).

## What was changed

| | |
|---|---|
| **Disabled** (15 packages, never uninstalled) | Google feedback and consent, Privacy Sandbox ad services, federated learning, on-device personalisation, partner attribution, both retail-demo apps, the AirPlay receiver, Spotify, Max, the Google TV movies app, the setup wizard, the stock Google TV home and its partner rows. The full list with reasons is in [`debloat.txt`](debloat.txt) |
| **Back after every boot** | The firmware turns `com.google.android.katniss` (Assistant) back on at each boot. The owner keeps it on, so the mic button works; `tools/check.py --fix` re-disables it if you prefer |
| **Kept** | Chromecast built-in, Prime Video, YouTube + YouTube Music, Play Games, Nebula Play, Nebula Life, Netflix (see quirks), Google's Ambient Mode screensaver, and everything the projector needs: focus / keystone / battery settings, remote, Bluetooth pairing, HDMI inputs, USB media player, Dolby audio, keyboard, Play Store, firmware-update UI |
| **Screensaver** | Google Ambient Mode: disabled at first with the screensaver switched to the built-in "Colors", then re-enabled on request and made the active screensaver again |
| **Home** | `com.gathin.tvhome`, configured by [`config.json`](config.json): six-column dock (YouTube, Twitch, Netflix, Prime Video, Vodafone TV, VLC), the app library and the gear menu |

## The quirks that cost the most time

1. **Google TV 14 gives Home to the setup wizard.** `cmd package set-home-activity` prints
   `Success`, but while setupwraith is enabled Home still resolves to
   `setupwraith/.RecoveryActivity` and the new launcher never comes up (the same behaviour as
   [xiaomi-tv-a-pro-2026](../xiaomi-tv-a-pro-2026/)). The whole setup-wizard package has to go;
   it is only used for first-time setup. Before disabling it we checked what it owns: on this unit
   it held one old hidden Wi-Fi network that was no longer connected — the network in use belongs
   to `android.uid.system` and kept working after the disable. Re-enable it (restore.sh does)
   before a factory reset. Phone casting kept working here too. On other Google TV units the Cast receiver
   loses its credentials with setupwraith gone and phone casting fails with "Untrusted device"
   ([docs/TROUBLESHOOTING.md](../../docs/TROUBLESHOOTING.md)) — re-enable setupwraith if your
   phone refuses to cast.
2. **The firmware re-enables Assistant at every boot.** `com.google.android.katniss` comes back
   enabled after each reboot, and its LED service with it (the same behaviour as the
   [D2426 Capsule](../anker-nebula-d2426/) list). The owner keeps it on so the mic button works.
3. **The remote's gear button cannot be revived.** It sends Linux KEY_MENU, the vendor keylayout
   maps it to KEYCODE_NOTIFICATION (83), and Android's GlobalKeyManager consumes it for the stock
   launcherx before any app or accessibility service can see it (`dumpsys window policy`,
   `mKeyMapping`). The mapping comes from the static overlay
   `com.mediatek.android.tv.globalkeyoverlay` (`Nebula_GlobalKeyHandlerOverlay_Taihang.apk`), and
   static overlays ignore package state: `cmd overlay disable` throws a SecurityException, and even
   `pm disable-user` plus a reboot leaves it applied. Leave the overlay alone; Settings is the gear
   pill in the launcher's top bar instead (`top_menu` in [config.json](config.json)).
4. **The Netflix button is Netflix-only.** Its key is consumed by the same global-key layer
   (`com.mediatek.tv.service`'s NetflixKeyReceiver) and never reaches an app, so no remap is
   possible without root. Netflix stays ENABLED so the button works — it sits in the launcher's
   dock as a tile. Disabling Netflix kills the button; `pm enable --user 0 com.netflix.ninja`
   brings it back.
5. **Keep the launcher's key services off** (`key_filter` false, no `INPUT_KEY_SERVICE` /
   `HOME_KEY_SERVICE` build flags): on this firmware any bound key-filtering service turns ramping
   volume into one step per press, and the services cannot see the remote's special buttons anyway
   (quirks 3 and 4).

## Undo everything

```bash
TV_HOST=<projector ip> tools/restore.sh examples/anker-nebula-mars-3-air/debloat.txt com.google.android.apps.tv.launcherx
```

That re-enables every package disabled here and hands Home back to the stock Google TV home.
ADB has to be on again first — see [ADB](#adb).

## Re-enable one app

On the projector: Settings > Apps > See all apps > show system apps > pick the app > Enable.

Or over ADB:

```bash
tools/venv/bin/python tools/tv.py 'pm enable --user 0 com.spotify.music'
```

To keep it enabled the next time the list is applied, put a `#` in front of its line in
[`debloat.txt`](debloat.txt).

## The dock

[`config.json`](config.json) pins the dock (six apps, six columns) and turns on the app library
and the gear menu (`top_menu`). Push it with:

```bash
tools/theme.sh examples/anker-nebula-mars-3-air
```

Vodafone TV is an app this owner installed; a dock app that is not installed is simply
skipped, so trim `dock_apps` to your own apps. Pushing `config.json` also clears layout choices
made on the projector in Launcher settings.

## Notes from this projector

- The Inputs menu lists HDMI 1 and HDMI 2, opening in Google's passthrough player. Screenshots of
  HDMI come back empty, which is the projector, not a fault.
- No tuner, so `live_tv_package` stays empty.
- All remote buttons (Home, Back, mic, app buttons, focus) were tested on the real remote after the
  stock home was disabled. The gear button is the one exception — quirk 3.

## ADB

ADB was left on at the end of the setup, by choice. Port 5555 is plaintext, so close it when you no
longer need it: Settings > Device Preferences > Developer options > revoke USB debugging
authorisations, switch off Wireless debugging and USB debugging, restart, then check from the repo:

```bash
tools/venv/bin/python tools/adb-closed.py <projector ip>
```

Only "Connection refused" (exit 0) proves the port is closed. To use any command above later, turn
USB debugging (and Wireless debugging) back on, accept the "Allow USB debugging?" prompt, and if
port 5555 is closed, pair as described in the main README.
