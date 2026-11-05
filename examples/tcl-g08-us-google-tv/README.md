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
| Disney+ | | | still opens: TCL starts it itself |

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
