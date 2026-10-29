# Sber Salute TV 32" (StarOS on Android 11, MediaTek)

CVTE board `TPD.MT9216.PB766` (MediaTek MT9256, platform `mt5867`), 1 GB RAM, 8 GB eMMC with a 2.7 GB data
partition, panel `ST3151A07-2` (1280×720), `ro.cvte.customer=Sber`. Firmware StarOS 1.103.76 on Android 11, build
`RP1A.200720.011` from 11 June 2026. No Google services. A different board from
[`sber-salute-tv-at30a6`](../sber-salute-tv-at30a6/) (Amlogic, Android 13), with mostly the same Sber apps.

| File | What it is |
|---|---|
| `debloat.txt` | The 27 packages to disable, grouped and commented |
| `config.json` | Dock, hidden apps (factory apps and the ones Sber re-enables at boot), no Live TV entry, Russian names for three Sber apps; push it with `tools/theme.sh` |

## Turning ADB on

Settings → Device Preferences → About, OK on the build number seven times, then Developer options → USB debugging.
Network ADB answers on port 5555 straight away. `ro.adb.secure=0`: there is no "Allow USB debugging?" prompt and
any device on the network gets a shell, so switch debugging off when you are done.

## Install

```bash
TV_HOST=<tv-ip> tools/tv.py 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream'
TV_HOST=<tv-ip> tools/disable.sh examples/sber-salute-tv-pb766/debloat.txt
TV_HOST=<tv-ip> STOCK_LAUNCHER=none launcher/build-and-install.sh
TV_HOST=<tv-ip> tools/theme.sh examples/sber-salute-tv-pb766
```

Run the screensaver line after the launcher is installed (the dream is the launcher's), or before the list if the
launcher is already there. The tested unit also turned screensavers off with
`settings put secure screensaver_enabled 0`. Undo: `settings put secure screensaver_components
ru.sberdevices.starscreensaver/ru.sberdevices.starscreensaver.StarScreenSaverService` and `screensaver_enabled 1`.

The stock launcher stays enabled: this is a CVTE board. `set-home-activity` made the new launcher Home, and Home and
ADB came back after a reboot. The Inputs menu lists HDMI 1–3 and Composite through `com.mediatek.tvinput`.

## What Sber puts back

`ru.sberdevices.services` installs and re-enables the apps it considers mandatory. During the session it installed
`openbeta`, `starkidsroom.prod`, `parentcontrol` and `startube`, re-installed Okko, and after a reboot
`ru.more.play`, `subscriptions`, `smarthome.promo`, `ru.sberdevices.tv` and `rcfupdater` were enabled again.
`config.json` hides them. Check `pm list packages -d` after a reboot rather than re-running the list in a loop.

## Space

`/data` had 472 MB free, close to the ~355 MB at which StarOS starts removing ("rotating") apps (see the
[at30a6 notes](../sber-salute-tv-at30a6/README.md)). `pm clear` on the disabled packages and
`pm uninstall-system-updates` on `starscreensaver`, `starlivetv`, `starbrowser` and `ru.sberdevices.tv` took it to
739 MB. Reverting the updates re-enabled those four, so run the list again afterwards. `smarthome.promo` and
`rcfupdater` refused the revert.

## Apps

Apps are installed and updated only by Sber's platform service and its catalogue (`ru.sberdevices.starmarket`,
"Мои приложения" needs a Sber ID). VK Video and Rutube were updated within minutes of the TV going online; Wink
(`1.48.8_SALUTTV`) and Kinopoisk were still at their September 2024 versions, and Wink showed "Плохое соединение с
Интернетом" while the network was validated and its servers answered. Re-enabling the Sber packages in this list
did not change that, so it is not the list.

`ru.sberdevices.startube` is Sber's YouTube client. It only has a `STAR_LAUNCHER` entry, so other launchers do
not show it, and on the tested unit it answered "Нет доступа к сервису YouTube". The official YouTube app needs
Google Play services, which this firmware does not have.
