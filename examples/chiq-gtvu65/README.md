# Second unit: CHiQ L32FM10V

A 32-inch CHiQ on the same Changhong platform as the list beside this note, on a newer firmware line.
These are same-day observations from 2026-11-04, checked across reboots, not a several-day soak.

| Field | Observed value |
|---|---|
| Retail model | CHiQ L32FM10V, Italy |
| Android model / product / device | `AI PONT` / `GTVU65` / `dangni`, MediaTek `mt5867` |
| Android / API | Google TV 14 / 34 |
| Firmware | `UKN6.260116.139` (the list's header unit runs `UKN2.241219.138.A1`) |
| RAM | `MemTotal: 952692 kB` |
| Display | 1920x1080 physical, 1280x720 override, 213 dpi (the same as the header unit) |
| Network | Wi-Fi |
| Stock Home | `com.google.android.apps.tv.launcherx` |
| Live TV app | `com.cltv.hybrid` ("Canali TV", `com.iwedia.cltv.CanaliTvActivity`) |

## What differs from the header unit

- **Wireless debugging only.** With USB debugging on, nothing listened on port 5555. The set advertised
  `_adb-tls-connect._tcp` and needed `adb pair` with the code from Developer options > Wireless debugging.
  `tools/tv.py` worked through that transport with `TV_ADB_BINARY` (`tools/native-adb.md`).
- **setupwraith owned the saved Wi-Fi network** (`dumpsys wifi` showed
  `cname=com.google.android.tungsten.setupwraith`). The owner forgot the network in Settings and joined it
  again, after which it read `cname=android.uid.system:1000`, and only then was setupwraith disabled. The
  TV stayed on Wi-Fi. `tools/disable.sh` refuses setupwraith while it owns the network.
- **setupwraith takes Home back here too.** Its `RecoveryActivity` component read `enabled=false` before
  any change. Yet with only launcherx disabled, Home resolved to `.RecoveryActivity` and
  `launcher/build-and-install.sh` rolled back, as the header describes. With setupwraith disabled the install went through.
- **Live TV.** `com.mediatek.wwtv.tvcenter.plus` was already disabled from the factory, and `com.cltv.hybrid`
  was enabled and is the TV's "Canali TV" tile, the reverse of the header unit. Set `live_tv_package` to
  `com.cltv.hybrid` in `launcher/app/src/main/res/values/config.xml`. Leave both packages as the firmware ships them.
- **`tv.anoki.acr.anokiacroptin`** (Anoki content-recognition opt-in, `/system/priv-app/AnokiAcrOptin`)
  is installed on this firmware and not on UKN2; the list now carries it.
- Not installed on this unit: `android.autoinstalls.config.google.gtvpai`, `com.android.printspooler`,
  `com.iqiyi.i18n.tv`. `tools/disable.sh` reports them as absent and goes on.
- The Inputs menu listed HDMI 1, HDMI 2, AV and Live TV.

## Applied on this unit

From the list: the telemetry and ad services (adservices, feedback, feedback consent and the Anoki opt-in), the
Homwee store and help, Play Games, TalkBack, setupwraith and launcherx (the last through
`launcher/build-and-install.sh`). `com.mediatek.android.leanbacklauncher.partnercustomizer` was **not** tried
on this unit. The owner also turned off streaming apps, the Assistant, Chromecast, the Homwee screen-share
apps and the Ambient screensaver by preference; those stay out of the shared list. The firmware updaters were
left on.

After a reboot (written to disk first with `sync` and a wait), `pm list packages -d` was the same as before
it. Home was the reference launcher. The boot went `FallbackHome` straight to it, with no Live TV in front
and no helper enabled. `com.mediatek.tv.service` was running and the crash buffer was empty.

## Remote: the gear button and held volume

With launcherx disabled, the remote's gear button did nothing. Home, Input, the YouTube and Netflix buttons
and held volume still worked. The gear arrives from the `MStar Smart TV IR Receiver` as `KEY_BRL_DOT2`
(`MSC_SCAN 0040bf72`), which `HomeKeyService` sees as scan code 498. So `settings_key_scancodes` 498 (a
`config.xml`-only key) with `HOME_KEY_SERVICE=1` opened the quick panel on the real remote.

With that helper bound, holding Volume Up or Down stepped **once** per press, the same trade as
`docs/devices/tcl-55p71b-g09-key-repeat.md`. `consume_volume_keys` true did not bring the ramp back, the
same result as that note's G08. The owner chose the ramp and runs without a helper. Undoing the helper
(`settings delete secure enabled_accessibility_services`, `accessibility_enabled 0`) brought the ramp back.

## Undo

```sh
TV_HOST=<tv ip> tools/restore.sh examples/chiq-gtvu65/debloat.txt com.google.android.apps.tv.launcherx
```

## Test limits

Checked on the TV the same day: YouTube and Netflix playback, the Canali TV tile, HDMI 1 from the
launcher's Inputs menu, D-pad navigation, and the real remote's Home, Input, app buttons, gear and held volume. Not
checked: a channel scan, Bluetooth pairing of a new remote, or several days of use. Wireless debugging and USB
debugging were turned off with the authorisations revoked, and after a restart `tools/adb-closed.py` reported
port 5555 and the Wireless debugging port closed.
