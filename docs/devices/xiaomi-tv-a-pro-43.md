# Xiaomi TV A Pro 43-inch (`MiTV-MZTU0` / `river`): one-session report

Owner-reported 43-inch Xiaomi TV A Pro. Android reports the same `MiTV-MZTU0` model and `river`
codename as the existing 75-inch 2026 profile ([examples/xiaomi-tv-a-pro-2026](../../examples/xiaomi-tv-a-pro-2026/debloat.txt)), but this unit has a different firmware build. This is
an initial compatibility report, not a multi-day debloat list.

| Field | Observed value |
|---|---|
| Retail model | Xiaomi TV A Pro 43-inch (owner-reported) |
| System model / codename | `MiTV-MZTU0` / `river` |
| Board platform | `merak` |
| System | Google TV on Android 14 (SDK 34) |
| Firmware | `UKN1.240806.017.V816.0.26.6.2.6.URTMIXM` |
| Stock launcher | `com.google.android.apps.tv.launcherx` |
| Live TV app | `com.mitv.livetv` |

## Setup wizard and Home

CleanYourTV Setup left the stock Home screen alone and reported that the setup wizard owned the saved
Wi-Fi network. The owner had already forgotten Wi-Fi and connected Ethernet, but the warning remained.
ADB inspection showed `com.google.android.tungsten.setupwraith` as the Wi-Fi config owner. After
Ethernet was confirmed as the active default network, the setup wizard was disabled; the launcher
build-and-install script installed and started CleanYourTV, made it Home, and only then disabled the
stock launcher. After one reboot, Home still resolved to `com.gathin.tvhome/.MainActivity`.

Do not disable the setup wizard while the TV is still relying on its saved Wi-Fi network. Confirm
that Ethernet (or another network not owned by the wizard) is active first. Disabling it can also stop phone casting after a reboot, since the Cast receiver reads its credentials from setupwraith.

## Package state observed

This subset was adapted from the 75-inch `river` profile. Ten entries were already disabled before
this session; `com.google.android.tungsten.setupwraith` was the only newly disabled package in this
list. The stock launcher was disabled separately by `build-and-install` after the replacement Home
started successfully.

```text
# Xiaomi and Google telemetry
com.miui.tv.analytics
com.xiaomi.statistic
com.xiaomi.tvqs.overseas.y24
com.google.android.feedback

# PatchWall and sponsored/content rows
com.mitv.tvhome.atv
com.mitv.tvhome.michannel
com.mitv.tvhome.oemtab
com.xm.webcontent
android.autoinstalls.config.google.gtvpai
com.xiaomi.mitv.tvmanager

# Newly disabled for Home handover
com.google.android.tungsten.setupwraith
```

Streaming apps, voice search, Chromecast/casting and firmware updates were kept enabled by owner
request. SmartTube and TV Bro were pinned in the launcher's dock. The package state and launcher were
checked in one session and after one reboot; use this report as provisional until the owner has lived
with the setup for several days.
