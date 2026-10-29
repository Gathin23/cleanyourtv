# NetTV SEI700NTV (SEI Robotics, Android 14): `pm disable*` is refused, Home taken via RoleManager and HomeKeyService

One-session report, cold-boot and reboot-checked.

## Identify the device

| Field | Value |
|---|---|
| Device type | Smart Set-Top Box (IPTV STB via WorldLink Nepal) |
| Retail name | NetTV (`ro.product.model` `NETTV`) |
| Maker | SEI Robotics (`ro.product.manufacturer`), device `SEI700NTV`, hardware `amlogic` |
| Android | 14 (SDK 34), build `UKG3.250826.001.8426` |
| Stock launcher | `np.com.geniussystems.android.atv.launcher` (`np.com.geniussystems.android.stb.launcher.MainActivity`, priority 3) |
| Live TV package | `np.com.geniussystems.android.atv.livetv` |
| Network | Wi-Fi (`wlan0`, RSSI -42 dBm); Wi-Fi owned by `android.uid.system:1000` |
| ADB | TCP port 5555 after one prompt; `ro.adb.secure=1` |

## `pm disable-user` is refused

As on the [waipu.tv Stick](waipu-tv-stick.md) and Nokia Streaming Box 8000, SEI Robotics / Amlogic firmware
refuses every `pm disable-user --user 0`, `pm disable` and `pm disable-until-used` with
`Warning! This command is illegal!`.

`/system/bin/pm` forwards commands to `cmd package "$@"`, and the refusal is enforced inside the vendor's
patched `PackageManagerService`.

What works to restrict background telemetry without uninstalling system components:
`cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore`, followed by `cmd appops write-settings`.

Applied for:
- `com.nes.bugreportsender` (vendor bug/crash telemetry)
- `com.nes.skywayclient` (SEI remote device management)
- `com.google.android.feedback` (Google feedback)
- `com.google.android.tvrecommendations` (Google TV sponsored/recommendation rows)
- `com.nes.leanbacklauncher.partnercustomizer`
- `com.google.android.tvsetup.partnercustomizer`

Revert with: `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND allow; cmd appops write-settings`.

Kept enabled:
`com.sei.hdmi.services` (HDMI inputs), `com.nes.blerc` (Bluetooth remote), `com.nes.tvglobalkeyhandler`,
`com.nes.remoteota.xihao`, `com.standard.irtvcontrol`, `com.dolby.android.audio.service`,
`np.com.geniussystems.android.atv.livetv` (NetTV Live TV), and `com.utsmta.app` (factory test installer,
shares UID 1000, left untouched).

## Home takeover

The stock operator launcher (`np.com.geniussystems.android.atv.launcher`) declares `android:priority="3"` in its
HOME intent-filter. Because `pm disable-user` is blocked, it cannot be disabled directly.

Takeover route:
1. **RoleManager:** Android 14 manages the default Home role via RoleManager:
   ```sh
   cmd role add-role-holder android.app.role.HOME com.gathin.tvhome
   cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
   ```
2. **`HomeKeyService` with `stock_home_redirect`:**
   In `launcher/app/src/main/res/values/config.xml`:
   ```xml
   <string name="stock_home_redirect">np.com.geniussystems.android.atv.launcher</string>
   <bool name="restore_key_services">true</bool>
   ```
3. Enable accessibility service and grant permissions:
   ```sh
   settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
   settings put secure accessibility_enabled 1
   pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS
   cmd notification allow_listener com.gathin.tvhome/.GuardRevival
   ```

**Cold boot test:**
On boot, `np.com.geniussystems.android.atv.launcher` briefly initializes its splash, and `HomeKeyService`
intercepts it, switching foreground focus to `com.gathin.tvhome/.MainActivity` in ~1.3 seconds.
Verified via `logcat -b events -d | grep wm_set_resumed_activity`.

## Remote button click sensitivity

The TV remote's standard button press duration (~400 ms over Bluetooth) caused Android's default
`long_press_timeout` (400 ms) to misread simple clicks as long-presses, opening system App Info.

Fixed by:
1. Increasing long-press threshold:
   ```sh
   settings put secure long_press_timeout 600
   ```
2. Enabling `tile_menu: true` in `config.xml`, so intentional long-presses open the glass reorder menu
   rather than system settings.

## Top bar and inputs

As an IPTV Set-Top Box without physical HDMI input ports, the Android TV Input Framework only
registers the operator Live TV stream (`np.com.geniussystems.android.atv.livetv`). In
`launcher/app/src/main/res/values/config.xml`:
- Set `<bool name="show_inputs_pill">false</bool>` to hide the redundant Inputs button.
- Set `<bool name="date_below_clock">true</bool>` to stack the date neatly below the clock when using full button text.

## Private DNS

AdGuard Private DNS works cleanly:
```sh
settings put global private_dns_specifier dns.adguard-dns.com
settings put global private_dns_mode hostname
```
Survives reboot. Undo: `settings put global private_dns_mode opportunistic`.
