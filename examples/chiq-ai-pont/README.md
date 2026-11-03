# Also tested: OK. ODL 43951UC-TAB / DVB93KO

<!-- Made by PapaGordon -->

A second unit on the same `samseong` platform and `RTO2.250508.001` firmware as the
CHiQ DVB93CQ list: an **OK. ODL 43951UC-TAB**, Android model `AI PONT`, product
`DVB93KO`, manufactured by Changhong. These are same-day observations from
2026-11-02, not several-day testing of the full list beside this note.

| Field | Observed value |
|---|---|
| Android / API | 11 / 30 |
| Device / board | `samseong` / `m7322` |
| Firmware | `RTO2.250508.001` |
| RAM | `MemTotal: 1381996 kB`, about 1.3 GB |
| Android display mode | 1920 x 1080, 320 dpi; this is the Android UI mode, not a panel-resolution measurement |
| Network | Ethernet |
| Stock Home | `com.google.android.tvlauncher/.MainActivity` |
| Live TV / HDMI player | `com.mediatek.wwtv.tvcenter` |

## Cleanup actually applied on this unit

Only this subset of the existing list was applied, in small batches with a
package/settings backup and recovery record saved first:

```text
tv.anoki.acr.controller
com.homwee.aipont
com.google.android.tvrecommendations
android.autoinstalls.config.homwee
com.homwee.store
com.homwee.help
com.google.android.feedback
```

Those packages stayed disabled after reboot. `com.homwee.store` declares the
Changhong retail-demo receiver and service; it is not the Play Store.

All installed streaming apps, Chromecast, Assistant, Miracast/AirPlay, the ambient
screensaver, USB media apps, firmware/app updaters and Google services were kept.
The same important keep rules from the list apply, including
`com.google.android.leanbacklauncher.partnercustomizer`, `com.homwee.synckey`,
`com.homwee.dmt.upgrade` and the MediaTek TV/input services.

`com.cltv.fast` was enabled before cleanup and was not sent to `pm disable-user`.
After reboot it was disabled, with `lastDisabledCaller: com.mediatek.tv.service`
in `dumpsys package com.cltv.fast`. This was the firmware's own action, separate
from the cleanup batches. TalkBack was already disabled before cleanup.

## Launcher and power-on behavior

The unchanged reference launcher 3.3 built and installed successfully. The stock
launcher was disabled only through `launcher/build-and-install.ps1` after the new
Home started. The resolved Home and focused activity were checked, including
after reboot. Aurora was applied through `tools/theme.ps1`, with a local copy of
its configuration; the personal dock and wallpaper copies are not shared here.

Without a helper, the firmware opens `com.mediatek.wwtv.tvcenter` at boot and
restores the last source even while the reference launcher owns Home.
`HomeKeyService` with these existing options opens Home once after power-on:

```json
{
  "live_tv_package": "com.mediatek.wwtv.tvcenter",
  "power_on_home": false,
  "power_on_home_always": true,
  "key_filter": false,
  "consume_volume_keys": false
}
```

Enable the helper with `-HomeKeyService` on `launcher/build-and-install.ps1`
(`HOME_KEY_SERVICE=1` with the shell twin), and merge the options into the local
theme before applying it. No launcher source change is needed.

Do not combine `power_on_home: true` with `key_filter: false` here. The former
keeps redirecting Live TV during its boot/screen-on window; the key event that
normally disarms it does not reach the helper when filtering is off. On this
unit a source selected shortly after boot opened the TV player and was sent back
to Home. It started working after the window expired. The one-shot option above
avoids keeping that Live TV redirect armed.

After the change, Home came forward after reboot without an injected Home key.
An injected sleep/wake check also opened Home, then selected HDMI 3 from the top
Sources menu about eight seconds after wake. The TV player and its HW6 input
session stayed active after another six seconds, inside the old screen-on window.

## HDMI and the source picker

The input IDs on this unit map as follows:

| Port | TV input ID |
|---|---|
| HDMI 1 | `com.mediatek.tvinput/.hdmi.HDMIInputService/HW4` |
| HDMI 2 | `com.mediatek.tvinput/.hdmi.HDMIInputService/HW5` |
| HDMI 3 | `com.mediatek.tvinput/.hdmi.HDMIInputService/HW6` |

A dock tile using `launch_actions` with
`com.mediatek.wwtv.tvcenter=input:com.mediatek.tvinput/.hdmi.HDMIInputService/HW6`
selected HDMI 3, confirmed by the active input session. HDMI screenshots are
empty on this firmware, so an empty capture is not evidence that the input failed.

## Test limits

Dock/grid/top-bar navigation, the Home handover and injected sleep/wake behavior
were checked on the TV.
The owner also confirmed the console picture, streaming video playback, phone
casting, opening Steam Link, the physical Home/Settings/Source controls and held
volume. This does not establish compatibility with every streaming app or PC streaming.
Debugging authorizations were revoked and debugging was turned off at the end.
After the owner restarted the TV, port 5555 explicitly refused connections over
both Ethernet IPv4 and the link-local IPv6 address used during setup.
No antenna/cable scan, full restore or several-day soak was performed. This note
does not validate every package in `debloat.txt` for the OK. unit.
