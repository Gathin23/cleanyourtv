# Philips Google TV TA1 (65PUS8729/60)

Philips 65PUS8729/60 (TPV, PH1M_WW_9972, MediaTek), Google TV 14, build UKN2.241117.001, about 2.8 GB RAM.
Debloated on 2026-10-25 and given a custom launcher.

| File | What it is |
|---|---|
| `debloat.txt` | The packages that were disabled, in the two batches they were applied, with what each one is |

All commands run from the repository root and need the Python client once:
`python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt`.
They also need network ADB: Settings > System > About > press Build 7 times, then Developer options >
USB debugging on, and accept the "Allow USB debugging?" prompt the first time a command connects.

## What changed

- **Disabled** (never uninstalled): TP Vision analytics (`candeebug`, `gel`), partner attribution and
  auto-installs, the retail demo, Samba TV, Control4, AirPlay, HbbTV, Google text-to-speech, Play Games,
  calendar sync, Netflix, Prime Video, the Google TV films app, YouTube Music, ivi, the built-in browser,
  the Philips PlayFi and NFT gallery apps, the setup wizard and the stock Google TV launcher.
- **Kept**: everything that plays HDMI and the tuner, the input menu, remote pairing, keyboards, Play Store
  and Play Services, Assistant, Chromecast (phone casting may stop after a reboot with setupwraith off), firmware updates.
- **Wi-Fi first**: the saved network was owned by `setupwraith`, and disabling it while it did would take the
  TV off Wi-Fi and ADB. The network was forgotten and added again before the list ran; check
  `dumpsys wifi | grep -o 'cname=[^ ]*'` on yours before applying the last two lines.
- **Memory**: about 100 MB more free after batch 2 (used PSS 1.62 GB to 1.52 GB), launcher frames 0.28 % janky.

## Check

A cold reboot after the list brings the TV back to Home with ADB and Wi-Fi, no crashes in the crash log.
HDMI and Live TV were not exercised on this unit.

## Second set: Philips Google TV TA5

Same device (`PH1M_WW_9972`) and build (`UKN2.241117.001/3000669`, patch 2026-07-05), Netherlands, sound over
HDMI eARC to a soundbar, no tuner in use. Batch 3 of `debloat.txt` is this set's; its header says what was kept.
Free memory on Home went from about 0.71 GB to 0.98 GB after a reboot (`MemAvailable`).

### Remote buttons with launcherx disabled

`org.droidtv.GlobalKey` routes the remote. Logged on this set with the stock launcher disabled:

| Button | Scancode / keycode | What GlobalKey does | Without launcherx |
|---|---|---|---|
| Gear | 141 / `KEYCODE_GLOBAL_FUNCTION` | starts `launcherx/.dashboard.DashboardActivity` by component | nothing (result code -92) |
| Apps (3x3 grid) | 801 / `KEYCODE_LOCAL_OTT1` | starts `android.intent.action.ALL_APPS` | nothing (-91: no handler) |
| Netflix, YouTube, Prime Video | - | starts the app itself | works |
| Volume | - | - | works, and keeps repeating when held |

`HomeKeyService` or `InputKeyService` bound with key filtering on cost the volume ramp here (one step per press),
as on the other sets in `docs/devices/tcl-55p71b-g09-key-repeat.md`.

### All buttons and the volume ramp together

The owner wanted every button and the ramp, which took the Sony VU3 setup from `docs/TROUBLESHOOTING.md`:

1. `pm enable com.google.android.apps.tv.launcherx`, and in the theme's `config.json`
   `"stock_home_redirect": "com.google.android.apps.tv.launcherx"` and `"key_filter": false`.
2. `HomeKeyService` enabled (`HOME_KEY_SERVICE=1`). Home now opens launcherx for a moment and lands on this
   launcher; the gear opens the Google TV dashboard again; holding volume still ramps.
3. With launcherx enabled, GlobalKey hands the Netflix, YouTube and Prime Video launches to launcherx, and
   Android blocks them as background starts (`BAL_BLOCK`, result code 102) while this launcher is in front.
   `appops set com.google.android.apps.tv.launcherx SYSTEM_ALERT_WINDOW allow` brought all three back.
4. The apps button sends `ALL_APPS`, which launcherx now answers too, so Android showed a chooser on the first
   press; choosing this launcher and "Always" once settled it.

### "Switching to audio system" at every power-on

With a soundbar on eARC, every power-on showed a two-second toast from `org.droidtv.tv.tpvaudiomanager`:
"Switching to audio system. We recommend using an Ultra High Speed HDMI cable." The cable was fine. The
package's notification importance is fixed, Do Not Disturb does not stop it, and taking launcherx out of
`android.app.role.SYSTEM_TELEVISION_NOTIFICATION_HANDLER` (`cmd role remove-role-holder`) only lasts until
the next boot. What stayed off across reboots is launcherx's notification listener, which draws these toasts:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'cmd notification disallow_listener com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.coreservices.notificationlistener.TvNotificationListenerService'
```

No other notification pops up on screen after that. Undo with `allow_listener` and the same component.

## Third set: 55OLED718/12

A Philips 55OLED718/12 (OLED, Bulgaria) reports the same model, board and build. Debloated on 2026-10-27,
wired Ethernet, lived with for six days. The details are at the end of `debloat.txt`; what differed:

- **`demome` stayed**: `tools/disable.sh` skipped it on that unit because it requests `TV_INPUT_HARDWARE`.
- **setupwraith and launcherx stayed enabled**, for Chromecast (setupwraith holds the Cast credentials) and the
  saved Wi-Fi networks setupwraith owned. With launcherx disabled, setupwraith's `RecoveryActivity` took Home,
  and `pm disable-user` on that one activity, or on launcherx's `.home.HomeActivity`, is refused
  (`Shell cannot change component state`). The launcher went on with `STOCK_LAUNCHER=none HOME_KEY_SERVICE=1`.
- **Home without the Google TV flash**: GlobalKey starts `launcherx/.home.HomeActivity` by component, so
  `stock_home_redirect` alone showed Google TV for about 0.1 s on every Home press. With `"key_filter": true` and
  `"home_long_press_home": true` in the theme, `HomeKeyService` takes the Home key first and Google TV never
  starts (`ActivityTaskManager: START` shows only this launcher). The gear still opens the Philips quick panel
  (`org.droidtv.philipstvdrawer`), and Apps opens this launcher.
- **Volume**: the price of that is the volume ramp, as on the TA5 set: a held key moves one step. Firmware
  applies only volume changes that come from a key; `media volume --adj` and `--set` from the shell changed
  nothing, so `consume_volume_keys` cannot repeat it either. The owner chose no flicker over the ramp.
- **Screensaver**: with `dreamx` disabled and still the selected screensaver, the TV went to sleep after the
  10-minute screensaver timeout instead. Selecting the launcher's clock screensaver fixed it:
  `settings put secure screensaver_components com.gathin.tvhome/.ClockDream`.
- **Mimi kept**: with `io.mimi.sdk.service` and `io.mimi.tvapp` disabled, HBO Max dropped 33 to 46 % of its
  frames in some playback sessions (`dumpsys media.metrics`; MediaTek's tunneled A/V sync failed at the start,
  `DVB_DECCMD_AVSYNC`), with the CPU far from busy. After both were enabled again it played smoothly for the two days since.
  One set and one app, so not proven, but worth keeping Mimi if a streaming app stutters.
- **Second profile**: a second Google TV profile is Android user 10 with about 70 packages; the matching ones were
  disabled with `pm disable-user --user 10`, which `tools/disable.sh` and `restore.sh` do not cover.
- **Memory**: about 920 to 960 MB available with the launcher, a Home Assistant add-on and YouTube running.

## Re-enable an app

On the TV: Settings > Apps > See all apps > Show system apps (or "Disabled apps"), pick the app, Enable.
Over ADB:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm enable --user 0 com.netflix.ninja'
```

Then delete or comment out its line in `debloat.txt`, so a later run does not disable it again. To undo the
whole list: `TV_HOST=<tv ip> tools/restore.sh examples/philips-google-tv-ta1/debloat.txt com.google.android.apps.tv.launcherx`.
