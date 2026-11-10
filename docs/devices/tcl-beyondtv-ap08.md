# TCL BeyondTV AP08: initial setup report

Checked on a real TV in one session on 2026-10-21, including a restart and owner checks with the physical remote and phone casting. This is **not a several-day tested debloat list**. The package changes below record this owner's choices, not a list to apply unchanged. The retail model was not established on that unit; the [second unit](#second-unit-iffalcon-43k61-india-v656) is an iFFALCON 43K61 sold in India.

## Device identifiers

| Field | Observed value |
|---|---|
| Model / device / product | `BeyondTV` |
| Board | `RTD2851` |
| Android / API | Android TV 9 / 28 |
| Build display | `PPR1.180610.011 release-keys` |
| Build incremental | `AP08` |
| Security patch | `2021-01-05` |
| Reported memory | `2097152 kB` |

This supplements the [BeyondTV example](../../examples/tcl-beyondtv/debloat.txt). Do not confuse AP08 with the [RT2851 AP06 example](../../examples/tcl-beyondtv-rt2851/debloat.txt), which has a different security patch. The trial used a locally built launcher reporting version 2.5 / versionCode 6; it does not validate every later upstream revision.

## Launcher and input configuration

The local launcher configuration used these values in `launcher/app/src/main/res/values/config.xml`; shared defaults do not need changing:

```xml
<string-array name="dock_apps">
    <item>com.google.android.youtube.tv</item>
    <item>com.netflix.ninja</item>
    <item>com.amazon.amazonvideo.livingroom</item>
    <item>com.android.vending</item>
</string-array>
<string-array name="hidden_apps">
    <item>com.tcl.tv</item>
</string-array>
<string name="live_tv_package" translatable="false">com.tcl.tv</string>
```

Replace the corresponding resource entries rather than adding duplicate names. Keep `com.tcl.tv` and `com.tcl.tvinput` enabled: hiding the TV tile is only a launcher presentation choice. Input services on this unit were `com.tcl.tvinput/.TDTVInputService` and `com.tcl.tvinput/.passthroughinput.TvPassThroughService`.

The original HOME activity was `com.google.android.tvlauncher/.MainActivity`. The replacement was installed and launched before disabling stock HOME, then set as HOME and checked with the Home key. `com.gathin.tvhome/.MainActivity` was the resumed activity, including after restart. `com.google.android.tungsten.setupwraith` was installed but did not advertise HOME on this unit and stayed enabled. The TCL key-customization and Android Settings fallback HOME activities stayed enabled too. No accessibility helper or remote scancode remapping was needed.

## Changes observed in this session

All changes used reversible user-0 package disabling, not uninstalling. There were 19 disabled packages before the session and 30 afterward. These eleven additions remained disabled after restart, with no unexpected changes to the disabled-package set:

| Package | Session choice / constraint |
|---|---|
| `com.tcl.bi` | TCL analytics |
| `com.google.android.feedback` | Feedback reporting |
| `com.google.android.tv.bugreportsender` | TV bug reporting |
| `com.tcl.tv.tclhome_passive` | Passive TCL Home promotion |
| `com.tcl.esticker` | Retail stickers |
| `com.google.android.tvrecommendations` | Stock-home recommendations |
| `com.google.android.leanbacklauncher.recommendations` | Stock-home recommendations |
| `com.google.android.tvlauncher` | Only after replacement HOME was installed and working |
| `com.tcl.tvweishi` | Cleaner; disabled after installation because it also exposes an installer service |
| `com.tcl.appmarket2` | Duplicate store; disabled after installation because it also exposes an installer service |
| `com.tcl.versionUpdateApp` | TV firmware updater, disabled at the owner's explicit request; not a default recommendation |

The remote firmware updater stayed enabled. No newly disabled package needed re-enabling during this session; longer-term behavior is unknown. Record original package states before making changes and restore those states when undoing them. Re-enable stock HOME before removing a replacement launcher.

YouTube, Netflix, Prime Video, Play Store, ZEE5, Google Play Movies & TV, YouTube Music and TCL Channel stayed enabled. Chromecast, voice assistant, Miracast and T-Cast stayed enabled. In particular, this owner did **not** apply the existing example's Play Movies or YouTube Music disables.

Ambiguous or input-related packages were retained, including `com.tcl.partnercustomizer` (T-key activities), `com.tcl.messagebox`, `com.tcl.usercenter`, `com.tcl.showmode`, `com.tcl.framework.custom` and `com.tcl.keycustomfunctionservice`. No conclusion about their safe removal follows from this report.

## Verification and limits

- The Inputs menu showed HDMI 1, HDMI 2, AV and Live TV. Selecting HDMI 1 opened `com.tcl.tv/.TVActivity` and its no-signal screen; Home returned to the launcher. **HDMI picture/audio with a connected source and tuner reception were not tested.**
- The owner confirmed all present physical remote buttons worked after restart, covering the requested Home, Input/Source, Settings, volume, voice/microphone and available app shortcuts. This is an owner check, not an exhaustive remote-event trace.
- At the owner's request, Android Private DNS was set to hostname mode with `dns.adguard-dns.com`. Connectivity validation passed and the owner confirmed video and sound in a streaming playback test. This does not establish playback in every retained app or block YouTube ads. Restore the previous Private DNS settings to undo it.
- The owner confirmed the TV appeared for phone casting and played video with sound after restart. Miracast and T-Cast were preserved but not separately exercised.
- The post-restart crash buffer was empty at inspection. Standby cycles, prolonged use, firmware updates and every app's functionality were not tested.
- Finally, the owner revoked USB-debugging authorizations, turned debugging off and restarted. A TCP connection attempt to port 5555 returned **connection refused**. Network debugging was not left enabled.

## Second unit: iFFALCON 43K61 (India), V656

One session on 2026-11-10, with a screenshot after each round of changes and two reboots (after the package rounds
and after the launcher); not a several-day list. iFFALCON is TCL's
sub-brand, and the K61 (43, 50 and 55 inch) is sold as Android TV in India.

| Field | Observed value |
|---|---|
| Retail model | iFFALCON 43K61 |
| Model / device / product | `BeyondTV` |
| Board | `RTD2851` (`ro.board.platform` `rtd285o`) |
| Android / API | Android TV 9 / 28 |
| Software | `V8-R851T02-LF1V656`, version name `AP08` |
| Client type | `TCL-AP-RT2851-S1` |
| Security patch | `2021-01-05` |
| Reported memory | `2097152 kB` |
| Network | Ethernet |

### Firmware and TCL's service update

- TCL's system update reported no newer firmware for this client type. The first check after a boot said there was
  no network while Android had validated the Ethernet link; `com.tcl.versionUpdateApp` had crashed and restarted
  during boot. A second check a few minutes later answered normally.
- The same updater also runs a separate **service update** (`AppUpdateService`) that replaces TCL's own apps. On
  this day it installed `com.tcl.usercenter` 4.0.2.1 and `com.tcl.waterfall.overseas` 7.5.1.11, and an update
  described as "Optimize the upgrade process for TTVS" was offered again after a reboot. This build has no
  `com.tcl.ttvs` package (TCL's own code logs `NameNotFoundException` for it); the TTVS classes run inside
  `com.tcl.bi`.
- `com.tcl.versionUpdateApp` was disabled at this owner's request, after the firmware check above. It is not a
  recommendation: keep it for firmware updates.

### Packages

Disabled one at a time, in three rounds with a screenshot after each, and kept after a reboot:
`com.tcl.bi`, `com.google.android.feedback`, `com.google.android.tv.bugreportsender`,
`com.tcl.tv.tclhome_passive`, `com.tcl.esticker`, `com.google.android.tvrecommendations`,
`com.google.android.leanbacklauncher.recommendations`, `com.tcl.waterfall.overseas`,
`com.tcl.partnercustomizer`, `com.tcl.messagebox`, `com.tcl.smartalexa`, `com.google.android.partnersetup`,
`com.google.android.onetimeinitializer`, `com.tcl.useragreement`, `com.tcl.android.webview` (Google WebView is the
active provider), `com.tcl.miracast`, `com.tcl.MultiScreenInteraction_TV` and `com.tcl.versionUpdateApp`. After the
launcher handover below: `com.google.android.tvlauncher`, `com.tcl.tvweishi` and `com.tcl.appmarket2`.

- `com.tcl.notereminder` and `com.tcl.usercenter` were enabled again by the first reboot, as on the RT2851 AP06
  second unit (`com.tcl.appstatecontroller`). Both stay idle and were left enabled; `appstatecontroller` was not
  touched.
- Voice (`katniss`, `com.tcl.assistant`, `com.tcl.micmanager`) and `com.google.android.tv.remote.service` were kept.
  Chromecast, the photo screensaver and TalkBack had already been switched off by the owner from the TV's own menus.
- HDMI goes through `com.tcl.tvinput`; there is no separate `com.tcl.tvpassthrough` on this build, as on the first
  unit.

### Stock Home and the launcher

- The stock Home (`com.google.android.tvlauncher` 7.7.15) shows server-side rows at the top ("Presented by …",
  "Top-selling movies", "Trending on Google"). Settings > Home screen > Customize channels does not turn these off,
  and disabling `tvrecommendations` and `partnercustomizer` did not remove them.
- An unmodified `launcher/` built with `tools/docker-build.sh` (no local JDK), with only `live_tv_package` and
  `hidden_apps` set to `com.tcl.tv` in `config.xml`, started with `am start -W` before the stock launcher was
  disabled, then took Home with `cmd package set-home-activity`. The other HOME claimants were only
  `com.tcl.keycustomfunctionservice/.FallbackHome` and `com.android.tv.settings/.system.FallbackHome`.
- After a reboot the launcher was in front and `getprop sys.enable_key` was `true`: the locked arrows and OK seen on
  the V237 build ([tcl-beyondtv-rt2851.md](tcl-beyondtv-rt2851.md)) did not occur.
- Play Store updates to `com.google.android.tungsten.setupwraith` (1.0.993403918) and
  `com.google.android.tv.remote.service` (7.00.999235495), installed with the stock launcher already disabled,
  added no HOME claimant.
- The owner checked on the physical remote, after the reboot: Home from inside an app, Back, the arrows and OK,
  Source/Input with an HDMI device, Power, the mic button and the app buttons.

### Not checked

Tuner reception, standby over days, a firmware install and a full rollback.

Afterwards Developer options were turned off; `tools/adb-closed.py` reported port 5555 closed.

Private device dumps, network addresses, Wi-Fi names, screenshots containing network identifiers, signing keys and downloaded build tools are intentionally absent from this report.
