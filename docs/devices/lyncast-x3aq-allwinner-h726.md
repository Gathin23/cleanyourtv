# LYNCAST X3AQ / Allwinner H726 (LuminaOS), Android 14

Hardware specification, vendor ADB behavior, and debloat notes for the **LYNCAST X3AQ** projector.

| Field | Observed value |
|---|---|
| Retail model | LYNCAST X3AQ Android TV Projector |
| SoC / Board | Allwinner H726 (`sun50iw15p1`), board reports `model: h726-2` / `H726 Focus` |
| Reported Model | `product: rk3562_projector`, `device: rk3562_projector` |
| Fingerprint | `google/redfin/redfin:13/TQ2A.230305.008.F1/7679548:user/release-keys__2025-10-05` (faked as Pixel 5 Android 13, but actually Android 14 / API 34) |
| Android / API | Android 14 / API 34 (`ro.build.version.sdk=34`) |
| Kernel | `5.15.167-g3b33e70e1aa6-dirty #249 SMP PREEMPT armv7l Toybox` |
| SELinux | `Permissive` (`ro.boot.selinux=permissive`) |
| Stock Home | `com.htc.luminaos` (`.activity.MainActivity`) |
| Autofocus / Keystone | `com.hysd.vafocus` (ChuangFei Vision / `tpjz_allwinner` camera stepper) + `com.htc.luminaos` |
| Video Inputs | `com.softwinner.awsource`, `com.softwinner.vis` (VideoInputService) |
| Web Remote API | `com.htc.btremote.receiver` ("Lumina Go" REST API listening on port `8899`) |

---

## Vendor ADB Behavior

On stock retail firmware, the vendor actively restricts ADB debugging via custom hooks in `SettingsProvider.apk`:
- Toggling **USB Debugging** in Developer Options, LuminaOS Developer Mode, or Softwinner Factory Menu (`*#*#3646633#*#*` / `AwTvFactoryMenu`) immediately flips back to **Disabled / Off** within 1 second.
- Decompilation of `SettingsProvider.apk` reveals logic that forces `adb_enabled = 0`, `adb_wifi_enabled = 0`, and `development_settings_enabled = 0` whenever `!Build.IS_DEBUGGABLE`.
- Because `adb_enabled` remains `0`, `AdbDebuggingManager` in `system_server` stays dormant. It never displays the on-screen key confirmation dialog, leaving incoming network ADB connections permanently `unauthorized`.

Mitigating this vendor restriction requires gaining an initial privileged shell or external key provisioning, which falls outside CleanYourTV's scope as a reversible, non-root debloater.

For environments where an authorized ADB connection is already established, the sections below document the verified launcher configuration and safe package list.

---

## CleanYourTV Launcher Takeover

1. **Build**: CleanYourTV (`com.gathin.tvhome`) compiles cleanly for API 34.
2. **Install**:
   ```bash
   adb push app-release.apk /sdcard/cleanyourtv.apk
   adb shell pm install -r /sdcard/cleanyourtv.apk
   adb shell rm -f /sdcard/cleanyourtv.apk
   ```
3. **Set Default Home**:
   ```bash
   adb shell cmd package set-home-activity com.gathin.tvhome/.MainActivity
   ```
4. **DO NOT DISABLE `com.htc.luminaos` COMPLETELY**:
   Unlike standard Android TV devices where the stock launcher can be safely disabled, `com.htc.luminaos` also hosts the projector's keystone adjustment receivers (`com.htc.intent.action.OPEN_MANUAL_KEYSTONE`), projection orientation modes, and Bluetooth remote bindings. 
   Leaving `com.htc.luminaos` enabled while pointing `set-home-activity` to `com.gathin.tvhome` works reliably across reboots without losing projector controls.

---

## Debloat List

See [`examples/lyncast-x3aq/debloat.txt`](../../examples/lyncast-x3aq/debloat.txt). Tested on retail unit, reboot-checked.

### Safe to Disable (`pm disable-user --user 0`)
- `com.android.ytb` (YouTube accessibility helper)
- `com.android.nfx` (Netflix accessibility helper)
- `com.android.music` (AOSP stock music player)
- `com.softwinner.TvdVideo` (AOSP stock video player)
- `com.softwinner.awlogsettings` (Vendor logging UI)
- `com.htc.hyk_test` (Factory test app)
- `com.htc.screensaver` (Vendor screensaver)
- `com.hotack.screensaversdk` (Vendor screensaver SDK)
- `com.android.bips` (Built-in print service)
- `com.android.companiondevicemanager` (Companion manager)
- `com.android.dreams.basic` (AOSP screensaver)
- `com.android.traceur` (System tracing)
- `com.android.htmlviewer` (HTML viewer)

### Off by Choice (Commented Out in `debloat.txt`)
- `com.toofifi.lineserver` (Cast server)
- `com.mphotool.usbcastserver` (USB mirror server)
- `com.google.android.youtube.tv` (Stock YouTube TV)
- `com.google.android.katniss` (Google Assistant / voice search)

### Load-Bearing — Keep Enabled
- `com.hysd.vafocus` (Autofocus camera and stepper motors)
- `com.htc.luminaos` (Keystone, settings, remote bindings)
- `com.softwinner.awsource` (HDMI / AV input switcher)
- `com.softwinner.vis` (Video Input Service)
- `com.softwinner.miracastReceiver` (Miracast screen mirroring)
- `com.htc.btremote.receiver` (Remote receiver & Lumina Go Web API)
- `com.softwinner.update` (OTA Update service)

---

## App Stores & Remote Navigation
- **Official F-Droid**: Touch-only UI. Does not support D-pad Leanback navigation well.
- **Droid-ify (`com.looker.droidify`)**: Fully compatible with Android TV D-pad remotes; Material You design works cleanly at 1080p.
- **Google Play Store**: Built-in Play Store remains 100% operational after debloat.
