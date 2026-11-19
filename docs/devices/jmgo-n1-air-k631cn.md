JMGO N1 Air projector (K631CN, China LunaOS): pm disable-user is refused, and Home is re-granted by a hook inside system_server
One session on 2026-11-19, same-day results only. That is why this is recorded here and not in examples/:
CONTRIBUTING asks for a few days of living with a list. Two further reasons: this firmware refuses every
disable path, so the packages below are suspended, not disabled, and tools/restore.sh does not undo a
suspended package - the undo is in this note.
This is the China build (LunaOS, com.jmgo.launcher as Home), not the Google TV build of the same family
that examples/jmgo-n1s-4k-lonavla/ and
examples/jmgo-n1s-holan/ cover, where pm disable-user works.
None of their package names are safe to copy onto this one without checking: the launcher, the app store and
the screensaver all differ.
Identify the device
Field
Value
Retail name
JMGO N1 Air 超亮版 (坚果 N1 Air), ro.product.model K631CN
Maker
ro.product.manufacturer and ro.product.brand are both the generic MediaTek; device and board K631CN
Android
13 (SDK 33), TP1A.220624.021.A1, vendor build LunaOS 1.1.31.9, UI 7.6.4.032
Fingerprint
MediaTek/K631CN/K631CN:13/TP1A.220624.021.A1/1.1.31.9:userdebug/dev-keys
Build
ro.build.type=userdebug but ro.debuggable=0: no adb root, no su
SoC and ABI
MediaTek, armeabi-v7a only (32-bit)
Memory
2 GB RAM / 64 GB, ro.config.low_ram=false
Screen
1920x1080, density 320, no wm size override
Home
Stock com.jmgo.launcher, Home activity com.jmgo.launcher/com.jmgo.os.home.HomeActivity
ADB
ro.adb.secure=0: port 5555 accepts any host on the LAN without the Allow prompt
pm disable-user is refused, cmd package suspend works
Every disable path answers without changing anything:
pm disable-user --user 0 <package>       exit 1, silent, enabled stays 0
pm disable / cmd package disable         SecurityException: Shell cannot change component state for <pkg> to 2
pm disable-until-used                    SecurityException, same message, to 4
pm hide                                  SecurityException: neither user 2000 nor current process has MANAGE_USERS
pm disable-user is the interesting one: it exits 1 and prints nothing at all, not even the
Warning! This command is illegal! the waipu boxes give, so the exit status is the only signal. AOSP's own
com.android.printspooler is refused the same way, which is how the check ruled out a permission problem
with the vendor packages.
cmd package suspend --user 0 <package> takes, answers new suspended state: true, and the package does
not run again until it is unsuspended - a reboot kept every one of them suspended. What a suspended package
on this build cannot do: launch an activity, start a background service, or receive a broadcast. That is close
enough to disabled for the purpose, but it is not the same state:
- pm list packages -d stays empty, because the package is still enabled=0. Read
|  |  |
| --- | --- |
dumpsys package <pkg> | grep suspended instead, or the work looks like it did nothing.
- tools/restore.sh sends pm enable, which is a no-op on a suspended package. Undo per package with
cmd package unsuspend --user 0 <pkg>.
- A suspended package that the system keeps trying to bring up pops SuspendedAppActivity
("应用已暂停") rather than being skipped quietly. Here that was the screensaver, below.
Suspended and left suspended on this unit, by the owner's choice:
com.jmgo.spider          manufacturer telemetry, broadcast receivers only, no launcher activity
com.jmgo.miiot           Mi Home / IoT account link, never paired
com.jmgo.os.guide        first-run tips and tutorial cards
com.jmgo.music           preloaded media app, unused (VLC covers local playback)
com.jmgo.player          same
com.jmgo.browser         same
com.jmgo.os.gamecenter   game-centre recommendation rows
Two more were suspended during the session and put back, both while the screensaver was being dealt
with (next section): com.jmgo.os.dream itself and com.jmgo.imagebrowser4k, the image viewer it reads
from. With the screensaver switched off by the settings below, the image viewer is free to be suspended
again; it was only restored because the screensaver shows SuspendedAppActivity without it.
Kept on purpose, because the projector or the owner needs it: com.jmgo.appstore (the official store, the
owner wanted it), com.jmgo.helpcenter, com.jmgo.update, com.jmgo.holacast.x (casting),
com.softwinner.tvremoteserver, com.bajintech.assistant, com.jmgo.ai.voice, the apps in use
(com.xiaodianshi.tv.yst, com.quark.yun.tv, org.videolan.vlc, com.limelight.unofficialA,
com.ugreen.tvnas), and the packages the optics and the remote depend on: com.jmgo.hippo (key handling
and brightness), com.jmgo.autokeystone, com.jmgo.tofautofocus, com.jmgo.autobrightness,
com.jmgo.dewarping, com.jmgo.aieyeprotector, com.jmgo.a2dpsinkservice, com.jmgo.remotecontrolota,
com.jmgo.systemapi, com.jmgo.setting.x, com.jmgo.PackageInstaller, com.jmgo.fallbackhome,
com.jmgo.tv (opens HDMI/AV and answers HOME), com.jmgo.inputmethod.pinyin, com.jmgo.launcher, and every
com.mediatek.* and com.mstar.* package.
Home is re-granted by a hook inside system_server
The replacement launcher installs and runs, cmd package set-home-activity answers Success, and
resolve-activity names it. One press of the remote's Home key and the stock launcher is back in front,
with the preferred Home changed back. Nothing in the app layer is doing that:
- logcat shows the press as START ... cat=[HOME] cmp=com.jmgo.launcher/com.jmgo.os.home.HomeActivity
from uid 0 - dispatched by system_server from a preference it holds, so the preference was rewritten
before the dispatch.
- JmgoWmHook runs inside system_server, not in com.jmgo.launcher. The press logs
startHomeHook reason:startDockOrHome, fromHomeKey:true, then findHomeComponent, then
setPreferHome packageName:com.jmgo.launcher, then starts com.jmgo.os.home.HomeActivity and
com.jmgo.launcher/.home.standard.ui.MovieHomeActivity.
- The defaultLauncherPkg = com.jmgo.launcher inside that call is hardcoded in the firmware. It is not a
property and not a setting, so it cannot be changed from the shell.
What was tried and did not work, so the next person need not repeat it:
Attempt
Result
Add a key-filtering accessibility service to enabled_accessibility_services
Written back within a second; the vendor's JmgoKeyAccessibilityService guards the list
FLAG_REQUEST_FILTER_KEY_EVENTS on the new service
The flag sets (flags=0x20, KeyboardInterceptor shows as enabled in dumpsys accessibility) but onKeyEvent is never called, so the service is not in the dispatch chain at all
Suspend com.jmgo.launcher
Home opens SuspendedAppActivity, not the replacement; and uid 0's Home start goes around the suspend, so the stock launcher comes up anyway
Disable only the Home activity of com.jmgo.launcher
Shell cannot change component state
Write settings global current_package_name / running_package_name
Changed back within seconds
adb root
Refused, ro.debuggable=0 and no su
So there is no way to hold Home on this build without losing the remote: com.jmgo.hippo handles the
keys and the brightness, so suspending it takes the remote with it. What does work is not fighting for the
Home slot at all:
- Leave com.jmgo.launcher enabled, and set home_guard_package to it in the launcher's config.xml. The
launcher's accessibility service watches TYPE_WINDOW_STATE_CHANGED and sends a Home intent when a
com.jmgo.launcher window comes to the front. Measured 540 ms from the press to the replacement being
focused, still focused across five samples three seconds later, and still working 71 s after a reboot with
no ADB involved.
- Debounce it by about 2 s. This firmware emits 2-3 window events per press, and each openHome() is a
|  |  |
| --- | --- |
NEW_TASK|RESET_TASK_IF_NEEDED start that hands MainActivity a new intent and replays its
"back to the first tile" animation, which reads as the home screen jittering. Logged once per press after
the debounce.
- Keep stock_home_redirect empty. Set to com.jmgo.launcher it never matches - the redirect collects
the CATEGORY_HOME activity com.jmgo.os.home.HomeActivity, while the window that actually comes to the
front is home.standard.ui.MovieHomeActivity, started by the private action
com.jmgo.os.action.movie.HOME. Worse, it makes the "stock Home stayed" test true after every press, so
the launcher falls back to a bare component start, which lands in a standard task and gets its layer hidden
by the same hook: the process is alive, the window is invisible, and the focus request answers
NOT_VISIBLE.
Screensaver: the vendor's full-screen promo
The screensaver is com.jmgo.os.dream/com.jmgo.os.dream.core.service.JGDreamService, and on this build it
plays a full-screen film promotion once it is up. Suspending it is the wrong fix here: the system still tries
to raise it and shows SuspendedAppActivity over Home instead. Switching it off where the firmware expects
to be told is enough:
settings put secure screensaver_enabled 0
settings put secure screensaver_activate_on_sleep 0
settings put secure screensaver_activate_on_dock 0
All three were 1. With them at 0, mShowingDream stays false and the panel goes to standby on
screen_off_timeout (180 s here) instead, which a remote key wakes. The vendor package stays installed and
enabled, so nothing else that touches it breaks.
The screen moves in apps, not only on Home
An up-down drift of a few pixels, visible on Home and inside apps alike, on a unit that was not doing it
before the session. It is worth writing down because the cause was not the obvious one.
screencap comparisons came back identical frame to frame (0 px over 10 frames, and the Home layer produced
no frame at all over 6 s), so the Android composition is not moving: the drift happens after it, in the
projection geometry, and a screenshot cannot show it - it takes a phone camera.
What moves the geometry is com.jmgo.autokeystone, and specifically
com.jmgo.autokeystone.tof.TofRealTimeKeystone, which holds the gyroscope at 50 Hz (samplingPeriod=20000us)
and steers the keystone in a closed loop, with the vertical axis as its main one. That on its own is normal
and does not drift. What was not normal: dumpsys sensorservice showed the same gyroscope registered again
and again inside 30 seconds, from four different pids, one of them the factory page below. Two sources of
attitude data steering one geometry means it gets corrected back and forth, which is what the drift looked
like.
Letting com.jmgo.autokeystone reload once cleared it - the owner toggled the projector's own
"实时梯形校正" switch off and on - and after that the feature can be left on without the drift coming back.
Checked afterwards: one com.jmgo.autokeystone process, one TofRealTimeKeystone connection in
dumpsys sensorservice. Also checked along the way and ruled out: dynamic_black_enabled=0,
smart_illuminate_enabled=0, picture_pixel_shift=0, auto brightness off (screen_brightness_mode=0), the
display mode unchanged (mActiveModeId=2), and the TOF range reading stable at 3689 across samples, so the
autofocus motor was not breathing.
Two traps on the way:
- Do not start com.jmgo.autokeystone/.tof.ui.MainActivity from ADB. It is the factory calibration page
(it shares the TofRealTimeKeystone registration), and it carries "正面零位校准" and "复位光机顶点", which
overwrite the optical calibration. This session opened it by mistake, pressed nothing, and left; the drift
above is what followed. Nothing in this repository needs that page.
- settings get system jmgo_low_latency_before holds {"keystone_realtime":1,...}: entering the
game/low-latency mode writes the real-time keystone back on, so after a game the projector's switch has
to be checked again.
am force-stop and cmd package suspend do not stop this package - it runs under the shared system uid and
comes back within ten seconds - so do not reach for them.
Other firmware behaviour
- The vendor binds an accessibility service over the private broadcast
action.jmgo.request.accessibility.service, with the component as the extra compontentNameStr (the
misspelling is theirs) and its own uid as the sender. com.jmgo.hippo's
JmgoKeyAccessibilityService is what owns the channel on a stock unit.
- The app grid was empty with the reference launcher because the projector's apps only register
CATEGORY_LAUNCHER, not CATEGORY_LEANBACK_LAUNCHER: eight leanback entries exist, four in the dock, one
is the launcher itself, and the rest were hidden. library_apps in config.xml is what brings
the app store, the NAS client, the casting app and the file manager in. An app in the dock that is not a
leanback entry has to be in library_apps as well, or its dock cell stays empty.
- rows_below_dock=0 means "no grid rows below the dock", not "dock at the bottom with the grid above": the
extra tiles are laid out off-screen instead. With a grid, keep it at 1 or more.
What was not checked
- Several days of use, and whether any suspended package is missed: this is a same-day report.
- A cold boot with the replacement launcher and the guard both in place. 71 s after a warm reboot the guard
was bound and Home landed on the replacement, nothing more.
- The remote's own Input and Settings keys. Injected keys are not the same test (the repository says so, and
this unit demonstrated it: an injected Home press goes through the hook while a filtered one never reaches
the service), so those two still want a human with the remote.
ADB
ro.adb.secure=0 and persist.adb.tcp.port=5555, so anything on the LAN that reaches port 5555 gets a shell
with no prompt. Both debug switches were turned off in the projector's own Settings at the end of the
session, and tools/venv/bin/python -I tools/adb-closed.py <tv ip> is the check that the port is closed
rather than the switch merely being off.
