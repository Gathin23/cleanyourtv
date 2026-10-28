# Troubleshooting

Things that bit us or contributors, so they don't bite you. Commands use `tools/tv.py`; see the
[README](../README.md#doing-it-by-hand) for setting it up.

## Connecting

- **macOS: "No route to host" (`EHOSTUNREACH`)** although the port is open. Recent macOS keeps a
  process off the local subnet under Local Network privacy, and the kernel reports that as this
  error, the same one a TV that is switched off gives. `tools/tv.py` does **not** avoid it: the
  permission belongs to the app that runs the client (Terminal, iTerm2, VS Code), not to the `adb`
  binary or to `python`, so changing ADB client makes no difference. Allow that app under System
  Settings > Privacy & Security > Local Network, then quit and reopen it; a denial sticks once it
  has happened, and a prompt with no window to appear in is denied for you.
  Confirm it before changing anything on the network, with the **default gateway**: in practice it
  keeps answering while every other address on the subnet gives this error, so if the router
  connects and nothing else does, it is the permission and not the network. ARP and mDNS keep
  working throughout, which is what makes it read as AP isolation or a router firewall: `arp -an`
  shows the TV's MAC and `dns-sd -B _androidtvremote2._tcp` still finds it by name while no port on
  it can be opened. Seen on macOS 26 (Darwin 25.6) against a Hisense `hengshan` set, Google TV 12,
  where the stock `adb` and `tools/tv.py` failed identically.
  The closing check is denied the same way, and it is the one the procedure accepts: `tools/adb-closed.py
  <tv ip>` answers `could not check <ip>:5555 ([Errno 65] No route to host). Is the TV on and on this
  network?` followed by the Local Network note, exit 2, so a machine without the permission can neither
  confirm nor deny that the port is closed. Grant it to the terminal, reopen it, and check again: only a
  "closed" from a granted terminal counts.
- **No "wireless debugging" toggle** on some TVs, Xiaomi among them. USB debugging alone opens
  network ADB on port 5555.
- **USB debugging is greyed out and there is no Wireless debugging entry either**, seen on a carrier
  box ([Airtel Xstream, Android 11](https://github.com/Gathin23/cleanyourtv/issues/227)). Some ISP
  firmware removes both switches, and then no shell can be reached: `tools/tv.py` fails,
  `adb connect <ip>:5555` times out, and so does a port check. That timeout is the firmware, not
  Wi-Fi, the router or the cable, so don't go rebuilding the network for it. Greyed-out rather than
  missing points at a device-owner policy or a provisioning lock; the toggle comes back after a
  firmware update from the ISP, or not at all. Without one of the two switches this repo has nothing
  to talk to.
- **Developer options is hidden, or Build number asks for a password**, seen on an ISP box (FPT Play
  650, Android TV 14): tapping Build seven times opens a password prompt instead of unlocking the menu.
  Often only the way in is locked, not the screen itself: TvSettings still has
  `com.android.tv.settings/.system.development.DevelopmentActivity`, and any app can open it by its
  name or with the `android.settings.APPLICATION_DEVELOPMENT_SETTINGS` action. Install an app that
  opens activities by name (Activity Launcher) from the Play Store, open that screen and switch on
  USB debugging there. When no app can open it either, see the carrier-box bullet above.
- **USB debugging never opens port 5555** on some Google TV sets (a Skyworth-built Philips among
  them). Pair once through Wireless debugging with the stock `adb` (`adb pair <ip>:<port> <code>`,
  then `adb connect`), run `adb tcpip 5555`, and `tools/tv.py` works from then on. `dns-sd -B
  _adb-tls-pairing._tcp` (or `adb mdns services`) shows the pairing port while the code is on screen.
  Android switches Wireless debugging off whenever Wi-Fi drops; turn it back on and `adb` reconnects
  without a new code. Port 5555 from `adb tcpip` usually lasts only until the next reboot. When you
  close up, switch Wireless debugging off on its own: on a Chromecast with Google TV 4K (`sabrina`)
  turning USB debugging off left the Wireless debugging port accepting connections, and
  `tools/adb-closed.py` checks 5555 unless it is given that port. `adb mdns services` lists the port
  as `_adb-tls-connect._tcp` while it is open: note it before switching Wireless debugging off, and
  run `tools/adb-closed.py <tv ip> <port>` straight after, before the reboot. After the reboot the old
  number reads closed either way, because Wireless debugging takes a new port each time it starts.
- **Port 5555 stays closed and there is no Wireless debugging entry either**, so the pair-once recipe
  above has nothing to pair with: a [Philips 58PUS8505/62 The One, Android TV 12,
  `STT2.230526.001`](https://github.com/Gathin23/cleanyourtv/issues/1489) reported USB debugging on, no
  Wireless debugging screen, `adb connect <tv ip>:5555` refused, and a USB cable to the computer that got
  no connection either. Two routes are left, and the firmware has to offer one of them. The first is the
  port-scan bullet further down this section: some firmware runs `adbd` on a port of its own, so scan and
  try each open one with `TV_PORT=<port> tools/tv.py --probe`. The second is the cable, when `adb devices` lists the set
  while USB debugging is on: `adb tcpip 5555` with the stock `adb` then opens the port, the cable comes
  out, and `tools/tv.py` reaches the TV over the network. The cable is only a way to open the port, never
  a way to run these tools: `tools/tv.py` connects with `AdbDeviceTcp(HOST, PORT)` - `<tv ip>:5555`, or
  `TV_PORT` for another port - and carries no USB transport, so no script here talks to a TV over USB. A
  set that neither enumerates over USB nor runs `adbd` on any TCP port has no way in, and this repo does
  not try to get around that.
- **USB devices go dead while USB debugging is on**, seen on the Xiaomi Mi Box S and the Xiaomi TV
  Box S 3rd gen ([MiTV-AFMU0, Google TV 14](https://github.com/Gathin23/cleanyourtv/issues/737)):
  their firmware switches the USB port to ADB device mode, which cuts the power to a USB speaker or
  drive. A **USB Ethernet adapter sits on that same port**, so the network goes with it and the box
  falls back to Wi-Fi without saying so. `dumpsys usb | grep host_connected` prints `false`;
  `dumpsys connectivity | grep -o 'Transports: [A-Z]*'` prints `WIFI` on a set the owner calls wired.
  A bare `eth0` stuck at `NO-CARRIER` is these boards' own socketless MAC, not the adapter - on the
  TCL 65C735 the adapter came up as `eth1`. How long the port survives debugging differs by firmware:
  the two Xiaomi boxes lose it as soon as debugging is on, while the 65C735 kept the adapter until a
  reboot with debugging still on put the port in gadget mode (`sys.usb.config=adb`, `Mstar-udc`) and
  it never came back ([that list's header](../examples/tcl-65c735/debloat.txt)). Either way there is
  no way back from the shell on a set with no Wireless debugging, and switching USB debugging off
  stops `adbd` too, so plan one sitting rather than two: switch debugging off at the end, replug the
  adapter, and confirm Ethernet before handing the TV back. The launcher never needs debugging.
  A Chromecast with Google TV 4K (`sabrina`, `UTTC.250917.004`) kept its USB Ethernet adapter with
  USB debugging on: `eth0` stayed listed, and Ethernet connected with debugging still on. It printed
  `host_connected=false` while the adapter was present as `eth0`, so there `host_connected=false` was
  no proof the adapter was gone ([its list](../examples/google-chromecast-gtv-4k/debloat.txt)).
- **Port 5555 refuses the connection although network debugging is on**, and no Wireless debugging is
  advertised, seen on a [Wanbo T6R Max projector](devices/wanbo-t6r-max.md) (MediaTek MT5862, Android 9,
  `TVOS-04.16.031.01.12`): the firmware starts `adbd` on its own port, 5877 there (`service.adb.tcp.port`),
  and the TV never shows it. Scan the TV's TCP ports (`nmap -p- <tv ip>`, or a short Python loop over
  `socket.connect_ex`; skip `nc` on macOS) and try each open port with `TV_HOST=<tv ip> TV_PORT=<port>
  tools/tv.py --probe`: the ADB one answers, the others time out. A sandboxed shell can make `nmap`'s own
  ping-discovery probe fail outright ("Network is down", SO_ERROR 50) before it scans anything;
  `nmap -sT -Pn` (a plain TCP connect scan, no ping, no raw sockets) or the Python loop above still
  works there. Every script here honours `TV_PORT`, and
  the closing check is `tools/adb-closed.py <tv ip> <port>`.
- **Close port 5555 when you're done**: plaintext, and a shell for anyone on the LAN when `ro.adb.secure=0`, or when it is unset on a debug build or Android 4.4 (`tools/disable.sh` warns about both). Steps in the README FAQ.
- **The TV sits on a separate VLAN or IoT network** and `tools/tv.py` times out although debugging
  is on: the router keeps the computer from reaching it. Add a temporary firewall rule from your
  computer to the TV on tcp/5555 only, and remove it again when you close ADB. Opening the whole
  segment, or moving the TV onto the main network for good, undoes the reason it was separate.
- **Nothing on the network answers while a VPN runs on the computer**, not even the router: `ping`
  prints `General failure` on Windows and port 5555 times out, although the TV is on the same Wi-Fi and
  `arp -a` lists it. Seen with Proton VPN's free plan on Windows 11, working on a Sankey SMART_TV (CVTE,
  HiSilicon Hi3751V350, Android 12). The VPN client's firewall blocks the local network; marking the
  Wi-Fi as a private network does not change that. Turn on the client's local-network option and
  reconnect: Windscribe has Allow LAN traffic (Preferences > Connection) on its free plan, Proton VPN's
  Allow LAN connections is for paid plans only. Or disconnect the VPN for the session. Turn the option
  off again afterwards if you use public Wi-Fi.
- **`tools/tv.py` times out after the TV took the connection** (`ADB did not finish the handshake`,
  `TcpTimeoutException: Reading from <tv ip>:5555 timed out`) and no "Allow USB debugging?" prompt is
  waiting: another ADB client may be holding the TV, as some firmware's `adbd` serves one client at a
  time. Stop that client for the session, then run `tools/tv.py --probe` again. The stock `adb` server
  is one: if `adb devices` lists the TV, run `adb kill-server` first. A
  [Xiaomi Mi TV 4A, Android 6.0.1](https://github.com/Gathin23/cleanyourtv/issues/1185) timed out
  whenever that server held a connection to the same port. Home Assistant's Android Debug Bridge
  integration is another (its Android TV Remote integration does not use ADB): a
  [TCL 55C61B, Google TV 12](https://github.com/Gathin23/cleanyourtv/issues/1526) owner had to disable
  it in Home Assistant first, because it was holding the TV's ADB connection.
- **Windows PowerShell 5.1 adds a byte-order mark** to text piped into a program, which turned the
  first batch command into gibberish. `tv.py` now strips it, and the `.ps1` scripts pipe clean UTF-8.
- **"Allow USB debugging?" comes back**, or the TV answers `unauthorized` although `adb devices` already
  works: `tv.py` keeps a key of its own in `tools/adbkey/` and offers the stock `~/.android/adbkey`
  ([#105](https://github.com/Gathin23/cleanyourtv/issues/105)) after it, so a set that trusts either key
  connects with no prompt. Older copies offered the stock key only while `tools/adbkey` did not exist,
  which kept a TCL 55C61B `unauthorized` ([#1526](https://github.com/Gathin23/cleanyourtv/issues/1526)),
  so update the checkout first. When the TV trusts neither key, the prompt asks for `tv.py`'s own. To have
  it register the stock key instead, so `adb` and `tv.py` share one approval, delete the folder:
  `rm -rf tools/adbkey` (`Remove-Item -Recurse -Force tools\adbkey` in PowerShell,
  `rmdir /s /q tools\adbkey` in Command Prompt), then `tools/tv.py --probe`
  (`tools\venv\Scripts\python.exe -I tools\tv.py --probe` on Windows) and accept that one prompt. Every
  attempt with an unaccepted key queues one more prompt on the TV, so dismiss them all (Back) before
  testing with key presses.
- **Git Bash rewrites paths** like `/data/local/tmp` into Windows paths. The scripts set
  `MSYS_NO_PATHCONV=1` for you. Set `JAVA_HOME` and `ANDROID_HOME` before building there.

## Working on the TV

- **Chained `pm` commands** (`pm disable a; pm disable b`) may silently stop after the first. Run
  one per shell call; `--batch` does exactly that.
- **TCL's software update says "Local network or server abnormal" after the debloat**, while the
  network works. TCL's updater gets the TV's device number (dnum) from `com.tcl.xian.StartandroidService`,
  which the lists disable as reporting; without it the check stops with `dnum is null!` in logcat. Enable
  it, reboot once (it starts at boot), run Settings > About > Software Update > Network Update, then disable
  it again. Seen on a TCL Smart TV Pro (`G03_4K_US`, Google TV 11, GTV93).
- **The Google Home panel (lights) is gone from the quick settings** on Google TV. It lives in
  `com.google.android.apps.tv.dreamx`, the same package as the promo screensaver, and Apps only mode hides it
  too. Re-enable dreamx and switch Apps only mode off to bring it back; the screensaver can stay on the
  launcher's clock. Seen on a TCL Smart TV Pro (`G03_4K_US`, Google TV 11, GTV93).
- **The Google Home panel opens and closes at once, though dreamx is still enabled**, once the stock
  launcher is off: dreamx (`com.google.android.apps.tv.dreamx`) loads the panel's account and the device
  trust through launcherx, so with `com.google.android.apps.tv.launcherx` disabled logcat shows
  `Failed to find provider info for
  com.google.android.apps.tv.launcherx.coreservices.account.AccountModeContentProvider`, then
  `EligibilityManagerImpl: SmartHome: Error loading account`, and a `bindService` to launcherx's
  `TrustedNetworkStateService` returning false. Re-enabling launcherx brings the panel back straight away,
  with no reboot. Sparing launcherx's home activity alone is refused as well (`Shell cannot change
  component state`, as in the bullet on `pm disable <package>/<class>` below), so it is the whole package
  or the panel: if the household uses Google Home lights, keep launcherx enabled and hand Home to the new
  launcher with `stock_home_redirect` (`themes/README.md`) instead of disabling it. Seen on a TCL Smart TV
  Pro (`G03_4K_US`, Google TV 11, GTV93,
  [#1653](https://github.com/Gathin23/cleanyourtv/issues/1653)).
- **Large pushes hang** through the Python client. Use `tools/b64push.py` for APKs.
- **`tools/tv.py reboot` never comes back.** `reboot` is a shell command, and adbd stops before it
  sends the shell's last `CLSE`, so the client waits for a reply that cannot arrive: two minutes,
  then an error, on a TCL 65C935 (`G05_4K_GB`, Google TV 11, AR2101,
  [#995](https://github.com/Gathin23/cleanyourtv/issues/995)). A chain that reboots and then checks
  something stalls there. Use `tools/tv.py --reboot`: it sends the reboot through ADB's own service
  and returns once the request is accepted. The TV is still restarting at that point, so let it
  answer again (`tools/tv.py --probe`) before the next command.
- **Every `dumpsys` answers `Service dumps disabled due to hung system process.`**, and `tools/disable.sh`
  skips every package in the list with "the TV's dumpsys shows neither a requested nor a granted permission
  block". That message names Android 5.x, but the cause here is another one: Android's watchdog turns service
  dumps off once it has seen `system_server` hang, and they stay off until `system_server` restarts.
  `dumpsys activity` and `dumpsys connectivity` print nothing useful either, so the Home check cannot read the
  focused app. On a Xiaomi Mi TV 4A (MiTV-MOOQ1, Android TV 11, `RTM4.220307.164.8277`) that had been up for
  almost eight days, the load average was around 62 and `logcat` held repeated watchdog lines from
  `system_server`. Reboot before changing anything (`tools/tv.py --reboot`); `dumpsys` worked again straight
  after. Do not get past the skips with `TVINPUT_ALLOW=1`: the check could not run, which is not the same as
  passing.
- **A `logcat` without the line you expected is not proof that nothing happened.** On a TCL 55C728
  (`G05_4K_GB`, Google TV 11) the main buffer is 1 MiB (`persist.logd.size.main`), and the TV's own
  HDMI, ARC and media logging fills it in well under a minute; around power-on it held about 30
  seconds. A wake or boot event read a minute later is already gone. Enlarge the buffer for the
  session with `tools/tv.py 'logcat -G 16M'` (`logcat -g` shows the size; a reboot brings back the
  firmware's), or keep `logcat` running from before the event. For sound, `dumpsys audio` keeps a
  history of player events that outlasts the log.
- **Every way of disabling a package is refused**, not just one. On a Fire TV Box 2015 (`sloane`,
  Fire OS 5 / Android 5.1) `pm disable-user --user 0` returns `SecurityException`,
  `pm uninstall --user 0` returns `DELETE_FAILED_INTERNAL_ERROR`, and `pm hide` reports success
  while `pm list packages -d` stays empty ([#577](https://github.com/Gathin23/cleanyourtv/issues/577)).
  Read `pm list packages -d` after each attempt instead of the exit code - `pm hide` is the one that
  lies. When all three refuse, this firmware does not take a debloat list at all: there is no shell
  route around it, sponsored rows stay on the home screen, and the only useful work left is the
  TV's own privacy toggles plus DNS blocking (Pi-hole with a smart-TV ad list; the report used
  Perflyst's `AmazonFireTV` list). Fire OS 6 is the opposite case: `pm disable-user` works and about
  ten Amazon packages are protected, which
  [`examples/amazon-fire-tv-stick-4k-2018/debloat.txt`](../examples/amazon-fire-tv-stick-4k-2018/debloat.txt)
  lists, with the same DNS fallback for their endpoints.
- **`pm disable <package>/<class>` is refused with `Shell cannot change component state`**, the
  `SecurityException` you get when you try to keep a package and switch off only its Home activity or
  boot receiver. The notes here have it for setupwraith's `.RecoveryActivity` (TCL G08, Chromecast with
  Google TV HD, Google TV Streamer), launcherx's `.home.HomeActivity` (XGIMI MoGo 4), boot receivers on a
  Fire TV Stick Lite (Fire OS 7) and a Motorola MT4310 (Android TV 11), and the ERGO DVB92YC's Live TV
  boot receiver below. It is not that firmware: from Android 7.0, AOSP's `PackageManagerService` lets
  the shell change only a whole package, and only between default, enabled and disabled-user (from 8.0
  a test-only APK is the exception). The same check refuses `pm disable` and `pm disable-until-used` on
  a whole package, which ask for other states, and any change, `pm enable` included, to a package that
  is already `disabled` (`enabled=2` in `dumpsys package`, as `tvrecommendations` is on a Chromecast
  with Google TV 4K). There is no shell route around it, so don't retry it with other flags: disable the
  whole package with `pm disable-user --user 0`, or keep it.
- **`pm disable-user` answers `Error: java.lang.IllegalArgumentException: Unknown package: <package>`**,
  by hand or for one line of a list ([#1880](https://github.com/Gathin23/cleanyourtv/issues/1880), a Konka
  B50U). Nothing of that name is installed for the user the command names, so that one line changed
  nothing - and the lines around it still run: `tools/disable.sh` pipes the list into
  `tools/tv.py --batch`, which runs each line as its own shell call and carries on to the next whatever
  this one answered, so a list with three unknown names in it still finishes, and the client's exit
  status says nothing about them. What the line is worth is the name in it. Two causes. The list was
  written on another set: every list here names the model and firmware build in its header, preloads
  differ between regional builds of the same model, and
  [`examples/tcl-c845x2-google-tv/debloat.txt`](../examples/tcl-c845x2-google-tv/debloat.txt) calls its
  own two `Unknown package` errors harmless for exactly that reason. Or the name is not a package at all:
  a store title, a home-screen tile, or a name guessed from the app's look, as
  `com.vendor.recommendations` was. `pm list packages` is the only list of names the TV answers to, and a
  fragment of the app's name usually finds it:
  ```sh
  tools/tv.py 'pm list packages | grep -i recommend'
  tools/tv.py 'pm list packages -u --user 0 <package>'    # -u: one removed earlier still shows
  ```
  Nothing from the first command means the app is not on this TV, so comment its line out of the list
  instead of retrying it - and check the model and Android version `tools/tv.py --probe` prints against
  the header of the list you are following. A name the TV does list is the case the bullet below covers:
  `tools/recovery.py` records a listed package that was already gone before the first run as `absent`,
  and `restore.sh` and `check.py` say so in their own words.
- **A package removed with `pm uninstall --user 0`, by hand or by another debloat tool, stays gone
  after `tools/restore.sh`.** The [Toshiba 55Z500QF](devices/toshiba-55z500qf-cn.md) note (Hisense
  firmware, Android 11, `RD2A.211001.002`) has 23 preinstalled packages its owner removed that way. The
  command takes a package away from user 0 only, and a preinstalled one keeps its APK on the system
  partition. `pm list packages -u --user 0 <package>` still names it (`-u` adds uninstalled packages)
  and the same command without `-u` does not; the `User 0:` line of `dumpsys package <package>` says
  `installed=false`. `tools/recovery.py` records a listed package that is already gone when
  `tools/disable.sh` first runs as `absent`, so `restore.sh` prints
  `leaving <package> alone (absent when the record was taken)`, and `tools/check.py` lists it under
  "not installed any more". Bring it back with
  `tools/tv.py 'cmd package install-existing --user 0 <package>'` (AOSP's `cmd package` has it from
  Android 8.0; the Android 6 set in [#1185](https://github.com/Gathin23/cleanyourtv/issues/1185) had no
  such command; no TV here has run it yet). It comes back without its data unless the uninstall used
  `-k`, and disabled if a list that names it has gone through `disable.sh` since it was removed: that
  run sent it `pm disable-user --user 0`, which AOSP applies to a package removed for user 0 too (the
  [Shield list](../examples/nvidia-shield-tv-pro-2019/debloat.txt) notes `disable.sh` reporting
  "disabled-user" for such packages), and install-existing marks it installed without changing that
  state. `pm list packages -d --user 0 <package>` names it if it came back disabled. To keep it, comment
  its line out of your list, then run `tools/tv.py 'pm enable --user 0 <package>'`: with the line still
  in, `check.py` reports it as back on, and `--fix` or the next `disable.sh` run disables it again. If
  you don't want it, keep the line: the list disables it, or keeps it disabled, which the TV's own
  settings can undo. `--fix` or a `disable.sh` run that finds it enabled first changes it from absent to
  enabled in the record, so `restore.sh` turns it back on too. Until then, or in a record an older
  version updated, it stays absent, and `restore.sh` leaves it alone: `pm enable --user 0 <package>` is
  its undo. Two cases this does not cover. An app with no copy on the system partition that only user 0
  had is deleted with its APK: the command answers `Package <package> doesn't exist`, and only
  reinstalling the APK brings it back (the Toshiba's `com.hisense.business.ad`). And a `User 0:` line
  with `installed=true hidden=true` was hidden, not uninstalled. install-existing changes nothing there
  even though it prints `Package <package> installed for user: 0`, and the shell cannot unhide it:
  `pm unhide` needs `MANAGE_USERS`, which the shell does not hold from Android 7.0. Unhide it from the
  device-owner app that hid it, or leave it, as the
  [Chromecast with Google TV 4K list](../examples/google-chromecast-gtv-4k/debloat.txt) does with
  `cast2class` (`hidden=true` and `enabled=2` there).
- **A second Google TV profile keeps the listed packages enabled for itself.** The lists,
  `tools/disable.sh`, `tools/restore.sh` and `tools/check.py` act on user 0, the TV's main profile. A
  profile added on Google TV is a user of its own (type `com.android.tv.profile` in `dumpsys user`), with
  its own enabled state for every package, so the list never reaches it. On a Chromecast with Google TV
  4K (`sabrina`, Google TV 14, `UTTC.250917.004`) with one such profile, six packages the list had
  disabled for user 0 were still enabled for that profile, user 10: `com.google.android.videos`,
  `com.android.tv.feedbackconsent`, `com.google.android.adservices.api`,
  `com.google.android.ext.adservices.api`, `com.google.android.federatedcompute` and
  `com.google.android.ondevicepersonalization.services`. They run only while that profile is in use; it
  had not been since setup. See which profiles there are, and what each still has on:
  ```sh
  tools/tv.py 'pm list users'                    # UserInfo{10:...} after user 0 is a second profile
  tools/tv.py 'pm list packages -e --user 10'    # what that profile has enabled
  ```
  To clean that profile as well, run the list's names for its user by hand (both lines below were run on
  that Chromecast). The recovery record, `restore.sh` and `check.py` do not see it, so undo it the same way:
  ```sh
  tools/tv.py 'pm disable-user --user 10 com.google.android.videos'
  tools/tv.py 'pm enable --user 10 com.google.android.videos'          # undo
  ```
- **The Input key and CEC die after the hidden factory menu is opened**, seen on a Haier/CVTE set
  (Durabrand `DURAT32ISDB`, `dupont`, Android TV 11 build `RTM6.230109.232`,
  [#470](https://github.com/Gathin23/cleanyourtv/issues/470)). `com.cvte.tv.hkcfac` is the
  manufacturer's production app: `dumpsys package` shows it as a factory component, and it has a
  launchable activity, so "let me see what this package does" is enough to trigger it. Opening it
  sets `customer_factory_mode=true` in `/vendor/tvcertificate/fdata.ini` and
  `vendor.mtk.factory.disable.input=1`. The Input button then does nothing and HDMI-CEC is off, and
  at the next boot `com.at.factory.factorymanager` disables Play services, Netflix,
  `com.google.android.katniss` and `com.google.android.apps.mediashell` - `pm enable` from the shell
  is refused for those, so casting and the store stay gone until the mode is cleared. Read the
  package instead of starting it. If it has already been opened, start it, send the factory-exit
  keycode twice **with it in front**, reboot, then turn CEC back on:
  ```sh
  tools/tv.py 'am start -n com.cvte.tv.hkcfac/.MainActivity'
  tools/tv.py 'input keyevent 218; input keyevent 218'
  tools/tv.py --reboot             # returns once ADB accepts it; wait for the TV to come back
  tools/tv.py 'settings put global hdmi_control_enabled 1'
  ```
  Press the real Input button afterwards and check the packages are back:
  `tools/tv.py 'pm list packages -e | grep -E "gms|netflix|mediashell|katniss"'`. More on the unit in
  the [device notes](devices/durabrand-durat32isdb.md).
- **Stremio looks like its server died after the debloat**, seen on a Xiaomi MiTV-MZTU0 (`river`,
  Google TV 14, [list](../examples/xiaomi-tv-a-pro-2026/debloat.txt)). The app opens, the add-ons
  load, and nothing ever plays, which reads like a local server fault. It is not: with
  `com.google.android.apps.mediashell` disabled, Stremio's own `stremio_server_process` is still
  running, still listening on 11470/12470, still holds ESTABLISHED connections to `api.strem.io`,
  and `logcat -b crash` is clean. Stremio's Android TV build plays through mediashell, so the server
  is healthy and the playback path is gone. `pm enable --user 0 com.google.android.apps.mediashell`
  fixed it on that set with no reboot and no reinstall. Check this before blaming your add-ons or
  the network:
  ```sh
  tools/tv.py 'pm list packages -d | grep mediashell'     # output here = this is the cause
  tools/tv.py 'pm enable --user 0 com.google.android.apps.mediashell'
  ```
  Keep the package out of your list if you use Stremio on the TV, or cast from a phone.
- **Picking HDMI 1 in the Inputs menu opened an unrelated app**, seen on an RCA Android TV 9
  (Toptech/Zeasn firmware `RTK2851P`, Realtek RTD2851P,
  [#805](https://github.com/Gathin23/cleanyourtv/issues/805)). The launcher switches to a passthrough
  input with an *implicit* `ACTION_VIEW` on the input's channel URI
  (`vnd.android.cursor.item/channel`), and only sends the theme's `input_action` when nothing claims
  that intent at all. "Nothing claims it" is the catch: that firmware has no Live TV app for the URI,
  and ES File Explorer (`com.estrongs.android.pop/.app.transferstation.FileTransferStationActivity`)
  declares a `VIEW` filter for the same MIME type, so HDMI 1 opened the file manager and
  `input_action` was never reached. A set with no handler at least shows "Not available on this TV";
  a set with one wrong handler opens that app. Ask which app would take a row before blaming the
  launcher:
  ```sh
  tools/tv.py 'cmd package query-activities --brief -a android.intent.action.VIEW -t vnd.android.cursor.item/channel'
  ```
  `live_tv_package` does not help: it is applied as `setPackage` on the Live TV row only, and the
  HDMI and AV rows carry no package. Until the launcher checks that the handler is a TV-input app,
  hide the rows instead: a theme with `source_panel_service` set and `live_tv_package` empty makes
  the Inputs pill open the TV's own source panel (what the reporter did), or disable the app that
  answers when it is not a Live TV or input app. `tools/disable.sh` (`disable.ps1`) keeps any listed
  app that query names and skips it with "the TV opens its HDMI/AV inputs and channels with it", so
  put its line in a list of its own and apply that with `HANDLERS_ALLOW=1` (`$env:HANDLERS_ALLOW`):
  the rest of your list keeps the guard. Run `tools/venv/bin/python -I tools/check.py --fix` on that list with the same
  switch, or it leaves the app on and exits 1. `disable.ps1` reads the switch once and clears it
  from the environment, so one `$env:HANDLERS_ALLOW=1` covers only that one run (#1845); `check.py
  --fix` still reads it, and in PowerShell it stays set until the window is closed: run
  `Remove-Item Env:HANDLERS_ALLOW` after those two, or a later `check.py --fix` on your main list
  skips the guard too. To turn the app back on, use
  `tools/tv.py 'pm enable --user 0 <package>'`. `tools/restore.sh` with that list turns it back on
  as well, and leaves Home on this project's launcher: it turns the stock launcher back on and makes
  it Home only if the stock launcher was on when the list was applied (#1714).
  Report another one rather than reading it as a firmware quirk: the wrong-handler case needs
  a source change, not a setting.
- **`adb exec-out screencap -p` gives a broken PNG** on some MediaTek sets (Xiaomi, and Amazon's
  own Fire TV smart TVs): the binary prints `Init wrapper sys mutex successful` on stdout first.
  Save on the TV and pull the file instead: `screencap -p /data/local/tmp/s.png`, or cut
  everything before the `\x89PNG` header. `tools/tv.py --screenshot shots/home.png` does the save,
  the pull and the cleanup in one command, and says so when the capture is 0 bytes.
- **Gradle says "Your project path contains non-ASCII characters" on Windows** before building the
  launcher. Android's build tools reject a checkout under a localized folder such as `桌面` (Desktop).
  This was seen while installing on a Sony KM-55X85J (Google TV 12, Taiwan firmware); the TV is not
  changed when the build stops here ([#1777](https://github.com/Gathin23/cleanyourtv/issues/1777)).
  Clone the repo into an ASCII-only path such as `C:\cleanyourtv`, or map a drive letter to the
  checkout's parent directory and run the build through it:
  ```powershell
  subst X: 'C:\Users\<user>\OneDrive\桌面\sideproject'
  Set-Location X:\cleanyourtv
  powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost <tv ip>
  Set-Location C:\
  subst X: /D  # after the build is finished
  ```
- **Play Protect blocked the launcher** ("unsafe app blocked, built for an older version of Android")
  and `pm install` hung, then failed with `INSTALL_FAILED_VERIFICATION_FAILURE`, while it targeted
  SDK 29. It targets 34 now; if you built an old checkout, rebuild.
- **`pm install` fails with `INSTALL_FAILED_OLDER_SDK`** on a set older than the launcher: the APK's
  `minSdk` is a compile-time floor, so a TV on Android 7.0/7.1 (SDK 24/25) cannot take the published
  build at all ([#576](https://github.com/Gathin23/cleanyourtv/issues/576)). Read both numbers before
  changing anything: `tools/tv.py 'getprop ro.build.version.sdk'` and
  `grep minSdk launcher/app/build.gradle` (26, i.e. Android 8.0, at the time of writing).
  `launcher/build-and-install.sh` now asks the TV first and stops with both numbers, so nothing is
  built or pushed for a set that will refuse it; `build-and-install.ps1` only reports it after
  `pm install`, which fails safely with the stock launcher untouched. Lowering `minSdk` is a source
  change, not a setting, and it means maintaining your own copy: the launcher uses AndroidX and
  framework calls newer than API 25, so expect compile errors or a launcher that starts and then
  crashes. An APK produced by rebuilding the project elsewhere (an assistant, an online builder, a
  "rebuilt for API 25" zip from a forum) is not something this repository can check, and the install
  scripts only push the APK they compiled themselves: a build signed with another key does not
  install over this one (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), so uninstall the old one first if you
  go that way, and keep the stock launcher enabled until the new one really starts. On a set below
  `minSdk` the debloat and privacy half still applies, from the TV's own home screen: it is the
  launcher that cannot be installed, not the lists. If the `Failure [...]` line names
  `INSTALL_FAILED_VERIFICATION_FAILURE` instead, that is the bullet above - the firmware's installer
  verifier, not the API level.
- **App info opens half-width with a dark rectangle** from a tile's long-press menu on a TCL G07
  (`55P61B`, Android 11, [#1696](https://github.com/Gathin23/cleanyourtv/issues/1696)). The TV's
  `AppManagementActivity` renders the same way when opened by the standard
  `android.settings.APPLICATION_DETAILS_SETTINGS` intent from `adb`, with the launcher out of the
  path. To view an app's details on this firmware, open TV Settings > Apps and select the app there:
  Settings uses its `AppsActivity` with an embedded detail pane, which the owner checked renders at
  full width. Changing the launcher's intent flags would not address this firmware's standalone
  App info screen.
- **Screenshots come out empty** while Live TV, an HDMI input or Netflix is on screen, seen on TCL
  and a [Sony KDL-49W800G](https://github.com/Gathin23/cleanyourtv/issues/92). Press Home before
  `screencap`. An empty capture means nothing about the ADB connection or playback; check those
  separately.
- **Screenshots come out identical instead of empty**, seen on an Amazon Fire TV Stick 4K 2nd gen
  (AFTKRT, karat, Fire OS 8) right after a reboot: `screencap -p` kept returning the same non-empty
  PNG byte-for-byte (same MD5) across completely different resumed activities, minutes apart. It
  looked like a real (black) capture and cost real debugging time before `md5`-comparing two pulls
  gave it away. `dumpsys SurfaceFlinger --display-id` showed `no identification data` for Display 0
  at the time. Don't trust a `screencap` pull on its own here: diff it against another pull, or
  check `dumpsys activity activities | grep mResumedActivity` and real key events
  (`adb shell getevent -lt`) instead.
- **The whole UI is huge and clipped, and it is not the launcher**, seen on a Sony K-85XR70
  (`BRAVIA 4K AE2`, Android 14, China firmware,
  [#590](https://github.com/Gathin23/cleanyourtv/issues/590)). Its UI runs at 1920×1080 on a 4K panel
  by design ([#29](https://github.com/Gathin23/cleanyourtv/issues/29)), and two confirmed boots came
  up with an override density of 640 where 320 worked: everything doubles in size, tiles clip, and it
  looks like a grid bug. Read the overrides before touching the layout:
  ```sh
  tools/tv.py 'wm size'          # Physical size vs Override size
  tools/tv.py 'wm density'       # Physical density vs Override density
  tools/tv.py 'settings --user 0 get secure display_density_forced'
  ```
  An `Override size` left behind by an old resolution experiment is the first thing to explain, and an
  empty saved density is a normal Android default rather than damage. `wm density reset` fixes the
  session; on this firmware it came back as 640 at the next boot, and `wm density 320` alone did not
  stick because it left the saved setting empty. Put `settings --user 0 put secure
  display_density_forced 320` after it, reboot, and read all three lines back before believing it.
  Don't copy the number to another TV: record the three values first, and undo with `wm density reset`
  plus `settings delete secure display_density_forced`. Device note:
  [Sony K-85XR70](devices/sony-k85xr70-ae2.md).
- **Disable the stock launcher only after the new one is installed**, or the Home button lands on
  a factory test app. Launch the replacement by hand first. `set-home-activity` can report
  `Success` while HOME still resolves to the stock launcher (priority 2) until that one is disabled.
  And check which packages actually handle HOME before disabling anything by name: on the Sony
  above, `com.google.android.leanbacklauncher` has a boot receiver but no HOME activity.
  `tools/disable.sh` now does that check itself: when the list holds the app that owns Home and
  nothing else on the TV answers HOME, it skips that entry and says so (`FORCE_HOME=1` overrides),
  so applying a list before the handover no longer leaves the TV without a launcher. Apply the list
  again after the install script has made the new launcher Home to disable the old one. A factory
  test app does not count as something else: on a Xiaomi Mi TV 4A
  `com.duokan.factorytest/.FactoryLauncher` answers HOME, so the guard leaves it out, along with any
  package named `*factorytest*`, `*factory_test*` or `*factorymode*`.
- **The TV no longer boots after a list that disabled Android's core packages** (original Mi TV
  Stick, [#884](https://github.com/Gathin23/cleanyourtv/issues/884)). An agent's list took
  `com.google.android.permissioncontroller`, `com.google.android.packageinstaller`,
  `com.google.android.ext.services`, `com.google.android.ext.shared`,
  `com.google.android.modulemetadata` and the `com.google.android.overlay.modules.*` overlays, plus
  the stock launcher and setupwraith, and the stick never came back. Those are part of Android
  itself, not bloat. `tools/disable.sh`, `disable.ps1` and `check.py --fix` now refuse any list that
  names them (or `com.android.providers.*` other than calendar, contacts, user dictionary and
  blocked numbers, `com.android.systemui`, `com.android.shell`, `com.android.phone`,
  `com.android.settings`, `com.android.tv.settings` or `android`), with no switch to override it, and
  since [#1578](https://github.com/Gathin23/cleanyourtv/issues/1578) the same refusal covers the TV
  middleware: `com.cltv.mal`, `com.mediatek.tis`, `com.mediatek.tv.service` and
  `com.mediatek.tv.agent` (the bullet below). The three tools say "core Android or TV middleware
  packages" in that message, so a run that refuses one of the four reads the same way as this one.
  If a set is already stuck and still answers ADB, `pm enable` each of those packages and reboot;
  if ADB is gone too, a factory reset from the recovery menu is usually the only way back.
- **The TV restarts in a loop after a debloat**, seen once, on a Haier H50H5F (Google TV 14,
  MediaTek, [#1578](https://github.com/Gathin23/cleanyourtv/issues/1578)). What the agent disabled
  is not known. The reply on the issue names eight packages from the disabled-package list attached
  to the report: `com.cltv.mal` (TV middleware), `com.cltv.hybrid` (Live TV), the ISDB and ATSC
  tuner inputs and four Haier apps. But the six files attached match the backup PROMPT.md has the
  agent save before changing anything, and on the Haier-built [AQUA MatrixTV EE
  50](../examples/aqua-matrixtv-ee-50/debloat.txt) five of those eight are disabled from the
  factory. So the list may be the TV's state before the debloat: then it says nothing about the
  cause, and turning those eight back on would switch on what the factory left off instead of
  undoing the debloat. If the set stays up long enough between restarts, `pm enable` with the stock
  `adb` each package from the list that was applied (or run the agent's restore script), and reboot.
  Wireless debugging usually goes off at every restart and gets a new port each time it is switched
  on, so read the port from its screen or `adb mdns services`. This set never stayed up long enough
  to switch it back on, and then restarted into the recovery menu, where a factory reset turns every
  disabled package back on (nothing was uninstalled). One of those eight, `com.cltv.mal`, is on the
  list `tools/disable.sh`, `disable.ps1` and `check.py --fix` refuse (the bullet above), so a debloat
  cannot take it out again; the other seven are not guarded.
- **The TV stays on the boot logo after a list, and disabling the only Home app is enough to do
  it**, seen on a KitKat-era set ([COOP COTV-0001](https://github.com/Gathin23/cleanyourtv/issues/795):
  Realtek `rtd298x`, Android 4.4.2, SDK 19). 17 packages were disabled over Ethernet ADB, each
  answered `new state: disabled-user` and `pm list packages -d` read back all 17; the factory
  launcher `com.haiersoft.launcherproject2995hk` was among them with no replacement, because the
  launcher here needs Android 8.0. After the power cycle the set sat on the green robot and
  `INICIALIZANDO` for 20+ minutes, across a hard power cut and another off/on from the remote. The
  reporter then isolated it: disabling that one package reproduces the hang and re-enabling it boots
  the set, so the batch size and the power cut were not the cause. Two things to carry away. First,
  the screen being stuck does not mean the set is gone: this one still answered ADB on Ethernet while
  it said `INICIALIZANDO`, and `pm enable` on every package from the pre-change backup plus a reboot
  brought it back (the recovery steps are in the [device notes](devices/coop-cotv-0001.md)). Second, a
  vendor package that looks unrelated can be what the launcher runs on - here disabling `com.tsb.tv`
  stopped the launcher with "Se ha detenido la aplicación lancherProject" instead of breaking the
  boot - which is why a list goes on in batches with a reboot and a look at the screen between them,
  on a set that cannot take the launcher especially: leave the stock launcher alone, and keep ADB on
  Ethernet (KitKat has no network-debugging toggle, and Wi-Fi closed 5555 on this one).
  `tools/disable.sh` skips Home's owner when it can name it; since
  [#795](https://github.com/Gathin23/cleanyourtv/issues/795) it asks `dumpsys activity` when `cmd
  package` is missing (Android 6 and older, where that command does not exist) and warns when neither
  answers. That warning means the guard did not run, not that the list is safe. Without ADB and
  without a vendor recovery image, a set stuck this way is still out of reach.
- **China-market Xiaomi rejects a default Home despite `set-home-activity` reporting success**,
  verified on MiTV-AETP0 (dofus), Android 9, firmware 22.9.2.851: the framework filters third-party
  HOME candidates by a package-name allowlist. Disabling the stock launcher then selects
  `FallbackHome`. An opt-in application-ID build worked with the stock launcher still enabled;
  see the [device notes and rollback steps](devices/xiaomi-mitv-aetp0-dofus.md). This is different
  from a missing default-home picker or a remote that emits ALL_APPS.
- **China-market Xiaomi refuses the new launcher as Home outright**, seen on a Laser Cinema 2 4K
  (`nemo`, Android 9, firmware `2.1.3.4134`): `cmd package set-home-activity` answers `cannot be
  home on user 0`, while the APK does declare `MAIN`/`HOME`/`DEFAULT` and is installed and enabled.
  This firmware's `PackageManagerService.applyPostResolutionFilter` removes every HOME candidate that
  is not `FLAG_SYSTEM` when `ro.formovie.launcher_type` is not `AndroidTV` (it reads `Patchwall`
  here, and a shell `setprop` is refused), so this is a flag test rather than the package-name
  allowlist above and the alternate application ID it accepted does not help. `query-activities -c
  android.intent.category.HOME` lists only PatchWall and `FallbackHome`. Keep the stock launcher
  enabled - disabling it leaves only `FallbackHome` - and see the
  [device notes](devices/xiaomi-nemo-laser-cinema2.md) for the queries and the firmware code.
- **The new launcher still doesn't own Home** after the stock one is disabled, seen on Google TV
  (Xiaomi MiTV-MOOR2). Another system app answers HOME: `com.google.android.tungsten.setupwraith`,
  with `.MainActivity` at intent-filter priority 4 and `.RecoveryActivity` at priority 1. You
  cannot outrank it, and the obvious workarounds don't help: Android ignores `android:priority` on
  HOME filters for apps that aren't on the system image, `cmd package set-home-activity` prints
  `Success` without changing anything (not even after a reboot), and `pm disable-user` on one of
  setupwraith's components is refused with a SecurityException. Disabling the whole
  `com.google.android.tungsten.setupwraith` package is what frees the slot. It is the first-run
  setup wizard, so everyday use is unaffected, but re-enable it before a factory reset - list it in
  your `debloat.txt` and `tools/restore.sh` does that for you. Measured again on a Google TV
  Streamer (`kirkwood`, `UTTK.260317.003`): a launcher built with `android:priority="3"` on its HOME
  filter (`aapt2 dump xmltree` finds it in the installed APK) still has no `mPriority` line in
  `dumpsys package`, and `cmd role get-role-holders android.app.role.HOME` names the new launcher
  while the HOME key lands on `setupwraith/.RecoveryActivity` anyway. `cmd package resolve-activity`
  and `query-activities` answer with the resolver table's winner, which is not necessarily the
  activity that takes focus: `dumpsys activity activities | grep mFocusedApp` is the ground truth,
  and it is what to check before saying the takeover worked.
- **Home works when tapped but does nothing when pressed firmly**, after the stock launcher is
  disabled, seen on a Philips 65OLED804/12 (TPM191E, Android TV 12, `STT2.230526.001`) with a slow
  third-party remote whose presses last 430 to 550 ms. Android turns a Home press held past 400 ms
  into its long-press action, and `dumpsys window policy` shows it is
  `mLongPressOnHomeBehavior=LONG_PRESS_HOME_ALL_APPS`. `PhoneWindowManager.launchAllAppsAction` sends
  `ACTION_ALL_APPS` to the system-image Home only (`MATCH_SYSTEM_ONLY`), which with the stock
  launcher disabled is `com.android.tv.settings/.system.FallbackHome`, and nothing answers it there:
  logcat shows `START u0 {act=android.intent.action.ALL_APPS pkg=com.android.tv.settings}` 400 ms
  after each key down and the TV stays where it was. Before the debloat the stock launcher answered
  with its app grid, so it looked like Home. An injected `input keyevent --longpress KEYCODE_HOME`
  reproduces it and a plain `input keyevent KEYCODE_HOME` does not. `settings put secure
  long_press_timeout 800` does not move the limit, not even after a reboot. `home_long_press_home`
  in `config.xml` or a theme, with `HOME_KEY_SERVICE=1` at install, makes `HomeKeyService` take every
  Home press and open Home on release. That needs the helper to filter keys, so it costs the held-key
  repeat described under [the key-repeat section](#a-key-helper-is-bound-and-a-held-remote-button-stopped-repeating).
  Confirmed on this set: with `HomeKeyService` bound, Volume Down held for 3 s gave one step in
  `dumpsys audio`; unbound, a 1.07 s hold gave 14 repeats 50 ms apart. Single presses step once either
  way, also through HDMI ARC to a home cinema system. Choose between a firm Home press and a held volume
  ramp; this set cannot have both. A 50PUS7354 on the same board and firmware does the same with its original
  Philips remote, so a slow remote is not needed: firm presses sent `ALL_APPS` to FallbackHome there
  too, and `home_long_press_home` fixed it.
- **The TV drops off Wi-Fi for good the moment setupwraith is disabled**, seen on Google TV 14 (Sony
  BRAVIA 9 II, Philips R8G, Skyworth SWTV-24NA-4K NF, Xiaomi TV Box S 3rd gen MiTV-AFMU0) and on a TCL
  G07 on Google TV 11 (build AR17; on AR20 the network belonged to `android.uid.system`). A Wi-Fi
  network saved during the first-run wizard belongs to setupwraith, and Android deletes an app's saved
  networks when the app is disabled. The TV is fine but offline, so ADB and the phone remote stop
  working, and a restart doesn't bring the network back. Check before disabling it: `tools/tv.py "dumpsys wifi" | grep -o 'cname=[^ ]*'`.
  If it names setupwraith, forget the network and join it again in Settings > Network & Internet (it
  then belongs to `android.uid.system`), or use Ethernet. `tools/disable.sh` skips setupwraith while it
  owns the network (`FORCE_SETUPWRAITH=1` overrides). Already offline? Rejoin from the TV with its own
  remote or a USB keyboard, then turn Wireless debugging back on if it was in use. Reconnecting is not
  enough; forget the network first (Xiaomi MiTV-MOSR4, where this also switched USB debugging off).
- **A reboot on setupwraith's recovery screen factory-reset the TV** (Xiaomi MiTV-MOSR4). Disable
  setupwraith before launcherx. If `mFocusedApp` shows `.RecoveryActivity`, re-enable launcherx
  first and do not reboot. [Notes](devices/xiaomi-mitv-mosr4.md).
- **Home worked for days, then went black after a power cut** (Acer R4G and Panasonic 4KTV-3MUP,
  MOKA `R4_GTV`, Google TV 14): the firmware re-enables setupwraith at every cold boot and its
  `.RecoveryActivity` takes Home. `logcat | grep RecoveryActivity` shows it starting several times a
  second. Re-enable launcherx as above. [Notes](devices/acer-r4g.md).
- **Black screen at every boot after the launcher change, LED on, remote dead** (Vision
  2024VISION43STV, CVTE, Android 13: [its list](../examples/vision-2024vision43stv/debloat.txt) has the
  details). With file-based encryption (`getprop ro.crypto.type` says `file`) Android starts Home before
  the first unlock, and only a direct-boot-aware activity can start then. Most firmwares have a
  `FallbackHome` that stands in until the unlock; some, like this CVTE board, do not, and then boot
  never completes. This repo's launcher is only partly direct-boot-aware: `HomeKeyService` declares
  the flag, so Android binds it while the user is still locked, and `GuardRevival` declares it too
  (neither that listener nor [the standby opt-in](#the-power-key-does-not-wake-the-tv-while-the-key-helper-is-bound)
  is on in an ordinary install). `MainActivity`, the activity that
  answers HOME, does not declare it. The second command below therefore prints
  `PARTIALLY_DIRECT_BOOT_AWARE` for this launcher and cannot say whether Home exists before the
  unlock; on a set with no `FallbackHome`, the first command is the one that decides. Check both
  before disabling the stock launcher:
  ```sh
  tools/tv.py 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
  tools/tv.py 'dumpsys package <new.launcher.package> | grep -o "[A-Z_]*DIRECT_BOOT_AWARE"'
  ```
  No `FallbackHome` in the first and nothing from the second: keep the stock launcher enabled and set
  Home with `cmd package set-home-activity` only. That line is not proof the handover happened: on a set
  whose stock launcher declares a HOME priority it can print `Success` and change nothing, with the Home
  role already naming the new launcher while Home still opens the stock one, so read the resolver instead
  (`tools/tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'`).
  When it keeps naming the stock launcher, the set is debloat-only until a boot with the stock launcher
  disabled has been tried on that exact board - which does not mean the launcher is unusable there: it
  still opens from the app list and runs, with Home left where it is. The two checks have been run on the
  Thomson 40FG2S14W (Expressluck `nagata`, Android TV 14 `UKNV.260317.001`) and both fallbacks are there,
  but that boot has not been tried ([notes](devices/thomson-40fg2s14w.md),
  [#1350](https://github.com/Gathin23/cleanyourtv/issues/1350)).
- **Stuck on the boot animation after the launcher change, ADB gone** (JVC LT-43N7115, CVTE
  `TP.SK706S.PC822`, Android 11, [#741](https://github.com/Gathin23/cleanyourtv/discussions/741)): the stock
  launcher `com.cvte.tv.launcher.space` was disabled and a remote-key helper enabled before one reboot.
  Nothing was uninstalled, so try in this order: wait ten minutes once; boot to safe mode (hold Power on
  the remote during the animation on many sets), which turns off added apps, then re-enable the stock
  launcher under Settings > Apps > show system apps; keep retrying `adb connect <ip>:5555` for a couple of
  minutes right after power-on and run `pm enable com.cvte.tv.launcher.space` and
  `settings put secure enabled_accessibility_services ""` if it answers (a phone hotspot with the old
  network's name and password stands in for a missing router); wipe the cache partition from the recovery
  menu. A factory reset also works but erases everything, so it comes last. The prompt now keeps the stock
  launcher enabled on CVTE boards without a tested list.
- **Accounts & profiles is empty after the stock launcher is disabled** (TCL 75C845X2, `G08_4K_GB`,
  Google TV 14, [#304](https://github.com/Gathin23/cleanyourtv/discussions/304)). The accounts are still
  there (`dumpsys account` lists them); the page lives in `com.google.android.apps.tv.launcherx`, and while
  launcherx is enabled it takes Home back (`set-home-activity` prints `Success` and changes nothing, and
  its home activity can't be disabled from the shell). To switch profile: `pm enable --user 0
  com.google.android.apps.tv.launcherx`, switch in Settings, then disable it again and set Home last.
- **Voice search for a song or video says "Google Assistant is having trouble processing your request"**
  once launcherx is disabled, while app commands ("open YouTube") still work. Seen on an onn 4K Streaming
  Box (`YOC`, Google TV 14, `URO4.260304.011.B1.15051976`). Google TV shows those results in its own home
  screen: the Assistant sends `VIEW https://tv.google.com/...` to launcherx
  (`.gateway.LauncherDeepLink`, then `.home.HomeActivity` lists the results, and picking one opens YouTube
  by package). With launcherx disabled there is nothing to open, and logcat shows `Failed to execute
  provider.OPEN with status: UNKNOWN_ERROR` within 100 ms. Two things make it look like something else:
  right after launcherx has run, the Assistant keeps serving results for a while even when it is disabled
  again, and the failure returns only after a reboot; and nothing in the debloat list matters - with every
  other package re-enabled it fails the same way. Apps only mode (Settings > Accounts & sign-in) is no way
  out: it switches the Assistant off. What works: leave launcherx enabled (it is then Home, see the entry
  above) and open this launcher from its app row, where voice search works normally because the results
  screen comes up over it; or search inside YouTube with its on-screen mic. The Projectivy community found
  the same (voice results "need the launcher X search results page").
- **Every mic press answers "Sorry, not available in this profile yet"** once launcherx is disabled, seen on a
  TCL 75Q7CXB (G08, Google TV 14, `UTT2.250416.001 AU02`) with this launcher as Home. The Assistant opens and
  hears the press, then gives up before listening: logcat has `katniss_interactor_KatnissVoiceInteractionUtilsImpl:
  Assistant is not enabled in LauncherX kids mode or privacy mode. Will punt.` It reads that mode from launcherx,
  so with launcherx disabled it cannot tell and refuses, whatever profile is signed in. Nothing in the debloat
  list is involved; `katniss` and `com.google.android.tv.remote.service` were both enabled. What works is the same
  as in the entry above: launcherx enabled, with this launcher opened from its row or reached through
  `stock_home_redirect` (see `examples/tcl-q7c-google-tv`, "Working alternative").
- **Pressing the mic shows no listening indicator at all**, seen on the same 75Q7CXB
  before the entry above: each press logs `AtvRemote.BleRemoteDevice: HTT session initiated`, so the remote does
  send it, then `AtvRemote.AppOpsDisclosureService: com.google.android.katniss doesn't have access to the
  on-remote mic` and a `SensorPrivacyService` line saying katniss started using sensor 1 (the microphone).
  Switching on Microphone access in the TV's Settings (Privacy > Microphone) brought the button back, and it
  stayed after a reboot. Don't judge it from `dumpsys sensor_privacy`: that set read `state_type=1` for the
  microphone both before and after, while the button failed and while it worked.
- **The remote's mic button does nothing, or opens the Assistant and it never hears you**, seen on a
  Xiaomi TV Box S 3rd gen (`MiTV-AFMU0`, `twilight`, Google TV 14, `V816.0.7.0.UZFAABX`). Two
  packages have to stay enabled, and re-enabling only one of them is what makes this hard to
  diagnose: `com.google.android.katniss` is the Assistant itself, and
  `com.google.android.tv.remote.service` is the Google TV bridge for the physical remote, which
  carries the remote's Bluetooth microphone. With both disabled the button does nothing. With only
  `katniss` back the Assistant opens, takes `AudioRecord` (`inputSource 6`, VOICE_RECOGNITION) and
  records silence, because the audio never leaves the remote. `pm enable` on both restores the
  button without a reboot (run `pm enable` once per package). To tell the two failures apart: `dumpsys package
  com.google.android.katniss | grep enabled` and the same for `tv.remote.service` (`enabled=3` is
  disabled), then `logcat -d | grep AudioRecord` right after pressing the button: a session that
  opens and closes with no recognition is the missing bridge, not a broken Assistant. The AFMU0
  list keeps both now; the lists that still disable `com.google.android.tv.remote.service` include
  `liberton-ltv-24h01at`, `panavox-google-tv`, `rca-ai-pont`, `sharp-aquos-tve21f`,
  `sony-bravia-4k-gb-us`, `sony-xbr-65x900f`, `sony-xbr-x900e`, `tcl-32s5k`, `tcl-40s5400a`,
  `tcl-55s470g`, `tcl-65ep640`, `tcl-beyondtv2-android-11`, `tcl-g09-google-tv`,
  `xgimi-horizon-s-pro`, `xgimi-mogo-3-pro`, `xgimi-mogo-4`, `xiaomi-mi-box-3` and
  `xiaomi-mi-box-s`, and `grep -l '^com.google.android.tv.remote.service' examples/*/debloat.txt`
  prints the current set. Those TVs haven't been tested for this, but any of them whose remote has a
  microphone may show the same symptom. The G10 list has since been tested from the other side: on a
  43T6C (G10, Realtek, build `STT2.230203.001`) a mic press with `com.google.android.tv.remote.service`
  disabled logs `Unable to start service Intent { act=...AtvSearchAssistantService ... } U=0: not found`
  — that service lives in the package — and re-enabling only it, with every other line applied, brought
  the button back, so `tcl-g10-google-tv` keeps it now.
- **The remote's voice button stops working after the package that owns Home is installed or updated,
  and comes back after one reboot** ([#1869](https://github.com/Gathin23/cleanyourtv/issues/1869), a TCL
  BeyondTV2, board `rtd285o`, Android TV 11, `PPR1.180610.011`/`AR05`, the same model and build as
  `examples/tcl-beyondtv2-android-11/`). The button is the firmware's on this build, not the Assistant's:
  `KEYCODE_SEARCH` (84) sends the system to the activity that owns Home, which clicks its own voice
  button, so only the `ACTION_ASSIST` fired there records - a background one is refused with
  `Recording audio is not allowed for this invocation on R.`. Installing or updating that package, a
  rebuild over a different signing key included, leaves that routing stale: every press is denied until
  the TV reboots, and then 3 of 3 presses recorded with a 16 kHz mono stream and no denials. Reboot once
  before judging the button, and take installing the launcher again as no fix - changing which launcher
  owns Home is not what breaks it, `cmd package set-home-activity` in both directions recorded normally.
  If the button was dead before that too, check `com.google.android.katniss` first: the lists that turn
  voice off, `tcl-beyondtv2-android-11` among them, disable it on purpose. `input keyevent 84` goes
  through this firmware path rather than the launcher's (see "Accessibility helper enabled but the remote
  button does nothing" below), so the physical button is the only test.
- **Casting from a phone fails with "Untrusted device" after setupwraith is disabled**, seen on
  Chromecast with Google TV HD (`boreal`) on Google TV 14 build UTTC.250917.004, the build the
  `examples/google-chromecast-gtv-4k/` list was made on. YouTube on the phone says "Untrusted device.
  ... couldn't be verified", and logcat repeats `cast_ClientAuthCredsProvisioning: Loading factory
  credentials for 1P device`, `Failed to find provider info for ...CastInfoProvider` and
  `cast_shell: No private key, hence empty signature`. The Cast receiver
  (`com.google.android.apps.mediashell`) loads its factory device credentials from a content provider
  inside setupwraith (`...tv.setupwraith.libs.castinfo.CastInfoProvider`), so disabling the wizard
  takes the credentials with it. Re-enable it and within a minute the log shows
  `SetupWraith|CastInfoProvider: querying cast infos` and `Created singleton instance of
  ClientAuthSigner`, and casting works. You cannot keep the provider and give up only Home:
  `pm disable-user` on `setupwraith/.RecoveryActivity` is refused with `SecurityException: Shell
  cannot change component state`. So either debloat without setupwraith and launcherx (for the 4K:
  [`debloat-keep-cast.txt`](../examples/google-chromecast-gtv-4k/debloat-keep-cast.txt)), or re-enable
  setupwraith after each boot, wait for `ClientAuthSigner`, then disable it again: the receiver keeps
  the credentials in memory, so casting survives until the next reboot - and only in that process.
  `am force-stop com.google.android.apps.mediashell` followed by the receiver coming back prints
  `Retry loading factory credentials` and `No private key, hence empty signature` again, so anything
  that restarts it (a crash, a force-stop, an update) needs the same sequence. Re-enabling the
  wizard is not always enough after a reboot: on a Chromecast with Google TV 4K (`sabrina`,
  UTTC.250917.004) no `ClientAuthSigner` line came within 3 minutes and phones did not even list the
  device. Restart the receiver while setupwraith is enabled instead: `am force-stop
  com.google.android.apps.mediashell`, then `am start-foreground-service -n
  com.google.android.apps.mediashell/.CastReceiverService` (after a force-stop nothing starts it
  again), and the signer line and `MdnsAppFilter: Done announcing.` follow within seconds. Reported on Google's
  own Chromecast hardware (`boreal`) and, with the same log lines, on a Google TV Streamer
  (`kirkwood`, `UTTK.260317.003`), where the `CastInfoProvider` also lives inside the setupwraith
  APK; the Xiaomi Mi Box S 2nd gen list says casting from a Pixel still works there with
  setupwraith off.
- **Casting disappears although the Cast receiver is still running**, seen on a TCL 75QM751G (QM7,
  Google TV 14) after its list, and the same "alive is not discoverable" conclusion on a TCL
  BeyondTV2 (Android TV 9, R851T02 V660) in the same thread. Phones and a PC stop seeing the TV,
  `com.google.android.apps.mediashell` is up and listening on 8008/8009, and there is no
  `No private key` or `CastInfoProvider` line — so this is not the setupwraith case above, and
  re-enabling setupwraith does not help. Check discovery instead of the process:
  `tools/tv.py "logcat -d -s cast_shell | grep -E 'Done announcing|responses sent'"` and
  `tools/tv.py "dumpsys activity service com.google.android.apps.mediashell | grep WargService"`.
  A receiver that is advertising logs `MdnsAppFilter: Done announcing.`,
  `responses sent in 60 seconds: N` and `Gcm.Register.Success=2`, with `WargService` up. If those are
  missing while the process lives, the receiver is hung: the QM7 report saw its connections sitting
  in `CLOSE_WAIT` on 8009 with unread data. `tools/tv.py "am force-stop
  com.google.android.apps.mediashell"` restarts it (the TV starts it again on demand), and
  advertising returned within seconds, without a reboot. Device notes:
  [TCL 75QM751G](devices/tcl-75qm751g-qm7.md).
  On Android 14 the receiver may be frozen rather than hung: with launcherx disabled nothing keeps it
  above the cached state, and the cached-app freezer stops it a few minutes after each wake. A TCL Smart TV Pro
  (`G08_4K_US`, `UTT2.250416.001 AU16`) showed `isFrozen=true` and `curProcState=19` in
  `tools/tv.py "dumpsys activity processes | grep -A3 mediashell"`, and `ActivityManager: freezing <pid>
  com.google.android.apps.mediashell` in `logcat` every two or three minutes, with the same slow and
  missing discovery and, once, the `CLOSE_WAIT` sockets on 8009. A force-stop only helps until the next
  freeze. What lasts is an app that is always running binding the receiver: there, the Home app binding
  `com.google.android.apps.mediashell/.CastReceiverService` (action `com.google.cast.action.BIND`, with
  the package in its `<queries>`) moved it to `vis` with `(service)` in
  `tools/tv.py "dumpsys activity oom | grep -A1 mediashell"`, and it has not frozen since. This repo's
  launcher does not do that today. That the freezing causes the lost discovery is inferred from the two
  showing up together, not proven: if `isFrozen=true` is not there, the receiver is hung, as above.
  [The G08 US notes](../examples/tcl-g08-us-google-tv/README.md#fourth-unit-cast-and-the-freezer).
- **The TV drops off Wi-Fi every minute right after the install**, seen on Google TV 14 (Xiaomi
  MiTV-MZTU0): the same setupwraith `.RecoveryActivity` took Home and system_server crash-looped
  on it. The install script now starts the new launcher before disabling the stock one, sets the
  home preference last (Android drops it whenever the set of HOME apps changes), then checks where
  Home resolves and re-enables the stock launcher if it is not the new one. Disable setupwraith as
  above. To confirm it is this bug: `logcat -b crash` shows `FATAL EXCEPTION IN SYSTEM PROCESS:
  android.display`, and the main log shows `setupwraith/.RecoveryActivity` being started every
  200 ms or so. On a Google TV Streamer (`kirkwood`, `UTTK.260317.003`) the same loop came with its
  own signature - `IllegalArgumentException: positionChildAt: container=Task=... is not a child of
  container=Task=1 current parent=null` at `WindowContainer.positionChildAt` /
  `Task.moveToBack` / `TransitionController.tryStartCollectFromQueue` - and repeated roughly every
  47 s, each cycle taking setupwraith and SystemUI down with `DeadSystemException` too. If you're
  already in the loop:
  `tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'`.
- **`theme.sh` fails with "couldn't create file: Permission denied"**, seen on TCL G07 (Android 11): the
  shell is not in the group that may write `Android/data`. `theme.sh` stages the files in
  `/data/local/tmp` and writes them through the launcher's `ThemeProvider`, which creates them as the
  launcher; `cp` is only the fallback for a launcher installed before that provider existed: run
  `launcher/build-and-install.sh` once, then push the theme again.
- **A theme push prints "is on the TV" and nothing changes**, seen on Android 14: the shell *can* write
  `Android/data` there, so `cp` succeeded - but the file it created belonged to the shell (`ls -ln` shows
  `2000 1078` instead of the launcher's uid) and the launcher logged `W TvHome.Config: ignoring
  .../config.json: open failed: EACCES (Permission denied)`. Overwriting a file the launcher wrote itself
  keeps its owner, so this only bit files that did not exist yet: it looked fine on a set that had been
  themed before and did nothing on a fresh install or right after a reinstall. `theme.sh` now removes the
  shell's copy and writes through the `ThemeProvider` first (the provider cannot truncate a file the shell
  owns); on an older checkout, pushing the theme again after `launcher/build-and-install.sh` has written
  the files once also fixes the ownership.
- **Google TV comes back after a few remote presses**, seen on XGIMI (MoGo 3 Pro, Google TV 14). The
  remote's app buttons (YouTube, Netflix, Prime Video), the programmable button, the profile button and power
  all send F keys that the system hands to `com.xgimi.xrmservice/.XgimiGlobalKeyBroadcast` as global keys
  (`tools/tv.py 'dumpsys window' | grep -A40 mKeyMapping` lists them). That receiver runs
  `pm enable com.google.android.apps.tv.launcherx` on every one of them, so the stock launcher takes Home back.
  Keys listed in `app_keys` are taken by the key services before global key dispatch, so XGIMI's receiver never
  sees them: `examples/xgimi-mogo-3-pro` lists the app buttons. Power (F11) can't be taken over, because its
  sleep and power-menu handling needs system permissions. For that one, run with `HOME_KEY_SERVICE=1` and set
  `stock_home_redirect` to `com.google.android.apps.tv.launcherx`: if it comes back, its home screen is sent
  straight back here, and `tools/disable.sh` with the list disables it again.
- **No on-screen keyboard** after switching launchers, seen on Xiaomi: the default input method
  pointed at a Baidu keyboard that isn't installed. Fix:
  `tools/tv.py 'ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME'`
- **Third-party launchers** often charge for basics like a custom wallpaper. That's why this repo
  has its own.
- **Wrong time zone, clock off by 30 or 60 minutes**, seen on Xiaomi in Iran: the TV picked a
  neighbouring zone, and older firmware ships time zone data from before Iran dropped daylight
  saving in 2022 (on Android 10 and newer, check with
  `tools/tv.py 'cat /apex/com.android.tzdata/etc/tz/tz_version'`). Set the zone yourself:
  `tools/tv.py 'settings put global auto_time_zone 0; cmd alarm set-timezone Asia/Tehran'`.
  If the clock then jumps an hour ahead in spring, a fixed offset keeps it right:
  `tools/tv.py 'cmd alarm set-timezone GMT+03:30'`. The same applies to any country whose rules
  changed after the firmware was built.
- **The network pill says "Wi-Fi" instead of the network name** after `pm clear com.gathin.tvhome`
  or a reinstall from scratch: clearing the launcher's data also drops the location permission
  Android 10 needs to read the SSID. `pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION`
  (the install script does this).
- **Home is "Select a Home app"** after `tools/restore.sh` followed by re-applying a list: disabling a
  package that also claims HOME (PatchWall on Xiaomi) makes Android drop the default. The install
  script now picks the known stock launcher among the candidates by itself; otherwise choose one on
  the TV or pass `STOCK_LAUNCHER=<package>`.
- **`tools/restore.sh` leaves the packages an earlier batch disabled off, after the TV changed its
  address mid-cleanup.** The recovery record is one file per list and address
  (`~/.cleanyourtv/recovery-<address>-<list hash>.json`), so a new DHCP lease - a router restart, or
  the Wi-Fi forget and re-join the setupwraith step asks for itself - makes the next
  `tools/disable.sh` run find no record and write a fresh one from a TV that is already partly
  cleaned: every package the earlier batches disabled is in it as "already disabled, left alone by
  undo", and undo at the new address leaves them off. `tools/disable.sh` now says so on stderr, names
  the record under the old address and the packages the two disagree about. Run `tools/restore.sh` at
  the old address first, or move that record to the new address's name once you are sure it is this
  set and not a second one cleaned with the same list ([#2139](https://github.com/Gathin23/cleanyourtv/issues/2139)).
- **Stremio fails every stream with `ConnectException: Failed to connect to /127.0.0.1:11470`**,
  and only Force stop brings it back until the next reboot. This is not the debloat. It is a Stremio
  regression in 1.11 ([stremio-bugs#2866](https://github.com/Stremio/stremio-bugs/issues/2866)):
  `startServer()` only runs once the lifecycle is at least STARTED, so a process Android woke in the
  background never starts the local server, and opening the app reuses that process. On a TV the
  waker is Stremio's own `BootReceiver` refreshing the home-screen channels, so it happens on every
  boot. Leaving the app and reopening does not help (same pid); any fresh process does, `am kill`
  included. Keeping the boot-woken process from sticking around fixes it for good:
  `tools/tv.py 'cmd appops set com.stremio.one RUN_ANY_IN_BACKGROUND ignore; cmd appops write-settings'`.
  The second command is not optional: app-ops are written to disk lazily, and on a Vestel shandao set
  the op was back to `allow` after the next reboot without it, which looks exactly like the
  workaround not working. An app started in the foreground is unaffected, and the side effect, that
  the home-screen channels only refresh while Stremio is open, costs nothing once the recommendation
  rows are disabled anyway.
- **An app you keep runs in the background after every boot, or stays in memory with a foreground service**,
  seen with streaming apps on a Noblex NoblexTV (`shinjuku`, Android TV 11, `RTO9.230812.001`) and a
  Tornado 65UA1400E (Android TV 11, `RTM5.220609.106`), and with TvSettings on a
  [TCL G09](../examples/tcl-g09-google-tv/README.md) (Google TV 12). On Android 9 and newer the package can
  stay installed and still be kept from running in the background, the same fix as for Stremio above and
  saved with `write-settings` for the same reason:
  `tools/tv.py 'cmd appops set <package> RUN_ANY_IN_BACKGROUND ignore; cmd appops write-settings'`. The app
  still opens and plays on screen. Once it leaves the screen, Android holds back its jobs and alarms, takes
  away its foreground services and, if the app targets Android 8 or newer, refuses its background service
  starts. Its home-screen channels and push features then only update while it is open, and a music app
  loses the foreground service that keeps it playing once you leave it. The op does not stop its boot
  receiver, so the app still starts at every boot and then drops to cached, and one that opens its own
  screen at boot, like the waipu app on the [waipu.tv Stick](devices/waipu-tv-stick.md), still does.
  Android accepts the op without an error but still runs the jobs and alarms of a package whose uid is
  below 10000 (`tools/tv.py 'pm list packages -U <package>'`), a persistent one
  (`tools/tv.py 'dumpsys package <package>' | grep PERSISTENT`) and one on the Doze allowlist
  (`tools/tv.py 'dumpsys deviceidle whitelist'`, which only reads the list when nothing follows it), so
  check those first. Undo:
  `tools/tv.py 'cmd appops set <package> RUN_ANY_IN_BACKGROUND allow; cmd appops write-settings'`.
  When the cost is a foreground service the app has no reason to hold, as on the G09
  (`tools/tv.py 'dumpsys activity services <package>'` shows `isForeground=true` long after the app was
  last opened), take away only the foreground:
  `tools/tv.py 'cmd appops set <package> START_FOREGROUND ignore; cmd appops write-settings'`. Android only
  reads this op when the app asks for the foreground, so a service that is already in the foreground stays
  there until it next starts: reboot with `tools/tv.py --reboot`, then check
  `tools/tv.py 'dumpsys activity services <package>'` again, which should no longer show `isForeground=true`.
  From then on the service runs as an ordinary one that Android can reclaim. Use `ignore`, never `deny`:
  with `deny`, the app's request for the foreground throws a `SecurityException`, which crashes an app that
  does not expect it. Leave the remote, keyboard, cast and input services alone. Undo:
  `tools/tv.py 'cmd appops set <package> START_FOREGROUND allow; cmd appops write-settings'`.
  App standby buckets (`am set-standby-bucket`) do nothing useful on a TV: whatever you set, Android keeps
  a system app with a uid below 10000 at `exempted` and one with no phone-style `LAUNCHER` activity no
  lower than `active`, and it treats a TV on mains power as charging
  (`tools/tv.py 'dumpsys alarm' | grep Parole` reads `App Standby Parole: true` on a Chromecast with Google
  TV 4K, `sabrina`, `UTTC.250917.004`), which lifts the bucket limits on alarms and on every job outside
  `restricted`, a bucket the app leaves the next time it is opened.
  `tools/tv.py 'am get-standby-bucket <package>'` only reads the bucket (5 is exempted, 10 active), and
  `tools/tv.py 'am set-standby-bucket <package> active'` puts back one that was set.
- **Every package in a batch is enabled again after the reboot that follows it**, seen on a Hisense
  HiSmartTV A4 (xiaoyushan, Android TV 11, `RTT2.221108.001` / `00.00.01.18`). It looks like firmware
  that resets the list at every boot, but it is the timing of the reboot. Android writes package state
  to disk about ten seconds after a change (AOSP's `WRITE_SETTINGS_DELAY`). A `tools/tv.py reboot`
  sent straight after `tools/disable.sh` came before that write, so the TV booted with the old state.
  `dumpsys package <name>` showed `enabled=0` (default) for every package, and `pm list packages -d`
  was empty. The same package disabled, given 12 seconds, then rebooted stayed at `enabled=3`
  (disabled-user), and so did every batch applied that way. Wait 15 seconds after the last `pm` command
  before rebooting, the same wait the tested lists already describe between batches. It is the
  package-state counterpart of the lazy app-ops write above.
- **A firmware update quietly undoes the cleanup.** The update installs over the cleaned-up system, so
  every listed package is enabled again, Home goes back to the stock launcher and new preloads
  appear that no list mentions. `tools/venv/bin/python -I tools/check.py <list>` reports the first
  two and exits 1; `--fix` disables them again and puts the launcher back on Home. New preloads it
  cannot know about, so after a big update compare `tools/tv.py 'pm list packages'` with the list
  once by hand. On a TV whose list disables the updater, this only happens when you install an
  update on purpose: enable the updater, update, `check.py --fix`, disable it again.
- **The TV opens Live TV by itself at every power-on**, often on a "please scan channels" screen, with
  the new launcher already Home: seen on an ERGO DVB92YC (Changhong AI PONT, `redwood`, MT5867,
  Android TV 14, `UKNV.260514.001`). `logcat -b events -d | grep wm_set_resumed_activity` after a
  reboot shows `com.mediatek.wwtv.tvcenter/.nav.TurnkeyUiMainActivity` resumed about 12 s in, a
  Homwee "hotel" `ProtocolActivity` on top, then Live TV again at about 25 s. It is started by
  `com.mediatek.wwtv.tvcenter/.util.BootBroadcastReceiver` (BOOT_COMPLETED, priority 1000); on
  Android 14 `pm disable` of that one component is refused for the shell, and neither the Live TV menu
  nor Android's Settings has a power-on-input option. Disabling the whole Live TV app would cost the
  HDMI inputs. Set `power_on_home` (`config.xml` or a theme) with `HOME_KEY_SERVICE=1`: until the first
  key press after boot or screen-on, `HomeKeyService` sends that Live TV window back to Home. Ask the
  person first; someone who watches broadcast TV may want the opposite, `boot_to_live_tv`.
- **A third-party screensaver never shows**, seen on a TCL 65C745 (`G05_4K_GB`, Google TV 11, `AR2101`):
  the TV starts dreaming after the timeout and wakes up in the same millisecond, so nothing appears.
  `logcat` shows `DreamController: Unable to bind dream service` right after
  `[TclAppBoot] ... calleeComponent = 'com.neilturner.aerialviews/.ui.screensaver.DreamActivity', bootType =
  'bind_service', isAutoStart = 'true', isAllow = 'false', reason = 'callee_does't_have_OP_AUTO_START_permission.'`:
  the firmware will not start the screensaver app's process to bind it. Allowing the op for that app only
  and saving it (`tools/tv.py 'appops set <package> APP_AUTO_START allow; appops write-settings'`) fixed it,
  and it still bound after a reboot. Safety Guard (`com.tcl.guard`, disabled by the TCL lists here) is not
  the cause: enabling it again changed nothing. The launcher's own Clock screensaver is not affected: Home
  is already running, so binding `ClockDream` starts nothing (checked on the same TV, with the launcher's
  `APP_AUTO_START` at `ignore`). To test without waiting for the timeout:
  `tools/tv.py 'am start -n com.android.systemui/.Somnambulator'`, then
  `tools/tv.py 'dumpsys dreams | grep mCurrentDreamName'`. The key helpers hit the same denial: see
  [Accessibility helper enabled but the remote button does nothing](#accessibility-helper-enabled-but-the-remote-button-does-nothing).
- **The TV wakes from standby back on the last HDMI input, and `power_on_home` takes about 5 s or misses it**
  (Philips 65OLED706, Android TV 11): the firmware only resumes the `org.droidtv.playtv` window it left, which
  sends its window event once the input has a picture, if at all. Set `power_on_home_always` too: Home comes
  up about a second after the screen turns on, whatever is in front (an input, Live TV or an app).

## Accessibility helper enabled but the remote button does nothing

The optional `HomeKeyService` and `InputKeyService` only filter remote keys once Android has bound to
them. The installer's `enabled` message means the setting was written, not that the service is bound.
Read the state without changing anything:

```sh
tools/tv.py 'am get-current-user'
tools/tv.py 'settings --user current get secure enabled_accessibility_services'
tools/tv.py 'dumpsys accessibility'
```

| Helper | Component in the enabled-services list | Key it filters |
| --- | --- | --- |
| Home button | `com.gathin.tvhome/.HomeKeyService` | `KEYCODE_ALL_APPS` (an ordinary `KEYCODE_HOME` only with `home_long_press_home`, `config.xml` or a theme) and `KEYCODE_SETTINGS`, plus the keys in `settings_key_keycodes` and `app_keys` (`config.xml` or a theme) and in `settings_key_scancodes` (`config.xml` only) |
| Input button | `com.gathin.tvhome/.InputKeyService` | `KEYCODE_TV`, `KEYCODE_TV_INPUT`, plus the keycodes in `input_key_keycodes` and `app_keys` (`config.xml` or a theme) |

A force stop of the launcher (Settings > Apps, a cleaner app, `am force-stop`) takes both helpers off
that list. With `restore_key_services` on (`config.xml` or a theme) and `WRITE_SECURE_SETTINGS` granted
(`RESTORE_KEY_SERVICES=1` at install, `-RestoreKeyServices` on Windows), Home puts the helpers that were
running back on the list the next time it starts; a helper turned off in Settings stays off.

- **Home puts the helper back on the list after a force stop, and it still is not bound.** `dumpsys
  accessibility` then shows `Bound services:{}`, `accessibility_enabled` falls back to `0`, and the helper
  sits under `Crashed services`: a killed helper is marked crashed, and Android never binds a crashed
  service again, whatever the list says. It drops the mark only when it sees the helper leave the list,
  and after a force stop the list is already empty, so a plain removal changes nothing. `ServiceRepair`
  therefore adds the helpers, removes them and adds them again, a second apart. Seen on a Hisense SmartTV
  4K FFM (`songshan`, Android 11, `RTT2.220118.001`), whose standby (`com.hisense.STR_SUSPEND`) kills every
  third-party app except the one on screen (`PowerVtImp: kill 3rd app`): the remote's app buttons stopped
  after the first standby with another app open. By hand, the same three writes:

  ```sh
  tools/tv.py "settings put secure enabled_accessibility_services com.gathin.tvhome/.InputKeyService; sleep 1; settings put secure enabled_accessibility_services ''; sleep 1; settings put secure enabled_accessibility_services com.gathin.tvhome/.InputKeyService"
  ```

- **Enabled** means the component is in that user's colon-separated list (the class may be written in
  full, `com.gathin.tvhome/com.gathin.tvhome.HomeKeyService`). Match the whole component.
- **Bound** means it appears under that user's `Bound services` section of the dump (`services` on older
  builds). Give it a few seconds after enabling and read again. Labels in the dump can be translated or
  shared with other services; a line containing "Home" is not proof.
- **Working** means the physical button does the right thing from inside another app. An injected key
  over ADB proves navigation, not that the remote sends the code this helper filters. It does not even
  reach the helper: `input keyevent` skips accessibility key filtering, so an injected `KEYCODE_TV` does
  nothing even when the real Input button works.
- **Which keycode** a dead button sends: `dumpsys window policy` lists the "global keys" Android routes
  to one app (on Google TV several go to launcherx, which is disabled), and Xiaomi firmware logs every
  press as `handleAction<keycode>` in `logcat`. TCL Google TV firmware logs it as
  `TclPhoneWindowManager: handleKeyBeforeQueueing, key = 4099, down = true` (a TCL 55C728, Google TV 11);
  read the log straight after the press, as its main buffer there holds well under a minute.
  Put it in `input_key_keycodes` (opens Inputs) or
  `app_keys` (`keycode:package`, or `hold:keycode:package` for a long press), reinstall with
  `INPUT_KEY_SERVICE=1` and press the real button.
  A press that `logcat` reports as `KEY_UNKNOWN` or `KEY_PROGRAM` cannot be matched by keycode at all,
  because every unmapped button shares those codes: use the Linux scancode from
  `tools/tv.py 'getevent -lt'` in `settings_key_scancodes` and install with `HOME_KEY_SERVICE=1`
  (an RC-36 remote sends its gear on scancode 240 and its notifications button on 362 that way; see
  [the FPD CG50-C3 note](devices/fpd-cg50-c3.md)).
  Not every set answers those two routes, though ([#853](https://github.com/Gathin23/cleanyourtv/issues/853),
  a PEAQ "AI PONT DE", `DVB99PQ` / Changhong `changi`, Google TV 14, `UKNV.260504.001`): a HID
  consumer-control usage prints an evdev `MSC_SCAN` that is not necessarily the `KeyEvent.getScanCode()`
  the framework reports, so a value read with `getevent -lt` can match nothing in
  `settings_key_scancodes` (that set's gear printed `MSC_SCAN 000c009f`, and neither 159 nor 786591, the
  two spellings of it, did anything), and `logcat -v time -s InputReader:D`, the filter `config.xml`
  suggests, printed no key line at all there, for any button, only `Reconfiguring input devices`. What
  Android really delivers comes from a temporary `Log.d` at the top of the helper's `onKeyEvent` printing
  `getKeyCode()`, `getScanCode()` and `getDeviceId()`, rebuilt with `HOME_KEY_SERVICE=1` and read with
  `tools/tv.py "logcat -s TvHomeKeys:D"` while pressing the real button: that set reported
  `action=0 keyCode=83 scanCode=240 deviceId=9`, and `settings_key_keycodes = [83]` opened the TV's
  Settings at once. So a `getevent` code that matches nothing is not proof the press never arrived, and
  consuming the key in the helper also keeps it from the vendor receiver
  `com.homwee.factory/.FactoryKeyReciver`, which `dumpsys window policy` routes `KEYCODE_NOTIFICATION`
  to once the stock launcher is gone.
- **All vendor buttons die at once** and `logcat` says `GLOBAL_BUTTON ... process is bad`: the
  global-key receiver crashes on the disabled launcherx and Android stops starting it. Map every such
  keycode in the helpers, also for disabled apps. See [the MOSR4 note](devices/xiaomi-mitv-mosr4.md).
- **A remote shortcut (such as a "Live" or TV button) opens Google Play Store or crashes after debloating**:
  When a target app such as Live Channels (`com.google.android.tv`) is disabled, pressing its physical
  button makes OEM broadcast receivers (such as Xiaomi's `com.xiaomi.android.tvsetup.partnercustomizer/.GlobalKeyReceiver`)
  launch a market intent to install the missing package, or crash repeatedly.
  To remap it cleanly, put its hardware scancode in `app_keys`: `"scan:<scancode>:<package>"` (e.g.
  `"scan:399:org.smarttube.stable"` on the Xiaomi Mi Box S, read from `getevent -lt`) and rebuild with
  `INPUT_KEY_SERVICE=1`.
  **Use the scancode form (`scan:<scancode>`) rather than a raw keycode (`191`):** generic button keycodes
  like `KEYCODE_BUTTON_4` (191) may be shared with other buttons on some remotes/key layouts, so mapping by keycode
  can unintentionally hijack other buttons.
  `scan:` checks match first and target only that specific physical button. See [the Mi Box S note](devices/xiaomi-mi-box-s.md).
- **The Settings button opens Android TV Settings, not the TV's own menu** (Sony BRAVIA VU3, Google TV 14,
  `BRAVIA_VU3`; the report records no build, [#2131](https://github.com/Gathin23/cleanyourtv/issues/2131)):
  the Quick Settings ribbon that button used to open (Picture mode, Picture settings, Brightness) is Sony's
  own app, and the helper takes `KEYCODE_SETTINGS` before any app is asked, so the press opens the standard
  settings action - on this set `com.android.tv.settings/.MainSettings`, or the launcher's own panel with
  `settings_key_panel`. With the helper off the press arrives and nothing opens: `WindowManager` logs
  `mShortPressOnSettingsBehavior=SHORT_PRESS_SETTINGS_NOTHING`. The ribbon is still installed and opens when
  it is asked directly: `tools/tv.py 'am start -n com.sony.dtv.smartui/.settings.SmartSettingsActivity'`.
  `settings_key_keycodes` and `settings_key_scancodes` do not bring the ribbon back on their own: both end in
  `Settings.ACTION_SETTINGS`, and `settings_fallback_components` is read only when that action resolves to
  nothing, which it does not on this set. If the remote reports a scancode for that button, an `app_keys`
  entry should put it back (read from the launcher's code, not yet tried on a set), because a `scan:` entry is
  answered before the settings branch in
  `HomeKeyService.onKeyEvent`: `"scan:<scancode>:com.sony.dtv.smartui/.settings.SmartSettingsActivity"` in
  `config.xml`, or in a theme's `app_keys` (find `<scancode>` with `getevent -lt` while pressing the button,
  as the `scan:` bullet above does for a Sony remote). A keycode entry cannot: `"176:..."` is
  `KEYCODE_SETTINGS`, which the helper takes itself before it looks at any app key.
- **The list still names the helper but `accessibility_enabled` is `0`**, so nothing is bound and the
  button does nothing (not the force stop below): on a TCL C6K Pro (`G08`, Android 14,
  [#1047](https://github.com/Gathin23/cleanyourtv/issues/1047)) a reboot left
  `enabled_accessibility_services` naming `com.gathin.tvhome/com.gathin.tvhome.HomeKeyService` while
  `accessibility_enabled` read `0`, `dumpsys accessibility` showed `Bound services:{}` and the Home
  button opened Google TV. `restore_key_services` does not repair this state: `ServiceRepair` writes
  `accessibility_enabled 1` only on the path that also puts a missing helper back on the list, and here
  the list was complete. Write the flag back, then read the dump again; toggling the service off and on
  in Accessibility, which is what the owner did, brought the redirect back for that session. Whether the
  flag survives the next reboot was not retested on that set, and what cleared it is not established.
  On a TCL 65C655 Pro (`G03`, Android 11, [`examples/tcl-55c725`](../examples/tcl-55c725/README.md)) every
  one of three reboots left the same state, and there writing the flag back alone bound nothing; clearing
  `enabled_accessibility_services` and writing it back, as `build-and-install` does, did:

  ```sh
  tools/tv.py 'settings get secure accessibility_enabled'
  tools/tv.py 'settings put secure accessibility_enabled 1'
  tools/tv.py 'dumpsys accessibility'
  ```

A force-stop switches the helpers off for good: after `am force-stop com.gathin.tvhome` (or Force stop in
Settings > Apps) Android removes the launcher's services from the enabled list, and they do not come back
when the launcher starts again (seen on a TCL 55C715, Android TV 9). Enable them again the same way you did
the first time, and avoid force-stopping the launcher while testing.

Enabled but never bound points at a firmware restriction on auto-starting sideloaded services (some TCL
builds log `OP_AUTO_START` denials). Look at `logcat` for the binding failure before changing settings,
and do not rewrite the enabled-services list or grant app-ops as a blanket fix. On a TCL 55S470G (Google
TV 12) the line is `[TclAppBoot] ... calleeComponent = 'com.gathin.tvhome/.HomeKeyService', bootType =
'bind_service', isAllow = 'false', reason = 'callee_does't_have_OP_AUTO_START_permission.'`, and
`appops get com.gathin.tvhome` shows `APP_AUTO_START: ignore`. Allowing that one op for the launcher only
and saving it (`appops set com.gathin.tvhome APP_AUTO_START allow; appops write-settings`) made the service
bind on its own after a restart; toggling the service off and on in Accessibility works until the next
restart. App-ops reach the disk lazily: without `write-settings`, a restart soon after the `set` drops the
change and the denial is back. Seen on a TCL C89B (Google TV 14, `G08`, `UTT2.250416.001 AU02`), whose log
reads `[TclAppBoot]:forbid bind service ComponentInfo{com.gathin.tvhome/com.gathin.tvhome.HomeKeyService}`
and whose `appops get` names the op `AUTO_START`: use the name `appops get` prints. When reporting, give the
model, build, current user and the relevant redacted lines, not the raw dump.

On a TCL Smart TV Pro (`G05`, Android 11, `AR2101`), the same permission was set through
**Safety Guard → Permission Shield → Auto Launch Permission**. Temporarily set **Auto manager** to
**Closed**, set the reference launcher's entry to **Opened**, then restore the original
**Auto manager** setting. After a reboot,
`APP_AUTO_START` read `allow` and HomeKeyService was bound without an ADB repair. See the
[G05 startup-permission check](devices/tcl-55c728-g05-4k-gb.md#further-g05-unit-startup-permission-with-stock-home-retained)
for the steps, verification and undo; this has not been retested on the G03 or G08 units above.

## SHIELD TV phone app connects, but no key does anything

On an NVIDIA SHIELD, the SHIELD TV phone app finds the box and connects, but the D-pad, OK, Back and
power buttons in the app do nothing. Restarting the remote server brings them back until the next
reboot. The key helpers are not the cause: it happens with them off too.

The app's keys go through `com.nvidia.shield.remote.server`, which injects them through its own
`virtualinput.InputService`, a `TvRemoteProvider` that Android's TV remote service binds at boot.
Read the state without changing anything:

```sh
tools/tv.py "logcat -d | grep -E 'Key injection failed|Beyonder_VIS'"
tools/tv.py "dumpsys activity services com.nvidia.shield.remote.server | awk '/\* ServiceRecord/{inb=/virtualinput.InputService/} inb' | grep -c IntentBindRecord"
```

`Key injection failed` with `Beyonder_VIS ... mProvider ... isInitialized=false`, and a count of `0`, is
this fault: the service runs but Android never bound it. `1` is a bound, working service.

Cause: `com.google.android.tvrecommendations` disabled. With it disabled, the provider is not bound after
boot. Bisected with a reboot per step on a 2017 SHIELD (`darcy`) and confirmed on a 2015/2017 one
(`foster_e`), both on SHIELD Experience with Android 11: every reboot with it off left the count at `0`,
every reboot with it on gave `1`. Fix:

```sh
tools/tv.py "pm enable com.google.android.tvrecommendations"
```

Wait about 20 seconds before rebooting: Android writes package state to disk with a delay, and a reboot
straight after `pm enable` can lose the change. `am force-stop com.nvidia.shield.remote.server` makes
Android bind the provider at once, but only until the next boot. The SHIELD lists in `examples/` keep
`tvrecommendations` enabled for this reason.

## Fire TV (Fire OS): debloat works, the launcher cannot take Home

Why Google TV differs: on Google TV a third-party app can take the HOME role, so the launcher, the
`set-home-activity` default and the stock-launcher disable all work. Fire OS keeps Home for Amazon's own
launcher, and the same tools fail there. Checked on an Amazon Fire TV, model AFTDEC012E (codename ginza),
Android 11 (SDK 30), build RS8185.3885N:

- `cmd package query-activities` for HOME: `com.amazon.tv.launcher/.ui.HomeActivity_vNext` at priority 950,
  `com.amazon.firehomestarter` at 1. A third-party launcher installed with `android:priority="1000"` on its
  HOME filter still sits at 0. Fire OS ignores the priority of a non-system app.
- `cmd package set-home-activity com.gathin.tvhome/.MainActivity` prints `Success`, and HOME still resolves
  to Amazon's launcher.
- `pm disable-user --user 0` on `com.amazon.tv.launcher`, on its component `HomeActivity_vNext`, and on the
  other Fire OS home packages is refused: `Cannot disable a protected package`.
- Intercepting the Home key with an AccessibilityService did not work: `onKeyEvent` received nothing for
  injected keys (`input keyevent KEYCODE_HOME`) or for a 40-second window of remote presses.

Debloat: `tools/disable.sh examples/amazon-fire-tv-aftdec012e/debloat.txt` disables telemetry, ACR, shopping and
tutorial packages. Undo with `tools/restore.sh examples/amazon-fire-tv-aftdec012e/debloat.txt com.amazon.tv.launcher`.

**Home and the Alexa button do nothing, every other button works**, seen on a Fire TV Stick 4K 1st gen
(`AFTMM`, `mantis`, Fire OS 6.7.1.1, build `NS6711`). It was there before any debloat and survived a factory
reset, so check it before blaming a list. The remote is fine: `getevent -lt` shows `KEY_HOMEPAGE` and
`KEY_SEARCH` arriving from "Amazon Fire TV Remote", and `logcat` shows Fire OS receiving them
(`mic_button_released`, `handleLongPressOnHome`), yet Home stays put and Alexa never listens. The cause was
`settings get secure user_setup_complete` answering `0` (with `global device_provisioned` at `1` and the
Amazon account registered): the first-run setup had been left unfinished, and Android holds back Home and the
assistant until it is. `settings put secure user_setup_complete 1` brought both buttons back; `0` undoes it.
A factory reset clears it again, so finish the setup wizard to the end afterwards.

## Remapping the remote's app buttons without a key helper

A process of your own can take the buttons the helpers exist for without binding an accessibility service,
and the key auto-repeat a helper costs stays: a key-filtering service pushes every key through a filter
path that drops the repeats, while reading the remote's input device leaves Android's input alone.
It has to run as the `shell` user, because a normal app cannot open `/dev/input/event*`, so this is not
something the launcher can grow into — it is a script and a supervisor on the TV. On a Google TV Streamer
(`kirkwood`, Google TV 14, `UTTK.260317.003`) that was the one way to have both: `HomeKeyService` alone,
`InputKeyService` alone and both together each stopped the ramp after roughly six steps, and it came back
the moment the service was unbound ([#796](https://github.com/Gathin23/cleanyourtv/issues/796)).

The Streamer's remote appears as `Google TV Remote Keyboard`, and these are the buttons it sends:

| Button | HID usage | Android keycode |
| --- | --- | --- |
| YouTube | `0x000c0077` | `BUTTON_3` (190) |
| Star | `0x000c0186` | `MACRO_1` (313) |
| Netflix | `0x000c0078` | `BUTTON_4` |

Find the node by name, not by number: `tools/tv.py 'getevent -pl'` lists every input device with its name
and the key codes it declares, and the `/dev/input/event<N>` a remote lands on is not stable between boots,
so a watcher pinned to `event5` stops working after the next reboot and reads as a dead button again.

A reader cannot consume the press. `getevent` observes the events and Android still delivers them to
whatever answers them today, so a remapped button's stock action runs as well: map the Netflix button and
Netflix starts too, then sits in the activity back stack. A helper consumes the key it maps — which is why
the vendor receiver in the "Which keycode" bullet above never sees it — so the buttons you have to
suppress, rather than duplicate, still need a helper and its repeat trade.

What runs it again after a reboot is the part that is not free. The `shell` user reads
`/dev/input/eventX` on this unit without root and a script kept under `/data/local/tmp/` runs, but nothing
there starts itself: Android gives the `shell` user no cron and no init hook without root, so the watcher is
gone after every reboot and has to be started again from a debugging session you close afterwards. Two
things fail silently when you do: the script must be detached from the `adb shell` session or it dies with
it, and a supervisor must not start a second copy. Do not solve the reboot by leaving TCP ADB on so
something local can restart it — port 5555 reachable from the LAN is a shell on the TV for anything on the
network, which is why the closing step in [README](../README.md#questions-people-ask) turns it off.

## The Netflix button still opens Netflix, and a key helper never sees it (TCL UnionTV)

**Seen on:** a TCL UnionTV (`ro.product.model` UnionTV, Android 9, `PPR2.180905.006.A1`, remote `TCL_RC802VEM`)
with the launcher as Home and `InputKeyService` and `HomeKeyService` both enabled.

The Netflix button sends keycode 4062 (scancode 0, `deviceId=-1`). With `4062:<package>` in `app_keys` the
button still opened Netflix and the mapped app never started. `logcat` shows why:

- `interceptKeyTq keycode=4062` is logged, then an `ActivityManager START ... com.netflix.ninja/.MainActivity`
  about 60 ms later **from Netflix's own uid**. The firmware sends the key to Netflix's `NetflixKeyReceiver`
  (action `com.netflix.ninja.intent.action.NETFLIX_KEY`, a `signature|privileged` permission), and Netflix
  starts itself before any window gets the key.
- `pm disable com.netflix.ninja/.NetflixKeyReceiver` is refused for the shell user (`Shell cannot change
  component state`). Disabling the whole app works: `pm disable-user --user 0 com.netflix.ninja` (undo with
  `pm enable com.netflix.ninja`). Do that only if the person does not use Netflix.
- With Netflix disabled the button does nothing, and `logcat` shows `Unhandled key: win=Window{... com.gathin.tvhome/...MainActivity}, keyCode=4062`:
  the key reaches the focused window, but the two accessibility helpers still never get it, so `app_keys`
  has no effect. The firmware appears to inject this key after the input filter the helpers sit on.

So on this set only the focused window can answer the button, and only while Home is in front. Find the
keycode first (`tools/tv.py 'logcat -d -v time | grep interceptKeyTq'` right after pressing the button),
because `getevent -lt` did not show it in the captures taken here.

## A key helper is bound and a held remote button stopped repeating

Hold volume up or down and the volume moves one step per press instead of ramping. The remote may be
fine and so may the debloat: the ramp is back the moment no key-filtering accessibility service is bound,
and `getevent -lt` shows whether the repeat still arrives from the remote while the button is held. On a
TCL 55P71B Pro (`G09`, Google TV 12, `STT2.230203.001.AS23`, #763) it was all or nothing - `HomeKeyService`
alone, `InputKeyService` alone and both together all broke it, with `settings put secure
enabled_accessibility_services ''` plus `accessibility_enabled 0` restoring it. The dumps show
`capabilities=8` (`CAPABILITY_CAN_FILTER_KEY_EVENTS`), so the platform routes every key through a filter
path that drops the repeats, before an app sees them; the launcher's own helpers return `false` for keys
they do not map, so they are not consuming the volume key. The same was reported on a Xiaomi MiTV-MOSR4
(`irobot`, Android 11, `RTM5.220609.003.1265`) with the launcher's own helpers, both and `InputKeyService`
alone (#944), where the empty list was not tried because it also unhooks the buttons the helpers were
installed for. An onn 4K Streaming Box (`YOC`, Google TV 14, `URO4.260304.011.B1.15051976`) does the same with
`HomeKeyService` alone; there `key_filter` false (config.xml or a theme) brought the repeat back with the helper
still bound, so a helper kept for something other than keys need not cost it. A Hisense 75U75N (`hengshan`,
Google TV 12, `STT2.230310.002`) with `HomeKeyService` alone does it too, and its log shows where the repeat goes:
the filter hands volume keys to the window policy first, Hisense's policy sends the CEC step and consumes the key,
so no helper sees it and nothing repeats (the G09 note has the log). The soundbar is not the condition: that unit
had one, while a 55U7N on the same board and build steps once per press with `HomeKeyService` alone and no soundbar
in its report (#1326), so the CEC step is one unit's account of a key the policy takes, not the cause. The 55U7N
also shows where that board's app buttons go: with launcherx disabled Hisense's own `GlobalKeyReceiver` receives
295, 607 and 606 and nothing opens, so only a helper answers them
([the hengshan notes](../examples/hisense-u75-hengshan/README.md)); its held-key capture was not taken. A TCL 55Q77K
(`G08_4K_US`, Google TV 14, `UTT2.250416.001 AU16`, TV speakers, no soundbar) steps once per press with
`InputKeyService` alone as well. On that set Settings (83), Netflix (4062), Prime Video (4099) and Apple TV (4160)
still reach the focused window with launcherx disabled, so Home could answer them without a helper, while YouTube is
`BUTTON_3`, a global key Android hands to launcherx before any window
([the G08 US notes](../examples/tcl-g08-us-google-tv/README.md#the-remotes-buttons-with-launcherx-disabled)). Check the
state first, do not rewrite it blind:

```sh
tools/tv.py 'settings get secure enabled_accessibility_services'
tools/tv.py 'settings get secure accessibility_enabled'
tools/tv.py 'dumpsys accessibility'
```

This is a trade, not a defect to fix: the buttons the helpers exist for (a gear button sending
`KEYCODE_NOTIFICATION` 83, app buttons sending vendor keycodes such as 4062) were answered by the stock
home, so on a set whose list disabled launcherx only a helper can answer them. Either keep the repeat and
install without `INPUT_KEY_SERVICE=1`/`HOME_KEY_SERVICE=1`, or keep the buttons and accept one step per
press, or leave launcherx enabled and bind nothing, or keep the services bound and stop them filtering:
`"key_filter": false` in a theme's `config.json` — the same bool in `config.xml` for a rebuild — makes
them drop `FLAG_REQUEST_FILTER_KEY_EVENTS` at runtime, so the ramp comes back and the buttons they
handled go dead, the same trade without a `settings` write. Write back the exact string the first read
printed; the empty list also unbinds TalkBack. Bisect, keycodes and limits:
[the G09 note](devices/tcl-55p71b-g09-key-repeat.md).

Where launcherx is what answers the buttons, keeping it enabled and redirecting its home screen keeps both. On a
Sony BRAVIA VU3 (`BRAVIA_VU3_4K`, Google TV 14, `UKR1.240726.001`) the Netflix, YouTube, Prime Video and YouTube
Music buttons are global keys (`BUTTON_4`, `BUTTON_14`, `BUTTON_2`, `BUTTON_15`) routed to
`com.realtek.advancesetting`, which on a Google TV hands each launch to launcherx's `GlobalButtonLaunch_Receiver`
and drops it when launcherx is disabled. `InputKeyService` with their scancodes in `app_keys` opened all four but
cost the ramp, and the volume key never reached the helper, so `consume_volume_keys` could not repeat it.
launcherx enabled, `stock_home_redirect` set to it, `key_filter` false and `HomeKeyService` bound gave the buttons,
the ramp and the new Home together ([the VU3 notes](../examples/sony-bravia-vu3/README.md)).

A [Sony KD-55X9000H PA-firmware follow-up](devices/sony-kd-55x9000h.md#later-remote-key-follow-up-24-october-2026) on Android 12 reproduced the same filter/repeat conflict with a personal fork. Its simultaneous kernel/callback capture saw held Volume in hardware but neither Volume direction in the helper. A separate saved-layout experiment retained Apps and native Volume without key filtering; it requires that fork's companion and Library entry, reserves an application-launch category, and has not been tested across restart/sleep/reconnect. It is not a stock upstream option.

## The power key does not wake the TV while the key helper is bound

A remote's power button does nothing while the TV is in standby, and only the set's own button wakes it.
Seen on a TCL `tc8000_ay30a2` (Android 11, `RQ3A.210705.001.2024101221`, #1387), where a key-filtering
accessibility service that stays bound with the screen off was reported to swallow the power key. The
launcher can stand its own helper down for standby instead of losing the helper altogether: with
`HOME_KEY_SERVICE=1` at install, at screen-off `HomeKeyService` takes itself off the accessibility list and
the first screen-on puts it back, so the keys it answers still work whenever the screen is on. Off by
default; three writes turn it on, and none of them is part of an ordinary install:

```sh
tools/tv.py 'pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS'
tools/tv.py 'cmd notification allow_listener com.gathin.tvhome/.GuardRevival'
tools/tv.py 'am broadcast -a com.gathin.tvhome.GUARD -n com.gathin.tvhome/.GuardConfig --ez guard_off_in_standby true'
# off again:
tools/tv.py 'am broadcast -a com.gathin.tvhome.GUARD -n com.gathin.tvhome/.GuardConfig --ez guard_off_in_standby false'
```

The broadcast prints `guard_off_in_standby=true` back, so the answer is readable without a reboot. The
first two writes are what make the opt-in survive, so read why:

- `WRITE_SECURE_SETTINGS` is what lets the launcher write the helper back onto the list at screen-on.
  Without it `GuardKeep` gives up silently and the helper stays off after the first standby, which looks
  like the helper having died. `RESTORE_KEY_SERVICES=1` at install already grants it
  ([the force-stop section](#accessibility-helper-enabled-but-the-remote-button-does-nothing)).
- `GuardRevival` is the listener the system re-binds once the process is gone, and firmware that kills
  third-party apps in standby takes the screen-on watch with it. The rebind is what re-arms it.

While the opt-in is on, `dumpsys accessibility` naming no `com.gathin.tvhome/.HomeKeyService` during
standby is the opt-in working, not a fault: wake the TV and read it again. Only that helper stands down;
`InputKeyService` keeps filtering, so the Input button still works.

A power button that does nothing at all while the TV is **on**, with a long press opening the shutdown
dialog instead, is a different fault: [a TCL 43BP615 whose stock launcher switched the TV off on one press](#a-power-press-that-does-nothing-and-a-long-press-opens-the-shutdown-dialog).

## A power press that does nothing and a long press opens the shutdown dialog

A press on the remote's power button does nothing, and a long press brings up Android's shutdown
confirmation instead of turning the set off, where the stock launcher switched it off on one press.
Checked on a TCL 43BP615 (BeyondTV, Android 11, SDK 30, #1608) after an ordinary
`launcher/build-and-install.sh` with no key helper flags, and again with `HomeKeyService` bound through
`settings put secure enabled_accessibility_services`: the dialog came up both times, so the helper is not
what changes the key. The owner's reading is that the platform takes `KEYCODE_POWER` itself, in the
window policy, before an application or an accessibility service is handed it, and the launcher maps no
power key of its own (its only `KEYCODE_POWER` mention keeps the key out of the `power_on_home`
handover) — so there is no `app_keys` entry, theme key or `config.xml` value that takes the press back.
On this set the one-press power-off belonged to the firmware's own home screen and went with it.

Nothing is broken: the install, the debloat and the key helper are not involved, and the shutdown dialog
is Android's, so the TV still turns off from it. An owner who wants the one-press power-off back gets it by
putting the stock launcher back — `tools/restore.sh <list> <stock launcher>` — and hands Home back to it
at the same time.

## The soundbar on ARC/eARC falls asleep and the TV does not wake it

A soundbar on the TV's ARC or eARC port can go to standby on its own after a stretch of silence while
the TV stays on, and starting an app or a video does not bring it back until someone wakes it by hand.
Seen on a Sony BRAVIA 4K VH2 (KD-55X80J, Google TV 12, `STT2.230505.001.S136`) with a Moto 160SB on
HDMI 3 (eARC), with the TV's own settings already right (Speakers: Audio system, Audio system prioritisation on, BRAVIA Sync on).

The TV reports the state over ADB. While the soundbar sleeps, both values are `false`:

```sh
tools/tv.py 'dumpsys hdmi_control | grep -E "mSystemAudioActivated|mArcEstablished"'
```

Asking for System Audio Mode over HDMI-CEC wakes it, the same request the TV's Speakers → Audio system
setting sends; the soundbar reconnects about 5 to 8 seconds later:

```sh
tools/tv.py 'cmd hdmi_control setsam on'
```

To have that happen without a computer, an app needs the shell: on Android 12 an app cannot send CEC
itself, and writing the TV's speaker setting needs the maker's own permissions. These were tried on the
VH2 and do nothing from an app: Android's system-audio setting, volume nudges, and Sony's audio
service. What does work is an app that keeps Network debugging on and connects to the TV's own ADB on
`127.0.0.1:5555` with its own key, which the TV asks to allow once, and runs only that one command when
an app opens or media starts while no `TYPE_HDMI_ARC`/`TYPE_HDMI_EARC` output is listed. The
[Sony BRAVIA Launcher](https://github.com/udeshvirk/sony-bravia-launcher) in [`SHOWCASE.md`](../SHOWCASE.md)
does this (`SoundbarWaker.java`). It is a trade-off: Network debugging stays on and the port is open on
the home network, which the PROMPT's last step closes on purpose; on a set with `ro.adb.secure=0` an open port is a shell for anyone on the network, so only consider it where the TV asks to allow each new key. On Sony, an app can instead open the
Home Theatre Control panel (`com.sony.dtv.HomeTheatreControl`) for about half a second, which also
wakes the soundbar and needs no ADB, at the cost of the panel flashing on screen.

## Moonlight: two controllers both drive player 1, or a DualSense button opens the exit prompt

With `HomeKeyService` bound (`HOME_KEY_SERVICE=1`) and filtering keys, Moonlight streaming from Sunshine on a
TCL G07 (`G07_4K_GB_NF`, Android 11, `AR17`) with a DualSense (`054c:0ce6`) and an Xbox Wireless Controller
(`045e:0b20`), both over Bluetooth, put both controllers on player 1, and Cross on the DualSense brought up
Moonlight's exit prompt instead of pressing A. Both controllers were listed in `dumpsys input` as separate
devices with sources `0x01000511`, so pairing was not the problem.

The DualSense half has a known shape: Android 11 ships no key layout for it, so `dumpsys input` shows
`KeyLayoutFile: /system/usr/keylayout/Generic.kl`, and `getevent -lp` shows the generic HID order
(`BTN_GAMEPAD`, `BTN_EAST`, `BTN_C`, `BTN_NORTH`, ...). Cross is `BTN_EAST`, which `Generic.kl` maps to
`BUTTON_B`, and an unhandled `BUTTON_B` falls back to Back. Moonlight has a scan-code remap for this layout
(`isNonStandardDualShock4` in its `ControllerHandler`), but it did not take effect while the helper filtered keys,
consistent with what `GamepadPassthrough` (#1118) describes: re-injected keys no longer read as the
controller's own, so Moonlight could neither remap them nor tell the two controllers apart.

`gamepad_passthrough` fixed both: Cross acts as A and the second controller joins as player 2. Set it in
`config.xml` and rebuild, or `"gamepad_passthrough": true` in a theme's `config.json`. The trade is the
one in [the key-repeat section above](#a-key-helper-is-bound-and-a-held-remote-button-stopped-repeating): the
remote buttons the helper answers (gear, app buttons) do nothing while a controller is connected, and come back
when it disconnects. To check what a controller reports:

```sh
tools/tv.py 'dumpsys input' | grep -E 'Controller|KeyLayoutFile|Sources'
tools/tv.py 'getevent -lp'
```

## One app refuses to play while the rest of the TV is online

An ad-blocking Private DNS is a common addition and the resolver answering is not proof that playback
is unaffected. A player also calls measurement and analytics domains, and a block there comes back as
the app's own error while the TV stays online. On a Master-G MGA3200 (`ATV R1`, `R1`, SCBC, Realtek,
Android TV 9, `PTT1.201001.001`) with `dns.adguard-dns.com` in hostname mode, Max (`com.wbd.stream`)
logged `ERR_CONNECTION_REFUSED` for its Braze and Conviva endpoints and stalled, while every other app
played and Android's own connectivity check passed (#1060). Blocking `pixel.spotify.com` takes Spotify
off the Freebox Player POP (`examples/freebox-player-pop`) the same way. So when one app fails and the
rest play, set the mode back before debugging the app, then try the same title once more - on the
MGA3200 that cleared the errors:

```sh
tools/tv.py 'settings put global private_dns_mode opportunistic'
```

The other failure mode reads differently: when the TV itself says "No internet", the resolver is down
or unreachable, and the same command restores it. What the optional resolver does, and what it costs,
is in the README's "Can it block ads inside apps too?".

When you report how an app does on your TV, say how far it got, because each step can fail on its
own: installed, opened, usable with the physical remote, signed in, and played a title with sound.
Reaching the login screen is not playback. Quote the app's full error code (Netflix's `UI-800-3`,
for example) rather than "doesn't work", since that is what the app's own help pages look up (#1008).

## Xiaomi CN firmware: Settings freezes or crashes after the debloat

On China-market Xiaomi firmware the settings app is Xiaomi's own (`com.xiaomi.mitv.settings`), and it
reads the TV's hardware configuration from the `TvService` system service that `mitv.service` registers.
Disable that package and Settings breaks while everything else on the TV keeps working: opening it
freezes for 20-30 s and drops back to Home (an ANR, not a reboot), and pressing Down twice from the
first page, Network -> Display -> Sound, crashes it outright. Seen on a MiTV-ASTP0 (`mulan`, Android 9,
SDK 28, Amlogic/droidlogic, 1.5 GB RAM; the version string says HyperOS 2, the base is still Android 9)
in [issue #1310](https://github.com/Gathin23/cleanyourtv/issues/1310), whose report could not quote
`ro.build.display.id`: that firmware closes network ADB on every reboot.

The crash log names the cause:

```sh
tools/tv.py "logcat -b events -d | grep am_crash"
# java.lang.NumberFormatException, For input string: "NULL"   <- SoundView.init, Integer.valueOf
```

`SoundView.init` parses a value it got from `mitv.common.ConfigurationManager` with no guard, and with
the service gone that manager retries ten times half a second apart, gives up and hands back the literal
string `NULL`:

```
ConfigurationManager: getService() 0..9, service = null
ConfigurationManagerDefaultImpl: service null return
```

So it is neither the Settings app nor the debloat in general. `mitv.service` is `/system/app/TvService`,
a PERSISTENT app whose `TvApplication.onCreate` starts `TvServer`, the thing that registers the service:

```sh
tools/tv.py "service list | grep -i tvservice"   # TvService: [mitv.internal.ITvService]
```

Keep the package. Every settings page that reads a hardware value then stalls about 5 s, and the one
page that parses the answer crashes. The China-market lists here already say not to touch it; this is
why.

**Re-enabling the package on its own does not bring Settings back.** The service is registered at boot
and the disabled package's PERSISTENT process starts anyway, so nothing looks different after
`pm enable` and it is easy to conclude the debloat was innocent - the reporter lost a day to exactly
that. Start the service by hand instead:

```sh
tools/tv.py "pm enable mitv.service"
tools/tv.py "am startservice -n mitv.service/.TvServer"
tools/tv.py "service list | grep -i tvservice"      # expect: TvService: [mitv.internal.ITvService]
```

A restart registers it too, and on that firmware the restart also turns network ADB off while the switch
that turns it back on sits inside the Settings app that is broken: have ADB open before the reboot, or
count on reaching Settings by hand. `examples/xiaomi-mitv-aesp0` is the one list here that disables the
package (its second set, in daily use since 21 Oct 2026); carrying that line to a set whose settings app
is Xiaomi's takes Settings with it.

## Settings freezes, then closes, in Accessibility

The highlight passes over Accessibility > Text-to-speech output and Settings stops responding; a few seconds
later Android closes it ("Input dispatching timed out"). TV Settings previews the focused row, and that row's page
starts Google's speech engine (`com.google.android.tts`, "Speech Recognition & Synthesis" in the Play Store) to
name the current voice. On a Hisense 75U75N (`hengshan`, Google TV 12, `STT2.230310.002`, 23 and 24 October 2026)
the engine took more than ten seconds to start, and leaving the row before it answered left Settings waiting for
it. The debloat does not touch the engine. The ANR report names the wait:

```sh
tools/tv.py 'dumpsys dropbox --print system_app_anr' | grep -m2 TextToSpeech
#   at android.speech.tts.TextToSpeech.shutdown(TextToSpeech.java:914)
#   at com.android.tv.settings.system.TextToSpeechFragment.onDestroy(TextToSpeechFragment.java:218)
```

Clearing the engine's data fixed it on that set; the engine downloads its voice again the first time it is used:

```sh
tools/tv.py 'pm clear com.google.android.tts'
```

The Play Store had no newer engine there. Without the reset, wait on the row until the voice shows before moving on.
## The router's domain rules stop applying to the TV after Private DNS

Private DNS in hostname mode sends every lookup over DNS-over-TLS straight to the resolver, past the
router's own DNS. A router that filters or routes traffic by domain through its resolver - podkop and its
forks on OpenWrt, mihomo/Clash in fake-IP mode, a VPN subscription with domain lists, Pi-hole or AdGuard
Home - then never sees the TV's lookups, and its rules stop applying to the TV without any error. On a TCL
Smart TV Pro (G05, Android 11, `AR2101`/`AR28`) behind such a router, with `dns.adguard-dns.com` in
hostname mode, the TV got real addresses instead of the router's fake-IP range (198.18.0.0/15), so traffic
the router would have sent through its tunnel went out directly: services that only work through that
route broke or slowed down, and the resolver itself answered `127.0.0.1` for `api.themoviedb.org` (#1385).

To tell, compare the address the TV gets with the router's answer for the same host:

```sh
tools/tv.py 'ping -c1 -W1 <host>'     # the first line shows the address the TV resolved
nslookup <host> <router ip>           # on the computer: what the router answers
```

A fake-IP or other router-made answer next to a real address on the TV means Private DNS is going around
the router. Then either set the mode back, so the TV asks the router again, and block ads on the router
instead (most of these setups can):

```sh
tools/tv.py 'settings put global private_dns_mode opportunistic'
```

or keep Private DNS and accept that the router's domain rules no longer cover the TV. Write down which one
you chose, so the next "this one service is slow on the TV" does not start from scratch.

## An app that runs a local VPN is on until the next boot, then gone

A DNS filter, an ad blocker or a VPN client you keep, set to start with the TV, works once switched on, and
after every reboot the TV comes up without it: no `tun0`, no process. Seen with the AdGuard app (4.6.207,
local VPN mode) on an Anker Nebula D2426 projector (Android TV 14, `UKNV.260603.001`, 1.7 GB), and the
mechanism is Android's, not the app's. Its boot receiver did run: the TV's log shows
`ActivityManager: Start proc <pid>:<package> ... for broadcast {.../BootUpReceiver}`, then 12 s later
`Process <package> (pid <pid>) has died: cch+95 CEM`, while the app's own lines still say it is loading. The
receiver had returned and handed the loading to a thread, no foreground service was up yet, so to Android
the process was an empty cached one, and this firmware keeps very few of those:
`tools/tv.py 'dumpsys activity settings' | grep CUR_MAX` prints `CUR_MAX_CACHED_PROCESSES=8` and
`CUR_MAX_EMPTY_PROCESSES=4`. The first boot services push it out before it reaches the VPN. Any such app that
loads slowly on a small TV can hit this; Private DNS (the README's option) is not affected, since the system
resolver does the work.

What does not help: the background exemptions (`cmd appops set <package> RUN_ANY_IN_BACKGROUND allow`,
`dumpsys deviceidle whitelist +<package>`, `am set-standby-bucket <package> active`), because the app
was never restricted, only evicted; and `device_config put activity_manager max_cached_processes 32`,
which leaves `CUR_MAX_CACHED_PROCESSES` at the maker's 8. What does: make the system itself bring the VPN
up at boot, by declaring the app as the always-on VPN. The app must have had its VPN consent dialog
accepted once before (it has, if its protection was ever switched on by hand):

```sh
tools/tv.py 'settings put secure always_on_vpn_app <package>; settings put secure always_on_vpn_lockdown 0'
```

At the next boot the log shows `Background started FGS: Allowed [callingPackage: <package>` about 25 s
after the process start, and `tools/tv.py 'ip addr show tun0'` has an address, before the firmware's own
boot-time calls go out (about two minutes in, here). Keep `always_on_vpn_lockdown` at 0: with 1 Android
drops every packet until the VPN is up, and a TV whose app fails to start stays offline, with no screen
that says why. Undo:
`tools/tv.py 'settings delete secure always_on_vpn_app; settings delete secure always_on_vpn_lockdown'`.
