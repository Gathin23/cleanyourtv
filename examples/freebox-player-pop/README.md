# Freebox Player POP

Free's Android TV 10 box (`fbx8am`, Amlogic, 2 GB RAM, build `QTT8.201201.002.v10.5.66`), debloated and
running this repo's launcher in place of OQEE. Everything is disabled, nothing is uninstalled.
`debloat.txt` lists each package and why it went.

All commands run from the repo root, with the Player's IP in `TV_HOST` (Paramètres > Réseau et
Internet shows it). ADB has to be on for any of them: Developer options >
USB debugging, then accept the prompt on the TV.

## Install, from scratch or again

```bash
TV_HOST=<player-ip> tools/disable.sh examples/freebox-player-pop/debloat.txt
TV_HOST=<player-ip> STOCK_LAUNCHER=net.oqee.launcher launcher/build-and-install.sh
```

`build-and-install.sh` builds the launcher, starts it, and only then disables OQEE and makes it the
Home screen. It needs JDK 17 and the Android SDK (`brew install openjdk@17` and
`brew install --cask android-commandlinetools`). On macOS, make the venv with a uv-managed Python: the
Command Line Tools Python is blocked by Local Network privacy ("No route to host"). See the header of
`debloat.txt`.

## Undo everything

```bash
TV_HOST=<player-ip> tools/restore.sh examples/freebox-player-pop/debloat.txt net.oqee.launcher
```

It re-enables the packages from the list and gives Home back to OQEE. It replays the state recorded before the first change (`~/.cleanyourtv/recovery-*.json`, so
keep that file).

## Re-enable one app

```bash
TV_HOST=<player-ip> tools/venv/bin/python -I tools/tv.py 'pm enable com.google.android.youtube.tv'
```

Then comment its line out in `debloat.txt`, so a later run of the list doesn't disable it again. To
disable another app: `pm disable-user --user 0 <package>`. Without a computer: Paramètres > Applications >
Afficher toutes les applications > the app > Activer.

## Change the dock or its order

Edit `dock_apps` in `launcher/app/src/main/res/values/config.xml` and run
`launcher/build-and-install.sh` again. OQEE, the Live TV app, is `net.oqee.androidtv`; set it as
`live_tv_package` too. No rebuild is needed if you use a theme instead: put
`"dock_apps": [...]` in a theme's `config.json` and push it with `tools/theme.sh`. You can also reorder
from the TV: hold OK on the Paramètres pill.

## Change the wallpaper

Copy a theme, drop a dark 1920×1080 `wallpaper.jpg` into it, and push it:

```bash
cp -r themes/aurora themes/mine     # then replace themes/mine/wallpaper.jpg
TV_HOST=<player-ip> tools/theme.sh themes/mine
TV_HOST=<player-ip> tools/theme.sh --reset   # back to plain black
```

`themes/README.md` lists every key a theme can set.

## Ad-blocking DNS (off, on purpose)

AdGuard's public DNS blocks `pixel.spotify.com`, which the Spotify TV app needs to start, and that broke
the Spotify login. Android's Private DNS takes one server for the whole box, with no per-app exceptions.
For ad blocking with Spotify, create a free personal profile at NextDNS or AdGuard DNS, allowlist
`pixel.spotify.com`, then:

```bash
TV_HOST=<player-ip> tools/venv/bin/python -I tools/tv.py 'settings put global private_dns_specifier <your-id>.dns.nextdns.io'
TV_HOST=<player-ip> tools/venv/bin/python -I tools/tv.py 'settings put global private_dns_mode hostname'
```

To undo: `settings put global private_dns_mode opportunistic`. It doesn't block YouTube ads. If Spotify
then shows "Vérifiez votre connexion réseau", switch DNS back and log in to Spotify again.

## When you're done: close ADB

Port 5555 is plaintext. On the TV: Developer options > Revoke USB debugging authorisations, then
turn USB debugging off. Then check from the Mac that the player refuses the port:

```bash
tools/venv/bin/python -I tools/adb-closed.py <player-ip>
```

It must say the port is closed (exit 0); only a refused connection proves that. A timeout or "No route to
host" proves nothing. Use the venv's Python here: Local Network privacy blocked the Command Line Tools
Python on this Mac (see `debloat.txt`).

Also: a Freebox system update that re-enables packages means running `tools/disable.sh` on the list
again.
