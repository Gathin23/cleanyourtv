# Skyworth / Coocaa 8R40_K5C (China domestic, Android 8.0)

Report for the mainland-China Coocaa/Skyworth firmware (`com.tianci.*` / `com.coocaa.*`) on a Realtek RTD285x set. Source: issue #692. Related older-sibling-in-family report: #417 (8R145_P3DM / K3 Pro, Android 11), whose notes do **not** transfer directly.

> **Warning:** Disabling the wrong package crashes the "About this TV" page, which is the only place ADB can be turned on. Recovery is a factory reset. Read the traps before running any debloat list.

## Device

| Field | Value |
| :--- | :--- |
| Retail model | Skyworth / Coocaa K5C (China domestic) |
| Product / device | `8R40_K5C` |
| SoC / board | Realtek `rtd285x` |
| Kernel | Linux 4.4.3, armv7l (32-bit userspace, armeabi-v7a only) |
| Android / SDK | 8.0.0 / 26 |
| Build | `OPR6.170623.012 dev-keys`, type `user`, `ro.debuggable=0` (adb root refused) |
| Security patch | 2017-08-05 |
| SELinux | Permissive |
| RAM | 1 GB |
| Packages | 56 total |

## Result

16 packages disabled, RAM freed, boot ad removed. Verified across a reboot with ADB reconnecting on its own and the stock home screen intact. List: `examples/skyworth-8r40-k5c/debloat.txt`.

Everything below is done over ADB as `shell`; nothing here writes `/system`.

## Traps

### Trap 1: debloating can destroy the only ADB entry

- There is no `com.android.settings` and no Developer options page. `com.tianci.setting` has no develop/debug activities.
- The ADB switch is in `com.coocaa.factory`, reached via Settings → 基本设置 → 设备信息 → 更多信息 → up up down down left right left right.
- That page depends on `com.tianci.de` (process `com.tianci.de:TianciDaemons`) and `com.skyworthdigital.stb.dataprovider`. Disabling either crashes About instantly. Wi-Fi ADB and port 5555 are then unreachable and a factory reset is the only recovery (the reporter needed two).
- On the second attempt `com.coocaa.sky.ccapi` and/or `com.tianci.user` caused the same crash. Treat `ccapi` as must-keep. `com.tianci.user` was in the final verified-safe list, so the evidence is inconclusive; disable it last and re-check About.
- **Escape hatch (no root):** `com.tianci.setting/.factorysetting.FactorySetting` declares the unguarded public action `coocaa.intent.action.TIANCI_FACTORY`, so any app can open the factory menu. Install Activity Launcher (`de.szalkowski.activitylauncher.oss`, has a leanback entry so it appears in the TV launcher) **before** debloating and test it.
- **Recommended workflow:** apply in small batches, reopen About after each batch.
- Follow-up (2026-10-22): with `com.tianci.de` disabled on its own, `AboutTvSetting`, `EquipInfoActivity` and
  `FactorySetting` all render normally — that unit ran this way for days. The crash needs `de` **plus**
  `dataprovider` or `ccapi` in the same batch. Disabling `de` alone saves ~70 MB (two processes), but keep the
  pairing rule above and re-check About after the batch.

### Trap 2: HOME is hardcoded — never disable the stock launcher

- `cmd package resolve-activity -c android.intent.category.HOME` returns "No activity found" on the stock firmware. No package, the stock launcher included, declares HOME.
- `com.tianci.system` (SkySystemService) starts the home page from `persist.service.homepage.pkg` / `.cls`, and the remote's Home key goes through `HotKeyHandler` to the same pair.
- The props accept a third-party launcher at runtime but are rewritten at boot from read-only `/system/pcfg/8R40_K5C/config/general_config.xml` (`DEFAULT_HOMEPAGE` = `com.tianci.movieplatform/com.coocaa.homepage.vast.HomePageActivity`), so a runtime change does not survive a reboot. Which home actually launches is decided by the rule table `/system/etc/bootintent.xml`, which the firmware's sky_launcher walks in order and whose first matching `<home>` wins. Both files are on `/system`: changing them needs root, which is out of scope for this repo.
- What breaks boot:
  - `cmd package set-home-activity ...` is refused: `cannot be home on user 0` (it worked on the Android 11 unit in #417).
  - `pm disable-user com.tianci.movieplatform` gives a black screen; `sys.boot_completed` never set.
  - `pm disable-user app.orchard.tv` (Aerial launcher on some units) gives a black screen too. Never disable the current launcher.
  - `setprop persist.service.homepage.switch 0` plus pointing pkg/cls at a third-party launcher: after reboot `adbd` did not start and About crashed. Leave that prop unset.
- Practical answer without root: keep the stock launcher installed and enabled, and open other launchers from My Apps.

### Trap 3: boot video ad and autostart queue

- Ad: 15 s video at `/data/local/bootvideo.ts` (system-owned), played by `com.skyworth.tv`, ad SDK in `com.coocaa.adsdk`.
- Fix (survives reboot, no root):
  ```
  tools/tv.py 'setprop persist.sys.bootvideo.time 1'
  ```
- `bootQueue.xml` in the same pcfg folder starts `com.tianci.push` at 1.2 s and `com.tianci.ad` at 5.0 s. Also `persist.sys.ad.startboot = com.tianci.ad/ui.BootViewActivity`.
- Other pcfg switches that exist but need write access to `/system`: `CURRENT_AD_SERVER` (`hoisin.coocaatv.com`), `HIDE_LAUNCHER`.

### Trap 4: input quirks that break automation

- TVBox-based players (e.g. 影视仓 `com.tencent.qqlivehuawei`) swallow injected `KEYCODE_HOME` / `KEYCODE_BACK`. The physical remote Home still works.
- The stock home ignores `input tap` and only responds to `KEYCODE_DPAD_*`. Screenshot-driven clicking fails: uiautomator bounds are 1280x720 but screencap is scaled.
- Injected `KEYCODE_HOME` is also ignored by `com.tianci.movieplatform` and by Aerial (`app.orchard.tv`) on this
  firmware, with or without an accessibility helper. `am start -a android.intent.action.MAIN -c
  android.intent.category.HOME` still resolves and brings the launcher forward, so scripts should send the
  intent, not the key.

### Trap 5: no clipboard service — a text field can kill the app it is in

`service list | grep clipboard` is empty and `dumpsys clipboard` reports `Can't find service: clipboard`, so
`getSystemService(CLIPBOARD_SERVICE)` returns null on this firmware.

Android 8's `TextView.canPaste()` does not null-check it (AOSP `TextView.java:11104`). While an accessibility
service is active, a focus change walks
`View.onFocusChanged` → `sendAccessibilityEventInternal` → `TextView.onInitializeAccessibilityNodeInfoInternal`
→ `canPaste()` → `NullPointerException`, and the process dies.

Seen on 影视仓 (`com.tencent.qqlivehuawei`): opening its search page looks like "the app reloads the home page",
because the process is killed on focus and restarted, once every 20-30 s while the page stays up.

```
am_crash: java.lang.NullPointerException
  Attempt to invoke virtual method 'boolean android.content.ClipboardManager.hasPrimaryClip()'
  on a null object reference
  at android.widget.TextView.canPaste(TextView.java:11104)
  at android.widget.TextView.onInitializeAccessibilityNodeInfoInternal(TextView.java)
  at android.view.View.onFocusChanged(View.java)
```

A/B on the same unit: `settings put secure accessibility_enabled 0` → the search page opens, gains focus and
survives; `settings put secure accessibility_enabled 1` → crash within seconds of moving focus into the field.

Two ways out, in order of preference:

1. Turn accessibility help off while using apps with search boxes.
2. If a launcher turns it back on at every start (Aerial's `HomeGuardService` does, and rewriting its
   `home_in_front` preference does not stop it), that single component has to be disabled instead of the
   global switch. `pm disable` on it is refused for `shell`, so this needs root and is out of scope here.

This is not Aerial-specific: any accessibility helper on this firmware can trigger it, and any app with an
`EditText` is a candidate.

### Trap 6: the tuner holds the video plane — third-party players show noise, audio keeps playing

The factory menu's 单键开关 (single-key switch) ships on, and `ro.boot.singleKeyMode=1` comes from the kernel
cmdline, so it cannot be cleared with a property. While it is on, the TV pipeline stays alive: `tvserver`
re-tunes roughly once a minute (`TvTunerControl: SetFrontend freq=826000000`) and the TV channel keeps the VFE
video plane, so a third-party player gets no pixels in its surface. The screen shows tuner noise while audio is
fine — it looks like a decoder or player problem, and it is not.

Turning 单键开关 off in the factory menu takes effect immediately without a reboot:

| Property | Before | After |
| :--- | :--- | :--- |
| `persist.sys.facSingleKey` | `1` | `0` |
| `third.get.facSingleKeyEnable` | `true` | `false` |

That stops the automatic boot into live TV, but not the tuner itself: the system restarts `com.skyworth.tv`
within seconds, and `com.skyworth.tv.provider` keeps `tvserver` tuning. `com.skyworth.tv` stays enabled: it owns
live TV and the tuner, and picture settings and the accessibility toggle live in it too, so this list never
disables it.

### Trap 7 (omitted)

A Baidu safety banner shows for about three seconds in the bottom right after boot, drawn by
`com.tianci.launcher.bootcompleted.BootCompletedService` inside `com.tianci.system`. The only fix is disabling
that component, which `shell` is refused; needs root, so it is out of scope here. It is cosmetic and costs
nothing if left alone.

### Trap 8: ADB looks like it "falls off" after every reboot — it was never on by itself

`adbd` is declared in `/init.usb.rc` (not in `/init.rc`) as `class core` and **`disabled`**, so nothing starts
it at boot. `persist.sys.openadb` only makes `getprop` return 1; it does not start the daemon.

So on this firmware, network ADB has to be switched on again after every reboot. That is the firmware working
as designed, not a channel being lost — which matters, because "ADB disappeared" usually sends people looking
for a debloat casualty. Evidence: the mtime of the `persist.sys.openadb` file matches the moment the switch was
flipped by hand, minutes after `uptime` restarted.

For diagnosis, `getprop init.svc.adbd` is the honest answer. On this firmware toybox `netstat -tln` keeps
showing `:::5555 LISTEN` after `adbd` is gone, so a port check can report a listener that is not there; use
`busybox pidof adbd` instead.

## Must stay enabled

| Package | Why |
| :--- | :--- |
| `com.tianci.de` | About / device info (ADB path) — safe alone, crashes when batched with the two below |
| `com.skyworthdigital.stb.dataprovider` | About / device info (ADB path) |
| `com.coocaa.sky.ccapi` | Core API; About crashed after disabling it |
| `com.tianci.movieplatform` | Stock launcher, boot crash if disabled |
| `com.coocaa.factory` | Factory menu, holds the ADB toggle |
| `com.tianci.localmedia` | USB APK install — also the only file browser for sideloading without ADB |
| `com.skyworth.tv` | Factory menu, picture settings, accessibility; also the live TV app and the owner of the tuner |
| `com.coocaa.tvmanager` | TV management |
| `com.coocaa.guide`, `com.coocaa.miracast` | System guide and casting |

## Verified safe to disable (16)

`com.tianci.ad`, `com.tianci.appstore`, `com.tianci.push`, `com.tianci.user`, `com.tianci.ipc`, `com.tianci.autoinstaller`, `com.baidu.oases`, `com.coocaa.mall`, `com.coocaa.activecenter`, `com.coocaa.app_browser`, `com.coocaa.messageprovider`, `com.coocaa.tvpitv`, `com.coocaa.theme.child`, `com.icoolme.tv.weather`, `com.skyworth.blockchain`, `com.skyworth.smarthome_tv`.

The first six (`ad`, `push`, `appstore`, `user`, `ipc`, `autoinstaller`) are already disabled from the factory on this unit, so they are the lowest risk.

Two optional additions, both reboot-checked, with the costs stated:

- `com.tianci.de` — ~70 MB in two processes. Safe **alone**; see Trap 1 for the batch rule.
- `com.coocaa.tvmanager` has no launcher entry of its own (it resolves to the system chooser), so it is a dead
  tile if placed on a home screen, but disabling it is harmless.

## Related report: Coocaa 8R145 / K3 Pro (#417)

Same vendor family (`com.tianci.*` / `com.coocaa.*`), but a different firmware: Realtek RTD2885N, Android 11, board `8R145_P3DM`. Its notes and package list do **not** transfer to this TV, and this one does not transfer to that one.

| | 8R145 (#417, Android 11) | 8R40_K5C (this guide, Android 8.0) |
| :--- | :--- | :--- |
| `cmd package set-home-activity` | accepted | refused: `cannot be home on user 0` |
| Package declaring `android.intent.category.HOME` | the Coocaa launcher | none in the stock firmware; a sideloaded launcher can declare it |
| Disabling `com.tianci.movieplatform` | boot hang, factory reset | black screen at boot, factory reset |
| `com.coocaa.guide` | disabled in the #417 list | **must stay enabled** here |
| About page / ADB lockout (Trap 1) | not reported | the main hazard on this firmware |
| Clipboard service in the firmware | not reported | missing, which crashes text fields under accessibility (Trap 5) |

Packages that appear in both reports as disabled without problems: `com.tianci.ad`, `com.tianci.push`, `com.tianci.autoinstaller`, `com.coocaa.app_browser`, `com.coocaa.messageprovider`.

Do not copy either list onto the other TV. Packages that appear only in the #417 list (for example `com.skyworth.aisense`, `com.coocaa.sysreport`, `com.coocaa.tvpi`) were not tested on the 8R40_K5C and are not in this one.

## Open questions

1. Can the launcher be changed **without** root, through `HIDE_LAUNCHER`, the broadcast
   `com.skyworth.broadcast.homepage.switch`, or hotel mode `persist.sky.hotel_switch`? Untested — the props are
   rewritten and `/system/etc/bootintent.xml` is re-read at boot, both from read-only `/system`, so it looks
   unlikely.
2. Which component of `com.skyworth.tv.provider` keeps `tvserver` re-tuning (Trap 6) is still unidentified;
   disabling the provider was not tried, since the TV app is in the must-keep list.
3. Whether the missing clipboard service can be brought back by installing a third-party provider, instead of
   giving up accessibility. Not tried.
