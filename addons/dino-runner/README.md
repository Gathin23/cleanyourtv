# Dino Runner

The T-Rex game from Chrome's offline page as a screensaver. A bot plays it on its own, in silence,
jumping the cacti and ducking the birds while the speed climbs and night falls now and then. Pick
up the remote to play it yourself:

| Key | What it does |
|---|---|
| OK or Up | Jump (and take over from the bot, with the game's sounds on) |
| Down | Duck, or drop faster in the air |
| Left or Right | Hand the game back to the bot, which plays silently again |
| Volume | Changes the volume as usual |
| Back, Home or any other key | Leave the screensaver |

After 20 seconds without a key the bot takes over again on its own. The page is dark (the same
invert filter Chrome uses for its night mode), so it is not a white screen in a dark room.

On cleanyourtv's Home it is also a module card, **Dino Runner**: press OK on it to start the game at
once, with the same game keys, and Back to leave. The screen stays on while the card's game is open.

- Package: `io.github.oussama22.dinorunner`
- Author: [@ous-sama22](https://github.com/ous-sama22)
- Tested on: Skyworth SWTV-24AE-FHD (Google TV 14, MediaTek, 1 GB RAM)

## Permissions

None. No network access (no `INTERNET` permission): the game, its pictures and its sounds are in the
APK, and nothing leaves the TV. No background service: it runs only while the screensaver or the
card's screen is showing.

## Build and install

JDK 17 and Android SDK 34, as for the launcher. From the repo root:

```bash
cd addons/dino-runner && ./gradlew assembleRelease && cd ../..
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --push addons/dino-runner/app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
```

Press Home: the launcher asks once whether to show the card.

## Use it as the screensaver

Settings > Device Preferences > Screen saver > **Dino Runner** (the menu's name depends on the TV), or:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'settings put secure screensaver_components io.github.oussama22.dinorunner/.DinoDream'
```

It starts after the TV's screensaver delay, which on Android TV is the screen-off timeout. To make it
start after 5 minutes idle:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'settings put system screen_off_timeout 300000'
```

To see it without waiting, some TVs can start the screensaver now:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'am start -n com.android.systemui/.Somnambulator'
```

On the Skyworth it was tested on (Google TV 14) that activity does not exist and `cmd dreams start-dreaming`
needs root, so either wait for the delay or open the card, which runs the same game.

## How it works

The game is Chromium's own code, unchanged, in `app/src/main/assets/dino/` (see [CREDITS.md](CREDITS.md)).
`index.html` loads it in a WebView, and `bot.js` plays it: it reads the next obstacle from the game,
jumps when it is about twelve frames away, ducks under a bird at head height and lets a high one fly
over. `bot.js` also mutes the game while the bot plays. The Java code only shows the page and sends
it the remote's keys: `DinoGame.java` sets up the WebView and maps the keys, `DinoDream.java` is the
screensaver and `DinoActivity.java` is the card's screen.

It uses about 50 MB of memory while it runs (51 MB PSS on the 1 GB TV above), and nothing of it
keeps running once it closes.

To change the bot's timing, edit `reach` in `bot.js`; `MANUAL_MS` is how long you keep the game after
your last key, and `RESTART_MS` is the pause on the game-over screen before the bot starts again.

## Remove

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm uninstall io.github.oussama22.dinorunner'
```

If it was the screensaver, pick another one in Settings.
