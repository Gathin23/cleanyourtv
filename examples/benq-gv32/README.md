# BenQ GV32 (Google TV 14 portable projector)

SDMC-built board (product `BenQ_GV01`, device `himalaya`, MediaTek m7332, 1.8 GB RAM), firmware
UKNV.260626.001. Both halves work: the debloat list and the Clean Your TV launcher as Home. Tested on
21 October 2026.

| File | What it is |
|---|---|
| `debloat.txt` | The 8 packages that were disabled, grouped and commented, with what to keep and why (`build-and-install.sh` disables launcherx on top) |
| `config.json` | Dock order, hidden tile, and the Settings and profile buttons; push it with `tools/theme.sh` |

All commands run from the repo root, with the projector's IP in `TV_HOST`.

## Turning ADB on

Settings → Device Preferences → About → press OK on **Build** 7 times, then Developer options →
**Wireless debugging** → **Pair device with pairing code**. Then, with Google's `adb`:

```bash
adb pair <ip>:<pairing port> <code>
adb connect <ip>:<port>        # the IP address & Port line on the Wireless debugging screen
adb tcpip 5555                 # so tools/tv.py works
```

The connect port is random and is announced over mDNS, which does not cross WSL2's NAT; scanning the
projector's ports 30000-50000 for the one that accepts `adb connect` finds it.

## Install, from scratch or again

1. **Move the Wi-Fi network away from the setup wizard first.** The network saved during first setup
   belongs to `com.google.android.tungsten.setupwraith`, and Android deletes it the moment setupwraith
   is disabled. On the projector: Settings → Network & Internet → your network → Forget, then join it
   again. Check with `tools/tv.py "dumpsys wifi | grep -o 'cname=[^ ]*'"` (it should say
   `android.uid.system`).
2. Run:

```bash
TV_HOST=<projector-ip> tools/disable.sh examples/benq-gv32/debloat.txt
TV_HOST=<projector-ip> HOME_KEY_SERVICE=1 launcher/build-and-install.sh
TV_HOST=<projector-ip> tools/theme.sh examples/benq-gv32
```

`disable.sh` skips setupwraith by itself while it still owns the Wi-Fi, so step 1 only matters for the
launcher handover: with setupwraith enabled, its `RecoveryActivity` claims Home behind launcherx.

## Remote buttons ("RCI078/079", Bluetooth)

Without the stock launcher these keep working on their own: Home, Back, volume, the D-pad, the
YouTube, Netflix and Prime Video buttons, Input (Google's input switcher), focus, auto-keystone, the
BenQ help button (Support Center) and the mic.

Two buttons were answered only by launcherx and go dead without it. `config.json` maps them by
scancode, which needs the launcher's HomeKeyService (`HOME_KEY_SERVICE=1` above):

| Button | Scancode (Linux key) | Mapped to |
|---|---|---|
| Settings (gear) | 189 (`KEY_F19`) | Android Settings |
| Profile (person) | 433 (`KEY_LOGOFF`) | Plex (Google TV's profile switcher lived in launcherx) |

**The trade:** while any key-filtering accessibility service is bound, this firmware drops held-key
repeats, so holding Volume Up moves one step instead of ramping, as on
[the TCL G09](../../docs/devices/tcl-55p71b-g09-key-repeat.md). Checked both ways on this unit: the ramp
comes back as soon as the helper is unbound. To choose the ramp instead, leave out
`HOME_KEY_SERVICE=1`, or switch the helper off:

```bash
TV_HOST=<projector-ip> tools/tv.py "settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0"
```

## Re-enable an app

```bash
TV_HOST=<projector-ip> tools/tv.py 'pm enable com.instagram.airwave'
```

Use the package name from `debloat.txt`. To put everything back and give Home to launcherx again:

```bash
TV_HOST=<projector-ip> tools/restore.sh examples/benq-gv32/debloat.txt com.google.android.apps.tv.launcherx
TV_HOST=<projector-ip> tools/theme.sh --reset
```

Switch the key helper off as shown under Remote buttons, and `tools/tv.py 'pm uninstall com.gathin.tvhome'`
removes the launcher itself.

## Change the dock or the wallpaper

Edit `dock_apps` in `config.json` (package names, left to right; `columns` sets how many slots), then
push it again:

```bash
TV_HOST=<projector-ip> tools/theme.sh examples/benq-gv32
```

For a wallpaper, put a `wallpaper.jpg` (1920x1080) next to `config.json` and push the same way;
`themes/README.md` has the dim and rotation options. `tools/theme.sh --reset` goes back to the
launcher's built-in config.

## When you are done

Switch **USB debugging** and **Wireless debugging** off in Developer options, revoke the debugging
authorisations there, restart, and check that ADB is closed:

```bash
tools/venv/bin/python tools/adb-closed.py <projector-ip>
```
