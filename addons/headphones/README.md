# Headphones

Bluetooth headphones on the TV, made easy to keep quiet: one screen that shows where the sound goes now, your
paired headphones with their battery, a shortcut to pairing, and a **quiet guard** that pauses and mutes the
speakers when headphones drop, so a flat battery doesn't send a film's sound into a room where someone is
asleep. For the [Clean Your TV](../../README.md) launcher it declares a [module card](../../docs/modules.md):
once you answer **Show card** its tile reads "Headphones" with a blue glow. On any other Android TV home it is an
ordinary app.

![Headphones on a TV with none paired yet](screenshot.jpg)

Opening it shows:

| Row | What it does |
|---|---|
| **Sound now plays through** | At the top: the headphones' own name, the TV speakers, or the TV's audio output (ARC or optical). |
| **Pair or connect headphones** | Opens the TV's Bluetooth settings. Apps can't pair or connect headphones themselves on Android; the TV's settings do that. |
| **Quiet when they drop** | On by default. When headphones or a Bluetooth speaker disconnect, it sends a pause to whatever is playing and mutes the TV speakers. When they connect again it lifts its own mute (never one you set yourself). |
| **Unmute the speakers** | Shown only while the guard's mute is on, to lift it by hand. |
| **Paired headphones and speakers** | Each pair, connected or not, with its battery where the headphones report it. OK opens the Bluetooth settings to connect it. |
| **Several headphones at once** | What this TV can do for two listeners or more; see below. |

## Several headphones at once

Android TV sends sound to one pair of classic Bluetooth headphones at a time; an app can't change that. The
screen says what the TV in front of you supports:

- **LE Audio broadcast (Auracast)**, on Android 13+ TVs whose Bluetooth chip has it: any number of Auracast
  headphones can listen at once, started from the TV's Bluetooth settings.
- Otherwise, a **dual-link Bluetooth transmitter** on the TV's headphone or optical output feeds two pairs, and
  some headphones can share their sound with a second pair of the same brand.

## Permissions

| Permission | Why |
|---|---|
| Nearby devices (`BLUETOOTH_CONNECT`, Android 12+) | To list paired headphones and whether they're connected, and to receive the connect and disconnect broadcasts the quiet guard acts on. Granted by `install.sh`; otherwise the app asks once. |
| Bluetooth (`BLUETOOTH`, Android 8 to 11 only) | The same on older versions, granted at install. |

No scanning and no location: it reads only devices already paired in the TV's settings. No internet permission:
nothing leaves the TV. No accounts, telemetry, ads or libraries. No background service: Android starts the
quiet guard only for a connect or disconnect broadcast. Muting and the pause key need no permission.

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher. From the repo root, with ADB on:

```bash
TV_HOST=<tv ip> addons/headphones/install.sh
```

It builds the add-on, installs it, grants Bluetooth access (and TCL's auto-start), and opens it. Press Home: the
launcher asks once whether to show the card. Change the answer later by holding OK on Settings, then Module
cards. To build only: `cd addons/headphones && ./gradlew assembleRelease`; the JVM tests run with
`./gradlew testReleaseUnitTest`.

The release build is signed with the debug key so it installs over ADB like the launcher. Sign your own builds
with your own key if you hand them out; the launcher pins its Show answer to the signing key.

## Limits

- **A moment of sound can still escape.** Android reroutes to the speakers as headphones drop, and players
  that follow Android's rules pause on their own at that moment. The guard's pause and mute follow the
  disconnect broadcast, a fraction of a second later. Keep the TV's own volume low while headphones are in use
  for the surest quiet.
- **Fixed volume.** When the TV's sound goes through HDMI ARC or an amplifier, Android reports a fixed volume
  and an app can't mute it; the guard then only pauses, and the screen says so.
- **The pause goes to the app playing media.** Apps without a media session (some live TV and HDMI inputs)
  ignore it; the mute still applies.
- **Battery** comes from a call that isn't public Android API. Many TVs answer it; where one doesn't, the
  battery isn't shown.
- **Pairing screens differ.** On TVs with no Bluetooth settings screen of their own, the shortcut opens the main
  Settings instead.
- **TCL** blocks broadcasts to apps it hasn't allowed to auto-start; `install.sh` allows it.

## Tested on

MOKA R8G (Android 14), over ADB, with its own home app and no headphones paired: Bluetooth access granted by
ADB, the screen naming the TV speakers as the output, the guard row, the empty paired list, and the sharing line
for an Android 14 TV without LE Audio broadcast.

The JVM tests cover the guard's decisions (audio devices only, pause only on fixed volume, unmute only its own
mute), the battery and sharing wording, and name cleaning.

Still to check, with a pair of Bluetooth headphones and on a TV running this repo's launcher (the test TV's home
app has no module cards):

1. The card prompt and **Show card**; the card opens the app.
2. Pairing through the shortcut, then the pair listed as Connected, with its battery where reported.
3. "Sound now plays through" names the headphones, then the speakers after disconnecting.
4. With a video playing, switch the headphones off: it pauses and the speakers are muted. Switch them on: the
   mute is lifted. With the guard off, neither happens.
5. A remote or keyboard disconnecting never pauses anything.
6. On a TV with ARC or fixed volume, the screen says so and the guard only pauses.

## Maintainer

[@maxhayim](https://github.com/maxhayim)
