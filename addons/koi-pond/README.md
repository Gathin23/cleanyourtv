# Koi Pond

A calm koi pond seen from above, in pixel art, as the TV's screensaver and as a card on Home. Koi swim in pairs and
alone, their shadows sliding over the pond floor; small fry dart about in schools; lily pads, lotus flowers and
duckweed float at the edges, and light ripples across the floor.

- **Water that moves.** A real ripple simulation: rain drops, surfacing koi and pebbles send out rings that bend
  the light on the floor.
- **The time of day, from the TV's clock.** Dawn, day, golden hour, dusk and night, blended slowly into each
  other; fireflies blink over the pond after dark.
- **Rain now and then.** Every 20 to 40 minutes a shower of a few minutes: drops land on the water, the light
  dims a little and the fish go deeper.
- **A breathing guide** (on the card): press OK and a soft ring grows as you breathe in (4 s) and shrinks as you
  breathe out (6 s), while the koi gather around it. Each time the breath turns, a ring of ripples rolls out across
  the water, so you can follow it without reading the label. OK again stops it.

The pond is drawn at 480x270 and scaled up with sharp pixels, on one render thread at about 30 frames a second, so
it stays light on a 1 GB TV. The fish, pads, flowers, icon and banner are drawn in code for this add-on.

On cleanyourtv's Home it is a module card, **Koi Pond**:

| Key | What it does |
|---|---|
| OK | Start or stop the breathing guide |
| Arrows | Drop a pebble: a ripple, and the fish scatter |
| Back | Leave |

As a screensaver any key wakes the TV, as usual.

- Package: `io.github.nottoobright.koipond`
- Author: [@nottoobright](https://github.com/nottoobright)
- Tested on: TCL Smart TV Pro G08 (G08_4K_GB), Google TV 14, firmware UTT2.250416.001, 7 Nov 2026: card and screensaver, all five times of day, rain, pebbles and the breathing guide; about 10 ms a frame (99th percentile 12 ms, 16 ms in the rain) against a 33 ms budget; any key wakes the screensaver back to Home. `am start com.android.systemui/.Somnambulator` does not start a screensaver on this TV; a short `screen_off_timeout` does

## Permissions

None. No network access (no `INTERNET` permission): nothing leaves the TV. No background service: it runs only
while the screensaver or the card's screen is showing.

## Build and install

JDK 17 and Android SDK 34, as for the launcher. From the repo root, build, install and make it the screensaver in
one go:

```bash
TV_HOST=<tv ip> addons/koi-pond/build-and-install.sh
```

On Windows: `addons\koi-pond\build-and-install.ps1 -TvHost <tv ip>`. Add `START=1` (or `-Start`) to see it now.
No JDK? Build in Docker with `tools/docker-build.sh addons/koi-pond`, then install with `SKIP_BUILD=1` (or
`-SkipBuild`). The script keeps the screensaver the TV had before, in `/data/local/tmp/cyt-koipond-before`.

Or by hand, as for any add-on:

```bash
cd addons/koi-pond && ./gradlew assembleRelease && cd ../..
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --push addons/koi-pond/app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
```

then pick **Koi Pond** as the screensaver in Settings (Device Preferences > Screen saver, or System > Ambient mode on
Google TV; the menu's name depends on the TV). If the menu does not list it, the script above sets it with
`settings put secure screensaver_components` instead.

## Undo

```bash
TV_HOST=<tv ip> addons/koi-pond/build-and-install.sh --uninstall
```

puts the previous screensaver back and removes the app. Without the script: pick another screensaver in Settings,
then `TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm uninstall io.github.nottoobright.koipond'`.

## See a time of day or the rain now

The card's screen takes three extras, handy for a screenshot:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'am start -n io.github.nottoobright.koipond/.PondActivity --ei minute 1260 --ez rain true'
```

`minute` fixes the clock (minutes after midnight: 360 dawn, 720 day, 1050 golden hour, 1140 dusk, 1260 night),
`rain` starts a shower at once and `breathe` starts the breathing guide.

## Change the look

- Colours of each time of day: the five palettes in `TimeOfDay.java`, and when each begins in its anchors.
- How often it rains and for how long: the rain schedule in `PondModel.java`.
- The fish: varieties, sizes and speeds in `PondModel.java`; their colours and shapes in `PondRenderer.java`.
- The breathing rhythm: `IN_MS` and `OUT_MS` in `Breath.java`.
