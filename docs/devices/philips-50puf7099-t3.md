# Philips 50PUF7099/T3 (TPV EBONY_V560, Hisilicon, China): debloat and a BestV Home handover

Owner report for a China-market Philips 50PUF7099/T3. The debloat list for this unit lives in
[`examples/philips-50puf7099-t3/debloat.txt`](../../examples/philips-50puf7099-t3/debloat.txt).

It is a China image, not the TP Vision `org.droidtv` platform the other Philips lists in this repo
cover (`examples/philips-50pus8517`, `examples/philips-tpm191e`, `examples/philips-55pus7503`) and
not the TPV `PH0M_EA_T32` board either (`examples/philips-50pud7406`). Three package families sit
side by side here: `tv.fun.*` (BestV / 百视通), `com.newtv.*` plus `tv.newtv.loginservice` (the
NewTV IPTV platform) and `com.tpv.*` (the board itself). There is no `org.droidtv.xtv`.

## Observed firmware

| Field | Observed value |
|---|---|
| `ro.product.model` | `V560V2_CPFC1_0` |
| `ro.product.device` | `EBONY_V560` |
| `ro.product.manufacturer` | `TPV` (TPV-built, Hisilicon platform) |
| Android / SDK | `9` / `28` |
| `ro.build.display.id` | `PPR1.180610.011 release-keys` |
| RAM | 2 GB (2,027,292 kB total) |
| Storage | 25 GB `/data`, 7.6 GB used |
| `ro.adb.secure` | `0` — port 5555 takes a shell with no pairing prompt |
| `service.adb.tcp.port` | `5555` |
| Google services | none: no `com.google.*` package is installed |
| Stock home | `com.bestv.ott/.home.HomeActivity` (BestV / 百视通), plus `com.android.settings/.FallbackHome` at priority -1000 |
| Live TV / inputs | `com.tpv.tvinput` — the only `TvInputService` on the set |
| Screensaver | none: `screensaver_components` pointed at `com.google.android.deskclock`, which is not installed |

## The stock home is not in KNOWN_STOCK_LAUNCHERS

`com.bestv.ott` is not in the repo's `KNOWN_STOCK_LAUNCHERS` list, so `launcher/build-and-install.sh`
needs it named explicitly, or it refuses the list and changes nothing on the TV:

```bash
STOCK_LAUNCHER=com.bestv.ott TV_HOST=<tv ip> launcher/build-and-install.sh
```

`tools/restore.sh` takes it the same way, as its second argument.

## Do not disable the upgraders without reading this

Three packages on the set hold `INSTALL_PACKAGES`: `tv.fun.tvupgrade`, `tv.fun.appstore` and
`com.tpv.xmic.ota`. `tv.fun.tvupgrade` is the `installerPackageName` of `com.bestv.ott` itself, so
while it lives it can put the BestV launcher back and wipe the whole debloat list.

The list disables all three. **The price is system OTA**: firmware updates have to be sideloaded by
hand from then on. If you want OTA, drop `tv.fun.tvupgrade` from the list, but know that the set may
then restore its own launcher over the CleanYourTV home.

## The screensaver had to be pointed somewhere

This set shipped with **no screensaver at all**. `screensaver_components` pointed at
`com.google.android.deskclock/com.android.deskclock.Screensaver`, that package is not installed, and

```sh
pm query-services -a android.service.dreams.DreamService
```

answers `No services found`. After the launcher install, point it at the launcher's own clock:

```sh
settings put secure screensaver_components com.gathin.tvhome/.ClockDream
settings put secure screensaver_default_component com.gathin.tvhome/.ClockDream
```

## `org.droidtv.usagelogger` and the TV_INPUT_HARDWARE guard

`tools/disable.sh` skips any listed package that asks for `android.permission.TV_INPUT_HARDWARE`,
because that permission usually marks the software layer under the HDMI, AV and tuner inputs
(#321). `org.droidtv.usagelogger` is a false positive on this set: it requests the permission but
drives no input at all. What settles it is which packages actually serve the input service:

```sh
pm query-services --components -a android.media.tv.TvInputService
```

which lists only `com.tpv.tvinput`'s six services (tuner, HDMI, VGA, composite, component, SCART)
and not usagelogger. Disabling it needs `TVINPUT_ALLOW=1`. Same as `examples/cecotec-shandao`.

On the 17 packages in the list, usagelogger is the only one the guard catches: the other packages
that mention the permission hold it as an *installed* permission, not as a requested one.

## What was disabled

17 packages, in three groups — the four the owner picked off the home screen, the background
services, and the upgraders:

| Group | Packages |
| :--- | :--- |
| Casting, camera, video call, smart home | `com.tpv.asc.aiot` (智能连接), `com.hpplay.happyplay.aw` (乐播投屏), `org.droidtv.camera.cn` (相机), `com.tpv.asc.videocall.cn` (视频通话) |
| Telemetry, ads, background services | `tv.fun.bugreporter`, `org.droidtv.usagelogger`, `com.tpv.asc.taadserver`, `com.tianci.ad`, `com.tpv.app.recommendation`, `com.funshion.publicity`, `tv.fun.master`, `tv.fun.ottsecurity`, `tv.newtv.loginservice` |
| Store and upgraders | `tv.fun.tvupgrade`, `tv.fun.appstore`, `tv.fun.sdkmanager`, `com.tpv.xmic.ota` |

The preinstalled content apps (芒果TV, 银河奇异果, QQ音乐, 欢视直播, 百视通, 酷开, 云视听小电视,
欢唱K歌, 云视听极光, NewTV音乐, NewTV体育, IPTV运营商点播), the voice assistant, WeChat casting and
Miracast are all kept: the owner uses them.

## What was checked

Applied with `tools/disable.sh`, then the launcher handover, then a reboot:

- `pm list packages -d` read back exactly the 17 packages listed above — nothing was re-enabled by
  the firmware, and no other package was disabled by accident.
- `cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME`
  answers `com.gathin.tvhome/.MainActivity`.
- `pm query-services --components -a android.media.tv.TvInputService` still answers with
  `com.tpv.tvinput`'s six services, and the input menu still opens and lists HDMI 1, HDMI 2,
  HDMI 3 and composite.
- `com.android.systemui` and `com.android.settings` are running, and logcat shows no
  `FATAL EXCEPTION` from either.
- The four icons the owner wanted gone are gone from the home screen; the content apps are still
  on it.

## Limits

- **One session, one reboot.** The list has not been lived with for days, so this note is the
  record of what was checked, not of what held up over time. `CONTRIBUTING.md` asks for several days
  before a list moves into `examples/`; the list is here so it can be reviewed and corrected.
- **Only injected keys were used** (`deviceId=-1`), never the physical remote. The Home key, the
  app list scrolling, the app library and the theme switch have not been tried with the real remote
  on this set.
- **Physical HDMI picture was not checked.** The input menu lists the sources and the input service
  answers, but no cable was plugged in and no picture was seen.
- **Standby and wake were not tested.** Whether the set comes back to the launcher after a long
  standby is unknown.
- The launcher installed is the repo's own build, `com.gathin.tvhome` version 3.0 (versionCode 9),
  built locally with `-PlauncherMinSdk` left at the default 26.
- Only one unit and one build. Nothing here says another `EBONY_V560` behaves the same.

## Turning it back

```sh
TV_HOST=<tv ip> tools/restore.sh examples/philips-50puf7099-t3/debloat.txt com.bestv.ott
```

Re-enables the 17 packages. Home then needs pointing back at the stock launcher:

```sh
cmd package set-home-activity --user 0 com.bestv.ott/.home.HomeActivity
```

and the screensaver restored to whatever the firmware had (here: nothing — the set shipped without
one).

## ADB

`ro.adb.secure=0` means port 5555 answers any device on the network with a shell and no prompt.
Turn network ADB off again on the set when the work is done.
