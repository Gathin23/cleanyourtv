# Philips R8G Google TV: switching the standby light off

Checked on a Philips R8G built by MOKA/TPV (device `whitefield`), Google TV 14 / API 34, Realtek `rtd2851f`,
build `UKRE.260524.058`, fingerprint `PHILIPS/PHILIPS/whitefield:14/UKRE.260524.058/16139055:user/release-keys`.
Its debloat list is [`examples/philips-r8g-google-tv`](../../examples/philips-r8g-google-tv/debloat.txt).
These are observations on one set, not a firmware compatibility claim.

The red light under the screen stays on while the TV is in standby, and the TV's menus have no setting for it.
It is driven by a Realtek vendor service, `com.realtek.power.ILedManager/default`, which the ADB shell can call
and apps can't (it is a vendor HAL, so no add-on can do this).

## The call

Its interface (from the `com.realtek.power` AIDL classes in `com.realtek.energymode`) has call 1,
`setPowerLedConfiguration(int state, int setting)`. The states are `LedState`:

| state | meaning |
|---|---|
| 0 | `PANEL_ON`: the TV is on |
| 1 | `PANEL_OFF` |
| 2 | `STR`: standby (suspend to RAM) |
| 3 | `LPM`: low-power standby |
| 4 | `BLINKING` |

The setting isn't documented. 0 switches the light off for that state; 1 is assumed to be the normal light (the
old values can't be read back: there is no getter). Off when on and in both standbys:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'for s in 0 2 3; do service call com.realtek.power.ILedManager/default 1 i32 $s i32 0; done'
```

Each call answers `Result: Parcel(NULL)`. `service call com.realtek.power.ILedManager/default 16777215`
(`getInterfaceVersion`) answering `1` is a harmless first check that the service is there.

## What it does

With the TV on, the light was already off. On the power key the light comes on for a few seconds while the TV
shuts down (that sequence isn't covered by these states), then goes off and stays off in standby. To undo, send
setting 1 instead of 0 for the same states (the undo hasn't been tried yet). Not yet checked: whether it survives a full power cut or a firmware
update; if the light comes back, run the line again.

Other Realtek-based sets may have the same service under the same name (`service list | grep -i led`); the state
numbers above come from this firmware.
