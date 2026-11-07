# Hyundai AI PONT CO / Changhong `osaka` (Android 14): launcher redirect and a same-session cleanup

This is an owner-observed note, **not a tested or reusable debloat list**. The
TV is sold as a Hyundai AI PONT CO, but Android reports
Changhong as the manufacturer. Every claim below was observed on one unit in a
single maintenance session and after one reboot; it has not been lived with for
the several days required for a reusable list. Check the build before copying
any package decision to another TV.

## Observed firmware

| Field | Observed value |
|---|---|
| Retail model | Hyundai AI PONT CO |
| Manufacturer / product | `Changhong` / `DVB86HY` |
| Android model / device | `DVB86HY` / `osaka` |
| Android / API | `14` / `34` |
| Board | `mt5867` |
| Build display id | `UKNV.260604.001` |
| Build fingerprint | `HYUNDAI/DVB86HY/osaka:14/UKNV.260604.001/15566512:user/release-keys` |
| Security patch | `2026-06-01` |
| Stock Home | `com.google.android.apps.tv.launcherx/.home.HomeActivity` |
| TV input stack | `com.mediatek.tvinput` (tuner, composite and HDMI inputs) |

## Same-session package changes

The following eight packages were disabled with `pm disable-user --user 0`, in three
small batches, and were still disabled after the reboot. Nothing was
uninstalled:

```text
tv.anoki.acr.anokiacroptin
com.google.android.feedback
com.apple.atve.androidtv.appletv
com.iqiyi.i18n.tv
com.mercadolibre.android.mplay_tv
com.rcntv.rcntv
com.esaba.downloader
com.google.android.play.games
```

This was an owner-selected cleanup for this unit, not a recommendation to
apply blindly. Google TV's stock `launcherx` and `setupwraith` were kept,
along with the voice, Cast, mirroring, update, file, input, Bluetooth, Play
Store and Play Services components. The OEM Help/Store and MediaTek/Homwee
middleware were also kept because their dependencies were not established.

Streaming apps kept and checked were YouTube/YouTube Music, Netflix, Prime
Video, Disney+ and Max.

## Home and launcher

`cmd package set-home-activity` and the Home role reported the replacement
launcher, but the device continued to resolve Home to
`com.google.android.apps.tv.launcherx/.home.HomeActivity`. Disabling the stock
launcher was not used as a workaround because it is part of the working TV
and setup stack on this firmware.

The reference launcher was installed and used with its `HomeKeyService`
redirecting the stock Google TV Home screen to it. The redirect was checked
after the reboot. The helper was configured without key filtering, so the
physical remote's held-volume behaviour was preserved. Home, volume repeat,
Settings, app buttons, Inputs, HDMI/TV playback, voice and Cast were checked
with the real remote during the session; playback was checked again after
Private DNS was enabled.

The personal launcher layout used five dock slots, in this order:

```text
YouTube → Netflix → Prime Video → Disney+ → Max
```

That layout is not part of this device note or a proposed default theme.

## ADB and recovery state

After the work, Wireless debugging, Network/ADB debugging and USB debugging
were disabled, and authorisations were revoked. Before the final reboot,
`tools/adb-closed.py` reported the active wireless TLS connection port closed;
after the reboot it reported plaintext port 5555 closed as well. The original
package and settings state was backed up locally before the first change; those
backups and screenshots are intentionally not included here.

## Limits

- This is one TV, one build and one maintenance session plus one reboot.
- The eight packages have not been lived with for several days, so no
  `examples/` list is published.
- Another AI PONT CO or another `osaka` build may differ; compare
  `ro.build.display.id` and the package state first.
- The launcher redirect was observed on this firmware, but the stock Home
  remains enabled and the redirect is not a replacement for testing the
  physical Home key on another unit.
