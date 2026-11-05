# Skyworth SWTV-20SA-PRO (Android TV 10, SW4H_FF)

Skyworth set, product `SW4H_FF_B`, device `SW4H_FF`, board SW4, MediaTek m7332.
Firmware `QTG3.200617.002`, security patch 2022-02-05. About 1.6 GB RAM, 32-bit armeabi-v7a.
Same firmware build as [karbonn-swtv-20ae](../karbonn-swtv-20ae/), whose device is `SW4H`.
Not the Android 11 `SW4H_FF` build in [tornado-65ua1400e](../tornado-65ua1400e/).

Tested on one set on 4 November 2026, including one reboot. Same-day test, not a multi-day soak.
After the reboot the new launcher was still Home and every package in `debloat.txt` was still disabled.

USB debugging alone opened port 5555. Android 10 has no Wireless debugging screen.

| File | What it is |
|---|---|
| `debloat.txt` | Manufacturer ads, ACR, shop demo, feedback and the stock recommendation rows |
| `config.json` | Live TV package for the Entradas menu; push it with `tools/theme.sh` |

`com.google.android.tungsten.setupwraith` owned a saved Wi-Fi network on this set, so the list
leaves it enabled. Forget that network and join it again in Settings before disabling the wizard.

`com.smartdevice.tvworkwithalexa` was already disabled and is not in the list. It asks for
`TV_INPUT_HARDWARE`. If HDMI or AV is a black picture, enable it again.

`fusion.android.tv.demo` stays commented. On the Tornado SW4H list it receives the remote's Input
key. Here the launcher's Entradas menu still listed the HDMI ports, AV and Live TV with that
package off, and the physical Input key was not pressed.

## Install

```bash
TV_HOST=<tv ip> tools/disable.sh examples/skyworth-swtv-20sa-pro/debloat.txt
TV_HOST=<tv ip> launcher/build-and-install.sh
TV_HOST=<tv ip> tools/theme.sh examples/skyworth-swtv-20sa-pro
HOME_KEY_SERVICE=1 SKIP_BUILD=1 TV_HOST=<tv ip> launcher/build-and-install.sh
```

The nine-dot button is `KEYCODE_APPS` (390), a global key that used to open
`com.google.android.tvlauncher` apps view. With that launcher disabled it does nothing.
`app_keys` `390:home` plus `HomeKeyService` opens this launcher from any app. On this
firmware a bound key helper makes a held volume key step once per press.

## Undo

```bash
TV_HOST=<tv ip> tools/restore.sh examples/skyworth-swtv-20sa-pro/debloat.txt com.google.android.tvlauncher
```
