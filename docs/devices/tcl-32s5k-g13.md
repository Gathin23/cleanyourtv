# TCL 32S5K (G13, Google TV 14): power key, restart and Settings key notes

This TCL 32S5K is not the unit in [`examples/tcl-32s5k/`](../../examples/tcl-32s5k/): that one is a C06 board on
Android TV 11, this one reports device `G13`, Google TV 14 (SDK 34), build `UKN2.241110.001`. It has 954 MB of RAM
(`ro.config.low_ram=true`), a 4-core ARMv7 CPU, draws its UI at 1280x720, and its remote is the
`MStar Smart TV IR Receiver` (vendor `0x3697`). These are notes from one owner's set, not a tested debloat list.

## Power key and restart

- **Tap:** `dumpsys window policy` reports `mShortPressOnPowerBehavior=SHORT_PRESS_POWER_GO_TO_SLEEP`. A tap puts
  the set in standby with the screen off.
- **Hold:** `power_button_long_press` was unset on this set. The owner set it to 3 so that a hold switches the TV
  off with no dialog (`LONG_PRESS_POWER_SHUT_OFF_NO_CONFIRM`), instead of Android's power menu, which is the phone
  layout and hard to use with a D-pad:

  ```bash
  tools/venv/bin/python tools/tv.py "settings put global power_button_long_press 3"
  ```

  Undo: `tools/venv/bin/python tools/tv.py "settings delete global power_button_long_press"`.
- **Restart from the shell:** `tools/tv.py --reboot` comes back on, to Home, with no key pressed, and is back
  about 40 seconds after the request (`sys.boot.reason` `reboot,shell`).
- **Restart from Android's power menu:** the set goes dark and drops off the network (no route to its address), and
  it boots only when the remote's power key is pressed (`sys.boot.reason` `reboot,userrequested`). For the owner it
  behaves like a power-off.
- **The launcher's own key helper:** the power key never reaches an accessibility service. The policy handles it
  first (`interceptKeyBeforeQueueing`), so a tap cannot open the launcher's menu instead.

## Settings key and sound

- **Settings key:** the remote's Settings key opens the launcher's quick settings panel only with the Home key
  helper installed (`HOME_KEY_SERVICE=1 launcher/build-and-install.sh`). After a reinstall without it, the key
  stops opening the panel.
- **`am force-stop com.gathin.tvhome`:** also switches the helper off, until the next install with the flag.
- **Sound settings:** `ACTION_SOUND_SETTINGS` has two handlers on this firmware:
  `com.android.tv.settings.device.sound.SoundActivity`, a side panel over the app in front, and
  `DisplaySoundActivity`, the full-screen page.
- **Picture settings:** `ACTION_DISPLAY_SETTINGS` has one handler,
  `com.android.tv.settings/.partnercustomizer.picture.PictureActivity`, the picture side panel.

## Memory

With the Home key helper on, the launcher's process stays in the foreground. While another app is in front,
`dumpsys activity processes com.gathin.tvhome` reported `trimMemoryLevel=10` (`TRIM_MEMORY_RUNNING_LOW`), and
Android refused `am send-trim-memory ... BACKGROUND` for the process. On this 1 GB set, a full-screen app usually
runs under that level.
