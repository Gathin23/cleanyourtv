# TCL 75P635: Live TV in Inputs

On this BeyondTV4 set (Android TV 11, build `RP1A.200622.001 release-keys`, device G07),
the tuner app is `com.tcl.tv`. The launcher's built-in `live_tv_package` points elsewhere,
so Live TV would be missing from Inputs without this fix.

[config.json](config.json) sets `live_tv_package` to `com.tcl.tv` and hides the raw TV app
icon from the grid (it remains reachable via the Inputs pill). From the repository root,
apply it to an installed launcher with:

```sh
TV_HOST=<tv ip> tools/theme.sh examples/tcl-75p635
```

If you already use a custom theme, add these two keys to that theme's `config.json` instead:

```json
"live_tv_package": "com.tcl.tv",
"hidden_apps": ["com.tcl.tv"]
```

## Reboot note

`com.tcl.notereminder` resets to enabled after a reboot on this firmware
(`com.tcl.usercenter` stays disabled). Re-disable it after each restart:

```sh
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py "pm disable-user --user 0 com.tcl.notereminder"
```

## Launcher Home switch

On this TV, `cmd package set-home-activity` must be called while the new launcher is
already in the foreground, then `setupwraith` must be disabled immediately after.
The /product system version of setupwraith respawns on every HOME keyevent until it
is fully disabled-user. `launcher/build-and-install.sh` runs set-home-activity but does not disable
setupwraith, so disable it by hand right after the install, as the list's header says.
