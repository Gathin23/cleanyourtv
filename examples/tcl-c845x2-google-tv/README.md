# TCL 75C845X2 (UK/Europe), Google TV 14

- **TV:** TCL 75C845X2, UK/Europe build. Model string "Smart TV Pro", product `G08_4K_GB`, MediaTek `tcl9618`, Android TV OS 14 (Google TV).
- **Firmware:** `V8-0012T01-LF1V667`, fingerprint `TCL/G08_4K_GB/G08:14/UTT2.250416.001/AU04:user/release-keys`.
- **Based on:** [`tcl-c755-google-tv`](../tcl-c755-google-tv/debloat.txt), the same G08 board and package set.
- **Tested:** applied on a real 75C845X2 on 2026-10-20. Home, the launcher and its dock apps worked afterwards.

## Differences from the C755 list

- YouTube (`com.google.android.youtube.tv`) and Google Play Movies & TV (`com.google.android.videos`) are disabled, because SmartTube is used instead.
- Chromecast, AirPlay (three packages) and the game bar are kept (commented out).
- `com.tcl.hotelmenu` and `au.com.stan.and` are not on this UK build. The "Unknown package" errors for them are harmless.

## Known issue: Settings > Accounts & profiles is empty

Disabling the stock launcher (`com.google.android.apps.tv.launcherx`) empties this page, because on this
firmware launcherx serves it. The accounts are not deleted. How to switch profile anyway (enable launcherx
for a moment, then disable it and set Home again) is in [TROUBLESHOOTING](../../docs/TROUBLESHOOTING.md),
under "Accounts & profiles is empty after the stock launcher is disabled". Most account switching can also be
done inside each app (YouTube, Plex, SmartTube).

Not confirmed: `pm list users` also shows `configured_user` (10) and `new_user` (11). Whether other Google profiles run as separate Android users was not tested (`am get-current-user` while on each profile would show it).

## Second unit: 55C845X2, the remote's buttons and their cost

- **TV:** TCL 55C845X2, same product (`G08_4K_GB`) and firmware (`V8-0012T01-LF1V667`, AU04) as above. Remote `BT_RC833_U1`.
- **Tested:** 2026-10-27 and 28, one owner, with the CleanYourTV launcher as Home and `HOME_KEY_SERVICE=1`.
- **Disabled:** a subset of this list in three batches, plus launcherx. The batches were tracking and ads (`com.tcl.ttvs`,
  `com.tcl.interactive`, `com.tcl.partnercustomizer`, `com.tcl.tv.tclhome_passive`), unused preloads (Play Games,
  YouTube Music, Play Movies & TV, the feedback apps, `com.google.android.partnersetup`,
  `android.autoinstalls.config.google.gtvpai`, `com.tcl.guard`, `com.tcl.UpdatePeripheral`), then `dreamx` and
  `setupwraith`. YouTube stayed, for YouTube Premium. Chromecast, AirPlay, the Assistant, the phone remote and the
  game bar were kept too.
- **Already disabled when first read:** 16 TCL packages, including `com.tcl.browser`, `com.tcl.miracast`,
  `com.tcl.gallery` and `com.tcl.inputmethod.international`. `tools/restore.sh` leaves them disabled.

### The Wi-Fi network setupwraith owned

The first `launcher/build-and-install.sh` run stopped and rolled back: Home resolved to
`com.google.android.tungsten.setupwraith/.RecoveryActivity`. `dumpsys wifi | grep -o 'cname=[^ ]*'` showed that one
saved network had been created by setupwraith during setup. The TV was connected through another saved network, so
that one was forgotten (`cmd wifi forget-network <id>`). After that, disabling setupwraith kept Wi-Fi and ADB, and
the second install made the launcher Home.

### Remote keycodes

These were read from `InputReader: TIDD: keyCode=...` in logcat while the owner pressed each button. The scan
codes are the `0x000c....` usage codes on the remote's consumer-control node.

| Button | Keycode | Usage | With launcherx disabled and no helper |
|---|---|---|---|
| Settings (gear) | 83 | `0x0c0055` | nothing |
| Netflix | 4062 | `0x0c0051` | nothing |
| Prime Video | 4099 | `0x0c005f` | nothing |
| YouTube | 190 | `0x0c0057` | nothing |
| TCL Home | 4137 | `0x0c003a` | a TCL panel |
| TCL Channel | 4020 | `0x0c0053` | nothing |
| Web | 4159 | `0x0c029c` | nothing |
| Input | `KEYCODE_TV_INPUT` | | TCL's source panel, before any helper sees it |

[`config.json`](config.json) brings back the first four with `HOME_KEY_SERVICE=1`. The same entries, built into
`config.xml` rather than pushed as a theme, worked when the owner pressed each button on the real remote. To make TCL Home do nothing, add `"4137:ignore"` to `app_keys`. TCL Channel and Web can open
any app the same way (`"4159:<package>"`).

### What the helper costs: held keys stop repeating

With `HomeKeyService` bound, holding D-pad Down for 5 seconds on Home logged one `handleKeyBeforeQueueing ...
KEYCODE_DPAD_DOWN ... repeatCount=0`, one `inputFilter host injectInputEvent` with the same event, and the
`ACTION_UP` 5 seconds later, with no repeats in between, so the focus could only move once. Before the helper was bound,
holding Channel up in TV Settings reached `repeatCount=32`. This is the trade described in
[the G09 key-repeat note](../../docs/devices/tcl-55p71b-g09-key-repeat.md), and here it includes the D-pad, so
holding a direction to scroll or seek inside apps moves one step per press too. Only held D-pad Down and Channel up
were measured; volume was pressed, not held. The owner kept the helper for the buttons. The soundbar (HDMI ARC) has
its own remote, which still ramps the volume.

### Also checked on this unit

- AdGuard Private DNS (`dns.adguard-dns.com`) validated.
- After a reboot, all disabled packages stayed disabled and the TV came up on the new Home, with no `power_on_home`.
- Port 5555 closes at every reboot on this firmware. `tools/adb-closed.py` reported it refused once debugging was
  switched off and the TV had restarted.
