# Philips 65PQT8169/56: the launcher and the remote

[`debloat.txt`](debloat.txt) only disables, and its header has the Wi-Fi warning for the setup wizard. The
launcher half, as it ran on this set:

1. Apply the list first, setup wizard included, so its `.RecoveryActivity` cannot take Home.
2. Map the remote's gear button: add its scancode to `settings_key_scancodes` in
   `launcher/app/src/main/res/values/config.xml` (a theme cannot carry scancodes):

   ```xml
   <integer-array name="settings_key_scancodes">
       <item>397</item>
   </integer-array>
   ```

3. Build and install with the helper that reads it:
   `HOME_KEY_SERVICE=1 TV_HOST=<tv ip> launcher/build-and-install.sh`. The handover held on the first run:
   launcherx was disabled, and `dumpsys activity activities | grep mFocusedApp` named the new launcher.

## The remote

An IR remote (`MStar Smart TV IR Receiver`), tested by pressing the real buttons with launcherx disabled:

| Button | Result |
|---|---|
| Netflix, YouTube, Prime Video | Open their apps with no helper: the MediaTek hotkey dispatcher in com.mediatek.tv.service answers them |
| Input, Home, Back | Work with no helper |
| Gear | Does nothing without the helper. The dispatcher's settings action asks for `android.app.action.TOGGLE_NOTIFICATION_HANDLER_PANEL`, which only launcherx answered (`TvNotificationPanel: Could not resolve activityInfo`). `getevent -lt` shows `KEY_CALENDAR`, scancode 397 (`MSC_SCAN` 0xbf). With step 2 and HomeKeyService bound it opens Settings |
| Held Volume Up/Down | Ramps with no helper. With HomeKeyService bound (`capabilities=8`) it moves one step per press, the trade-off described in [the TCL G09 notes](../../docs/devices/tcl-55p71b-g09-key-repeat.md). The owner kept the gear; `consume_volume_keys` was not tried |

To get the ramping volume back, switch the helper off in Settings > Accessibility, or run
`settings delete secure enabled_accessibility_services` over ADB. The gear then does nothing, and the Settings pill
on Home still opens Settings.

## Undo

`TV_HOST=<tv ip> tools/restore.sh examples/philips-65pqt8169/debloat.txt com.google.android.apps.tv.launcherx`
brings every listed package and the stock home back. The screensaver and the helper were set by hand, outside the
list: the list header has the screensaver setting to put back.
