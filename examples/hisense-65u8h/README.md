# Hisense 65U8H: the list, the Home handover and what was tested

The [debloat.txt](debloat.txt) beside this file is the tested list; the firmware behaviour behind it
is in [`docs/devices/hisense-65u8h.md`](../../docs/devices/hisense-65u8h.md). This page is the order
to apply it in, and the results after three weeks.

## Model

| Item | Value |
|---|---|
| Retail model | Hisense 65U8H, US |
| Android model / device | `SmartTV 4K FFM` / `wuyishan` |
| Board | MediaTek MT9900 (`mt5895`), `armeabi-v7a`, 2 GB RAM |
| Android | Google TV / Android 11, SDK 30 |
| Build | `RTT2.211209.001` / `00.00.00.56` |
| Stock Home | `com.google.android.apps.tv.launcherx/.home.HomeActivity` |
| Live TV and HDMI | `com.mediatek.wwtv.tvcenter/.LiveTVMainActivity` |
| Input menu | `com.vt.tv.input/.view.activity.MainActivity` |

`ro.product.model` is `SmartTV 4K FFM` on several Hisense boards in `examples/`; match on
`ro.product.device` (`wuyishan`) and the build id before applying this list.

## Before applying it

1. Back up the package list, disabled list, `getprop` and settings, as `PROMPT.md` says.
2. Forget and re-join the saved Wi-Fi network in Settings > Network & Internet. `setupwraith` owned it
   on this set, and disabling it before the re-join deletes the network and takes ADB with it. Check
   `dumpsys wifi | grep -o 'cname=[^ ]*'` names `android.uid.system:1000`, not `setupwraith`.
3. Apply the list with `FORCE_SETUPWRAITH=1`, which the check needs once the Wi-Fi is re-owned:

   ```
   TV_HOST=<tv ip> FORCE_SETUPWRAITH=1 tools/disable.sh examples/hisense-65u8h/debloat.txt
   ```

   `launcherx` stays enabled in this step, so Home keeps working. The six streaming apps are
   commented out because this set had them off; uncomment the ones you do not use before running.
4. Install the replacement launcher. It starts the launcher, confirms Home, then disables `launcherx`:

   ```
   TV_HOST=<tv ip> launcher/build-and-install.sh
   ```

5. Reboot twice and confirm Home and ADB come back.

The debloat list disables 20 packages by default: tracking and PAI, retail and first-run modes,
preloads, the three voice assistants, DLNA and `setupwraith`. Six streaming apps are commented out
because this set had them off; uncomment the ones you do not use. What it keeps, and why, is at the
bottom of the list.

## Undo

```
TV_HOST=<tv ip> tools/restore.sh examples/hisense-65u8h/debloat.txt com.google.android.apps.tv.launcherx
```

That re-enables the 20 packages and the stock launcher. It does not uninstall the replacement
launcher; set `launcherx` as Home again or uninstall `com.gathin.tvhome` yourself. The six streaming
apps are commented out, so restore leaves them as they are.

## Validation

Applied on **2026-11-02**, rebooted twice, and lived with until **2026-11-21**. The tested set had 26
packages off, including the six streaming apps; the shared list ships 20 active and those six
commented out.

| Feature | Result |
|---|---|
| Home after two reboots | Working, `com.gathin.tvhome/.MainActivity` |
| Physical remote, held volume | Working; no key helper, which cost the volume ramp |
| Input button | Opens the launcher's Inputs menu through `com.vt.tv.input` |
| Wi-Fi and ADB | Working after the network was forgotten and re-joined |
| Native AirPlay | Working, tested from an Apple device |
| Chromecast, Miracast, Google TV screensaver | Working |
| YouTube TV, Twitch, Jellyfin | Working |
| HDMI signal playback, CEC | Not tested beyond the Inputs menu listing the ports |
| Casting from a phone | Not tested |
| Full restore | Not tested |
