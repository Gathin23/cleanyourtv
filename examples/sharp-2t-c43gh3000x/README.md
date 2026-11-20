# SHARP 2T-C43GH3000X

Tested on one SHARP 2T-C43GH3000X ("SHARP 2K Smart TV", device umeda, product SW7_SHARP_PH, Skyworth board,
MediaTek mt5867), Google TV on Android 11 (SDK 30), firmware RTM9.241015.159, Philippines, November 2026.
It is the same board and firmware family as `examples/sharp-32gf3265e/debloat.txt`, which disables a few
packages fewer. Despite the name, `examples/sharp-2t-c32gh3000i/` is a different board (beomil, Google TV 14).

## Apply

1. Debloat: `TV_HOST=<tv ip> tools/disable.sh examples/sharp-2t-c43gh3000x/debloat.txt`
2. Install the repo launcher as Home. The script disables the stock launcher itself once the new one owns Home:
   `TV_HOST=<tv ip> HOME_KEY_SERVICE=1 launcher/build-and-install.sh`
3. The first install stops on setupwraith's RecoveryActivity, as on other Google TV sets. Disable setupwraith
   (the last lines of the list, after the Wi-Fi check in its header), then run the install again.

Undo: `TV_HOST=<tv ip> tools/restore.sh examples/sharp-2t-c43gh3000x/debloat.txt com.google.android.apps.tv.launcherx`

## Remote buttons

- **Gear** arrives as HID usage 0x0C009F, which the kernel reports as `KEY_UNKNOWN`; Android sees keycode 83.
  With launcherx disabled it does nothing until the launcher's Home key helper takes it: set
  `settings_key_keycodes` to 83 in `launcher/app/src/main/res/values/config.xml` (or a theme) and install with
  `HOME_KEY_SERVICE=1`. `settings_key_panel` true opens the launcher's quick-settings panel instead of
  full-screen Settings.
- **Profile** (person icon) is a global key for `com.smartdevice.global.service`, whose `SkwGlobalKeyReceiver`
  starts Daily Key (`com.smartdevice.myapp`). With Daily Key disabled the receiver crashes
  (`ActivityNotFoundException ... com.smartdevice.myapp/...ui.MainActivity` in `logcat -b crash`) and the button
  does nothing. Either leave `com.smartdevice.myapp` enabled, or give the button another target with `app_keys`:
  the Home key helper sees the press before the global key receiver does.
- **Input, Netflix, Prime Video, YouTube and the mic** kept working with launcherx disabled and no extra helper.

## Notes

- Volume hold: with the Home key helper bound, a held Volume key steps once per press, as on other Android 11
  and 12 sets (`docs/devices/tcl-55p71b-g09-key-repeat.md`). `volume_pauses_filter` true brings it back for a tap
  followed by a hold within 4 s; the gear and app keys keep working once the pause ends. Spotify, while playing,
  writes the volume back a step behind the remote, so a hold stutters in it; that is the app, not the helper.
- Voice: the Assistant opened apps, answered questions and handed "play ... on YouTube" to YouTube with launcherx
  disabled. The first command after setup once failed with `provider.OPEN ... UNKNOWN_ERROR`
  (`docs/TROUBLESHOOTING.md`) and worked when repeated.
- After a launcher reinstall the Home key helper was once left bound and marked crashed in `dumpsys accessibility`,
  and took no keys. Rewriting `enabled_accessibility_services` (clear it, turn accessibility off, write it back)
  bound it again.
- Wi-Fi: setupwraith owned a phone hotspot saved during first setup; the home network belonged to the system.
  Forgetting the hotspot let `tools/disable.sh` disable setupwraith without touching the connection.
- Private DNS works on this set: `dns.adguard-dns.com` validated, ad domains resolved to 127.0.0.1, and Netflix
  and YouTube kept resolving. Set it in the TV's network settings; this folder does not configure it.
- The set has about 1.4 GB of RAM with very little free; Google's ambient screensaver was replaced by the
  launcher's clock and then disabled.
