# Dangbei DBOX02 (Mars Pro 2): initial Android TV 11 setup

This note records the owner's [single-session report and follow-up](https://github.com/Gathin23/cleanyourtv/issues/2162), not a several-day tested debloat list. The device runs Android TV 11 (SDK 30), firmware `RTM9.241015.060`. The reference launcher was built from source. The owner checked Home, the remote and package state after a reboot, then turned debugging off and confirmed the ADB port was closed after another reboot.

Do not apply another projector's list just because it also has MediaTek components. The ten packages below describe this owner's choices on this firmware; they are not a ready-to-apply list.

## Home handover and saved Wi-Fi

`cmd package set-home-activity` reported `Success`, but Google TV still resolved as Home. The replacement launcher was installed and visibly running before the stock Home packages were disabled with the repository's guarded PowerShell tool. Afterward, the HOME resolver and foreground activity pointed to `com.gathin.tvhome/.MainActivity`, including after reboot. No Home or Input accessibility helper was enabled.

Before disabling `com.google.android.tungsten.setupwraith`, the owner found that it owned the saved Wi-Fi network. They forgot and rejoined that network through TV Settings, then checked that `com.android.tv.settings` owned the new saved network. Skipping that check could leave another unit without its saved connection. Keep the original package states and recovery record so the guarded restore tool can reverse the session's changes.

## Packages changed in this session

The owner disabled these packages for user 0; all ten stayed disabled after the validation reboot. Nothing was uninstalled. The optional media and game apps were disabled by personal choice, not because this model requires their removal.

| Group | Packages |
|---|---|
| Feedback and stock Home configuration | `com.google.android.feedback`, `com.mediatek.android.leanbacklauncher.partnercustomizer` |
| Unused media and game apps | `com.netflix.ninja`, `com.amazon.amazonvideo.livingroom`, `com.iqiyi.i18n.tv`, `com.google.android.youtube.tvmusic`, `com.google.android.play.games`, `com.google.android.videos` |
| Stock Home claimants, only after the replacement was verified | `com.google.android.tungsten.setupwraith`, `com.google.android.apps.tv.launcherx` |

Freeview packages were already disabled before this session and are not part of these ten changes. No separate manufacturer telemetry package was established from the inspected packages; an SDMC prefix alone was not treated as evidence of tracking.

The owner kept the projector focus/keystone service, SDMC remote and Bluetooth services, MediaTek TV/input services, Google voice dictation, keyboard, Play Store and Play Services, Chromecast receiver and firmware updater enabled. Preserving these packages does not by itself prove that every feature was tested.

## What was checked and what remains open

The owner checked D-pad navigation at the launcher edges, Inputs and Settings, dock launches, the physical remote, video with sound and voice dictation. HDMI video worked, and Home returned from HDMI to the replacement launcher. The disabled-package set persisted through reboot. After debugging was turned off and authorizations revoked, a post-reboot ADB connection attempt was refused.

This was one setup session, not several days of use. Actual Chromecast playback, Bluetooth speaker pairing, prolonged standby and a full restore were not tested. The report does not establish that the same package choices are safe on other Dangbei firmware.
