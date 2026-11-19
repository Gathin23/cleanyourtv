# TCL 75T7 (G08, Google TV, Android 14), US firmware

TCL 75" T7 Series 4K 144Hz QLED (`75T7`), device `G08`, model "Smart TV Pro", product `G08_4K_US`, firmware
`UTT2.250416.001 AU16`, MediaTek `tcl9618` / `mt5896`, about 2.4 GB RAM. The same G08 board as the C755, QM6K
and 85RM7L lists, on the US build.

| File | What it is |
|---|---|
| `debloat.txt` | The 37 packages that were disabled, grouped and commented |

**How much testing is behind it:** one set, 20-21 October 2026. The first 35 packages were disabled and survived
a reboot; the stock launcher and the setup wizard were disabled afterwards for a custom launcher and have not been
through a reboot yet. It was done by hand with `pm disable-user`, not with `tools/disable.sh`, so the Apply and
Undo lines in the list header are the repo's standard ones, not something run on this set.

## What is different from the other G08 lists

- **US streaming preloads** (Disney+, Hulu, Tubi, Max, Prime Video, YouTube TV, Google TV, Play Games) are on
  the set and are off here by choice. Comment them out if you use them.
- **`com.tcl.dashboard`, `com.tcl.hearaid` and `com.tcl.t_solo`** are disabled here; the C755 list keeps them.
  No problem was noticed in light use (Home, YouTube, Netflix and other apps opening), but HDMI inputs, Live TV,
  voice, casting, TCL's wireless speakers and the hearing helper were not specifically retested afterwards.
  `com.tcl.channelplus` (the TCL Live FAST input) was disabled, so check that Live TV still opens for you.
- **Left alone:** `com.tcl.ttvs`, `com.tcl.partnercustomizer`, `com.tcl.interactive`, `com.tcl.smartlink.core`.
  The C755 list disables some of them; they were not tried on this set.

## Notes for this set

- The saved Wi-Fi network belongs to `com.google.android.tungsten.setupwraith` (`dumpsys wifi | grep cname`).
  Forget the network and join it again in Settings before disabling the wizard, or it disappears with it and ADB
  goes with it.
- Disabling just the wizard's `RecoveryActivity` is refused: `Shell cannot change component state`. With
  launcherx off and the wizard still on, Home falls to `setupwraith/.RecoveryActivity`.
- Wireless debugging switches itself off on every reboot and comes back on a new port: note the port from
  Developer options each time.
- The setup wizard, launcherx and the Ambient screensaver (`dreamx`, left enabled here) are the ones to look at
  first if Home or the screensaver misbehaves after a system update.

## Second unit: TCL 55Q77K

TCL 55Q77K, 55", model "Smart TV Pro", product `G08_4K_US`, client type `TCL-NA-MT9653-S1` (MediaTek MT9653),
the same firmware `UTT2.250416.001 AU16`. On the Ethernet port, TV speakers, nothing in USB. Set up on
23 October 2026: this list without the AirPlay packages, the TCL browser and the streaming apps (kept by that
owner), then launcherx and the setup wizard for a custom launcher. All of it came through a reboot the same day.

- **The setup wizard's saved Wi-Fi on an Ethernet set.** Four saved networks belonged to
  `com.google.android.tungsten.setupwraith` (`dumpsys wifi | grep cname`), though the set was on Ethernet.
  Disabling the wizard deleted them, as Android does, and nothing else: the address on `eth0` and ADB stayed. Join
  Wi-Fi again in Settings if the set ever goes back to it; a network saved there belongs to Settings and stays.
- **Apps only mode** (Settings > Accounts & Sign In > the account) lives in launcherx. With launcherx disabled the
  switch is gone; enable launcherx, change it, disable launcherx again. Google TV takes Home back the moment
  launcherx is enabled, `set-home-activity` or not, so make the new launcher Home again afterwards and check
  `mFocusedApp`.
- **Network debugging** on port 5555 came back on its own after the reboot on this unit.

### The remote's buttons with launcherx disabled

The remote is `BT_RC923A_B1` over Bluetooth (vendor `0x0417`, product `0x0304`). Its key layout has no entry for
the app buttons, so the kernel reports `KEY_UNKNOWN` (scancode 240) and TCL's input layer maps the HID usage
(`MSC_SCAN` in `getevent -lt`) to a keycode; `logcat` shows each as a `TIDD:` line.

| Button | HID usage | Keycode | With launcherx disabled |
| --- | --- | --- | --- |
| Settings | `0x000c0055` | `NOTIFICATION` (83) | reaches the focused window; the launcher's `settings_key_keycodes` answers it on Home |
| Netflix | `0x000c0051` | 4062 | reaches the focused window, nothing opens |
| Prime Video | `0x000c005f` | 4099 | reaches the focused window, nothing opens |
| YouTube | `0x000c0057` | `BUTTON_3` (190) | a global key: Android sends it to launcherx's `GlobalKeyListener_Receiver`, no window sees it |
| Apple TV | `0x000c0086` | 4160 | reaches the focused window, nothing opens |
| TCL tv+ | `0x000c0150` | 4184 | TCL's `TclShortCutKeyManager` starts `tcl://com.tcl.tvplus`, which fails with TCL TV+ disabled |
| Disney+ | `0x000c004d` | 4141 | still opens: TCL starts it itself (codes from the sixth unit) |

Input, Back, Home, the Assistant button and volume work as before. `dumpsys window policy` lists the global keys
that go to launcherx on this build: `GUIDE`, `BUTTON_3`, `PROFILE_SWITCH`, `FEATURED_APP_1` and `MACRO_1`.

Since four of the buttons reach the focused window, a Home screen can answer them while it is on screen without
any accessibility helper; inside another app they go to that app. YouTube needs a key-filtering helper
(`InputKeyService` with `app_keys` `190:com.google.android.youtube.tv`), and with only that helper bound a held
volume button stepped once per press here too, with TV speakers and no soundbar
([TROUBLESHOOTING](../../docs/TROUBLESHOOTING.md#a-key-helper-is-bound-and-a-held-remote-button-stopped-repeating)).
That owner kept the volume ramp and went without the YouTube button.
## Third unit: TCL 55Q77K on Wi-Fi

TCL 55Q77K, model "Smart TV Pro", the same firmware `UTT2.250416.001 AU16`, on Wi-Fi. Set up on 25 October 2026
with this list less the streaming apps, AirPlay and Alexa (kept by that owner), then the setup wizard and
launcherx for the reference launcher. All of it came through a reboot the same day.

- **A wizard-owned network that Settings does not list.** After forgetting and joining the current network again
  in Settings, `tools/disable.sh` still skipped the wizard: an older network saved during first setup also
  belonged to it, and Settings > Network & Internet lists only networks in range, so it could not be forgotten
  there. `tools/tv.py 'cmd wifi list-networks'` shows every saved network with its id;
  `tools/tv.py 'cmd wifi forget-network <id>'` removed the stale one, the current network stayed connected, and
  `tools/disable.sh` then disabled the wizard without `FORCE_SETUPWRAITH`.
- **Power-on** lands on the new Home (`logcat -b events -d | grep wm_set_resumed_activity`); the firmware does
  not open Live TV by itself, so neither `power_on_home` nor `boot_to_live_tv` is needed.
- **Every app button back with one helper.** With `INPUT_KEY_SERVICE=1` and these `app_keys`, Netflix, YouTube,
  Prime Video, Apple TV and the Settings button opened their targets again, pressed on the real remote. TCL TV+ is disabled by this list, so its button (4184) can open any other app;
  `83:settings` answers the Settings button through the same helper, without `HOME_KEY_SERVICE`. Disney+ needs
  no entry: TCL still opens it itself.

  ```xml
  <string-array name="app_keys" translatable="false">
      <item>4062:com.netflix.ninja</item>
      <item>190:com.google.android.youtube.tv</item>
      <item>4099:com.amazon.amazonvideo.livingroom</item>
      <item>4160:com.apple.atve.androidtv.appletv</item>
      <!-- the TCL TV+ button, free once TCL TV+ is disabled: 4184:<package of your choice> -->
      <item>83:settings</item>
  </string-array>
  ```

  The held-volume trade described above applies to this helper; it was not measured on this unit.

## Fourth unit: Cast and the freezer

Another `G08_4K_US` set on `UTT2.250416.001 AU16`, in Google TV basic mode with no account, launcherx and the setup
wizard disabled for a custom Home app. Seen 22 and 23 October 2026.

- **Cast froze.** With launcherx off, `com.google.android.apps.mediashell` sat in the cached state and Android 14's
  freezer stopped it every few minutes (`isFrozen=true`, `curProcState=19`, and `ActivityManager: freezing <pid>
  com.google.android.apps.mediashell` in `logcat`). Phones found the TV slowly or not at all, and once its 8009
  connections sat in `CLOSE_WAIT`. Having the Home app bind `.CastReceiverService` (action
  `com.google.cast.action.BIND`) kept it at `vis (service)` in `dumpsys activity oom`, and it has not frozen since.
  Details and the caveat in
  [TROUBLESHOOTING](../../docs/TROUBLESHOOTING.md#working-on-the-tv), under "Casting disappears although the Cast
  receiver is still running".
- **Reinstalling the Home app can switch the TV to an input.** While Home restarts, TCL's player `com.tcl.tv` is next
  in the back stack: it resumes, re-tunes its last input (here the AirPlay input) and stays in front.
  `am force-stop com.tcl.tv` before `pm install -r` avoids it.

## Fifth unit: a 98-inch set on AU14

A 98-inch `G08_4K_US` set, model "Smart TV Pro", client type `TCL-NA-MT9653-S1`, firmware `UTT2.250416.001 AU14`
(older than the AU16 above), on Wi-Fi, with a soundbar on HDMI eARC. Its firmware reports `ro.odm.tcl.panel_size`
55, so that property says nothing about the real size. Set up on 13 November 2026 with this list less AirPlay and
Prime Video (kept by the owner), plus `com.tcl.ttvs` and `com.tcl.UpdatePeripheral`, which the owner wanted off
(no firmware or soundbar updates); then launcherx and the setup wizard for the reference launcher. Two days of use,
several reboots.

- **`com.tcl.ttvs` turned off cleanly.** `dumpsys package` shows what it carries: TCL's firmware updater
  (`versionUpdateApp`, the System update screens), BI telemetry, the Samba TV ACR service (`SambaVideoService`),
  T-Cast and Alexa hooks. With it disabled, Home, the Input menu, Live TV, HDMI switching from an app, Settings,
  Cast and AirPlay all kept working. The cost: Settings > System > About > System update needs it, so the TV takes
  no firmware updates until it is enabled again.
- **AirPlay never showed up, and the debloat was not why.** `logcat` repeated
  `FAIRPLAY_CA: ... Failed to call MTTZ_MFI_CopyCertFromTEE()` and `FairplayKey status = false`: the set had no Apple
  AirPlay key installed. Re-enabling every package from this list and rebooting changed nothing. Opening Settings >
  System > AirPlay and HomeKit Settings > Apple AirPlay and HomeKit made `com.tcl.airplay2` download the key from TCL
  and write it into the TEE (SELinux denials on `/dev/tee0` are logged during the write, but it succeeds). The TV
  then advertised `_airplay._tcp`, and the key stayed through reboots with `com.tcl.ttvs` disabled. Details in
  [TROUBLESHOOTING](../../docs/TROUBLESHOOTING.md#working-on-the-tv).
- **Energy Modes > Increased ("Always connected") leaves Low Power Standby on.** `low_power_standby_enabled` stays
  1 and the firmware sets it back to 1 at every boot, so apps lose the network while the TV sleeps
  (`blocked=LOW_POWER_STANDBY` in `dumpsys netpolicy`). Only the system and the shell can write that setting.
## Sixth unit: TCL 55Q77K with Google TV's home kept

TCL 55Q77K, model "Smart TV Pro", product `G08_4K_US`, the same firmware `UTT2.250416.001 AU16`, on Wi-Fi with
the TV speakers, remote `BT_RC923A_B1`, a Google account signed in. Set up on 14 November 2026 with
`tools/disable.sh`: this list without AirPlay (kept by that owner) and with launcherx left enabled, plus Pluto TV
and the Roku app, which this set had in /data/app (commented out in the list). The saved Wi-Fi network belonged to
the setup wizard, so it was forgotten and joined again in Settings before the wizard was disabled, as in the notes
above. Everything came through a reboot the same day, and each button below was pressed on the real remote.
### Why launcherx stayed enabled
With launcherx disabled and the new launcher as Home, the mic showed its listening icon in the corner and nothing
else. The Assistant ends the session a few milliseconds after it starts, and `logcat` has:
```
I/katniss_search_KatnissVoiceInteractionSession: #onHandleAssist
I/katniss_search_KatnissVoiceInteractionSession: AccountIdMissing
```
It looks like a third form of the voice failures in
[TROUBLESHOOTING](../../docs/TROUBLESHOOTING.md#working-on-the-tv) ("Sorry, not available in this profile yet"
and the voice results that never open): the account was signed in, and enabling launcherx again fixed it. The
Settings, Netflix and YouTube buttons did nothing in the same state. What kept all of them:
- launcherx enabled, and `stock_home_redirect` set to `com.google.android.apps.tv.launcherx` in
  `launcher/app/src/main/res/values/config.xml`.
- `launcher/build-and-install.sh` run in `launcher/` with `STOCK_LAUNCHER=none HOME_KEY_SERVICE=1`. The script
  checks that Home resolves to the new launcher, so it was run while launcherx was still disabled, and launcherx
  was enabled afterwards with `pm enable --user 0 com.google.android.apps.tv.launcherx`.
- Home and power-on land on the new launcher. Google TV shows for a moment at boot before the redirect.
- The mic, Netflix, YouTube, Settings and Apple TV buttons then work as on a stock set. Unlike the Philips TA5 in
  [App buttons stopped working after launcherx was re-enabled for the redirect](../../docs/TROUBLESHOOTING.md#app-buttons-stopped-working-after-launcherx-was-re-enabled-for-the-redirect),
  no `appops` step was needed.
- The same helper answers `app_keys` for the buttons whose apps the list disables. These were the owner's
  picks; any package works:
  ```xml
  <item>4099:com.stremio.one</item>          <!-- Prime Video button -->
  <item>4141:com.spotify.tv.android</item>   <!-- Disney+ button, free once Disney+ is disabled -->
  <item>4184:com.cbs.ott</item>              <!-- TCL TV+ button, free once TCL TV+ is disabled -->
  ```
### Held volume with the remapped buttons
The trade the third unit did not measure, measured here with `key_filter` on:
- While the helper filters keys, a held Volume button moves one step and stops.
- `consume_volume_keys` changed nothing: the helper never receives the volume keys on this board, as on the Xiaomi
  MOEU0 and Sony VU3 in
  [TROUBLESHOOTING](../../docs/TROUBLESHOOTING.md#a-key-helper-is-bound-and-a-held-remote-button-stopped-repeating).
- `"volume_pauses_filter": true` in the theme's `config.json`, pushed with `tools/theme.sh`, brings the ramp back
  as tap-then-hold: tap Volume once, then hold it within 4 s. A hold from cold still moves one step. During those
  4 s a remapped button goes to TCL instead and nothing opens; press it again after the pause.
- It is a theme key, so a theme pushed later needs it in its own `config.json` too.
### More remote buttons
Read from `getevent -lt` and the `TIDD:` lines in `logcat`, as for the second unit, with launcherx enabled:
| Button | HID usage | Keycode | Does |
| --- | --- | --- | --- |
| Input | `0x000c0049` | `TV_INPUT` (178) | TCL's input bar along the bottom (`com.tcl.suspension`) |
| ☰ | `0x000c0050` | 4514 | TCL's quick settings panel |
| 123 | `0x000c0052` | 4056 | away from Live TV, TCL shows "This page does not support virtual remote" |
| ⓘ | `0x000c0076` | `INFO` (165) | nothing on Home |
| Channel + / - | `0x000c009c` / `0x000c009d` | `CHANNEL_UP` (166) / `CHANNEL_DOWN` (167) | nothing on Home |
### Privacy settings
- **TCL:** Settings > Privacy > Terms & Privacy (`com.tcl.useragreement`). Unticking "Terms of User Experience
  Improvement Program" and "Share Diagnostics Data" and pressing Agree moves `settings get global tcl_uxp_status`
  from 1 to 0. `com.tcl.ttvs` still starts `BIService`, `UsagestService` and `TrackDataService` after a reboot, so
  whether they send less cannot be seen from the shell. Its `SambaVideoService` (Samba TV ACR) was not running.
- **Google:** Usage & Diagnostics (Settings > Privacy) is a slice from the setup wizard
  (`com.google.android.settings.usage.UsageDiagnosticsPreferenceProvider` in setupwraith), so with the wizard
  disabled OK on it opens nothing. Switch it off before applying the list, or enable setupwraith, change it, and
  apply the list again.
