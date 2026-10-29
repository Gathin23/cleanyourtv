# TiVo Stream 4K

SEI Robotics `IPA1104HDW` (SKU `RA2400`, device `SEI400TV`), Amlogic S905Y2, 2 GB RAM, Android TV 12, build `SC.1272`,
security patch 2025-07-05, NES middleware. Tested on one real unit on 2026-10-22.

## Why `tools/disable.sh` does not work here

The firmware answers every `pm disable-user`, `pm disable`, `pm disable <component>` and `pm hide` with a refusal
("This command is illegal!", or "Shell cannot change component state"), and refuses `pm uninstall --user 0` for its core
apps. What works, per package: `pm suspend --user 0`, `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore` and
`am force-stop`, all of which survive a reboot. A suspended package still receives its boot broadcasts and bindings, so
some processes come back after every boot and need another `am force-stop`.

## Apply

```bash
export TV_HOST=192.168.1.50
L=examples/tivo-stream-4k/debloat.txt
tools/venv/bin/python -I tools/recovery.py --save "$L"   # record the box first, like tools/disable.sh
grep -vE '^\s*(#|$)' "$L" | awk '{print $1}' | while read -r p; do
  echo "pm suspend --user 0 $p"
  echo "cmd appops set $p RUN_ANY_IN_BACKGROUND ignore"
  echo "am force-stop $p"
done | tools/venv/bin/python -I tools/tv.py --batch
```

The last group (the stock launcher) is commented out in the list: uncomment it only after another launcher answers the Home key, see below.

## Undo

```bash
L=examples/tivo-stream-4k/debloat.txt
grep -vE '^\s*(#|$)' "$L" | awk '{print $1}' | while read -r p; do
  echo "pm unsuspend --user 0 $p"
  echo "cmd appops reset --user 0 $p"
done | tools/venv/bin/python -I tools/tv.py --batch
```

## The launcher

The stock launcher declares HOME with priority 2: `set-home-activity` answers `Success` and changes nothing.
`com.nes.coreservice` (`EvoBlackListAppsFun`) uninstalls, at every boot, every non-system app that declares
`android.intent.category.HOME`, and a fixed list of 53 packages (alternative launchers, Downloader, Magisk, IPTV
variants) from `array/evo_black_list_to_uninstall_pkgs` in its `resources.arsc`.

What worked on this unit, across several reboots with `coreservice` running: remove the HOME category from this
launcher's `MainActivity` intent filter (it stays listed as a Leanback launcher) and open it by component from a
small accessibility service on the Home key. The stock launcher cannot be suspended while it owns the HOME role: move
it first with `cmd package set-home-activity --user 0 com.android.tv.settings/.system.FallbackHome`.

The remote's Assistant button is handled by Android before the accessibility key filter, so `app_keys` cannot take it.

Remote buttons (`0471:1207`): TiVo `DVR` (173), Guide `GUIDE` (172), Live keycode `5124`, Skip `MEDIA_SKIP_FORWARD` (272). They do
nothing on the stock firmware once `com.nes.tvglobalkeyhandler` is gone; map them with `app_keys` and
`settings_key_keycodes` in a theme, with `HOME_KEY_SERVICE=1`. `theme/config.json` only holds the dock.

A fuller write-up, the patches and the measurements: https://github.com/lucaswotta/tivo-stream-4k-debloat
