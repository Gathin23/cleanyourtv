# Haunted

A Halloween screensaver for the TV: the night photos of the Halloween theme, slowly crossfading (14 s each), with a
few bats drifting across now and then, a ghost that fades in and floats up every half minute or so, a jack-o'-lantern
glow flickering in the bottom-right corner and a small clock. The photos are in the app (seven, 1280x720, about
1 MB together) and everything is drawn on one Canvas at about 20 frames a second, so it is light enough for a 1 GB TV.

On cleanyourtv's Home it is also a module card, **Haunted**: press OK on it to start it at once, and Back to leave.

- Package: `com.gathin.haunted`
- Author: CleanYourTV contributors
- Tested on: Xiaomi Mi TV 4A (MiTV-MOOQ1), Android 10, 29 Oct 2026: crossfade, bats, ghost, glow and clock all show; 3,021 frames, 0.4% janky, 99th percentile 5 ms; any key wakes it

## Permissions

None. No network access (no `INTERNET` permission): nothing leaves the TV. No background service: it runs only
while the screensaver or the card's screen is showing.

## Build and install

JDK 17 and Android SDK 34, as for the launcher. From the repo root, build, install and make it the screensaver
in one go:

```bash
TV_HOST=<tv ip> addons/haunted/build-and-install.sh
```

On Windows: `addons\haunted\build-and-install.ps1 -TvHost <tv ip>`. Add `START=1` (or `-Start`) to see it now. The
script keeps the screensaver the TV had before, in `/data/local/tmp/cyt-haunted-before`.

Or by hand, as for any add-on:

```bash
cd addons/haunted && ./gradlew assembleRelease && cd ../..
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --push addons/haunted/app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
```

then Settings > Device Preferences > Screen saver > **Haunted** (the menu's name depends on the TV).

## Undo

```bash
TV_HOST=<tv ip> addons/haunted/build-and-install.sh --uninstall
```

puts the previous screensaver back and removes the app. Without the script: pick another screensaver in Settings, then
`TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm uninstall com.gathin.haunted'`.

## Change the look

The constants at the top of `HauntedView.java`: `PHOTO_MS` and `FADE_MS` for how long a photo stays and how slowly it
crossfades, `FRAME_MS` for the frame rate, and the numbers in `drawBats` and `drawGhost` for how often they appear
(`nextBat`, `nextGhost`).

## Photo credits

The photos are the Halloween theme's wallpapers (`themes/halloween/`), cropped, colour-graded toward night and
darkened toward the edges (see the theme's [CREDITS.md](../../themes/halloween/CREDITS.md)), here scaled to 1280x720.
Each keeps its own licence, not the repository's MIT licence; the screensaver shows no credit text, so the credits
below are the attribution. Adaptations of the CC BY-SA pictures stay under CC BY-SA.

- "Jack-O-Lantern at night.jpg" by Alabama Extension, CC0 1.0
  https://commons.wikimedia.org/wiki/File:Jack-O-Lantern_at_night.jpg
- "Lit by the Moon SQF A1 -1619-Edit (52321356482).jpg" by the USDA Forest Service, Pacific Southwest Region, public
  domain (a work of the US federal government)
  https://commons.wikimedia.org/wiki/File:Lit_by_the_Moon_SQF_A1_-1619-Edit_(52321356482).jpg
- "Chorzów St Hedwig cemetery chapel in fog 2021.jpg" by Adrian Tync, CC BY-SA 4.0
  https://commons.wikimedia.org/wiki/File:Chorz%C3%B3w_St_Hedwig_cemetery_chapel_in_fog_2021.jpg
- "Jack-o-lantern 2.jpg" by Douglaspperkins, CC BY 4.0
  https://commons.wikimedia.org/wiki/File:Jack-o-lantern_2.jpg
- "Horizonte lunar.jpg" by Halleyuncometafotografo, CC BY-SA 4.0
  https://commons.wikimedia.org/wiki/File:Horizonte_lunar.jpg
- "Great Cemetery in the fog 03.jpg" by Андрей Романенко (Andrey Romanenko), CC BY-SA 4.0
  https://commons.wikimedia.org/wiki/File:Great_Cemetery_in_the_fog_03.jpg
- "Full moon over Gullmarn fjord at Holma Marina 2.jpg" by W.carter, CC BY-SA 4.0
  https://commons.wikimedia.org/wiki/File:Full_moon_over_Gullmarn_fjord_at_Holma_Marina_2.jpg

Licences: CC0 1.0 https://creativecommons.org/publicdomain/zero/1.0/ · CC BY 4.0
https://creativecommons.org/licenses/by/4.0/ · CC BY-SA 4.0 https://creativecommons.org/licenses/by-sa/4.0/

The bats, the ghost, the icon and the banner are drawn for this add-on.
