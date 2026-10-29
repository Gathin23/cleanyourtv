# Tuvio TQ55UFGSV1 (Yandex TV, CVTE CV9632, Android 11): ADB behind the factory menu, a 32-bit set, and what the Yandex Home keeps

This is an owner-observed note, **not a tested debloat list** and not an `examples/` folder. It covers one 55-inch
unit in daily family use since mid-2026. The other Tuvio folder here, `examples/tuvio-td43ufbcv5`, is a different
machine (Changhong AI PONT on Google TV 14); none of its packages apply to this one. The Home handover was
deliberately not done. Facts below were read from the TV over ADB on 2026-10-25 unless marked as earlier.

| Field | Observed value |
|---|---|
| Android model / brand | `TQ55UFGSV1` / `TUVIO` |
| Android / API | `11` / `30`, security patch `2021-03-05` |
| Build | `RP1A.200720.011`, built 2026-05-25 |
| Fingerprint | `TUVIO/TQ55UFGSV1.HV550QUB-E1D/TQ55UFGSV1.HV550QUB-E1D:11/RP1A.200720.011/4179333235:user/release-keys` |
| Board | `ro.product.board=m7332_eu` (MediaTek MT9632), CVTE chassis `CV9632xx` |
| Application ABIs | `armeabi-v7a,armeabi`: **32-bit only**, an `arm64-v8a` APK fails with `INSTALL_FAILED_NO_MATCHING_ABIS` |
| RAM / storage | `MemTotal` about 1.4 GB; `/data` about 2.7 GB, 56 % used |
| Network | Ethernet; the TV leaves the network entirely when switched off (no link, so no wake-on-LAN) |

## Turning ADB on

Developer options in this Yandex OS build have no ADB switch. What worked was the CVTE service menu: hold **Menu**
for about a second, press **Left, Up, Left, Up**, then **Back**, and in the blue-grey menu switch **ADB / USB
debugging** on. Port 5555 opened and "Allow USB debugging?" appeared on the screen. Leave the panel and backlight
entries in that menu alone. The TV has no USB device mode, so ADB over USB is not an option. After a power cycle the
port came back by itself on the occasions checked; if it does not, repeat the menu steps.

Do not launch `mediatek.factorymenu.ui` or `com.ctv.autotest.helper` from the shell, not even to look
(see the factory-package rule in `PROMPT.md`).

## What was changed, and how far to trust it

| Change | State |
|---|---|
| `com.yandex.android.advid`, `com.yandex.tv.bugreportsender`, `com.yandex.tv.daydream` and `com.yandex.launcher.updaterapp` removed with `pm uninstall --user 0` (September 2026) | Running since. They can be brought back with `cmd package install-existing <pkg>`. Listed here for information only: this repository's lists disable and never uninstall, and these four were not re-tried with `pm disable-user`. |
| `com.cultraview.screensaver` disabled with `pm disable-user --user 0` (2026-10-25) | One session only. Home and the video player kept working; this is not a several-day result. |
| `pm trim-caches` | Freed about 1 GB of cache on `/data` once. |

Everything else is untouched, including the Yandex Home.

## The Yandex Home stays

`com.yandex.tv.home` is the only Home with a window; the framework's `FallbackHome` in `com.android.tv.settings` is the
other candidate. The voice search on the remote (Alice) and the kids' feed are built into that launcher and its
services (`com.yandex.tv.services*`, `com.yandex.io.sdk`), so a third-party launcher loses both. The price is that the
sponsored rows on Home remain. Nothing here removes them.

Packages that look like candidates but were kept, with the reason: `com.yandex.tv.home` and
`com.yandex.tv.services`, `.platform`, `.privileged` (launcher, Alice, platform services; the `AppMetrica` processes
that run inside them cannot be separated), `com.yandex.devices.appstore` (the only app store),
`com.yandex.tv.live`, `com.yandex.tv.input.efir` and `com.mediatek.wwtv.tvcenter` (broadcast TV and the antenna input),
`com.yandex.tv.webplayer`, `.videoplayer`, `.ytplayer` (the players; the YouTube player is a light Cobalt build and
was not the cause of slow playback), `com.yandex.tv.music`, `ru.yandex.androidkeyboard.tv`, `com.mediatek.tv.service`,
`com.mediatek.tvinput`.

## Why the ad rows cannot be cut at the network

Checked from the router: the TV's connections do not pass the router's proxy at all. It has a global IPv6 address and
its own resolvers (`77.88.8.8` and `8.8.8.8` over DHCP, Private DNS on automatic), and the open sockets are mostly
Yandex's own IPv6 ranges (`2a02:6b8::/32`). Feed content, Alice and the ads come from the same servers, so there is
no host that separates "ad" from "content"; a domain block either does nothing or breaks the rows and the voice
search.

## Other things worth knowing

- **Adopted storage does not work.** The kernel (4.19.116) has no `default-key` device-mapper target, so
  `sm partition disk:X private` makes a volume that is `unmountable` (`failed to setup metadata encryption`). A USB
  drive works only as portable media storage, not for apps.
- Apps from a "TV" build of an APK bundle install with `adb install-multiple` when they need split APKs; pick the
  `armeabi_v7a` config split, the `xhdpi` density split (320 dpi screen) and the language splits.
- There is no Play Store and no Play Services on this set, so in-app purchases and leaderboards do not work in
  games, while the games themselves start.
- `screencap` prints a short `Init wrapper...` line before the PNG; cut everything before the `\x89PNG` signature, or
  the file is not recognised.
- The IR remote works with the stock Home; none of the key helpers was enabled.

## Not established

Whether other `TQ55UFGSV1` units or other Tuvio sets on the CVTE CV9632 chassis ship the same firmware, whether the
service-menu key sequence is the same on them, and whether the four Yandex packages above can be disabled instead of
removed. A several-day list needs a second owner or a longer run.
