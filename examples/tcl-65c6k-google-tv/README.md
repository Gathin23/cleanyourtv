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

Undo, as in the list's own Undo line:

```
TV_HOST=<tv ip> tools/restore.sh examples/tcl-65c6k-google-tv/debloat.txt com.google.android.apps.tv.launcherx
```

setupwraith is not on the list; re-enable it with `tools/tv.py 'pm enable com.google.android.tungsten.setupwraith'`
after launcherx owns Home again.

## Third unit: 55C6K, Europe / UK, Wi-Fi, launcher replaced

TCL 55C6K (QD-Mini LED, 55"), model "Smart TV Pro", device `G08`, product `G08_4K_GB`, MediaTek `mt5893` (SoC MT9653),
European build `STT2.221228.001 AS35` (`V8-T653T01-LF1V531`, `TCL-EU-MT9653-S1`), Google TV 12, on Wi-Fi.
Set up on 31 October 2026, then the reference launcher (`com.gathin.tvhome`); rebooted once afterwards:
every package stayed disabled, nothing re-enabled itself, and the TV came up on the new Home (`com.gathin.tvhome`).

**Different choices from the first unit:** YouTube (`com.google.android.youtube.tv`), Netflix (`com.netflix.ninja`),
Prime Video (`com.amazon.amazonvideo.livingroom`), and JioHotstar (`in.startv.hotstar`) kept for daily family use.
Play Store (`com.android.vending`), settings (`com.tcl.settings`, `com.android.tv.settings`), and Live TV
(`com.mediatek.wwtv.tvcenter`) kept. The stock browser (`com.tcl.browser`), T-Cast (`com.tcl.magiconnectfree`),
stock media center / gallery (`com.tcl.ui_mediaCenter`, `com.tcl.gallery`), gamebar (`com.tcl.gamebar`),
cleaner (`com.tcl.guard`), and passive TCL home telemetry (`com.tcl.tv.tclhome_passive`) disabled.

**Launcher handover.** As on the first unit, the saved Wi-Fi belonged to setupwraith
(`dumpsys wifi | grep -o 'cname=[^ ]*'`). The Wi-Fi network was forgotten and re-joined in Settings > Network & Internet,
moving ownership to `com.android.tv.settings`. setupwraith was then disabled with `tools/disable.sh`
(Wi-Fi and ADB stayed up) and `com.gathin.tvhome` took Home cleanly.

What was checked on the TV: Home and the Inputs menu (HDMI 1-4 and Live TV, `com.mediatek.wwtv.tvcenter`); after the reboot,
the real Input, Settings, D-pad, and Volume buttons all worked with no key helper enabled.
