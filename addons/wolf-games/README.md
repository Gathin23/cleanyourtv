# Wolf Games

A game shelf for the TV: the games you share from your PC through Moonlight appear as a row of
covers, OK launches a game, and with the optional Wolf session controller you can resume, close or
switch games without leaving the TV. Declares a module card ("Wolf Games", caption "Stream from
your PC") for the CleanYourTV launcher.

The visual follows the launcher's game shelf from the fork this add-on was extracted from: dark
screen, one row of 3:4 covers with rounded corners, focus zoom and a soft white outline, the game
name over a dark gradient on the focused cover, and a "PC: host" pill on top when the catalog lists
more than one host.

## What it does

- Reads a `catalog.json` plus cover files that you push to the add-on's own external directory and
  shows them as one row of covers.
- OK on a game launches it through Moonlight (`com.limelight/.ShortcutTrampoline`, extras
  `Name`/`UUID`/`AppName`, plus `AppId` when the launch went through the session menu).
- With a provisioned Wolf session controller, OK first asks the Wolf what is running: resume the
  game, close it, or close it and open another one. Every close is confirmed and revision-checked;
  nothing is ever stopped implicitly.
- If the Wolf reports a running game, its cover gets a soft outline and a play badge.
- Moonlight not installed: a clear message on screen, no crash.

## Requirements

- [Moonlight](https://moonlight-stream.org) installed on the TV (the catalog and the launches belong
  to it). The Wolf (https://github.com/mattpandey/wolf) is optional.
- The catalog lives in the add-on's own external files directory, in the same format the fork's
  shelf uses (`moonlight/catalog.json` plus `*.png`/`*.jpg` covers). You push it with adb; the add-on
  never goes online to fetch a catalog.

## Provisioning

Open the add-on once first, so it creates its `moonlight` folder (a folder made by adb is not readable by the app). Then push the catalog and covers; repeat after every change and reopen the add-on:

```bash
adb connect <tv ip>
adb push catalog.json /sdcard/Android/data/io.github.vitorcamillo.wolfgames/files/moonlight/catalog.json
adb push 123.png /sdcard/Android/data/io.github.vitorcamillo.wolfgames/files/moonlight/123.png
```

Optional Wolf session controller: push `wolf-control.json` (version 1, an `https://` URL on LAN port
47991, the Wolf's `host_uuid`, the token, and the server certificate as base64 DER — the same file
the fork's shelf reads) to the add-on's external directory:

```bash
adb push wolf-control.json /sdcard/Android/data/io.github.vitorcamillo.wolfgames/files/wolf-control.json
```

On the first open after the push the add-on validates the file, copies it into its private storage
and **deletes the external copy**, so the bearer token does not stay readable on shared storage. An
invalid file is discarded, not copied. Until you push a new one, the controller stays as it is; to
revoke it, uninstall the add-on (its private storage goes with it).

With the controller provisioned, the add-on also refreshes the catalog and covers from the Wolf
itself (same host, same port, same pinned certificate), so a push is only needed when the
controller is not provisioned.

## Permissions

- `INTERNET` — used only by the optional Wolf session controller: HTTPS to a single private IPv4
  address (10/8, 172.16/12 or 192.168/16), port 47991 only, with a pinned certificate and a bearer
  token, plus the catalog/cover refresh through the same connection. No other host is ever
  contacted. There is no telemetry, no ads, no analytics, no background service, no boot receiver,
  no polling: the screen reads data when it opens or resumes, and nothing runs behind it.

The launcher itself needs no permission from this add-on: it talks to the launcher only through the
module card.

## Build and install

From this folder (JDK 17, Android SDK 34):

```bash
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
adb connect <tv ip>
adb install -r app/build/outputs/apk/release/app-release.apk
```

Press Home and answer Show when the launcher asks about the "Wolf Games" card.

## Tested on

TCL Smart TV Pro, Google TV 14.
