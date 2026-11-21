# CHiQ M75Q10S: notes and test limits

Tested on the TV on 2026-11-15, checked across two reboots the same day - not a several-day soak.

| Field | Observed value |
|---|---|
| Retail model | CHiQ M75Q10S, 75" 4K, Hebrew-market unit |
| Android model / product / device | `AI PONT MAX FF` / `G4FFDVB87` / `ventura`, MediaTek `mt5889` |
| Android / API | Google TV 14 / 34 |
| Firmware | `UKNV.260805.001` |
| RAM | `MemTotal: 3744808 kB` |
| Network | Wi-Fi (`cname=android.uid.system:1000` owned the saved network) |
| Stock Home | `com.google.android.apps.tv.launcherx` |
| Live TV app | `com.mediatek.wwtv.tvcenter` |

## What the list does and what was verified

The debloat list disables 19 packages: the Anoki ACR and the Android ad/personalisation services,
the Homwee store and help, the partner-row customizer and partnersetup, the calendar, health,
Dynamic-System and wallpaper-backup background services, Google TTS, and the two stock-home
packages (`com.google.android.tungsten.setupwraith` and `com.google.android.apps.tv.launcherx`).
Nine more were disabled on the tested set by the owner's choice and stay commented out of the
shared list (Prime Video, Netflix, YouTube Music, Play Games, TalkBack, the ambient screensaver
and the three screen-share/Miracast packages).

Two reboots: after the first, `pm list packages -d` listed exactly what the batches sent - the
firmware re-enabled nothing. Home was the repo launcher (`cmd package set-home-activity`, then
`dumpsys activity activities` `mFocusedApp`); the stock home and the setup wizard were disabled
afterwards with `tools/disable.sh` and its Home guard. After the second reboot HDMI, Live TV,
YouTube from a dock tile, D-pad navigation and the remote's Home, Input and volume were checked.
Live TV screenshots come out black (protected video layer), as on the mtk5867 CHiQ notes.

setupwraith's `RecoveryActivity` read `enabled=false` from the factory, but the whole package
still had to be disabled: with only launcherx gone, Home fell to it, the same as
examples/chiq-gtvu65 reports. The saved Wi-Fi network read `cname=android.uid.system:1000`, not
setupwraith's, so nothing needed forgetting first.

## Remote keys on this firmware

`dumpsys window policy` maps the remote's global keys to enabled receivers the list keeps:
STAR/POUND/CLEAR/BUTTON_2 to `com.gdch.gameoptimiser`, most others to
`com.homwee.factory.FactoryKeyReciver` and the MediaTek hotkey dispatcher, KEYCODE_STEM_1 to
`com.mstar.netflixobserver.NetflixKeyReceiver` (opening com.netflix.ninja, which dies with the
commented Netflix line), KEYCODE_SETTINGS/TV_INPUT pairs to
`com.mediatek.tv.service/com.mediatek.hotkey.dispatcher`.

With launcherx disabled and no key helper: Home, Input and volume keys still work; the remote's
gear button, long-press Home (KEYCODE_ALL_APPS), the GUIDE/PROFILE_SWITCH/MACRO_1 keys and the
Netflix key do nothing. The owner declined the key helper
(`HOME_KEY_SERVICE=1 launcher/build-and-install.sh`, `-HomeKeyService` in PowerShell) because of
the volume-ramp trade (docs/devices/tcl-55p71b-g09-key-repeat.md); the gear key needs that helper
(`settings_key_keycodes` 17 or 83) the same way examples/sharp-aquos-tve23a maps it.

## Dock

Four dock slots (TiviMate `ar.tvplayer.tv`, Stremio, SmartTube, YouTube) with the app library on,
set in `launcher/app/src/main/res/values/config.xml` before building. The Twilight theme from
the repo's `themes/twilight` folder was pushed afterwards with the theme tool, and comes off
again with the same tool's reset.

## Undo

```sh
TV_HOST=<tv ip> tools/restore.sh examples/chiq-m75q10s/debloat.txt com.google.android.apps.tv.launcherx
```

Then the commented lines for whatever you also disabled from the owner-choice block, and
`tools/theme.ps1 -Reset` if a theme is on.

## Test limits

Checked the same day: HDMI and Live TV switching from the launcher's Inputs menu, YouTube from a
dock tile, D-pad from every edge, the real remote's Home, Input and held volume, voice input with
the mic button, one `sync` + wait before each reboot, and - after everything - USB debugging,
Network/ADB debugging and Wireless debugging switched off with the authorisations revoked:
`tools/adb-closed.py` reported port 5555 and the Wireless debugging port closed, and after a full
power-off both stayed closed. Not checked: a channel scan, Bluetooth pairing of a new remote,
several days of use, or what the firmware opens at power-on with ADB closed (the DEXP AI PONT
note, examples/dexp-a651, reports Live TV there; launcher/config.xml's `power_on_home` with
`HOME_KEY_SERVICE=1` covers it if wanted).
