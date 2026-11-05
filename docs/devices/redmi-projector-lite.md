# Redmi Projector Lite (Hi3751V350, CVTE ROM), Android 9

From @XucroYuri's report in #1178. One session, no package list. The main finding is that network ADB stays open
and unauthenticated, which matters before anyone starts the procedure on this projector.

| Field | Observed value |
|---|---|
| Retail model | Redmi Projector Lite (Redmi 投影仪 Lite 版) |
| Model / SoC | `Projector_10` / HiSilicon `Hi3751V350`, `ro.product.board=bigfish` |
| Fingerprint | `HiDPT/Hi3751V553_32_DMO_2layer_1024M` |
| ROM | CVTE-built, `ro.product.rom.rommodelnumber=CVTE-PJ-DANGBEIOS-001` |
| Android / API | Android 9 / 28 |
| Settings app | `com.asu.settings` (no Developer options) |
| Stock Home | `com.mitv.tvhome.pub` |

## Network ADB cannot be closed from the projector

```
[ro.adb.secure]: [0]
[init.svc.adbd]: [running]
[persist.adb.tcp.port]: [5555]
[ro.CVT_DEF_ADB_DEFAULT_VALUE]: [0]
```

`ro.adb.secure=0`: any device on the network gets a shell on port 5555 with no authorisation prompt. The closing
step in [PROMPT.md](../../PROMPT.md) cannot pass here, and `tools/adb-closed.py <tv ip>` keeps reporting the port open:

| Tried | Result |
|---|---|
| Settings > 账号与安全 (Account & Security) > ADB toggle off | Port 5555 stays open, and the toggle is back on after a reboot |
| Developer options | Not present: `com.android.settings/.Settings$DevelopmentSettingsDashboardActivity` does not exist and `android.settings.APPLICATION_DEVELOPMENT_SETTINGS` does not resolve |
| `setprop persist.adb.tcp.port -1` | Reads back `-1`, reset to `5555` at the next boot |
| `stop adbd` | `stop: must be root` |
| `ro.CVT_DEF_ADB_DEFAULT_VALUE` (or a persist twin) | No effect: read at boot, before the shell can set it |

Until there is a way to switch it off, keep the projector on a network of its own (a guest network or VLAN that
other devices cannot reach), and say so plainly at the end of a session instead of retrying the check.

### A CVTE factory-menu switch, from a set with the same SoC (untested here)

From @fgarciag97's comment in #1178: on a Sankey SMART_TV, CVTE firmware on the same `Hi3751V350` / `bigfish`
(Android 12, `Hi3751V352E_DMO`), network ADB is switched off from the remote, as
[`examples/sankey-smart-tv-hi3751v350/README.md`](../../examples/sankey-smart-tv-hi3751v350/README.md) describes:

1. Switch to a source first (TV or HDMI); the menu does not open from the launcher.
2. Press **MENU**, then **1 1 4 7** quickly.
3. **DEBUG > ADB ENABLE > OFF**, then leave with Back and touch nothing else.

`tools/adb-closed.py` then reported "closed: the TV refused the connection", twice; the closed port was not
re-checked after a reboot. Whether Dangbei's projector ROM keeps that menu is not known. Only the ADB row: in the
same menu SHIPPING INIT and BOARD INIT reset the set. This is a remote key sequence; nothing is started over ADB.

## Home

- `cmd package set-home-activity` printed `Success`, but Home stayed on `com.mitv.tvhome.pub`, and `system_server`
  then force-stopped the new launcher:

  ```
  D kill    : 当前进程是用户进程:com.gathin.tvhome
  I ActivityManager: Force stopping com.gathin.tvhome appid=10046 user=0: from pid 1986
  ```

- The `com.ts.launcher` application-ID build that works on China Xiaomi TVs
  ([`xiaomi-mitv-aetp0-dofus.md`](xiaomi-mitv-aetp0-dofus.md)) did not help: the guard here is CVTE's, not Xiaomi's.
- `ro.desktop.appwhitelist=com.cvte.tv.media,com.hisilicon.miracast,com.cvte.tv.pictorial` did not filter the HOME
  candidates: a third-party launcher was listed by `cmd package query-activities` for `category.HOME` normally.
- The reporter found that Home landed on the replacement only after `com.mitv.tvhome.pub` was disabled, and that
  this survived a reboot. This is a CVTE board, so [AGENTS.md](../../AGENTS.md) keeps the stock launcher enabled
  unless an `examples/` folder for this exact board disables it, and asks for one reboot with Home and ADB
  confirmed before anything else (#741). There is no such folder yet, so treat this as one owner's result.
- On the Sankey set the Home key and boot send `MAIN` + `HOME` + `COMMON_HOME`
  (`ro.CVT_EN_COMMON_HOME_INTENT=1`), which the launcher has no filter for (#1201). Check
  `getprop | grep CVT_EN` here before drawing conclusions about Home.

ToF ranging, autofocus and keystone correction still worked after the session.
