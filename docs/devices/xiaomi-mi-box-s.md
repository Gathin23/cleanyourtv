# Xiaomi Mi Box S (MIBOX4), second unit

A second Mi Box S, done with `PROMPT.md` and `examples/xiaomi-mi-box-s/debloat.txt` on 2026-10-21.
Same build as the first unit: `Xiaomi/oneday/oneday:9/PI/3933:user/release-keys`, security patch
2021-07-05, SDK 28, 2 GB RAM, 1920x1080, `ro.adb.secure=1`. Done with `tools/tv.py` (no adb binary)
and the `.ps1` scripts.

## What matched the list

Every package in the list was on this unit and could be disabled with `pm disable-user --user 0`. Disabled
in three batches with screenshots in between (telemetry; PatchWall, rows and updater; casting and
preloads), then the launcher, then Backdrop after `screensaver_components` was set to `ClockDream`.
Nothing broke between batches.

- Home: `com.google.android.tvlauncher` before, as on the first unit. The only other HOME claimant was
  `com.android.tv.settings/.system.FallbackHome`; no setupwraith claim.
- `launcher/build-and-install.ps1` worked first time: `set-home-activity` with the component, the stock
  launcher disabled, `mResumedActivity` on `com.gathin.tvhome/.MainActivity`.
- After a reboot the same 25 packages were disabled: nothing is re-enabled at boot. Home and Wi-Fi came
  back by themselves. `MemAvailable` was 1.08 GB before (box running for a while) and 1.23 GB right after
  the reboot.
- The Wi-Fi pill says only "Wifi": Location is off, as the list header says.
- USB debugging alone opened port 5555 (`service.adb.tcp.port=5555`, `sys.usb.config=adb`).

## Where this unit differed

- **Nothing was disabled before we started** (`pm list packages -d` was empty). The five apps the list
  keeps commented out as "already disabled on this unit" (Prime Video, YouTube Music, Play Movies, Play
  Games, Camera) were all enabled here; they were disabled by choice.
- **Google Play Music** (`com.google.android.music`) was installed and enabled. The service closed in
  2020; disabled with no side effects.
- **The saved Wi-Fi did not belong to setupwraith**: `dumpsys wifi | grep -o 'cname=[^ ]*'` showed
  `android.uid.system:1000` and `com.google.uid.shared:10011` only. setupwraith was not needed for the
  takeover on this Android TV 9 build and was left alone.
- **The remote's Apps button can be made useful.** It sends scancode 580 (`KEY_APPSELECT`), which
  `Vendor_2717_Product_32b9.kl` maps to `ALL_APPS`. Android sends that to `com.android.tv.settings`,
  which does nothing with it (the first unit saw the same, also on the stock launcher). The launcher's
  `HomeKeyService` catches `KEYCODE_ALL_APPS`, so enabling it (`-HomeKeyService` on
  `launcher/build-and-install.ps1`) turns the Apps button into a second Home button. Checked with the
  real remote from inside VLC: the press started `com.gathin.tvhome/.MainActivity`.
- The other buttons, checked with the real remote and `getevent -lt`: Home (`KEY_HOMEPAGE`), Back,
  voice (`KEY_VOICECOMMAND`, Assistant kept) and Netflix (`KEY_VIDEO`, scancode 393 is `NETFLIX` in
  the same `.kl`) all work with the stock launcher disabled.

An empty `live_tv_package` (no tuner) can be pushed as a theme `config.json` with `tools/theme.ps1`,
so `config.xml` stays at its defaults.

## Building on Windows without Android Studio

`launcher/build-and-install.ps1` looks for a JDK only in `JAVA_HOME` or Android Studio's `jbr`. A
Temurin JDK 17 zip plus the Android command-line tools (`sdkmanager "platforms;android-34"
"build-tools;34.0.0"`, SDK in `%LOCALAPPDATA%\Android\Sdk`) worked with `JAVA_HOME` set to the JDK.

Lived with: one day so far; still to soak for a few days as CONTRIBUTING.md asks.
