# Toshiba 32V35MP: Android TV 11, Hisense/CVTE hayward

One unit, tested during one setup session on 2026-10-27, including several software reboots.
This records observations, not a several-day debloat list. The owner supplied the retail model;
Android identifies it as `Toshiba TV`, Android 11 / SDK 30, firmware `RTMA.250416.097`.
It is a 1 GB set with a 1280×720 launcher UI, connected over Wi-Fi, with no USB or HDMI device attached.

The [Motorola MT4310](../../examples/motorola-mt4310/) and
[Polytron 2K](../../examples/polytron-2k-hayward/) share the hayward platform, but their lists cover
different firmware and different choices about voice search, casting and streaming apps.
Do not apply either list solely because the board name matches.

The session used source revision `57733360177173c880a023380a864114a03feb25`, with local launcher
resource configuration for the dock and reduced motion. The Bluetooth add-on's resources were also
configured locally; there were no launcher Java or permission changes.

## Home handover and the remote

With the replacement installed, the HOME claimants were:

| Component | Priority |
|---|---:|
| `com.google.android.tvlauncher/.MainActivity` | 2 |
| `com.gathin.tvhome/.MainActivity` | 0 |
| `com.android.tv.settings/.system.FallbackHome` | -1000 |

`set-home-activity` reported `Success`, but resolving HOME still returned the stock launcher.
`com.google.android.tungsten.setupwraith` did not claim HOME on this firmware and stayed enabled.
The direct-boot-aware HOME query returned the Settings `FallbackHome`; no factory activity was launched.

The replacement was opened explicitly and checked before disabling stock Home. An initial reboot
with stock Home still enabled completed and ADB returned. After the guarded `tools/disable.sh`
handover, both the HOME resolver and foreground window identified `com.gathin.tvhome/.MainActivity`;
this held across subsequent software reboots. This is the observed result on this firmware, not a
reason to skip the CVTE/Home checks in [PROMPT.md](../../PROMPT.md) on another unit.

The owner confirmed physical Home, YouTube, Input/source, Settings, voice search and held Volume Up
worked. Neither `InputKeyService` nor `HomeKeyService` was enabled. The Inputs menu showed HDMI 1,
HDMI 2, Composite and Live TV. The input providers included `com.mediatek.tvinput` and
`com.mediatek.tv.service`; these and the TV/input/remote services stayed enabled.
No HDMI picture, CEC or tuner reception was tested.

## Packages disabled during this session

These 20 packages were newly disabled for user 0 and still disabled at the final post-reboot check.
This is a record of owner-approved choices, including unused apps, not a ready-to-apply list or a
claim that every entry is bloat. Nothing was uninstalled. Package backups and the applied list were
kept locally for `tools/restore.sh`; a complete restore has not been tested.

| Package | Purpose / owner choice |
|---|---|
| `android.autoinstalls.config.hisense.hayward` | Partner app auto-install configuration; see the guard note below |
| `com.mediatek.android.leanbacklauncher.partnercustomizer` | Partner promotional rows |
| `fusion.android.tv.demo` | Retail demo |
| `com.google.android.feedback` | Feedback uploader |
| `com.google.android.partnersetup` | Google partner setup |
| `com.mstar.android.tv.disclaimercustomization` | Store/setup disclaimer; see the guard note below |
| `com.google.android.youtube.tvkids` | YouTube Kids |
| `com.google.android.youtube.tvmusic` | YouTube Music |
| `in.startv.hotstar` | JioHotstar |
| `com.sonyliv` | SonyLIV |
| `com.graymatrix.did` | ZEE5 |
| `com.mxtech.videoplayer.ad` | MX Player |
| `com.google.android.videos` | Google TV / Play Movies |
| `com.google.android.play.games` | Google Play Games |
| `com.jio.web.androidtv` | JioSphere / JioPages browser |
| `nextapp.fx` | FX File Explorer |
| `com.microsoft.rdc.android` | Microsoft Remote Desktop |
| `net.easyjoin.applauncher` | SideApps / sideload app launcher |
| `cm.aptoide.pt` | Aptoide |
| `com.google.android.tvlauncher` | Stock Home, only after the handover checks above |

The input-driver guard initially held back the auto-install configuration and disclaimer packages:
their package dumps had no `requested permissions:` section. The pulled APK manifests contained
neither requested permissions nor input services, and a separate `TvInputService` query identified
the MediaTek input packages. Only those two packages received a scoped input-check override after
inspection; the guard was not changed. Their disabled state survived reboot, but the absence of an
attached HDMI device limits what this session establishes.

## Netflix and recommendations return at boot

Netflix (`com.netflix.ninja`) and `com.google.android.tvrecommendations` came back enabled after
being disabled. Boot log lines attributed both actions to the firmware's
`com.at.factory.factorymanager.CommonManager`:

```text
enableApp : com.netflix.ninja
enableApp : com.google.android.tvrecommendations
```

The factory manager was left alone. Recommendations stayed enabled and did not add rows to the
replacement launcher. Netflix was briefly suspended as a separate experiment and remained suspended
after reboot, but that suspension was undone: **Netflix and Prime Video
(`com.amazon.amazonvideo.livingroom`) were both enabled in the final configuration**, at the owner's
request. Both were opened successfully; this was not a long playback or DRM test.

YouTube, BrowseHere, Play Store and USB Media Player were kept in the dock. Google Assistant,
Chromecast, firmware updates, Google Play services, keyboard, Bluetooth and required TV/input/remote
components were also kept. Keeping these packages enabled does not establish that casting playback
or installing an OTA was tested. DNS was unchanged.

## Bluetooth and low-memory observations

The existing [Bluetooth add-on](../../addons/bluetooth-toggle/) opened the accessory-pairing screen
with `switch_on_open=false` and `close_after_seconds=0`. Opening it left the Bluetooth radio enabled;
pairing a new accessory and Bluetooth audio were not tested.

Window and transition animation scales of `0.5` survived reboot. The animator duration scale,
also set to `0.5`, returned to `0.6`. Android's global background process limit stayed at Standard.
Netflix and Prime Video alone received `RUN_IN_BACKGROUND` and `RUN_ANY_IN_BACKGROUND` restrictions;
Netflix's full system power exemption was removed, while its separate except-idle entry remained.
Those settings survived reboot and both apps still opened. Recovery settings were saved locally.
This does not establish a speedup, sustained playback compatibility or a universal RAM-tuning recipe.

## Remaining checks

There has been no several-day use, mains-disconnect cold boot, long standby test or full restore.
HDMI/CEC, tuner reception, casting playback, OTA installation, USB media playback and Bluetooth
pairing remain unverified. Network ADB was used with on-TV authorization; the owner deferred turning
debugging off, so closure of the debugging port has not been verified. Raw backups, screenshots,
network details and device identifiers are intentionally not included in this report.
