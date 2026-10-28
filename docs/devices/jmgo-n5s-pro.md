# JMGO N5s Pro: a China LunaOS projector, ADB without a prompt, and a launcher-only session

The JMGO N5s Pro (坚果 N5s Pro) is a China-market LED projector on JMGO's own LunaOS, not Google TV.
Android calls it `K990CN` on a MediaTek board. The closest relative in [`examples/`](../../examples/) is the
[N1S `holan`](../../examples/jmgo-n1s-holan/debloat.txt), but that unit runs Google TV 14 in Australia, so
its list does not carry over to this firmware. The reference launcher was installed and removed again in one
session (the owner preferred the stock desktop); no packages were disabled and there is no debloat list yet.

## Identify the firmware

`tools/tv.py --probe` on the unit:

| Field | Observed value |
|---|---|
| Retail model | JMGO N5s Pro (坚果 N5s Pro), China |
| `ro.product.model` / `ro.product.device` | `K990CN` / `K990CN` |
| `ro.product.manufacturer` | `MediaTek` |
| Android / SDK | 11 / 30, JMGO LunaOS |
| `ro.build.display.id` | `K990CN-userdebug 11 RP1A.200720.011 1.1.31.7 dev-keys` |
| `ro.adb.secure` | `0` |

The build string says `userdebug ... dev-keys`, which is unusual for a retail unit.

## Opening ADB on this firmware

The usual Android TV recipe does nothing here: there is no visible build-number item that unlocks a
Developer options screen after seven presses. What works is the older JMGO convention:

1. Settings → About (关于本机) → System version (系统版本).
2. Press OK on that row repeatedly, up to a dozen times, until a toast confirms ADB.
3. A "console debugging" (console 调试) toggle sits under the ADB toggle; it is not needed for network ADB.

Because `ro.adb.secure` is `0`, port 5555 opens with **no "Allow USB debugging?" prompt on the projector**:
any device on the LAN gets a shell as soon as the toggle is on. Turn ADB off when the work is done and
check the port with `tools/adb-closed.py <ip>`. The Python client in `tools/tv.py` connects with no
authorisation step on this unit.

## Package landscape (observed, nothing disabled)

The China preload set differs from the N1S list. Packages present on this build, all left enabled:

```text
# JMGO tracker and preloads
com.jmgo.spider                        # tracker, same package the N1S list disables
com.jmgo.appstore                      # JMGO app store
com.jmgo.os.gamecenter
com.jmgo.browser
com.jmgo.helpcenter
com.jmgo.music
com.jmgo.arwen                         # music splash activity, starts with no visible trigger
com.jmgo.miiot                         # Mi Home / IoT link
com.jmgo.mtc

# China video preloads
com.xiaodianshi.tv.yst                 # 云视听小电视, Bilibili TV
com.ktcp.video                         # 云视听极光, Tencent Video TV
com.bajintech.assistant

# Cast, retail demo, phone remote
com.jmgo.holacast.x                    # screen cast receiver (HolaCast)
fusion.android.tv.demo
com.softwinner.tvremoteserver          # phone-remote server, despite the MediaTek board
```

The stock launcher is `com.jmgo.launcher`, Home activity
`com.jmgo.launcher/com.jmgo.os.home.HomeActivity`. The projector has no tuner and no Live TV app.
The optics and remote are assumed to depend on the packages the N1S list keeps: `com.jmgo.hippo`,
`com.jmgo.autofocus`, `com.jmgo.tofautofocus`, `com.jmgo.autokeystone`, `com.jmgo.dewarping`,
`com.jmgo.ptz`, `com.jmgo.autobrightness`, `com.jmgo.autocolortemp`, `com.jmgo.aieyeprotector`,
`com.jmgo.a2dpsinkservice`, `com.jmgo.remotecontrolota`, `com.jmgo.accessory` — none were touched here.

## Launcher session: install, Home handover, full revert

The reference launcher from this repo was built (AGP 8.5.2 / Gradle 8.9 / JDK 17, `assembleRelease`,
470 KB) and installed over ADB with `tools/b64push.py` plus `pm install -r`. On Android 11 everything in
`launcher/build-and-install.ps1` worked as written, with `-StockLauncher com.jmgo.launcher`:

1. `cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity` succeeded.
2. The launcher started cold in 652 ms (`am start -W`).
3. `pm disable-user --user 0 com.jmgo.launcher`, then `set-home-activity` again, then
   `cmd package resolve-activity` confirmed Home resolved to `com.gathin.tvhome`.
4. `dumpsys activity activities` showed `mFocusedApp` as the new launcher.

The revert path was verified in the same session and is what another owner needs:

```sh
pm enable --user 0 com.jmgo.launcher
cmd package set-home-activity --user 0 com.jmgo.launcher/com.jmgo.os.home.HomeActivity
pm uninstall com.gathin.tvhome
```

followed by `input keyevent KEYCODE_HOME` and the `mFocusedApp` check. The owner stayed on the stock
LunaOS desktop by preference; this is a procedure note, not a recommendation to switch.

On the closely related [JMGO G9S](jmgo-g9s.md), `com.jmgo.tv` (the HDMI input app) also answers HOME, and
that owner deliberately left `com.jmgo.launcher` enabled. This N5s Pro session handed Home over cleanly
while the projector was running, but did not reboot with the stock launcher disabled, so the same caveat
is untested here.

## Limits

- One unit, one session, no reboot test while the replacement launcher was Home.
- No packages were disabled, so nothing here is a debloat list; the package groups above are observations,
  not recommendations.
- Firmware-update behaviour is unknown; a future update may change the ADB trigger or the preloads.
- Promote this to `examples/jmgo-n5s-pro/` once someone has applied a list, lived with it for a few days,
  and can say what a reboot and a firmware update bring back.
