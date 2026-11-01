# Startup & Branding

A welcome screen of your own for the TV: your picture (a logo, a family photo, a room name), a background colour
and a line of text such as "Welcome to the Den", full screen for a few seconds or until Back or OK. Pick the picture,
how it's scaled (Fit, Fill, Stretch or Original) and where it sits, see it in a live 16:9 preview beside the
settings, and preview it full screen. A Compatibility screen says plainly what this TV allows without root and what
it doesn't. For the [Clean Your TV](../../README.md) launcher it declares a [module card](../../docs/modules.md):
once you answer **Show card** its tile reads "Startup & Branding" with an amber glow. On any other Android TV home it
is an ordinary app.

![Startup & Branding on a TV: the Custom Image screen with its live preview](screenshot.jpg)

It does **not** change the maker's boot logo or the boot animation, and it does not open by itself after a
restart: see [Limits](#limits). It never pretends to be part of the TV's startup.

## What's on it

- **Welcome Screen**: Custom (picture, colour, text), Black (a plain black screen) or Off.
- **Preview**: the welcome screen full screen, exactly as it will look.
- **Custom Image**: the picture, Scaling, Position, a live preview and the full-screen preview. PNG, JPEG or WebP up
  to 15 MB; pictures are checked by their first bytes, and big ones are decoded only as large as the screen needs.
- **Welcome Text** (up to 60 characters, one line), **Text Position** (bottom, center, top), **Background Color**
  (six), **Display Duration** (3 to 30 s, or until Back or OK).
- **Device Name**: the TV's real name, as phones and casting see it, and OK opens the TV's own setting to change it.
  **Nickname** is a second line under the welcome text and lives in this module only; it never renames the TV.
- **Compatibility**: the TV's maker, model, Android version and screen, and each feature as Works, Not here,
  Needs launcher, Needs system or Not possible.
- **Restore Defaults**: turn the welcome screen off (keeping your settings), put every setting back, or delete the
  pictures. The last two take OK twice.

## Permissions

None. No internet: nothing leaves the TV. No storage permission: pictures live in the app's own folder
(`Android/data/io.github.maxhayim.startupbranding/files/pictures`). No overlay, accessibility, foreground service,
start at boot, accounts, telemetry, ads or libraries.

- Package visibility is limited to the file picker and the TV's device-name and device-info settings screens, to
  offer them only when they exist (many Android TVs have only a stub picker that does nothing).
- One content provider, guarded by `WRITE_SECURE_SETTINGS`, which the ADB shell has and ordinary apps don't:
  `push-image.sh` writes pictures through it so the app owns them. It writes and deletes picture names only.
- `.Welcome` is exported so a remote button or a launcher hook can open the welcome screen (below). It reads nothing
  from the intent and shows only while the welcome screen is on.

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher. From the repo root, with ADB on:

```bash
TV_HOST=<tv ip> addons/startup-branding/install.sh
TV_HOST=<tv ip> addons/startup-branding/push-image.sh logo.png family.jpg   # pictures from a computer
TV_HOST=<tv ip> addons/startup-branding/push-image.sh --delete logo.png
TV_HOST=<tv ip> addons/startup-branding/install.sh --uninstall              # app, settings and pictures gone
```

Then pick the picture on the TV in Custom Image. Where the TV has a real file picker, Choose a File copies a picture
in from there instead. To build only: `cd addons/startup-branding && ./gradlew assembleRelease`; the JVM tests run
with `./gradlew testReleaseUnitTest`.

## Limits

- **The maker's boot logo** is shown by the bootloader before Android starts. Only the maker's firmware changes it.
- **The boot animation** is a file on a read-only system partition. Replacing it needs root or a firmware change;
  this module never touches it, and Compatibility only says whether the file is there.
- **Opening by itself after a restart.** From Android 10, an app can't open a screen from the background, so a boot
  receiver can't show the welcome screen. The ways around that (drawing over other apps, accessibility, a foreground
  service) are left out on purpose. Today the welcome screen opens from Preview, or from anything that starts
  `.Welcome`.
- **Renaming the TV** needs `WRITE_SECURE_SETTINGS`, so this module opens the TV's own setting rather than setting
  the name itself.
- **Pictures.** Animated GIFs, SVG and HEIC are not supported, and pictures over 8192 px on a side are refused.
  Original size is relative to a 1080p screen.

## Opening after boot: a launcher hook (proposal)

The home app is in front after every boot, so it can do what an add-on can't. A possible opt-in for the launcher,
**not part of this add-on and off by default**:

- A launcher setting such as "Open after boot", off unless the owner turns it on, naming one activity (for this
  add-on, `io.github.maxhayim.startupbranding/.Welcome`).
- Once per boot, when Home first appears, the launcher starts it. If the welcome screen is off, `.Welcome` closes at
  once, so nothing shows.
- Nothing else changes: no permissions for the launcher, no change to Home's behaviour while the setting is off.

That needs the maintainer's say-so and its own pull request. Mapping a remote button to `.Welcome` through the
launcher's app keys works the same way, once those can target an activity.

## Tested on

MOKA M1G (Android TV 11, Realtek RTD2851F, 1920×1080) with the stock Google TV home, over ADB:

- `install.sh` from source, and `push-image.sh` sending a picture through the provider; it showed up in Custom Image
  and in the live preview.
- The full-screen preview, closed with OK; Preview still opens while the welcome screen is Off.
- `.Welcome` opened from outside, as a remote button or launcher hook would: it showed, closed by itself after 5 s and
  went back to the Google TV home. While Off, it closed at once and Home stayed in front.
- Compatibility read the TV correctly and found a real file picker on it.

Not tested yet: copying a picture in through Choose a File, Device Name opening the TV's own setting, and Restore
Defaults. The JVM tests cover the settings and their limits, scaling and placement, picture types, decode size, text
cleaning, file names and the compatibility answers.

## Maintainer

[@maxhayim](https://github.com/maxhayim)
