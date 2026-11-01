# Síragon 4K Smart TV (KTC `elliniko`, MediaTek MT9676)

Hardware and debloat notes for the Síragon 4K Smart TV sold in Venezuela, manufactured by KTC.

| Field | Observed value |
|---|---|
| Retail Brand / ODM Maker | `Síragon` (Venezuela) / `KTC` |
| Model String | `SIRAGON 4K SMART TV` |
| Product Identifier | `KTC_SIRAGON_4K_24GV1` |
| Board / Codename | MediaTek MT9676 (`ro.board.platform=merak`, device `elliniko`) |
| Operating System | Google TV on Android 14 (API 34) |
| Firmware Build | `UKN6.260116.117` |
| Architecture / Memory | `armeabi-v7a` (32-bit runtime), 1.8 GB usable RAM |
| Remote Control | Bluetooth / IR model `RC39 Consumer Control` |

## Notes on Debloat & Home Handover

- **Wi-Fi Network Ownership:** Wi-Fi credentials in `dumpsys wifi` belong to `cname=android.uid.system:1000`. Disabling `com.google.android.tungsten.setupwraith` does not sever active Wi-Fi connections on this build.
- **Physical Remote Settings & Input Keys:** `dumpsys window policy` routes `KEYCODE_SETTINGS` and `KEYCODE_TV_INPUT` through `com.mediatek.tv.agent` (`GlobalKeyReceiver`). Preserving `com.mediatek.tv.agent` keeps physical remote buttons functional.
- **KTC Hardware Hotkeys:** Packages `ktc.factorymenu.ui`, `com.ktc.tv.factory`, and `com.ktc.serialport` are kept enabled as they handle hardware hotkeys and input mapping.
- **Live TV & Broadcast Tuner:** Live TV is handled by `com.mediatek.tv.oneworld.tvcenter` and the ISDB-Tb broadcast tuner service `com.mediatek.dtv.tvinput.isdbtuner`. Both are retained.
- **Dynamic Accent Glow:** Works natively with CleanYourTV's `ambient_glow: true` and `app_glow: true`, dynamically illuminating the top shelf to match the active app. Long Spanish dates fit under the clock by configuring `"date_below_clock": true`.
