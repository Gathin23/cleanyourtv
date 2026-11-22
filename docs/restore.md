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

- The file is named after the address the TV was reached at, so `TV_HOST` is what picks the record:
  a run without it stops with tv.py's line and sends nothing to the TV, rather than falling back to
  the whole list for a record it cannot name.
- The list is part of the name too: the eight characters after the address are a hash of the list's
  path, resolved first, so `examples/x/debloat.txt`, `./examples/x/debloat.txt` from that folder and
  a soft link to the file all reach the one record. A copy of the list - one you keep beside your
  notes, a second checkout - is a different file with a name of its own: undo from there finds no
  record, says `no recovery record for this TV and list` while the record for the copy's original sits
  in `~/.cleanyourtv/`, and takes the no-record path below, which enables every listed package,
  including the ones you had disabled yourself. Undo with the file you applied, in the checkout you
  cleaned the TV from.
- The same file can also hash twice. A hard link, or the same list spelt with other capitalisation on
  a disk that ignores case (macOS, by default), is one file reached by a path that hashes differently:
  the run finds the record taken for that file and prints which one it used (`using <record>, the
  record taken for this list under another spelling of its path`) instead of falling back to the
  whole list.
- Two records that both name this TV and list are not chosen between: the run prints both paths and
  stops before anything reaches the TV (`this TV and list have two recovery records:`, then `Nothing
  was changed on the TV`). They are either the older name for this address beside the current one, or
  two spellings of the list that each wrote a record before the lookup above existed, and they can
  disagree about what was on before cleanup - which of them is this TV is not something the script can
  tell. Move the one that is not this TV's state from before cleanup aside deliberately, then run it
  again.
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

Without a record (an older run, a record you deleted, or the TV reached at a new address - a record
is filed under the address it was taken at) the script says so and falls back to enabling the whole
list and resetting the scales to `1.0`, which is what it used to do.

A record for the same list under another address is named with its file before that batch runs, as
that is the case where the fallback turns on packages you had disabled before cleanup: run the undo
at that address instead, or move the record to the name the run prints. If the other address is a
second TV you cleaned with the same list, both records are right and nothing is wrong.

## What restore leaves alone

It leaves the new launcher installed (remove it from the TV's Settings → Apps if you want it gone)
and does not know about packages you disabled outside the list. To switch back later, run the
install script again; it works out which launcher to disable even when Home has fallen back to
Android's chooser. If the TV is unreachable, its own Settings → Apps page can re-enable packages too.
