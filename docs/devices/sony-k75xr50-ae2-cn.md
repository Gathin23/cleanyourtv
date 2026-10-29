# Sony K-75XR50 (BRAVIA 4K AE2, China): no Google packages, and the one that blanks Display & Sound

The `K-75XR50` is a 75-inch BRAVIA sold in China. It reports `BRAVIA 4K AE2` / `BRAVIA_AE2` and carries the
same `UKN4.250422.001` build string as the North American AE2 in
[`examples/sony-bravia-ae2/`](../../examples/sony-bravia-ae2/debloat.txt), but it is not the same product:
this firmware has no Google services at all, its stock Home is Dangbei, and its Settings app runs Sony's
China picture/sound stack. Those differences matter more than the model name, so they get their own note.

One session, one unit, one reboot, 37 packages disabled and the reference launcher installed. That is a
note, not an `examples/` list - [`CONTRIBUTING.md`](../../CONTRIBUTING.md) asks for a few days of living
with a list first, and nothing here was reboot-soaked or repeated on a second set.

## Identify the firmware, not the retail name

| Field | Observed value |
|---|---|
| Retail model | Sony `K-75XR50`, 75 inch |
| Market | China (`_CN` firmware, no Google packages) |
| `ro.product.model` | `BRAVIA 4K AE2` |
| `ro.product.device` | `BRAVIA_AE2` |
| Android / SDK | 14 / 34 |
| `ro.build.display.id` | `BRAVIA_AE2_CN-user 14 UKN4.250422.001 603100 release-keys` |
| SoC | `ro.soc.manufacturer=Mediatek`, `ro.soc.model=MT5838` |
| RAM | 4.6 GB (`MemTotal: 4765492 kB`) |
| Panel / UI | 3840x2160 panel, Android UI at 1920x1080, density 640 |
| Stock Home | `com.dangbei.TVHomeLauncher/com.dangbei.leard.leradlauncher.ui.sony.main.MainActivity` |
| `ro.adb.secure` | `1` |
| Google packages | none: no `com.google.android.apps.tv.launcherx`, no `com.google.android.tungsten.setupwraith`, no `com.android.vending` |

The density is worth reading before you touch it: this panel reports 640 at 1080p, and
[`sony-k85xr70-ae2.md`](sony-k85xr70-ae2.md) records the same platform coming back from a boot at a
doubled density. Do not copy a density between units.

## Reaching ADB

Pairing survives a reboot; the port does not. Port 5555 is closed after every reboot on this firmware, so
the sequence each time is: `adb mdns services`, find `_adb-tls-connect._tcp` for the TV, `adb connect
<ip>:<mdns port>`, then `adb -s <ip>:<mdns port> tcpip 5555`. The mdns port changes on every boot. With
`ro.adb.secure=1` the first connection shows the RSA prompt on the TV.

`settings put global adb_wifi_enabled 0` turns the wireless-debugging master switch off again;
USB debugging is a separate toggle and stays on until someone turns it off.

## Home handover without launcherx

Neither Google TV guard applies here: there is no `setupwraith` claiming HOME, and no `launcherx` to
disable. The stock Home is Dangbei, whose package name the tooling's stock-launcher list does not know, so
pass it explicitly:

```sh
STOCK_LAUNCHER=com.dangbei.TVHomeLauncher launcher/build-and-install.sh
```

The script installs and switches Home with `cmd package set-home-activity com.gathin.tvhome/.MainActivity`.
Keep the stock launcher enabled until the new one answers HOME, reboot once, then confirm with
`dumpsys activity activities | grep mFocusedApp` before anything else. On this set the handover held across
two reboots.

## `com.sony.dtv.sensingfusionservice` is not camera bloat

This is the finding worth carrying to another AE2. The package sits naturally in a "voice and camera"
group - it ships next to `sensingcamera.*` and `distancealert` - and disabling it looks harmless until
someone opens Settings.

**Symptom.** Settings lists the entry ("Display & Sound" on this build), pressing OK does nothing, and the
Back key takes the Settings app down with it.

**Log evidence.** Every press produces:

```text
W ActivityManager: Unable to start service Intent { act=ISensingDevice
  pkg=com.sony.dtv.configsettings
  cmp=com.sony.dtv.sensingfusionservice/.SensingFusionService } U=0: not found
E AndroidRuntime: Process: com.sony.dtv.configsettings
E AndroidRuntime: java.lang.IllegalStateException: AutoAdjustSettingBinder is not ready yet.
E AndroidRuntime:   at com.sony.dtv.sonysystemservice.audiopictureautoadjust.IAutoAdjustSetting$Stub$Proxy.getValue
```

`com.sony.dtv.configsettings/.DisplaySoundActivity` reads the picture/sound auto-adjust state through
`IAutoAdjustSetting`, which `com.sony.dtv.sonysystemservice` implements on top of the `ISensingDevice`
binder from `sensingfusionservice`. No binder, no state, and the activity throws before it builds its
fragments - which is also why Back then fails with a `NullPointerException` in
`MenuItemManager.isPowerNoticeDialogVisible` (the fragment manager was never set). `com.sony.dtv.ecodashboard`
reads the same interface and crashes too.

**Re-enabling needs a reboot.** `pm enable --user 0 com.sony.dtv.sensingfusionservice` alone is not enough.
Right after it the crash stops and all seven `DirectSettings` binders connect (`IAutoAdjustSetting`,
`IPictureSetting`, `IAudioControl`, `ITargetState`, `IContentControlSetting`, `IAcousticOptimization`,
`IPowerModule`), and it still looks broken: the activity opens a transparent window and draws nothing. The
same entry drew normally - 398 frames - after the next boot, with an empty crash buffer. If you hit this,
enable the package and reboot before you look for a second cause.

## Reading a blank Sony settings page on this set

`screencap` is not a reliable witness here. While `com.sony.dtv.configsettings` owns the focused window,
`screencap` either returned a 0-byte PNG or the frame that was on screen before the page opened. What does
answer:

```sh
tools/tv.py "dumpsys gfxinfo com.sony.dtv.configsettings | grep 'Total frames rendered'"
tools/tv.py "dumpsys window windows | grep -A6 DisplaySoundActivity"
```

A working page counts frames as you move the D-pad (`Total frames rendered` grows, 398 after the reboot
above); a broken one sits at `1`. The window itself reports `mHasSurface=true` in both states, so a surface
is not evidence of a drawn page.

Two log lines look like causes and are not, on this build: `Unable to start service ...
com.mediatek.dtv.tvinput.framework.server` (that package is not installed at all, and no `com.mediatek.*`
package was disabled) and `Failed to find provider info for
com.google.android.apps.tv.launcherx.coreservices.UserModeContentProvider` (no launcherx on a China
firmware). The eight `notifySystemStatusChanged is called before initialization` warnings per launch are a
symptom of the failed initialization, not firmware noise: they are absent once the page works.

## What was kept, and why

- `com.sony.dtv.sonysystemservice`, `com.sony.dtv.configsettings`, `com.sony.dtv.pictureaudio.autoadjustsettings`:
  the picture/sound stack the Settings entry above depends on. The last one is also the only client-side UI
  for auto adjust.
- `com.sony.dtv.tvinput.*`, every `com.mediatek.*` package, `com.sony.dtv.tvlin`: HDMI and the input
  switcher. `com.sony.dtv.tvinput.dvbtuner` is refused outright by `tools/disable.sh`'s input-hardware
  guard, so it was never disabled.
- `com.sony.dtv.remotecontrol*`, `irblaster*`, `devicecontrolservice`, `inputinjectionproxy`,
  `com.uei.quicksetsdk`, `com.sony.dtv.partnerkeycn`: remote, IR and Bluetooth pairing. `partnerkeycn`
  handles the remote's shortcut keys and is not on the tooling's keep list - keep it anyway.
- `com.sony.dtv.settingsprovider`, `imeproxy`, `ime.chww`, `seeds.auth`, `notificationcenter`,
  `smartmediaapp`, `photoservice`, `webapi`.
- `com.sony.dtv.update*`, `upgradesetup`, `otaurlprovisioning`: the firmware update chain, kept by the
  owner's choice.
- Kept by owner choice, not by dependency: `com.ktcp.video` (Tencent), `com.cibn.tv` (Youku), `com.cctv.tv`,
  `com.hunantv.market`, `com.quark.yun.tv`, `com.ugreen.nasprotv`, `com.sony.dangbeimarket`, `org.smarttube.stable`,
  Netflix's token manager and the Dolby audio service.

## The list as applied

One session, one reboot, one unit. Read it as a starting point and comment out what you need.

```text
# Telemetry and usage reporting
com.sonyericsson.idd.agent
com.sony.dtv.usercontext
com.sony.dtv.lastaccess
com.sony.dtv.crashlogcollector
com.sony.dtv.sonybugreportsys

# Demo mode, the stock screensavers, and Sony extras
com.sony.dtv.demosupport
com.sony.dtv.demosystemsupport
com.sony.dtv.multiscreendemo
com.sony.dtv.braviashow
com.sony.dtv.livingfit                          # the default screensaver; ClockDream replaces it
com.sony.dtv.osat.music.toast
com.sony.dtv.calibrationmonitor
com.sony.dtv.gestureapp

# Voice assistant and camera (the remote's voice key stops working - owner's choice)
com.sony.dtv.searchapp
com.sony.dtv.interactivetvutil
com.sony.dtv.interactivetvutil.output
com.sony.dtv.videochatrecord
com.sony.dtv.sensingcamera.settings
com.sony.dtv.sensingcamera.fwupdate
com.sony.dtv.distancealert
com.sony.dtv.seeds.iot
# com.sony.dtv.sensingfusionservice is NOT in this group, see above

# Casting and DLNA
com.hpplay.happyplay.aw
com.sonyericsson.dlna
com.sony.huey.dlna.module
com.sony.dtv.homenetwork

# Antenna tuner and broadcast services (the owner does not watch antenna TV)
com.sony.dtv.broadcastclock.broadcastrequirementcontrol
com.sony.dtv.broadcastclock.clocksyncservice
com.sony.dtv.broadcastsettings
com.sony.dtv.servicemode.tuner
com.sony.dtv.serviceinformation

# Kids, account and help
com.sony.dtv.kidsprofileadapter
com.sony.dtv.mysony
com.sony.dtv.customersupport
com.sony.dtv.smarthelp
com.sony.dtv.helpguidecontents

# Home, after the replacement was confirmed as HOME
com.dangbei.TVHomeLauncher

# Disabled by hand, outside the batch
com.ktcp.tvvideo                                # a second Tencent build next to com.ktcp.video
```

That is 37 entries: 36 from the batches and `com.ktcp.tvvideo` by hand. `com.smgtech.vvdplayer`,
`com.gitvdemo.video` and `com.xiaodianshi.tv.yst` were disabled with the batches and re-enabled by the owner
afterwards, so they are not in the list above.

## Chinese retail sets lie about app identity

The label on the Home tile is not the package. On this set `com.gitvdemo.video` shows as a CCTV casting
helper and is the iQIYI (银河奇异果) build, `com.xiaodianshi.tv.yst` is Bilibili's TV app, and
`com.smgtech.vvdplayer` is an SMG casting helper. Ask the owner what a tile opens before disabling the
package behind it.

## Limits

- One unit, one session of about an hour, one reboot. No multi-day soak, no second AE2, no firmware update
  cycle.
- The package list is the owner's, applied through `tools/disable.sh` in five batches with a screenshot
  between batches; this note is the parts that carry to another set.
- Nothing here is checked against a second China AE2, and no claim is made about the North American AE2 in
  `examples/`: same build string, different product.
- Promote this to `examples/sony-bravia-ae2-cn/` once someone has lived with the list for a few days, seen
  what a firmware update restores, and can say whether a second unit behaves the same.
