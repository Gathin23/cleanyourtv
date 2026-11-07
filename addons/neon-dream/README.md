# Neon Dream

A screensaver of a neon city in the rain, or a synthwave sun over a glowing grid, with the clock drifting over
it. It is drawn on the TV at about 30 frames a second, with no pictures or fonts to download.

On cleanyourtv's Home it is also a module card, **Neon Dream**: press OK to open its screen, where you set

- **Scene**: Rain city or Neon grid;
- **Colours**: Magenta, Amber or Green;
- **Clock**: On or Off;
- **Preview**: the screensaver at once with these options, Back to leave;
- **Choose screensaver…**: opens the TV's own screensaver menu, where you pick Neon Dream.

Changes apply the next time the screensaver starts. This began as a launcher feature
([#2262](https://github.com/Gathin23/cleanyourtv/pull/2262)) and moved here so the launcher stays untouched.

- Package: `io.github.smartgenius2.neondream`
- Author: [@SmartGenius2](https://github.com/SmartGenius2)
- Tested on: Challenger R3 (Android TV 11)

## Permissions

None. No network access (no `INTERNET` permission): nothing leaves the TV. No background service: it runs only
while the screensaver or the preview is showing. The options are stored in the app's own preferences.

## Build and install

JDK 17 and Android SDK 34, as for the launcher. From the repo root:

```bash
cd addons/neon-dream && ./gradlew assembleRelease && cd ../..
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --push addons/neon-dream/app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
```

Press Home: the launcher asks once whether to show the card.

## Use it as the screensaver

Open the card and choose **Choose screensaver…**, then pick **Neon Dream** (the menu's name depends on the TV), or:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'settings put secure screensaver_components io.github.smartgenius2.neondream/.NeonDream'
```

To see it without waiting, start the screensaver now:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'am start -n com.android.systemui/.Somnambulator'
```

## Remove

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm uninstall io.github.smartgenius2.neondream'
```

If it was the screensaver, pick another one in Settings.
