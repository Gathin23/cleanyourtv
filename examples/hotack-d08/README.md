# Hotack D08-series projector (Android 11, Allwinner, reports ADT-3)

The Hotack D08 series - the board behind many Magcubic HY300/HY320-class projectors. The firmware
reports the generic `ADT-3` / `adt3` identity on Android 11 with a 1280x720 panel, `com.htc.*`
packages (Hotack's OEM stack, not HTC Corp.) and the stock launcher
`com.htc.htclauncherhighenglishd08`. From the report in #1576.

| File | What it is |
|---|---|
| `debloat.txt` | The 20 packages that were disabled, grouped and commented, with what to keep and why |
| `config.json` | The App library, Live TV and Inputs setup; push it with `tools/theme.sh` |

## Install

All commands run from the repo root, with the projector's IP in place of `<projector ip>`.

1. Disable the list:
   `TV_HOST=<projector ip> tools/disable.sh examples/hotack-d08/debloat.txt`
2. Before building the launcher, add the two things this folder cannot ship (the launcher reads
   them from its own files, not from a pushed config):
   - in `launcher/app/src/main/res/values/config.xml`, inside the `settings_fallback_components`
     array: `<item>com.htc.htcsettings/com.htc.activity.HomeActivity</item>` - the projector's
     settings resolve through the standard `LAUNCHER` category, not `LEANBACK_LAUNCHER`;
   - in `launcher/app/src/main/AndroidManifest.xml`, inside `<queries>` (Android 11 package
     visibility): `<package android:name="com.htc.htcsettings" />`,
     `<package android:name="com.softwinner.awsource" />` and
     `<package android:name="com.softwinner.awlivetv" />`.
3. Build and install the launcher, letting it disable the stock HTC launcher:
   `TV_HOST=<projector ip> STOCK_LAUNCHER=com.htc.htclauncherhighenglishd08 launcher/build-and-install.sh`
4. Push the config:
   `TV_HOST=<projector ip> tools/theme.sh examples/hotack-d08`

Done when HOME shows the Clean Your TV launcher and its Settings pill opens the projector's own
settings.

## config.json

It holds only what this projector needs:

- `app_library` on. Many apps on this firmware - Netflix among them, and the IPTV/OTT apps people
  sideload on projectors - only declare `android.intent.category.LAUNCHER`, so without the App
  library they never appear. Seed `library_apps` with the apps you want pre-added to it, for
  example `"library_apps": ["com.netflix.mediaclient"]`.
- `live_tv_package` on `com.softwinner.awlivetv`, and the Inputs pill opening Allwinner's own source
  picker (`com.softwinner.awsource`): nothing on this firmware handles the passthrough URI as a TV
  app, the same as the HY300 Pro+ folder found.
- `HW2`-`HW4` hidden from the Inputs list: the board registers three HDMI inputs and CVBS, but the
  projector has one physical HDMI port (`HW1`).

## Notes

- **The saved Wi-Fi is safe.** `com.htc.htcsettings` owns it (`lname` in `dumpsys wifi`), and it
  stays enabled, so disabling the stock launcher does not take the projector off Wi-Fi. No
  setupwraith on this firmware: Android's `FallbackHome` is the only other Home claimant.
- **The handover in the report was done by hand** and worked on the first attempt:
  `pm disable-user --user 0 com.htc.htclauncherhighenglishd08`, then
  `cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity`, with `mFocusedApp`
  checked. Step 3 above does the same through the install script.
