# TiVo Stream 4K (SEI400TV, Android 12): Launcher Handover & Key Remapping

Tested on a retail TiVo Stream 4K (SEI Robotics `IPA1104HDW`, SKU `RA2400`, device `SEI400TV`), Amlogic S905Y2, 2 GB RAM, Android TV 12 (SDK 31), build `SC.1272 release-keys`, security patch 2025-07-05, NES middleware.

## Identify the device

| Field | Value |
|---|---|
| Retail model | TiVo Stream 4K |
| Manufacturer | SEI Robotics (`IPA1104HDW`) |
| Product / Device | `SEI400TV` |
| Android TV OS | 12 (API level 31) |
| Firmware Build | `SC.1272 release-keys` |
| Fingerprint | `eSTREAM4K/SEI400TV/SEI400TV:12/SC/1272:user/release-keys` |

---

## 1. Why `com.nes.coreservice` uninstalls alternative launchers

The firmware includes a persistent background daemon and service (`com.nes.daemon` and `com.nes.coreservice`). At boot and periodically, `com.nes.coreservice` (`EvoBlackListAppsFun`) checks installed non-system packages:

1. It automatically **uninstalls** any third-party application whose `MainActivity` declares `<category android:name="android.intent.category.HOME" />` in its intent filter.
2. It also uninstalls a fixed list of 53 package names defined in its resources.

### The Solution: Build with `-PomitHomeCategory=true`

To prevent `coreservice` from removing Clean Your TV:
* Build the launcher with `-PomitHomeCategory=true` (or configure manifest placeholders).
* In `AndroidManifest.xml`, `MainActivity` declares `CATEGORY_DEFAULT` and `CATEGORY_LEANBACK_LAUNCHER`, but omits `CATEGORY_HOME`.
* `coreservice` sees the app as a standard Leanback TV app and leaves it completely alone across reboots.

---

## 2. Launcher Handover via `HomeKeyService`

Because the launcher does not claim the system `HOME` role directly, handover is handled cleanly through `com.gathin.tvhome/.HomeKeyService` (an accessibility service).

### Enable Accessibility Service:
```bash
adb shell settings put secure enabled_accessibility_services com.gathin.tvhome/com.gathin.tvhome.HomeKeyService
adb shell settings put secure accessibility_enabled 1
```

When Home is pressed (or when the stock launcher attempts to take focus), `HomeKeyService` intercepts the window state change and launches `MainActivity` directly by explicit component (`Intent(this, MainActivity.class)`).

---

## 3. Remote Shortcut Key Remapping

The TiVo Stream 4K remote communicates via Bluetooth (`0471:1207`) and emits distinct hardware keycodes:

| Remote Button | Android Keycode | Scancode | Default Behavior | CleanYourTV Remapping |
|---|---|---|---|---|
| **TiVo Button** (Yellow circle) | `173` (`KEYCODE_DVR`) | `520` | Launches `com.tivo.atom` | Map to Clean Home via `app_keys: ["173:com.gathin.tvhome"]` |
| **Guide Button** | `172` (`KEYCODE_GUIDE`) | `365` | Opens TiVo Guide | Map to Home or custom module (e.g. `"172:com.poppy.scoreboard"`) |
| **Live TV** | `5124` | `5124` | Opens live channels | Map to Live TV or custom app |
| **Skip** | `272` (`MEDIA_SKIP_FORWARD`) | `272` | Skip forward | Media control |

### Theme Configuration (`examples/tivo-stream-4k/theme/config.json`)
```json
{
  "dock_apps": [
    "com.plexapp.android",
    "com.netflix.ninja",
    "com.disney.disneyplus",
    "com.amazon.amazonvideo.livingroom",
    "com.wbd.stream",
    "com.peacocktv.peacockandroid",
    "com.cbs.ott"
  ],
  "sky_intensity": 70,
  "dock_glass": true,
  "advanced_effects": true,
  "cinematic_home": true,
  "app_glow": true,
  "glow_style": "horizon",
  "app_keys": [
    "173:com.gathin.tvhome",
    "hold:173:com.poppy.scoreboard",
    "172:com.poppy.scoreboard"
  ],
  "module_cards": true,
  "tile_menu": true
}
```

---

## 4. Debloating & Background Management

Because the firmware returns `"This command is illegal!"` on `pm disable-user`, packages are suspended rather than disabled:

```bash
L=examples/tivo-stream-4k/debloat.txt
grep -vE '^\s*(#|$)' "$L" | awk '{print $1}' | while read -r p; do
  adb shell pm suspend --user 0 "$p"
  adb shell cmd appops set "$p" RUN_ANY_IN_BACKGROUND ignore
  adb shell am force-stop "$p"
done
```

---

## 5. Verified Hardware Results

* **Reboot Persistence:** Confirmed across multiple cold boots—Clean Your TV remains installed, `HomeKeyService` rebinds, and the TiVo button immediately returns to Clean Home without error dialogs.
* **Tested Features:** Time-of-day dynamic sky gradient (`sky_intensity: 70`), frosted glass container (`dock_glass: true`), top-bar weather pill, and streaming app usage scoreboard.
