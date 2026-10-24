# Philips 50PUH8215/96 on Android TV 11

This Taiwan-market TV reports model `TPM191E`. It runs Android TV 11 (SDK 30), build `RTT2.211108.001/3565`.

The same board appears in [`philips-tpm191e`](../../examples/philips-tpm191e/debloat.txt), but that list describes Android TV 12 and a different firmware build. Its Android 9 unit is also a different build. This note records one Android 11 unit tested on 2026-10-24. It is not a several-day debloat list.

## Packages changed on this unit

The baseline contained 195 installed packages and 16 Freeview packages already disabled. The following six packages were disabled with `pm disable-user --user 0`, one call per package, in three batches. A screenshot was checked after each batch.

| Group | Packages |
| --- | --- |
| Philips analytics | `org.droidtv.candeebug`, `org.droidtv.gel` |
| Philips content and promotions | `org.droidtv.nettvrecommender`, `org.droidtv.recommendation`, `org.droidtv.nettv.market`, `org.droidtv.sticker` |

The launcher install separately disabled `com.google.android.tvlauncher` after `com.gathin.tvhome/.MainActivity` opened. `cmd package set-home-activity` alone reported success but left the stock launcher on Home. After the stock package was disabled and Home was set again, `resolve-activity` and `mFocusedApp` named the new launcher. One reboot preserved Home and all seven disables.

`org.droidtv.usagelogger` stayed enabled because it requests `TV_INPUT_HARDWARE`. `com.google.android.tvrecommendations` stayed enabled because it owns the notification panel on related TPM191E firmware. Live TV and input services, the remote and Bluetooth services, Play Store, Play Services, voice, Chromecast, Miracast, firmware updates, and the owner's streaming apps stayed enabled. This session did not test playback in each streaming app or a firmware update.

## Launcher and inputs

The reference launcher built and installed on SDK 30. The unit uses `org.droidtv.playtv` for Live TV. Its four dock apps were Netflix, Disney+, 動畫瘋, and YouTube. The launcher showed the app grid, dock, input menu, and Ethernet status; `eth0` was the active built-in network interface.

The input menu listed HDMI 1 through 4, YPbPr, and Live TV. Selecting HDMI 1 changed the active TV input to its `HDMIInputService/HW5` entry with `hdmi_port=1`. HDMI capture returned zero bytes, so this check confirms input selection, not a visible picture. The owner reported that the real remote's Input, Settings, and dedicated app buttons worked after the Home change. No accessibility key helper was enabled.

The device had already disabled the 16 Freeview packages before this work. A restore must leave them disabled. The stock Home component is `com.google.android.tvlauncher/.MainActivity`; re-enable that package before selecting it as Home.

## Screensaver observation

The installed DreamServices were Android Colors, Google Backdrop, and the launcher's Clock. The owner selected `com.gathin.tvhome/.ClockDream` as the system screensaver. The TV's **Start now** preview displayed Clock, and `dumpsys dreams` named that service as the active dream. Automatic idle activation was not measured over ADB; the owner reported that the TV entered sleep normally.

On this firmware, the screensaver time menu offers 5, 15, and 30 minutes, 1 and 2 hours, or Never. It writes `settings system screen_off_timeout`. A requested 10-minute value of `600000` ms reads back correctly, but the menu leaves its time label blank because 10 minutes is not a listed choice. Choosing a listed time replaces that custom value.
