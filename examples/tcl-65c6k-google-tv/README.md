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

## Fourth unit: 85C6K, Czechia (EU firmware), Wi-Fi, debloat only

TCL 85C6K (QD-Mini LED, 85"), model "Smart TV Pro", device `G08`, product `G08_4K_GB`, `ro.boot.product=C6K`,
`ro.boot.extension=TCL_QDMINI_4K`, client type `TCL-EU-MT9653-S1`, cs-CZ, on Wi-Fi, with an Apple TV on HDMI 1 (eARC)
and a PS5 on HDMI 2. Android only reports "Smart TV Pro", and `ro.odm.tcl.panel_size` reads 55 on this 85" set; the
real model name is in Settings > System > About > Status > Product information. Debloated on 23 November 2026 with
`tools/disable.sh` in six batches with a screenshot after each, stock launcher and setupwraith kept (setupwraith owned
the saved Wi-Fi). One evening and three reboots, not a several-day test.

**It started on Android 12 and ended on this build.** The set came on `STT2.221228.001 AS35` (EU Android 12), so the
first five batches followed `examples/tcl-g08-eu-android12/debloat.txt`. TCL's updater (`com.tcl.ttvs`, auto update on)
had downloaded the Android 14 update in the background and posted "System update downloaded"; the next reboot, a check
after a batch, installed it as an A/B update (`reboot,ota` in the boot reasons, then `UTT2.250416.001 AU02`, security
patch 2025-12-05). Every disabled package stayed disabled; `com.iplayer.android.webview` (BBC WebView, disabled in
batch 2) was removed from the firmware by the update. There is no way back to Android 12, so on an EU Android 12 set
check `dumpsys notification --noredact | grep android.title` for a downloaded update before any reboot. The sixth batch
followed this list and `examples/tcl-g08-au-android14/debloat.txt` on the new build.

**What it turned off, compared with this list.** Every active line except these, left enabled on purpose:
`com.tcl.interactive` (the EU Android 12 list found the source overlays break without it; not tried here),
`com.tcl.guard` (it frees memory, and swap filled fast on Android 12), `com.tcl.UpdatePeripheral` (updaters stay),
`com.google.android.katniss` (the Assistant is used), and `android.autoinstalls.config.google.gtvpai` and
`com.google.android.partnersetup` (not in the lists it started from; not tried). `com.tcl.hotelmenu` is not on this
set. From the "Kept on this unit" lines it turned off `com.tcl.magiconnectfree` and Prime Video, and kept Chromecast,
AirPlay, Miracast, the game bar, the USB media player and gallery, Netflix and YouTube. On top of the list it turned off
thirteen more: the four lines under "Spare webviews" (in the list now), the three commented as one day so far, and six
commented at the end of the "Kept on this unit" lines (Play Movies, Disney+, HBO Max, iQIYI, `com.tcl.t_solo`,
`com.tcl.hearaid`). Sound from the TV speakers with `com.tcl.hearaid` off was not checked.

**The TCL Channel row on Home went away.** Unlike the first unit's TCL tab, this EU set had a "TCL Channel" row of
app promotions (sledovani.tv, O2 TV, Magenta TV, Spotify) that stayed after `com.tcl.channelplus` and
`com.tcl.waterfall.overseas` were off, and disappeared with `com.tcl.partnercustomizer`. Right after that, the "Your
apps" row showed a dead "TCL Channel" tile (Play Store: "Item not found"); it was gone after the next reboot. The
source panel's Apps row still shows the TCL Channel and User Guide tiles, as on the first unit.

**Checked:** after each batch Home, the Input key's source panel (AirPlay, TV, HDMI 1), Wi-Fi and ADB; after the last
batch Netflix, YouTube and Play Store; after each of the three reboots the same disabled set. Not checked: Live TV,
Chromecast, AirPlay and Miracast sessions, the real remote's buttons, sound.

**Also seen on this set:** a built-in far-field microphone, on (`persist.tcl.far_field_mic_status=1`, a hardware
switch shows up as the `mic_mute` input device), with no hotword model enrolled; `com.tcl.ttvs` runs TCL's BI, usage
statistics and track-data services as well as T-Cast. The UI renders at 1920x1080 on the 4K panel
(`ro.surface_flinger.max_graphics_width=1920`, `vendor.mtk.osd_size=1080p`); video and HDMI inputs are full 4K.

Network ADB was switched off afterwards and `tools/adb-closed.py` reported port 5555 closed; Wireless debugging was
never on.

Undo, as in the list's own Undo line:

```
TV_HOST=<tv ip> tools/restore.sh examples/tcl-65c6k-google-tv/debloat.txt com.google.android.apps.tv.launcherx
```

setupwraith is not on the list; re-enable it with `tools/tv.py 'pm enable com.google.android.tungsten.setupwraith'`
after launcherx owns Home again.
