# SDMC SMART4KTVPLUS (WhaleTV, Madagascar)

Android 13 (SDK 33), build `SDMC/WhaleTV/at30ak:13/TQ1A.230205.002/20260610111643:Mp.user/release-keys`, firmware
`WHALEOS-C8W-S2C.A1744.4K.00001136` (`persist.sys.product.version`), board `t5w`. About 1.5 GB of RAM
(`MemTotal` 1514672 kB), 4.5 GB `/data`. The panel is 3840x2160 with a stock override to 1920x1080 at 320 dpi
(`wm size` / `wm density`), which is how it ships, not damage. `ro.product.brand` and `ro.product.manufacturer` are
both `SDMC`; the TV calls itself `whaleTV`.

This is a one-session report, not a several-day list. 24 packages were disabled and stayed disabled over four
reboots; Home, YouTube, JioSphere and the Live TV app opened afterwards. **The launcher half is impossible on this
firmware** (below).

## Connecting

Settings > Device Preferences > About > Build seven times, then Developer options > USB debugging. ADB answers on
5555, `ro.adb.secure=1`, so the TV asks once to trust the computer. Close it afterwards the usual way and check with
`tools/adb-closed.py <tv ip>`; on this unit it answered "connection refused" after debugging was switched off and the
authorisations revoked.

## The launcher cannot be installed

Every route ends at the firmware, for any APK not signed by Zeasn, with or without `category.HOME`:

- `pm install` (and so `build-and-install.sh`) answers `Error: user version can't use 'adb install' to install
  package`. Setting `verifier_verify_adb_installs` and `package_verifier_enable` to 0 and disabling
  `com.droidlogic.appinstall` (the workaround reported for another WhaleOS Android 13 device) changes nothing here.
- Android's installer started from the shell (`am start ... com.android.packageinstaller/.InstallStart` with a
  MediaStore `content://` URI) closes at once: `InstallStart: Requesting uid 2000 needs to declare permission
  android.permission.REQUEST_INSTALL_PACKAGES`. The Fichiers app (`com.droidlogic.FileBrower`) answers "Non pris en
  charge" for an `.apk`.
- WhaleTV's own installer accepts the file
  (`am start -a com.zeasn.whale.os.app.action.INSTALL_APK --es com.zeasn.whale.os.app.extra.APK_PATH <path>`) and
  opens a session, but PackageManager rejects it at parse time:
  `INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES: ... base.apk has mismatched zeasn certificates`. A build of the
  reference launcher with both `category.HOME` filters removed gets the same answer, so this is a signing gate on
  every package, not a launcher-only rule (compare `examples/tcl-ak30a5`, where only HOME is gated).

`persist.sys.zeasnsignature.enable` reads `false` and the gate applies anyway. The other WhaleOS Android 13 sets here
(`examples/hotack-smart2kpjt`, firmware dated 2026-04-27, and `examples/sankey-smart2ktvse`, same stock launcher
`com.whaletv.launcher`) took the launcher, so the gate looks like it came with a 2026 firmware. On this firmware keep
the stock Home and apply the debloat half only.

## Home and the app store

- Stock launcher: `com.whaletv.launcher`. Android's `FallbackHome` in `com.android.tv.settings` is the only other HOME
  claimant. There is no Google Play: the only app store and the only on-screen installer live inside
  `com.whaletv.launcher` (`com.whaletv.action.PAGE_APPS` opens its apps page), so it has to stay enabled even on a set
  where a replacement Home would install.
- Live TV app: `com.zeasn.livetv` (keep it; with no channels it opens to its channel-setup dialog).
- At every boot `com.whaletv.launcher/.module.home.systemid.SystemIdActivity` shows "Netflix n'est pas disponible car
  l'identifiant système de l'appareil ne correspond pas". It also appears with `com.netflix.ninja` and
  `com.netflix.tokenmanager` enabled and with the device portal and track service enabled, so it is not caused by
  the list; it is a Netflix certification problem of this unit.
- The big Netflix, Prime Video and Disney+ tiles in WhaleTV's bottom row come from its server and stay after those
  apps are disabled; they open nothing.

## Packages disabled (one session)

```text
# Zeasn / WhaleTV ads and tracking
com.zeasn.whaletrackservice                    # usage tracking, starts at boot (21 MB RSS)
com.zeasn.deviceportal.asdprovider.certified   # device portal: ad screensaver receivers, starts at boot (16 MB)
com.whaletv.logger                             # USB-disk log service
com.zeasn.hotelmodelservice                    # hotel mode
# Streaming apps the owner does not use
com.netflix.ninja
com.netflix.tokenmanager
com.amazon.amazonvideo.livingroom
com.WhaleTV.whaleos.youtube.tv                 # WhaleTV's YouTube wrapper; com.google.cobalt_youtube stays
com.disney.disneyplus
com.rlaxxtv.tvapp.atv                          # Whale TV+ free channels
com.qello.qelloGTV
org.videolan.vlc
# Phone-style and unused system apps
com.amlogic.dab                                # DAB radio
com.svox.pico                                  # text-to-speech
com.android.dreams.basic
com.android.deskclock
com.android.gallery3d
com.android.music
com.android.musicfx
com.android.camera2
com.android.printspooler
com.droidlogic.imageplayer
com.google.android.webview.beta                # not the WebView provider: `dumpsys webviewupdate` names com.android.webview
org.chromium.webview_shell
```

`MemAvailable` went from about 370 MB before to about 520 MB after a reboot with the list applied.

## Kept on purpose

- `com.whaletv.launcher` (Home, app store, installer), `com.zeasn.livetv`, `com.droidlogic.tvinput`,
  `com.droidlogic.dtvkit.inputsource`, `android.cultraview.tv.service`, `com.droidlogic.inputmethod.remote` (the only
  keyboard), `com.android.bluetooth`, `com.android.tv.settings`, `com.droidlogic.tv.settings`,
  `com.zeasn.whaleos.settings`, `com.whaletv.whalesystemui` (PIN lock and language selector).
- Role not confirmed, left on: `com.zhixin.smarthw` (a `micro.auth` module), `com.zeasn.whaleservice`,
  `com.whaletv.service.pidcontrol`, `com.zeasn.tokenapp` (WhaleTV account, likely needed by the store).
- Firmware updates (`com.droidlogic.updater`), Miracast, DIAL (`com.aml.dial`) and WhaleTV casting
  (`com.zeasn.tv.cast`) were kept by the owner's choice.
- Factory packages `com.cultraview.ctvfactorymenu` and `com.ctv.autotest.helper` were neither opened nor disabled.

## Notes

- A `pm enable` followed at once by `reboot` was lost on this set: PackageManager writes its state a few seconds
  later. Wait about 20 seconds (or run `sync`) before rebooting when testing whether a package comes back at boot.
- `screencap` works on Home and in apps; the Live TV app with no signal also captures.
