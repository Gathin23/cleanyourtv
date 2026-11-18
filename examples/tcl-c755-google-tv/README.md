# TCL C755 (G08, Google TV 14), also sold as the C805

The C755 is sold as the C805 in Europe: the same G08 board and firmware family. The Australian and UAE units'
notes are in the header of this folder's `debloat.txt`. This page is for the third unit, a 55C805 with a list of
its own, `examples/tcl-c755-google-tv/debloat-55c805.txt`. A
[50C805 compatibility report](../tcl-55c7k-uk/#compatibility-report-tcl-50c805-wi-fi), on AU02, is in the 55C7K's
folder.

## Third unit: 55C805, EU, Wi-Fi

A TCL 55C805: model `Smart TV Pro`, device `G08`, product `G08_4K_GB`, Google TV 14 (SDK 34), build
`UTT2.250416.001 AU04` (software `V8-T653T01-LF1V670`), EU firmware. Its list was applied on 13 November 2026 and
lived with since, through restarts and standby, with the reference launcher as Home
(`launcher/build-and-install.sh` with the key helper).

It started from the EU list in `examples/tcl-65c7k-google-tv/debloat-75t8c-eu.txt`, whose DO NOT DISABLE section
names packages that broke something on that set. This unit tested six of them one at a time on Live TV, with the
log watched, and lived with the result through three restarts:

| Package | On this unit |
|---|---|
| `com.tcl.interactive` (TIAS) | **On.** Off, the TV app and `vendor.tcl.tvinput.extend-service` logged a failed lookup of `tcl_tias_tbrowser` about 35 times a second, without end (about 1-3% of one core), as on the 75T8C. `debloat.txt` turns it off; its AU02 unit saw only a few seconds of it each time the TV player started. |
| `com.tcl.ttvs` | **Off, but a comment in the list**, as it holds the TV's firmware updater. Also TCL's analytics, usage statistics, tracking data, device identifiers, T-Cast and Samba TV's ACR (not running here). No TIAS errors with it off: the TV app starts `com.tcl.interactive` itself. With it off, Settings > System > About > System update does nothing, for an update from a USB stick too, and Settings > Apps (where it is "TCSCore") has no Enable for it, so it goes back on over ADB before an update: `tools/tv.py 'pm enable com.tcl.ttvs'`. |
| `com.tcl.channelplus` | **Off.** TCL Channel, free internet channels as a source in Live TV. That source goes; the antenna, the guide and HDMI were unchanged. |
| `com.tcl.waterfall.overseas` | **Off.** TCL's film and show browser. |
| `com.tcl.guard` | **Off.** TCL's cleaner and app manager. The 75T8C's complaint, Android retrying its TGuardService, did not come up: this build's `com.tcl.guard` has no such service. |
| `com.tcl.dashboard` | **Off.** TCL Home: messages, promos, account and smart-home controls. The remote's TCL Home button does nothing now; the Input key's source panel (`com.tcl.suspension`, kept), the gear, Home and Back still work. `debloat.txt` keeps it. |

With `com.tcl.ttvs` off, TCL's boot control (`TclAppBoot` in `system_server`) refused to start apps that lack its
auto-start permission: the Koi Pond screensaver (the screensaver never came up), and an add-on's receivers for
Bluetooth headphones and boot. `com.tcl.guard` on again, with a restart, did not change it. The fix is the one
`launcher/build-and-install.sh` makes for the launcher itself:

```sh
TV_HOST=<tv ip> tools/tv.py 'appops set <package> AUTO_START allow; appops write-settings'
TV_HOST=<tv ip> tools/tv.py 'logcat -d' | grep TclAppBootInfo     # which apps were refused, and why
```

The owner also keeps Chromecast, AirPlay, Miracast, YouTube Music and the game bar (`com.tcl.gamebar`, for a PS5:
with the six above as in the table, Game Master still turned on for the PS5 and the game bar opened over it), which
`debloat.txt` turns off by its first owner's choice. Samsung's Smart View mirrors through Miracast
(`com.tcl.miracast`): switched off for a test, the TV was gone from Smart View, and Google Home's "Cast my screen"
did not mirror instead. Beyond `debloat.txt`, this list turns off MediaTek's telemetry
(`com.mediatek.backgrounddetection`), TCL's silent partner-app installer (`com.tcl.smartlink.core`), MediaTek's
second WebView (`com.mediatek.support.webview`; Google's WebView stays the active one), TCL's "listen alone"
(`com.tcl.t_solo`), the print spooler and calendar sync. TCL's browser is off on this unit too, but left as a
comment in the list: it is the TV's only browser.

What was checked on this unit:

- Live TV on the antenna, HDMI with a PS5 named over HDMI-CEC, and switching between them; a soundbar on eARC.
- Chromecast, AirPlay and Miracast: a Mac mirrored to the TV over AirPlay, and a Samsung phone over Smart View.
  AirPlay's first pairing worked with TCL's AirPlay app open on the TV (with a 4-digit code); once paired it
  mirrored straight from Home.
- Bluetooth headphones: standby drops their link, so they are disconnected while the TV is off.
- Wi-Fi: the 2.4 GHz network saved during first setup belonged to setupwraith (`dumpsys wifi`), so it was
  forgotten before the list went on; the 5 GHz network the TV uses was the system's.

Remote: a TCL RC902N bought separately, not the remote the TV came with (that one has number buttons and was not
checked). Without Google TV's launcher, the RC902N's buttons send keycodes that `app_keys` can map with the key
helper on: Settings 83, TV 4001, YouTube 190, Netflix 4062, Prime Video 4099, the Quick panel button under Home
4139, the bookmark button 174 and TCL Home 4137. With `com.tcl.dashboard` off, TCL Home does nothing unless mapped;
here it opens TCL's game bar (`4137:com.tcl.gamebar/.activity.MainActivity`), which shows only over a game. An
injected `input keyevent 4139` goes to TCL's own shortcut handling instead, so test that one with the real remote.
With the helper on, a held Volume key moves one step, as on the AU02 unit; `volume_pauses_filter` gives the ramp
back after a tap, then a hold.

A USB Ethernet adapter (Realtek r8152, 1 Gbit/s) carried ADB with USB debugging on. At the end, USB debugging was
switched off and its authorisations revoked, and `tools/adb-closed.py` reported 5555 closed before and after a
restart.
