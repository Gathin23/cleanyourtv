# Wanbo T2 Max, Android 6 version (MStar, `T2 M+`)

Observed on one projector, in France, in a single session. This is a device report, **not a debloat list**: the
shell user cannot disable anything on this firmware, so nothing was disabled. It also records what this repo's
launcher needed to run on Android 6; that build is a local copy, not something this repository ships.

The T2 Max was sold in two versions. This note is about the **Android 6** one. The Android 9 T2 Max has a different
mainboard; owners on XDA report that its firmware will not flash on the Android 6 unit, and there is no custom ROM
for this MStar board, so the Android version cannot be raised.

| Field | Observed value |
|---|---|
| Retail model | Wanbo T2 Max; `ro.product.model` is `T2 M+` |
| Manufacturer / device | `MStar Semiconductor, Inc.`, device and board `bennet`, platform `mainz`, `armeabi-v7a` |
| Android / SDK | `6.0` / `23` |
| Build | `aosp_bennet-userdebug 6.0 MRA58K W073_DC.1080T2.Zeasn.21041417 test-keys` |
| RAM | 862 MB (`MemTotal: 882704 kB`), about 315 MB free on the stock home screen |
| Display | 1920x1080 at 240 dpi |
| Stock Home | `cn.zeasn.launcher.vstoresubclient.mstar358` ("Zeasn Launcher") |

## Connecting

Settings → About → tap the build number seven times, then Developer options → **USB debugging**. Network ADB then
answers on **5555**, with the usual "Allow USB debugging?" prompt (`ro.adb.secure=1`).

After a reboot, 5555 refuses connections again although Developer options stay on: toggle USB debugging off and on
to get it back. So a reboot is enough to close it.

## Nothing can be disabled

```
$ pm disable-user --user 0 com.android.galaxy4
Error: java.lang.SecurityException: Permission Denial: attempt to change component state from pid=4700, uid=2000, package uid=10025
$ pm hide com.android.galaxy4
Error: java.lang.SecurityException: Neither user 2000 nor current process has android.permission.MANAGE_USERS.
```

The same as on the [Xiaomi Mi TV 4S](xiaomi-mitv4-ansm0.md), the other Android 6 set here. `cmd` does not exist
yet on Android 6, and `pm query-activities` is unknown. The build is `userdebug` with `ro.debuggable=1`, so
`adb root` would probably work, but it was not tried: there is little to gain. The projector has 76 packages,
none of them user-installed, and the heaviest non-system processes are Google Play services (about 44 MB and
21 MB PSS), the Zeasn launcher (27 MB) and `com.zeasn.services.general` (11 MB).

Two packages come disabled from the factory: `com.ecloud.eairplay` and `com.google.android.youtube.tv`. The factory
menus `mstar.factorymenu.ui` and `com.toptech.factorytools` are installed; they were not opened.

## The projector's own apps

Only the Play Store has a leanback entry. Everything else declares a plain `LAUNCHER` activity:

| App | Package |
|---|---|
| LocalMM (USB media player) | `com.jrm.localmm` |
| EShareServer (screen mirroring) | `com.ecloud.eshare.server` |
| Mcast (Miracast receiver) | `com.toptech.mitv.wfd` |
| MTvPlayer (the HDMI input) | `com.mstar.tv.tvplayer.ui`, opens `.channel.SourceInfoActivity` |
| ZoomSettings (picture size) | `com.example.zoomsettings` |
| TV magasin (Zeasn store) | `cn.zeasn.toptech.projector.msd358.vstoresubclient` |
| Browser, Settings, Downloads | `com.android.browser`, `com.android.settings`, `com.android.providers.downloads.ui` |

The Netflix, Prime Video and Facebook tiles of the stock home screen are shortcuts into the Zeasn store, not
installed apps.

## Running the launcher on Android 6

This repo's launcher has `minSdk 26`, so `pm install` fails with `INSTALL_FAILED_OLDER_SDK`
([TROUBLESHOOTING](../TROUBLESHOOTING.md)). A copy built with `minSdk 23` runs on this projector after these
changes, all found with `lintRelease` (66 `NewApi` errors) or on the device:

- `coreLibraryDesugaring` (`com.android.tools:desugar_jdk_libs`) for `java.time` and `java.util.function`.
- API 24 calls behind a version check: `registerDefaultNetworkCallback` (a `registerNetworkCallback` with
  `NET_CAPABILITY_INTERNET` instead), `forceHasOverlappingRendering`, `TvInputInfo.isHidden` and
  `loadCustomLabel`.
- **Crash at the first `onResume`**: `getSystemService(TV_INPUT_SERVICE)` returns a manager, but its service is
  missing, and `getTvInputList()` throws a `NullPointerException` from `ITvInputManager`. The copy checks it once
  and treats the set as having no `TvInputManager`.
- **Only the Play Store tile showed up**, since the projector's apps have no leanback entry: the copy also lists
  `LAUNCHER` apps as tiles, and `hidden_apps` trims them.
- `live_tv_package` resolves through `getLeanbackLaunchIntentForPackage` only; MTvPlayer needs a
  `getLaunchIntentForPackage` fallback for the Inputs pill to appear.
- **Roboto everywhere**: `android:fontFamily="@font/..."` is ignored before Android 8, and the variable Outfit
  would load at its default weight (100). The copy ships four static instances (200, 300, 400, 500, cut with
  fontTools) and an inflater factory that sets them through `ResourcesCompat.getFont` on `TextView` and
  `TextClock`.
- `wifi_status_color`: `setCompoundDrawableTintList` does not reach the vector icon on Android 6; tinting the
  drawable itself does.

With these changes, the following worked from injected keys:
- **Home**: the stock home cannot be disabled, but the first Home press offers a chooser ("Zeasn Launcher" or
  the new one), and **Always** kept the new launcher after a reboot.
- **Tiles and pills**: tiles open their apps and Back returns, the Inputs pill opens MTvPlayer, and the gear opens
  Settings.
- **Idle clock**: it comes up over a wallpaper (`clock_wallpaper`).

Not tested: a physical remote, several days of use, `ClockDream` as the system screensaver, and Play Store sign-in.

## Time zone

The unit came set to `America/Sao_Paulo` with automatic time zone on, so every clock was 5 hours off in France.
Settings → Date & time fixes it, and so does the shell, without root:

```
settings put global auto_time_zone 0
service call alarm 3 s16 Europe/Paris
```

The time itself still comes from the network (`auto_time` stays 1).
