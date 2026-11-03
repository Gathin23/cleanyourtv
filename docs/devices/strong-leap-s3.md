# Strong LEAP-S3: a Google TV box, a two-day list, and the order that avoids a black screen

The `Strong LEAP-S3` is a set-top box (the box and the label under it say "STRONG LEAP S3"). Android calls
it `4K Google TV Box`, made by SkyworthDigital on an Amlogic S905Y4. It is the same Skyworth firmware family
as [`wanbo-x5-pro`](../../examples/wanbo-x5-pro/debloat.txt) and
[`byintek-t5-max`](../../examples/byintek-t5-max/debloat.txt), but a box (device `YDA`) on an older build
(`C3.0.12`, where those two are `C3.3.5`).

The owner followed [`PROMPT.md`](../../PROMPT.md) on Windows 11 and applied the list by hand with
`pm disable-user`, not through `tools/disable.sh`, and the first day of that is what this note records. The list
itself has since moved to [`examples/strong-leap-s3/debloat.txt`](../../examples/strong-leap-s3/debloat.txt), lived
with for over a week with several reboots on the same firmware - the few days
[`CONTRIBUTING.md`](../../CONTRIBUTING.md) asks for - so apply it from there and read this page for what the list
cannot say. What carries to another unit is the ordering, the quirks and the remote buttons below.

## Identify the firmware

`tools/tv.py --probe` on the unit:

| Field | Observed value |
|---|---|
| Retail model | `STRONG LEAP S3` (box and label) |
| `ro.product.model` | `4K Google TV Box` |
| `ro.product.device` | `YDA` |
| `ro.product.manufacturer` | `SkyworthDigital` |
| Product / board / platform | `HP4624gb-STRONG` / `HP46AY4` / `HP46A`, Amlogic S905Y4 |
| Android / SDK | 14 / 34, Google TV |
| `ro.build.display.id` | `UKG3.260128.001.C3.0.12_20260904` |
| Usable RAM / userspace | about 1.9 GB, 32-bit (`armeabi-v7a`) |
| `ro.adb.secure` | `1` |

`/oem/oem.prop` also says `ro.mecool.device.model=KM7PLUS`, so the board looks shared with a Mecool KM7 Plus
(not confirmed). The stock launcher is `com.google.android.apps.tv.launcherx`. It is a box: no Live TV app and
no HDMI inputs (`dumpsys tv_input` shows an empty `mHardwareList`), so the Inputs pill has nothing to switch.

## The list as applied

The list after that first day of use and one reboot; it is the same one that ships in
[`examples/strong-leap-s3/debloat.txt`](../../examples/strong-leap-s3/debloat.txt) after a week. Read it as a
starting point and comment out what you need.

```text
# Privacy Sandbox / ads framework
com.android.adservices.api
com.android.ondevicepersonalization.services
com.android.federatedcompute.services

# Vendor test, QA and feedback
com.oem.qa
com.sdt.producttest
com.amlogic.voicesubtitle              # no visible effect when disabled
com.android.tv.feedbackconsent

# Google extras
com.google.android.play.games

# Only to let a replacement launcher take Home (see below). Disabling it deletes the Wi-Fi network
# saved during setup on this unit.
com.google.android.tungsten.setupwraith
com.google.android.apps.tv.launcherx   # after the replacement is Home and survived a reboot
```

Off by the owner's choice, not part of the list: Assistant (`com.google.android.katniss`), Cast receiver
(`com.google.android.apps.mediashell`) and the official YouTube app (replaced by a sideloaded one).
Uninstalled for user 0 (this repo never lists uninstalls): YouTube Music.

## HOME and Wi-Fi: the order that worked, and one failure that was not explained

`launcherx` declares HOME at priority 2 and `com.google.android.tungsten.setupwraith/.RecoveryActivity` at
priority 1; a replacement launcher has priority 0. After disabling `launcherx` alone, Home opened
`RecoveryActivity` even with the replacement set as HOME role holder
(`cmd role add-role-holder --user 0 android.app.role.HOME <pkg>`) and `set-home-activity`. The replacement
took Home only after `setupwraith` was disabled too.

Disabling `setupwraith` dropped the box off Wi-Fi both times (the network saved during setup belonged to it),
and ADB went offline with it. Re-joining the network in Settings brought it back. Other Google TV profiles
say to forget and re-join the network **before** disabling it, and `tools/disable.sh` guards for that.

**One failure was not explained.** On the first attempt both packages were disabled, then the box was
rebooted straight away: the HDMI signal stayed, the screen was black and the remote did nothing. The box
has no reset button; it was recovered with a hardware reset from a pad on the board. The cause was not
identified. The second time this order was followed, with a reboot test that passed:

1. Pair Wireless debugging (`adb pair`) so ADB can be reached after a reboot.
2. Disable `setupwraith` while `launcherx` is still enabled; re-join Wi-Fi from the stock Home.
3. Set the replacement as HOME role holder, then disable `launcherx`, press Home, check.
4. Reboot and check it comes back.

With a box that has no reset button, the pairing step and not disabling both packages in one go are what
make a mistake recoverable.

## Remote buttons

The remote is Bluetooth (vendor `0x0957`, keylayout `Vendor_0957_Product_000f.kl`):

| Button | Keycode |
|---|---|
| YouTube | `KEYCODE_STEM_2` (266) |
| Netflix | `KEYCODE_STEM_1` |
| Prime Video | `F8` |
| Disney+ | `F6` |
| Gear (Settings) | `KEYCODE_NOTIFICATION` (83) |
| Assistant | `ASSIST` |

The gear button stops working once `launcherx` is disabled. With the reference launcher it is fixed in
`config.xml` (the launcher was built with these values and the real buttons checked after a reboot):

```xml
<integer-array name="settings_key_keycodes"><item>83</item></integer-array>
<string-array name="app_keys" translatable="false"><item>266:<your YouTube replacement package></item></string-array>
```

with `HomeKeyService` enabled. A key injected with `adb shell input keyevent` does not reach an accessibility
service, so test with the real button.

## Smaller notes

- **ADB.** USB debugging opens port 5555 and it survived a reboot; the first `adb connect` asks for
  confirmation on the TV. Wireless debugging with a pairing code also works. `adb install` may hang on
  Android 14, so push the APK and run `pm install -r`.
- **Screenshots** of DRM windows (Netflix) come back empty; check `topResumedActivity` instead.
- **Private DNS** (`dns.adguard-dns.com`, mode `hostname`) validated after 15 to 60 s on this network, but
  names stopped resolving in two earlier attempts. Check `ping -c1 www.google.com` after every change.
- **`restore.sh`.** With the reference launcher already Home, restore re-enables the stock launcher and sets
  it as Home again, as designed. Pass `com.google.android.apps.tv.launcherx` as the stock launcher.

## What was kept, and why

- `com.sdt.globalkey`, `com.sdt.vendor`: remote app-button receivers.
- `com.droidlogic.*`: Amlogic vendor services (remote pairing, HDMI-CEC, picture). `com.mft.rcupair`: remote
  pairing. `com.mft.frontpanelledsservice`.
- `com.sdt.ota`, `com.sdt.upgrade`, `com.google.android.tv.dfuservice`: firmware and remote updates.
- `com.google.android.tv.remote.service` (phone remote), `com.google.android.apps.tv.dreamx` (screensaver),
  Play Store and Play Services.
- `com.sdt.deviceinfo` (v1.0.4): reads HDMI and EDID information from `/sys/class/amhdmitx` and `hdmirx` and
  exports a content provider, a service and an activity, so other vendor apps may read it. No URLs in its
  code. About 8 MB PSS; ps shows a larger RSS because the pages are shared.
- `com.sdt.customization`: purpose unclear.

The Wanbo and Byintek lists disable `com.oemservice` ("xcpe", 1.2.1). On this build its APK is 21 KB and its
whole code is a `BOOT_COMPLETED` receiver that tries to install APKs it finds in `/oem` through
`PackageInstaller`, plus an `UpgradeService`. The dex has no network classes or URLs even though the
manifest declares `INTERNET`, `MASTER_CLEAR` and `WRITE_SECURE_SETTINGS`. `/oem` holds no APK here, so it
only logs `Failed to open APK '/oem/oem.prop'` once a boot. It looks safe to disable (about 6 MB PSS);
it was left enabled.

## Limits

- This note covers one unit, about a day and a half and two reboots. A second unit is still untested.
- The list was applied by hand, not through `tools/disable.sh`. That script and `tools/recovery.py` were
  run on two of the packages (`com.oem.qa`, `com.sdt.producttest`) and worked; `restore.sh` and the full
  list were not run through them.
- Nothing here says another `YDA` box has the same firmware: check `ro.build.display.id` first.
- What is still open: what comes back after the next firmware update - the list has been lived with, but no
  update has landed under it yet.
