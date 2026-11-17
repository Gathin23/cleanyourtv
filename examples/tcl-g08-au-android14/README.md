# TCL Smart TV Pro (G08, Android 14), Australian firmware

TCL `G08` set, model "Smart TV Pro", product `G08_4K_GB`, firmware `UTT2.250416.001 AU02`, Android 14 with the
Google TV home (`launcherx`), 32-bit userspace. It is the same model as [`tcl-g08-au-android12`](../tcl-g08-au-android12/)
after TCL's Android 14 update, which brings Android's ad and personalisation services, TCL's Alexa client, a TCL
account, and Google's voice assistant and Cast.

| File | What it is |
|---|---|
| `debloat.txt` | The 26 packages to disable (MagiConnect is a comment), grouped and commented |

**How much testing is behind it:** one set, from 30 October 2026, lived with for two weeks with nothing broken
and nothing re-enabled. The list was applied with `tools/disable.sh` in four batches with a screenshot after each,
then the launcher with `launcher/build-and-install.sh`. Checked afterwards: the remote's Input key panel, HDMI 1
and HDMI 2, the app and Settings buttons with the real remote, the streaming apps, and Private DNS on AdGuard.
Not checked: Live TV (no aerial), Chromecast, AirPlay and Miracast sessions.

```sh
TV_HOST=<tv ip> tools/disable.sh examples/tcl-g08-au-android14/debloat.txt
TV_HOST=<tv ip> launcher/build-and-install.sh
```

## Notes

- **Wi-Fi and the setup wizard.** On this set the saved network belonged to `setupwraith`
  (`tools/tv.py "dumpsys wifi" | grep -o 'cname=[^ ]*'`). It was forgotten and joined again from Settings before
  the wizard was disabled; `disable.sh` skips the wizard by itself while it owns the network.
- **Remote buttons.** Unlike the Android 12 firmware, the app buttons and the Settings button kept working with
  `launcherx` off, and the Input key still opens TCL's own panel, so no key helper (`HOME_KEY_SERVICE`) is needed
  and holding volume keeps ramping.
- **Game bar.** `com.tcl.gamebar` was disabled with the extras and turned back on for a console on HDMI 2; it is
  commented out in the list.
- **Live TV.** With no aerial, TCL Live and TCL Channel are the only live channels, so both were kept.
- **SmartTube** needs the 32-bit (`armeabi-v7a`) build. The F-Droid build (`app.smarttube.fdroid`) installs and
  plays; install a large APK with the SDK's `adb install`, as `tools/tv.py --push` is meant for small files.
- Live TV and HDMI pictures come back as empty screenshots: the TV protects video.
