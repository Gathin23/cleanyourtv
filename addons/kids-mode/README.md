# Kids Mode

A kids' screen locked with a PIN: big tiles for the apps you choose (YouTube Kids, Scholastic, PBS KIDS and similar
start there), and nothing else. Home, the remote's Settings key and any app the kids' apps link to all lead back
to it, until a grown-up gives the PIN (or a password). For the [Clean Your TV](../../README.md) launcher it
declares a [module card](../../docs/modules.md): once you answer **Show card** its tile reads "Kids Mode" with a
yellow glow and opens the parents' screen. On any other Android TV home it is an ordinary app.

![The kids' screen](screenshot.jpg)

## How it works

- **Kids Mode is the TV's home app.** While it is off, Home hands straight over to your normal launcher (the one
  that was home before), so Home looks as it always did. While it is on, Home is the kids' screen.
- **A guard** (an accessibility service) watches which app comes to the front. While Kids Mode is on, any app
  that isn't on the kids' screen is sent back to it: Settings from the remote's key, the full YouTube from a link,
  another launcher. The on-screen keyboard and the system's volume bar are let through. The guard reads only
  which app is in front: never what is on screen, never keys.
- **Back does nothing** on the kids' screen. **Grown-ups** asks for the PIN; with it, Kids Mode switches off and
  Home is your launcher again.

## The parents' screen

The tile or card opens it. The first time it asks for a new PIN (4 to 8 digits, typed twice); after that it asks
for the PIN every time it opens.

| Row | What it does |
|---|---|
| **Start Kids Mode** | Switches it on and shows the kids' screen. |
| **Kids' apps** | Every TV app on the TV; OK puts one on the kids' screen or takes it off. YouTube Kids and apps from children's publishers start on it. The full YouTube does not. |
| **Change the PIN**, **Use a password instead** | A PIN is typed on a number pad worked with the D-pad (number keys work too); a password, with the TV's keyboard. |
| **Normal home app** | What Home shows while Kids Mode is off; OK picks the next one. |
| **Guard**, **Kids Mode is the home app** | Whether both are set up, with shortcuts to the settings. |

The PIN is never stored, only a salted PBKDF2 hash of it. After five wrong tries each further try waits, 30
seconds and doubling up to 5 minutes.

## Permissions

| Permission | Why |
|---|---|
| Accessibility service (`GuardService`) | The guard: which app's window is in front, while Kids Mode is on. `canRetrieveWindowContent` is off and it filters no keys. Switched on by `install.sh`. |
| Home (`CATEGORY_HOME`) | To be Home, and hand over to your launcher while off. Set by `install.sh`. |

Package visibility is limited to apps with a TV launcher entry and home apps. No internet permission: nothing
leaves the TV. No accounts, telemetry, ads or libraries. Settings stay in the app's own storage (backups are off).

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher. From the repo root, with ADB on:

```bash
TV_HOST=<tv ip> addons/kids-mode/install.sh
```

It builds the add-on, installs it, makes it the home app, switches on the guard next to any accessibility
services already on, and opens the parents' screen to choose a PIN. To undo:

```bash
TV_HOST=<tv ip> addons/kids-mode/install.sh --off com.gathin.tvhome/.MainActivity   # your launcher's home activity
```

To build only: `cd addons/kids-mode && ./gradlew assembleRelease`; the JVM tests run with
`./gradlew testReleaseUnitTest`.

## Limits

- **It is a guard, not a vault.** An app that isn't on the kids' screen can show for a moment before it is sent
  back. HDMI inputs reached with the remote's Input key are system windows on some TVs and may not be guarded.
  It suits small children, not a determined teenager with a computer and ADB.
- **Apps on the kids' screen are trusted.** Whatever an allowed app shows inside itself is up to that app, which
  is why the full YouTube doesn't start on it.
- **Clearing the app's data** (Settings, Apps) also switches the guard off and removes the PIN; Settings itself is
  guarded while Kids Mode is on.
- **A home app chooser.** Some TVs have no screen to choose the home app; `install.sh` sets it over ADB.

## Tested on

MOKA R8G (Android 14) with the Scholastic app, on a home app that implements the module-card spec: a new PIN
typed twice on the number pad, the parents' screen finding Scholastic and leaving the full YouTube and Disney+
off, Start Kids Mode, the kids' screen, Scholastic opening from it, and the guard sending Settings (from an intent
and from the remote's Settings key), Netflix and Home back to the kids' screen. Grown-ups with the PIN switched
Kids Mode off and handed Home to the normal launcher, and Home went there afterwards too. YouTube Kids wasn't
installed on the test TV. The JVM tests cover the PIN rules and hash, the wait after wrong tries, which apps start
on the kids' screen and what the guard lets through.

## Maintainer

[@maxhayim](https://github.com/maxhayim)
