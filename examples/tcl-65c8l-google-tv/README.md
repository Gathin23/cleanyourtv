# TCL 65" C8L Google TV

Cleaned up on 24 October 2026, on a real unit in one session. Board **G17** (MediaTek tcl9655), product
`G17_4K_GB`, Android 14, firmware `UTT2.250416.001 AU08`. A newer board than the C7L (G08); this list was
checked package by package against the unit with `dumpsys`. Replace `<tv ip>` below with your TV's IP.

What changed:

- **27 apps disabled**, none uninstalled, listed with reasons in [debloat.txt](debloat.txt): TCL tracking
  (Samba TV ACR, BI analytics, logkit), TCL promos / FAST channels / recommendations and the message hub,
  the retail and hotel demo, the e-manual, repair guide and "cleaner", the partner-app auto-installers
  (gtvpai, partnersetup, smartlink.core), Google feedback, the T-Cast bridge, the soundbar updater, a
  preloaded "AstroTV" service, Google's promo screensaver (dreamx), the setup wizard (setupwraith) and the
  stock Google TV home (launcherx).
- **Google TV home replaced** by the clean launcher from `launcher/`: a dock of Netflix, ABC iview, Plex
  and Prime Video, the other apps in a 4-wide grid, and pills for Inputs, Wi-Fi and Settings. Its clock
  screensaver replaces Google's promo screensaver.
- **Remote buttons remapped** (see below), since the stock home answered them.

Kept by this owner: Google Assistant, Chromecast, AirPlay, Miracast, the game bar, the USB media player,
Play Games, YouTube Music, and every installed streaming app.

Checked afterwards: Live TV (coax tuner) starts, the Input button's source panel opens, the four HDMI
inputs are registered (one named "X Box" over CEC), YouTube streams, and the remapped remote buttons work.
All 27 packages stayed disabled after a reboot; nothing re-enabled.

Take a full backup (package lists, settings, `getprop`) into the gitignored `backup/` folder before any change.

## Connecting to the TV

This set offers only **Wireless debugging** (Settings > System > Developer options). There is no plain
port 5555 until you open it: pair with the on-screen code, connect, then switch to TCP:

```
adb pair <tv ip>:<pairing-port> <code>
adb connect <tv ip>:<connect-port>
adb tcpip 5555
adb connect <tv ip>:5555
```

Now `tools/*` can reach it with `TV_HOST=<tv ip>`. A reboot closes 5555; pair again. Each `tools/*.sh`
script has a PowerShell twin (`tools\disable.ps1`, `tools\restore.ps1`, `tools\theme.ps1`,
`launcher\build-and-install.ps1`) that takes `-TvHost <tv ip>`.

## Forget and re-join Wi-Fi first (Wi-Fi-only sets)

`dumpsys wifi` showed the saved network owned by setupwraith (`cname=...setupwraith`). On a Wi-Fi-only
TV, disabling setupwraith deletes that network and drops Wi-Fi and ADB. On the TV, **forget the network
and join it again** in Settings > Network & Internet; ownership moves to `com.android.tv.settings`, and
setupwraith can then be disabled. Its HOME components could not be disabled on their own (shell
`SecurityException`), so the whole package goes.

## Apply the list and install the launcher

1. Apply the list. `tools/disable.sh` skips setupwraith while it owns Wi-Fi and skips launcherx while it
   owns Home, so run it, do step 2, then re-run it to finish those two:

   ```
   TV_HOST=<tv ip> tools/disable.sh examples/tcl-65c8l-google-tv/debloat.txt
   ```

2. Switch the screensaver to the launcher's clock (dreamx is in the list):

   ```
   TV_HOST=<tv ip> tools/tv.py 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream'
   ```

3. Build the launcher and make it Home (JDK 17, Android SDK 34). This disables launcherx with its
   start-first ordering and verifies Home landed on the new launcher (it rolls back if setupwraith grabs
   it, which is why step 1's Wi-Fi/setupwraith handling comes first):

   ```
   TV_HOST=<tv ip> launcher/build-and-install.sh
   ```

4. Push this folder's [config.json](config.json) (dock, columns, Live TV app, remote-button mappings):

   ```
   TV_HOST=<tv ip> tools/theme.sh examples/tcl-65c8l-google-tv
   ```

5. Turn on the key helper so the remapped buttons work (see the trade-off below):

   ```
   TV_HOST=<tv ip> INPUT_KEY_SERVICE=1 RESTORE_KEY_SERVICES=1 launcher/build-and-install.sh
   TV_HOST=<tv ip> tools/theme.sh examples/tcl-65c8l-google-tv
   ```

## Remote buttons

With launcherx disabled, the Settings, TV-guide and app-shortcut buttons go dead. On this BT remote
(BT_RC833A_B2) they all share scancode 240 but send distinct Android keycodes, so they are mapped by
keycode in [config.json](config.json) `app_keys`:

| Button | keycode | opens |
|---|---|---|
| Settings | 83 | `com.android.tv.settings` |
| TV guide | 4001 | `com.tcl.tv` (Live TV) |
| Netflix | 4062 | `com.netflix.ninja` |
| Prime Video | 4099 | `com.amazon.amazonvideo.livingroom` |
| YouTube | 190 | `com.google.android.youtube.tv` |
| TCL | 4020 | *(unmapped)* |

**Trade-off:** `INPUT_KEY_SERVICE=1` runs a key-filtering accessibility service, and on this board that
stops continuous volume-repeat (holding Volume steps once per press). It is all-or-nothing: leave the
helper off to keep volume hold-to-ramp, at the cost of the remapped buttons. The remote's Home, Back,
D-pad, OK, Volume, Mute, Power and Input buttons work either way; the hardware Input button opens the
system source switcher without a helper.

## Re-enable one app

On the TV: **Settings > Apps > See all apps > Show system apps**, pick the app, choose **Enable**. Or:

```
TV_HOST=<tv ip> tools/tv.py 'pm enable com.tcl.waterfall.overseas'
```

Then put a `#` in front of its line in `debloat.txt`. Leave `launcherx` and `setupwraith` disabled while
the new launcher is Home; use the full undo below instead.

## Change the dock or wallpaper

Edit `dock_apps`/`columns` in [config.json](config.json) and push with the step 4 command (no rebuild).
For a wallpaper, put `wallpaper.jpg` (1920×1080) in this folder and add `"wallpaper": true`. On the TV,
hold OK on the Settings pill to reorder the dock. Every key is in `themes/README.md`.

Package names on this unit: Netflix `com.netflix.ninja`, ABC iview `au.net.abc.iview`, Plex
`com.plexapp.android`, Prime Video `com.amazon.amazonvideo.livingroom`, Disney+ `com.disney.disneyplus`,
Kayo `au.com.kayosports.tv`, 7plus `com.swm.live`, 9Now `au.com.ninenow.ctv`, 10 `au.com.tenplay`,
SBS `com.sbs.ondemand.tv`, Spotify `com.spotify.tv.android`, YouTube Music
`com.google.android.youtube.tvmusic`, Live TV `com.tcl.tv`.

Private DNS to an ad-blocking resolver works on this TV (it does not block YouTube ads).

## After a firmware update

```
TV_HOST=<tv ip> tools/venv/bin/python -I tools/check.py examples/tcl-65c8l-google-tv/debloat.txt com.gathin.tvhome
```

Add `--fix` after `check.py` to re-disable whatever came back and put the launcher back on Home.

## Undo everything

```
TV_HOST=<tv ip> tools/restore.sh examples/tcl-65c8l-google-tv/debloat.txt com.google.android.apps.tv.launcherx
TV_HOST=<tv ip> tools/tv.py 'settings delete secure screensaver_components'
```

The first line turns every disabled app back on and makes the Google TV home Home again. The second puts
Google's screensaver back. To remove the new launcher as well, run
`tools/tv.py 'pm uninstall com.gathin.tvhome'`.

## When you're done

Turn off Wireless/USB debugging and **Revoke USB debugging authorisations** in Developer options, restart
the TV, then check the port is closed ("Connection refused" means it worked):

```
tools/adb-closed.py <tv ip>
```
