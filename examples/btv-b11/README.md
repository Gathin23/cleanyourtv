# BTV B11

Brazil-market Android TV 9 streaming box on an Amlogic reference build (`Amlogic/franklin/franklin:9/PPR1.180610.011/20210527:user/test-keys`),
2 GB of RAM. Tested on one unit over Wi-Fi, with reboots between batches.

## Connecting

Network ADB is on **port 5188**, not 5555 (`service.adb.tcp.port=5188`). Switch on USB debugging in
Developer options, then use `TV_HOST=<tv ip>:5188` with `tools/tv.py` and every other script. The port
closes again once USB debugging is off.

## Launcher

- The stock Home is `com.bb.launcher2`. Pass `STOCK_LAUNCHER=com.bb.launcher2` to
  `launcher/build-and-install.sh`, which then disables it once the new launcher is confirmed as Home.
- The preloaded Netflix is the phone build (`com.netflix.mediaclient`, no leanback entry), so the launcher
  does not list it by itself. Add it to `library_apps` in `launcher/app/src/main/res/values/config.xml`
  (the Play Store, `com.android.vending`, is the same) to put it on Home or in the dock.
- There is no tuner or HDMI input: leave `live_tv_package` empty.

## Things to know about this firmware

- SELinux is **permissive** out of the box (`getenforce`), and the build is signed with test keys.
- `com.android.icore` is not an Android package despite its name: it sits in `/data/app` with obfuscated
  classes and can install and remove apps. The list disables it.
- `com.bb.ota` and `com.bb.otaback` run as the system uid from `/data/app`; the owner disabled both; the list leaves them as comments (see the
  header of `debloat.txt`).
- The Wi-Fi is 2.4 GHz only: the box sees no 5 GHz network at all, and links at 72 Mbps. On a crowded
  channel, live streams dropped frames (`/sys/class/video/fps_info` showed up to 18 of 30 frames dropped)
  while the CPU was idle and cool. Use the Ethernet port for live TV if you can.
- YouTube answers "this device is not compatible" on this build, before and after the list: the build is
  not Google-certified.

## Undo

```bash
TV_HOST=<tv ip>:5188 tools/restore.sh examples/btv-b11/debloat.txt com.bb.launcher2
```
