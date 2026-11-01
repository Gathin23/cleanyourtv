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

## Re-enable an app

On the TV: Settings > Apps > See all apps > Show system apps (or "Disabled apps"), pick the app, Enable.
Over ADB:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm enable --user 0 com.netflix.ninja'
```

Then delete or comment out its line in `debloat.txt`, so a later run does not disable it again. To undo the
whole list: `TV_HOST=<tv ip> tools/restore.sh examples/philips-google-tv-ta1/debloat.txt com.google.android.apps.tv.launcherx`.
