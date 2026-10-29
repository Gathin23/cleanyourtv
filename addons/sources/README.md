# Sources

The TV's sources by the names the devices give themselves: "PlayStation 5" and "Apple TV" instead of "HDMI 2" and
"HDMI 3". HDMI-CEC devices report their names to the TV, which lists each one as an input under its port; this
screen shows those names (or the name you gave a port in the TV's settings), connected ones first in full
brightness, and OK switches to one. Live TV comes last. For the [Clean Your TV](../../README.md) launcher it
declares a [module card](../../docs/modules.md): once you answer **Show card** its tile reads "Devices" with a
green glow. On any other Android TV home it is an ordinary app. (The launcher keeps the name of its own Inputs
menu for itself, in English and in the TV's language. That is "Sources" in French, so the card is called Devices.)
The screen is in English and French.

![Sources on a TV](screenshot.jpg)

A port with one device is one row under that device's name, with "HDMI 2 · connected" under it; a port with
several (a receiver and the console behind it) has a row for each; a port with nothing on it keeps its own name
and is dimmed.

## Permissions

None. It reads the TV's input list through `TvInputManager`, which needs no permission; package visibility is
limited to TV input services. No internet permission: nothing leaves the TV. No background service, accounts,
telemetry, ads or libraries. Nothing is stored.

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher. From the repo root, with ADB on:

```bash
TV_HOST=<tv ip> addons/sources/install.sh
```

To build only: `cd addons/sources && ./gradlew assembleRelease`; the JVM tests run with `./gradlew testReleaseUnitTest`.

## Limits

- **Device names need HDMI-CEC** on both the TV and the device (it has brand names: Anynet+, BRAVIA Sync, SimpLink,
  EasyLink…). Without it the row keeps the port's name.
- **Switching** opens the input through the TV's own input app, as the launcher's Inputs menu does; a TV without
  one says so.
- **"Connected"** is what the TV reports for each port. Some TVs report a port as connected (or connected, in
  standby) whenever anything is plugged in, whether or not it is on; the MOKA R8G reports its AV input as
  connected with nothing in it.
- **One card.** The card spec allows one card per app, so the devices are rows inside, not cards of their own.

## Tested on

MOKA R8G (Android 14), on a home app that implements the module-card spec: the card prompt and the card, three HDMI ports the TV reports as connected in standby, AV and Live TV. No HDMI-CEC device was attached, so device names are covered only by the JVM tests (ports, devices behind a port, the owner's own names, standby).

## Maintainer

[@maxhayim](https://github.com/maxhayim)
