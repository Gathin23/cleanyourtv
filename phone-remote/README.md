# TV Remote (phone app)

An Android phone app that turns the phone into a **Bluetooth keyboard and media remote** for an Android TV. The
TV needs no app and no network: it sees the phone as an ordinary Bluetooth keyboard.

| Control | What the TV receives |
|---|---|
| Arrow pad and **OK** | Arrow keys and Enter, which move and select on any Android TV screen |
| **Back**, **Home** | The consumer-control keys AC Back and AC Home |
| **Vol +**, **Vol −**, **Mute**, **Play/Pause** | The consumer-control volume and media keys |
| **Type text** | The characters as key presses, for search boxes and passwords (US layout: letters, digits and common punctuation) |

Holding a key down holds it on the TV, which repeats it by itself, so holding an arrow scrolls.

The screen is in English and Hindi.

## How it works

Android 9 and later let an app register as a **Bluetooth HID device** (`BluetoothHidDevice`). The app registers
one keyboard report and one consumer-control report (`HidKeys.DESCRIPTOR`), then sends 8-byte keyboard and 2-byte
consumer reports to the connected TV. `HidKeys` holds every report byte and has no Android dependency, so it is
unit-tested on the JVM.

## Use it

1. Install the APK on the phone (Android 9 or newer) and open it. On Android 12 or newer allow the Bluetooth
   permission.
2. Tap **Make phone visible**.
3. On the TV: Settings, Remote and accessories, **Add accessory**. Choose the phone and confirm the code on both
   screens.
4. Back in the app, tap the TV under **Paired devices**. The status line turns green: "Connected to ...".
5. Use the pad.

## Permissions

| Permission | Why |
|---|---|
| `BLUETOOTH_CONNECT` (Android 12+) | To connect to the paired TV. Asked once. |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` (Android 11 and older, `maxSdkVersion` 30) | The same, on older phones. |

No internet permission, accounts, telemetry, ads or libraries.

## Limits

- **Some phones do not let apps use the Bluetooth keyboard profile**, or ship it half-working, depending on the
  maker and the Android build. The app says so when the profile is missing; nothing else can fix that.
- **A phone can be a keyboard for one host at a time.** Connecting to a second TV disconnects the first.
- **Typing is US layout only.** Characters outside it, such as Hindi letters, are skipped with a message.
- **Power is not sent**: many TVs cannot be woken from standby by a Bluetooth keyboard, so use the TV's own
  remote to switch it on.
- The app stops when you close it; the connection ends with it.

## Build and test

JDK 17 and Android SDK platform 34. In this folder: `./gradlew testReleaseUnitTest assembleRelease`. The release
build is signed with the debug key so it installs from a file; sign your own builds with your own key if you
hand them out.

## Tested on

Built and unit-tested on GitHub's runner. Not yet tried with a real phone and TV pair: that is the next step.
