# Amazon Fire TV: Native Launcher Support & Zero-Latency Home Binding

This note documents findings and configuration tested across two generations of Amazon Fire TV Sticks:
- **Target 1:** Fire TV Stick 4K Max 1st Gen (`AFTKA` / `kara`), Android 9 / API 28, Fire OS **7.7.1.3**.
- **Target 2:** Fire TV Stick 4K 1st Gen (`AFTMM` / `mantis`), Android 7.1.2 / API 25, Fire OS **6.7.1.1** (build `NS6711 / 0011644900484`).

---

## 1. Native Home Key Binding without Accessibility Delay

By default, alternative launchers on Fire OS rely on an accessibility service (`HomeKeyService`) or background pollers to detect the stock launcher and redirect focus. This introduces noticeable latency (500–1500 ms) and visible UI flicker when pressing the remote's Home button.

With root access (e.g. via transient GhostLock execution):
- **Stock HomeActivity Disabling:** Rather than disabling the entire `com.amazon.tv.launcher` package, only the primary home activity component is disabled:
  ```bash
  pm disable com.amazon.tv.launcher/.ui.HomeActivity_vNext
  ```
  *(Leaving `com.amazon.tv.launcher` enabled preserves the full settings menu, screensavers, and system overlays.)*
- **Default Home Intent Assignment:**
  ```bash
  cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
  ```
- **Result:** Physical Home key presses (`KEYCODE_HOME`) resolve directly to CleanYourTV with **0 ms latency** and zero screen flash.

---

## 2. Boot Animation Watchdog Delay (60-second Fix)

### Issue
When `HomeActivity_vNext` is disabled, Fire OS WindowManagerService (`FireTvWindowManagerServiceCallback$HomeInitialisationObserver`) never receives the signal indicating that the real home application has finished rendering. As a safety measure, Fire OS holds the boot animation ("firetv" loop) on screen for a **60-second fallback timeout**:
```text
FireTvWmsCallback: Stopping boot animation because timer elapsed waiting for real home to send signal.
```
Even though the Android runtime and CleanYourTV boot in ~28 seconds, the UI is hidden behind the boot animation until ~88 seconds after power-on.

### Solution
CleanYourTV now sets the secure setting upon launch in `MainActivity.onCreate()` and `onResume()`:
```java
Settings.Secure.putInt(getContentResolver(), "bootanim.real_home_ready", 1);
```
Requires `android.permission.WRITE_SECURE_SETTINGS` (granted via ADB or root):
```bash
pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS
```
Upon receiving this signal, Fire OS immediately logs:
```text
FireTvWmsCallback: Stopping boot animation because real home sent signal.
```
This reduces boot time from **88.2s down to 30.8s** (a ~58s reduction).

---

## 3. Full Settings Grid vs "About" Page

On Fire OS:
- `com.amazon.tv.settings.v2/.tv.device.DeviceActivity` opens only the single-page "My Fire TV / About" subscreen.
- `com.amazon.tv.launcher/.ui.MainSettingsActivity` hosts the complete **14-category Settings Grid** (Network, Display & Audio, Applications, Remotes & Bluetooth, Preferences, Alexa, Equipment Control, etc.).

By listing `com.amazon.tv.launcher/.ui.MainSettingsActivity` first in `settings_fallback_components` and keeping `MainSettingsActivity` enabled while disabling `HomeActivity_vNext`, the Settings pill in CleanYourTV launches the full system settings page.

---

## 4. Backwards Compatibility for Fire OS 6 (Android 7.1 / API 25)

1. **`minSdk 25` & Desugaring:**
   Enabled `coreLibraryDesugaring` (`com.android.tools:desugar_jdk_libs:2.0.4`) so `java.time` (`LocalDate`) works seamlessly on Android 7.1 without runtime `NoClassDefFoundError`.
2. **API Level Guard:**
   Guarded `PackageManager.getChangedPackages()` (introduced in API 26) with `Build.VERSION.SDK_INT >= 26` to avoid `NoSuchMethodError` on Android 7.1.
