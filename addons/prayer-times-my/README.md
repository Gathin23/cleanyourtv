# Solat MY

Malaysia prayer times on the TV, from official JAKIM data. Full-screen page with Subuh, Syuruk, Zohor,
Asar, Maghrib and Isyak (each with its Arabic name), the next prayer highlighted with a countdown, and
today's Gregorian and Hijri dates. For the [Clean Your TV](../../README.md) launcher it declares a
[module card](../../docs/modules.md): answer **Show card** once and its tile reads "Solat MY" with a
green glow. On any other Android TV home it is an ordinary app.

This is a fork addition by [@9M2PJU](https://github.com/9M2PJU): it covers Malaysia zones via
[api.waktusolat.app](https://api.waktusolat.app/) (free, no key), while the older
[ Prayer Times](../prayer-times/) add-on covers any city via Aladhan coordinates.

## Data and zones

- Source: [api.waktusolat.app](https://api.waktusolat.app/) `v2/solat/{zone}`, official JAKIM data.
- Default zone is `WLY01` (Kuala Lumpur / Putrajaya), set in `app/src/main/res/values/config.xml`.
- 30 built-in zones covering every state (code — area):
  `WLY01` KL/Putrajaya, `WLY02` Labuan,
  `SGR01` Selangor (Gombak/Petaling/Sepang), `SGR02` Selangor (K. Selangor/Sabak Bernam),
  `SGR03` Selangor (Klang),
  `JHR01` Johor (P. Aur/Pemanggil), `JHR02` Johor Bahru/Kulai/Mersing, `JHR03` Kluang/Pontian,
  `JHR04` Batu Pahat/Muar/Segamat,
  `KDH01` Alor Setar/Kubang Pasu, `KDH02` Sg Petani/Yan/Pendang, `KDH05` Kulim, `KDH06` Langkawi,
  `KTN01` Kelantan, `MLK01` Melaka, `NGS01` Tampin/Jempol, `NGS02` Seremban/Port Dickson,
  `PHG02` Kuantan/Pekan, `PHG04` Bentong/Lipis/Raub,
  `PLS01` Perlis, `PNG01` Pulau Pinang, `PRK02` Ipoh/Kampar, `PRK06` Taiping,
  `TRG01` Kuala Terengganu, `TRG02` Besut/Setiu, `TRG04` Dungun/Kemaman,
  `SBH04` Kota Kinabalu, `SBH02` Tawau/Lahad Datu, `SWK04` Sibu/Kapit, `SWK08` Kuching.
- Change the zone on the TV with **Left/Right** on the page; the choice is remembered on the TV.
  No location permission is used on purpose: JAKIM zones are fixed codes and TVs have no GPS, so a
  picker is more accurate than a location fix and reveals nothing about the house.

## Behaviour

- Fetches the month once when the page opens and again after midnight or a month change
  (`?year=&month=`); the countdown ticks locally every minute, so there is no polling.
- Times are Malaysia time (`Asia/Kuala_Lumpur`), 24-hour. Syuruk is shown but never counted as "next".
- Offline: the last cached month for that zone stays on screen with a note, instead of a blank page.
- D-pad: Left/Right switches zone, Back exits. No focusables, no settings screen to get lost in.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | api.waktusolat.app: this month's times for the chosen zone only. |

That is all. Sent: zone code, year, month. No account, no telemetry, no ads, no background service, no
location permission, no launcher changes. Backups are off.

## Build and install

Needs JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher:

```bash
cd addons/prayer-times-my && ./gradlew assembleRelease
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py --push app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> ../../tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
```

Press Home: the launcher asks once whether to show the card. Change the answer later by holding OK on
Settings, then Module cards. The release build is signed with the debug key so it installs over ADB like
the launcher; sign your own builds with your own key if you hand them out (the launcher pins its Show
answer to the signing key).

## Status on this fork

- Status: works on the emulator; **not yet verified on a real TV.**
- `screenshot.jpg` in this folder is a generated placeholder (correct size, not a TV capture) so CI's
  shape checks pass. Replace it after a real run:
  `TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --screenshot shot.png`, then save as 1280×720
  JPEG under 300 KB (blur Wi-Fi names): `sips -s format jpeg -s formatOptions 85 -z 720 1280 shot.png --out addons/prayer-times-my/screenshot.jpg`.
- Then update the `Tested on` line below and the row in `addons/README.md` with your TV model and
  Android/Google TV version.

## Credits

Prayer icons: [Lucide](https://lucide.dev) (ISC) and [Feather](https://feathericons.com) (MIT). Font: Outfit
(SIL Open Font License, `FONT-LICENSE-OFL.txt`). Data: [Waktu Solat Malaysia](https://api.waktusolat.app/)
(JAKIM source data). Layout follows [addons/prayer-times/](../prayer-times/).

## Tested on

Emulator (Android TV, API 34): page render, countdown, Left/Right zone switch, cached offline month, and
card declaration (`DOCK_CARD` filter + card meta-data). Real-TV run pending — see Status above.

## Maintainer

[@9M2PJU](https://github.com/9M2PJU) (fork addition).
