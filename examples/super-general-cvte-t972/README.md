# Super General (CVTE TP.SK708D.PC821, Android 9): notes

Done remotely on one set. Debloat from `debloat.txt`; Home is a third-party launcher,
[Afnon](https://github.com/egyjs/afnon-android-tv-launcher), not the reference launcher.

## 1. Turn on network ADB

There is no Developer options screen. ADB is in CVTE's factory menu:

1. Switch to a source first (TV or HDMI). The factory menu does not open from the launcher.
2. On the remote press **MENU**, then **1 1 4 7** quickly. The Factory Menu opens.
3. **DEBUG → ADB ENABLE → ON.**
4. Leave the menu with Back. Touch nothing else there: some entries reset the TV to factory settings.
5. From the computer: `adb connect <tv ip>:5555`.

Network ADB stays on across reboots (`persist.service.adb.enable=2`) until it is switched off in the same menu.
The firmware is `userdebug`, so `run-as` and most `settings put` calls work.

Never launch the factory packages (`com.cvte.fac.menu`, `com.cvte.factory.*`) over ADB; they are kept, not disabled.

## 2. Remote access (optional)

The set was done from another country over [Tailscale](https://tailscale.com) (installs from Google Play on
Android 9), then `adb connect <tailscale ip>:5555`.

- Make it the always-on VPN so it comes back after a reboot:
  `adb shell settings put secure always_on_vpn_app com.tailscale.ipn` (undo: `settings delete secure always_on_vpn_app`).
  Reboot-checked: Tailscale and network ADB were back about a minute after boot.
- Never disable or force-stop it for good if it is your only way in.
- [scrcpy](https://github.com/Genymobile/scrcpy) (v5.0.1) shows and controls the screen over the same ADB link.
  AnyDesk installs but can only view: this firmware has no AnyDesk control plugin.

## 3. Debloat

`TV_HOST=<tv ip> tools/disable.sh examples/super-general-cvte-t972/debloat.txt`, then:

- **Google Play Services ships disabled by the system** (`enabled=3`), so Google Play does nothing:
  `adb shell pm enable com.google.android.gms`. It stayed on across a reboot.
- Check RAM afterwards with `adb shell dumpsys meminfo | head -40` (low_ram device, ~1 GB).

## 4. Launcher

- A FallbackHome exists on this build, so disabling the stock launcher (`com.cvte.tv.launcher.highend` and
  `.skin`) after the new Home is set is safe. Undo with
  `TV_HOST=<tv ip> tools/restore.sh examples/super-general-cvte-t972/debloat.txt com.cvte.tv.launcher.highend`.
- System language: Settings → Language (Arabic worked; RTL launchers follow it).
- **Wi-Fi name**: Android 9 hides the network name from apps without location. A launcher that shows it needs
  `adb shell pm grant <launcher package> android.permission.ACCESS_FINE_LOCATION`.

## 5. Remote buttons

The app buttons are wired in CVTE's hotkey service (`com.cvte.tv.api.impl`), which reads the read-only
`/vendor/menu_config_xml/hotkey.xml`. They work from any app and need no accessibility service:

| Button | Keycode | Opens |
|---|---|---|
| SETTING | 1110 | `com.cvte.settings` |
| YouTube | 1111 | YouTube |
| NFX | 1112 | `com.netflix.mediaclient`, else `com.stark.store` |
| ESHARE | 1113 | `com.ecloud.eshare.server` |
| SOURCE | `KEYCODE_TV_INPUT` | nothing: the firmware passes it to the app in front |

- **SOURCE**: the stock launcher opened `com.cvte.tv.globalui/.InputSourceActivity`. A replacement Home must do
  the same, or the button does nothing on the home screen. Inside other apps press Home first.
- **Holding Volume**: an accessibility service that filters keys (`FLAG_REQUEST_FILTER_KEY_EVENTS`) makes Android
  re-inject them, and injected keys never repeat, so Volume moves one step per hold. On this firmware the hotkey
  service already answers the buttons, so a launcher's key filter is not needed; leave it off.
- **Testing**: `adb shell input keyevent 1112` reaches the hotkey service, so the app buttons can be tested over ADB.
- **NFX without Netflix**: the button is hard-wired to the package name. Uninstalling Netflix (a normal app here) and
  installing a tiny app with the same package name whose only activity opens another app repoints the button.
- **ESHARE**: EShare is a CVTE-signed system app, so it can't be replaced that way. A launcher's accessibility
  service can watch for EShare's window and open another app instead (Afnon's `redirects` preference does this).

## 6. Casting

- EShare (v6.4.17, 2020) is a system app and can't be updated; newer EShare phone apps stop connecting to it.
- The built-in Miracast receiver (`com.droidlogic.miracast`) works from an Android phone's Cast / Smart View tile.
- Its name comes from `settings put global wifi_p2p_device_name "<name>"` and shows only after a reboot. Set
  `settings put global device_name` and `settings put secure bluetooth_name` to the same name.

## Troubleshooting

| Problem | Cause | Fix |
|---|---|---|
| Factory menu doesn't open | MENU 1147 pressed on the launcher, or too slowly | Switch to TV/HDMI first, then MENU 1 1 4 7 quickly |
| Google Play does nothing / won't open | Play Services disabled by the firmware | `pm enable com.google.android.gms` |
| Volume moves one step per hold | Launcher's accessibility service filters keys | Turn its key filter off (see Remote buttons) |
| SOURCE does nothing | Firmware leaves it to the app in front | Press Home first; the Home app must open `InputSourceActivity` |
| NFX does nothing | It opens Netflix (or the Stark store) and that is disabled | Re-enable it, or install a same-package stand-in |
| Launcher doesn't show the Wi-Fi name | No location permission (Android 9) | `pm grant <pkg> android.permission.ACCESS_FINE_LOCATION` |
| YouTube "loading forever", Play Store `ERR_NAME_NOT_RESOLVED`, after a reboot | Tailscale's DNS (100.100.100.100) stopped forwarding; plain IP still works | Restart Tailscale: `am force-stop com.tailscale.ipn`, then open it. Turning off "Use Tailscale DNS" left Android 9 with no DNS at all. Setting a nameserver in the Tailscale admin console may fix it for good (untested) |
| Private DNS (e.g. AdGuard) has no effect | Tailscale sends all DNS to its own resolver while it is up | Use Tailscale's DNS settings instead |
| A launcher's accessibility service stops working after `pm install -r` then `am force-stop` | Android 9 leaves the binding dead (`dumpsys activity services <pkg>` shows it) | Reboot. Next time don't force-stop right after installing |
| After an update the accessibility service runs with old settings (e.g. key filter still on) | Android 9 keeps the old service capabilities | `settings put secure enabled_accessibility_services com.example.none/.X`, wait a second, then set it back to the real service |
| `adb install` / `install-multiple` dies halfway over a slow link | The stream is dropped | `adb push` the APKs to `/data/local/tmp` (retry each), then `pm install-create -r -S <total>`, `pm install-write -S <size> <id> <n> <path>` per APK, `pm install-commit <id>` |
| An app from Play/APK sites won't install | 32-bit only (armeabi-v7a), Android 9 | Pick a build with min Android 9 (API 28) or lower and an `armeabi-v7a` split; many 2026 app versions need Android 12 |
| `adb exec-out screencap` hangs | Slow link | `screencap -p /data/local/tmp/s.png`, then `adb pull` |
| AnyDesk shows the screen but can't control it | No AnyDesk OEM plugin on this firmware | Use scrcpy over ADB |
