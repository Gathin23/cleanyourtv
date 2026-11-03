# Skyworth 7T861_A23 (Coocaa OS, Android 10)

Tested on Skyworth 7T861_A23 (MediaTek MT9638 / mt5872, Android 10, build `eng.nobody.20241225.120706`).

## Safety Warning: Critical Packages to Keep

> [!CAUTION]
> **Do not disable any of these critical system packages:**
> - **`com.skyworth.officehomepage`** & **`com.tianci.movieplatform`**: Stock launchers for Office Mode and Standard Mode. The Coocaa OS boot flow hard-depends on them; disabling either causes the TV to hang indefinitely at the boot logo (network stack fails to start, ADB drops).
> - **`com.loostone.tuning`**: Microphone tuning & audio HAL driver bridge. Disabling it triggers `AudioService`/HAL self-test timeouts and boot hangs.
> - **`com.tianci.user`**: User authentication & token provider with strong IPC Binder dependencies across system framework daemons.
>
> Recovery requires entering hardware Recovery Mode and performing a factory reset ("Wipe data / factory reset"). Keep all four enabled at all times.

## Recommended: Zero Debloat via Office Mode (100% Safe)

> [!TIP]
> **No packages need to be disabled on this model.**
> Switching the TV to **办公模式 (Office Mode)** in the native system settings shuts off all video streaming feeds, banner advertisements, and background cloud preloading directly from the source.
>
> In `debloat.txt`, only `com.skyworth.device` is active by default; the rest are commented out:
> - **Zero native loading screens**: Cold boot seamlessly transitions into CleanYourTV without intermediate "正在准备..." loading screens or network preloading delay.
> - **Zero brick risk**: All system packages remain untouched (`pm list packages -d` is empty), ensuring 100% firmware stability and OTA update safety.

## Applying the debloat list (Optional)

If you explicitly wish to disable background telemetry and non-essential stores, uncomment the respective lines in [`debloat.txt`](debloat.txt):

- **Apply debloat list**:
  ```bash
  TV_HOST=<tv-ip> tools/disable.sh examples/skyworth-7t861-a23/debloat.txt
  ```
- **Restore debloat list**:
  ```bash
  TV_HOST=<tv-ip> tools/restore.sh examples/skyworth-7t861-a23/debloat.txt
  ```

## Replacing the Home Launcher

On Coocaa OS, the stock launcher cannot be replaced via standard Android Home chooser without bootlooping.
The cleanest and safest handover sequence:

1. **Switch to Office Mode first**: On the TV, switch desktop mode to **办公模式 (Office Mode)** in the native settings/menu.
2. **Build and install launcher**: Build under the Coocaa package ID (`com.coocaa.tvhome`) and install companion `coocaa-home-shim`.
3. **Redirect Home properties**: Point `persist.service.model.*` directly to `com.coocaa.tvhome` (direct cold-boot) and `persist.service.homepage.*` to `com.ccos.tv.homepage` (remote Home key dispatch).

Detailed instructions, boot observations, and property undo commands are documented in:
👉 [`docs/devices/skyworth-7t861-a23.md`](../../docs/devices/skyworth-7t861-a23.md)

## The launcher's Settings pill

The Settings pill sends the standard `android.settings.SETTINGS` intent. This firmware's own settings app can be
offered beside it as **Native settings** in the launcher's menu: add
`com.skyworth.setting/com.skyworth.setting.module.main.MainProxyActivity` to `settings_fallback_components` in
`launcher/app/src/main/res/values/config.xml` and build. That list is read from `config.xml` only, never from a
theme's `config.json` ([`themes/README.md`](../../themes/README.md), Keys table); on API 30 and later the package
also has to be in the launcher's `<queries>`, and this set is Android 10, where package visibility does not apply.
