# Vidda C1S and C1 (MT9669, Android 9, China JuUI)

| File | What it is |
|---|---|
| `debloat.txt` | The list, tested on a C1S and on a C1 with the same build; its header has both units |
| `config.json` | Inputs panel, app library, Home at power-on and hidden AOSP leftovers, for the C1 launcher setup below |

## C1: the reference launcher as Home, with 聚好看 left enabled

Measured on a Vidda C1 4K tri-colour laser projector on 2026-11-01, build `PPR2.180905.006.A1`
(02.02.60.02), after the list in this folder. The C1S header says Home stayed with 聚好看 on that unit
whatever `set-home-activity` reported; on this C1, with the same build, it did not.

**Do not disable `com.jamdeo.tv.vod`.** Nothing below needs it off. On a Hisense Android 9 set with the
same JuUI launcher, disabling it left boot hanging with no way back without root
(`docs/devices/hisense-70v1f-s-nova72691-android9.md`).

Install the launcher with the stock one left alone and the key helper on:

```bash
TV_HOST=<projector ip> STOCK_LAUNCHER=none HOME_KEY_SERVICE=1 launcher/build-and-install.sh
```

What happened:

- `cmd package set-home-activity` printed `Success`, and `cmd package resolve-activity` for HOME then
  answered `com.gathin.tvhome/.MainActivity`. The only HOME candidate before the install was
  `com.jamdeo.tv.vod/com.ju.main.launcher.LauncherActivity`.
- The physical Home key on the Bluetooth remote, pressed from inside another app, opened the launcher:
  logcat shows `START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME] ...
  cmp=com.gathin.tvhome/.MainActivity} from uid 1000`. Injected `input keyevent HOME` was not used as proof.
- Boot still lands on 聚好看: the firmware starts it by component name,
  `START u0 {... cat=[android.intent.category.HOME] ... cmp=com.jamdeo.tv.vod/com.ju.main.launcher.LauncherActivity} from uid 0`,
  whatever Home resolves to. With `power_on_home_always` and `HOME_KEY_SERVICE=1`, the helper sends Home
  about a second later: after a reboot, logcat shows that second HOME start and `dumpsys activity`
  has the launcher in front. A key pressed before that wins.
- With the helper bound, holding volume up still ramps, and Home, Back and the Input key work on the real
  remote (owner-checked).
- `dumpsys tv_input` lists no inputs, so the Inputs pill uses the vendor panel (`source_panel_service`):
  it opens 信号源 with HDMI 1/eARC, HDMI 2 and 投屏, and switches from there.
- 聚好看's own 切换桌面 offers only its 标准, 少儿 and 长辈 desktops, not a third-party launcher.

`config.json` holds the keys this unit used. They were built into
`launcher/app/src/main/res/values/config.xml` on the C1; pushing the same file with
`tools/theme.sh examples/vidda-c1s-mt9669` was not tried. Dock apps are left out: pick your own.

To give Home back to 聚好看: hold OK on Settings in the launcher, then Launcher settings, then Choose
default home. Switch the CleanYourTV accessibility service off in the TV's accessibility settings to stop
the power-on redirect.

## Closing ADB

Check with `tools/adb-closed.py` after switching debugging off. On the C1, port 5555 was still open
after the owner's pass through the menu (`settings get global adb_enabled` still answered 1).
`settings put global adb_enabled 0` over ADB closed it, and it stayed closed after a reboot.
