# Amazon Fire TV: Native Launcher Support & Zero-Latency Home Binding

This guide details how to replace the stock Amazon Fire TV launcher with CleanYourTV as the native default home screen with **0 ms hardware Home button latency**, verified across two different Fire TV generations:

| Property | Target 1: Fire TV Stick 4K Max 1st Gen | Target 2: Fire TV Stick 4K 1st Gen |
| :--- | :--- | :--- |
| **Model ID** | `AFTKA` (board: `kara`) | `AFTMM` (board: `mantis`) |
| **SoC / Kernel** | MediaTek MT8696 (Linux 4.19 / arm64) | MediaTek MT8695 (Linux 4.4.120+ / armv7l) |
| **OS Version** | Fire OS **7.7.1.3** (Android 9 / API 28) | Fire OS **6.7.1.1** (Android 7.1.2 / API 25) |
| **Root Engine** | GhostLock Fire OS 7 (Sheldon/Kara profile) | GhostLock Mantis port (API 24, MT8695 profile) |
| **Execution** | **100% Wireless ADB** (`<IP>:5555`) | **100% Wireless ADB** (`<IP>:5555`) |

---

## 1. Why Native Binding vs Accessibility Overlay

Standard launcher managers on Fire OS rely on Android Accessibility (`HomeKeyService`) or background polling to detect when Amazon's launcher comes into the foreground and immediately start the custom launcher. 

While functional, this approach has inherent flaws:
- **Latency:** 500–1500 ms delay before focus switches.
- **UI Flicker:** Stock Amazon home renders briefly before CleanYourTV appears.
- **Service Killing:** Fire OS aggressive memory management frequently stops accessibility services during heavy video playback.

By gaining temporary in-memory root access via GhostLock, we can disable only the stock Home activity component while keeping the rest of Fire OS intact:
```bash
pm disable com.amazon.tv.launcher/.ui.HomeActivity_vNext
cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
```
Because the stock launcher package (`com.amazon.tv.launcher`) remains enabled, Amazon's Alexa overlays, screensavers, and the complete 14-grid System Settings remain fully functional.

---

## 2. External Prerequisites & Companion Repositories

CleanYourTV is strictly an application launcher and does not bundle or distribute root exploit binaries within its repository. Because transient in-memory root requires device-specific and kernel-version-specific memory offsets, users obtain the matching binaries and profiles from dedicated open-source companion projects:

- **For Fire OS 7 (`kara` / `sheldon` / Fire TV Stick 4K Max 1st Gen):**
  Use the [firetv-clean-root](https://github.com/saisarathbb/firetv-clean-root) companion toolchain. It provides the pre-built `ghostlock_root` binary compiled for Android 9 / API 28 along with Sheldon/Kara configuration profiles.
- **For Fire OS 6 (`mantis` / Fire TV Stick 4K 1st Gen):**
  Use the [firetv-mantis-standalone](https://github.com/Delitants/firetv-mantis-standalone) toolchain. It provides the MediaTek MT8695 memory profile (`mantis-NS6711-5908.conf`) and patch files to compile GhostLock targeting Android 7.1 / API 24 libc.
- **Upstream Exploit Engine:**
  The underlying in-memory privilege escalation mechanism is maintained at [GhostLock](https://github.com/R0rt1z2/GhostLock).

---

## 3. Wireless Setup: Fire OS 7 (`kara` / `sheldon` / 4K Max 1st Gen)

### Requirements
- Fire OS 7.x (e.g. 7.7.1.3).
- GhostLock Fire OS 7 binary (compiled for API 28 arm64/arm).

### Wireless Steps
1. Connect over Wi-Fi ADB:
   ```bash
   adb connect <TV_IP>:5555
   adb -s <TV_IP>:5555 wait-for-device
   ```

2. Push CleanYourTV and the root payload:
   ```bash
   adb -s <TV_IP>:5555 install -r launcher/cleanyourtv-release.apk
   adb -s <TV_IP>:5555 shell "mkdir -p /data/local/tmp/gl"
   adb -s <TV_IP>:5555 push ghostlock_root /data/local/tmp/gl/ghostlock_root
   adb -s <TV_IP>:5555 shell "chmod 755 /data/local/tmp/gl/ghostlock_root"
   ```

3. Launch the GhostLock root daemon wirelessly:
   ```bash
   adb -s <TV_IP>:5555 shell "nohup /data/local/tmp/gl/ghostlock_root > /data/local/tmp/gl/daemon.log 2>&1 &"
   ```

4. Execute root actions through the transient `su` socket:
   ```bash
   adb -s <TV_IP>:5555 shell "/data/local/tmp/gl/su -c '
       pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS
       cmd package compile -m speed -f com.gathin.tvhome
       pm disable com.amazon.tv.launcher/.ui.HomeActivity_vNext
       cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
       pm hide --user 0 com.amazon.firehomestarter
       pm hide --user 0 com.amazon.tv.forcedotaupdater.v2
       pm hide --user 0 com.amazon.device.software.ota
   '"
   ```

5. Reboot wirelessly:
   ```bash
   adb -s <TV_IP>:5555 reboot
   ```

---

## 4. Wireless Setup: Fire OS 6 (`mantis` / 4K 1st Gen)

Fire OS 6 uses Android 7.1.2 (API 25) with a 32-bit `armv7l` kernel (4.4.120+). Pre-built Fire OS 7 GhostLock binaries will fail on Fire OS 6 with:
```text
CANNOT LINK EXECUTABLE: cannot locate symbol "aligned_alloc"
```
because `aligned_alloc` was only introduced in Android API 28 libc.

### Compilation for Mantis (API 24)
To build a compatible root binary for Mantis:
1. Apply the Mantis memory corruption patch from `Delitants/firetv-mantis-standalone` to GhostLock branch `4.4`.
2. Compile using Android NDK targeting API 24 (`armv7a-linux-androideabi24-clang`) to ensure all symbols link against Android 7.x libc:
   ```bash
   $NDK/toolchains/llvm/prebuilt/*/bin/armv7a-linux-androideabi24-clang \
       -fPIE -pie -Wall -O2 src/*.c -o ghostlock_root
   ```
3. Use the matching device kernel profile (e.g. `mantis-NS6711-5908.conf`).

### Wireless Steps
1. Connect over Wi-Fi ADB:
   ```bash
   adb connect <TV_IP>:5555
   adb -s <TV_IP>:5555 wait-for-device
   ```

2. Install CleanYourTV:
   ```bash
   adb -s <TV_IP>:5555 install -r launcher/cleanyourtv-release.apk
   ```

3. Stage the exploit binary, kernel profile, and an action script:
   ```bash
   adb -s <TV_IP>:5555 shell "mkdir -p /data/local/tmp/gl_deploy"
   adb -s <TV_IP>:5555 push ghostlock_root /data/local/tmp/gl_deploy/ghostlock_root
   adb -s <TV_IP>:5555 push mantis-NS6711-5908.conf /data/local/tmp/gl_deploy/profile.conf
   ```

4. Create the execution script `/data/local/tmp/gl_deploy/action.sh`:
   ```bash
   adb -s <TV_IP>:5555 shell "cat << 'EOF' > /data/local/tmp/gl_deploy/action.sh
   #!/system/bin/sh
   pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS
   cmd package compile -m speed -f com.gathin.tvhome
   pm disable com.amazon.tv.launcher/.ui.HomeActivity_vNext
   cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
   pm hide --user 0 com.amazon.firehomestarter
   pm hide --user 0 com.amazon.tv.forcedotaupdater.v2
   pm hide --user 0 com.amazon.device.software.ota
   pm hide --user 0 com.amazon.device.software.ota.override
   reboot
   EOF
   chmod 755 /data/local/tmp/gl_deploy/ghostlock_root /data/local/tmp/gl_deploy/action.sh
   "
   ```

5. Trigger in-memory root execution wirelessly:
   ```bash
   adb -s <TV_IP>:5555 shell "nohup /data/local/tmp/gl_deploy/ghostlock_root --profile /data/local/tmp/gl_deploy/profile.conf --exec /data/local/tmp/gl_deploy/action.sh > /data/local/tmp/gl_deploy/exec.log 2>&1 &"
   ```
   GhostLock obtains kernel RW, zeroes the task UID to `root (0)`, runs `action.sh`, applies the changes, and initiates a clean reboot.

---

## 5. Boot Animation Watchdog Delay (60-second Fix)

### The Issue
By analyzing `/system/framework/oat/arm/fosservices.odex`, we identified that Fire OS's WindowManagerService (`FireTvWindowManagerServiceCallback$HomeInitialisationObserver`) listens for a signal on the secure setting:
```text
bootanim.real_home_ready
```
When Amazon's stock home screen finishes rendering, it sets this key to `1`, signaling `surfaceflinger` to exit the boot animation.

When `HomeActivity_vNext` is disabled, this signal is never received. Fire OS starts an internal **60-second safety fallback timer** (`BOOT_ANIMATION_TIMEOUT = 60000`):
```text
FireTvWmsCallback: Stopping boot animation because timer elapsed waiting for real home to send signal.
```
Even though the system finishes booting in ~28 seconds, the TV screen stays trapped on the looping "firetv" logo for 88+ seconds.

### The Fix in CleanYourTV
CleanYourTV now sets `bootanim.real_home_ready = 1` immediately inside `MainActivity.onCreate()` and `onResume()`:
```java
private void dismissFireTvBootAnimation() {
    try {
        android.provider.Settings.Secure.putInt(getContentResolver(), "bootanim.real_home_ready", 1);
    } catch (Throwable ignored) {}
}
```
Requires `android.permission.WRITE_SECURE_SETTINGS` (granted in step 4 above).

### Result
Upon launching CleanYourTV, Fire OS immediately records:
```text
FireTvWmsCallback: Stopping boot animation because real home sent signal.
```
- **Before fix:** `wm_boot_animation_done: 88239 ms` (~88.2 seconds)
- **After fix:** `wm_boot_animation_done: 30828 ms` (**~30.8 seconds**)
- **Improvement:** Boot animation dismissed **57.4 seconds faster** (~65% reduction).

---

## 6. Full Amazon Settings vs "About" Screen

On Fire OS:
- `com.amazon.tv.settings.v2/.tv.device.DeviceActivity` opens only the single-page "My Fire TV / About" subscreen.
- `com.amazon.tv.launcher/.ui.MainSettingsActivity` hosts the complete **14-category Settings Grid** (Network, Display & Audio, Applications, Remotes & Bluetooth, Preferences, Equipment Control, Alexa, Notifications, etc.).

By disabling only `com.amazon.tv.launcher/.ui.HomeActivity_vNext` (instead of the whole launcher package), `MainSettingsActivity` remains fully intact and functional. CleanYourTV prioritizes `com.amazon.tv.launcher/.ui.MainSettingsActivity` in `config.xml` (`settings_fallback_components`), ensuring the Settings pill opens the complete settings menu.

---

## 7. Verification and Rollback

### Verification
Check default Home resolver over Wi-Fi ADB:
```bash
adb -s <TV_IP>:5555 shell "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
```
Expected output:
```text
priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
com.gathin.tvhome/.MainActivity
```

Simulate physical Home button press:
```bash
adb -s <TV_IP>:5555 shell "input keyevent 3"
```
Focused window should immediately return:
```text
mCurrentFocus=Window{... u0 com.gathin.tvhome/com.gathin.tvhome.MainActivity}
```

### Rollback to Stock
To restore the stock Amazon launcher at any time:
```bash
adb -s <TV_IP>:5555 shell "pm enable com.amazon.tv.launcher/.ui.HomeActivity_vNext"
adb -s <TV_IP>:5555 shell "pm unhide --user 0 com.amazon.firehomestarter"
adb -s <TV_IP>:5555 shell "reboot"
```
