# Sharp AQUOS-TVE19A

A European Sharp (UMC) set, bought in Poland. It has the same CVTE board (`sindang`), SoC and
firmware (RTOB.250721.001) as `examples/sharp-aquos-tve19c/`. The list has been in daily use since
2026-11-04 (two days), with the launcher from `launcher/` as Home. Read that folder too. The
notes below are what was different on this unit.

## What was checked

- After a reboot every disabled package stays disabled and the launcher takes Home (no Live TV takeover).
- D-pad navigation in the launcher, the remote's Netflix, YouTube and volume buttons.
- HDMI-CEC and ARC with a soundbar on HDMI 4: ARC connects, System Audio mode is on, TV and
  soundbar volume follow the remote.
- AdGuard Private DNS validates and SmartTube plays.

## Getting ADB to connect

On this unit port 5555 opened as soon as USB debugging was on, without a reboot. The TVE19C
needed a reboot. If `tools/tv.py` cannot connect, reboot the TV once.

## Remote

The remote pairs over Bluetooth LE (`TV BLE Remote`).

| Button | HID usage | Key | Handled by |
|---|---|---|---|
| Netflix | `0x0c018e` | `KEYCODE_STEM_1` (265) | `com.mediatek.tv.service` (NetflixObserver) |
| YouTube | `0x0c0073` | `KEYCODE_TV_INPUT_COMPOSITE_1` (scan 592) | `com.cvte.hotkey.dispatcher` |

- With Netflix disabled, each Netflix press crashes `com.mediatek.tv.service`. The service
  restarts itself and the TV keeps working. Nothing else happens.
- The YouTube button does nothing while YouTube is disabled.

### Remapping costs the volume ramp

`app_keys` needs the key helper (`HOME_KEY_SERVICE=1` at install). On this TV, with the key
helper enabled, a held volume key steps the volume once instead of ramping. `consume_volume_keys`
did not change that. The owner kept the volume ramp, so the buttons are not remapped. To undo the
key helper:

```sh
TV_HOST=<tv-ip> tools/venv/bin/python -I tools/tv.py 'settings delete secure enabled_accessibility_services'
TV_HOST=<tv-ip> tools/venv/bin/python -I tools/tv.py 'settings put secure accessibility_enabled 0'
```

## Audio

- Only HDMI 4 has ARC. ARC carries Dolby Digital and DD+, not TrueHD.
- A player on a TV HDMI input that sends TrueHD gives no sound through ARC. Connect that player
  to the soundbar's HDMI input and the soundbar's HDMI output to TV HDMI 4.

## Hardware

1.4 GB RAM (about 380 MB free with the launcher running), 4 cores, /data 4.2 GB.
HDR10 and HLG, no Dolby Vision.
