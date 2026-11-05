# Photo screensaver

A system screensaver (Android's "Daydream") for any Android TV or Google TV that shows your own pictures
and MP4 clips, separate from the launcher. Pictures go in filename order, each slowly zooming and drifting
and crossfading into the next after 20 seconds (or what you set). A clip plays once, muted and cropped to
fill the screen, then the loop moves on; a lone clip loops. Any key wakes the TV back to where it was.

Package `com.gathin.photodream`. You see it as **Photos** (Dutch: **Foto's**) in the TV's screensaver
settings. On cleanyourtv's Home it is also a module card, **Photos**: OK starts the same slideshow at once,
holding OK (or Menu) opens the settings, Back leaves.

## Options

All off by default, which is the plain full-screen slideshow above. Switch them on in the screensaver's own
settings (TV settings > screensaver > Photos > settings), or by holding OK (or pressing Menu) in the card's
slideshow. Google TV has no settings entry for other screensavers, so there it is the slideshow only:

| Option | What it does |
|---|---|
| Time and date | A line along the bottom with the time and the date, moved a little every minute against burn-in |
| When a picture was taken | Month and year, from a name ending in the date (`trip-20231202.jpg`) or else the camera's EXIF date |
| Three prints | Three pictures at a time as tilted prints, the whole picture, never cropped; clips are skipped |
| Sky | Behind the prints, a sky that follows the time of day |
| Weather | Temperature and a symbol, from the TV's rough location and Open-Meteo, every 30 minutes |

The weather needs location access (asked when you switch it on) and the TV's location services on; the
settings say when they are off and open the TV's location settings. The prints, the sky and the date on a
picture come from Photo Prints by [@shivamsingh262](https://github.com/shivamsingh262) (#936).

## Install

JDK 17 and Android SDK 34, as for the launcher, and the `tools/venv` from the main README. The small
`gradlew` scripts reuse the launcher's pinned Gradle wrapper with this folder as the project, so no wrapper
JAR is copied into the add-on; keep this folder inside the repository when building.

```sh
TV_HOST=192.168.1.50 addons/photo-screensaver/build-and-install.sh
```

```powershell
powershell -ExecutionPolicy Bypass -File addons\photo-screensaver\build-and-install.ps1 -TvHost 192.168.1.50
```

## Your pictures

Create a `pictures/` folder here (it is gitignored, so your pictures stay yours), put pictures (JPG, PNG,
WebP, up to 10 MB, 4K at most) and MP4 clips (H.264 or HEVC, up to 1 GB) in it, named with letters, digits,
`-`, `_` and `.` only, at most 100 files, and run:

```sh
TV_HOST=192.168.1.50 addons/photo-screensaver/sync.sh                          # or pass another folder
TV_HOST=192.168.1.50 SECONDS_PER_PICTURE=30 IDLE_MINUTES=5 START=1 addons/photo-screensaver/sync.sh
TV_HOST=192.168.1.50 addons/photo-screensaver/sync.sh --off                    # previous screensaver back
```

```powershell
addons\photo-screensaver\sync.ps1 -TvHost 192.168.1.50 [-Seconds 30] [-IdleMinutes 5] [-Start]
addons\photo-screensaver\sync.ps1 -TvHost 192.168.1.50 -Off
```

It makes Photos the TV's screensaver and syncs the folder by md5: only new or changed files are sent, and
files you removed are removed from the TV. A sync that broke off (standby, Wi-Fi drop) is finished by
running it again. Files go through the shell as base64, about 200 KB/s, so keep clips short: a minute of
1080p at 8 Mbit/s is about 60 MB, roughly five minutes to send. The screensaver the TV had before the
first run is remembered for `--off`; `build-and-install.sh --uninstall` does `--off` and removes the app.

`IDLE_MINUTES` sets `screen_off_timeout`, after which the TV starts its screensaver. You can also pick
Photos yourself in the TV's screensaver settings.

## Memory

At most two screen-sized pictures are held, as GPU bitmaps, and one hardware video player. A 4K picture
is decoded at screen size. On a TCL 43P69K (1.5 GB) a 1080p clip played with the screensaver at about
42 MB and 705 MB RAM still free.

## Permissions

- **Network: `INTERNET` and `ACCESS_COARSE_LOCATION`**, used only by the weather option, which is off by
  default. With it on, the TV's rough location, rounded to two decimals (about 1 km), goes to
  `api.open-meteo.com` every 30 minutes while the screensaver runs, and the temperature and weather code
  come back, with whether it is day. Nothing else goes out; with the weather off, the app makes no network connection at all.
- No storage permission and no background service: the pictures stay on the TV in the app's own folder
  (`Android/data/com.gathin.photodream/files/pictures`), removed with the app.
- **`SlideshowActivity`** (exported) is the module card; **`SettingsActivity`** (exported) is opened by the
  TV's screensaver settings. Both only show the slideshow or change its options.
- **`FilesProvider`** (exported) is how `sync.sh` writes the pictures, so the app owns them: on TCL G10 a
  file the adb shell copies into `Android/data` is unreadable for the app. Only callers holding
  `WRITE_SECURE_SETTINGS` may use it, which the adb shell has and ordinary apps cannot get, and only
  picture and clip names and `seconds.txt` are accepted.
- **`PhotoDream`** is the screensaver service, bound by the system only (`BIND_DREAM_SERVICE`).
- **TCL only:** `build-and-install.sh` allows `APP_AUTO_START` with `appops`, because TCL's TclAppBoot
  blocks the provider without it. Elsewhere that op doesn't exist and the line does nothing.

## Tested on

TCL 43P69K (G10, Google TV 12, Realtek), Netherlands.
