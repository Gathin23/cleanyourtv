# Epson EF-21: the remote's Settings (gear) key without a key-filtering helper, over IR

An owner report from 2026-11-19 for the unit in [`examples/epson-ef21/`](../../examples/epson-ef21/README.md).
It is not a launcher option. It applies the keyboard-layout overlay from the
[VH22 Settings-key note](sony-bravia-4k-vh22-settings-key.md), with the Android 14 step from the
[AE2 note](sony-bravia-4k-ae2-eu-remote-keys.md), to an SDMC board and to an **IR** remote, which the earlier
notes had not tried.

## Observed firmware

| Field | Observed value |
|---|---|
| Manufacturer | `Epson` (SDMC-built board) |
| Product / device | `HB35` / `himalaya`, MediaTek m7332 |
| Android / API | `14` / `34` |
| Firmware build | `UKNV.260904.001` |
| Remote | `MStar Smart TV IR Receiver`, vendor `3697`, product `0001`, bus `0x0018` (IR), `Classes: KEYBOARD \| DPAD` |
| Key files | `/vendor/usr/keylayout/Vendor_3697_Product_0001.kl`, `/system/usr/keychars/Generic.kcm` |

The launcher is CleanYourTV built from `main` after v3.5.0, as Home. `launcherx` and `setupwraith` are
disabled and no accessibility service is bound.

## The problem

With `launcherx` disabled the gear key does nothing, on Home and in apps. It reaches the IR driver as
scancode 249 (`0x00f9`):

```text
/dev/input/event1: EV_KEY       00f9                 DOWN
[GLUE][IR]u8KeyCode: 0x00f9 , u8KeyState: 0x0
```

`Vendor_3697_Product_0001.kl` has no `key 249` line (nor does any other `.kl` on the unit), so Android turns
it into no keycode. Logcat shows only the IR driver lines above; no service and no activity reacts. How the
stock setup answered the key before the debloat was not traced.

What did not help, or cost too much:

- `settings_key_scancodes` 249 with `HOME_KEY_SERVICE=1` is the documented fix, but on this board a bound
  key-filtering helper drops held-key repeats (holding Volume moves one step per press), as on the
  [BenQ GV32](../../examples/benq-gv32/README.md), which has the same board.
- The BenQ GV32's launcherx redirect route (launcherx enabled, its home sent back to this launcher, the helper
  on without filtering keys) brought neither the gear back nor kept the ramp here: with no keycode there is
  nothing for launcherx to answer. It was undone.

## The overlay

Same idea as the VH22 note: a layout of type `OVERLAY` changes only the keys it names. Scancode 249 becomes
`CALENDAR`, Android starts `MAIN` + `APP_CALENDAR`, and a small activity answers it by opening Settings.

```text
type OVERLAY
map key 249 CALENDAR
```

The `map key` line works although the vendor `.kl` has no line for 249. There is no HID usage line: the IR
receiver sends no `MSC_SCAN` value. Before installing anything, nothing on the unit claimed the category
(`cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.APP_CALENDAR` printed
`No activities found`), and `android.settings.SETTINGS` resolved to `com.android.tv.settings/.MainSettings`.

The provider app has the two components the VH22 note lists and no permissions: a receiver for
`android.hardware.input.action.QUERY_KEYBOARD_LAYOUTS` with `KEYBOARD_LAYOUTS` metadata pointing at the
layout, and a `Theme.NoDisplay` activity for `MAIN` + `DEFAULT` + `APP_CALENDAR` that starts
`Settings.ACTION_SETTINGS` and finishes. It was built with build-tools 34 directly (`aapt2`, `d8`,
`apksigner`), without Gradle.

## Selecting it

As on the AE2 (Android 14), the legacy selection only takes effect with the new keyboard UI flag off:

```sh
TV_HOST=<tv ip> tools/tv.py 'settings put global settings_new_keyboard_ui false'
```

The flag was unset (`null`) before. Then, as `shell` through `app_process`, a small dex calls
`addKeyboardLayoutForInputDevice` and `setCurrentKeyboardLayoutForInputDevice` with the remote's
`InputDeviceIdentifier` (its descriptor from `dumpsys input`, vendor `0x3697`, product `0x0001`) and the layout
id `<provider package>/<provider package>.<LayoutsReceiver>/<layout name>`.
`getCurrentKeyboardLayoutForInputDevice` read back `null` before and the layout id afterwards.

The IR receiver is an input device from boot, so the Bluetooth gotcha in the VH22 note (press a key first)
does not apply.

## What the owner checked

On the physical IR remote, with `launcherx` disabled and no accessibility service bound:

- The gear opens Settings from Home and from an app.
- Held Volume still ramps.
- After a standby and wake, and after a restart from Settings, the gear still opened Settings and the
  layout id still read back.

Not checked: a firmware update.

## What it does not fix

- Only scancode 249 changes. The remote's projector-settings key, which opens Epson's own menu, was never
  affected and is left alone.
- It reserves the Calendar category: a calendar app installed later would compete for the key, and Android
  would show a chooser.
- The provider app and the dex are built from source by the owner; nothing is shipped here.

Undo: `removeKeyboardLayoutForInputDevice` with the same arguments, then
`TV_HOST=<tv ip> tools/tv.py 'settings delete global settings_new_keyboard_ui'`, then uninstall the provider app.

This report omits network addresses, serials, Android IDs, device descriptors and account details.
