# Sony BRAVIA 4K AE2 (EU, Android 14): the Settings and app buttons without launcherx, and a held volume that still ramps

An owner report from 2026-10-28, with a read-back of every button on the physical remote. It is not a debloat
list and not a launcher option. It extends the Android 12 note for the VH22
([sony-bravia-4k-vh22-settings-key.md](sony-bravia-4k-vh22-settings-key.md), [#1680](https://github.com/Gathin23/cleanyourtv/issues/1680))
in two ways: it covers the remote's five app buttons as well as Settings, and it records what changes on
**Android 14**, where that note's selection step silently does nothing.

## Observed firmware

| Field | Observed value |
|---|---|
| Manufacturer | `Sony` |
| Android model | `BRAVIA 4K AE2` (sold as BRAVIA 8) |
| Device | `BRAVIA_AE2` (EU firmware) |
| Android / API | `14` / `34` |
| Firmware build | `BRAVIA_AE2_EU-user 14 UKN4.250422.001 603100 release-keys` |
| Remote | `SONY TV VRC 001`, vendor `054c`, product `0x0eae` (Bluetooth), `KeyboardType: 1` in `dumpsys input` |

The launcher is CleanYourTV v3.1 as Home. `launcherx` and `setupwraith` are disabled, no accessibility
service is bound, and `setupwraith` did not own the saved Wi-Fi network (`cname=android.uid.system`).

## The problem

With `launcherx` disabled, the remote's Settings button and its Netflix, YouTube, Prime Video, Disney+ and
Crunchyroll buttons do nothing. A key helper (`HomeKeyService` or `InputKeyService`) brings them back but, as on
the VH22 and the sets in [the key-repeat section](../TROUBLESHOOTING.md#a-key-helper-is-bound-and-a-held-remote-button-stopped-repeating),
holding Volume then moves one step per press. Keeping `launcherx` enabled with `stock_home_redirect` also works
([the VU3 notes](../../examples/sony-bravia-vu3/README.md)) but leaves the stock launcher running.

Why they die, from decompiling `SonySystemService.apk` (pulled from the TV, read with the REA tool):

- The five app buttons arrive as `KEYCODE_VIDEO_APP_*` / `KEYCODE_BUTTON_8` global keys and go to
  `com.sony.dtv.sonysystemservice.remotekey.GlobalKeyReceiver` (`dumpsys window policy` lists the mapping).
  `GlobalKey.handleGlobalKey` then builds the launch and, on a Google TV build (`hasAmati()`), sends it only
  as a broadcast to `com.google.android.apps.tv.launcherx` (`GlobalButtonLaunch_Receiver`). With `launcherx`
  disabled nothing receives it and nothing is logged as failing.
- The Settings button is not a normal key at all: Android starts
  `android.app.action.TOGGLE_NOTIFICATION_HANDLER_PANEL` for `launcherx/.dashboard.DashboardHandler`.

## Button codes on this remote

Read from `logcat` (`DefaultMenuUiController: requestClose ... scanCode=`) and `getevent -lt /dev/input/event6`
while pressing each button:

| Button | Scancode | HID usage |
|---|---|---|
| Settings | 646 | `0x000c0586` |
| Netflix | 583 | `0x000c0547` |
| YouTube | 485 | |
| Prime Video | 490 | |
| Disney+ | 491 | |
| Crunchyroll | 507 | |

Flush `getevent` line by line when writing it to a file (`... | while read l; do echo "$l" >> file; done`): a
plain `> file` was empty when `timeout` stopped it.

## The overlay

Same idea as the VH22 note: Android lets an app provide extra keyboard layouts, and a layout of type `OVERLAY`
changes only the keys it names. Six unused Android app keys carry the six buttons:

```text
type OVERLAY
map key 646 CALENDAR
map key usage 0x000c0586 CALENDAR
map key 583 CALCULATOR
map key usage 0x000c0547 CALCULATOR
map key 485 CONTACTS
map key 490 ENVELOPE
map key 491 MUSIC
map key 507 EXPLORER
```

Android turns those keys into `ACTION_MAIN` with the matching `CATEGORY_APP_CALENDAR`, `APP_CALCULATOR`,
`APP_CONTACTS`, `APP_EMAIL`, `APP_MUSIC` and `APP_BROWSER`. Check first that nothing on the TV claims them
(`cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.APP_CALENDAR`): here
all six were unclaimed, and `APP_GALLERY` was not. The provider app needs no permissions:

- a receiver for `android.hardware.input.action.QUERY_KEYBOARD_LAYOUTS` with `KEYBOARD_LAYOUTS` metadata
  pointing at an XML file that lists the layout above;
- one `Theme.NoDisplay` activity with six `activity-alias` entries, each with `MAIN` + `DEFAULT` + one of the
  categories. It reads the category from `getIntent().getSelector()`, then opens `Settings.ACTION_SETTINGS` or
  the app's launch intent (`getLeanbackLaunchIntentForPackage`). On Android 11 and newer it needs a
  `<queries>` entry for each target package.

Pressing a button logs `START u0 {... cmp=<provider>/.KeyCalculator sel=... APP_CALCULATOR}` followed by the
app's own `START`, and no `GlobalKey.handleGlobalKey` line from Sony's service.

## What changes on Android 14

The VH22 note selects the layout with `addKeyboardLayoutForInputDevice` and `setCurrentKeyboardLayoutForInputDevice`
through `app_process`. On this Android 14 build both calls return without an error, the layout is listed by
`getKeyboardLayouts()`, and nothing happens: `getCurrentKeyboardLayoutForInputDevice` reads back `null`,
`getEnabledKeyboardLayoutsForInputDevice` is `[]`, and `dumpsys input` shows `KeyboardLayoutInfo: <not set>`.

`com.android.server.input.KeyboardLayoutManager` (decompiled from `/system/framework/services.jar`) explains it:

- `setCurrentKeyboardLayoutForInputDevice` logs "API not supported" and returns while
  `FeatureFlagUtils.isEnabled(context, "settings_new_keyboard_ui")` is true, which is the default.
- The new `setKeyboardLayoutForInputDevice` (per input method and subtype) does store a value, but
  `getKeyboardLayoutForInputDeviceInternal` returns `null` for a device that is not `isFullKeyboard()`.
  The remote reports `KeyboardType: 1` (not alphabetic), so no overlay is ever applied to it.
- `getKeyboardLayoutOverlay` with the flag off goes straight to the legacy `getCurrentKeyboardLayoutForInputDevice`,
  with no keyboard-type check. That is why the Android 12 recipe works there.

So the working order on this set was:

```sh
tools/tv.py 'settings put global settings_new_keyboard_ui false'
# then the VH22 note's two calls: addKeyboardLayoutForInputDevice, then setCurrentKeyboardLayoutForInputDevice,
# with InputDeviceIdentifier(descriptor, 0x054c, 0x0eae) and the layout id
# <provider package>/<provider package>.<LayoutsReceiver>/<layout name>
```

Read it back with `getCurrentKeyboardLayoutForInputDevice` from the same `app_process` helper: it should now
print the layout id instead of `null`.

`settings_new_keyboard_ui` is a global feature-flag override; the flag was unset (`settings get global ...`
printed `null`) before. Undo: `settings delete global settings_new_keyboard_ui`, then the layout removal below.
It changes how Android handles physical-keyboard layouts for every device, which on a TV whose only
keyboard-like device is the remote makes no visible difference.

The descriptor passed to `InputDeviceIdentifier` comes from `dumpsys input` (`Descriptor:` of the remote). It is
a device identifier, so it is not printed here.

## What the owner checked

On the physical remote, with `launcherx` disabled and no accessibility service bound:

- Settings opened from Home. Netflix, YouTube, Prime Video, Disney+ and Crunchyroll each opened their app.
- Home worked, and held Volume ramped.
- With `launcherx` still enabled the same presses took this path, not Sony's.

After a restart of the TV, the same six buttons still worked, so the layout and the flag override survive a reboot.

Not checked: a standby/wake, a re-paired remote, or a firmware update.

## What it does not fix

- Only the keys named in the overlay change. The Input button (scancode 563 on the VH22) was left alone and
  still opens Sony's own overlay; the launcher's Inputs pill covers inputs.
- It reserves six Android app categories: a real calendar, calculator, contacts, email, music or browser app
  would compete for the keys and Android would show a chooser.
- The layout is selected per input device. A new or re-paired remote needs the selection again, after a key
  press on it, because the Bluetooth remote is not an input device before then.
- The provider app and the `app_process` helper are built from source by the owner; nothing is shipped here.

Undo: `removeKeyboardLayoutForInputDevice` with the same arguments, uninstall the provider app, and delete
the flag override above.

This report omits network addresses, serials, Android IDs, device descriptors and account details.
