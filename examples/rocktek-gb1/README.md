# RockTek GB1

Tested on RockTek GB1, Google TV 14, firmware `UTT1.241206.001.142008`.

## What is in this folder

| File | Purpose |
|---|---|
| `debloat.txt` | The reversible disabled-package list: seven unused/privacy packages plus the setup wizard required for the Home handover. |
| `config.json` | A launcher overlay that restores the two remote controls documented below. Push it with `tools/theme.sh examples/rocktek-gb1` (or `tools/theme.ps1 -TvHost <tv ip> examples/rocktek-gb1`). |

`config.json` is deliberately device-specific. It leaves the built-in launcher defaults unchanged for every
other TV.

## Home handover

Google TV Home is `com.google.android.apps.tv.launcherx`. It was disabled only after
`com.gathin.tvhome` had become Home and stayed there across a reboot. The first-run wizard,
`com.google.android.tungsten.setupwraith`, also has to be disabled on this unit for the replacement Home
to remain active.

Before applying `setupwraith`, check which package owns the saved Wi-Fi network. `tools/disable.sh` and
`disable.ps1` refuse to disable it while it owns that network; forget and rejoin Wi-Fi in TV Settings first.
Re-enable it before a factory reset or remote re-pairing. Casting behaviour after disabling it has not been
tested, so this list makes no claim about Chromecast.

## Remote buttons after Google TV Home

The stock launcher had been handling two physical controls. With it disabled they stopped doing anything;
the optional `HomeKeyService` can consume the framework key events first and start the intended action.
Install with `HOME_KEY_SERVICE=1` (or `-HomeKeyService` in PowerShell), then push this folder's
`config.json`.

| Physical control | Framework event | Restored action |
|---|---|---|
| Settings / gear | `KEYCODE_NOTIFICATION` (83); Linux scan code 240 | Opens the TV's Settings through `settings_key_keycodes` |
| Macro | `KEYCODE_GUIDE` (172) | Opens the installed `com.seena.app` through `app_keys` |

The launcher filters Android `KeyEvent` values, so the overlay uses the delivered keycodes rather than a
raw HID `MSC_SCAN` value. These mappings were verified with the real remote; `input keyevent` over ADB
does not exercise an accessibility key service and is not a valid test.

`HomeKeyService` is optional and a force-stop of the launcher unbinds it. Re-enable the service before
testing again. Check that holding Volume Up and Down still repeats normally before leaving it enabled;
that repeat behaviour has not yet been recorded for this firmware. Other branded buttons are not mapped.

## Verification still needed before submitting upstream

The list, Home handover, reboot and the two buttons above have been checked. Use the box normally for a
few days, then update this note with any package that had to be re-enabled. Test streaming apps you use,
voice, casting and firmware update behaviour explicitly before claiming them preserved or disabled.
