# Skyworth 7T861_A23 (Coocaa OS, Android 10)

Tested on Skyworth 7T861_A23 (MediaTek MT9638 / mt5872, Android 10, build `eng.nobody.20241225.120706`).

## Safety Warning

> [!CAUTION]
> **Do not disable `com.skyworth.officehomepage`.**
> Disabling the stock home app on this firmware causes the TV to hang indefinitely on the SKYWORTH boot logo upon restart. The system will fail to start the network stack, disabling port 5555. Recovery requires entering Recovery Mode and performing a factory reset ("Wipe data / factory reset").

## Applying the debloat list

Everything in [`debloat.txt`](debloat.txt) is disabled via `pm disable-user`, never uninstalled.

- **Apply debloat list**:
  ```bash
  TV_HOST=<tv-ip> tools/disable.sh examples/skyworth-7t861-a23/debloat.txt
  ```
- **Restore debloat list**:
  ```bash
  TV_HOST=<tv-ip> tools/restore.sh examples/skyworth-7t861-a23/debloat.txt
  ```

All 16 packages in `debloat.txt` have been verified across a cold reboot. Specifically, `com.skyworth.device` and `com.coocaa.theme_package_ccos_nine` remain disabled with Bluetooth remote control, Wi-Fi, audio, and settings working normally.

## Replacing the Home Launcher

On Coocaa OS, the stock launcher cannot be replaced via standard Android Home chooser without bootlooping. Replacing the launcher safely requires building under the Coocaa package ID, using `coocaa-home-shim`, and pointing both the `persist.service.homepage` and `persist.service.model` properties (dual-track dispatch for standard/video modes and Home Office mode).

Detailed instructions, boot observations, and property undo commands are documented in:
👉 [`docs/devices/skyworth-7t861-a23.md`](../../docs/devices/skyworth-7t861-a23.md)

## The launcher's Settings pill

The Settings pill sends the standard `android.settings.SETTINGS` intent. This firmware's own settings app can be
offered beside it as **Native settings** in the launcher's menu: add
`com.skyworth.setting/com.skyworth.setting.module.main.MainProxyActivity` to `settings_fallback_components` in
`launcher/app/src/main/res/values/config.xml` and build. That list is read from `config.xml` only, never from a
theme's `config.json` ([`themes/README.md`](../../themes/README.md), Keys table); on API 30 and later the package
also has to be in the launcher's `<queries>`, and this set is Android 10, where package visibility does not apply.
