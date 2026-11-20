# Floating Clock

A minimalist floating `HH:mm.ss` clock screensaver over a subtle, slow-shifting ambient glow: the first five digits
and separators float gently in place with staggered offsets and soft tilt, drifting slowly upward only across a carry
(±1.6 s), while the last second digit flows continuously upward as a swaying vertical stream that fades at the edges.
The whole clock drifts imperceptibly around the screen for burn-in protection, drawn live on the TV with no network.

On cleanyourtv's Home it is also a module card, **Floating Clock**: press OK on it to start the clock at once, and
any key to return.

- Package: `io.github.shiruiliu.floatingclock`
- Author: [@shirui-liu](https://github.com/shirui-liu)
- Tested on: TCL Google TV (`TCL C02_2K_GLB`, Android 9)

## Permissions

None. No network access (no `INTERNET` permission): nothing leaves the TV. No background service: it runs only
while the screensaver or the card's screen is showing.

## Font licence

The bundled `Outfit` typeface (`app/src/main/res/font/outfit.ttf`) is copyright The Outfit Project Authors
([Outfit-Fonts](https://github.com/Outfitio/Outfit-Fonts)) and licensed under the SIL Open Font License 1.1; see
[`FONT-LICENSE-OFL.txt`](FONT-LICENSE-OFL.txt).

## Build and install

JDK 17 and Android SDK 34, as for the launcher. From the repo root:

```bash
cd addons/floating-clock && ./gradlew assembleRelease && cd ../..
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --push addons/floating-clock/app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
```

Press Home: the launcher asks once whether to show the card.

## Use it as the screensaver

Settings > Device Preferences > Screen saver > **Floating Clock** (the menu's name depends on the TV), or:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'settings put secure screensaver_components io.github.shiruiliu.floatingclock/.FloatingClockDream'
```

To see it without waiting, start the screensaver now:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'am start -n com.android.systemui/.Somnambulator'
```

## Remove

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm uninstall io.github.shiruiliu.floatingclock'
```

If it was the screensaver, pick another one in Settings.
