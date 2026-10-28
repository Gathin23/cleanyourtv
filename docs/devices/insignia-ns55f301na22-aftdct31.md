# Insignia 55" F30 4K Fire TV (NS-55F301NA22, AFTDCT31): session notes

Observed on one unit in October 2026. The retail label is NS-55F301NA22; the set reports
`ro.product.model` **AFTDCT31**, codename `duckie`, Fire OS **7.0** / Android 9 (SDK 28), build
**PS7717.5741N** (`ro.build.fingerprint` `Amazon/duckie/duckie:9/PS7717.5741N/0036408028416:user/amz-p,release-keys`).
The panel is 3840x2160 with Fire OS's own 1920x1080 size override (`wm size`), which is the default and was left alone.

[`examples/insignia-ns55f301na25/debloat.txt`](../../examples/insignia-ns55f301na25/debloat.txt) already covers this
model code (it names firmware PS7715.5585N). This note is not a new list. It adds a second firmware's observations and what
happened when a third-party launcher was tried on Fire OS 7.

## What was disabled

Forty packages were disabled with `pm disable-user --user 0` (`pm list packages -d` shows 41: `com.android.nfc` was already
disabled before the session). Backups of the package lists, `getprop` and all three settings namespaces were taken first,
and the work was done in small batches with a screenshot of Home between them. Nothing was uninstalled.

- Telemetry and tracking: `com.amazon.tv.fw.metrics`, `wirelessmetrics.service`, `perfcollection`, `perfc`, `tv.acr`,
  `hybridadidservice`, `dp.logger`, plus three that are **not** in the NS-55F301NA25 list: `com.amazon.neo.minerva`,
  `com.amazon.neo.minerva.logcat` and `com.amazon.tv.developer.dataservice`.
- Recommendations, shopping, tutorials, demo content and unused Amazon apps: `media.recommendations`, `shoptv.client`,
  `shoptv.firetv.client`, `sneakpeek`, `minitv.android.app`, `tv.releasenotes`, `tmm.tutorial`,
  `storm.lightning.tutorial`, `tv.alexanotifications`, `tv.alexaalerts`, `bueller.music`, `bueller.photos`, `gamehub`,
  `tv.localgallery`, `tv.democontent.provider` and the five `com.amazon.dummy.*` stubs.
- The owner's own choice of streaming apps (not part of any list).

Fire OS refused these as protected packages (`SecurityException: Cannot disable a protected package`):
`com.amazon.device.metrics`, `com.amazon.device.logmanager`, `com.fireos.usagestats.proxy`,
`com.amazon.ftvads.deeplinking`, `com.amazon.tv.launcher` and `com.amazon.firehomestarter`. The first and fourth are also
named in the NS-55F301NA25 list header; the logmanager and usagestats ones are new for this firmware.

After a reboot `pm list packages -d` was identical to the list before it (diffed, nothing re-enabled), Home came up, and
Netflix started and the replacement launcher's Inputs menu still listed HDMI 1 to 3, Composite and Live TV. Switching to an
HDMI input from that menu worked with the real remote (the screenshot of an HDMI input is empty, so it was checked by eye).

## A third-party launcher on Fire OS 7 (experimental)

The NS-55F301NA25 list says Fire OS will not allow a third-party launcher. That matches what was seen:
`pm disable-user com.amazon.tv.launcher` is refused, `cmd package set-home-activity` prints `Success` and changes
nothing, and `query-activities` still lists `com.amazon.tv.launcher/.ui.HomeActivity_vNext` at priority 950 ahead of a
launcher at priority 0.

A small launcher was still made to work by an **accessibility service** that watches for the stock `HomeActivity_vNext`
window and starts the replacement. What was measured:

- After an injected `KEYCODE_HOME` the system starts the stock Home about a second later; the service's redirect started
  the replacement about 0.3 s after that. Fire OS blocks activity starts for roughly 5 s after a Home press, and the
  replacement only drew after that block lifted. With the real remote the owner saw about 2 s, the old home screen, then
  the new one 1.5 s later.
- A key-event filter (`flagRequestFilterKeyEvents`) did not receive injected keys, so catching the Home key earlier could
  not be shown from the shell. It was not confirmed with the real remote either.
- Reinstalling the app, or `am force-stop`, clears `enabled_accessibility_services`; it has to be set again with
  `settings put secure enabled_accessibility_services <pkg>/<service>` and `accessibility_enabled 1`.
- It is a background service, so it does not belong in the CleanYourTV launcher. Treat it as a note, not a feature.

### Settings on Fire OS 7

The stock Settings page is part of `HomeActivity_vNext`, so a redirect like the one above bounces it: pressing a
Settings button in the replacement launcher flashed and returned. Pages that are their own activity in
`com.amazon.tv.settings.v2` opened normally:

| Opens | Does not open |
|---|---|
| `.tv.network.NetworkActivity`, `.tv.device.DeviceActivity` (Device & Software, with Developer options), `.tv.controllers_bluetooth_devices.ControllersAndBluetoothActivity`, `.tv.applications.ApplicationsActivity`, `android.settings.ACCESSIBILITY_SETTINGS`, `android.settings.WIFI_SETTINGS` | `.tv.display_sounds.DisplayAndSoundsActivity`, `.tv.preferences.PreferencesActivity`, `.tv.my_account.MyAccountActivity` (`Permission Denial ... requires com.amazon.tv.permission.LAUNCHER_SETTINGS`, a signature|privileged permission); `android.settings.DISPLAY_SETTINGS` and `SOUND_SETTINGS` open a chooser |

Getting to the full stock Settings needs a pause in the redirect. **Keep ADB on until a way into Settings has been checked with
the real remote**, because turning ADB debugging back on needs Settings (Device & Software, Developer options).

## Screensaver during tests

After a few minutes idle the Fire TV screensaver (a dream) covers the screen, so a screenshot taken then shows the
screensaver and not the launcher. `KEYCODE_WAKEUP` brings the screen back.

## Closing ADB

`settings put global adb_enabled 0` followed by a reboot made port 5555 answer `Connection refused`. The shell cannot delete
`/data/misc/adb/adb_keys`, so the stored authorisation stays until the TV's own Developer options revoke it.

## Not checked

Switching to Live TV from the replacement launcher (screenshots of the tuner are empty), HDMI-CEC, and firmware updates
after the list was applied.
