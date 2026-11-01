# Síragon 58DA7000 (KTC `zhongshan`, MediaTek MT7632)

Hardware and debloat notes for the 58" Síragon 4K sold in Venezuela, manufactured by KTC.

| Field | Observed value |
|---|---|
| Retail Brand / ODM Maker | `Síragon` (Venezuela) / `KTC` |
| Model String | `SMART TV` (`ro.product.model`); product `KTC_SIRAGON_4K_SA_21V2` |
| Board / Codename | MediaTek MT7632 (`ro.board.platform=m7632`), device `zhongshan` |
| Operating System | Android TV 11 (API 30) |
| Firmware Build | `RTM3.211215.203` (2022-05-10), security patch 2022-04-01 |
| Architecture / Memory | `armeabi-v7a` (32-bit runtime), 1.75 GB visible RAM, 8 GB eMMC |
| Remote Control | MStar IR receiver, `/dev/input/event1` |

## Debloat

The [debloat list](debloat.txt) disables 16 packages: Google's sponsored rows, feedback and partner
preloads, the Play Movies/Music/Games entries, the Prime Video preload, the vendor demo, Freeview Play,
the setup wizard and the stock launcher. The KTC factory-menu and serial-console packages stay enabled,
because other Siragon boards route hardware hotkeys through them.

Apply it with `tools/disable.sh`, and undo it with the Undo line in the list header, which passes
`com.google.android.tvlauncher` (the Android TV home on this build, not `com.google.android.apps.tv.launcherx`).

## Notes on Debloat & Home Handover

- **Setup wizard:** `com.google.android.tungsten.setupwraith` can take Home after the stock launcher is
  disabled, and it owns the saved Wi-Fi on Google TV builds. On this Android TV 11 build Wi-Fi survived
  several reboots with it disabled; install the new launcher first, then disable it.
- **Live TV & HDMI:** Live TV is `com.mediatek.wwtv.tvcenter`; the tuner/HDMI layer is
  `com.mediatek.tvinput` plus `com.mediatek.tv.service`. HDMI 1-3, Composite and "TV en vivo" work in the
  launcher's Inputs menu with this list applied.
- **Remote buttons:** the YouTube and coloured buttons are raw IR scancodes on `/dev/input/event1`:
  YouTube `379`, red `398`, green `399`, yellow `400`, blue `401`. They keep working with the factory
  services enabled, and can be mapped with `app_keys` in the launcher (`scan:379:org.smarttube.beta`).
- **No OTA updater:** this build has no firmware-update package or service. Play Store and Play Services
  are left enabled, so apps still update.
- **Memory:** 2 GB nominal; about 890 MB `MemAvailable` at idle after the list, enough for a 4-column
  launcher and one streaming app.
- **Kept on purpose:** Play Store/Services, Google Assistant, Chromecast, Netflix, Bluetooth and remote
  pairing, the Latin IME, TalkBack and TTS. No package is uninstalled.
