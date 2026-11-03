# Toshiba 55M550LP (Android 11)

This Toshiba is built by Hisense: inside it is the same board and firmware as the Hisense SmartTV 4K FFM
(`examples/hisense-smarttv-4k-ffm-android11/debloat.txt`), so the notes there apply here too. It is not one of
the Vestel-built Toshiba sets, and their lists do not fit it. Check with `tools/tv.py --probe`: this set prints
`ro.product.device = songshan` and `ro.product.manufacturer = Hisense`.

## What to expect

- **ADB**: USB debugging alone opens port 5555 on this set; there is no Wireless debugging to pair.
- **Setup wizard**: the launcher swap needs the Google TV setup wizard (setupwraith) disabled, or it takes Home
  back. It owned one saved Wi-Fi network here that the TV was not using, so `tools/disable.sh` stopped until it
  was run with `FORCE_SETUPWRAITH=1`; that saved network was forgotten and the TV stayed online. Check which
  network is yours first, as the list's header says.
- **Remote buttons**: once the Google TV home is disabled, Home and Input keep working, but the gear and YouTube
  buttons do nothing. These lines in `launcher/app/src/main/res/values/config.xml`, installed with
  `INPUT_KEY_SERVICE=1`, bring them back:

  ```xml
  <string-array name="app_keys" translatable="false">
      <item>83:com.android.tv.settings</item>
      <item>293:com.google.android.youtube.tv</item>
  </string-array>
  ```

- **The trade**: with that key helper on, holding Volume Up moves one step per press instead of ramping. It is
  one or the other on this firmware (`docs/devices/tcl-55p71b-g09-key-repeat.md` explains why). Leave the helper
  off to keep the ramp and open YouTube and Settings from Home instead.
- **Restarting**: holding Power on the remote only offers Shut down; restart from Settings instead.

## Undo

```sh
TV_HOST=<tv ip> tools/restore.sh examples/toshiba-55m550lp/debloat.txt com.google.android.apps.tv.launcherx
```
