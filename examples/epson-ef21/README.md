# Epson EF-21 (Google TV 14 laser projector)

SDMC-built board (model `HB35`, device `himalaya`, MediaTek m7332, 1.8 GB RAM), firmware
UKNV.260904.001. Same board as [the BenQ GV32](../benq-gv32/README.md), whose list this one starts from.
Both halves work: the debloat list and the Clean Your TV launcher as Home.

| File | What it is |
|---|---|
| `debloat.txt` | The 13 packages the list disables (YouTube, YouTube Music and Netflix, also off on this unit, are commented out), grouped and commented, with what to keep and why (`build-and-install.sh` disables launcherx on top) |
| `config.json` | Dock order and the hidden USB player tile; push it with `tools/theme.sh` |

All commands run from the repo root, with the projector's IP in `TV_HOST`.

## Turning ADB on

Settings → System → About → press OK on **Android TV OS build** 7 times, then Developer options →
**USB debugging**. On this unit that alone opened network ADB on port 5555:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --probe
```

Accept "Allow USB debugging?" on the projector the first time.

## Install, from scratch or again

1. **Move the Wi-Fi network away from the setup wizard first.** The network saved during first setup
   belongs to `com.google.android.tungsten.setupwraith`, and Android deletes it the moment setupwraith
   is disabled. On the projector: Settings → Network & Internet → your network → Forget, then join it
   again. Check with `tools/tv.py "dumpsys wifi | grep -o 'cname=[^ ]*'"` (it should say
   `android.uid.system`).
2. Run:

```bash
TV_HOST=<tv ip> tools/disable.sh examples/epson-ef21/debloat.txt
TV_HOST=<tv ip> launcher/build-and-install.sh
TV_HOST=<tv ip> tools/theme.sh examples/epson-ef21
```

3. The list disables Google's ambient screensaver, so turn on the launcher's Clock screensaver:

```bash
TV_HOST=<tv ip> tools/tv.py 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream; settings put secure screensaver_enabled 1'
```

## Remote buttons

Without the stock launcher these keep working on their own: Home, Back, volume, the D-pad, Input and
the Prime Video button. The list leaves YouTube, YouTube Music and Netflix commented out; if you
uncomment them, the YouTube and Netflix buttons do nothing.

The Settings (gear) button does nothing once launcherx is disabled: it sends scancode 249, which the
remote's key layout does not map. Settings stays one press away on the launcher's Settings pill (Home,
then Up). The launcher's key service (`HOME_KEY_SERVICE=1`) could map it back, but on this board a bound
key-filtering service drops held-key repeats (holding volume moves one step per press). The
[Settings key note](../../docs/devices/epson-ef21-settings-key.md) brings the gear back with a
keyboard-layout overlay instead, with no key helper, so held volume still ramps.

## Re-enable an app

```bash
TV_HOST=<tv ip> tools/tv.py 'pm enable com.netflix.ninja'
```

Use the package name from `debloat.txt`. To put everything back and give Home to launcherx again:

```bash
TV_HOST=<tv ip> tools/restore.sh examples/epson-ef21/debloat.txt com.google.android.apps.tv.launcherx
TV_HOST=<tv ip> tools/theme.sh --reset
```

`tools/tv.py 'pm uninstall com.gathin.tvhome'` then removes the launcher itself.

## After a firmware update

Epson's updater is left enabled. An update can re-enable packages, so check afterwards and put them back:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/check.py examples/epson-ef21/debloat.txt --fix
```

## Change the dock or the wallpaper

Edit `dock_apps` in `config.json` (package names, left to right; `columns` sets how many slots), then
push it again:

```bash
TV_HOST=<tv ip> tools/theme.sh examples/epson-ef21
```

For a wallpaper, put a `wallpaper.jpg` (1920x1080) next to `config.json` and push the same way;
`themes/README.md` has the dim and rotation options. `tools/theme.sh --reset` goes back to the
launcher's built-in config.

## When you are done

Switch **USB debugging** off in Developer options, revoke the debugging authorisations there, restart,
and check that ADB is closed:

```bash
tools/venv/bin/python -I tools/adb-closed.py <tv ip>
```
