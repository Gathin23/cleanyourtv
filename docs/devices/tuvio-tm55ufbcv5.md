# Tuvio TM55UFBCV5 (Changhong AI PONT, Google TV on Android 11): read from the Settings screens, one app disabled

This is an owner-observed note, **not a tested debloat list** and not an `examples/` folder. It covers one 55-inch
unit with the stock Google TV Home. Nothing here was read over ADB: the TV is in a different home from the
computer, so every fact below comes from photos of the TV's own Settings screens taken on 2026-11-07, plus the
owner's account of daily use.

The other Tuvio folder, `examples/tuvio-td43ufbcv5`, is the same `UFBCV5` series but a newer firmware (Google TV 14,
`UKNV.260506.001`). This set runs the older Android 11 line, so that list does not apply to it as written. The
closest list in this repository is `examples/benq-e55-750` (Changhong AI PONT, Android 11, `RTM8.240401.136`).

| Field | Shown in Settings > System > About |
|---|---|
| Model | `AI PONT` |
| Android | `11`, security patch 1 September 2024 |
| Build | `RTM8.240401.221` |
| Kernel | `4.19.116`, built 29 September 2024 |
| Software version | `V01.04023` |

## What was changed

Settings > Apps > All apps lists one entry under "Disabled apps": **Prime Video**
(`com.amazon.amazonvideo.livingroom`), disabled from the TV's own Settings. The owner has used the TV like that for
several days and reports nothing broken: the stock Google TV Home, the Yandex apps (Kinopoisk) and everything else
work. Everything else on the set is untouched.

## Developer options

Developer options has **USB debugging** but no Wireless debugging entry, which matches what
`examples/benq-e55-750/debloat.txt` records for the same firmware line. That list says USB debugging alone opens
port 5555; on this unit the port was not checked.

## Apps shown in the system list, all still enabled

These are the labels as the Russian-language Settings shows them, for whoever writes the list for this set. Only
Prime Video's package name is confirmed above; the others need `pm list packages` before they go in a list.

- Google: Google TV, Google Partner Setup, Google Play Games, Google Cast, the "Sleep mode" screensaver
  (`Спящий режим`, 42.5 MB), Android TV Remote Service.
- Changhong / Homwee: the store (`Магазин`), MultiScreenShare, the update service (`обновить сервис`),
  DynamicBacklight, LightSensorService, LED Service, SpeakerService, SyncKey, AutoPair.
- Yandex Market: a feedback app (`Отзывы о Маркете`).
- Factory and test: Factorymenu, Factory Key Service, TIF Test. Do not open these, not even to look
  (see the factory-package rule in `PROMPT.md`).

## Not established

The product and board names (`ro.product.name`, `ro.product.board`), RAM, the package names behind the labels above,
whether port 5555 opens with USB debugging alone on this unit, and which of the Yandex apps came preinstalled.
A several-day list needs an ADB session in the TV's own home.
