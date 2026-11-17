# Sharp AQUOS-TVE19C and TVE19A

A European Sharp (UMC) set on a CVTE board with an MStar/MediaTek
SoC, Android TV 11 (firmware RTOB.250721.001), stock Google-TV-style home on
`com.google.android.tvlauncher`. The list ran for over a week before this PR, with the
launcher from `launcher/` as Home.

## Getting ADB to connect

USB debugging alone does not open port 5555 on this firmware. Turn the toggle on in
Developer options, then **reboot the TV** (Settings → Device Preferences → About → Restart);
the port is open after the restart. There is no separate network-debugging toggle.
On the TVE19A unit the port opened as soon as USB debugging was on, without a reboot.

## Remote

The remote pairs over Bluetooth LE (`TV BLE Remote`). Its Netflix button arrives as the
global key `KEYCODE_STEM_1`, routed to the (disabled) Netflix observer, so it does nothing
after the debloat. With the key helper enabled (`HOME_KEY_SERVICE=1` at install) it can be
remapped in a theme's `config.json`:

```json
{ "app_keys": ["KEYCODE_STEM_1:org.jellyfin.androidtv"] }
```

On the TVE19A unit the key helper costs the volume ramp: a held volume key steps the volume once.
`consume_volume_keys` did not change that. With the original button mapping and the key helper
off, the ramp works. To turn the key helper off:

```sh
TV_HOST=<tv-ip> tools/venv/bin/python -I tools/tv.py 'settings delete secure enabled_accessibility_services'
TV_HOST=<tv-ip> tools/venv/bin/python -I tools/tv.py 'settings put secure accessibility_enabled 0'
```

## What must stay enabled

- `com.mediatek.wwtv.tvcenter` (Live TV, the launcher's default `live_tv_package`) and
  `com.mediatek.tvinput` - tuner and HDMI inputs
- `com.mediatek.tv.service` and `com.cvte.hotkey.dispatcher` - vendor key handling
- `com.airoha.android.btd` - the BLE remote

## Second unit: TVE19A

A Sharp AQUOS-TVE19A bought in Poland: same board (`sindang`), SoC family and firmware
(RTOB.250721.001), product `tcbrae_a`, m7322 SoC, 1.4 GB RAM. A variant of this list has been in daily
use on it since 2026-11-04 (several days), with the launcher from `launcher/` as Home. That owner kept Digitalmedia Player,
the e-manual, the Assistant, Smart Connect and the ambient screensaver, and disabled Google TV /
Play Movies and the Speedtest preload. Netflix, YouTube, YouTube Music, Play Games, `com.cltv.fast`
and the Freeview apps came factory-disabled.

Checked on this unit:

- After a reboot every disabled package stays disabled and the launcher takes Home.
- D-pad navigation in the launcher, the remote's Netflix, YouTube and volume buttons.
- HDMI-CEC and ARC with a soundbar: ARC connects, System Audio mode is on, TV and soundbar volume
  follow the remote.
- AdGuard Private DNS validates and SmartTube plays.

Remote:

| Button | HID usage | Key | Handled by |
|---|---|---|---|
| Netflix | `0x0c018e` | `KEYCODE_STEM_1` (265) | `com.mediatek.tv.service` (NetflixObserver) |
| YouTube | `0x0c0073` | `KEYCODE_TV_INPUT_COMPOSITE_1` (scan 592) | `com.cvte.hotkey.dispatcher` |

- With Netflix disabled, each Netflix press crashes `com.mediatek.tv.service`. The service
  restarts itself and the TV keeps working. Nothing else happens.
- The YouTube button does nothing while YouTube is disabled.

Audio:

- Only HDMI 4 has ARC. ARC carries Dolby Digital and DD+, not TrueHD.
- A player on a TV HDMI input that sends TrueHD gives no sound through ARC. Connect that player
  to the soundbar's HDMI input and the soundbar's HDMI output to TV HDMI 4.
