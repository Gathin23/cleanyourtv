# JVC LT-43KM348 (KTC, jagalchi), Google TV 14

KTC-built JVC on MediaTek mt5867 / T56, about 0.96 GB RAM, firmware `UKN7.260716.013`. The list is in
[`debloat.txt`](debloat.txt); [`config.json`](config.json) has a dock, the Live TV action and the YouTube and
Netflix buttons (needs `INPUT_KEY_SERVICE=1`).
Everything below was checked on one TV between 2026-10-22 and 2026-10-25.

## Connecting

- Port 5555 was closed at first. Wireless debugging (pairing code) worked; after `adb tcpip 5555` the tools in
  `tools/` connect as usual. Port 5555 closes again on every reboot.
- After a reboot, with Wireless debugging still on, `adb` reconnected over mDNS without a new pairing code.
- `screencap` works on Home and in apps; HDMI and the tuner come back as 0-byte files, as on most sets.

## Anoki ACR: what talks to whom

- `tv.anoki.acr.anokiacroptin` (priv-app, v3.2.7) is the ACR client. Its code fetches its configuration from
  `acr-config.aistrm.net` and has opt-in states (`ACR_STATE_SHOW_OPTIN`, `ACR_DECLINED`). The owner does not
  remember ever seeing a viewing-data prompt during setup.
- `com.cltv.hybrid` (Anoki / iWedia CLTV free channels, updated from the Play Store) carries an `F_ACR_SUPPORT`
  flag, a long list of ad-tech vendors in its consent SDK, and streams from `stream.aistrm.net`.
- `vendor.anoki.acrservice@1.0-service` is a vendor HAL (`vendor.anoki.acrservice@1.0::IAnokiAcr`). It keeps
  running after both apps are disabled and cannot be disabled without root, but it held no network connection of
  its own when checked (`/proc/net/tcp*` for its uid showed only adbd).
- **AdGuard Private DNS (`dns.adguard-dns.com`) does not block these hosts**: on 2026-10-25 the TV resolved
  `acr-config.aistrm.net`, `stream.aistrm.net` and `stream-stage.aistrm.net` to real addresses with AdGuard on.
  Disabling the two packages is what stops it, not the DNS extra.
- The TV has a second, never-started user (10, `new_user`) where both packages were still enabled:
  `tools/tv.py 'pm disable-user --user 10 tv.anoki.acr.anokiacroptin'`, same for `com.cltv.hybrid`.
  `tools/restore.sh` only handles user 0, so undo those with `pm enable --user 10 <package>`.

## Launcher handover

- The first `launcher/build-and-install.sh` run ended with Home on `setupwraith/.RecoveryActivity`; the script
  rolled back to launcherx as designed. setupwraith owned the network saved at setup (`dumpsys wifi |
  grep -o 'cname=[^ ]*'`), so the network was forgotten and joined again in Settings first. After disabling
  setupwraith, the second run moved Home to the new launcher.
- `dumpsys package providers` lists setupwraith's `CastInfoProvider` on this build, so Chromecast is expected to
  lose its credentials once setupwraith is off (see docs/TROUBLESHOOTING.md). The owner then disabled
  `com.google.android.apps.mediashell` too; casting was not retested. The Google TV phone app's remote
  (`_androidtvremote2._tcp`, `com.google.android.tv.remote.service`) keeps working.

## Remote buttons (Bluetooth remote RM-C3293)

Checked with a temporary `Log.d` in `InputKeyService.onKeyEvent`, on HDMI 1:

| Button | HID usage | What the launcher's key services see |
|---|---|---|
| TV | `0x0c008d` | `KEYCODE_GUIDE` (172), scancode 362. Only launcherx answered it; with it disabled it does nothing until mapped |
| Red / Green / Yellow / Blue | `0x0c0069`, `0x0c006a`, `0x0c006c`, `0x0c006b` | `KEYCODE_PROG_RED`..`PROG_BLUE` (183-186), scancodes 398-401 |
| Settings (gear) | `0x0c009f` (`KEY_UNKNOWN`) | Never reaches them. `com.mediatek.tv.service` (`HotKey_SettingsAction`) takes it and asks SystemUI's `TvNotificationPanel` for `android.app.action.TOGGLE_NOTIFICATION_HANDLER_PANEL` |
| D-pad down, OK | `0x0c0043`, `0x0c0041` | Not delivered to them either on this firmware |

- **The Settings button's side panel lives in launcherx** (`.dashboard.DashboardHandler`, the only handler of
  that action). With launcherx disabled, logcat shows `TvNotificationPanel: Not launching notification handler
  activity` on every press and nothing opens. Re-enabling launcherx brings the panel back, but launcherx then takes
  Home again (`set-home-activity` changes nothing) and costs about 190 MB here (about 130 MB for the app, 58 MB
  for `:coreservices`, which stays up). `stock_home_redirect: "com.google.android.apps.tv.launcherx"` with
  `HOME_KEY_SERVICE=1` bounced Home back to the new launcher within a second in testing; the owner chose the RAM.
- With `INPUT_KEY_SERVICE=1` (all testing was done with it on), Input, TV, YouTube and Netflix work and holding
  volume up still repeats. Not tried with the service off.
- `dumpsys window` routes `KEYCODE_BUTTON_4` (191, Netflix) to
  `com.mediatek.tv.service/com.mstar.netflixobserver.NetflixKeyReceiver` and `KEYCODE_BUTTON_3` (190, YouTube)
  and `KEYCODE_GUIDE` to launcherx; `config.json` maps 190 and 191 anyway.
- Mapping the TV button to Live TV needs a launcher change: `app_keys` launches a package's launch entry, and
  tvcenter has none (it starts only through `live_tv_action`).

## Picture and Live TV

- `android.settings.DISPLAY_SETTINGS` opens `com.android.tv.settings/.partnercustomizer.picture.PictureActivity`
  as a side panel over HDMI, with picture mode, **backlight**, brightness, contrast, saturation, tint and
  sharpness. That is the closest thing to the missing Settings-button panel.
- Android's display brightness does nothing here: `cmd display set-brightness 0.02` reaches the LightsHAL but the
  panel does not change. The backlight is the `picture_backlight` system setting, which apps cannot write.
- `com.mediatek.wwtv.tvcenter` has no launcher entry. `android.mtk.intent.action.ACTION_REQUEST_TOP_RESUME`
  opens it (`live_tv_action` in `config.json`). `com.mediatek.wwtv.tvcenter.nav.TurnkeyUiMainActivity.Menu` only
  brings the tuner to the front ("no signal" without an antenna), and the tuner's passthrough URI opens Google's
  generic input player, so neither helps.
- The sleep timer is `com.android.tv.settings/.partnercustomizer.timer.TimerActivity`, which shares
  `android.settings.DATE_SETTINGS` with Date & time: name the component to avoid a chooser.

## Memory

Measured with `dumpsys meminfo` after the list, the new launcher and HDMI 1 in front: about 250 MB free (22 MB
truly free plus reclaimable caches), ZRAM holding about 345 MB in 56 MB. The new launcher took 27 MB in the
background and about 90 MB on screen.
