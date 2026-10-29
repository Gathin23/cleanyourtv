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
