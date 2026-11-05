# Sony BRAVIA 4K VH22: remote Settings key without a key-filtering helper

This is an owner report from 2026-10-26, with a read-back on 2026-10-27. It is
not a debloat list and not an upstream launcher option. It records a
keyboard-layout overlay that gives the remote's Settings key a job again on a
set where `HomeKeyService` breaks held-volume repeat
([#1680](https://github.com/Gathin23/cleanyourtv/issues/1680)).

## Observed firmware

| Field | Observed value |
|---|---|
| Manufacturer | `Sony` |
| Android model | `BRAVIA 4K VH22` |
| Device | `BRAVIA_VH22` (EU firmware) |
| Android / API | `12` / `31` |
| Firmware build | `BRAVIA_VH22_EU-user 12 STT2.230505.001.S136 682241 release-keys` |
| Remote | `SONY TV VRC 001`, vendor `054c`, product `0x0eae` (Bluetooth) |

The retail model was not recorded. The launcher is CleanYourTV v3.0.0 as Home,
with no accessibility service enabled.

## The problem it works around

With `HomeKeyService` bound, holding Volume on this set moves one step instead
of ramping, as in [#1680](https://github.com/Gathin23/cleanyourtv/issues/1680)
and the [key-repeat section](../TROUBLESHOOTING.md#a-key-helper-is-bound-and-a-held-remote-button-stopped-repeating)
of the troubleshooting guide; the issue also reports that `consume_volume_keys`
does not help. With the helper off, volume ramps again, but the Settings key
does nothing.

## The overlay

Android lets an app provide extra keyboard layouts, and a layout of type
`OVERLAY` changes only the keys it names. This one turns the Settings key into
`CALENDAR`; Android then sends `APP_CALENDAR`, which a small activity answers
by opening Settings. No key passes through an app, so the input filter stays
off.

The layout file (`res/raw/settings_key.kcm`):

```text
type OVERLAY
map key 646 CALENDAR
map key usage 0x000c0586 CALENDAR
```

Scancode 646 and HID usage `0x000c0586` are the Settings key on this remote;
check yours with `getevent -l` before copying them.

The provider app needs two components and no permissions:

- a receiver for `android.hardware.input.action.QUERY_KEYBOARD_LAYOUTS` with
  `android.hardware.input.metadata.KEYBOARD_LAYOUTS` metadata pointing at an
  XML file that lists the layout above (`<keyboard-layout android:name="settings_key" ...>`);
- an activity with `Theme.NoDisplay` for `MAIN` + `DEFAULT` +
  `APP_CALENDAR` that starts `Settings.ACTION_SETTINGS` and finishes.

## Selecting it

The layout must be selected for the remote. Here the selection was made as
`shell` with a small dex run through `app_process`. It calls the public `IInputManager` methods with the
remote's `InputDeviceIdentifier` (descriptor, vendor, product):

```text
addKeyboardLayoutForInputDevice(identifier, "<package>/<package>.<Receiver>/settings_key")
setCurrentKeyboardLayoutForInputDevice(identifier, same descriptor)
```

```sh
adb push layoutctl.dex /data/local/tmp/
adb shell 'CLASSPATH=/data/local/tmp/layoutctl.dex app_process / LayoutCtl apply'
```

No system file is written. `getCurrentKeyboardLayoutForInputDevice` read back
the layout the next day.

Undo: `removeKeyboardLayoutForInputDevice` with the same arguments, then
uninstall the provider app.

**Gotcha:** right after boot the Bluetooth remote is not an input device until
a key is pressed, so a setup step has to ask for a key press first or it will
not find the remote.

## What the owner checked

On the physical remote:

- Settings opens from Home and from YouTube.
- Held Volume ramps.
- The mapping survived a reboot.

A second VH22 owner in [#1680](https://github.com/Gathin23/cleanyourtv/issues/1680)
used the same idea for the TV key as well, and reports it held across a
standby/wake and a remote reconnect.

## What it does not fix

- Only the keys named in the overlay change. Anything else `HomeKeyService`
  used to handle stays unhandled.
- It reserves the Calendar category: a real calendar app would compete for the
  key and Android would show a chooser.
- The layout is selected per input device. Standby/wake, a re-paired or new
  remote, and firmware updates were not tested on this unit.
- The provider app and the dex are built from source by the owner; nothing is
  shipped here.

This report omits network addresses, serials, Android IDs and account details.
