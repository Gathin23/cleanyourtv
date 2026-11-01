# Sharp AQUOS-TVE19C

A European Sharp (UMC) set on a CVTE board with an MStar/MediaTek
SoC, Android TV 11 (firmware RTOB.250721.001), stock Google-TV-style home on
`com.google.android.tvlauncher`. The list ran for over a week before this PR, with the
launcher from `launcher/` as Home.

## Getting ADB to connect

USB debugging alone does not open port 5555 on this firmware. Turn the toggle on in
Developer options, then **reboot the TV** (Settings → Device Preferences → About → Restart);
the port is open after the restart. There is no separate network-debugging toggle.

## Remote

The remote pairs over Bluetooth LE (`TV BLE Remote`). Its Netflix button arrives as the
global key `KEYCODE_STEM_1`, routed to the (disabled) Netflix observer, so it does nothing
after the debloat. With the key helper enabled (`HOME_KEY_SERVICE=1` at install) it can be
remapped in a theme's `config.json`:

```json
{ "app_keys": ["KEYCODE_STEM_1:org.jellyfin.androidtv"] }
```

## What must stay enabled

- `com.mediatek.wwtv.tvcenter` (Live TV, the launcher's default `live_tv_package`) and
  `com.mediatek.tvinput` - tuner and HDMI inputs
- `com.mediatek.tv.service` and `com.cvte.hotkey.dispatcher` - vendor key handling
- `com.airoha.android.btd` - the BLE remote

