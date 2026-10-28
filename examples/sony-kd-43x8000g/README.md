# Sony BRAVIA KD-43X8000G

- **Model:** Sony BRAVIA KD-43X8000G (APAC region, `BRAVIA_ATV3_4K_PA`)
- **Board / SoC:** MediaTek quad-core (MT5891 / MT5893 family)
- **Firmware build:** `BRAVIA_ATV3_4K_PA-user 9 PTT1.190515.001.S54 672201 release-keys`
- **Android version:** Android TV 9 (API 28, Pie)

## Notes

- **Stock launcher:** `com.google.android.tvlauncher`.
- **Built-in Fallback:** `com.sony.dtv.settings.system.FallbackHome` in `SonySystemSettings.apk` claims `android.intent.category.HOME` with priority -1000. If any third-party launcher fails to start, Android falls back directly to system settings without bootlooping.
- **Wi-Fi ownership:** `dumpsys wifi` confirms `cname=android.uid.system:1000`. Wi-Fi is owned by Android system, so disabling the setup wizard or debloating does not drop network connectivity.
- **TV inputs and HDMI:** All HDMI 1-4 inputs, audio return channel (ARC), and tuner are handled by `com.sony.dtv.tvx` and `com.sony.dtv.tvinput.*`, which remain untouched. The reference launcher's Inputs menu reads the hardware input list and HDMI-CEC labels directly.
- **Action Menu remote key:** `com.sony.dtv.scrums.action` (`TvDotAction.apk`) is kept enabled. It registers `TvDotActionKeyReceiver` to handle the physical ACTION MENU remote button and render the Picture and Sound settings overlay.
- **Keyboard:** The stock Gboard (`com.google.android.inputmethod.latin`) can be disabled if an alternative Leanback IME (such as LeanKey Keyboard, `org.liskovsoft.androidtv.rukeyboard`) is installed.
- **Memory impact:** Reclaimed ~100 MB of available RAM after disabling the stock launcher and background commercial B2B daemons.
