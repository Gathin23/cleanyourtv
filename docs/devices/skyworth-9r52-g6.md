# Skyworth 55G6 / 9R52_G6 (China domestic, Android 5.1.1)

Report for a mainland-China Coocaa/Skyworth set (`com.tianci.*` / `com.coocaa.*`) on a Realtek RTD299x board. All commands were run on the unit itself; no device identifiers, network addresses or screenshots are included.

> **Warning:** hiding `com.tianci.movieplatform` hangs the boot at the Skyworth logo — the boot flow waits for the stock home. Recovery is `pm unhide` + reboot. Read the traps before debloating.

## Device

| Field | Value |
| :--- | :--- |
| Retail model | Skyworth 55G6 (China domestic) |
| Product / device | `9R52_G6` / `rtd299o_tv001` |
| SoC / board | Realtek RTD299x, `armeabi-v7a` only (no arm64) |
| Kernel | Linux 3.10.19-mag2+, built 2018-05-18 |
| Android / SDK | 5.1.1 / 22 |
| Build | `LMY47V`, type `user`, `test-keys`, `ro.debuggable=0` (adb root refused) |
| SELinux | Disabled |
| RAM | 1.27 GB (`MemTotal` 1331356 kB) + 128 MB swap |
| Firmware | Coocaa 6.20.80515-805180 (no GMS) |
| Packages | 77 total, 56 visible after debloat |
| System config | `/system/pcfg/9R52_G6/config/general_config.xml` (root:root 644) |

Sibling pcfg directories on the same image: `9R51_F6/G6`, `9R52_V9E`, `9R53_E810S/F6/G6`, `9R58_G6`, `9R59_G7/G910M/V9/V9S`, `9R67_F6`, `9R68_F6` — 9R5x appears to be a whole product generation, but only 9R52_G6 was tested here.

## Result

21 packages hidden with `pm hide`, surviving reboot. Free RAM went from 129 MB to 300+ MB (443 MB peak right after cleanup). ADB stays usable, and a third-party launcher (Quiet TV) takes over HOME and skips the 15 s boot ad.

Debloat list and rollback commands: see the "Debloat" section below.

## Traps

### Trap 1: `pm disable-user` is blocked; `pm hide` is the only reliable lever

- `pm disable-user --user 0 <pkg>` fails with `SecurityException: attempt to change component state ... from uid=2000`.
- `pm uninstall -k --user 0 <pkg>` returns `Success`, but the root daemon `coocaa_guard` (`/system/vendor/bin/coocaa_guard`) reinstalls it; the package reappears (sometimes with a duplicate user-state entry).
- `pm hide <pkg>` works and persists across reboot; rollback is `pm unhide <pkg>`.
- `pm enable/disable <component>` is refused the same way `disable-user` is, so individual components cannot be toggled (e.g. you cannot enable the `VendorBootActivity` shipped disabled by an app).

### Trap 2: HOME is owned by `com.tianci.system`, not by intent resolution

- The Home key and boot flow use `persist.service.homepage.pkg` / `.cls`, not `android.intent.category.HOME`.
- Those props accept a new value at runtime (`setprop` succeeds) but are rewritten on every boot from the read-only `DEFAULT_HOMEPAGE` in the pcfg file (`com.tianci.movieplatform/com.coocaa.homepage.vast.HomePageActivity`). Changing this needs root.
- **What breaks boot:** `pm hide com.tianci.movieplatform` → the unit stays on the Skyworth logo behind a `KeyguardScrim`, indefinitely. Recovery: `pm unhide com.tianci.movieplatform` (still reachable over ADB, adbd starts before the home) then reboot.
- What does *not* work: pointing `persist.service.homepage.*` at a third-party launcher. It survives only until the next reboot.
- Third-party HOME takeover on this firmware therefore has to go through an accessibility-based relay (see below), not through the standard HOME claim.

### Trap 3: adbd serves a single TCP transport; the launcher can take it

- On this Android 5.1 build, adbd accepts one TCP transport. A second host sees the port open but gets no reply — `adb devices` reports `offline`, not `unauthorized`.
- Quiet TV's application-management page opens its own local ADB session on load and retries every 30 s; once the app becomes HOME at boot, the PC-side connection is permanently starved. Uninstalling the app restores it immediately. Tracked upstream as `BeatsAD/quiet-tv#113`.
- Practical implication: any on-device tool that uses local ADB can lock the PC out on this generation. Worth checking before blaming the firmware.

### Trap 4: boot ad is firmware-level

- `BOOT_AD=true` in the pcfg file; the 15 s video/ad sequence runs before the home is shown.
- No root → cannot flip it. What did work: an accessibility relay that launches the replacement home during the "正在为您准备主页" stage, which visually skips the ad.
- Not tested on this unit: `persist.sys.bootvideo.time` / `persist.sys.ad.startboot` (reported for 8R40_K5C in `skyworth-8r40-k5c.md`). Worth trying if you want the ad gone at the firmware level.

### Trap 5: ADB recovery paths that do not exist here

- `adb backup` never shows its confirmation dialog on this firmware and produces a 0-byte archive; the "modify app data via backup/restore" route is closed.
- `bu backup` returns exit 0 but writes no file — it is effectively a stub.
- The ADB switch lives in the factory menu: Settings → 本机信息 → press up up down down left right left right → 通用设置 → ADB 开关 (confirmed on this unit; it was already enabled). Do not touch anything else in that menu — panel and gang parameters live there.

## Debloat

Hidden with `pm hide` (21, verified across reboot):

```
com.coocaa.ad.onekeybuy          com.coocaa.ad.webview
com.tianci.push                  com.coocaa.guide
com.tianci.logcatcher            com.tianci.fix_active_id
com.coocaa.mall                  com.coocaa.educate
com.coocaa.educate.theme.child   com.coocaa.theme.child
com.coocaa.game.fox              com.coocaa.tvpitv
com.coocaa.teampresent           com.dbappstore.lite
com.skyworth.lafite.srtnj.speechserver
com.skyworth.smarthome_tv        swaiotos.channel.iot
com.skyworth.tv_browser          com.bajintech.karaok
com.baidu.duer.tv.video          com.coocaa.messageprovider
```

Already disabled before we started: `com.kuyun.smarttv.coocaatv`, `com.coocaa.activecenter`, `com.android.system.network.server.helper`.

Kept on purpose: `com.tianci.system`, `com.tianci.movieplatform`, `com.tianci.setting`, `com.tianci.tv`, `com.skyworth.dtvLoader`, `com.skyworth.iSwitchDtvService`, `com.tianci.taskmanager`, `com.sohu.inputmethod.sogou.tv`, `com.skyworth.polymerize.player`, `com.skyworth.skywebviewapp.webview`, `com.coocaa.factory`, `com.skyworth.factory`.

```bash
# back up first
adb shell "pm list packages" > packages_before.txt
adb shell "pm list packages -u -f" > packages_paths.txt
adb shell "cat /system/pcfg/9R52_G6/config/general_config.xml" > general_config.xml

# hide in batches of 3-5, then reboot and check
for p in com.coocaa.ad.onekeybuy com.coocaa.ad.webview com.tianci.push; do
  adb shell "pm hide $p"
done

# rollback
adb shell "pm unhide com.coocaa.ad.onekeybuy"
```

## Launcher takeover

`coocaa-home-shim` does **not** apply: it relies on the Android 8 multimodel framework (`com.coocaa.os.multimodel.MODEL_REGISTER`) and is inert on 5.1.1.

What worked: Quiet TV's accessibility guard (`HomeGuardService`) plus `WRITE_SECURE_SETTINGS` and `GET_USAGE_STATS`:

- boot lands on the replacement home during the "preparing home" stage, skipping the boot ad;
- the Home key flashes the stock home for 1–2 s before being redirected;
- on this adbd, the guard's own ADB usage must be disabled or the PC is locked out (see Trap 3).

## Verified

Cold boot (power removed for 10 s, then reconnected) three times on 2026-10-31: all three landed on the replacement home with `boot_completed=1` and no stock home in the foreground. adbd answers about 90-100 s after power returns, so this checks the landing, not the intermediate frames. The boot ad still appeared once in three cold boots - a few seconds at the logo, before the relay runs - so the relay hides the stock home but does not remove the firmware ad.

The power key on this set does not suspend it: the screen restarts from the logo and `/proc/uptime` resets, so a standby-wake test is not applicable here. Three power-key cycles were observed on screen and all landed on the replacement home; ADB confirmed the reboot and the landing for the first of them. The first Home press after each restart flashed the stock home for 1-2 s before the relay returned; later presses were direct.

## Untested

- Other 9R5x chassis (package set is likely the same; `coocaa_guard` behaviour may differ)
- Android 4.4 variants of the same generation
- `persist.sys.bootvideo.time` on 9R52
