# XGIMI Vibe One (Google TV / Android 11 portable projector)

Model WM03H, device altai (same board as the MoGo 3 Pro and MoGo 4), MediaTek m7332, Android 11
(SDK 30), build RTMA.250416.186.13970001, Canadian unit. 2 GB RAM. Applied and verified on a real
unit on 2026-11-04, including reboots, in one session of a few hours rather than several days.

## Turning ADB on

Developer options offer USB debugging and a USB configuration menu (RNDIS never showed up as a network
adapter on the Windows host). The projector enumerates as a plain Google ADB device over USB, so the
simplest route is:

```bash
adb devices                      # accept "Allow USB debugging?" on the projector
adb tcpip 5555
adb connect <projector-ip>:5555
export TV_ADB_BINARY=/absolute/path/to/adb
export TV_HOST=<projector-ip>:5555
tools/venv/bin/python -I tools/tv.py --probe
```

`TV_HOST` needs the network address: given the bare USB serial, the tools ask adb for `<serial>:5555`
and stop with `device not found`.

**Close the port when you finish.** Port 5555 is plaintext, and on this unit it **stayed open across
reboots**, unlike the MoGo 4 note, so it does not go away by itself. Turning USB debugging off and
rebooting closed it here. Check with `tools/adb-closed.py`:

```bash
python3 -I tools/adb-closed.py <projector-ip>
```

Only its "closed" answer counts. The USB device did not re-enumerate after a reboot, but the network
connection kept working until USB debugging was turned off.

## Debloat

```bash
TV_ADB_BINARY=<adb> TV_HOST=<ip>:5555 tools/disable.sh examples/xgimi-vibe-one/debloat.txt
```

With `TV_ADB_BINARY` pointing at a Windows `adb.exe` from WSL2, each `disable.sh` run disabled only the
first package of its list and reported the rest as still enabled; the cause was not established.
Single `pm disable-user --user 0 <package>` calls through `tools/tv.py` worked, five at a time with a
pause. `restore.sh` was not tried on this setup, so check its result the same way.

`com.google.android.tungsten.setupwraith` owns the saved Wi-Fi network on this unit
(`dumpsys wifi` shows `cname=com.google.android.tungsten.setupwraith` on the connected entry), so it
is kept. The stock launcher is kept too: see below.

Result: Bell Fibe TV (`com.quickplay.android.bellmediaplayer`), the largest resident process before
the debloat at 153 MB with a boot receiver, no longer runs. All 19 entries stayed disabled after a
reboot.

## Launcher

Built with `tools/docker-build.sh`, then:

```bash
SKIP_BUILD=1 HOME_KEY_SERVICE=1 TV_ADB_BINARY=<adb> TV_HOST=<ip>:5555 launcher/build-and-install.sh
TV_ADB_BINARY=<adb> TV_HOST=<ip>:5555 tools/theme.sh examples/xgimi-vibe-one
```

`build-and-install.sh` stops with "Home resolves to launcherx" because `set-home-activity` does not
hold on this firmware, and exits before enabling the helper. That check is a false negative here:
enable the helper by hand and the redirect in `config.json` does the rest.

```bash
tools/tv.py "settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService; settings put secure accessibility_enabled 1"
```

What held on this unit, with `com.google.android.apps.tv.launcherx` left **enabled** and
`stock_home_redirect` set:

- A real Home press on the remote lands on the new launcher. An injected `input keyevent KEYCODE_HOME`
  does not, so only a press on the remote counts as proof.
- After a reboot the projector boots straight into the new launcher without any key press.
- Netflix, YouTube and Prime Video buttons (`KEYCODE_F9`, `F4`, `F8`, the MoGo 3 Pro / MoGo 4 codes)
  and the gear button (`KEYCODE_NOTIFICATION` 83) all worked on the real remote.
- Holding volume up or down does nothing: each press moves one step, the same auto-repeat loss as the
  MoGo 4 note (the helper binds with `capabilities=8`). Confirmed on the real remote.
- Autofocus and the other projector buttons were checked by hand and work.

The cost of keeping launcherx enabled is that it stays resident in the background (about 80 MB over
two processes). Disabling it is possible but on this unit was not needed, and it would require
disabling setupwraith first, which would delete the Wi-Fi network.

## Notes on `config.json`

Dock: Netflix, YouTube, Prime Video, Disney+, with `columns` 5 so the fifth slot fills from the grid.
`hidden_apps` keeps Settings off the tiles (the Settings pill and the gear button do not depend on the
tile), so Play Store takes that slot. The key replaces the built-in list, so it repeats those entries.
No tuner (`"live_tv_package": ""`), HDMI opens from the Inputs pill.
