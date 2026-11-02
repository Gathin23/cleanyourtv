# Sony BRAVIA 4K VH2 (XR-55X90JK, Latin America): Apps and Audio buttons after the debloat

This is an owner report from 2026-10-31 for the seventh unit in
[`examples/sony-bravia-4k-vh2/`](../../examples/sony-bravia-4k-vh2/debloat.txt). It is not an upstream
launcher option. It records what the remote's Apps and Audio buttons do once launcherx is disabled, and the
keyboard-layout overlay that gave Apps a job again without a key-filtering helper.

## Observed firmware

| Field | Observed value |
|---|---|
| Manufacturer | `Sony` |
| Android model | `BRAVIA 4K VH2` |
| Product | `BRAVIA_VH2_SA` (Latin American firmware), retail model XR-55X90JK |
| Android / API | `12` / `31` |
| Fingerprint | `Sony/BRAVIA_VH2_SA/BRAVIA_VH2:12/STT2.230505.001.S136/682241:user/release-keys` |
| Remote | `SONY TV VRC 001`, vendor `054c`, product `0x0d8d` (Bluetooth) |
| Home | the owner's own launcher (not this repo's); launcherx and setupwraith disabled |

## Apps button: ALL_APPS goes nowhere

The Apps key arrives as `KEY_APPSELECT`, scan code 580, HID usage `0x000c0544`, and the remote's key layout maps
it to `ALL_APPS`. Android sends that only to the system-image Home, which with launcherx disabled is
`com.android.tv.settings`, and nothing answers it. Logcat on each press:

```text
START u0 {act=android.intent.action.ALL_APPS pkg=com.android.tv.settings} from uid 1000
```

The same regression is described for the [KD-65A8F](sony-kd-65a8f-tw.md). It was fixed here with the overlay
from the [VH22 Settings-key note](sony-bravia-4k-vh22-settings-key.md), applied to the Apps key:

```text
type OVERLAY
map key 580 CALCULATOR
```

The owner's launcher provides the layout (a `QUERY_KEYBOARD_LAYOUTS` receiver) and answers
`MAIN` + `APP_CALCULATOR` with an activity alias that opens its app list. The layout was selected for the
remote as `shell` through `app_process`, with `InputManager.setCurrentKeyboardLayoutForInputDevice`.
`cmd package resolve-activity ... -c android.intent.category.APP_CALCULATOR` found no handler before and only
that alias afterwards. On the physical remote the owner confirmed:

- Apps opens the list from Home and from YouTube; pressing it again in the launcher closes it.
- Held volume still ramps (no accessibility service is involved).
- After a reboot, `getCurrentKeyboardLayoutForInputDevice` still named the layout and Apps still worked.

**Gotcha:** the HID-usage form is `map key usage 0x000c0544 CALCULATOR`. A first try written as
`map usage 0x0c0544 CALCULATOR` (without `key`) made Android drop the whole overlay, so Apps kept sending
`ALL_APPS`, and the only trace is one logcat line at the moment the layout is selected:

```text
E KeyCharacterMap: <package>/<package>.<Receiver>/<layout>:8: Expected keyword after 'map', got 'usage'.
```

`dumpsys input` keeps naming the vendor `.kcm` either way, so it cannot tell whether an overlay is active. The
scan-code line alone was enough on this remote.

## Audio button: the tuner app crashes

`KEYCODE_MEDIA_AUDIO_TRACK` (222) does nothing on Home or in the apps tried. While watching TV it closes
Sony's tuner app (`com.sony.dtv.tvx`, version `14.1.12-S`), which looks as if the button went Home. TVX
receives the key, asks the TV input for its audio tracks (one track here) and then crashes while building its
track dialog:

```text
FATAL EXCEPTION: main
Process: com.sony.dtv.tvx
java.lang.NullPointerException: Attempt to invoke virtual method 'void android.view.View.setVisibility(int)' on a null object reference
    at com.sony.dtv.tvx.tvplayer.common.dialog.DialogListAdapter.getView(DialogListAdapter.java:147)
```

`com.sony.dtv.tvx` has one overlay, `com.sony.dtv.tvx.overlay.sortui`, and it is enabled. Re-enabling the two
disabled packages whose `dumpsys` mentions TVX and that change TV behaviour, `b2b.hotelmode` and
`b2b.rs232csupport`, did not stop the crash. The rest of the list was not restored to test it, so this does
not prove a stock set crashes the same way. No workaround was tried.

## What it does not cover

- Standby/wake, a re-paired or second remote, and firmware updates were not tested with the overlay.
- The Calculator category is reserved: a calculator app would compete for the key.
- Nothing is shipped here: the provider and the selection step are in the owner's own launcher.

This report omits network addresses, serials, Android IDs, MAC addresses and account details.
