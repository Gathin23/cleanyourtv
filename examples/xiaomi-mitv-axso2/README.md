# Xiaomi MiTV-AXSO2: notes from a second unit

A second MiTV-AXSO2 (`dangalUHD`, India) on the same firmware as `debloat.txt`'s header, PI.6432, Android TV 9
(SDK 28). The list itself is unchanged: this unit ran its active lines, applied in five small batches with a
screenshot after each, plus personal choices that stay out of the shared list (Mi Cast and Miracast). It ran
them for an afternoon, not the several days a list change asks for, so these are notes rather than edits.

## What held on this unit

- **Every package stayed disabled across a reboot.** `pm list packages -d` after `reboot` matched what was
  disabled; the firmware re-enabled nothing.
- **Home handover was clean.** `launcher/build-and-install.sh` found `com.google.android.tvlauncher` as Home,
  started the new launcher, disabled the stock one and set Home with no `STOCK_LAUNCHER` or `FORCE_HOME`. Only
  `com.android.tv.settings/.system.FallbackHome` claimed HOME besides the new launcher afterwards.
- **Power-on lands on the new Home.** `logcat -b events` showed `FallbackHome`, then the launcher; Live TV did
  not take the screen, so neither `power_on_home` nor `boot_to_live_tv` was needed.
- **The remote needs no key helper.** With neither `HomeKeyService` nor `InputKeyService` enabled, the real
  Home, Netflix, Prime Video, Assistant (mic), Settings and Back buttons all did what they should, and a held
  Volume Up still ramped.
- **Inputs menu:** HDMI1, HDMI2, HDMI3, AV and Live TV (`com.android.tv`, as the list's header says).

## Watch out for

- **setupwraith owns the saved Wi-Fi network here.** `dumpsys wifi | grep -o 'cname=[^ ]*'` printed
  `cname=com.google.android.tungsten.setupwraith`. The list does not touch setupwraith, and it must stay
  enabled on a unit set up like this one: disabling it deletes the network, and the TV drops off Wi-Fi and ADB.
- **The Wi-Fi pill says "Wi-Fi", not the network name,** even with `ACCESS_FINE_LOCATION` granted: Android 9
  hides the name while the TV's Location setting is off, which it is here by default.
- **Storage differed from the first unit:** this one had about 3.7 GB free on `/data`, not the 600 MB the
  header mentions, so the Play Store error 1008 note may not apply to every unit.

## Sound settings

The Sound panel (`com.droidlogic.tv.soundeffectsettings`, kept) stores its values in `settings list global` as
`sound_effect_sound_mode`, `sound_effect_sound_mode_eq`, `sound_effect_treble`, `sound_effect_bass`,
`sound_effect_surround`, `sound_effect_agc_on`, `sound_effect_dialog_clarity` and `virtual_surround`, all at
their defaults on a unit nobody had tuned: mode 0, treble, bass and balance 50, surround, AGC and dialog
clarity off. How a mode picked
in the panel maps onto those keys was not worked out here.

## Android 11 firmware (`RTT0.211201.001.1150`)

A third unit, a 43" Mi TV 4X sold in India (`L43M4-4AIN`), runs Android 11 (SDK 30, 2 GB RAM, about 4.4 GB
`/data`) instead of the Android 9 firmware above. Tried for three days.

- **The list applies as it is.** Entries the build does not have (the Sensara guide, Play Music, the Android
  camera and gallery, the TCL browser, an old Zee5 copy and the Xiaomi auto-install config) are skipped by
  `tools/disable.sh`; the rest disabled cleanly and stayed disabled after a reboot.
- **Live TV is `com.android.tv`**, as the header says: set `live_tv_package` to it and hide it in
  `launcher/app/src/main/res/values/config.xml`.
- **Home handover was clean.** `com.google.android.tvlauncher` was Home; `launcher/build-and-install.sh`
  disabled it after the new launcher was confirmed, with no override. The saved Wi-Fi network is owned by the
  system here, not by setupwraith, so nothing was lost.
- **Unlike the Android 9 unit, the Home key helper costs hold-to-repeat.** With `HOME_KEY_SERVICE=1` (needed for
  the Apps button and `power_on_home`) holding Volume Up stepped once per press instead of ramping. Without
  the helper the Home, Netflix, Prime Video, JioHotstar, source and Google buttons all worked; only the Apps
  button did nothing.
- **A restart opened the last HDMI input.** The firmware starts Live TV on the remembered input
  (`tv_current_inputid` in the `system` settings). Changing the TV's own power-on / input setting fixed it,
  with no helper and no ADB.
- **RAM is tight:** about 150 MB free with JioHotstar open. One launch from the remote and the launcher bounced
  back to Home once and could not be reproduced; the app's process is killed in the background and starts cold,
  which takes about 5 s to draw.
