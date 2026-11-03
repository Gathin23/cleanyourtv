# Skyworth / Coocaa K3 (创维/酷开 K3, 7T634_P3H, Android 13)

Single-unit launcher notes for the China-market Skyworth / Coocaa K3, limited to the system model
and firmware below. K3 Pro and other K3 boards or firmware are not covered.

## Device and tested build

| Field | Value |
| :--- | :--- |
| Retail model | Skyworth / Coocaa K3 (创维/酷开 K3) |
| System model | `7T634_P3H` |
| Product / device | `sky_mt9633_7T631` / `mt9633` |
| Platform strings | `mt9633` / `mt9952` |
| Android / SDK | 13 / 33, Coocaa firmware |
| Firmware | `TP1A.220624.021.A1 / eng.www-da.20260902.162554 / user / release-keys` |
| Physical display / Android UI | 3840 x 2160 / 1920 x 1080, 240 dpi |
| Launcher source tested | `v3.1.0`; Android version name `3.1`, version code `10` |
| Owner's tested application ID | `com.coocaa.cleanhome` (historical local build, not a recommended ID) |
| Owner's tested main activity | `com.coocaa.cleanhome/com.gathin.tvhome.MainActivity` |
| Main test date | 2026-10-29 |

No root, flashing or changes to `/system`. The stock Coocaa launcher remained enabled and the
vendor Home properties were left unchanged. This launcher setup did not add any package disables;
`com.tianci.ad` was already disabled before testing. There is no tested debloat list for this model.

## Result

| Check | Observed result |
| :--- | :--- |
| Launcher display and input | Visible desktop, a visible SurfaceFlinger output layer and current InputDispatcher focus `OK` |
| Physical remote Home | Returned to CleanYourTV |
| Return from the weather app | Back returned to the desktop |
| Opening stock Coocaa Home | Home on Fire redirected it to CleanYourTV |
| One TV reboot | CleanYourTV appeared without an injected Home press or a manual launcher start; the physical TV picture was also checked |
| Chinese and lunar date | Displayed with the local Chinese text adjustments |
| Inputs entry | HDMI1, HDMI2, AV and live TV listed with `coocaa_sources=true` |
| Optional weather add-on | Local Chinese build: city search, refresh and Celsius display worked; its air-quality scale is US AQI |
| `nature-calm` photo theme | Photos and captions displayed, then the default background was restored |

The Home and reboot results apply to the launcher **plus the two external helpers below**, not to
setting the Android Home preference alone. Standby/wake, mains-off cold boot and long-term use were
not tested. The Inputs check covered the entry and list, not actual HDMI switching, port mapping,
CEC or ARC. The theme check did not establish long-running photo rotation.

## Owner's local build observations

The tested launcher was built from this repository's `v3.1.0` source with the existing
`launcherApplicationId` Gradle property set to `com.coocaa.cleanhome`. Its Java activity name stayed
`com.gathin.tvhome.MainActivity`. These identify the owner's tested build. For a new custom build,
keep the upstream application ID or choose your own neutral application ID rather than a vendor
prefix such as `com.coocaa.*`.

The owner also added `HOME` to the existing `MAIN + LEANBACK_LAUNCHER` intent filter in the local
manifest, retaining the separate `HOME + DEFAULT` filter. Home on Fire sent the target an intent
containing both categories, and this combined filter matched that launch path on Android 13.
The same arrangement had previously allowed the helper to open the owner's FreeTV build. This is
an observation about those local builds, not a manifest-editing recipe for other installations.

A local Simplified Chinese morning-greeting string and Chinese weather-add-on resources were also
used. These were text changes, not a demonstrated fix for display or Home takeover.

Package renaming and the combined filter were not independently removed and retested with this
CleanYourTV build, so neither is established as a requirement for every K3. No benefit from using
a vendor prefix was established. The tested APK was still a locally signed user app, without a
vendor signature or system-app privileges.

## Installation confirmation

An ADB installation opens Coocaa's on-TV confirmation dialog. Select **继续安装** (Continue installing)
before its countdown expires. Letting the dialog time out or leaving its default refusal selected
can produce `INSTALL_FAILED_USER_RESTRICTED`; that result is not a successful installation.

After installation, the owner explicitly opened the local launcher and confirmed an operable
desktop on the physical TV, then selected that installed build as Android's default Home over ADB.

This firmware's physical Home key and boot path also use vendor logic. The successful setup kept
the stock launcher enabled and used the external helpers below; a successful command or a focused
activity name alone does not verify those paths.

## Home and boot helpers used on this unit

| Helper | Tested configuration |
| :--- | :--- |
| [Home on Fire](https://github.com/toolicious/home-on-fire), 1.5.2 | Accessibility service enabled; target was the owner's local launcher listed above; **Map custom buttons** on; Launcher mapping matches stock `com.tianci.movieplatform` |
| Home on Fire other switches | **Replace Amazon Home** off; its own **Launch on boot** off |
| [Launch on Boot](https://github.com/ITVlab/Launch-On-Boot), 1.4.0 | **Enabled** on; target was the same local launcher; **Live channels** and **Wakeup** off |
| Coocaa process policy | The two helpers retained the per-app autostart permission and background-retention settings already used with FreeTV |

Home on Fire watches for stock Home and opens the replacement launcher. Launch on Boot starts the
target after boot. Its **TEST** button was not the reboot check: one actual TV reboot was separately
verified without manually opening Home afterwards.

The Home on Fire mapping matches the whole `com.tianci.movieplatform` package. Other vendor video
pages in that package can therefore be redirected too. Ordinary Back navigation inside an app
retains that app's behavior; the helper acts when the matched stock page appears.

### Volume continues changing after a single press

In a later follow-up, one press of Volume Up or Volume Down could leave the volume increasing or
decreasing continuously. The owner traced this to the auxiliary Home/accessibility app watching
and redirecting the launcher.

## Chinese date and Coocaa Inputs

The local config is `/sdcard/Android/data/<application-id>/files/config.json`, using the ID of the
installed launcher. The relevant settings on this unit were:

```json
{
  "locale": "zh-CN",
  "lunar_date": true,
  "lunar_utc_offset": 8,
  "coocaa_sources": true
}
```

Merge these keys into the existing config rather than replacing personal app/layout settings.
`hidden_apps` hides icons only; hiding helper icons must not disable the helpers.

On this firmware, the standard `TvInputManager` list was empty. `coocaa_sources=true` exposed the
existing vendor-source support: the Inputs button listed HDMI1, HDMI2, 视频 (AV) and 电视直播
(live TV). No separate input-source add-on was needed. Actual source switching remains unverified.

## Optional UI observations

- In the tested default layout, a short press on Settings opened TV settings; holding OK opened the
  launcher menu, including launcher settings, module cards, dock editing and app ordering.
- A weather module card was an entry into its add-on, not a live weather widget embedded in Home.
  Hiding the card did not remove the weather app's ordinary icon.
- In the ordinary dock style, the percentage labeled as dock transparency controlled the long dock
  background's opacity: 100% was opaque; 30% was about 30% opaque. It did not fade app icons or the
  entire desktop.
- With a custom application ID, theme pushes, the config directory and ThemeProvider must all use
  the installed ID (`content://<application-id>.theme/`). Preserve locale, lunar date and
  `coocaa_sources` when applying a theme's config. The tested photo theme was later removed from
  use; photo themes do not establish video-screensaver support.

These observations cover one firmware and a locally adapted `v3.1.0` launcher. Other K3/K3 Pro
units, newer launcher versions, standby/wake, Bluetooth add-ons and multi-day stability need their
own checks.
