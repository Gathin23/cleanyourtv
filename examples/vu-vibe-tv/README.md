# Vu VIBE TV

- **Model:** Vu VIBE TV (`ro.product.model` = `Vu VIBE TV`), device `bandra`, manufacturer KONKA, Realtek SoC
- **OS:** Google TV / Android 14 (SDK 34), firmware `UKRC.260302.018`
- **Region:** India
- **RAM:** ~1.3 GB

## Notes

- **Sideload:** APKs install over ADB with `pm install -r`; no `INSTALL_FAILED_VERIFICATION_FAILURE`, no
  HOME-signature block. SDK 34 is above the launcher's `minSdk` 26.
- **Home takeover:** the stock launcher is `com.google.android.apps.tv.launcherx`. On this build the setup
  wizard `com.google.android.tungsten.setupwraith` also claims Home and wins it once launcherx is disabled,
  so `build-and-install.sh` reports it and it must be disabled too. Its saved Wi-Fi network is owned by the
  system (uid 1000), not setupwraith, so disabling it does not drop Wi-Fi on this set. `set-home-activity`
  alone did not dislodge launcherx (higher priority); both launcherx and setupwraith have to be disabled.
- **Inputs:** HDMI 1-4 and AV are registered with `TvInputManager` by `com.realtek.tv.passthrough`, so the
  launcher's Inputs menu lists them natively. Live TV app is `com.realtek.tv` (set as `live_tv_package`).
- **HDMI 1 tile:** this set ships `com.mpiero.android.tvhdmi1`, a leanback-launchable shortcut that opens
  HDMI 1 directly. The `config.json` here pins it as the first dock tile labelled "HDMI 1". Pointing
  `live_tv_package` at it and setting `boot_to_live_tv` makes a cold boot land on HDMI 1 (not in this shared
  config; it drops the real tuner's Inputs row, fine only if you never watch broadcast).
- **Voice-to-text:** the Google Assistant / voice search (`com.google.android.katniss`) is disabled, but
  keyboard voice dictation is a separate service (`com.google.android.tts` voice IME) and still works; set
  `voice_recognition_service` to the Google TTS recognizer if a text field offers no mic.
- **Remote app buttons:** the remote has YouTube and Netflix buttons. The Realtek handler
  (`com.realtek.globalkeyhandler`) catches them and resends to launcherx (`GlobalKeyListener_Receiver`),
  so once launcherx is disabled they go dead. They send YouTube = keycode 190 (scancode 569), Netflix =
  191 (scancode 758); `app_keys` in the `config.json` here remaps them, installed with
  `INPUT_KEY_SERVICE=1`.
- **Key-filter trade:** this firmware drops volume auto-repeat whenever any key-filtering accessibility
  service is bound (the TCL G09 case in `docs/devices/`). With `InputKeyService` on and `key_filter` true
  the app buttons work but holding volume steps once per press; `key_filter` false brings the ramp back but
  the app buttons go dead. All-or-nothing here; this config keeps `key_filter` true (buttons over ramp).
- **Boot on AC power:** no working toggle found. `settings put global boot_power_on_key 0` had no effect;
  the set still needs a remote power press after mains is applied. Left at its default.
- **Private DNS:** an ad-blocking resolver works (Android 14). A LAN Pi-hole covers it without changing the
  TV.

## Keep / restore the stock Google TV launcher

launcherx is left installed but disabled. To switch back to it:

```bash
TV_HOST=<tv ip> tools/tv.py "pm enable com.google.android.apps.tv.launcherx"
TV_HOST=<tv ip> tools/tv.py "cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity"
```
