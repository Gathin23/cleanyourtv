# Sony BRAVIA 4K VH22 (2023), Google TV 12

The same firmware build as [`sony-bravia-4k-vh21`](../sony-bravia-4k-vh21/), so its notes apply. What this unit
added:

## Launcher: the redirect route on Wi-Fi

`cmd package set-home-activity` prints `Success` and changes nothing on this build: Home still resolves to
`com.google.android.apps.tv.launcherx`, and with launcherx disabled it falls to setupwraith's
`.RecoveryActivity`. The installer's Home check therefore fails either way and rolls back as designed.
What worked, as on the second and third VH21 units:

- Keep launcherx and setupwraith enabled, and set `stock_home_redirect` (this folder's `config.json`).
- Install with `STOCK_LAUNCHER=none HOME_KEY_SERVICE=1 launcher/build-and-install.sh`, or on Windows
  `launcher\build-and-install.ps1 -StockLauncher none -HomeKeyService` (the PowerShell script reads these as
  parameters, not environment variables). It still ends on the Home check, before it enables the helper.
- Enable `com.gathin.tvhome/.HomeKeyService` the way the script's `enable_service` does, then confirm with
  `dumpsys accessibility` that the "Home button" service is bound.

Checked: Home pressed in YouTube lands on `com.gathin.tvhome/.MainActivity` (`mFocusedApp`), the real remote's
Home button too, and after a reboot the TV started straight into the new Home with the helper bound.

## Undo

`TV_HOST=<tv ip> tools/restore.sh examples/sony-bravia-4k-vh22/debloat.txt`
