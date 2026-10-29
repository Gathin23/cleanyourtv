# TCL 55C745 (2023, Google TV, Android 14)

TCL 55C745, device `G08`, model "Smart TV Pro", product `G08_4K_GB`, firmware `UTT2.250416.001 AU02`,
MediaTek `tcl9618`, about 2.4 GB RAM. The same firmware build as examples/tcl-q7c-google-tv and
examples/tcl-85rm7l-google-tv, and the same G08 board as the C755, QM6K and 75T7 (US) lists. Set up on
Ethernet; a France/UK household (French and UK catch-up apps alongside Stremio and a third-party YouTube
client). Both halves work on this set: the debloat list and the Clean Your TV launcher as Home.

Not every 65C745 is a G08: a 65C745 from France reports `G05_4K_GB` (Google TV 11, build `AR2101`). Check
`ro.product.name` first; a G05 set belongs with examples/tcl-65c735.

| File | What it is |
|---|---|
| `debloat.txt` | The 48 app packages disabled, grouped and commented (plus the stock launcher and setup wizard) |
| `config.json` | Dock (Stremio + a third-party YouTube client only), hidden tiles and the Live TV app; push it with `tools/theme.sh` |

**How much testing is behind it:** one set, 21 October 2026. Disabled by hand with `pm disable-user --user 0`,
one call per package, in five batches plus two small follow-ups, with a screenshot after each batch. All 50
packages (48 app packages, the stock launcher and the setup wizard) survived two reboots. YouTube (via the
third-party client), Live TV, the HDMI/AV input list, the Source key QuickPanel, D-pad navigation and the
physical remote's Home and Input buttons were all checked on the real set. This is about one evening of use,
not weeks.

## Install

The order matters: setupwraith goes while Google TV's own home still owns Home, then `build-and-install.sh`
starts the new launcher and only then disables launcherx. `disable.sh` skips launcherx on the first run (its
Home guard), which is what you want here.

```bash
FORCE_SETUPWRAITH=1 TV_HOST=<tv-ip> tools/disable.sh examples/tcl-55c745/debloat.txt
INPUT_KEY_SERVICE=1 TV_HOST=<tv-ip> launcher/build-and-install.sh
TV_HOST=<tv-ip> tools/theme.sh examples/tcl-55c745
```

`FORCE_SETUPWRAITH=1` is only for a set on Ethernet with no saved Wi-Fi: there `dumpsys wifi` shows no network
owner, and `disable.sh` then plays safe and skips setupwraith. On Wi-Fi, run
`tools/tv.py "dumpsys wifi | grep -o 'cname=[^ ]*'"` first. If the network belongs to setupwraith, forget it
and join it again in Settings before you disable the wizard.

`build-and-install.sh` needs JDK 17 for Gradle 8.9, and an Android SDK with platform 34 and build-tools 34.0.0
(`sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"` into `~/Android/Sdk` if you don't
already have one). On a Mac where `java_home -v 17` answers with a newer JDK, pass `JAVA_HOME` explicitly.

## Undo everything

```bash
TV_HOST=<tv-ip> tools/restore.sh examples/tcl-55c745/debloat.txt com.google.android.apps.tv.launcherx
TV_HOST=<tv-ip> tools/theme.sh --reset
```

## Notes for this set

- **com.tcl.eva was disabled here**, unlike examples/tcl-q7c-google-tv which left it untouched over its
  camera and TCL BI permissions. It survived two reboots with no visible issue, matching
  examples/tcl-g08-eu-android12, which also disables it - but no camera-dependent feature was exercised on
  either set, so treat that specific risk as still unverified rather than cleared.
- **com.tcl.guard, com.tcl.usercenter and com.tcl.ui_mediaCenter** were disabled after cross-checking
  examples/tcl-q7c-google-tv, which tested them safe on the identical firmware build; they were not part of
  the first debloat pass here and were added in a follow-up batch.
- The Inputs ("Sources") menu lists HDMI 1-4, AV and "TV en direct" (Live TV, `com.tcl.tv`) correctly with
  the stock launcher gone.
- The Source key opens TCL's own QuickPanel (`com.tcl.suspension`) correctly; do not disable
  `com.tcl.suspension`, `com.tcl.interactive` or `com.tcl.ttvs` - see debloat.txt and
  examples/tcl-g08-eu-android12 for what breaks.
- `com.tpa.kb` carries `RECOVERY`, `MASTER_CLEAR` and `CHANGE_COMPONENT_ENABLED_STATE` permissions despite
  its generic name; left untouched, not evaluated further.
- Kept by the owner's explicit choice, differing from examples/tcl-q7c-google-tv: Google Assistant voice
  search (`com.google.android.katniss`), Miracast (`com.tcl.miracast`) and TCL's MagicConnect mirroring
  (`com.tcl.magiconnectfree`).
- French catch-up apps (France.tv, 6play, TF1+, Arte) were kept by choice and not exercised beyond appearing
  on the home screen; UK catch-up apps (ITVX, My5) and Rakuten TV/Wuaki were disabled by choice, also
  untested beyond being hidden.

## Second unit: 65Q6C, Ethernet

TCL 65Q6C, model "Smart TV Pro", device `G08`, product `G08_4K_GB`, the same `UTT2.250416.001 AU02`, on
Ethernet. Owner report from 28 October 2026: the debloat and the Clean Your TV launcher worked, Home stayed on
`com.gathin.tvhome` after a reboot, and the Home and Settings keys worked.
