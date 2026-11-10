# TCL 65C6K (G08, Google TV 14)

The list and its header are for the first unit, a 65C6K from Latin America, debloat only. This file adds the units
reported since, with what they did differently.

## Second unit: 55C6K, Vietnam, Wi-Fi, launcher replaced

TCL 55C6K (QD-Mini LED, 55"), model "Smart TV Pro", device `G08`, product `G08_4K_GB`, MediaTek `mt5896`, the same
`UTT2.250416.001 AU02`, vi-VN, on Wi-Fi. Set up on 31 October 2026 in four batches with screenshots between them,
then the reference launcher; rebooted once afterwards: every package stayed disabled, nothing re-enabled itself,
and the TV came up on the new Home (`FallbackHome`, then `com.gathin.tvhome`), not Live TV. One day of use, not
weeks.

**Different choices from the first unit:** YouTube Music (`com.google.android.youtube.tvmusic`), Assistant
(`com.google.android.katniss`) and the soundbar updater (`com.tcl.UpdatePeripheral`) kept, so those three lines
were commented out; Prime Video (`com.amazon.amazonvideo.livingroom`) disabled, its "Kept on this unit" line
uncommented. Chromecast (casting stops once setupwraith is off), AirPlay, Miracast, T-Cast, the game bar, the USB media player and gallery, Netflix and
YouTube kept, as on the first unit. The local apps (FPT Play, VTVcab Prime, TV360, VTV Go) were installed by the
owner and are not in the list.

**Launcher handover.** As on the first unit, the saved Wi-Fi belonged to setupwraith
(`dumpsys wifi | grep -o 'cname=[^ ]*'`). The first run of `launcher/build-and-install.sh` disabled launcherx, found
Home on setupwraith's `.RecoveryActivity` and put launcherx back by itself. Forgetting the network and joining it
again in Settings > Network & Internet moved it to `com.android.tv.settings`; then setupwraith was disabled with
`tools/disable.sh` (Wi-Fi and ADB stayed up) and the second run took Home, with launcherx disabled. With setupwraith off,
casting to this TV stops working; enabling it again brings casting back.

What was checked on the TV: Home and the Inputs menu (HDMI 1-4 and Live TV, `live_tv_package` `com.tcl.tv`) after
the handover; after the reboot, the owner pressed the real Input, Settings, app and microphone buttons and all
worked with the stock launcher disabled and **no key helper enabled**, and holding Volume Up still ramped. That
differs from the C7K units in `examples/tcl-55c7k-uk/README.md`, which needed InputKeyService for the app
buttons; it was one owner's check on one remote. YouTube still loaded with AdGuard Private DNS. The Weather add-on
and the scenes and cinematic themes ran on this unit too (the cinematic theme's `dock_apps` replaced by the owner's).
Wireless debugging was off throughout; after USB debugging was switched off and the authorisations revoked,
`tools/adb-closed.py` reported port 5555 closed, and again after a restart.

## Third unit: 55C6K, India, Onkyo 2.1, launcher replaced & buttons remapped

TCL 55C6K (QD-Mini LED, 55"), model "Smart TV Pro", device `G08`, product `G08_4K_GB`, MediaTek `mt5896` (MT9689),
the same `UTT2.250416.001 AU02`, en-IN / `Asia/Kolkata`, on Wi-Fi. Set up in October 2026 with the reference launcher,
25 bloat packages disabled from the list, and AdGuard Private DNS (`dns.adguard-dns.com`). Regional streaming apps
(JioCinema, Disney+ Hotstar, Sony LIV) and Moonlight / Stremio were kept and used daily.

**Onkyo 2.1 built-in sound system:**
This set has an integrated Onkyo 2.1 speaker system. Audio packages checked with `dumpsys package` that must stay
enabled:
- `com.mediatek.speakerservice` (built-in speaker subsystem)
- `com.dolby.android.audio.service` and `com.dolby.android.audio.calibration` (Dolby Atmos processing)
- `com.tcl.hearaid` (sound and accessibility)
`com.tcl.UpdatePeripheral` (firmware updater for external TCL soundbars) was disabled, with no impact on the internal
Onkyo 2.1 speakers.

**Remote buttons and remapping:**
TCL's `TclKeyLayoutMap` passes specific branded remote buttons through to the foreground app before dispatching,
allowing them to be caught and remapped by an accessibility helper (tested with Key Mapper 4.6.1):
- **TCL Channel button:** sends Android keycode `4020` (remapped to Stremio).
- **CH button:** sends Android keycode `4001` (remapped to Moonlight).
- **Input button:** sends `KEYCODE_TV_INPUT`, which TCL firmware routes to `com.tcl.suspension` before accessibility
  services see it; cannot be redirected.

To let a key remapper start at boot on Android 14 TCL G08 firmware:
- `appops set <package> AUTO_START allow; appops write-settings` (`TclAppBoot` blocks auto-start unless `AUTO_START` is allowed)

**Launcher and Wi-Fi:**
Handover matched the second unit: forgetting and re-joining Wi-Fi in Settings > Network & Internet moved ownership from
setupwraith to `com.android.tv.settings` before disabling setupwraith. When rebooting over ADB, use `tools/tv.py --reboot`
or `svc power reboot`: a raw `adb shell reboot` before the system server has synced network state can drop newly saved
Wi-Fi credentials.

Undo, as in the list's own Undo line:

```
TV_HOST=<tv ip> tools/restore.sh examples/tcl-65c6k-google-tv/debloat.txt com.google.android.apps.tv.launcherx
```

setupwraith is not on the list; re-enable it with `tools/tv.py 'pm enable com.google.android.tungsten.setupwraith'`
after launcherx owns Home again.
