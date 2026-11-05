# ZTE zBox 1 (`B866V2K` / `YXE`), Android TV 14: same-day launcher and app cleanup report

This is an owner-reported, single-session note from 2026-11-04, not a reusable debloat list. It records one zBox 1 and does not establish that other ZTE boxes or firmware builds behave the same way.

## Identify the unit

| Field | Observed value |
|---|---|
| Retail model | ZTE zBox 1 |
| `ro.product.model` / `ro.product.device` | `B866V2K` / `YXE` |
| Android / SDK | 14 / 34 |
| Build ID | `UKG3.250115.001` |
| Build fingerprint | `ZTE/B866V2K/YXE:14/UKG3.250115.001/V86611396.0020:user/release-keys` |
| Security patch | 2025-12-01 |

## What was tried and observed

- The reference launcher was installed and configured. The user reported that it felt faster and confirmed that the physical Home key opened it after a reboot.
- The stock `com.google.android.tvlauncher` stayed enabled. The post-reboot Home resolver still listed Google TV and `FallbackHome`; the replacement relied on the launcher's accessibility `HomeKeyService` to redirect Home behavior. Keep the stock launcher enabled on this unit. The Menu key opened the old Google TV surface at one point, so Home and Menu should be checked separately on any similar setup.
- No packages were uninstalled. The final post-reboot disabled-package snapshot contained these seven packages changed during the session:

  ```text
  com.google.android.play.games
  com.google.android.youtube.tvmusic
  tunein.player
  com.netflix.ninja
  com.google.android.tvrecommendations
  com.android.printspooler
  com.amazon.amazonvideo.livingroom
  ```

- `com.android.ondevicepersonalization.services`, `com.android.adservices.api`, `com.android.federatedcompute.services`, `com.android.tv.feedbackconsent` and `com.google.android.feedback` were already disabled in the initial snapshot; they are not part of the changes above.
- Activity Launcher, Google Play Store, Google Play Services, Google Assistant, Chromecast components, firmware updater services, HDMI/tuner services and Bluetooth remote services were kept enabled. The owner's streaming and utility apps were retained; no packages were uninstalled.
- See also [ZTE ZXV10 B866F](zte-b866f.md): never disable both system launchers on these ZTE boxes.
- The built-in updater reported that the software was up to date; no firmware was installed during the session.

## Limits of the report

The owner noticed a subjective speed improvement, but the session did not save a before-and-after RAM or storage measurement. Later measurement attempts could not start the computer's local ADB server, so this report makes no claim about freed RAM or storage. Disabling packages with `pm disable-user` leaves their APKs and app data installed.

The local backup and screenshots include device and network details and are intentionally not linked here. Do not publish them with this note.
