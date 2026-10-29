# Troubleshooting

Things that bit us or contributors, so they don't bite you. Commands use `tools/tv.py`; see the
[README](../README.md#doing-it-by-hand) for setting it up.

## Connecting

- **macOS: `adb connect` says "No route to host"** although the port is open. Recent macOS treats
  the Google-signed `adb` as its own app under Local Network privacy and silently blocks it.
  `tools/tv.py` avoids the binary entirely.
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
  `tools/adb-closed.py` only checks 5555. `adb mdns services` lists that port while it is open.
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
  tools/tv.py --probe`: the ADB one answers, the others time out. Every script here honours `TV_PORT`, and
  the closing check is `tools/adb-closed.py <tv ip> <port>`.
- **Close port 5555 when you're done**: plaintext, and a shell for anyone on the LAN when `ro.adb.secure=0`. Steps in the README FAQ.
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
- **Windows PowerShell 5.1 adds a byte-order mark** to text piped into a program, which turned the
  first batch command into gibberish. `tv.py` now strips it, and the `.ps1` scripts pipe clean UTF-8.
- **"Allow USB debugging?" comes back** although `adb devices` already works: `tv.py` used its own
  key next to the one platform-tools had registered. It now reuses `~/.android/adbkey` when that
  exists. Every attempt with an unaccepted key queues one more prompt on the TV, so dismiss them all
  (Back) before testing with key presses.
- **Git Bash rewrites paths** like `/data/local/tmp` into Windows paths. The scripts set
  `MSYS_NO_PATHCONV=1` for you. Set `JAVA_HOME` and `ANDROID_HOME` before building there.

## Working on the TV

- **Chained `pm` commands** (`pm disable a; pm disable b`) may silently stop after the first. Run
  one per shell call; `--batch` does exactly that.
- **Large pushes hang** through the Python client. Use `tools/b64push.py` for APKs.
- **`tools/tv.py reboot` never comes back.** `reboot` is a shell command, and adbd stops before it
  sends the shell's last `CLSE`, so the client waits for a reply that cannot arrive: two minutes,
  then an error, on a TCL 65C935 (`G05_4K_GB`, Google TV 11, AR2101,
  [#995](https://github.com/Gathin23/cleanyourtv/issues/995)). A chain that reboots and then checks
  something stalls there. Use `tools/tv.py --reboot`: it sends the reboot through ADB's own service
  and returns once the request is accepted. The TV is still restarting at that point, so let it
  answer again (`tools/tv.py --probe`) before the next command.
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
  answers. Report another one rather than reading it as a firmware quirk: the wrong-handler case needs
  a source change, not a setting.
- **`adb exec-out screencap -p` gives a broken PNG** on some MediaTek sets (Xiaomi, and Amazon's
  own Fire TV smart TVs): the binary prints `Init wrapper sys mutex successful` on stdout first.
  Save on the TV and pull the file instead: `screencap -p /data/local/tmp/s.png`, or cut
  everything before the `\x89PNG` header. `tools/tv.py --screenshot shots/home.png` does the save,
  the pull and the cleanup in one command, and says so when the capture is 0 bytes.
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
  `com.android.settings`, `com.android.tv.settings` or `android`), with no switch to override it.
  If a set is already stuck and still answers ADB, `pm enable` each of those packages and reboot;
  if ADB is gone too, a factory reset from the recovery menu is usually the only way back.
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
  never completes. This repo's launcher is direct-boot-aware; most other launchers are not. Check both
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
  When it keeps naming the stock launcher, the set is debloat-only until someone runs these two checks on
  that exact board (Thomson 40FG2S14W, Expressluck `nagata`, Android TV 14 `UKNV.260317.001`,
  [notes](devices/thomson-40fg2s14w.md)).
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
  list keeps both now; the lists that still disable `com.google.android.tv.remote.service` are
  `rca-ai-pont`, `sharp-aquos-tve21f`, `sony-bravia-4k-gb-us`, `sony-xbr-65x900f`,
  `sony-xbr-x900e`, `tcl-32s5k`, `tcl-40s5400a`, `tcl-55s470g`, `tcl-beyondtv2-android-11`,
  `tcl-g09-google-tv`, `tcl-g10-google-tv`, `xgimi-horizon-s-pro`, `xgimi-mogo-3-pro` and
  `xiaomi-mi-box-s`. Those TVs haven't been tested for this, but any of them whose remote has a
  microphone may show the same symptom.
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
  cannot change component state`. So either debloat without setupwraith and launcherx, or re-enable
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
| Home button | `com.gathin.tvhome/.HomeKeyService` | `KEYCODE_ALL_APPS` (not an ordinary `KEYCODE_HOME`) |
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

## A key helper is bound and a held remote button stopped repeating

Hold volume up or down and the volume moves one step per press instead of ramping. The remote may be
fine and so may the debloat: the ramp is back the moment no key-filtering accessibility service is bound,
and `getevent -lt` shows whether the repeat still arrives from the remote while the button is held. On a
TCL 55P71B Pro (`G09`, Google TV 12, `STT2.230203.001.AS23`) it was all or nothing - `HomeKeyService`
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
Google TV 12, `STT2.230310.002`) with a CEC soundbar does it with `HomeKeyService` alone, and its log shows where
the repeat goes: the filter hands volume keys to the window policy first, Hisense's policy sends the CEC step and
consumes the key, so no helper sees it and nothing repeats (the G09 note has the log). Check the state first, do
not rewrite it blind:

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
