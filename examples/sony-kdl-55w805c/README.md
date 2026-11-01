# Sony KDL-55W805C (BRAVIA 2015, EU firmware 5.433)

One unit, build `SVPDTV15_EU-user 7.0 NRD91N.S34 5.433`, set up with a coding agent from 25 October to
1 November 2026. The list is in [`debloat.txt`](debloat.txt); these are the things around it.

## Home on Android 7

The published launcher needs Android 8 (minSdk 26). This set runs the current launcher rebuilt with minSdk 24
and the four changes in [`docs/devices/mtek-mk32cs1nb.md`](../../docs/devices/mtek-mk32cs1nb.md): core library
desugaring, and guards around `getChangedPackages`, `Resources.getFont`, `Files.readAllLines` and
`Bitmap.Config.HARDWARE`. `testReleaseUnitTest`, `lintRelease` (no NewApi errors) and `assembleRelease` pass;
the APK is about 715 KB, over the size gate, so it stays a local build (the
[`cleanyourtv-legacy`](../../SHOWCASE.md) fork does the same for this platform).

- Home, the Home key, the D-pad from every edge, Inputs to HDMI 1 and themes work.
- D-pad along the dock: median frame 5 ms and about 15 % janky frames, the same on Aurora, Minimal and the
  default look; the launcher takes about 33 MB.
- A module card installed while Home was running asked "Show card" only after the launcher restarted
  (`am force-stop com.gathin.tvhome`, then Home).

## Album and the Inputs row

`com.sony.dtv.osat.album` answers `ACTION_VIEW` with the type `vnd.android.cursor.item/channel` and no data,
so `tools/disable.sh` keeps it as the TV's input handler. With the stock home (or a third-party home that opens
inputs through it) keep it. The repo launcher's Inputs popup sends the passthrough URI with that type, and on
this set it resolves to `com.sony.dtv.tvx/.tvplayer.main.TvxLauncherActivity`: with Album disabled
(`HANDLERS_ALLOW=1`), HDMI 1 still opened from the popup. It is in the list's last commented block.

## HbbTV off over ADB

Settings → Channel setup → Digital setup is a native MediaTek menu (`NativeMTKActivity`) that neither
`uiautomator` nor `screencap` can read, next to the channel scan. The same switch has an ordinary Android screen:

```bash
TV_HOST=<tv ip> tools/tv.py "am start -a com.sony.dtv.intent.action.HBBTV_CONFIRMATION"
```

It asks "Enable interactive broadcast application". No calls `setHbbTvDisagree`, which sets the MtkTvConfig
value `g_menu__hbbtv` to 0 (logcat shows it). The native HbbTV browser
(`/3rd/internet_browser/browser -x hbbtv`, about 13 MB) stopped and did not come back after a reboot. Yes on
the same screen turns HbbTV on again.

## Sony's content recognition

`com.sony.dtv.interactivetvplatform` runs `SambaTvRCR`, `NativeVideoShm` (video frames over shared memory) and
discovery plugins for Kantar, Kuyun and ShopTV. `com.sony.dtv.interactivetvutil` runs log collectors (broadcast,
application, action, internal events). The platform is in the list's last commented block; `interactivetvutil` stays, as
other Sony apps read its token provider (see `examples/sony-bravia-4k-vh21-eu`).

