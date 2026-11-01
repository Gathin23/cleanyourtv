# Hi VHIX-43F169MSY: Yandex TV on a 1 GB CVTE board

Observed on one owner-operated set on 20–22 October 2026. This is a device report,
not a general debloat list: the disabled set has only about two days of use, and
launcher 2.6 has only a short verification session. No root or firmware flashing.

## Match the board and firmware

- Model: `VHIX-43F169MSY`, marketed as Hi / Yandex TV.
- `ro.cvte.boardname`: `TP.SK506S.PB802`.
- `ro.hardware` and `ro.board.platform`: `m5621`.
- Android 9, API 28; display ID `PPR2.180905.006.A1 dev-keys`, security patch
  `2019-06-01`; incremental build `1475236709`, build date 7 October 2022.
- `armeabi-v7a`, four Cortex-A53 cores, `ro.config.low_ram=true`.
- `/proc/meminfo`: 915396 kB total RAM; swap capacity 204796 kB.
- `/data` is about 2.9 GB. The installed Yandex TV Home version was `2.255.81`.

These observations do not establish compatibility with another Hi model, another
CVTE board, or a similarly named UHD model.

## Home and settings

The original Home component is `com.yandex.tv.home/.HomeActivity`. The replacement
`com.gathin.tvhome/.MainActivity` was opened first, made Home, and the original
package disabled for user 0. Home and network ADB returned after reboots with both
launcher 2.4 and 2.6. No accessibility key helper was added in these sessions.

The firmware also exposes `com.example.user.myapplication/.FallbackHome` as a
fallback Home. This is an observed component, not a recovery guarantee; keep the
stock launcher installed and do not rely on FallbackHome to repair a broken boot.

The generic Settings intent resolves to `com.android.tv.settings/.MainSettings`,
but that APK crashes on this set with missing vendor/framework classes, including
`ConnectivityListener$2` and `MtkTvAipqTypes$SourceActivity`. Its network activity
also failed. The cause of those missing classes was not established.

The working TV settings activity is:

```text
com.cvte.tv.customer.settings/com.yandex.tv.settings.MainActivity
```

It opens picture, sound, HDMI CEC and channel settings. It is not a verified route
to Wi-Fi settings. The locally tested launcher build routed the Settings pill to
this component; **unmodified upstream 2.6 was not tested with that routing**. This
report does not add a global hard-coded vendor fallback to the launcher.

The Inputs popup lists HDMI 1–3, Component, Composite and VGA. Listing those rows
is not a test of a connected signal, HDMI audio or CEC. Tuner reception, casting,
Bluetooth pairing, physical app buttons and long-press actions were not verified.

To return to the stock Home while ADB is still available:

```sh
adb shell pm enable --user 0 com.yandex.tv.home
adb shell cmd package set-home-activity --user 0 com.yandex.tv.home/.HomeActivity
adb shell input keyevent KEYCODE_HOME
```

## Packages observed disabled

The following are the owner's choices, not a ready-to-apply list. Voice search,
Yandex Music, the Yandex screensaver and DLNA were not required by this owner.
Disabling them loses the corresponding features.

- `com.yandex.tv.bugreportsender`
- `com.yandex.prebuiltstub`
- `com.yandex.tv.daydream`
- `com.yandex.android.advid`
- `com.yandex.tv.alice`
- `com.yandex.tv.music`
- `com.yandex.tv.webplayer`
- `com.yandex.tv.videoplayer`
- `com.cvte.dssshare.dlna`
- `com.yandex.tv.home` (only after replacement Home was running)
- `com.yandex.launcher.updaterapp` (updates intentionally disabled by the owner;
  leave enabled if updates are wanted, and re-enable before updating)

Kinopoisk was retained and opened successfully. The sole installed YouTube client
was `com.yandex.tv.ytplayer`; do not remove it under the assumption that a Google
YouTube package is available instead.

`com.yandex.io.sdk` was initially disabled, then **re-enabled**: Yandex TV services
attempted to bind to its configuration service. Keep it enabled on this setup.
The observed `com.yandex.tv.services.platform` process also binds to
`com.yandex.io.sdk/.YandexIoConfigService` and hosts `TvHdmiService`.

Keep `com.yandex.tv.services`, `com.yandex.tv.services.platform`,
`com.yandex.tv.setupwizard`, `com.yandex.tv.vendor.config`,
`ru.yandex.androidkeyboard.tv`, the MediaTek input/hotkey stack and CVTE TV/input
services. Attempts to disable individual AppMetrica components were refused by
the shell; no root workaround was used. Factory/test packages were not opened as
part of this follow-up.

## Low-memory behavior and the 2.6 follow-up

After almost two days of use, with VK Video in front and a VPN foreground service
running, available RAM was about 164 MB and swap usage about 202 of 205 MB. There
was also a saved `Last ANR service` record for VK's `AppMeasurementService`, while
`dumpsys activity lastanr` reported no ANR since boot. This does not prove VK or
the VPN caused every delay. The four-core CPU still had substantial idle time;
a load average around 60 on this set is not evidence of 60 runnable CPU tasks.

The follow-up installed a locally built 2.6 release with the settings routing
above and the owner's dock order. `reduced_motion` was enabled, `ambient_glow`
disabled, and advanced effects left off. All three system animation scales were
changed from 0.5 to 0. VK and cached Yandex Live were stopped during diagnosis;
app data and VPN subscription data were retained.

A short sequence of twelve D-pad keys, with `gfxinfo` reset first, gave:

- Before: 79/133 janky frames (59.40%), p50 20 ms, p95 48 ms.
- After the combined changes, before reboot: 19/67 (28.36%), p50 9 ms, p95 27 ms.

The number of rendered frames differs because motion changed. These are small,
uncontrolled samples of the whole intervention, **not an isolated 2.4-versus-2.6
benchmark** or proof that long-term memory pressure is fixed. A cold launcher
start (`am start -W`, `ThisTime`) was 1708 ms before and 1172 ms after, also before
reboot.

After reboot, before opening the VPN UI, available RAM was about 301 MB and swap
usage about 55 MB. After opening the VPN, Kinopoisk, Jellyfin and TV settings,
swap was about 150 MB; memory recovered by reboot is not a lasting saving.
Kinopoisk and Jellyfin opened successfully. Measured returns to Home ranged from
123 to 2619 ms, so this report does not claim an instant return in every case.
The VPN foreground service and tunnel were present after opening its UI;
unattended VPN autostart was not established.

An earlier `activity_manager_constants=max_cached_processes=2` experiment read
back as `null` after reboot. It is not a verified persistent optimization for
this firmware and was not repeated. No periodic task killer or auto-reboot job
was installed. Longer observation is needed before promoting this report to a
package list.
