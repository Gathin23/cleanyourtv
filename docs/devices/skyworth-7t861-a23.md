# Skyworth 7T861_A23 (MediaTek MT9638, Coocaa OS on Android 10): debloat, custom home shim and boot safety

Owner report for a China-market Skyworth 7T861_A23 television running Coocaa OS on Android 10.
The debloat package list for this unit lives in [`examples/skyworth-7t861-a23/debloat.txt`](../../examples/skyworth-7t861-a23/debloat.txt)
and its launcher configuration in [`examples/skyworth-7t861-a23/config.json`](../../examples/skyworth-7t861-a23/config.json).

## Observed firmware

| Field | Observed value |
|---|---|
| Model | `7T861_A23` |
| Product / device | `mt9638` (`mt5872`) |
| Manufacturer | `skyworth` |
| Android / API | `10` / `29` |
| Build display id | `QQ2A.200305.004.A1 release-keys` / `eng.nobody.20241225.120706` |
| Application ABIs | `armeabi-v7a, armeabi` (32-bit userspace) |
| `ro.adb.secure` | `0` |
| `service.adb.tcp.port` | `5555` |
| Stock home | `com.skyworth.officehomepage` (OfficeMode) / `com.tianci.movieplatform` |
| TV inputs & source manager | `com.skyworth.tv` |

Port 5555 opens over Wi-Fi without pairing prompts (`ro.adb.secure=0`). `persist.service.adb.enable=1` and `persist.sys.openadb=1` keep network ADB open across reboots.

---

## Safety Warning: Never disable `com.skyworth.officehomepage`

> [!CAUTION]
> **Do not disable `com.skyworth.officehomepage` directly.**
> On this firmware, disabling the stock home application (`pm disable-user --user 0 com.skyworth.officehomepage`) breaks the system boot chain and causes the TV to hang indefinitely at the SKYWORTH boot logo. The network stack does not initialize, leaving ADB and port 5555 unreachable.
>
> Recovery requires entering Recovery Mode and performing a hardware factory reset ("Wipe data / factory reset"). **Keep `com.skyworth.officehomepage` enabled at all times.**

---

## Debloat verification: persistence across cold reboot

The 16 packages in [`examples/skyworth-7t861-a23/debloat.txt`](../../examples/skyworth-7t861-a23/debloat.txt) remove telemetry, ad daemons, app store push services, and factory calibration background tools.

### Verified safe to disable (16 packages)

- **Telemetry & Diagnostics (4)**:
  `com.skyworth.performance.monitors`, `com.skyworth.testtime`, `com.skyworthdigital.stb.dataprovider`, `com.skyworth.device`
- **Ads & Manufacturer Push (5)**:
  `com.coocaa.tvmanager`, `com.coocaa.guide`, `com.tianci.de`, `com.tianci.user`, `com.coocaa.theme_package_ccos_nine`
- **Preloaded Stores & Streaming (5)**:
  `com.coocaa.tvpitv`, `com.tianci.movieplatform`, `com.tianci.appstore`, `com.skyworth.skymarket`, `com.coocaa.app_browser`
- **Factory & Tuning Background Daemons (2)**:
  `com.loostone.tuning`, `com.coocaa.factory`

In particular, `com.skyworth.device` (SmartDevice IoT and location reporting) and `com.coocaa.theme_package_ccos_nine` (default GoldenFaithTheme package) were tested for persistence:
1. Disabled via `tools/tv.py 'pm disable-user --user 0 com.skyworth.device'` and `tools/tv.py 'pm disable-user --user 0 com.coocaa.theme_package_ccos_nine'`.
2. Verified after a cold reboot (power cycled): both packages remained in state `disabled-user` (`enabled=3`). The system did not re-enable them.
3. Bluetooth remote pairing, Wi-Fi connectivity, picture adjustments, and audio remain fully functional with nothing missing.

### Must stay enabled

| Package | Purpose / Why it must stay enabled |
|---|---|
| `com.skyworth.officehomepage` | Stock home application; disabling causes fatal hang on SKYWORTH boot logo |
| `com.skyworth.tv` | HDMI, AV, and TV tuner input switching; picture and sound settings |
| `com.tianci.localmedia` | Built-in USB media player and local storage access |

---

## Replacing Home: Coocaa OS boot and Home key mechanism

Coocaa OS does not use Android's standard `Intent.CATEGORY_HOME` activity chooser. Instead:
- Home navigation and boot dispatch are controlled by system properties: `persist.service.homepage.pkg`, `persist.service.homepage.cls`, `persist.service.model.pkg`, and `persist.service.model.cls`.
- In addition, Coocaa OS aggressive process management terminates third-party background applications whose package name does not begin with manufacturer prefixes (`coocaa`, `skyworth`, `tianci`, `android`).

### Step 1: Build the launcher under Coocaa package ID

Compile the launcher with the Coocaa application ID so it is recognized as a privileged OEM package by the process manager:

```bash
cd launcher
./gradlew assembleRelease -PlauncherApplicationId=com.coocaa.tvhome
```

### Step 2: Install launcher and coocaa-home-shim

Install the compiled launcher and the companion `coocaa-home-shim` (`com.ccos.tv.homepage`), which bridges Coocaa OS home dispatch to the CleanYourTV activity. The shim is not part of this repository: it is its own project, [coocaa-home-shim](https://github.com/VincentYe123/coocaa-home-shim), whose releases page carries the APK (or build it from source). `tools/tv.py` installs nothing on its own, so an APK goes up with `tools/b64push.py` and the TV's own `pm install` runs it, the way [`examples/skyworth-7s60-a5`](../../examples/skyworth-7s60-a5/README.md) does it on the same OS:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/b64push.py launcher/app/build/outputs/apk/release/app-release.apk /data/local/tmp/tvhome.apk
TV_HOST=<tv ip> tools/tv.py 'pm install -r /data/local/tmp/tvhome.apk; rm -f /data/local/tmp/tvhome.apk'
TV_HOST=<tv ip> tools/venv/bin/python -I tools/b64push.py coocaa-home-shim.apk /data/local/tmp/shim.apk
TV_HOST=<tv ip> tools/tv.py 'pm install -r /data/local/tmp/shim.apk; rm -f /data/local/tmp/shim.apk'
```

### Step 3: Redirect Coocaa Home properties

Point the system homepage properties to the shim using `tools/tv.py`:

```bash
TV_HOST=<tv ip> tools/tv.py 'setprop persist.service.homepage.pkg com.ccos.tv.homepage'
TV_HOST=<tv ip> tools/tv.py 'setprop persist.service.homepage.cls com.ccos.tv.homepage.LaunchActivity'
TV_HOST=<tv ip> tools/tv.py 'setprop persist.service.model.pkg com.ccos.tv.homepage'
TV_HOST=<tv ip> tools/tv.py 'setprop persist.service.model.cls com.ccos.tv.homepage.LaunchActivity'
```

---

## Cold boot behavior

After a cold boot (power unplugged and restored):
- Coocaa OS reads the `persist.service.homepage` properties, initiates `com.ccos.tv.homepage.LaunchActivity`, and immediately transitions focus to `com.coocaa.tvhome.MainActivity`.
- `dumpsys window | grep mCurrentFocus` settles directly on:
  ```text
  mCurrentFocus=Window{... u0 com.coocaa.tvhome/com.gathin.tvhome.MainActivity}
  ```
- Physical remote Home button presses immediately return to CleanYourTV without latency or flickering.
- ADB port 5555 reconnects cleanly over Wi-Fi.

---

## Undo steps: Restoring stock home

To restore the TV's original stock desktop dispatch properties:

```bash
# 1. Restore Coocaa Home system properties to stock components
TV_HOST=<tv ip> tools/tv.py 'setprop persist.service.homepage.pkg com.tianci.movieplatform'
TV_HOST=<tv ip> tools/tv.py 'setprop persist.service.homepage.cls com.coocaa.homepage.vast.HomePageActivity'
TV_HOST=<tv ip> tools/tv.py 'setprop persist.service.model.pkg com.skyworth.officehomepage'
TV_HOST=<tv ip> tools/tv.py 'setprop persist.service.model.cls com.skyworth.officehomepage.view.HomepageActivity'

# 2. Launch stock desktop
TV_HOST=<tv ip> tools/tv.py 'am start -n com.skyworth.officehomepage/.view.HomepageActivity'
```

If removing the custom launcher and shim completely:

```bash
TV_HOST=<tv ip> tools/tv.py 'pm uninstall com.coocaa.tvhome'
TV_HOST=<tv ip> tools/tv.py 'pm uninstall com.ccos.tv.homepage'
```

---

## Inputs and Source Switching

`com.skyworth.tv` manages HDMI, AV, and TV tuner input sources, as well as picture adjustments. Keep it enabled.
In `config.json`:
- `"show_inputs_pill": true`
- `"coocaa_sources": true`

This enables the top-bar Inputs pill to open the native Skyworth source picker directly.
