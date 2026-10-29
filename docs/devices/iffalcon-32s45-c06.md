# iFFALCON 32S45 QLED (2025), TCL C06, Android TV 11

From @edman01's report in #1031: one test session on one unit, two packages disabled. Not a several-day list.
The same C06 platform has tested lists in [`tcl-40s5400a`](../../examples/tcl-40s5400a/debloat.txt) and
[`tcl-32s5400a`](../../examples/tcl-32s5400a/debloat.txt), on other firmware builds; the reporter advises against
copying them wholesale without checking each package on this build.

| Field | Observed value |
|---|---|
| Retail model | iFFALCON 32S45 QLED (2025) |
| Manufacturer / model | `TCL` / `Smart TV` |
| Device / product / hardware | `C06` / `C06_2K_GB` / `mt5867` |
| Android / API | Android TV 11 / 30 |
| Build | `AR2101` / `AR21`, TCL software `V8-T221T05-LF1V363` |
| Stock Home | `com.google.android.tvlauncher/.MainActivity` (priority 2) |
| Live TV | `com.tcl.tv/.TVActivity` |

## Disabled

```text
com.tcl.waterfall.overseas
com.google.android.feedback
```

Both stayed disabled after a reboot. Kept: the stock launcher, the setup wizard, `com.tcl.ttvs` (the firmware
updater, automatic updates still on), `com.tcl.messagebox`, `com.tcl.partnercustomizer`, the TV and input
services, Play services, voice and Cast.

## Home

`cmd package set-home-activity` printed `Success`, but the stock launcher kept Home at priority 2 while it was
enabled. The reporter used a **locally modified** launcher: `HomeKeyService` redirecting the stock launcher's
window events, with key filtering off and several stock packages as targets. That change is not in this repo, so
this report does not show how an unmodified build behaves. The repo's own route for a stock launcher that keeps
Home is `stock_home_redirect` with `HOME_KEY_SERVICE=1` (see [themes/README.md](../../themes/README.md)), which
takes one package; it was not tried here.

With the local build, injected Home from Settings, Live TV and TCL's update screen returned to the launcher, and
after a reboot the launcher was in front before any key was injected, with the helper bound.

## Notes

- Opening Settings > About through `DEVICE_INFO_SETTINGS` (`AboutActivity`) hit a `ClassCastException` in TCL's
  `SoftwareUpdateFragment`; entering through the normal Settings menus works, and the update screen
  (`com.tcl.ttvs/.versionUpdateApp.core.UI.NetworkUpdateActivity`) opened from there.
- With `reduced_motion: true`, p95 frame time in one launcher navigation run fell from 32-34 ms to 7 ms (three
  runs each). That is one navigation flow, not overall speed.
- Not checked: holding a remote key, HDMI picture, casting, voice and pairing, long playback, a firmware install
  and a full rollback.
