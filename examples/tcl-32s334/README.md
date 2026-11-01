# TCL 32S334, North American firmware

The owner identifies both sets by the rear-label model number TCL 32S334. They report the Android
product `BeyondTV5`, device `BeyondTV`, and the North American RTD2851 client. The build is
`PPR1.180610.011 AP13` on Android TV 9, with the 2020-11-01 security patch.

The tested packages are in [`debloat.txt`](debloat.txt). The owner reports that both TVs ran this setup
for several days without issues, and CleanYourTV remained Home after reboot. The stock launcher is
disabled only after the launcher build script confirms the handover.

## Firmware behavior and scope

- `com.tcl.bootadservice` and `uk.co.freeview.systemdistributor` were already disabled before setup on
  both sets; the list leaves them alone.
- TV 2 also had Play Music, Play Games, and Play Movies & TV disabled before setup. Those three were
  enabled to match TV 1 and are not part of the shared list.
- TCL re-enabled `com.tcl.smartalexa` at reboot on both sets after it had been disabled. It is left
  enabled and excluded from the action list.
- Chromecast and the firmware updater remain enabled. Miracast was already off in Settings on both TVs.
- YouTube is disabled by choice. YouTube TV is a separate app and was installed on these TVs.
- Fox Sports was not installed: Play Store reported the TV as incompatible. This is not part of the
  debloat list.

The existing [`tcl-beyondtv`](../tcl-beyondtv/) entry covers an India-market BeyondTV on Android 9.
These are 32S334 / BeyondTV5 North American units, so their disabled list is kept separately.
