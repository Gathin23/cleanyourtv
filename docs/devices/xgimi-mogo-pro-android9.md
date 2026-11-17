# XGIMI MoGo Pro (original, Android TV 9): the launcher works, with no list of its own

Observed on one original MoGo Pro (#3813): device `xgimiapollo`, Chengdu XGIMI Technology, Android TV 9 /
SDK 28, build `PI.2.9.122 release-keys`. Launcher 3.1 (versionCode 10) was installed on 28 October 2026.
This is a compatibility report rather than a tested debloat list: apart from the stock launcher no
package was disabled, so there is nothing here to apply to another set. The supported-TV tables are
generated from a folder in `examples/` with its own list, which a report that touches no packages cannot
carry yet - so this is a note, and the list is the thing to send once someone has one.

It is not the [MoGo 2](../../examples/xgimi-mogo-2/debloat.txt), which is Android TV 11, nor the
[MoGo 3 Pro](../../examples/xgimi-mogo-3-pro/debloat.txt) (Google TV 14, device `altai`), and the
[Horizon S Pro](../../examples/xgimi-horizon-s-pro/debloat.txt) is Android 11 as well. Do not copy their
package lines onto this unit.

## What worked

- Network ADB reached the set.
- The launcher built from source with JDK 17 on an Apple Silicon Mac, installed and opened.
- Moving around the Home screen, opening apps with the remote, and coming back to it with the remote's
  Home button all worked.
- A theme was applied through `tools/manage.py`'s `set`.

## The Home handover

Home on this unit was `com.google.android.tvlauncher/.MainActivity`. The first install used
`STOCK_LAUNCHER=none`, and Home kept resolving to `com.google.android.tvlauncher/.MainActivity` after it -
which is what that mode is for: [`launcher/build-and-install.sh`](../../launcher/build-and-install.sh)
leaves the stock launcher as Home there, so its remote-key launches keep working (the
`stock_home_redirect` behaviour of #1274). It is not a sign that the install went wrong.

The handover that worked, after the new launcher had been opened and with the owner's agreement:

```bash
tools/venv/bin/python -I tools/tv.py 'pm disable-user --user 0 com.google.android.tvlauncher'
tools/venv/bin/python -I tools/tv.py 'cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity'
```

`com.google.android.tvlauncher` is one of the four stock launchers the installer already knows by name,
so a plain run of the installer runs those two commands itself once it has confirmed the new launcher
starts; they are written out here because that is the route this set was actually handed over by. Both
halves are needed: on Android 9 `cmd package set-home-activity` wants a component (`package/activity`)
and not a package alone, which is why the installer's own undo resolves the component on the TV first.

The way back, which a set with no list needs just as much:

```bash
tools/venv/bin/python -I tools/tv.py 'pm enable --user 0 com.google.android.tvlauncher'
tools/venv/bin/python -I tools/tv.py 'cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity'
```

## What was not checked

Reboot persistence, standby and wake, the stock launcher actually put back and read (the commands above
are the plan, not a rehearsal), HDMI, the tuner, audio and the remote's buttons beyond Home and the
D-pad, and the debugging switches: nothing in the report says USB or Network debugging was switched off
afterwards, so that closing step is still owed on this set.
