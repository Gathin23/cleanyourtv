# PPTV p34a4p (Suning PPTV, Amlogic, China, Android 5.1.1): three ad layers behind a locked launcher

An owner report from one set, backing the list in `examples/pptv-p34a4p/`. The list removes
layer 1 of the ads; this note covers what a package list cannot reach on this firmware: the
boot-animation video ad, the network-layer ads, and why the stock launcher cannot be replaced.

| Field | Observed value |
|---|---|
| Retail model | `p34a4p` (Suning PPTV) |
| Android / API | 5.1.1 / 22 |
| Build | `LMY47V.20251117` |
| Stock home | `com.pptv.launcher` |
| Live TV | `com.pptv.tv` |
| RAM / storage | 1 GB RAM |
| ADB | port 5555, no auth (`ro.adb.secure=0`); network debugging re-opened by the owner after each power cycle |

## `pm disable-user` is refused

`pm disable-user --user 0 <pkg>` fails for every package with a SecurityException (attempt to
change component state — the same wall as the [FFalcon r34a0](../../examples/ffalcon-r34a0-cn/debloat.txt)
and [Whaley WTV55K1](whaley-wtv55k1.md) sets on Android 5.x). The repo's `tools/disable.sh`
and `tools/restore.sh` cannot work here; the list is applied with `pm hide`, undone with
`pm unhide`, and the hidden state survives reboots.

## Layer 2: the boot-animation video ad

Amlogic's `/system/bin/bootanimation` on this build plays a ~35 s video ad before Home
(strings `has video advert` / `playAdvert` in the binary; the cache lives in `/data/tvad`,
system-only; the stock clip is `/system/media/settings_video.mp4`). No app draws it, so no
package list and no DNS rule touches it. Five properties turn it off, and they survive reboots:

```sh
adb shell "setprop persist.sys.videoadvert 0; setprop persist.sys.picadvert 0; \
            setprop persist.sys.advert 0; setprop persist.sys.advert_enable 0; \
            setprop persist.sys.advert_status 0"
```

Something flips `persist.sys.advert` back to 1 after boot; with `videoadvert=0` the ad stays
gone regardless.

## Layer 3: network-layer ads

What the package list cannot reach: the stock launcher embeds an ad SDK that pulls banner images
from `hifuntv.com` and `huan.tv`, and the LeBo cast app (`com.hpplay.happyplay.aw`, the DLNA/AirPlay
receiver this TV actually uses) serves `ad.hpplay.cn` promos before casting. Blocking them on the
network is outside what this repo covers; if you do, keep `cibntv.net` (the video apps' streams and
the bundled Dangbei Market) and `sdkauth.hpplay.cn` / `vipauth.hpplay.cn` (cast authentication)
reachable, or playback and casting break.

## The launcher lock-in

The stock launcher's HOME intent filter carries a custom `android.intent.category.HOME.pptv`
category (besides `TV_HOME`, `COMMON_HOME`, `HOME` and `DEFAULT`), and no HOME chooser ever
appears. Hiding `com.pptv.launcher` does work at run time — a third-party launcher takes the
HOME key immediately — but after a reboot the key goes dead: the boot-time HOME resolution
finds no candidate, the set comes up on a system info page, and only `pm unhide` over ADB
brings it back. ADB survives the whole procedure, so the state is recoverable, but the stock
launcher stays.

A third-party launcher (Dangbei 3.3.6, from the vendor's own download links) sideloads and
runs on this set — ~88 MB RSS against the stock launcher's ~125 MB — but cannot be set Home
by the mechanism above. The vendor's build of the Dangbei Market filters desktop apps out of
its catalog entirely (its own API serves the app list; no launcher category is returned).
Repacking a launcher with `HOME.pptv` in its filter was not tried.

## The reinstall watchdog

`com.pptv.search`, `com.pptv.usercenter` and the launcher each embed a `MyInstallReceiver`
that listens for `PACKAGE_REMOVED` and tries to download and reinstall the package
(log keyword `installAppDownload`). It needs the `INSTALL_PACKAGES` permission, so once the
three packages that hold it are hidden the chain is dead — nothing came back across a day
of reboots. `com.pptv.usercenter`'s receiver is broken from the factory (`ClassNotFoundException`
in its own logs).

## The batch that crashed, and why the list is short

A second batch of eight hides (`search`, `usercenter`, `terminalmanager`, `tvhousekeeper`,
`microhelper`, `remoteota`, `upgrad`, `karaok`) put the launcher in a crash loop at boot
until all eight were unhidden; the culprit was never isolated, so the shipped list keeps all
of them and documents the experiment. Hide one at a time, with a reboot in between.
