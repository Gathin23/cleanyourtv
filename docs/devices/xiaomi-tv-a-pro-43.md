# Xiaomi TV A Pro 43-inch (`MiTV-MZTU0` / `river`): original report and independent 2026 audit

Owner-reported 43-inch Xiaomi TV A Pro. Android reports the same `MiTV-MZTU0` model and `river`
codename as the existing 75-inch 2026 profile ([examples/xiaomi-tv-a-pro-2026](../../examples/xiaomi-tv-a-pro-2026/debloat.txt)), but this unit has a different firmware build. This is
an initial compatibility report, not a multi-day debloat list.

| Field | Observed value |
|---|---|
| Retail model | Xiaomi TV A Pro 43-inch (owner-reported) |
| System model / codename | `MiTV-MZTU0` / `river` |
| Board platform | `merak` |
| System | Google TV on Android 14 (SDK 34) |
| Firmware | `UKN1.240806.017.V816.0.26.6.2.6.URTMIXM` |
| Stock launcher | `com.google.android.apps.tv.launcherx` |
| Live TV app | `com.mitv.livetv` |

## Original report (#1285 / #1401)

The observations in this section and the package list below are from `doriel1243`'s
[original report](https://github.com/Gathin23/cleanyourtv/issues/1285), merged in
[PR #1401](https://github.com/Gathin23/cleanyourtv/pull/1401). That report did not establish the retail model year.

### Setup wizard and Home

CleanYourTV Setup left the stock Home screen alone and reported that the setup wizard owned the saved
Wi-Fi network. The owner had already forgotten Wi-Fi and connected Ethernet, but the warning remained.
ADB inspection showed `com.google.android.tungsten.setupwraith` as the Wi-Fi config owner. After
Ethernet was confirmed as the active default network, the setup wizard was disabled; the launcher
build-and-install script installed and started CleanYourTV, made it Home, and only then disabled the
stock launcher. After one reboot, Home still resolved to `com.gathin.tvhome/.MainActivity`.

Do not disable the setup wizard while the TV is still relying on its saved Wi-Fi network. Confirm
that Ethernet (or another network not owned by the wizard) is active first. Disabling it can also stop phone casting after a reboot, since the Cast receiver reads its credentials from setupwraith.

### Package state observed

This subset was adapted from the 75-inch `river` profile. Ten entries were already disabled before
this session; `com.google.android.tungsten.setupwraith` was the only newly disabled package in this
list. The stock launcher was disabled separately by `build-and-install` after the replacement Home
started successfully.

```text
# Xiaomi and Google telemetry
com.miui.tv.analytics
com.xiaomi.statistic
com.xiaomi.tvqs.overseas.y24
com.google.android.feedback

# PatchWall and sponsored/content rows
com.mitv.tvhome.atv
com.mitv.tvhome.michannel
com.mitv.tvhome.oemtab
com.xm.webcontent
android.autoinstalls.config.google.gtvpai
com.xiaomi.mitv.tvmanager

# Newly disabled for Home handover
com.google.android.tungsten.setupwraith
```

Streaming apps, voice search, Chromecast/casting and firmware updates were kept enabled by owner
request. SmartTube and TV Bro were pinned in the launcher's dock. The package state and launcher were
checked in one session and after one reboot; use this report as provisional until the owner has lived
with the setup for several days.

## Independent owner-reported 43-inch 2026 unit (31 October 2026)

This is a separate unit from the original report above. Its owner identifies it as **Xiaomi TV A Pro
43 2026**. Xiaomi has an [Indonesian product page for that retail model](https://www.mi.co.id/id/product/xiaomi-tv-a-pro-43-2026/specs/),
but this unit's rear-label SKU, purchase record and market variant have not been independently checked.
The Android model alone does not establish its retail year or screen size.

| Field | Observed value on the independent unit |
|---|---|
| Android model / device / board | `MiTV-MZTU0` / `river` / `river` |
| Board platform / boot hardware | `merak` / `mt5896` |
| Android / SDK | 14 / 34 |
| Build | `UKN1.240806.017.V816.0.26.6.2.6.URTMIXM` |
| Security patch | 5 May 2026 |
| Kernel | `5.15.192-android14-11-ga8e52546df5f-dirty`, build dated 2 July 2026 |
| Memory reported by the kernel | `MemTotal: 1827500 kB` |
| Display state, observed rather than changed for this report | Physical 3840x2160; existing override 1920x1080; density 320 |
| Resolved Home | `com.gathin.tvhome/.MainActivity` |

The [55-inch second-unit report](https://github.com/Gathin23/cleanyourtv/pull/1971) has the same full
build string, while the 75-inch report in [issue #90](https://github.com/Gathin23/cleanyourtv/issues/90)
has `V816.0.25.3.10`. Shared identifiers or firmware do not prove that panels, tuners, remote behavior,
Home handling or middleware are interchangeable. No 55- or 75-inch hardware was available for a direct
comparison. Do not apply their full list blindly to this 43-inch unit.

### State and verification limits

The pre-change backup recorded four disabled packages: `com.iwedia.tvinput`,
`com.mediatek.dtv.tvinput.atsctuner`, `com.mediatek.dtv.tvinput.isdbtuner` and
`com.xiaomi.floatingframe`. On 31 October the first three were still disabled. Floatingframe was absent from both the installed
and disabled inventories; this does not prove it was re-enabled. Its per-user installation state and
the cause of that difference remain unverified.

Fourteen additional packages were disabled during this unit's customization: the four telemetry
packages, the five PatchWall/content packages (including `com.mitv.tvhome.mitvplus`), TV Manager,
Play Games, Mi Music, setupwraith and launcherx. The state was rechecked after a reboot on 30 October
and by a read-only audit on 31 October. This is not a claim of several-day validation of every function,
and no new active debloat list is added here.

Unlike the 75-inch example, all three firmware updater packages remain enabled, as do Prime Video,
YouTube Music, `android.autoinstalls.config.google.gtvpai` and `com.google.android.apps.mediashell`.
An enabled package is not proof that its feature works: OTA installation, HDMI, tuner reception, Ethernet and USB media have not been functionally
validated on this unit.
The owner confirmed AirPlay works. Samsung Smart View and Windows `Win + K` initially did not find
the TV; after opening the built-in Miracast receiver app on the TV, both discovered it and connected
successfully. The owner separately tested Google Cast from Chrome after returning the TV to Home: the TV was
discovered and both picture and sound worked. AirPlay, Miracast and Google Cast are recorded as
separate tests, not inferred from their packages being enabled.

The owner forgot and rejoined Wi-Fi through TV Settings before setupwraith was disabled. Wi-Fi and
ADB remained available after the tested reboot. Both setupwraith and launcherx are currently disabled;
the replacement Home was resolved and observed after reboot. This report does not intentionally
reproduce the original crash-loop or claim to diagnose identical firmware behavior across sizes.

The physical YouTube button required the launcher's `InputKeyService` after launcherx was disabled.
The owner confirmed that YouTube opened, but holding a volume button became one step per press while
the helper was active; the owner chose to keep the YouTube mapping. After the latest reboot the helper
was bound, and the owner confirmed the physical YouTube button worked.

## Another unit on the same firmware (November 2026)

A third unit, same `MiTV-MZTU0` / `river` model and the same `V816.0.26.6.2.6.URTMIXM` build, with
1.8 GB of RAM. It was cleaned on 2 November 2026 with `PROMPT.md`. Everything below was checked across
reboots. This is not a claim of several-day validation of every function, and no new list is added here.

Seven batches disabled 31 packages, plus launcherx after the new launcher was Home: telemetry and feedback,
PatchWall and Mi channels, unused Xiaomi and Google apps, Privacy Sandbox, setupwraith, `com.google.android.apps.tv.dreamx`
and `com.mediatek.calman`. Calman Ready is the service for picture calibration from a PC; nothing else
missed it here. All three firmware updaters, Live TV and the tuners, AirPlay, the Assistant, the remote
microphone, Chromecast and the Play Store stayed enabled.

- **Setup wizard:** on this unit `dumpsys wifi | grep -o 'cname=[^ ]*'` named `android.uid.system`, so setupwraith
  was disabled while on Wi-Fi and Wi-Fi stayed up. The check still matters: the first report above found
  setupwraith as the owner. With setupwraith off, "Usage and diagnostics" in Settings no longer opens.
- **Netflix:** after a reboot, 31 of the 32 listed packages were still off. The firmware had turned
  `com.netflix.ninja` back on and reset its appops to allow. It was left on, kept off Home and out of the
  library (`hidden_apps`, `library_excluded_apps`) and stopped from running in the background, as
  [TROUBLESHOOTING](../TROUBLESHOOTING.md) describes: `cmd appops set com.netflix.ninja RUN_ANY_IN_BACKGROUND ignore`,
  the same for `RUN_IN_BACKGROUND`, then `cmd appops write-settings`. A reboot confirmed that it stayed.
  `tools/restore.sh` does not undo this: set both back to `default` and write the settings again.
- **A USB stick as internal memory:** this unit had one set up as adoptable storage and felt slow before
  the cleaning. That is the likely cause, not a proven one. The load average of 19 is not a busy CPU: about
  20 MediaTek driver threads wait in the kernel and the CPU was 94 % idle at that reading.
- **Storage:** system apps live on the read-only part, so disabling them frees nothing. Clearing speech data
  and trimming caches took `/data` free from 2.3 GB to 2.6 GB.
- **Wallpaper after a reboot:** Home showed no wallpaper until the theme was pushed again. Home starts before
  shared storage is mounted, so the launcher finds no wallpaper files at that moment.
- **Factory reset:** not tried. As a precaution, run `tools/restore.sh` first, with launcherx named too, so
  that the setup wizard and the stock launcher are on when the reset starts.

## The remote's buttons and a held volume key (November 2026)

The 43-inch unit above kept the helper and lost the ramp: a held volume button stepped once per press,
and its owner chose to keep the YouTube mapping. The model is not limited to that exchange.
`volume_pauses_filter` (off by default) makes a volume change stop the key helpers filtering for four
seconds, so the first press steps once, a press or hold inside those four seconds repeats as usual, and the
buttons answer again when the pause ends. It needs `key_filter` on, and
[`themes/README.md`](../../themes/README.md) documents it with the rest of the theme keys.

An owner of another `MiTV-MZTU0` / `river` set reported the result on
[#3360](https://github.com/Gathin23/cleanyourtv/issues/3360) on 17 November 2026: Google TV 14, build
`UKN1.240806.017.V816.0.25.1.14.2.URTMIXM`, launcher built from main of 15 November with local look changes
only, both helpers bound (`INPUT_KEY_SERVICE=1 HOME_KEY_SERVICE=1`) and `key_filter` on. Before the switch a
held Volume key moved one step per press. With `"volume_pauses_filter": true` a press followed by a hold
inside those four seconds ramped as usual on the physical remote, and holding Volume Down in a YouTube list
kept scrolling with the helpers still bound.

The exchange is unchanged: while the pause runs, the buttons the helpers filter do not answer, so the ramp
and those buttons take the remote by turns. That owner also recorded this model's keycodes for `app_keys`:
YouTube is `KEYCODE_BUTTON_3`, the Input button is `KEYCODE_TV_INPUT` (`KEYCODE_TV_INPUT:inputs` opens the
launcher's Inputs menu in place of Xiaomi's source picker), and the Settings button is `KEYCODE_SETTINGS`
(176), which `HomeKeyService` was already answering, not the `KEYCODE_NOTIFICATION` 83 that other Xiaomi
remotes send.

The mechanism, the other routes and what each of them costs are in
[A key helper is bound and a held remote button stopped repeating](../../docs/TROUBLESHOOTING.md#a-key-helper-is-bound-and-a-held-remote-button-stopped-repeating).

## Another unit on the same firmware, on Ethernet (25 November 2026)

Same `MiTV-MZTU0` / `river` model and `V816.0.26.6.2.6.URTMIXM` build, 1.8 GB of RAM, wired to its own Ethernet
port with no saved Wi-Fi network. It was cleaned with `PROMPT.md`: 29 packages, starting from the 75-inch list,
plus Privacy Sandbox, the store demo mode `com.xiaomi.floatingframe` (enabled at the factory on this unit) and the
streaming apps the household does not use. All three firmware updaters, the Assistant, Chromecast, AirPlay and
Miracast stayed enabled. Checked across two reboots on the day; this is not a claim of several-day validation.

- **Setup wizard on Ethernet:** `tools/disable.sh` skips setupwraith here, because with no saved Wi-Fi network
  `dumpsys wifi` names no owner to check, and its message asks for `FORCE_SETUPWRAITH=1` when the TV uses
  Ethernet. That was the case, and it was applied with the owner's agreement. The install run made before it
  stopped with Home resolving to setupwraith's `.RecoveryActivity` and put launcherx back, as designed. With
  setupwraith off, the Cast service still advertised itself and answered after each reboot; a cast session
  itself was not tried.
- **Wallpaper after a reboot, without ADB:** as on the third unit, a full reboot brings Home up with the theme's
  settings but no wallpaper (the launcher keeps a copy of `config.json` in its own storage for a start before
  shared storage is mounted, not the pictures). Pressing Home and waiting for the next rotation did not bring them
  back. Changing a value in Launcher settings does, because Home rebuilds when its settings change: hold OK on
  the Settings pill, choose Launcher settings, set Clock to 24-hour and back to System, then press Back. Choosing
  the value that is already selected changes nothing. Force stop in Settings > Apps would also restart Home, but it
  takes the key helpers off. Standby from the remote is not a reboot, and the pictures stay.
- **The other remote buttons:** Netflix is keycode 193 (`KEYCODE_BUTTON_6`) and Prime Video 194
  (`KEYCODE_BUTTON_7`). Without a key helper both go to `mitv.service`'s `GlobalKey` (`ACTION_GLOBAL_BUTTON`),
  which opened Netflix (back on after every boot) and only logged an error for the disabled Prime Video. With
  `INPUT_KEY_SERVICE=1`, `app_keys` entries `"193:<package>"` and `"194:<package>"` took both buttons ahead of it,
  checked with the real remote. Red is `KEYCODE_PROG_RED`, and
  `"KEYCODE_PROG_RED:com.android.tv.settings/com.xiaomi.gtv.settings.display.picture.MiPictureActivity"` opens
  Xiaomi's picture panel (picture mode, backlight, eye comfort) over whatever is on screen, a one-press brightness
  control; a colour key taken this way no longer reaches Live TV's teletext. With only `InputKeyService` bound,
  the Settings button (176) did nothing until `"176:settings"` was added, and the Input button kept opening
  Xiaomi's source picker (`com.mitv.livetv/.input.SelectInputActivity`), as there was no `inputs` entry.
- **Reading the keycodes:** `tools/tv.py 'getevent -lt'` returned no output, presses included, before tv.py's
  120-second read limit. This build logs real presses (not injected ones) as
  `WindowManager: interactive:true,keyCode:<n>,action:0`, so `tools/tv.py 'logcat -d | grep "WindowManager: interactive"'`
  right after the presses gave the codes above. Later in the same session, after the set had been through
  standby, those lines no longer appeared.
- **Waking from the network:** Energy modes (Settings > System > Power & Energy) was on the highest setting,
  "Always connected". In standby the system suspends (`System STR on ... wakeup_reason=cast` in the log) and the
  Ethernet controller wakes it on network traffic. Home Assistant's Android TV Remote integration turned the TV on
  from standby. While it was asleep, 2 of 8 TCP connections to the remote port 6466 timed out, so a first
  "turn on" can fail where a second one works. A Wake-on-LAN magic packet to the Ethernet port's MAC address
  turned the screen on about 10 seconds later, from a standby deep enough that the set no longer answered ping.
  The lower energy modes were not tried.
- **Apps:** the userspace is 32-bit only (`ro.product.cpu.abilist` is `armeabi-v7a,armeabi`), so a sideloaded
  APK needs its armeabi-v7a or universal build; SmartTube's armeabi-v7a stable build installed and opened.
  `tools/tv.py --push` stalled on that 25 MB APK, while the Android SDK's `adb push` took two seconds over the
  same network connection, with no new authorisation prompt: with `tools/adbkey/` empty, `tools/tv.py` signs with
  the same `~/.android/adbkey`. The Play Store offers Nuvio as `com.nuvio.app` (not `com.nuvio.tv`) and Jellyfin
  as `org.jellyfin.androidtv`; it does not offer the Home Assistant app on this set ("not compatible").
