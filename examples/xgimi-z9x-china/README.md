# XGIMI Z9X projector (Android 12, GMUI 6, China)

Tested on a mainland-China 极米 Z9X (`ro.product.model=XGIMI TV`, `ro.product.name=Product_raptor`,
`ro.product.device=snake_deep`, factory `G0074`) on the HiSilicon Hi3751V660 (`huanglong`) board,
Android 12 / SDK 31, firmware `v6.13.113`, fingerprint
`XGIMI/Product_raptor/snake_deep:12/SP1A.210812.016/v6.13.113:user/dev-keys`, GMUI 6.0
(`persist.sys.misckey.version`). 32-bit userspace (`armeabi-v7a,armeabi`). This is the
mainland-China counterpart of the international Elfin Flip 4K.

**How much testing is behind it:** applied on 2026-11-11, with a screenshot after each debloat
batch and after the launcher handover, then two days of everyday use (2026-11-11 to 2026-11-13)
with no problems. The projector was rebooted during that time and the new Home came back on its
own, so `HomeKeyService` survives a boot on this firmware (unlike the RS10 Ultra's GMUI 6, which
drops it on every boot). A live HDMI source was used and worked.

## What this folder contains

| File | What it is |
|---|---|
| `debloat.txt` | The 16 packages that were disabled, grouped and commented; `com.xgimi.duertts`, `com.google.android.katniss` and `com.xgimi.upgrade` are commented out so the default list keeps voice and OTA |
| `config.json` | Dock (Bilibili, YouTube, the XGIMI app store, 极米投屏), 4 columns, `stock_home_redirect`, `key_filter` true, the HDMI input action and LAUNCHER-only seeds |
| `tv.json` | Entry for the supported-TV index |
| `README.md` | Notes for this firmware: ADB, the Home chooser, Settings, dock |

## Critical notes for this TV

1. **No developer-options ADB toggle.** Network ADB was opened with the open-source
   [code-ant/z7x-adb](https://github.com/code-ant/z7x-adb) app (`local.z7x.adb`), installed from a
   USB stick after renaming the file to `.apk1` (this firmware will not install a plain `.apk` from
   USB). Do not launch the factory packages (`com.xgimi.minitvfactory`, `com.xgimi.remote`) to look
   for a switch; the repository's rules forbid starting factory/test packages. The firmware reports
   `ro.adb.secure=0`: while adbd runs, any device on the LAN has a shell. Deep standby drops the
   TCP connection after about 70 seconds; run `adb connect` again.
2. **`set-home-activity` is not enough.** With `com.xgimi.home` still enabled,
   `cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity` prints `Success` but
   writes a preferred activity with `mAlways=false`. HOME then opens
   `com.xgimi.xgimiframeworkui/.resolver.ResolverActivity` (打开方式: 极米桌面 / 主屏幕). Choosing
   始终 in that dialog did not stick. The fix is the install script with
   `STOCK_LAUNCHER=com.xgimi.home` (see Launcher and Home below). `com.xgimi.xrmservice` can
   re-enable the stock launcher on its remote keys; `stock_home_redirect=com.xgimi.home` with
   `HOME_KEY_SERVICE=1` and `key_filter` true sends that screen back to the new launcher.
3. **Settings is a service window, not an Activity.** `android.settings.SETTINGS` resolves to
   nothing. The launcher's Settings pill needs a `settings_fallback_components` entry; that key is
   `config.xml` only, so a theme push cannot carry it.
4. **HDMI:** `input_action=com.xgimi.action.hdmiPlayer` opens
   `com.xgimi.xhplayer/.InputActivity`. `xhplayer` is hidden from Home in `config.json` but the
   Inputs menu (输入源) still offers it.
5. **LAUNCHER-only apps:** 极米投屏, File Manager, the XGIMI app store and Settings have a
   LAUNCHER entry but no LEANBACK entry; `library_apps` seeds them or the launcher does not list
   them.
6. **Voice and OTA** are commented out of the default list. The author's unit disables them by
   choice (uncomment to match).
7. **`cmd package compile -m speed -f com.gathin.tvhome`** fails on this firmware during
   `launcher/build-and-install.sh`. The install and Home handover still succeed; the failure is
   harmless.

## Apply

```sh
TV_HOST=<tv ip> tools/disable.sh examples/xgimi-z9x-china/debloat.txt
```

Windows:

```powershell
tools\disable.ps1 -TvHost <tv ip> examples\xgimi-z9x-china\debloat.txt
```

## Launcher and Home

Install the launcher after the list. The stock home is disabled by the install script, not by the list.
`SKIP_BUILD=1` can be added when the APK is already built.

```sh
TV_HOST=<tv ip> HOME_KEY_SERVICE=1 RESTORE_KEY_SERVICES=1 STOCK_LAUNCHER=com.xgimi.home launcher/build-and-install.sh
```

Then push this folder's config:

```sh
TV_HOST=<tv ip> tools/theme.sh examples/xgimi-z9x-china
```

`stock_home_redirect=com.xgimi.home` in `config.json` and `HOME_KEY_SERVICE=1` hold Home if
`com.xgimi.xrmservice` brings the stock launcher back. The stock home is
`com.xgimi.home/com.xgimi.module.cellview.home.ui.HomeActivity`.

Do not run the install script with `STOCK_LAUNCHER=none` and expect the HOME key to stick: that is
the chooser case above.

## ADB on this firmware

Developer options do not expose a working network-ADB switch on this build. ADB was opened by
installing [code-ant/z7x-adb](https://github.com/code-ant/z7x-adb) from USB (rename the file to
`.apk1`) and using that app to start `adbd` on port 5555. The unit then answers
`adb connect <projector ip>:5555` and `tools/tv.py`. Do not launch the factory packages
(`com.xgimi.minitvfactory`, `com.xgimi.remote`) to look for a switch. The firmware reports
`ro.adb.secure=0`: while adbd is running, any device on the LAN has a shell. Deep standby drops
the connection after about 70 seconds.

## Settings is a service window

`com.android.newsettings` has no `android.settings.SETTINGS` activity. Its Settings UI is a
floating service window. The package does ship a LAUNCHER activity,
`com.android.newsettings/com.xgimi.newsettings.mock.MockActivity`. Before building the launcher,
add that component to `settings_fallback_components` in
`launcher/app/src/main/res/values/config.xml` and a `<package android:name="com.android.newsettings" />`
line under `<queries>` in `launcher/app/src/main/AndroidManifest.xml`. Those keys are not read
from a theme. The app library still lists Settings when it is in `library_apps`.

## Inputs and the dock

- The top-bar 输入源 control opens the launcher's Inputs menu;
  `input_action=com.xgimi.action.hdmiPlayer`.
- `config.json` pins Bilibili (`com.xiaodianshi.tv.yst`), YouTube, the XGIMI app store and
  极米投屏; `fill_dock=false`.
- `library_apps` seeds 极米投屏 (`com.xgimi.wirelessscreen`), File Manager
  (`com.xgimi.filemanager`), the app store (`com.xgimi.newappmarket`) and Settings
  (`com.android.newsettings`). `com.xgimi.smartconnect` and
  `com.google.android.apps.mediashell` stay enabled for casting but have no LAUNCHER/LEANBACK
  entry of their own.
- `com.google.android.tvrecommendations` was left enabled.

## Voice and OTA

`com.xgimi.duertts`, `com.google.android.katniss` and `com.xgimi.upgrade` are commented out in
`debloat.txt` so the default list keeps voice and firmware updates. The author's unit disables
them by choice; uncomment the lines and re-run `tools/disable.sh` for the same. On this unit,
disabling `duertts` (the only accessibility service, system-uid, boot receiver) did not break
remote keys, keystone/focus or Settings, and HDMI input kept working with a live source:
`com.xgimi.xrmservice` is the key handler. The
voice button itself stops working, which is the point.

## Restore

```sh
TV_HOST=<tv ip> tools/restore.sh examples/xgimi-z9x-china/debloat.txt com.xgimi.home
```

Windows:

```powershell
tools\restore.ps1 -TvHost <tv ip> examples\xgimi-z9x-china\debloat.txt -Stock com.xgimi.home
```

The Undo line in `debloat.txt` passes the same `com.xgimi.home`. After restore, `stock_home_redirect`
and `HomeKeyService` may still send the stock home screen back to the new launcher; drop
`com.gathin.tvhome/.HomeKeyService` from `enabled_accessibility_services`, or uninstall the
launcher, if you want the stock home to stay in front.
