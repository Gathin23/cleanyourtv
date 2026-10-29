# Xiaomi MiTV-AXS00 (Android 9): compatibility guidance, no tested list

This is an owner-reported compatibility question, **not a tested debloat list**. It records what the
reporter saw, which existing list most likely fits, and the read-only checks to collect before any
cleanup. No packages are disabled here.

| Field | Reported value |
|---|---|
| Model string | `MiTV-AXS00` |
| Android / API | `9` / `28` |
| Build | `PI.6036 release-keys` |
| Android security patch | 5 June 2021 |
| Kernel | `4.9.113` |

## Which existing list most likely fits

`MiTV-AXS00` as written is not a Xiaomi device code I can confirm; it is one character off
`MiTV-AXSO0` (an uppercase `O`, not the digit `0`), and the reported build `PI.6036` is the exact
firmware the [`xiaomi-mitv-axso0` list](../../examples/xiaomi-mitv-axso0/debloat.txt) targets:
codename `dangal`, sold as Mi TV 4C Pro 2019 and Mi TV 4A Pro, Android TV 9 (SDK 28), Amlogic. Confirm
the board before applying it:

```text
getprop ro.product.device     # dangal on the AXSO0 units
getprop ro.product.model      # the model string the About screen shows
getprop ro.build.display.id    # PI.6036 here
```

If `ro.product.device` reads `dangal`, the `xiaomi-mitv-axso0` list is the starting point; the UHD
sibling [`xiaomi-mitv-axso2`](../../examples/xiaomi-mitv-axso2/debloat.txt) (`dangalUHD`, India, 2 GB)
makes slightly different keep/disable choices. If it reads something else, the Mi TV 4A `PI.*`
MediaTek family is the next closest, e.g. [`xiaomi-mitv-mssp0`](../../examples/xiaomi-mitv-mssp0/debloat.txt)
(Mi TV 4A 32, `PI.5926`). Apply the list, don't copy a different board's packages by hand; a name that
is not installed is simply skipped.

## Self-navigation with the remote's batteries out

The reporter saw the TV navigate on its own, with the remote's batteries removed and after two or
three factory resets, and did not attribute it to CleanYourTV or to HDMI. A set that moves by itself
with no remote in play is almost always another device sending key presses, not the TV:

- **HDMI-CEC from a source device.** Switch HDMI-CEC off in the TV's settings, or unplug HDMI devices
  one at a time, and watch whether the self-navigation stops.
- **A phone paired as a remote** (Mi Remote, the Google TV app). Unpair or sign the phone out and
  watch again.

Settle this first: it is a stock-firmware behaviour, so it should be resolved before any debloating,
and nothing in a debloat list addresses it. These are read-only settings and pairing changes; no
package is disabled to test them.

## Read-only checks to collect before any cleanup

Back up first (package list, disabled-package list, `getprop`, settings), then gather the state that
decides whether the AXSO0 list fits, without changing anything:

```text
pm list packages                                 # everything installed
pm list packages -d                              # anything already disabled
dumpsys package <name>                           # what a package actually is, before deciding on it
getevent -pl                                     # which input device is sending keys (for the self-navigation)
```

`tools/tv.py --probe` prints the model, device and firmware in one call. Take a screenshot of Home
with `tools/tv.py --screenshot shots/home.png` so the launcher and any ad rows are on record. Keep
the TV app, the input/source menu, remote and Bluetooth pairing, the on-screen keyboard, Play Store
and Play Services enabled; disable with `pm disable-user --user 0`, never uninstall. The full
procedure and its safety rules are in [`PROMPT.md`](../../PROMPT.md) and
[`docs/TROUBLESHOOTING.md`](../TROUBLESHOOTING.md).

This note omits the unit's serial number, network addresses and any account details; only the model,
Android version and firmware build are quoted.
