# Saba SA40S67A9 (ikebukuro): debloat and launcher handover

A Saba SA40S67A9 was worked on over ADB on 2026-11-03 and 2026-11-04. The owner confirmed
that everything works after the cleanup. This report records the applied packages and Home handover;
the owner subsequently confirmed several days of use and tested all physical remote buttons.
A separate post-cleanup reboot test has not been recorded.

| Identifier | Observed value |
|---|---|
| Retail model | Saba SA40S67A9 |
| Manufacturer | Changhong |
| ADB product model / product / device | `AI PONT` / `DVB91` / `ikebukuro` |
| Android | Android TV 11, build `RTO2.250804.001`, incremental `13882904` |
| Memory and storage | 929000 kB RAM, 4.2 GB data partition |
| Stock Home | `com.google.android.tvlauncher/.MainActivity` |
| Live TV | `com.mediatek.wwtv.tvcenter/.nav.TurnkeyUiMainActivity` |

The [OK. AI PONT profile](../ok-ai-pont-ikebukuro/README.md) reports the same device
and firmware. Only the subset documented below was applied on the Saba.

## Home handover

`launcher/build-and-install.sh` installed the launcher built from source and started
`com.gathin.tvhome/.MainActivity`. After the launch succeeded, the script disabled
`com.google.android.tvlauncher` and set the new Home. Home then resolved to
`com.gathin.tvhome/.MainActivity`. `com.google.android.tungsten.setupwraith` stayed enabled.

`tools/theme.sh` applied a four-column layout with Netflix, Prime Video, YouTube and Disney+
in the dock, with Multi Screen Share and Play Store below. Screenshots confirmed the layout and
the Inputs, Wi-Fi and Settings buttons. Advanced effects were disabled and reduced motion enabled.

## Packages disabled during the cleanup

The package state was saved before cleanup. `tools/disable.sh` applied the changes in small batches
and wrote recovery records. Nothing was uninstalled.

- Content recognition and recommendations: `tv.anoki.acr.controller` (Anoki ACR),
  `com.homwee.aipont` (AI PONT portal and channels), `com.google.android.tvrecommendations`,
  `android.autoinstalls.config.homwee` (partner auto-installs).
- Vendor apps and feedback: `com.homwee.store`, `com.homwee.help`, `com.google.android.feedback`.
- Preloads unused by the owner: `com.google.android.play.games`,
  `com.google.android.youtube.tvmusic`, `tv.wuaki.apptv` (Rakuten TV).
- Voice search and casting, unused by the owner: `com.google.android.katniss`
  (the Assistant and voice search stop working), `com.google.android.apps.mediashell`
  (Chromecast built-in stops working).
- Stock Home: `com.google.android.tvlauncher`, disabled by the install script after the new Home started.

Already disabled before cleanup: `com.cltv.fast` and `com.google.android.marvin.talkback`.

Kept: Netflix, Prime Video, YouTube, Disney+, Play Store/Services, Multi Screen Share
(`com.homwee.multiscreenshare`), Live TV (`com.mediatek.wwtv.tvcenter`), TV/input middleware,
remote and Bluetooth services, the keyboard, firmware updates and setupwraith.

## Remote

The owner tested all buttons on the physical remote after the cleanup and reported
that they work without problems.

## Responsiveness and streaming

The three global animation scales (`window_animation_scale`, `transition_animation_scale` and
`animator_duration_scale`) were set to `0.5` and read back successfully. No measured performance
comparison was made.

Intermittent streaming freezes were reported during the session. The owner later confirmed that
everything works, but the affected app, reproduction steps and cause were not established.
The cleanup has not been shown to resolve the freezes.

## Apply and restore

Apply the shared list from the repository root:

```sh
TV_HOST='<tv ip>' tools/disable.sh examples/saba-sa40s67a9/debloat.txt
```

Restore it with:

```sh
TV_HOST='<tv ip>' tools/restore.sh examples/saba-sa40s67a9/debloat.txt
```

Replace `<tv ip>` with the TV's current local address. The shared list applies the
content-recognition, recommendations, auto-install, vendor-app and feedback changes.
Personal choices tested by this owner are commented out in the shared list.
The stock launcher handover belongs to `launcher/build-and-install.sh`.

## Validation limits

The owner confirmed several days of use and working physical remote buttons.
A separate post-cleanup reboot check, HDMI playback, CEC and a dedicated Multi Screen Share
functional test were not recorded. No package was recorded as re-enabled during this cleanup.
