# Restoring your TV

[Back to the README](../README.md#restoring-your-tv)

Keep the list you applied. One line puts the TV back the way it was:

```bash
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt                                    # Android TV
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt com.google.android.apps.tv.launcherx  # Google TV
```

On Windows the same undo is:

```powershell
powershell -ExecutionPolicy Bypass -File tools\restore.ps1 -TvHost 192.168.1.50 examples\<your tv>\debloat.txt
```

and on Google TV the call takes the stock launcher as well, `-Stock
com.google.android.apps.tv.launcherx`, after the list. `TV_HOST=... tools/restore.sh` does not run in
PowerShell: it sets a variable and then calls a program Windows does not have.

## What the record holds

Before `tools/disable.sh` changes anything it writes down what the TV looked like:

- for every package in the list, whether it was enabled, already disabled or not installed;
- which component owned Home;
- the three animation scales.

That record lives in `~/.cleanyourtv/`, one file per TV and list.

- A second run of the same list keeps the first record instead of saving the cleaned-up state over it.
- If you added packages to the list since, that run adds them to the record before disabling them, and
  a listed package that was not installed then but is installed and enabled now is recorded as enabled.
- `check.py --fix` does the same before it disables anything.

Keep the list you applied. The restore script replays the record, so a package you had disabled
yourself stays disabled and Home goes back to the component that owned it.

## The stock launcher argument

The `stock launcher` argument is only a fallback: with a record, the launcher that actually owned Home
is the one that gets it back, and the argument is used only when nothing answered HOME at the time
or this project's launcher owned it.

The record also notes whether the known stock launchers were on, listed or not, and the stock launcher
is enabled again only if it was:

- One that was already off (from the factory, or because the install script had disabled it before you
  applied this list) stays off, and if this project's launcher owned Home then, Home goes back to it.
- A record from before this note was added does not say, so the script leaves that launcher as it is
  and prints the `tools/tv.py 'pm enable <package>'` that turns it on.

## Without a record

Without a record (an older run, or a record you deleted) the script says so and falls back to
enabling the whole list and resetting the scales to `1.0`, which is what it used to do.

## What restore leaves alone

It leaves the new launcher installed (remove it from the TV's Settings → Apps if you want it gone)
and does not know about packages you disabled outside the list. To switch back later, run the
install script again; it works out which launcher to disable even when Home has fallen back to
Android's chooser. If the TV is unreachable, its own Settings → Apps page can re-enable packages too.
