# JMGO N1 Air projector (K631CN, China LunaOS): `pm disable-user` is refused, and Home is re-granted by a hook inside `system_server`

Two sessions, 2026-11-19 and 2026-11-20. Two days of results, which is why this is recorded here and not
in `examples/`: CONTRIBUTING asks for a few days of living with a list. Two further reasons: this firmware
refuses every disable path, so the packages below are **suspended**, not disabled, and `tools/restore.sh`
does not undo a suspended package - the undo is in this note.

This is the China build (LunaOS, `com.jmgo.launcher` as Home), not the Google TV build of the same family
that [`examples/jmgo-n1s-4k-lonavla/`](../../examples/jmgo-n1s-4k-lonavla/debloat.txt) and
[`examples/jmgo-n1s-holan/`](../../examples/jmgo-n1s-holan/debloat.txt) cover, where `pm disable-user` works.
None of their package names are safe to copy onto this one without checking: the launcher, the app store and
the screensaver all differ.

## Identify the device

| Field | Value |
|---|---|
| Retail name | JMGO N1 Air 超亮版 (Ultra Bright Edition; 坚果 N1 Air on the box), `ro.product.model` `K631CN` |
| Maker | `ro.product.manufacturer` and `ro.product.brand` are both the generic `MediaTek`; device and board `K631CN` |
| Android | 13 (SDK 33), `TP1A.220624.021.A1`, vendor build `LunaOS 1.1.31.9`, UI `7.6.4.032` |
| Fingerprint | `MediaTek/K631CN/K631CN:13/TP1A.220624.021.A1/1.1.31.9:userdebug/dev-keys` |
| Build | `ro.build.type=userdebug` but `ro.debuggable=0`: no `adb root`, no `su` |
| SoC and ABI | MediaTek, `armeabi-v7a` only (32-bit) |
| Memory | 2 GB RAM / 64 GB, `ro.config.low_ram=false` |
| Screen | 1920x1080, density 320, no `wm size` override |
| Home | Stock `com.jmgo.launcher`, Home activity `com.jmgo.launcher/com.jmgo.os.home.HomeActivity` |
| ADB | `ro.adb.secure=0`: port 5555 accepts any host on the LAN without the Allow prompt |

## `pm disable-user` is refused, `cmd package suspend` works

Every disable path answers without changing anything:

```text
pm disable-user --user 0 <package>       exit 1, silent, enabled stays 0
pm disable / cmd package disable         SecurityException: Shell cannot change component state for <pkg> to 2
pm disable-until-used                    SecurityException, same message, to 4
pm hide                                  SecurityException: neither user 2000 nor current process has MANAGE_USERS
```

`pm disable-user` is the interesting one: it exits 1 and prints nothing at all, not even the
`Warning! This command is illegal!` the waipu boxes give, so the exit status is the only signal. AOSP's own
`com.android.printspooler` is refused the same way, which is how the check ruled out a permission problem
with the vendor packages.

`cmd package suspend --user 0 <package>` takes, answers `new suspended state: true`, and the package does
not run again until it is unsuspended - a reboot kept every one of them suspended. What a suspended package
on this build cannot do: launch an activity, start a background service, or receive a broadcast. That is close
enough to disabled for the purpose, but it is not the same state:

- `pm list packages -d` stays empty, because the package is still `enabled=0`. Read
  `dumpsys package <pkg> | grep suspended` instead, or the work looks like it did nothing.
- `tools/restore.sh` sends `pm enable`, which is a no-op on a suspended package. Undo per package with
  `cmd package unsuspend --user 0 <pkg>`.
- A suspended package that the system keeps trying to bring up pops `SuspendedAppActivity`
  ("应用已暂停", app suspended) rather than being skipped quietly. Here that was the screensaver, below.

Suspended and left suspended on this unit, by the owner's choice:

```text
com.jmgo.spider          manufacturer telemetry, broadcast receivers only, no launcher activity
com.jmgo.miiot           Mi Home / IoT account link, never paired
com.jmgo.os.guide        first-run tips and tutorial cards
com.jmgo.music           preloaded media app, unused (VLC covers local playback)
com.jmgo.player          same
com.jmgo.browser         same
com.jmgo.os.gamecenter   game-centre recommendation rows
```

Two more were suspended during the session and **put back**, both while the screensaver was being dealt
with (next section): `com.jmgo.os.dream` itself and `com.jmgo.imagebrowser4k`, the image viewer it reads
from. With the screensaver switched off by the settings below, the image viewer is free to be suspended
again; it was only restored because the screensaver shows `SuspendedAppActivity` without it.

Kept on purpose, because the projector or the owner needs it: `com.jmgo.appstore` (the official store, the
owner wanted it), `com.jmgo.helpcenter`, `com.jmgo.update`, `com.jmgo.holacast.x` (casting),
`com.softwinner.tvremoteserver`, `com.bajintech.assistant`, `com.jmgo.ai.voice`, the apps in use
(`com.xiaodianshi.tv.yst`, `com.quark.yun.tv`, `org.videolan.vlc`, `com.limelight.unofficialA`,
`com.ugreen.tvnas`), and the packages the optics and the remote depend on: `com.jmgo.hippo` (key handling
and brightness), `com.jmgo.autokeystone`, `com.jmgo.tofautofocus`, `com.jmgo.autobrightness`,
`com.jmgo.dewarping`, `com.jmgo.aieyeprotector`, `com.jmgo.a2dpsinkservice`, `com.jmgo.remotecontrolota`,
`com.jmgo.systemapi`, `com.jmgo.setting.x`, `com.jmgo.PackageInstaller`, `com.jmgo.fallbackhome`,
`com.jmgo.tv` (opens HDMI/AV and answers HOME), `com.jmgo.inputmethod.pinyin`, `com.jmgo.launcher`, and every
`com.mediatek.*` and `com.mstar.*` package.

## Home is re-granted by a hook inside `system_server`

The replacement launcher installs and runs, `cmd package set-home-activity` answers `Success`, and
`resolve-activity` names it. One press of the remote's Home key and the stock launcher is back in front,
with the preferred Home changed back. Nothing in the app layer is doing that:

- `logcat` shows the press as `START ... cat=[HOME] cmp=com.jmgo.launcher/com.jmgo.os.home.HomeActivity
  from uid 0` - dispatched by `system_server` from a preference it holds, so the preference was rewritten
  before the dispatch.
- `JmgoWmHook` runs inside `system_server`, not in `com.jmgo.launcher`. The press logs
  `startHomeHook reason:startDockOrHome, fromHomeKey:true`, then `findHomeComponent`, then
  `setPreferHome packageName:com.jmgo.launcher`, then starts `com.jmgo.os.home.HomeActivity` and
  `com.jmgo.launcher/.home.standard.ui.MovieHomeActivity`.
- The `defaultLauncherPkg = com.jmgo.launcher` inside that call is hardcoded in the firmware. It is not a
  property and not a setting, so it cannot be changed from the shell.

What was tried and did not work, so the next person need not repeat it:

| Attempt | Result |
|---|---|
| Add a key-filtering accessibility service to `enabled_accessibility_services` | Written back within a second; the vendor's `JmgoKeyAccessibilityService` guards the list |
| `FLAG_REQUEST_FILTER_KEY_EVENTS` on the new service | The flag sets (`flags=0x20`, `KeyboardInterceptor` shows as enabled in `dumpsys accessibility`) but `onKeyEvent` is never called, so the service is not in the dispatch chain at all |
| Suspend `com.jmgo.launcher` | Home opens `SuspendedAppActivity`, not the replacement; and uid 0's Home start goes around the suspend, so the stock launcher comes up anyway |
| Disable only the Home activity of `com.jmgo.launcher` | `Shell cannot change component state` |
| Write `settings global current_package_name` / `running_package_name` | Changed back within seconds |
| `adb root` | Refused, `ro.debuggable=0` and no `su` |

So there is no way to *hold* Home on this build without losing the remote: `com.jmgo.hippo` handles the
keys and the brightness, so suspending it takes the remote with it. What does work is not fighting for the
Home slot at all:

- Leave `com.jmgo.launcher` enabled, and set `home_guard_package` to it in the launcher's `config.xml`. The
  launcher's accessibility service watches `TYPE_WINDOW_STATE_CHANGED` and sends a Home intent when a
  `com.jmgo.launcher` window comes to the front. Measured 540 ms from the press to the replacement being
  focused, still focused across five samples three seconds later, and still working 71 s after a reboot with
  no ADB involved.
- Debounce it by about 2 s. This firmware emits 2-3 window events per press, and each `openHome()` is a
  `NEW_TASK|RESET_TASK_IF_NEEDED` start that hands `MainActivity` a new intent and replays its
  "back to the first tile" animation, which reads as the home screen jittering. Logged once per press after
  the debounce.
- Keep `stock_home_redirect` **empty**. Set to `com.jmgo.launcher` it never matches - the redirect collects
  the `CATEGORY_HOME` activity `com.jmgo.os.home.HomeActivity`, while the window that actually comes to the
  front is `home.standard.ui.MovieHomeActivity`, started by the private action
  `com.jmgo.os.action.movie.HOME`. Worse, it makes the "stock Home stayed" test true after every press, so
  the launcher falls back to a bare component start, which lands in a standard task and gets its layer hidden
  by the same hook: the process is alive, the window is invisible, and the focus request answers
  `NOT_VISIBLE`.

## What each key on the remote actually sends

Recorded once with `getevent -lt` on the BLE receiver (`JMGO Controller`, 0508:0111) and again from
`WindowManager: interceptKeyTq` for the same presses, so the keycode and the window it raises are the same
event. This remote has no input-source key at all; the nearest equivalents are 设置 (settings) and 菜单 (menu),
which is why those two are the ones measured here:

| Key | Keycode | What happens |
|---|---|---|
| 桌面 (home) | 706, which the policy rewrites to 3 (`KEYCODE_HOME`) | stock `HomeActivity`, replacement back in front 90 ms later |
| 设置 (settings) | 605 | `com.jmgo.setting.x` raises its dashboard panel over the launcher |
| 菜单 (menu) | 82 (`KEYCODE_MENU`) | the replacement's own menu, nothing vendor-side |
| 返回 (back) | 4 (`KEYCODE_BACK`) | nothing, this is a root activity |

- 桌面 (home) is not `KEYCODE_HOME` on the wire. The remote sends `BTN_8`, the policy turns that into keycode
  706 and only then into 3, so nothing watching for HOME at the input layer ever sees it. That is enough to
  explain why an injected `input keyevent 3` and a real press behave differently on this unit.
- 设置 (settings) is an overlay, not an activity: `input_focus` moves to `com.jmgo.setting.x` and back while
  `com.gathin.tvhome.MainActivity` is never paused, so the guard has nothing to correct. The next 桌面
  (home) press dismisses it - `input_cancel` on the panel window at the moment the key is consumed, then its
  input channel is disposed.
- None of them interrupts anything else: the launcher process kept its pid across every press, the
  accessibility service stayed bound, and no `Force stopping` appeared. The 菜单 (menu) popup is the
  replacement's, `MainActivity` handles `KEYCODE_MENU` itself.

## Standby survives; a full power-off does not

The power menu has four entries: 关机 (power off), 重启 (restart), 休眠 (standby) and 关闭光源 (light source
off). The two standby entries never reach Android; 关机 (power off) is the only one that breaks the guard:

| Action | What Android sees | Guard afterwards |
|---|---|---|
| 休眠 (standby) | nothing: `mLastSleepTime=0`, `/proc/uptime` unbroken | still bound, launcher still in front |
| 关闭光源 (light source off) | nothing, same readings | still bound |
| 重启 (restart) | a full boot | rebinds during boot, launcher in front by 44 s |
| 关机 (power off) | shutdown, then a cold boot | **stays unbound** |

Measured with the remote: two minutes in 休眠 (standby), woken, the replacement launcher was still in front,
no ADB involved; 关闭光源 (light source off) reads the same way, light engine off and Android untouched. The
power-off case breaks in two steps.

### 1. The vendor's process cleaner stops the launcher on the way down

During shutdown `com.jmgo.hippo` runs `StrKillProcessUtils.clearBackagroundApplication`, which force-stops
the foreground app:

```text
23:11:55.158  Force stopping com.gathin.tvhome : from pid 1532
23:11:55.456  am_proc_start {GuardRevival}          <- the process is started again immediately
23:11:57.446  STR start off
```

Its keep-list is 32 hardcoded `com.jmgo.*` names plus anything that answers `isSystemApp` or
`isSystemUpdateApp`. A third-party launcher is neither, so it is stopped. The 298 ms restart does not help;
the damage is in the next step, not in the process being gone.

### 2. AMS does not re-read the list after a force-stop

The services actually in use live in a list inside `AccessibilityManagerService`, filled once at boot from
`enabled_accessibility_services`. Force-stopping the package drops its entry from that list. The value in
Settings is still there, but this firmware has turned off the notification that would make AMS read it again:

```text
23:35:45.148  SettingsProvider: mutateSecureSetting disable modify enabled_accessibility_services
```

So all of these leave AMS unchanged: writing the value again, deleting it, flipping `accessibility_enabled`
0 → 1, `cmd accessibility`, and switching the component off and on. The component gets re-bound against the
same stale list; nothing re-reads it.

Two things do bring the guard back, and both work because they cause a re-read rather than a re-bind:

- a reboot - AMS reads the list on the way up, and
- reinstalling the APK - `PACKAGE_REPLACED` is the one package event this AMS acts on.

Practical rule for this unit: use 休眠 (standby) or 关闭光源 (light source off) for daily standby. If 关机
(power off) is pressed by mistake, one 重启 (restart) brings the launcher back in about 40 s; nothing shorter
does.

A cold boot after 重启 (restart), with both the replacement launcher and the guard in place, landed on the
replacement five out of five, guard bound by 44 s. The boot after 关机 (power off) is not a different
boot - the guard was already unbound before it started, which is why nothing recovers on the way up.

## Screensaver: the vendor's full-screen promo

The screensaver is `com.jmgo.os.dream/com.jmgo.os.dream.core.service.JGDreamService`, and on this build it
plays a full-screen film promotion once it is up. Suspending it is the wrong fix here: the system still tries
to raise it and shows `SuspendedAppActivity` over Home instead. Switching it off where the firmware expects
to be told is enough:

```sh
settings put secure screensaver_enabled 0
settings put secure screensaver_activate_on_sleep 0
settings put secure screensaver_activate_on_dock 0
```

All three were `1`. With them at `0`, `mShowingDream` stays `false` and the panel goes to standby on
`screen_off_timeout` (180 s here) instead, which a remote key wakes. The vendor package stays installed and
enabled, so nothing else that touches it breaks.

## The screen moves in apps, not only on Home

An up-down drift of a few pixels, visible on Home and inside apps alike, on a unit that was not doing it
before the session. It is worth writing down because the cause was not the obvious one.

`screencap` comparisons came back identical frame to frame (0 px over 10 frames, and the Home layer produced
no frame at all over 6 s), so the Android composition is not moving: the drift happens after it, in the
projection geometry, and a screenshot cannot show it - it takes a phone camera.

What moves the geometry is `com.jmgo.autokeystone`, and specifically
`com.jmgo.autokeystone.tof.TofRealTimeKeystone`, which holds the gyroscope at 50 Hz (`samplingPeriod=20000us`)
and steers the keystone in a closed loop, with the vertical axis as its main one. That on its own is normal
and does not drift. What was not normal: `dumpsys sensorservice` showed the same gyroscope registered again
and again inside 30 seconds, from four different pids, one of them the factory page below. Two sources of
attitude data steering one geometry means it gets corrected back and forth, which is what the drift looked
like.

Letting `com.jmgo.autokeystone` reload once cleared it - the owner toggled the projector's own
"实时梯形校正" (real-time keystone correction) switch off and on - and after that the feature can be left on
without the drift coming back.
Checked afterwards: one `com.jmgo.autokeystone` process, one `TofRealTimeKeystone` connection in
`dumpsys sensorservice`. Also checked along the way and ruled out: `dynamic_black_enabled=0`,
`smart_illuminate_enabled=0`, `picture_pixel_shift=0`, auto brightness off (`screen_brightness_mode=0`), the
display mode unchanged (`mActiveModeId=2`), and the TOF range reading stable at `3689` across samples, so the
autofocus motor was not breathing.

Two traps on the way:

- **Do not start `com.jmgo.autokeystone/.tof.ui.MainActivity` from ADB.** It is the factory calibration page
  (it shares the `TofRealTimeKeystone` registration), and it carries "正面零位校准" (front zero calibration)
  and "复位光机顶点" (reset the optical engine's corner points), which
  overwrite the optical calibration. This session opened it by mistake, pressed nothing, and left; the drift
  above is what followed. Nothing in this repository needs that page.
- `settings get system jmgo_low_latency_before` holds `{"keystone_realtime":1,...}`: entering the
  game/low-latency mode writes the real-time keystone back **on**, so after a game the projector's switch has
  to be checked again.

`am force-stop` and `cmd package suspend` do not stop this package - it runs under the shared system uid and
comes back within ten seconds - so do not reach for them.

## Other firmware behaviour

- The vendor binds an accessibility service over the private broadcast
  `action.jmgo.request.accessibility.service`, with the component as the extra `compontentNameStr` (the
  misspelling is theirs) and its own uid as the sender. `com.jmgo.hippo`'s
  `JmgoKeyAccessibilityService` is what owns the channel on a stock unit.
- The app grid was empty with the reference launcher because the projector's apps only register
  `CATEGORY_LAUNCHER`, not `CATEGORY_LEANBACK_LAUNCHER`: eight leanback entries exist, four in the dock, one
  is the launcher itself, and the rest were hidden. `library_apps` in `config.xml` is what brings
  the app store, the NAS client, the casting app and the file manager in. An app in the dock that is not a
  leanback entry has to be in `library_apps` as well, or its dock cell stays empty.
- `rows_below_dock=0` means "no grid rows below the dock", not "dock at the bottom with the grid above": the
  extra tiles are laid out off-screen instead. With a grid, keep it at 1 or more.

## What was not checked

- Several days of use, and whether any suspended package is missed: this covers two sittings, not the days
  CONTRIBUTING asks for.
- The remote's voice and volume keys, which were not pressed. The settings and menu keys are in the key table
  above; this remote has no input-source key, so there is nothing to press for it on this unit.

## ADB

**Switch both debug switches back off in the projector's own Settings when the session ends**: with
`ro.adb.secure=0` and `persist.adb.tcp.port=5555`, anything on the LAN that reaches port 5555 gets a shell
with no prompt. Both were on throughout this session, which is how the note was taken.
`tools/venv/bin/python -I tools/adb-closed.py <tv ip>` is the check that the port is closed rather than the
switch merely being off.
