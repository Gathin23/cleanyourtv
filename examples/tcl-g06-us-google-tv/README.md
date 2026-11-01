# TCL Smart TV, G06 (Google TV 11, US firmware)

MediaTek MT9221 board (device `G06`, product `G06_2K_US`), build AR2101 / AR38, TCL software
V8-T221T04-LF1V122. Both halves work: the debloat list and the Clean Your TV launcher as Home, with
Google TV's home kept behind it for the remote's app buttons. Tested on 31 October 2026.

| File | What it is |
|---|---|
| `debloat.txt` | The 21 packages the list disables (Netflix, off on this set, is commented out), grouped and commented, with what to keep and why |
| `config.json` | Dock order, the hidden Live TV tile and the launcherx redirect; push it with `tools/theme.sh` |

All commands run from the repo root, with the TV's IP in `TV_HOST`.

## Turning ADB on

This firmware has no Wireless debugging. Settings → Device Preferences → About → press OK on **Build**
7 times, then Developer options → **USB debugging**. That alone opens port 5555 over Wi-Fi; no cable is
needed. Accept the "Allow USB debugging?" prompt on the TV the first time `tools/tv.py` connects.

## Install

1. **Move the Wi-Fi network away from the setup wizard first.** The network saved during first setup
   belongs to `com.google.android.tungsten.setupwraith`, and Android deletes it when setupwraith is
   disabled. On the TV: Settings → Network & Internet → your network → Forget, then join it again. Check
   with `tools/tv.py "dumpsys wifi | grep -o 'cname=[^ ]*'"`: setupwraith should no longer appear.
2. Run:

```bash
TV_HOST=<tv ip> tools/disable.sh examples/tcl-g06-us-google-tv/debloat.txt
TV_HOST=<tv ip> HOME_KEY_SERVICE=1 launcher/build-and-install.sh
TV_HOST=<tv ip> tools/theme.sh examples/tcl-g06-us-google-tv
TV_HOST=<tv ip> tools/tv.py 'pm enable com.google.android.apps.tv.launcherx'
```

Wait about a minute before rebooting: on this platform a reboot straight after `pm disable-user` can bring
the last batch back.

## Remote buttons (TCL_RC902N_M1, Bluetooth)

The YouTube and Prime Video buttons send TCL key codes (4065 and 4099) that only launcherx answers, and
the gear button opened the TV settings only with launcherx enabled. With launcherx disabled all three did
nothing. So `build-and-install.sh` disables launcherx, and the last command above enables it again.
`config.json` then sets `"stock_home_redirect": "com.google.android.apps.tv.launcherx"` and
`"key_filter": false`: the launcher's helper sends Home back to the new launcher whenever launcherx's
home comes up, without filtering keys.

Checked on the real remote with this setup: Home, Back, the D-pad, volume (holding Volume Up still ramps),
Input, the mic (voice search), YouTube, Prime Video and the gear (TV settings). Pressing Home can show
launcherx for a moment before the new launcher. The small TV (guide) button offered a picker for what it
should do; the owner set it to Plex there.

Still dead: the bookmark button (Google TV's watchlist, answered only on launcherx's own home screen) and
the buttons whose apps were disabled on this set: Netflix, TCL Channel and the dashboard button. Mapping them with
`app_keys` needs `key_filter` on, which costs the held-key volume ramp on TCL sets like
[the G09](../../docs/devices/tcl-55p71b-g09-key-repeat.md); the owner kept the ramp.

## Re-enable an app

```bash
TV_HOST=<tv ip> tools/tv.py 'pm enable com.netflix.ninja'
```

To put everything back and give Home to launcherx again:

```bash
TV_HOST=<tv ip> tools/restore.sh examples/tcl-g06-us-google-tv/debloat.txt com.google.android.apps.tv.launcherx
TV_HOST=<tv ip> tools/theme.sh --reset
```

## When you are done

Revoke the USB debugging authorisations, switch **USB debugging** off in Developer options, restart, and
check that ADB is closed:

```bash
tools/venv/bin/python tools/adb-closed.py <tv ip>
```
