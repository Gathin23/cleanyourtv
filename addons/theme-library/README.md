# Theme Library

Keeps every theme you put on the TV and switches back to any of them with the remote, no computer needed. For the
[Clean Your TV](../../README.md) launcher it declares a [module card](../../docs/modules.md): once you answer
**Show card**, its tile reads "Theme Library" with a violet glow.

| Row | What it does |
|---|---|
| **On screen now** | At the top: the saved theme on screen, or the built-in look. |
| **Save the theme on screen** | Saves the current theme under a name you type. |
| **Get more themes** | Lists every theme in this repository's [`themes/`](../../themes/) folder on GitHub, with its description from `themes/README.md`. **OK** downloads one into the library and asks whether to switch to it now; a theme marked **In library** switches straight away. **Back** returns to the library. |
| **Saved themes** | Newest first, with picture count and date. **OK** switches to one; **hold OK** to rename or delete it. |
| **Built-in look** | Removes the theme: the launcher's own colours and layout. |

**Nothing gets lost.** Opening the app saves a theme it hasn't seen yet (named "Theme saved <date>", rename it
later), and so does every switch before it replaces the theme on screen. A theme pushed with `tools/theme.sh` is
picked up the next time you open the app. The same theme is never saved twice: themes are matched by content
(the config, the captions and each picture's name and size).

## How a switch works

The launcher reads its theme from `/sdcard/Android/data/com.gathin.tvhome/files` (`config.json`, `wallpaper.jpg`,
`wallpapers/`) when it starts. A switch copies the saved files there (the config last), writes a new
`theme_revision` so the launcher drops the on-TV choices the old theme set (as `theme.sh` does), ends the
launcher's background process and opens Home, which starts with the new theme. Saved themes live in this app's
own folder, `/sdcard/Android/data/io.github.rahulk847.themelibrary/files/library/<id>/`, each with `name.txt` and `saved.txt`.

## Permissions

| Permission | Why |
|---|---|
| Storage (`READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`) | To read the launcher's theme folder (save) and write it (switch). Granted by `install.sh`; otherwise the app asks. |
| `KILL_BACKGROUND_PROCESSES` | To restart Home after a switch. It only ends processes in the background. |
| `INTERNET` | **Get more themes** only, and only when you open it: GitHub's contents API for `Gathin23/cleanyourtv/themes` and `raw.githubusercontent.com` for that repository's `main` branch. |

What a download takes, and nothing else (`Store.java`): `config.json`, `wallpaper.jpg`, and in `wallpapers/` the
images and `captions.txt`. Screenshots, credits and scripts are skipped. File names must be plain (letters,
digits, `.`, `_`, `-`, no `..`), every file must come from that repository's raw address, one file may be at most
10 MB and one theme 40 MB, and each file's size must match what GitHub listed. A download that is already in the
library (same content) is not saved twice. The request carries a generic `User-Agent` and nothing about you or
the TV. GitHub allows 60 listing requests an hour per network without an account; each theme takes one or two.

No accounts, no telemetry, no libraries, no background service.

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher. From the repo root, with ADB on:

```bash
TV_HOST=<tv ip> addons/theme-library/install.sh
```

It builds the app, installs it, grants storage access and opens it. Press Home and answer **Show card**. Tests:
`cd addons/theme-library && ./gradlew testReleaseUnitTest`.

A theme pushed with `tools/theme.sh` lands in the library the next time the app opens. To add one from a computer
without putting it on screen, copy its folder into the library with a `name.txt` beside it, for example:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py "mkdir -p /sdcard/Android/data/io.github.rahulk847.themelibrary/files/library/aurora"
```

then push `config.json`, `wallpaper.jpg` and `wallpapers/` there with `tools/tv.py --push` or `tools/b64push.py`.

## Limits

- **Android 8 to 10 only.** Android 11 closes other apps' `Android/data` folders, so the app cannot reach the
  launcher's theme there; it says so on screen and `install.sh` refuses to install. The app targets SDK 28 for
  the same reason (Android 10 applies scoped storage to apps that target 29 and newer).
- **Downloads follow this repository's `main` branch.** A theme that needs a newer launcher than the one on the TV
  may use keys the launcher ignores; rebuild the launcher from an up-to-date checkout to get them.
- **Each saved theme is a full copy**, so a 10-photo theme takes about 3 MB of storage.
- **Launcher settings chosen on the TV** (columns, dock opacity, clock format) are reset by a switch, the same way a
  `theme.sh` push resets them.

## Tested on

Sony BRAVIA 2015 (SVP-DTV15), Android TV 8.0, firmware 6.827, with this repo's launcher: the app saved the aerial
theme on screen by itself on first open; four themes listed with the right picture counts; switching to a single
wallpaper theme and back to a 10-photo collection with captions, each with Home restarting on the new theme and the
"On screen" marker following; no duplicate saved on reopening. **Get more themes** listed the repository's 21
themes with their descriptions, marked those already saved as **In library** (matched by name for themes saved
before downloads existed), and downloaded Christmas (config and wallpaper only, the staging folder cleaned up).
The JVM tests (26) cover copying, clearing, fingerprints, the library order, renaming, deleting, a failed save
leaving nothing behind, the download rules (names, kept files, trusted addresses) and reading every description
out of this checkout's `themes/README.md`.

Not yet checked on the TV: the rename and delete dialogs, typing a name for **Save the theme on screen**, the
**Built-in look** row, focus returning to the chosen theme after a download, and the message when GitHub's hourly
limit is reached.

## Maintainer

[@RahulK847](https://github.com/RahulK847)
