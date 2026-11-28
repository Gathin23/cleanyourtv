# Xiaomi TV Box S 3rd gen (MiTV-AFMU0, twilight), second unit

Tested with `PROMPT.md` and `examples/xiaomi-mitv-afmu0/debloat.txt` on 2026-10-27.
Model: `MiTV-AFMU0`, codename `twilight`, Amlogic, Google TV on Android 14 (SDK 34).
Firmware: `UKG3.250826.001 / V816.0.11.0.UZFAABX` (HyperOS), 2 GB RAM, `ro.adb.secure=1`.

## What was verified

- **Debloat:** Disabled 24 packages in batches (telemetry, PatchWall, promos, unused preloaded streaming apps).
- **Kept by choice:** Google Play Store, Google Assistant and Bluetooth remote microphone (`com.google.android.katniss`, `com.google.android.tv.remote.service`), Chromecast (`com.google.android.apps.mediashell`), Miracast and Mi share (`com.xiaomi.mitv.smartshare`, `com.xiaomi.wfdsinkhelperservice`, `com.xiaomi.mi_connect_service`), YouTube Music (`com.google.android.youtube.tvmusic`), YouTube (`com.google.android.youtube.tv`), Netflix (`com.netflix.ninja`) and TrueID TV (`com.truedmp.idtv`). The list itself disables Miracast and Mi share, YouTube Music and the Xiaomi USB media players (`com.mitv.gallery`, `com.mitv.videoplayer`, `com.xiaomi.mitv.mediaexplorer`), so comment out the lines for any of these you want to keep before applying it.
- **Wi-Fi handover from setupwraith:** During initial wizard setup, Wi-Fi is owned by `com.google.android.tungsten.setupwraith`. Disabling setupwraith while it owns Wi-Fi will cause Android 14 to delete the network and drop ADB. On the TV, forgetting the network and re-joining it in Settings switches ownership to `android.uid.system:1000`. Once owned by system, setupwraith can be disabled safely.
- **Launcher handover:** Built reference launcher (`com.gathin.tvhome`) with platform 34 and minSdk 26. `setupwraith` disabled first, then `com.google.android.apps.tv.launcherx` disabled, and `com.gathin.tvhome/.MainActivity` set as Home activity.

## Remote buttons and key mapping

The Bluetooth remote ("Xiaomi RC", `Vendor_2717_Product_32b9.kl`) behaves as follows once the stock launcher is disabled:

- **Home, Back, D-pad, and Mic:** Work out of the box (Google Assistant kept).
- **Netflix:** Sends `BUTTON_6` (scancode 240 / 416) and starts Netflix directly.
- **YouTube:** Sends `BUTTON_3` (scancode 425), which only the stock launcher answered.
- **Prime Video:** Sends `BUTTON_7` (scancode 207), which only the stock launcher answered.
- **Apps button (bottom right):** Sends `ALL_APPS` (scancode 216).

Enabling `HomeKeyService` (`settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService` and `settings put secure accessibility_enabled 1`) restores all three buttons via `examples/xiaomi-mitv-afmu0/theme/config.json`:
- `ALL_APPS` is caught by `HomeKeyService` and opens Home.
- `app_keys` maps `scan:425` and `KEYCODE_BUTTON_3` to `com.google.android.youtube.tv`.
- `app_keys` maps `scan:207` and `KEYCODE_BUTTON_7` to `com.truedmp.idtv` (TrueID TV).

The folder also holds `config.json` at its root, from another unit on the same firmware: that one points the same
Prime Video button at Prime Video itself, keeps Prime Video in the dock and works the Apps button through an
`ALL_APPS` entry. Push whichever mapping you want - `examples/xiaomi-mitv-afmu0/theme` for the TrueID TV one above,
`examples/xiaomi-mitv-afmu0` for the Prime Video one.

## Third unit: Prime Video works without a mapping

Tested with `PROMPT.md` and this folder's `debloat.txt` on 2026-11-27, one evening and one reboot, on the same
firmware (`V816.0.11.0.UZFAABX`). The header of `debloat.txt` has the details; in short:

| Button | Sends | With launcherx off |
| --- | --- | --- |
| Netflix | `BUTTON_6` (scancode 416) | Opens Netflix, no mapping needed |
| Prime Video | `BUTTON_7` (scancode 207) | Opens Prime Video, no mapping needed (`partnercustomizer` sends `AMAZON_BUTTON`) |
| YouTube | `BUTTON_3` (scancode 425) | Nothing; HomeKeyService with `KEYCODE_BUTTON_3` and `scan:425` app_keys brings it back |
| Apps | `ALL_APPS` (scancode 216) | Nothing; HomeKeyService with `KEYCODE_ALL_APPS:home` makes it open Home |

So on this unit only YouTube and Apps needed `app_keys`; a `BUTTON_7` mapping was not needed. Held D-pad keys
kept repeating with HomeKeyService on. Port 5555 kept answering after Wireless debugging was off, across a
reboot, while USB debugging was on: switch both off when you are done.

## Private DNS (AdGuard DoT)
Private DNS (such as AdGuard DoT) works on this set.

## Usage & diagnostics after the debloat

On another unit, same firmware (`V816.0.11.0.UZFAABX`), November 2026: the Settings → Privacy → Usage &
diagnostics page comes from setupwraith, which this list disables for the launcher swap. With it off the page
shows a spinner that never ends, and logcat has
`Failed to find provider info for com.google.android.settings.usage`. Flip it before the debloat if you can.

Afterwards it still works, in the order the header of [`debloat.txt`](debloat.txt) gives for re-pairing the
remote: the stock launcher first, one package per call, and no reboot until the end.

```sh
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm enable com.google.android.apps.tv.launcherx'
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm enable com.google.android.tungsten.setupwraith'
# on the TV: Settings → Privacy → Usage & diagnostics → off
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'dumpsys wifi' | grep -o 'cname=[^ ]*'
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm disable-user --user 0 com.google.android.tungsten.setupwraith'
TV_HOST=<tv ip> launcher/build-and-install.sh
```

Enabling the stock launcher first sends Home to it (HOME priority 2) rather than to setupwraith's
RecoveryActivity (priority 1) while the wizard is on. Disable setupwraith again only if no `cname=` line
names it: a saved Wi-Fi network it owns is deleted with it. If one does, forget and re-join that network in
Settings first. Do not hand Home back while setupwraith is still enabled. `launcher/build-and-install.sh` then
returns Home to the new launcher and disables the stock one; pass it the same `HOME_KEY_SERVICE` /
`INPUT_KEY_SERVICE` as the first time, and `SKIP_BUILD=1` reuses the APK already built. Tested once on this
firmware: the disabled packages were the same as before, and Home and Wi-Fi survived a reboot.

## YouTube kept playing with the TV off

On another unit, same firmware, tried in November 2026: after the TV was turned off, YouTube played on for
hours and the box was slow at the next start. `attentive_timeout` was `-1` after a factory reset, so nothing
stopped playback. `settings put secure attentive_timeout 21600000` is the "when inactive" setting at 6 hours,
as a safety net: Android warns before it sleeps and any key cancels. With HDMI-CEC on, the power key on the
remote put both the TV and the box to sleep (`mLastSleepReason=hdmi`). The 6-hour timer itself was set but
not watched to the end. To undo it: `settings put secure attentive_timeout -1`.

## Volume and power do nothing on a monitor

On another unit, same firmware (`V816.0.11.0.UZFAABX`), November 2026, on an LG 32UP83AK-W: a 4K monitor
rather than a TV. The monitor has no infrared receiver and does not answer HDMI-CEC: every message the box sent
it went unacknowledged (`dumpsys hdmi_control`). Plain Volume on the remote did nothing while watching, and the
owner found Power did nothing either. Holding Home and pressing Volume changed the box's own volume.

Here the remote sent plain Volume as infrared: `getevent -lt` on the box showed nothing for a plain Volume
press, and holding Home made the remote send `KEY_VOLUMEUP` / `KEY_VOLUMEDOWN` over Bluetooth instead. Settings
→ Remotes & Accessories → Set up remote buttons reported the remote as set up, but the owner found Volume and
Power still did nothing on the monitor.

The fix is in the same place: Settings → Remotes & Accessories → Set up remote buttons → **Reset** →
**Clear** ("Clear saved remotes?"). After that `getevent` showed plain Volume presses arriving over
Bluetooth, and the box changed its own output level (its volume bar, `STREAM_MUSIC` 0 to 15), during playback
too. The owner confirmed Power then sleeps and wakes the box. Set the monitor's own speaker level once from its
menu. Running "Set up remote buttons" again would most likely program the infrared codes back (not tried).
