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

## Third unit: TCL 55T7B (Turkey)

A **TCL 55T7B** (2024) on the same `G10_4K_GB` build, `STT2.230203.001` incremental AS50, on Wi-Fi. It ran
`debloat.txt` with `com.tcl.ttvs`, Spotify, Google TV / Play Movies and Play Games kept, plus the 55T7B extras at
the end, for four days and four reboots, the last day with the Clean Your TV launcher as Home and both key helpers
(`InputKeyService` and `HomeKeyService`) on.

- **`com.tcl.ttvs` kept.** Its `dumpsys package` lists `SystemUpdateActivity` and `SystemUpdateReceiver`, so on this
  build it is the firmware updater as well as TCL's analytics.
- **setupwraith owned the saved Wi-Fi network** here (`cname=com.google.android.tungsten.setupwraith`), unlike the
  55C655. The first `launcher/build-and-install.sh` run stopped at its Home check (Home went to
  `setupwraith/.RecoveryActivity`) and put launcherx back. Forgetting the network and joining it again in Settings
  moved it to `com.android.tv.settings`; then setupwraith was disabled, the TV stayed online, and the second run
  handed Home to the launcher.
- **Casting from a phone still works** with setupwraith disabled (YouTube from a phone).
- **With launcherx enabled it always wins Home** (intent-filter priority 2): `cmd package set-home-activity` and the
  launcher's Choose default home both answer Success and Home stays on launcherx. Switching back to the stock home
  for a while means `pm enable` and later `pm disable-user` from ADB.
- **Remote:** the same keycodes as the table above. The Guide button (4001) mapped to `4001:com.tcl.tv` opens Live
  TV; without an entry, `com.tcl.tv`'s `GlobalKeyService` turns it into `KEYCODE_GUIDE`, which nothing answers on
  Home. The gear (83) with `settings_key_keycodes` and `settings_key_panel` opens the launcher's panel. Held volume
  moves one step per press with the helpers on, as on the 55C655.
- `com.tcl.channelplus` (TCL Live): right after it was disabled its channels stayed in Live TV's list without playing;
  after the next reboot they were gone.

## Fourth unit: TCL 43T69C (Italy)

A **TCL 43T69C** (2024) on product `G10_4K_GB_NF_32BIT` (the 32-bit build of the same platform, 1.8 GB), firmware
`STT2.230203.001` incremental AS50, on Wi-Fi. It ran `debloat.txt` with Netflix and Prime Video kept and the 43T69C
extras at the end, for three days and three reboots, with the Clean Your TV launcher as Home and `HomeKeyService` on.

- **Wi-Fi:** the saved network belonged to setupwraith (`cname=com.google.android.tungsten.setupwraith`), as on the
  55T7B. Forgetting it and joining again in Settings moved it to `com.android.tv.settings`; setupwraith and launcherx
  then went in one `tools/disable.sh` run and the TV stayed online.
- **`com.tcl.interactive`: re-enabled on this unit.** With it off, pressing Up on a broadcast channel showed
  nothing, and `com.tcl.tv` logged `tias_sdk ... getService failure` on every press. It is TCL's HbbTV engine as
  well as TIAS: Up opens the channel's own HbbTV page (RaiPlay on Rai channels, Mediaset's on Mediaset ones).
  On Rai 1 the HbbTV app sometimes takes OK too, so OK opens the channel list only some of the time there;
  Settings, System, HbbTV settings turns HbbTV off. `com.tcl.channelplus` (TCL TV+) is not needed for either key.
- **`com.tcl.gamebar` came back once:** a TCL app update pushed through `com.tcl.ttvs` reinstalled it, which
  re-enabled it (`dumpsys package com.tcl.gamebar` showed `lastDisabledCaller: com.tcl.ttvs`). Check
  `pm list packages -d` after a TCL update.

### Remote buttons (this unit's remote)

Read from `logcat` (`WindowManager: handleKeyBeforeQueueing ... ACTION_DOWN`) while the owner pressed each button;
every row was checked with the real remote, with launcherx disabled.

| Button | keyCode | scanCode | With launcherx disabled | Mapped with |
|---|---|---|---|---|
| YouTube | 4065 | 569 | dead | `4065:org.smarttube.stable` |
| Netflix | 4062 | 242 | dead | `4062:com.netflix.ninja` |
| Prime Video | 4099 | 752 | dead | `4099:com.amazon.amazonvideo.livingroom` |
| Disney+ | 4141 | | dead | `4141:<any app>` |
| TCL Channel | 4020 | | dead (`com.tcl.waterfall.overseas` is disabled) | `4020:<any app>` |
| TV | 4001 | 365 | dead: the firmware turns it into `KEYCODE_GUIDE` | `4001:com.tcl.tv` and `front:4001:guide:com.tcl.tv` |
| Gear | `KEYCODE_NOTIFICATION` (83) | 233 | dead | `settings_key_keycodes` 83 |
| Media player | 4057 | 236 | works (opens `com.tcl.ui_mediaCenter`) | |
| ☰ | 4514 | | works (TCL's quick menu: brightness, picture mode, sound, sleep timer) | |
| Input | `KEYCODE_TV_INPUT` | 417 | works | |

- YouTube is scancode 569 here, not 245 as on the 55C655: map it by keycode.
- **Volume:** with `HomeKeyService` bound, a held Volume Up moves one step per press, as on the other units.
  `volume_pauses_filter` gave the ramp back after a tap: tap once, then hold within four seconds, and it repeats at
  the firmware's speed; a cold hold is still one step. The app buttons kept working. The owner kept that setup.

### Settings worth knowing

- **Standby LED:** Settings, System, Advanced video, "Standby indicator bar" (Italian: "Barra per l'indicazione
  dello standby"), Off. It writes `tcl_sys_lights_led_indicator_standby=0`. There is no setting for a light while the
  TV is on: writing `tcl_sys_lights_led_indicator_working=1` by hand changes nothing.
- **Auto store mode** ("Modalità Negozio Automatica") is on from the factory on the same page.
- **The Display & Sound side panel** (Picture, Screen, Sound, Audio output), which launcherx's gear used to open, is
  TCL's `ShowWindowService`: `am start-foreground-service -a com.tcl.settings.SHOW_WINDOW -p com.android.tv.settings
  --es Type device_display_sound`. The gear button, Settings, Display & Sound opens it too.
- The UI runs at 1920x1080 on the 3840x2160 panel (`wm size` shows the 1080p override from the factory).
