# TCL C06 / TV+ OS (Brazil) — debloat + Apple TV style launcher, second unit

**TV:** TCL "Smart TV", client type `TCL-LA-MT9221-01` (TV+ OS), device C06, build AR2101,
software `V8-T221T01-LF1V034.000077`, TV+ OS `V5.2.0 (V8-2203260-MF1V520)`, Android TV 11
(SDK 30), MediaTek MT9221, kernel 4.19.116 armv7l, 1280x720 @ 213dpi, ~940 MB RAM
(`low_ram=true`). Same platform as the TCL L40S66E (V030) and as the first Brazilian unit of
`tcl-40s5400a`, on an older build.

![stock home](stock-home.png)
![new home](new-home.png)

## What happened here

One session on this unit, not a several-day test. The debloat ran in three waves (TCL block, voice,
Google/duplicates), each followed by a sync, a wait and one reboot, with a screenshot and a
Chromecast check after every wave and after the reboot; the launcher was installed as Home and the
stock launcher disabled afterwards, as in the list of `tcl-40s5400a`.

## Disabled

14 packages in three waves (TCL block, voice, Google/duplicates), all with
`pm disable-user --user 0`, never uninstalled:

- TCL account/promos: `com.tcl.usercenter`, `com.tcl.waterfall.overseas`, `com.tcl.esticker`,
  `com.tcl.useragreement`, `com.tcl.copydatatotv`, `android.autoinstalls.config.tcl.device`
- Voice: `com.tcl.micmanager`, `com.tcl.smartalexa`, `com.google.android.tv.assistant`
- Google/duplicates: `com.google.android.partnersetup`, `com.google.android.onetimeinitializer`,
  `com.google.android.syncadapters.calendar`, `com.tcl.android.webview`,
  `com.google.android.apps.nbu.smartconnect.tv` (a data-saver VPN, not casting)

`com.google.android.feedback` was already off at the factory and was left alone. So were
`com.google.android.katniss`, `com.google.android.tvrecommendations` (Leanback Core Services
on this chipset), TalkBack, TTS, backdrop, Netflix, Prime Video, Stan and the other preloads.

**Back after reboot:** `com.tcl.usercenter` and `com.tcl.smartalexa` are switched back on by
this firmware at every boot, as on the Brazilian unit in `tcl-40s5400a`. They stay idle;
they are not re-disabled after each boot. A sideload test also re-enabled `com.tcl.esticker`,
which was disabled again. Final count: 28 disabled (15 at baseline + 14 minus the two that
came back, plus esticker re-disabled).

## Kept on purpose

`com.tcl.tv` + `com.tcl.tvinput` (Live TV, HDMI, AV, tuner — four inputs registered),
`com.tcl.suspension` (source Quick Panel), `com.tcl.t_solo`, `com.tcl.keyhelp`,
`com.tcl.autopair` + `com.tcl.tcl_bt_rcu_service` (Bluetooth remote), `com.tcl.system.server`
and `com.tcl.factory.view` (PERSISTENT), `com.tcl.guard` (PERSISTENT on this unit — a vendor
source warns that freezing PERSISTENT packages leaves the system inconsistent),
`com.tcl.settings`, `com.tcl.providers.config`, `com.tcl.initsetup`, `com.tcl.dashboard`,
`com.tcl.messagebox`, `com.tcl.partnercustomizer` (TKey button), `com.tcl.rc.ota`,
`com.tcl.ttvs` (firmware updater; owner choice to keep for Cast safety), `mediashell` +
Play services (Chromecast), `com.google.android.tv.remote.service`, and `setupwraith`
(it owns the saved Wi-Fi network via `cname=`).

## Launcher

Built in Docker (`tools/docker-build.sh`), installed with `launcher/build-and-install.sh`,
which disabled `com.google.android.tvlauncher` after Home moved. `com.gathin.tvhome/.MainActivity`
was confirmed as both `ResumedActivity` and `mFocusedApp`. Plain `tv.py --push` timed out on
the APK and on a 4.4 MB test APK, so `tools/b64push.py` (base64 chunks + md5) was used.

The look is the `apple-tv-br` theme (`themes/apple-tv-br/config.json`, applied on the TV via
`TV_HOST=<tv ip> tools/theme.sh themes/apple-tv-br`): frosted-glass dock band with the focused
app's name above it, ambient and app-colour glow, calm reduced motion and no wallpaper of its own.
The theme also hides the TCL tiles and the "Works with Alexa" (`com.tcl.smartalexa`) tile from the
grid without disabling them, so HDMI/Cast stay untouched; which apps fill the dock is the owner's
choice and is not part of the theme. `reduced_motion: true` keeps it smooth on 1 GB.

## Verification

Screenshots after every wave; Chromecast checked after each wave and after reboot (port
8009 LISTEN, `mediashell` and `gms` running). Sync + 60 s wait before the reboot — this
firmware loses the disabled state if rebooted immediately after a batch. AdGuard Private
DNS (`dns.adguard-dns.com`, `hostname` mode) resolves normally. One session of a few hours,
not a several-day test; reboot-checked once.

Neither `power_on_home` nor `power_on_home_always` could stop the firmware opening
`com.tcl.tv` a couple of seconds after boot. The owner chose to accept that and press Home.
Do not claim those helpers work on this firmware.

## Tests

- `python3 -m pytest -q tests/test_examples.py tests/test_list_gives_commands.py tests/test_example_readme_package_fates.py` → **56 passed, 987 subtests passed**.
- `python3 -m unittest tests.test_examples tests.test_example_readme_package_fates tests.test_example_list_count tests.test_list_gives_commands` → **88 tests, OK**.
- Full `python3 -m unittest discover -s tests` run during this PR (before the fold): **3818 tests, 14 failures, 114 skipped**. The failures were all in the freshly added `themes/apple-tv-br` (missing `screenshot.jpg` and missing `themes/README.md` row) and are being fixed with this report; no failure traced to the debloat list or the launcher report itself.

## Re-enable one app / undo

```
TV_HOST=192.168.0.6 tools/venv/bin/python tools/tv.py pm enable <package>
TV_HOST=192.168.0.6 tools/restore.sh examples/tcl-40s5400a/debloat.txt
```

The list lives in [`debloat.txt`](debloat.txt) as the Brazil unit, 2026-11-19 (V034) of this
folder, and the launcher-side switches live in
[`themes/apple-tv-br/config.json`](themes/apple-tv-br/config.json). The convenience scripts that
were used on this unit (`install-launcher.sh`, `restore-all.sh`, `batch1-3.txt`) were local to the
session and are not part of this report.
