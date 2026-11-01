# Xiaomi MiTV-AXFR1 (rango, dune): Android TV 11 Setup & HDMI ARC Audio

Tested on **Xiaomi MiTV-AXFR1** (codename `rango`, product `dune`, Amlogic/droidlogic, 1 GB RAM, India), running **Android TV 11** (SDK 30, build `RTT0.210630.002.485`).

---

## 1. Hardware & System Details

| Property | Value |
|---|---|
| Model | `MiTV-AXFR1` |
| Codename / Product | `rango` / `dune` |
| Chipset | Amlogic / droidlogic |
| Android / API | 11 / 30 |
| Firmware Build | `RTT0.210630.002.485` |
| External Audio | JBL Cinema SB510 over HDMI ARC (Port 1) |
| Live TV App | `com.android.tv` |
| Stock Launcher | `com.google.android.tvlauncher` |

---

## 2. Tested Debloat List

The debloat list in [`examples/xiaomi-mitv-axfr1/debloat.txt`](../../examples/xiaomi-mitv-axfr1/debloat.txt) safely disables 16 packages across 4 categories:

### Critical Safety Notes:
- Keep `com.droidlogic.tvinput`, `com.android.tv`, and Bluetooth/remote services enabled.
- Settings and Inputs menu (`com.android.tv.selectinput.SelectInputActivity`) remain fully functional.
- Changes survive reboots and are applied via `pm disable-user --user 0`, never uninstalled.

---

## 3. Launcher Configuration (`com.gathin.tvhome`)

- **Sideloading & Custom Home**: Android TV 11 on this set accepts third-party `CATEGORY_HOME` packages without requiring a manufacturer signature.
- **Icon-Only Top Bar**:
  Setting `icon_only_buttons` and `icon_only_inputs` to `true` renders Inputs, Wi-Fi, Reorder, and Settings as clean 42dp circular buttons with text labels omitted.
- **Interactive App Reordering**:
  Enabled with `<bool name="reorder_button">true</bool>` and `<bool name="tile_menu">true</bool>`. Users can move app tiles using the top-bar reorder icon or by holding the OK button on any tile.

---

## 4. HDMI ARC Audio & Floating Volume Overlay

### The HDMI ARC Volume Problem
When an external soundbar (e.g. JBL Cinema SB510) is active on HDMI ARC (`mArcEstablished: true`, `mSystemAudioActivated: true`), Android's `AudioService` suppresses the native TV volume UI dialog (`flags: 0x0`) to prevent duplicate on-screen indicators.
