# PRISM+ Q55 Ultra 2026 (R10G): stock Home redirect and reboot checks

One owner's setup, checked on 2026-10-31 and after a normal restart on
2026-11-01. This is a device note, not a several-day tested debloat list.

| Item | Observed on this set |
|---|---|
| Retail model | PRISM+ Q55 Ultra 2026, Q-Ultra series |
| Reported model | `R10G` |
| Manufacturer | MOKA |
| OS | Google TV / Android 14, API 34 |
| Firmware build | `UKR1.260612.001` |
| Connection | Wi-Fi |
| Stock Home | `com.google.android.apps.tv.launcherx/.home.HomeActivity` |

The [Q65 Ultra R10G list](../../examples/prismplus-q65-ultra-r10g/debloat.txt)
describes a sibling model on `UKR1.251010.001`. It was not applied wholesale to
this Q55. In particular, both `com.google.android.apps.tv.launcherx` and
`com.google.android.tungsten.setupwraith` remained enabled throughout this setup.
Neither was disabled to test whether this firmware would boot without it.

## Home and the physical remote

The stock-home query continued to resolve to Google TV Home. CleanYourTV worked
through its existing `HomeKeyService` redirect, rather than becoming the resolved
stock HOME component. These existing settings were used together:

```json
{
  "stock_home_redirect": "com.google.android.apps.tv.launcherx",
  "key_filter": true,
  "power_on_home_always": true
}
```

See [launcher and remote-key guidance](../launcher.md#remote-keys) for enabling
the helper; configuration alone does not enable the accessibility service.
`InputKeyService` was not enabled. The TV's original input handling was retained.

With key filtering initially off, Home appeared to work before reboot, but the
owner's physical Home presses returned to the original Google home after reboot.
After enabling key filtering and `power_on_home_always`, the owner opened YouTube
and confirmed that one Home press, then two more, stayed on CleanYourTV. Holding
Volume Up still raised the volume continuously. These settings were changed
together, so the test does not isolate which setting fixed the return to stock.

The initial configuration used the reference launcher at `57c00a4`, built from
source. Later weather and air-quality changes were made in a separate personal
launcher. The final restart check used that personal build with the same Home
settings, not an unmodified upstream APK. No personal launcher code or binaries
are part of this note.

## Cleanup observations

The following selected packages were disabled through the repository's guarded
tools and remained disabled after the initial reboot:

```text
com.android.adservices.api
com.android.federatedcompute.services
com.android.ondevicepersonalization.services
com.google.android.feedback
com.android.tv.feedbackconsent
com.realtek.leanbacklauncher.partnercustomizer
android.autoinstalls.config.google.gtvpai
```

These are session observations, not a recommended several-day list. A later
owner-selected cleanup also disabled unused apps. After reboot, the firmware
re-enabled AI Caption (`com.mk.tv.caption`), Meeting Mode (`com.apps.biz.mt`),
Gallery (`com.mk.tv.gallery`), YouTube Music (`com.google.android.youtube.tvmusic`)
and FastCast (`com.creative.fastscreen.tv`). Hiding their launcher tiles did not
establish that those packages stayed disabled.

TV/input/tuner services, Play Store and Play services, Bluetooth/remote support,
voice, casting/AirPlay and firmware updates were retained. Retaining a package
does not establish that its feature was tested.

## Verified results and limits

- The owner confirmed Home, the physical Input/source button and antenna live-TV
  picture and sound after the final normal restart. The antenna used a USB port.
- Repeated physical Home presses and held Volume Up were checked as described above.
- No launcher crash was present in the crash buffer at the final installation check.
- Debugging was closed before the restart and TCP port 5555 refused connections
  again afterwards. The owner reported completing the requested authorization
  revocation and restart steps.
- HDMI playback, HDMI-CEC, ARC, end-to-end casting, voice operation and several
  days of normal use were not verified. No claim is made for another firmware.

Original package/settings backups and the tool-generated recovery record were
kept privately. To undo the Home redirect, turn off CleanYourTV's Home helper in
Android's accessibility settings, leaving any other accessibility services alone;
the stock Home and setup packages are already enabled. Use the recorded list with
the repository's [restore procedure](../restore.md) to undo package changes.
