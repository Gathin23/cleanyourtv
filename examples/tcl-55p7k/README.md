# TCL 55P7K / 55C655 (G10, Google TV 12)

Notes from the second unit, a **TCL 55C655** (2024, Italy) on the same build as the 55P7K:
`G10_4K_GB`, `STT2.230203.001 AS16`, Realtek RTD2875P, Bluetooth remote `BT_RC833_U1` (vendor `0x0417`, product
`0x0304`). It ran `debloat.txt` with the streaming apps, Alexa and T-Solo kept, plus the extras at the end, for a
day and two reboots, with the Clean Your TV launcher as Home and `HomeKeyService` on
(`launcher/build-and-install.sh` with `HOME_KEY_SERVICE=1`).

## Order that worked

- `tools/disable.sh` skipped `android.autoinstalls.config.google.gtvpai` on the first run: its `dumpsys` has no
  permission block for the input-driver check. With Live TV and HDMI checked on the real remote after a reboot,
  `TVINPUT_ALLOW=1` disabled it.
- The saved Wi-Fi network already belonged to `android.uid.system` (`dumpsys wifi | grep -o 'cname=[^ ]*'`), so
  setupwraith went with launcherx in one run, with no re-join.
- With launcherx still enabled, `build-and-install.sh` stops at its Home check (Home still resolves to launcherx),
  before it turns the key helper on. Disabling setupwraith and launcherx in one `disable.sh` run then hands Home to
  the launcher; run the install script again for `HomeKeyService`.

## Remote buttons

With launcherx disabled the app buttons do nothing: TCL sends its own keycodes and only launcherx answered them.
`HomeKeyService` brings them back from `app_keys` and `settings_key_keycodes` in `config.xml`. Scancodes from
`getevent -lt`, names from `/system/usr/keylayout/Generic.kl` (the remote's own `Vendor_0417_Product_0304.kl` is not
readable from the shell), numbers from `KeyEvent.keyCodeFromString` run on the TV through `app_process`. Every row
was checked with the real remote.

| Button | Scancode | Name | Keycode | `app_keys` entry used |
|---|---|---|---|---|
| YouTube | 245 | `TCL_YOUTUBE` | **4065** | `4065:com.google.android.youtube.tv`; this unit used `4065:org.smarttube.stable` |
| Netflix | 242 | `TCL_NETFLIX` | **4062** | `4062:com.netflix.ninja` |
| Prime Video | 752 | `TCL_PRIMEVIDEO` | **4099** | `4099:com.amazon.amazonvideo.livingroom` |
| TCL Channel | — | `TCL_QUICK_MENU` | **4020** | `4020:com.tcl.tv` (Live TV) |
| Guide | 365 | `TCL_EPG` | **4001** | `4001:com.tcl.tv` and `front:4001:guide:com.tcl.tv`: Live TV, then its guide |
| TCL Home | 204 | `TCL_DASHBOARD` | **4137** | `4137:com.gathin.tvhome/.QuickSettingsActivity` (the launcher's panel) |
| Web | 651 | `TCL_BROWSEHERE` | **4159** | any app; this unit has no browser and used `4159:com.esaba.downloader` |
| Settings (gear) | 233 | `NOTIFICATION` | **83** | `settings_key_keycodes` 83 |
| Profile (person icon) | 653 | `PROFILE_SWITCH` | 288 | not mapped: Google TV profiles went with launcherx |

- **YouTube is 4065 here, not 190** (`KEYCODE_BUTTON_3`, the Chromecast and G08 value): an entry for 190 does nothing.
- **TCL Home** still opens TCL's own side panel (`com.tcl.dashboard`) with no entry, but its user icon leads to
  `com.tcl.usercenter`, which the list disables; this unit gave the button to the launcher's panel instead.
- **Web** opens BrowseHere on sets that have `com.tcl.browser`. This one does not: with `com.tcl.interactive`
  disabled, a press only makes `com.tcl.tv` retry `tcl_tias_tbrowser` for about 13 seconds, then stop.
- Home, Home held (`3:home` plus `hold:3:com.gathin.tvhome/.QuickSettingsActivity`), Input, D-pad and the
  microphone work. The Assistant answers voice with launcherx disabled.
- **Price:** with `HomeKeyService` filtering keys, Volume Up held moves one step per press, as on the TCL G09
  (`docs/devices/tcl-55p71b-g09-key-repeat.md`).
- The notification panel and Google TV profiles were launcherx's and are gone.

## Power-on

- From standby (the remote's power button) the set resumes where it was: it never really shuts down.
- After a full reboot the firmware brings `com.tcl.tv` to the front about seven seconds after Home starts
  (`logcat -b events | grep wm_set_resumed_activity`). This unit kept that; `power_on_home` would send it back to
  Home.
