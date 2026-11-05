# Philips 55PUK7100/12 (2015, QV151E, Marvell, Android TV 5.1.1): `pm disable-user`, `pm hide` and device owner refused

This is an owner report from one TV, **not a debloat list for the repo tools**. The adb shell cannot disable,
hide or manage packages on this firmware (next section), so there is deliberately no `examples/` entry. What
follows is what plain ADB showed on this unit, so the next owner of a 2015 Philips Android TV can skip the dead
ends. The TV is a Turkish unit; the rear label reads `55PUK7100/12`, but the firmware names itself
`49PUK7150/12` (JointSpace `/5/system` name, mDNS and Cast name), so the 49/55-inch 7100 and 7150 sets of 2015
appear to share this `QV151E` software. The label is the model to trust.

| Field | Observed value |
|---|---|
| Retail model | 55PUK7100/12 (rear label); the firmware reports `49PUK7150/12` |
| Android model / device | `QV151E` / `QV151E` |
| Fingerprint | `PHILIPS/QV151E/QV151E:5.1.1/LMY48N/0538219:user/release-keys` |
| Android / API | 5.1.1 / 22 (`LMY48N`) |
| RAM | 940 MB (`MemTotal` 964304 kB); 58 MB free (`MemFree`) on the stock install |
| Stock home | `com.google.android.leanbacklauncher` |
| Live TV app | `org.droidtv.playtv` (tuner `org.droidtv.tunerservice`, HDMI `org.droidtv.hdmiService`) |
| ADB | USB debugging alone opens TCP 5555; the RSA prompt appears at the first connection |

## What the adb shell refuses

| Command | Result |
|---|---|
| `pm disable-user <pkg>` (and `pm disable`) | `SecurityException: Permission Denial: attempt to change component state ... uid=2000` for every package |
| `pm hide <pkg>` | `SecurityException: Neither user 2000 nor current process has android.permission.MANAGE_USERS` |
| `dpm set-device-owner` (with no accounts on the TV, `device_provisioned` briefly 0) | `Can't set package ... as device owner`: `pm list features` has no `android.software.device_admin`, so the device policy service is a no-op and `setApplicationHidden` is out of reach |

Unlike the [XGIMI H1](xgimi-h1.md) on the same Android version, `pm hide` is closed too, so `tools/disable.sh`
has nothing to call. `launcher/build-and-install.sh` stops at the SDK check (the launcher needs Android 8.0).

## What does work: the Settings app's own Disable button

The phone-style Settings screen still ships and runs as the system uid, and its **Disable** button works where
the shell does not. It opens from adb:

```bash
adb shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:<pkg>
```

On that screen the remote's Right then OK presses **Disable**, and Right then OK confirms the dialog. **Enable**
on the same screen undoes it. Disabled packages stayed disabled across a reboot.

The button is greyed out for everything signed with the Philips platform key, which includes every package that
would matter most here: `org.droidtv.candeebug` (analytics), `org.droidtv.usagelogger`, `org.droidtv.nettvadvert`,
`org.droidtv.nettvrecommender`, `org.droidtv.sticker`, `org.droidtv.nettvregistration`, `org.droidtv.demome`,
`org.droidtv.nettv.market`, `org.droidtv.nettvapps`, `org.droidtv.nettvbrowser` (HbbTV, the largest process at
about 56 MB), `org.droidtv.epg`, `org.droidtv.dms`, also `com.teamviewer.quicksupport.addon.tpvision2k15_v1`,
`com.google.android.exoplayer.demo` and `com.google.android.tv.remote`.

These were disabled through that button on this unit, with home, Live TV, the HDMI inputs and a reboot checked
afterwards:

```text
com.google.android.gms              com.google.android.gsf             com.google.android.gsf.notouch
com.android.vending                 com.google.android.apps.mediashell com.google.android.katniss
com.google.android.speech.pumpkin   com.google.android.tts             com.google.android.tv.voiceinput
com.google.android.sss              com.google.android.sss.authbridge  com.google.android.partnersetup
com.google.android.onetimeinitializer com.google.android.backuptransport com.google.android.feedback
com.google.android.music            com.google.android.play.games      com.google.android.videos
com.google.android.youtube.tv       com.google.android.syncadapters.calendar com.google.android.syncadapters.contacts
com.netflix.ninja                   com.teamviewer.quicksupport.market org.droidtv.nettv_launcherapp
com.android.camera                  com.android.inputmethod.pinyin     com.android.printspooler
com.android.musicfx                 com.android.dreams.basic           com.android.htmlviewer
```

Owner's choice on this broadcast-only set: com.google.android.gms, com.google.android.gsf, com.google.android.gsf.notouch and com.android.vending stay enabled on any TV that uses Google apps.

Disabling Play services and the Play Store was a choice for a TV that only shows broadcast channels: the
leanback home and Live TV kept working and a reboot came up straight into Live TV. `MemFree` was 58 MB on the
stock install; after the changes `dumpsys meminfo` reported about 330 to 390 MB Free RAM, cached memory included
(that figure was not taken before the changes). Leave them enabled if you want any Google app on the TV itself. The Philips Live TV stack was left
alone: `org.droidtv.playtv`, `tunerservice`, `channels`, `epg`, `teletext`, `mheg`, `ci`, `xtv`, `tvserver`,
`settings`, `GlobalKey`, the HDMI/SCART/component services and `tvcontentprovider`.

## Why the preloaded apps no longer play

With no Play Store updates since the factory image (a fresh reset leaves 2017 builds), the preloads fail:
Netflix 3.3.2 shows error `-121`, YouTube 1.2.7 shows "action not allowed". Of current apps, the newest
minSdk 22 Netflix build found (8.3.11) installs over the system app, but Disney+'s last API 22 build is refused by its
server with "update required" (current builds need API 23) and Prime Video stops at "temporary problem". A new
Google account could not sign in on Play services 8.3 either: the web step asks for 2-Step Verification and a
seven-day wait. A streaming stick on HDMI is the practical way to keep this panel.

## Using it as a screen for a stick

- HDMI inputs open with the passthrough URI, which switches the panel straight to that port:
  `content://android.media.tv/passthrough/org.droidtv.hdmiService%2F.HdmiService%2FHW9`. `HW9` to `HW12` are
  HDMI 1 to 4 here (checked with `dumpsys hdmi_control`, routing path `0x1000` to `0x4000`).
- The remote's TV key (`KEYCODE_TV`) returns to the tuner. CEC (EasyLink) is on by default, so a stick's own
  Home key also switches the panel to it.
- An accessibility service with `flagRequestFilterKeyEvents` does receive the physical remote's keys on this
  Android 5.1, so an otherwise unused Philips key (Android key code 326 on this remote) can be mapped to the
  passthrough intent in a small app of your own. Key events injected with `adb shell input` did not reach the
  service; test with the real remote.
- The only accessibility service enabled out of the box is Philips' own
  `org.droidtv.nettvadvert/.advertisementimpressions.AdvertisementImpressionAccessibilityService`, which reports
  ad impressions. Setting `enabled_accessibility_services` to your own service replaces it.

## Other notes

- Network adb hung once (`offline`, no reply to the handshake) after relaunching an app; only a mains power cycle
  brought it back.
- Total audio loss that needs a power cycle was reported on this set before any change was made. The audio
  service (`ampservice`) logs `MV_OSAL_Mutex_Lock handle is null` continuously; the cause was not narrowed down.
- Status: 2026-11-04, one unit, one day of use after the changes above. Live TV was checked through the video
  decoder logs and the routing path, not per source. Not checked: the Sources menu, HbbTV, USB playback.
