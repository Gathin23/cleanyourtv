# TV Inspector

Optional standalone Android TV module card, maintained by [@shaharukhs](https://github.com/shaharukhs).
It answers the two questions a TV list or a bug report keeps asking: what does this remote actually send,
and what can this TV play?

- **Remote keys.** Press any button and see its key name, key code, scan code and the input device it came
  from, with the last eight keys listed. The D-pad is reported instead of moving the focus. Press Back twice
  to leave; volume keys still change the volume. A key injected with `input keyevent` shows as a virtual
  device, so an injected key can't be mistaken for the real remote. Home and Power never reach apps, and a
  button that an accessibility helper (such as the launcher's own) takes first shows nothing here: that is
  itself the answer.
- **TV info.** Manufacturer and model, device, board and SoC, Android version, firmware build, security
  patch and RAM; the current display mode, every supported resolution with its refresh rates, and the HDR
  formats the screen reports; hardware or software decoders for AV1, VP9, HEVC, H.264, Dolby Vision and
  MPEG-2 with the largest size each takes; the audio outputs (speakers, HDMI ARC/eARC, optical, Bluetooth)
  with the formats they accept, and the Dolby and DTS decoders present; and the remotes and other input
  devices connected. Move through the sections with the D-pad.

Every line is also written to logcat under the tag `TvInspector`, so you can copy it to a `tv.json`, a
`docs/devices/` note or an issue without typing it off the screen:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'logcat -d -s TvInspector'
```

## Build and install

JDK 17 and Android SDK 34, from this folder: `./gradlew testReleaseUnitTest lintRelease assembleRelease`.
The small gradlew scripts reuse the repository's pinned Gradle wrapper with this folder as the project, so
keep the folder inside the repository when building. Install it as `addons/README.md` describes, or with
`TV_HOST=<tv ip> tools/venv/bin/python -I tools/manage.py install tv-inspector`. No binaries or prebuilt APKs are committed.

The launcher discovers the static module card and asks once whether to show it. Module spec 1 has static
captions, so the card always reads "Remote keys and TV info"; OK opens the app.

## Permissions

None. No network, no telemetry, no ads, no background service and nothing runs after you leave the screen.
It only reads what Android gives any app: `Build` fields, the display modes, `MediaCodecList`, the audio
output devices and the input device list. It does not read the serial number, Android ID, MAC address,
Wi-Fi or accounts. Audio outputs are listed by type, not by name, because a Bluetooth speaker's name is
whatever its owner called it. Input device names are shown as the device reports them: blur one before
posting a screenshot if it carries a person's name.

## Validation

Tested on a **Sansui TV (Skyworth eleonas, MediaTek MT9676) running Android TV 14**, firmware
UKN5.251016.083: the key tester with the real BLE remote (D-pad and OK reported with their scan codes and
device), the TV info screen (4K at 60 Hz, Dolby Vision, HDR10 and HLG, hardware AV1, Dolby Atmos over the
speakers) and the logcat lines.

7 JVM tests, lintRelease (0 errors) and the release build pass. The release APK is about 18 KB.
