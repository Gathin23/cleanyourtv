# ZTE ZXV10 B866F V1.1 (Amlogic `oppen`, Android TV 14 EBS CFW)

## Device & Firmware Specifications

| Property | Value |
|---|---|
| **Brand / Model** | ZTE ZXV10 B866F V1.1 (`oppen`) |
| **SoC / ABI** | Amlogic (`armeabi-v7a`) |
| **OS / API Level** | Android TV 14 (SDK 34) |
| **Firmware Build** | `EBS.UKG3.250115.001` (Custom Firmware, Rooted via Magisk 29.0) |
| **Stock Launchers** | `com.google.android.tvlauncher`, `com.ebsproject.drawer` |
| **Wi-Fi Profile Owner** | `com.google.android.tungsten.setupwraith` (`cname` in `dumpsys wifi`) |

---

## Critical Caveat: ZTE `ControlDevice` Factory-Reset Watchdog & `PMSImpl` Home Override

Unlike stock Google TV or standard Android TV devices, the ZTE B866F firmware embeds two vendor anti-tamper layers that **will trigger an automatic factory reset (`/data` wipe) on reboot** if `com.google.android.tvlauncher` and `com.ebsproject.drawer` are disabled without mitigation:

### 1. `com.control.device.receiver.ZteKeyInterceptorReceiver` (`/system_ext/app/ControlDevice/ControlDevice.apk`)
- Runs under `sharedUserId="android.uid.system"`.
- Registers `com.control.device.receiver.ZteKeyInterceptorReceiver` (and a wrapper receiver `com.control.device.recevier.ZteKeyInterceptorReceiver`) listening for `android.intent.action.BOOT_COMPLETED`.
- Upon boot completion, it schedules a `TimerTask` (`com.control.device.receiver.A.run()`) that invokes `ZteKeyInterceptorReceiver.Q(Context)`:
  - `Q(Context)` calls `PackageManager.queryIntentActivities(Intent(ACTION_MAIN).addCategory(CATEGORY_HOME), 0)`.
  - It filters out `com.google.android.tungsten.setupwraith` and `com.android.tv.settings`, and checks whether any remaining enabled launcher has `ApplicationInfo.FLAG_SYSTEM` (`(flags & 1) != 0`).
  - If no enabled `FLAG_SYSTEM` launcher is found (because `com.google.android.tvlauncher` and `com.ebsproject.drawer` were disabled, and user-installed `com.gathin.tvhome` sits in `/data/app` without `FLAG_SYSTEM`), `Q(Context)` returns `null`.
  - `A.run()` logs `"[Broadcast] null is normal? false"` and immediately broadcasts:
    ```java
    Intent intent = new Intent("android.intent.action.FACTORY_RESET");
    intent.setPackage("android");
    intent.addFlags(268435456);
    intent.putExtra("android.intent.extra.REASON", "ResetButon");
    context.sendBroadcast(intent);
    ```
  - `MasterClearReceiver` in `system_server` receives this broadcast and reboots the STB into recovery to wipe `/data` (`boot.reason = reboot,factory_reset`).

### 2. `com.zte.framework.services.pm.PMSImpl.getBestActivity()` (`/system/framework/FrameworkCustomization.jar`)
- Injected into `system_server` via `android.content.FrameworkAdapter`.
- Blocks package-level `pm disable-user --user 0` for `com.control.device`, `com.ztestb.basecomp`, and `tv.upgrade.service` (returning `new state: default`).
- Overrides `HOME` intent resolution whenever `PMSImpl.isSetupComplete(Context)` is `true` (`Settings.Secure.tv_user_setup_complete == 1 && Settings.Secure.user_setup_complete == 1`), ignoring user-installed launchers in `PreferredActivities` and forcing `HOME` to resolve only to a `FLAG_SYSTEM` package (falling back to `com.android.tv.settings/.system.FallbackHome` if none is enabled).

---

## Step-by-Step Setup & Mitigation

Because the EBS CFW ships with Magisk root (`su` / `/debug_ramdisk/su`), both mechanisms can be cleanly neutralized before disabling the stock launchers.

### 1. Build and install CleanYourTV Launcher (keep stock launcher enabled first)

```bash
export TV_HOST=<tv ip>
STOCK_LAUNCHER=none launcher/build-and-install.sh
```

### 2. Apply the ZTE Anti-Tamper Mitigation (Automated 1-Command Script)

Run the included helper script, which:
1. Disables `com.control.device/.receiver.ZteKeyInterceptorReceiver` and `com.control.device/.recevier.ZteKeyInterceptorReceiver` at the component level via root (`pm disable` works on individual components even though `PMSImpl` blocks package-level disable).
2. Installs the systemless Magisk module ([`magisk-module/`](magisk-module/)) at `/data/adb/modules/cleanyourtv` so `com.gathin.tvhome` is bind-mounted into `/system/app/CleanYourTV/CleanYourTV.apk` and receives `FLAG_SYSTEM` on boot.
3. Installs `/data/adb/service.d/99-cleanyourtv-tweaks.sh` to enforce the watchdog disablement, keep `tv_user_setup_complete=0`, and re-assert `cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity` on every boot.

```bash
TV_HOST=<tv ip> examples/zte-b866f/setup-zte-mitigation.sh
```

### 3. Apply the Debloat List

Once the mitigation is active, disable the 18 bloatware packages (including `com.google.android.tvlauncher` and `com.ebsproject.drawer`):

```bash
TV_HOST=<tv ip> tools/disable.sh examples/zte-b866f/debloat.txt
```

Verify that all 18 packages remain disabled with zero drift:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/check.py examples/zte-b866f/debloat.txt
```

---

## Undo / Restore to Stock Launcher

To restore all disabled packages and return `HOME` to `com.google.android.tvlauncher`:

```bash
TV_HOST=<tv ip> tools/restore.sh examples/zte-b866f/debloat.txt
```

If you also want to remove the Magisk systemless module and re-enable the ZTE receivers after `com.google.android.tvlauncher` is re-enabled:

```bash
TV_HOST=<tv ip> tools/tv.py "su -c 'rm -rf /data/adb/modules/cleanyourtv /data/adb/service.d/99-cleanyourtv-tweaks.sh && pm enable com.control.device/.receiver.ZteKeyInterceptorReceiver && pm enable com.control.device/.recevier.ZteKeyInterceptorReceiver'"
```
