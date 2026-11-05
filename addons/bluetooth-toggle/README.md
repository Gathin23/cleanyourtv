# Bluetooth Toggle

A one-press Bluetooth switch for the TV. For the [Clean Your TV](../../README.md) launcher it declares a
[module card](../../docs/modules.md), so once you answer **Show card** its tile reads "Bluetooth Toggle" with a
blue glow; on any other Android TV home it is an ordinary app.

> **For Android 12 and older.** Android 13 and newer do not let apps switch Bluetooth. There this add-on can only
> ask Android to turn it on and send you to Settings to turn it off, which the TV's own Bluetooth settings already
> do, so it adds nothing on those sets. The version is under About in the TV's settings; check it before
> installing.

![The panel after switching Bluetooth off](screenshot.jpg)

- **Open it and Bluetooth switches.** A small glass panel at the top right shows the result ("Bluetooth off"),
  a button to switch back, and **Pair accessory**, which opens the TV's own pairing screen. It closes itself
  after 5 seconds without a key press, or with Back.
- **The tile cannot show the state.** A module card's label and caption are fixed text, so the tile always reads
  "Bluetooth Toggle"; the panel says which way it went.
- **Not a device switcher.** [Bluetooth Connect](../bluetooth-connect/) connects or disconnects a paired speaker
  and [Headphones](../headphones/) manages where the sound goes; this one switches the Bluetooth radio itself.
  They can sit side by side.
- **Bluetooth remotes.** Many TV remotes pair over Bluetooth. Switch it off only if yours also works over
  infrared, or you lose the remote until Bluetooth is back on (from the TV's own buttons, a phone app, or
  `adb shell svc bluetooth enable`). The Hisense 75U75N remote falls back to infrared (its key presses then
  come from the TV's IR receiver); its voice button was not tried with Bluetooth off. Headphones, speakers and
  game controllers disconnect while it is off.

## Options

Set in `app/src/main/res/values/options.xml` before building.

| Option | Default | What it does |
|---|---|---|
| `switch_on_open` | `true` | Opening the add-on switches Bluetooth at once. `false` opens the panel on the current state, and **Turn on** / **Turn off** switches it. |
| `close_after_seconds` | `5` | Seconds without a key press before the panel closes; `0` keeps it open until Back. |

## Permissions

| Permission | Why |
|---|---|
| `BLUETOOTH_CONNECT` | Android 12 and newer: switching Bluetooth and opening the pairing screen. Asked for once, with Android's own prompt, the first time it switches. |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` | Android 8 to 11 only (`maxSdkVersion` 30): the same, granted at install. |

No network, no account, no telemetry, no ads, no background service: the receiver for Bluetooth's state is
registered only while the panel is on screen.

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher:

```bash
cd addons/bluetooth-toggle && ./gradlew assembleRelease
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py --push app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
```

Press Home: the launcher asks once whether to show the card. Change the answer later by holding OK on
Settings, then Module cards.

## Limits

- **Android 13 and newer** do not let apps switch Bluetooth (`BluetoothAdapter.enable` and `disable` return
  false). There, turning it on goes through Android's own "turn on Bluetooth?" prompt, and turning it off is
  left to Settings: the panel says so. That is why the add-on is meant for Android 12 and older.
- Pair accessory opens `com.google.android.intent.action.CONNECT_INPUT` (Google TV and Android TV), else
  Android's Bluetooth settings, else Settings.

## Tested on

Hisense 75U75N (`hengshan`, MediaTek), Google TV 12 / Android 12, firmware `STT2.230310.002`: switching off and
on from the tile, the permission prompt, Pair accessory, and the remote over infrared with Bluetooth off.

## Maintainer

[@justin-newman](https://github.com/justin-newman)
