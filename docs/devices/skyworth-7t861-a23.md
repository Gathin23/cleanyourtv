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

## Safety Warning: Critical Packages to Keep

> [!CAUTION]
> **Do not disable any of these critical system packages:**
> - **`com.skyworth.officehomepage`** & **`com.tianci.movieplatform`**: Stock launchers for Office Mode and Standard Mode. The Coocaa OS boot flow hard-depends on them; disabling either causes the system to hang indefinitely at the boot logo (network stack fails to start, ADB drops).
> - **`com.loostone.tuning`**: Microphone tuning & audio HAL driver bridge. Disabling it triggers `AudioService`/HAL self-test timeouts and boot hangs.
> - **`com.tianci.user`**: User authentication & token provider with strong IPC Binder dependencies across system framework daemons.
>
> Recovery requires entering hardware Recovery Mode and performing a factory reset ("Wipe data / factory reset"). **Keep all four enabled at all times.**

---

## Debloat verification: persistence across cold reboot

The packages in [`examples/skyworth-7t861-a23/debloat.txt`](../../examples/skyworth-7t861-a23/debloat.txt) remove telemetry, ad daemons, app store push services, and factory background tools.

### Recommended: Zero Debloat via Native Office Mode

> [!TIP]
> **Debloating is optional on this firmware.**
> If the TV is switched to **办公模式 (Office Mode)** in the native system settings, Coocaa OS internally suppresses all video streaming recommendations, banner advertisements, and background cloud preloading.
>
> Setting up CleanYourTV under native Office Mode produces an instant cold boot with **zero native loading screens** ("正在准备..."), while leaving all system packages fully intact (`pm list packages -d` remains empty), eliminating any risk of boot hangs.

### Verified safe to disable (12 packages)

- **Telemetry & Diagnostics (4)**:
  `com.skyworth.performance.monitors`, `com.skyworth.testtime`, `com.skyworthdigital.stb.dataprovider`, `com.skyworth.device`
- **Ads & Manufacturer Push (3)**:
  `com.coocaa.tvmanager`, `com.coocaa.guide`, `com.tianci.de`
- **Preloaded Stores & Streaming (4)**:
  `com.coocaa.tvpitv`, `com.tianci.appstore`, `com.skyworth.skymarket`, `com.coocaa.app_browser`
- **Factory & System (1)**:
  `com.coocaa.factory`

### Must stay enabled (Critical system lifelines)

| Package | Purpose / Why it must stay enabled |
|---|---|
| `com.skyworth.officehomepage` | Office Mode stock home; disabling breaks system boot chain and hangs indefinitely on boot logo |
| `com.tianci.movieplatform` | Standard Mode stock home base; boot chain hardcoded dependency, disabling causes fatal boot hang on COOCAA/SKYWORTH logo |
| `com.loostone.tuning` | Loostone microphone tuning and audio HAL driver bridge; disabling causes AudioService / HAL timeout during boot |
| `com.tianci.user` | Coocaa user account & auth service; framework Binder IPC dependency |
| `com.skyworth.tv` | HDMI, AV, and TV tuner input switching; picture and sound settings |
| `com.tianci.localmedia` | Built-in USB media player and local storage access |

---

## Replacing Home: Coocaa OS boot and Home key mechanism

Coocaa OS does not use Android's standard `Intent.CATEGORY_HOME` activity chooser.
For seamless cold boot without native transition overlays, follow this setup sequence:

### Step 0: Switch to Office Mode in TV Settings

Before installing CleanYourTV or redirecting properties, switch the television's desktop to **办公模式 (Office Mode)** using the remote in native system settings/menu. This ensures the firmware records `OfficeMode` as the active scene in its persistent database, silencing the video recommendation engine.

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
TV_HOST=<tv ip> tools/tv.py 'cmd appops set com.ccos.tv.homepage SYSTEM_ALERT_WINDOW allow'
```

> [!NOTE]
> On Android 10, the background activity start restriction requires granting `SYSTEM_ALERT_WINDOW` permission to `com.ccos.tv.homepage` (`cmd appops set com.ccos.tv.homepage SYSTEM_ALERT_WINDOW allow`) so the shim can reliably bring CleanYourTV to the foreground when the physical remote Home button is pressed from within background apps.

### Step 3: Redirect Coocaa Home properties (Direct Cold-Boot + Remote Key Dispatch)

Coocaa OS uses a dual-track home dispatch mechanism governed by `/system/etc/bootintent.xml` in `system_server`:
- **Cold-boot priority (`modelhome`)**: On system boot, `sky_launcher` evaluates `modelhome` first:
  ```xml
  <home name="modelhome" relation="and" propcomp="persist.service.model.pkg/persist.service.model.cls">
      <prop name="persist.service.model.pkg" type="exists"></prop>
      <prop name="persist.service.model.cls" type="exists"></prop>
  </home>
  ```
  Pointing `persist.service.model.*` directly to CleanYourTV (`com.coocaa.tvhome/com.gathin.tvhome.MainActivity`) allows `system_server` to launch CleanYourTV directly upon boot without flashing OEM desktop layouts or triggering intermediate "正在准备..." loading screens.
- **Physical Remote Home key dispatch (`persist.service.homepage.*`)**: When third-party apps are running, remote Home button presses are routed to `persist.service.homepage.*`. Pointing these to `com.ccos.tv.homepage` allows `coocaa-home-shim` to instantly return focus to CleanYourTV.

Apply the properties using `tools/tv.py`:

```bash
# 1. Cold boot: launch CleanYourTV directly via modelhome
TV_HOST=<tv ip> tools/tv.py 'setprop persist.service.model.pkg com.coocaa.tvhome'
TV_HOST=<tv ip> tools/tv.py 'setprop persist.service.model.cls com.gathin.tvhome.MainActivity'
TV_HOST=<tv ip> tools/tv.py 'setprop persist.service.model.name CleanYourTV'

# 2. Remote Home key: dispatch through shim
TV_HOST=<tv ip> tools/tv.py 'setprop persist.service.homepage.pkg com.ccos.tv.homepage'
TV_HOST=<tv ip> tools/tv.py 'setprop persist.service.homepage.cls com.ccos.tv.homepage.LaunchActivity'
```

> [!TIP]
> Setting `persist.service.model.name` to `CleanYourTV` replaces the native desktop mode identifier. In system Settings, "系统模式 (System Mode)" will appear unselected, preventing the system from automatically triggering OEM mode initialization delays.

---

## Cold boot behavior

After a cold boot (power unplugged and restored):
- Coocaa OS `system_server` matches `modelhome` from `/system/etc/bootintent.xml` and launches `com.coocaa.tvhome.MainActivity` directly.
- Boots into CleanYourTV seamlessly with zero intermediate loading screens ("正在准备...") and zero desktop flash.
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
TV_HOST=<tv ip> tools/tv.py 'setprop persist.service.model.name OfficeMode'
TV_HOST=<tv ip> tools/tv.py 'cmd appops set com.ccos.tv.homepage SYSTEM_ALERT_WINDOW default'

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
