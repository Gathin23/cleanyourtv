# Crash watchdog and safe mode

If the launcher crashes three times within five minutes, the next start is in **safe mode**. It exists so a
bad theme or an optional feature cannot keep a crash loop going on the screen you need to fix things from.

## What it does

- A default uncaught-exception handler (installed in `WatchdogApp`) writes the time of each crash to a small
  preference (`crash_watchdog`, at most 5 timestamps), then passes the crash to the previous handler, so the
  crash itself looks exactly as before.
- At startup `Config.load` asks `CrashWatchdog` whether 3 or more crashes fall in the last 5 minutes. That is a
  few preference reads; there is no timer, thread, service or cache.
- Crashes older than 5 minutes stop counting the next time the list is read. A timestamp in the future (the
  TV's clock was set back) is ignored.

## Safe mode

In safe mode:

- `config.json` is not read, the wallpaper is not shown, and the on-TV advanced effects are off.
- Everything a theme adds (sky, festive skin, extra clocks, dock changes) is off, because it comes from `config.json`.
- The dock and the app grid work, so you can still open apps and the TV's Settings.
- A toast on the home screen says so.

## Leaving safe mode

It leaves by itself once there are fewer than 3 crashes in the last 5 minutes, which a quiet five minutes
does. Pushing or resetting a theme also clears the recorded crashes straight away, using the same theme
revision that already clears on-TV choices:

```
TV_HOST=<ip> tools/theme.sh themes/<fixed-theme>.json
TV_HOST=<ip> tools/theme.sh --reset
```

If the crash comes from the launcher itself rather than from the config, safe mode will not stop it; it only
removes the usual suspects.

## Limits

An app cannot re-enable other packages or choose the Home app (both need system permissions only `adb`
has), and this launcher adds no adb of its own. So, unlike a tool that hands Home back to the stock launcher,
the watchdog **cannot restore the stock launcher**.

To do that from a computer on the same network:

- `launcher/build-and-install.sh` already re-enables the stock launcher and sets it as Home if the new launcher
  fails to start during an install (it does nothing about crashes later).
- `TV_HOST=<ip> tools/restore.sh <your debloat list> [stock launcher package]` re-enables every package in the
  list, re-enables the stock launcher and sets it as Home again, and resets the animation scales.
- Or by hand: `tools/tv.py 'pm enable --user 0 <stock launcher package>'`, then
  `tools/tv.py 'cmd package set-home-activity --user 0 <package>/<home activity>'`.
