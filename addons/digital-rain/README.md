# Digital Rain

A screensaver in the style of the film's digital rain: columns of mirrored half-width katakana and digits fall down
a black screen, each with a bright head and a green trail that fades behind it, while a few glyphs in the trails
change as they fall. It is drawn on the TV at about 20 frames a second, with no pictures or fonts to download.

On cleanyourtv's Home it is also a module card, **Digital Rain**: press OK on it to start the rain at once, and
Back to leave.

- Package: `io.github.dennisoleary.digitalrain`
- Author: [@dennis-oleary](https://github.com/dennis-oleary)
- Tested on: NVIDIA SHIELD Android TV Pro 2019 (Android TV 11)

## Permissions

None. No network access (no `INTERNET` permission): nothing leaves the TV. No background service: it runs only
while the screensaver or the card's screen is showing.

## Build and install

JDK 17 and Android SDK 34, as for the launcher. From the repo root:

```bash
cd addons/digital-rain && ./gradlew assembleRelease && cd ../..
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --push addons/digital-rain/app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
```

Press Home: the launcher asks once whether to show the card.

## Use it as the screensaver

Settings > Device Preferences > Screen saver > **Digital Rain** (the menu's name depends on the TV), or:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'settings put secure screensaver_components io.github.dennisoleary.digitalrain/.RainDream'
```

To see it without waiting, start the screensaver now:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'am start -n com.android.systemui/.Somnambulator'
```

## Change the look

The constants at the top of `RainView.java`: `FRAME_NS` for the speed, `DROPS_PER_COLUMN` for how dense the rain
is, `TRAIL` and `HEAD` for the colours. The glyph size is the `12` in `onSizeChanged` (in dp), and the fade colour
in the constructor sets how long the trails stay.

## Remove

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm uninstall io.github.dennisoleary.digitalrain'
```

If it was the screensaver, pick another one in Settings.
