# Skyworth 7S60_A5 (Coocaa OS, China market, Android 8)

Skyworth 7S60_A5, board 6A658B (`sky8S34`, armeabi-v7a, about 1.9 GB RAM), Coocaa OS on Android 8.0,
build `OPR5.170623.014` / `022.008.220`. Both halves work: the debloat list, and the Clean Your TV
launcher as Home. Coocaa OS is what Skyworth ships on its China-market sets, and it does not hand
Home over the way Android TV does, so the launcher needs three extra steps, explained below.

| File | What it is |
|---|---|
| `debloat.txt` | Coocaa ads, push, usage reporting, auto-installer, preloads; grouped and commented |
| `config.json` | Inputs menu from Coocaa's own source list, the source panel as fallback, a dock |

All commands run from the repo root. Network ADB on port 5555 was already on; accept the
authorisation prompt on the TV the first time.

```bash
export TV_HOST=<tv-ip>
PY=tools/venv/bin/python
```

## Why Coocaa OS needs more than build-and-install.sh

- **Home is a property, not an intent.** The Home key and the boot flow start whatever
  `persist.service.homepage.pkg` / `.cls` name (`com.tianci.system` reads them), not Android's HOME
  resolution; the stock home (`com.tianci.movieplatform`) declares no HOME activity at all. The shell
  can `setprop` these.
- **`com.tianci.system` resets them at every boot**: to `com.ccos.tv.homepage/.LaunchActivity` when a
  package of that name is installed (a Coocaa homepage package), otherwise to the stock home. So Home
  goes through a tiny shim app with that package name, whose only screen starts the launcher:
  [coocaa-home-shim](https://github.com/VincentYe123/coocaa-home-shim).
- **Coocaa's memory manager force-stops third-party apps** about two seconds after they leave the
  screen. It spares packages whose second part is `coocaa`, `skyworth`, `tianci` or `android`. A
  launcher installed as `com.gathin.tvhome` is killed as soon as an app opens, and Back from that app
  then lands on the stock home. Build it as `com.coocaa.tvhome` with the existing
  `launcherApplicationId` property; the Java namespace stays `com.gathin.tvhome`.
- **Coocaa's boot flow opens the stock home itself** once the boot video ends, whatever the
  properties say. The shim watches for that during the first two minutes after boot and asks
  `com.tianci.system` to go Home (Coocaa blocks activity starts from background apps). With the
  stock home disabled, which is the last step, this no longer happens at all.

## Install

1. Debloat, then build and install the launcher under the Coocaa-friendly package name:

   ```bash
   tools/disable.sh examples/skyworth-7s60-a5/debloat.txt
   (cd launcher && ./gradlew assembleRelease -PlauncherApplicationId=com.coocaa.tvhome)
   "$PY" -I tools/b64push.py launcher/app/build/outputs/apk/release/app-release.apk /data/local/tmp/tvhome.apk
   "$PY" -I tools/tv.py 'pm install -r /data/local/tmp/tvhome.apk; rm -f /data/local/tmp/tvhome.apk'
   "$PY" -I tools/tv.py 'am start -W -n com.coocaa.tvhome/com.gathin.tvhome.MainActivity'
   ```

2. Push `config.json` (`tools/theme.sh` assumes `com.gathin.tvhome`, so push it by hand), and
   restart the launcher:

   ```bash
   "$PY" -I tools/b64push.py examples/skyworth-7s60-a5/config.json /sdcard/Android/data/com.coocaa.tvhome/files/config.json
   "$PY" -I tools/tv.py 'am force-stop com.coocaa.tvhome; am start -n com.coocaa.tvhome/com.gathin.tvhome.MainActivity'
   ```

3. Install the shim (APK from its [releases](https://github.com/VincentYe123/coocaa-home-shim/releases),
   or build it), point Home at it, and press Home:

   ```bash
   "$PY" -I tools/b64push.py coocaa-home-shim.apk /data/local/tmp/shim.apk
   "$PY" -I tools/tv.py 'pm install -r /data/local/tmp/shim.apk; rm -f /data/local/tmp/shim.apk'
   "$PY" -I tools/tv.py 'setprop persist.service.homepage.pkg com.ccos.tv.homepage; setprop persist.service.homepage.cls com.ccos.tv.homepage.LaunchActivity; input keyevent KEYCODE_HOME'
   ```

   Home should now open the new launcher, from any app. Restart the TV once and check that it comes up
   on the new launcher too (the stock home may show for a few seconds first).

4. Then disable the stock home. Without it, the boot flow has nothing else to open:

   ```bash
   "$PY" -I tools/tv.py 'pm disable-user --user 0 com.tianci.movieplatform'
   ```

   After this, the TV came up on the new launcher about 26 seconds after power-on, with no stock home
   in between.

Optional: `com.coocaa.screensaver` (ad SDK) is in the list; the launcher's clock screensaver can take
its place:

```bash
"$PY" -I tools/tv.py 'settings put secure screensaver_components com.coocaa.tvhome/com.gathin.tvhome.ClockDream; settings put secure screensaver_enabled 1'
```

## Inputs

Coocaa OS registers nothing with `TvInputManager`. With `coocaa_sources` on, the Inputs menu lists the
sources `com.skyworth.tv` reports (HDMI 1, HDMI 2, AV, the tuner), under the names the TV gives them,
and switching goes through `com.skyworth.tv` the same way its own Source panel does. If that list
cannot be read, the Inputs pill opens Coocaa's control center on its source row instead
(`source_panel_*`, with `control_center_command=100`), which is what the remote's Source button shows.

## Known issues

- Now and then the framework writes the literal strings `home[0]` / `home[1]` into the homepage
  properties at boot (it races `com.tianci.system`), and Home stops working. Run the `setprop` line
  from step 3 again.
- The TV has no clock battery and starts at 1970-01-01 08:00 until network time arrives a few seconds
  after boot, so the launcher's clock can read 08:00 briefly.
- `com.coocaa.sky.ccapi` and `com.tianci.user` are Coocaa's payment and account apps: Coocaa
  membership purchases and account login stop working while they are off.

## Undo

```bash
"$PY" -I tools/tv.py 'pm enable com.tianci.movieplatform; pm uninstall com.ccos.tv.homepage'
"$PY" -I tools/tv.py 'setprop persist.service.homepage.pkg com.tianci.movieplatform; setprop persist.service.homepage.cls com.coocaa.homepage.vast.HomePageActivity; input keyevent KEYCODE_HOME'
tools/restore.sh examples/skyworth-7s60-a5/debloat.txt com.tianci.movieplatform
```

The shim must go first: while it is installed, `com.tianci.system` points Home back at it on every
boot. Then remove the launcher with `pm uninstall com.coocaa.tvhome` if you like.
