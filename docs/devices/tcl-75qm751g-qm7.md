# TCL 75QM751G (QM7, 2024): the QM6K list on the same G08 board

One owner's report ([#453](https://github.com/Gathin23/cleanyourtv/issues/453)), applied and
checked on the same day and **not** lived with for several days, so this is not a list of its own.
Run [`examples/tcl-qm6k-google-tv/debloat.txt`](../../examples/tcl-qm6k-google-tv/debloat.txt) —
same `G08` board, same `AU14` build — and read the deltas and the two traps below. A second owner
confirmed the cast-receiver symptom on a TCL BeyondTV2 in the same thread. A second unit, a
55QM751G on the same build, is [further down](#a-second-unit-55qm751g-same-build): reboot
persistence, the remote's keycodes, the mic, and the volume trade with a key helper.

## Identify the firmware, not the retail name

| Field | Observed value |
|---|---|
| Retail model | `75QM751G` (QM7, 2024, US), supplied by the owner |
| `ro.product.model` | `Smart TV Pro` |
| `ro.product.device` | `G08` |
| `ro.product.manufacturer` | `TCL` |
| Android / SDK | `14` / `34` |
| `ro.build.display.id` | `tcl9618-user 14 UTT2.250416.001 AU14 release-keys` |
| `ro.adb.secure` | `1` |

`Smart TV Pro` is shared across TCL ranges, so it decides nothing on its own. What matters here is
`G08` + `AU14`: that is the pair the QM6K list was already applied on (#23 reported the same board
on AU14), and it is why the same list is the starting point on this set.

## Connection

Network ADB answered over Ethernet without the wireless-debugging pairing step. `ro.adb.secure=1`
still means the RSA prompt on the TV has to be accepted. Whether a wireless-only setup on another
unit of this model needs pairing was not tested.

## What the QM6K list needed on this set

Every package in the QM6K list was disabled, with one no-op: `com.tcl.repairguide` is **not
installed** on this preload (`Unknown package`), so the e-manual entry is missing rather than
disabled. The owner also uncommented these optional lines of that list:

| Package | What goes with it |
|---|---|
| `com.netflix.ninja`, `com.netflix.tokenmanager` | Netflix (the two lines belong together) |
| `com.google.android.katniss` | Google Assistant voice |
| `com.google.android.tv.remote.service` | Google TV phone-app remote; on the C755 list it also carries the remote's microphone |
| `com.tcl.airplay2`, `com.mediatek.AirplayAPK`, `com.mediatek.airplaydaemon` | AirPlay (three lines together) |

The Cast receiver (`com.google.android.apps.mediashell`) was kept, and casting still broke — see
below.

## setupwraith is skipped while it owns the saved Wi-Fi, Ethernet or not

The first `launcher/build-and-install.sh` run rolled back as designed: Home went to
`com.google.android.tungsten.setupwraith/.RecoveryActivity`. `tools/disable.sh` then skipped
setupwraith because the Wi-Fi network saved during setup belonged to it. The TV was on Ethernet and
that changed nothing: the guard asks who owns the saved network, not which interface is up. Only
after forgetting that network in Settings did the second run disable setupwraith and make
`com.gathin.tvhome` Home.

So on a set like this, decide before the install, not after the rollback:

- check the owner of the saved network first:
  `tools/tv.py "dumpsys wifi" | grep -o 'cname=[^ ]*'`;
- forget the network and rejoin it on the TV (it then belongs to `android.uid.system`), which is
  what this owner did, and what keeps Wi-Fi working afterwards;
- `FORCE_SETUPWRAITH=1` skips the guard instead, which is fine on a set that stays on Ethernet and
  loses nothing when that saved network is deleted.

## Launcher settings that worked

`INPUT_KEY_SERVICE=1`, `live_tv_package` `com.tcl.tv` (the player; `com.tcl.tvinput` has no
launcher activity) and `settings_key_keycodes` `83` for the gear button. These match the other G08
lists, and the owner reports Live TV, inputs and the launcher working with them; the individual
remote keys were not enumerated beyond that.

## Casting: the receiver can be running and still invisible

After the list, phones and a PC could not see the TV. `com.google.android.apps.mediashell` was
running and listening on 8008/8009, so "the process is up" proves nothing. This was also not the
setupwraith credentials failure: briefly re-enabling setupwraith did not help, and the log had no
`No private key` or `CastInfoProvider` lines. The receiver had stopped answering mDNS — its
connections sat in `CLOSE_WAIT` on 8009 with unread data. `am force-stop
com.google.android.apps.mediashell` restarted it, the TV advertised again within seconds, and
casting from a PC worked, without a reboot.

Check discovery, not the process:

```sh
tools/tv.py "logcat -d -s cast_shell | grep -E 'Done announcing|responses sent'"
tools/tv.py "dumpsys activity service com.google.android.apps.mediashell | grep WargService"
```

A receiver that is advertising logs `MdnsAppFilter: Done announcing.`,
`responses sent in 60 seconds: N` and `Gcm.Register.Success=2`, with `WargService` up. Missing
`Done announcing` while the process lives is the hang this owner hit. The second owner did not
reproduce the hang — their list leaves the Cast stack alone — and did not stream video, so the log
evidence covers discovery, not playback.

## A second unit: 55QM751G, same build

A 55QM751G reported the same `ro.product.device` `G08`, `ro.product.model` `Smart TV Pro` and
`tcl9618-user 14 UTT2.250416.001 AU14 release-keys`, SDK 34, `ro.adb.secure=1`. Also checked on the
same day and not lived with, by one owner in one session.

**Connection.** Network ADB answered on 5555 over Wi-Fi with no pairing step. Wireless debugging was
on as well (`adb_wifi_enabled=1`), on a port of its own. `wm size` showed a 1920x1080 override on the
3840x2160 panel, with no density override; it was left alone and the launcher laid out normally.

**The list.** The QM6K list was applied in three batches with a screenshot of Home after each, and
`com.tcl.repairguide` was again not installed. Kept by the owner's choice: Netflix, Chromecast
(`com.google.android.apps.mediashell`), Assistant (`com.google.android.katniss`), the phone remote
(`com.google.android.tv.remote.service`), AirPlay, YouTube Music, the USB media player
(`com.tcl.ui_mediaCenter`) and ambient mode (`com.google.android.apps.tv.dreamx`). From the optional
block, `com.tcl.gamebar`, `com.tcl.UpdatePeripheral` and `com.google.android.marvin.talkback` were
disabled. Prime Video ships preinstalled as `com.amazon.amazonvideo.livingroom`, which the list does
not name. With launcherx, 25 packages were disabled; after `sync`, a 15-second wait and a reboot,
`pm list packages -d` was the same 25, and the TV started straight into the new Home
(`wm_set_resumed_activity` showed `FallbackHome`, then `com.gathin.tvhome`), not into Live TV.

**setupwraith without losing Wi-Fi.** setupwraith owned one saved network, but not the one the TV
was on: `dumpsys wifi` gave `cname=com.google.android.tungsten.setupwraith` for a network saved
during setup and no longer used, and `cname=android.uid.system:1000` for the connected one. The
owner could not find it in Settings, so it was removed from the shell instead:
`cmd wifi list-networks` for its id, then `cmd wifi forget-network <id>`. `dumpsys wifi` then showed
`android.uid.system` only, `tools/disable.sh` disabled setupwraith without `FORCE_SETUPWRAITH=1`,
Wi-Fi stayed connected, and `launcher/build-and-install.sh` made `com.gathin.tvhome` Home on its
first run.

**The remote's keys.** The Bluetooth remote names itself `BT_RC923_M2` (key layout
`Vendor_0417_Product_0304.kl`). Its app and gear buttons are `KEY_UNKNOWN` (scancode 240) in
`getevent -lt`, with no entry in that layout; the firmware gives them keycodes of its own, which
`logcat` prints as `InputReader: TIDD: keyCode=...` with the HID usage beside it:

| Button | HID usage | Keycode |
|---|---|---|
| Netflix | `0x000c0051` | 4062 |
| Prime Video | `0x000c005f` | 4099 |
| YouTube | `0x000c0057` | 190 (`KEYCODE_BUTTON_3`) |
| Gear | `0x000c0055` | 83 (`KEYCODE_NOTIFICATION`) |

With launcherx disabled and no key helper, the Netflix, Prime Video and YouTube buttons did nothing,
and Input still worked. The gear sends 83 to SystemUI, which logs `TvNotificationPanel: Not launching
notification handler activity: Could not resolve activityInfo for intent
android.app.action.TOGGLE_NOTIFICATION_HANDLER_PANEL`. Without a helper, Home itself answers
`settings_key_keycodes` 83 and the `app_keys` with keycodes from 1000 up, so with `83` and
`4062:com.netflix.ninja`, `4099:com.amazon.amazonvideo.livingroom` in `config.xml` the gear, Netflix
and Prime Video buttons worked from Home. YouTube (190) needs the helper. With `INPUT_KEY_SERVICE=1`
all three app buttons worked and held volume up moved one step per press, as on the 75Q7CXB in
[the key-repeat note](tcl-55p71b-g09-key-repeat.md); clearing `enabled_accessibility_services`
brought the ramp back. The owner kept the ramp and no helper.

**The mic.** With launcherx disabled the mic did nothing: each press logged
`AtvRemote.AppOpsDisclosureService: com.google.android.katniss doesn't have access to the on-remote
mic`, the case in [TROUBLESHOOTING](../TROUBLESHOOTING.md). Switching on Microphone access under
Settings > Privacy brought it back, and the Assistant answered a spoken question. The
"not available in this profile" refusal seen on the AU02 build did not appear on this one.

**Private DNS and Prime Video.** With Private DNS set to `dns.adguard-dns.com` (`private_dns_mode`
`hostname`), another streaming app played, but Prime Video (`com.amazon.amazonvideo.livingroom`) did not.
With `private_dns_mode` back to `opportunistic` it played. Which domain AdGuard's list blocks was not
looked into. This build has no Private DNS entry under Settings > Network & Internet, so turning it off
again takes ADB: `settings put global private_dns_mode opportunistic`.

**Closing ADB.** Wireless debugging off closed its port (`tools/adb-closed.py` refused). The owner
then revoked the authorisations, switched USB debugging off and restarted the TV, and 5555 still
answered: USB debugging was on again in Developer options. Switched off a second time, it stayed off
through a restart, and `tools/adb-closed.py` reported 5555 closed. Whether the first switch did not
take or the restart put it back was not told apart.

## Test limits

- One set, one firmware, one session, same day. Nothing here has been lived with for days.
- Regional builds, other panel sizes, wireless-debugging pairing, Wi-Fi after setupwraith is
  disabled, reboot persistence of the disabled packages, the remote's app buttons, and playback
  through Netflix, Assistant or AirPlay after those packages were turned off are all untested.
- Casting was verified from a PC only, for discovery and one stream; casting from a phone and after
  a reboot was not re-checked.
- The 55QM751G is also one set, one firmware, one session. It covers what the first left open on
  reboot persistence, Wi-Fi after setupwraith was disabled, and the app buttons, for that unit only.
  It kept Netflix, Assistant, AirPlay and Chromecast enabled, so it says nothing about turning them
  off. Casting, AirPlay and the Cast receiver hang above were not checked on it. The mic was tested
  before the reboot, not after. Playback was checked once per case: one app with AdGuard DNS on,
  and Prime Video with it on (failed) and off (played).
