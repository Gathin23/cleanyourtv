# Photo Prints

Your own photos as a screensaver: three prints on a time-of-day sky, each the whole photo (never cropped) with
rounded corners and the month and year it was taken. Every 15 seconds they slide off to the left one after
another and three new ones are dealt in from the right, each at a slight tilt. A line along the bottom shows the
time and the date, and moves a little at each sweep so it does not burn in.

On cleanyourtv's Home it is also a module card, **Photo Prints**: press OK on it to start the same slideshow at
once, and Back to leave.

- Package: `io.github.shivamsingh262.photoprints`
- Author: [@shivamsingh262](https://github.com/shivamsingh262)
- Tested on: Sony BRAVIA KD-65X82L (Google TV 12)

## Permissions

None. The app reads photos from its own folder on the TV, which needs no permission, and has no network access
(no `INTERNET` permission): nothing leaves the TV. No background service: it runs only while the screensaver or
the slideshow is on screen.

## Build and install

JDK 17 and Android SDK 34, as for the launcher. From the repo root:

```bash
cd addons/photo-prints && ./gradlew assembleRelease && cd ../..
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --push addons/photo-prints/app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
```

Press Home: the launcher asks once whether to show the card.

## Add your photos

The photos live in the app's own folder on the TV:
`/sdcard/Android/data/io.github.shivamsingh262.photoprints/files/photos`. On a Mac, `photos.sh` fills it from a
folder, resizing each photo to 1280 px first (HEIC included) and writing the date it was taken into the name:

```bash
TV_HOST=<tv ip> addons/photo-prints/photos.sh ~/Pictures/TV
TV_HOST=<tv ip> addons/photo-prints/photos.sh --clear     # remove them all
```

Any `jpg`, `jpeg`, `png` or `webp` copied into that folder by other means works too. A name ending in the date,
`something-20231202.jpg`, gives the print its date; otherwise the camera's own date is used when the photo has
one, and the print shows no date when it has neither. Photos added while the TV is on show up at the next sweep.
With no photos, the screen shows the folder to copy them into.

`photos.sh` and `adb push` were tested on the Sony above, where the app reads files the shell copies into its
folder. Some firmware does not allow that (#921 found files copied this way unreadable on a TCL G10); there, the
prints stay empty and the screen keeps showing the folder.

## Use it as the screensaver

Settings > System > Ambient mode or Screen saver > **Photo Prints** (the menu's name depends on the TV), or:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'settings put secure screensaver_components io.github.shivamsingh262.photoprints/.PrintsDream'
```

## Remove

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm uninstall io.github.shivamsingh262.photoprints'
```

This removes the photos folder with it. If it was the screensaver, pick another one in Settings.
