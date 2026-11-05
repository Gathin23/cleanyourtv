# Nokia Streaming Box 8000

Sagemcom/NES build, product `SEI600NK4`, device `HND`, Amlogic, 2 GB RAM, Android TV 14
(`UKG3.240613.001`), German UI. Tested on a real unit on 2026-10-20.

Network debugging switches itself off on every reboot: after a restart, turn it on again in
Developer options before connecting. The box moves around on DHCP; it advertises itself over mDNS, so
`avahi-browse -rt _adb._tcp` (Linux) or `dns-sd -B _adb._tcp` (macOS) finds it.

## Why `tools/disable.sh` does not work here

The firmware answers every `pm disable-user`, `pm disable` and `pm disable-until-used` with
`Warning! This command is illegal!`, refuses `pm hide` (no `MANAGE_USERS` for the shell) and refuses
`pm uninstall --user 0` for its "core" apps. TV Settings has no Disable button either. What still
works, per package:

- `pm suspend --user 0 <pkg>`: the app cannot be opened and its notifications are hidden
- `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore`: no background services or jobs
- `am force-stop <pkg>`

All three survive a reboot (checked). It is weaker than disabling: a boot receiver can still wake
briefly.

## Apply

```bash
export TV_HOST=192.168.1.50
L=examples/nokia-streaming-box-8000/debloat.txt
tools/venv/bin/python -I tools/recovery.py --save "$L"   # record the box first, like tools/disable.sh
grep -vE '^\s*(#|$)' "$L" | awk '{print $1}' | while read -r p; do
  echo "pm suspend --user 0 $p"
  echo "cmd appops set $p RUN_ANY_IN_BACKGROUND ignore"
  echo "am force-stop $p"
done | tools/venv/bin/python -I tools/tv.py --batch
```

## Undo

Everything in the list:

```bash
L=examples/nokia-streaming-box-8000/debloat.txt
grep -vE '^\s*(#|$)' "$L" | awk '{print $1}' | while read -r p; do
  echo "pm unsuspend --user 0 $p"
  echo "cmd appops reset --user 0 $p"
done | tools/venv/bin/python -I tools/tv.py --batch
```

One app, for example YouTube Music:

```bash
tools/venv/bin/python -I tools/tv.py 'pm unsuspend --user 0 com.google.android.youtube.tvmusic; cmd appops reset --user 0 com.google.android.youtube.tvmusic'
```

Then comment the line out in `debloat.txt` so a later run leaves it alone. Google Assistant is
`com.google.android.katniss`; the remote's mic button works again once it is unsuspended.

## Screensaver

The list suspends the ambient screensaver (`com.google.android.backdrop`). Comment it out to keep it,
or install the launcher and use its clock instead:

```bash
STOCK_LAUNCHER=none launcher/build-and-install.sh   # the firmware refuses to disable the stock home anyway
tools/venv/bin/python -I tools/tv.py 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream; settings put secure screensaver_default_component com.gathin.tvhome/.ClockDream'
```

## Stock launcher

`com.google.android.tvlauncher` declares HOME with priority 2, which no sideloaded launcher can match,
and it cannot be disabled or uninstalled. So `set-home-activity` says `Success` but changes nothing,
and the new launcher cannot take over the Home button on this box yet. `theme/config.json` holds this
box's dock, hidden apps and the hidden Wi-Fi pill for when it can (push it with
`tools/theme.sh examples/nokia-streaming-box-8000/theme`).

Not yet tested on this box: on the TiVo Stream 4K, which runs the same NES middleware,
`com.nes.coreservice` (kept on in this list) uninstalls at every boot every non-system app that declares HOME,
and suspending or force-stopping it does not stop that (#1062). Expect the same here, so before installing
the launcher read [the TiVo notes](../tivo-stream-4k/README.md#the-launcher) for what worked there: a
launcher without the HOME category, opened from the Home key by an accessibility service.

## Before you start again

ADB over the network was switched off at the end of the first run. To work on the box again: TV
Settings > Geräteeinstellungen > Info > press OK on Build seven times, then Entwickleroptionen >
USB-Debugging on, and accept the prompt on the TV. Switch it off again when you are done; port 5555
is plaintext.
