# Sony KD-55X9000H (PA firmware): compatibility notes

This is a one-day owner report from 2026-10-21, not a tested debloat list.
The retail model was supplied by the owner; Android exposes a generic
platform model. Testing used a locally customized launcher derived from
upstream commit `9071ec8d2f41826778f6b4c7f5f42dadc22f5b22`, rather than an
unmodified build of the current upstream main branch.

## Observed firmware

| Field | Observed value |
|---|---|
| Owner-reported retail model | Sony `KD-55X9000H` |
| Manufacturer | `Sony` |
| Android model | `BRAVIA VH1` |
| Device | `BRAVIA_VH1` |
| Android / API | `12` / `31` |
| Firmware build | `BRAVIA_VH1_PA-user 12 STT2.230505.001.S137 626241 release-keys` |

This unit has Google's `com.google.android.tvlauncher` stock Home and Samba
packages. It differs from the [China-firmware X9000H report](sony-x9000h-cn.md),
which uses a Dangbei Home package. Sharing the X9000H name does not establish
that package choices or launcher handover steps apply across firmware variants.

## Stock Home and handover

The original Home component was:

```text
com.google.android.tvlauncher/.MainActivity
```

The customized `com.gathin.tvhome/.MainActivity` installed and started
successfully. During Home handover, the stock Home package was disabled with
`pm disable-user`. The HOME resolver and foreground activity then both
identified the custom launcher. This records the tested setup, without
establishing that disabling stock Home was necessary.

A TV reboot retained the custom Home and all recorded package states. The
owner also confirmed that custom Home appeared after revoking debugging
authorizations, switching debugging off and restarting the TV. The optional
Home/Input accessibility helpers were not enabled.

Record the original Home component and package enablement states before
changing them. On-device restoration to the original Home was not exercised
in this report.

## Sony TV inputs

The Sony TV/player package is `com.sony.dtv.tvx`, with activity
`com.sony.dtv.tvx/.MainActivity`. The local launcher configuration used it as
`live_tv_package` and hid its duplicate app-grid tile.

The Inputs menu listed HDMI 1-4, Video and Live TV. Selecting HDMI 3 brought
the Sony TV player to the foreground. This activity check establishes input
routing; its black screenshot does not establish picture or audio quality.
The owner's later grouped app/playback/HDMI/casting check was reported as
successful, without itemizing each input or casting protocol. Tuner reception
was not separately tested.

## Recorded package changes

Three optional cleanup packages were disabled using `pm disable-user`:

- `tv.samba.ssm`
- `tv.samba.ssm.ui`
- `com.google.android.feedback`

The stock Home package, `com.google.android.tvlauncher`, was a separate fourth
change. No packages were uninstalled. All four changes and the 11 packages
already disabled before this work retained their states after reboot, with
no unexpected disabled packages or originally disabled packages re-enabled.
Sony IDD and the existing casting, voice and firmware-update components were
retained.

These are observations from one unit over one day, rather than a general
safe-disable recommendation. A several-day `examples/` debloat list is not
provided.

## Launcher changes and validation limits

- The local build used Sony-specific resource defaults and a minimum SDK of
  31. It also changed Home handling to retain the focused app and subsequently
  added an `ambient_glow` option to hide the background halo.
- The owner reported that the tested physical remote buttons worked and the
  grouped app/playback/HDMI/casting checks passed. Those reports predate the
  focus-retention and halo updates. Focus retention was later checked using
  injected keys and launcher screenshots.
- The later halo-free APK and local wallpaper/calendar theme were installed;
  theme files were read back and the wallpaper's appearance checked. The
  earlier reboot and owner checks do not establish fresh validation of this
  latest build. The race-calendar panel was outside its display window.
- Several-day stability, standby/wake, individual casting protocols, voice
  input, firmware updates, USB peripherals and tuner reception were not
  independently verified.

This report omits network addresses, hardware serials, Android IDs, account
details, installed-app preferences, screenshots and raw dumps.

## Later remote-key follow-up (24 October 2026)

These checks used a later personal v2.6.0 fork on the same Android 12 firmware above; they do not establish behavior on an unmodified current upstream build.

- With the fork's Apps-only HomeKeyService enabled, holding Volume moved only one step. Disabling only that helper restored continuous adjustment in a physical A/B check, with InputFilterEnabled=false. Unrelated keys returning false in the helper did not avoid the platform filter side effect.
- Simultaneous kernel and callback capture recorded Volume Down held for approximately 1.98 seconds, while the helper received Apps and Back but neither Volume direction. A software-repeat timer inside that service could not work without press/release callbacks; the unsuccessful fallback was removed.
- The Sony TV VRC 001 Apps key was observed as Android ALL_APPS (284), scan 580, HID usage 0x000c0544, vendor 054c/product 0c99. This is remote model data, not a device-unique identifier.
- A separately installed keyboard-layout provider then mapped only that Apps key to an application-launch category, with a direct Library alias in the fork. The owner confirmed Apps from Home and another app, Back and native held Volume, then confirmed the intermediate Home flash was gone. Home ownership and external theme were unchanged, the key-filter helper stayed disabled and no resident watcher remained after the bounded trial.

The [fork's source, setup and rollback notes](https://github.com/Lzchyi/cleanyourtv/blob/v2.6.0-redesign/companions/sony-apps-layout/README.md) describe this opt-in alternative. It reserves the Calculator category and needs one-time shell setup; it is not a stock launcher option or a general recommendation for other TVs. Automatic baseline restoration was practiced before the accepted trial. Restart, sleep/wake and Bluetooth reconnect persistence were not tested for this mapping.

For the general filter/repeat tradeoff and preserving existing accessibility services, see [the troubleshooting guide](../TROUBLESHOOTING.md#a-key-helper-is-bound-and-a-held-remote-button-stopped-repeating). No additional package cleanup is proposed.
