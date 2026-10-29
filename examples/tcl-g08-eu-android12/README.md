# TCL Smart TV Pro (G08, Android TV 12), European firmware

TCL `G08` set, model "Smart TV Pro", product `G08_4K_GB`, firmware `STT2.221228.001 AS34`, Android 12.
The same G08 board family as the C755, QM6K, 85RM7L and US lists, on the older EU Android 12 build — the
US list matches only partially: this build has no Samba-ACR ttvs payload, no `AirplayAPK`, no
hotelmenu/channelplus/e-sticker/TV+/usercenter preloads, and no Android 14 ad services.

| File | What it is |
|---|---|
| `debloat.txt` | The 24 packages disabled on this set, grouped and commented |

**How much testing is behind it:** one set, 21 October 2026. All 24 packages are disabled on the live set
(`pm list packages -d` verified) and the TV boots, plays Live TV/HDMI, and the remote's Source key opens
the QuickPanel (verified via log injection `input keyevent 178` → `QuickPanelDialog`). Applied by hand
with `pm disable-user --user 0`, not via `tools/disable.sh`.

**What bit us:** an earlier pass using the US G08 list disabled `com.tcl.suspension`, `com.tcl.interactive`
and `com.tcl.ttvs`, which killed the remote's Source key (the QuickPanel lives in `com.tcl.suspension`,
started by `TclShortCutKeyManager` as `com.tcl.suspension/.DismissService`) and made
`vendor.tcl.tvinput.extend-service` spam null-binder + `com.tcl.tbrowser` NPEs at ~10 Hz. All three were
re-enabled and are listed in `debloat.txt` as commented DO-NOT-DISABLE lines with the failure signature.

**Not done here:** no custom launcher installed; stock `launcherx` stays enabled. The setup wizard
(`setupwraith`) is kept enabled here, and to be disabled only before installing a custom launcher.

Apply: `TV_HOST=<tv ip> tools/disable.sh examples/tcl-g08-eu-android12/debloat.txt`
Undo: `TV_HOST=<tv ip> tools/restore.sh examples/tcl-g08-eu-android12/debloat.txt`
