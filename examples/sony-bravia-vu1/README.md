# Sony BRAVIA VU1 (Android TV 11, Realtek)

Android product `BRAVIA_VU1_UC`, build `RTT7.210105.001` / `366.60436`. The list in `debloat.txt` came from a
KD-43X7500H. A second unit on the same product and build was set up on 26 October 2026 with the reference
launcher, and the notes below are about that one. A third unit, a KD-65X750H, is covered in
[Third unit](#third-unit-kd-65x750h) at the end.

| File | What it is |
|---|---|
| `debloat.txt` | The tested list; it disables the stock launcher last, after the new launcher owns Home |
| `config.json` | Live TV for the launcher's Inputs menu, with the tuner apps hidden from the grid |

**How much testing is behind it:** the second unit had one session with one reboot, then two days of normal
use. The owner checked Home, Input, the settings button and the volume ramp on the real remote, and casting
from a phone still worked with setupwraith kept.

## Launcher handover

`set-home-activity` alone does nothing here, because `com.google.android.tvlauncher` outranks any other HOME app.
`launcher/build-and-install.sh` handles that: it starts the new launcher, then disables the stock one with
`pm disable-user --user 0` and confirms Home. After a reboot the TV started straight into
`com.gathin.tvhome/.MainActivity` (`wm_set_resumed_activity` went from `com.realtek.tv.settings/.FallbackHome` to it).
It did not start Live TV, so neither `power_on_home` nor `boot_to_live_tv` is needed.

## Keeping casting: setupwraith can stay

On the first unit, setupwraith's `.MainActivity` answered HOME while it was enabled. On the second unit it was
left enabled, and once tvlauncher was disabled it did not claim HOME. `cmd package query-activities -a
android.intent.action.MAIN -c android.intent.category.HOME` listed only `com.gathin.tvhome/.MainActivity`,
`com.realtek.tv.settings/.FallbackHome` and `com.android.tv.settings/.system.FallbackHome`. The Cast receiver
reads its credentials from setupwraith, and on the second unit phone casting kept working two days later. So if
you cast from a phone:

1. Comment out the setupwraith line in `debloat.txt` before applying it.
2. After `launcher/build-and-install.sh`, run the same `query-activities` command. If setupwraith is listed,
   disable it as the list says (casting then stops after the next reboot).

The second unit's saved Wi-Fi belonged to `android.uid.system`, like the first.

## Live TV and inputs

`com.android.tv` (Live Channels) is the Live TV app, but it has no leanback launch entry on this build, so with
`live_tv_package` set to it the launcher leaves the Live TV row out of the Inputs menu. `com.realtek.tv.atv` is
the stock "TV" tile and opens the Realtek tuner (`com.realtek.dtv`), so `config.json` uses it. With no channels
scanned it opens channel setup. HDMI 1–3 and AV come from the TV input framework, and switching to HDMI ran
`com.android.tv/.MainActivity` as expected.

## Remote

The Input and settings buttons send vendor keys (`getevent` EV_KEY `0x225` and `0x254`) that the firmware
handles itself. Both kept working with the stock launcher disabled and no key helper enabled, so this set
needs neither `INPUT_KEY_SERVICE` nor `HOME_KEY_SERVICE`. Holding Volume still ramps.

## Third unit (KD-65X750H)

A KD-65X750H (US/Canada) on the same product `BRAVIA_VU1_UC` and build `366.60436`, on Ethernet, set up on
22 October 2026 and lived with for 8 days, with nothing re-enabled. It applied part of `debloat.txt`. It kept
Assistant, the Ambient screensaver, TTS, `tvrecommendations`, setupwraith, calendar storage, the gallery, the
HTML viewer and the live wallpaper picker, and added Play Games and Sony's Play auto-installs config (now in the
list) plus YouTube Music and Disney+ (commented out, the owner's choice). `launcher/build-and-install.sh`
disabled the stock launcher after the new one owned Home, and a reboot kept everything as it was.

- **setupwraith** again claimed no HOME with tvlauncher disabled. It owns one saved Wi-Fi network on this unit,
  so on a set that uses Wi-Fi, run the `cname` check in `debloat.txt` before disabling it.
- **Remote:** Netflix, YouTube, Prime Video, Input and the mic button were pressed on the real remote with the
  stock launcher disabled: all work, with no key helper. The Help key opens `com.sony.dtv.smarthelp`, so it does
  nothing once that line is applied.
- **HDMI chooser:** `com.android.tv` and `com.realtek.tv.atv` both answer an HDMI passthrough URI, so the
  launcher's HDMI rows open an "Open with: TV / TV" box, and "Always" is never applied. Either choice plays the
  input. The remote's Input key goes through `smartui` and has no box.
- **Live TV in the dock:** `hidden_apps` also keeps an app out of the dock, so to put Live TV in the dock, take
  `com.realtek.tv.atv` out of `hidden_apps` and add it to `dock_apps`, with `app_labels`
  `com.realtek.tv.atv=Live TV` and `app_banners` `com.realtek.tv.atv=banner_tv`.
