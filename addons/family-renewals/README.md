# Family Renewals

A small, optional Android TV app for manually tracking household mobile recharges and recurring subscriptions. It is an independent app, not a launcher modification.

## Features

- Track multiple people and services: mobile recharge, OTT, broadband, DTH and other recurring payments.
- Record the member label, service/operator, optional phone or account reference, amount, last payment date, next due date and notes.
- Sort by due date and distinguish overdue, due today, due within seven days and later.
- Mask phone/account references by default. Reveal is temporary for the current app session.
- Add, edit and delete records with the TV remote.
- Optional generic CleanYourTV Home countdown. It is off by default and can be enabled from the app.

All entries are manually maintained. The app does not read SMS, SIM data, operator apps, recharge receipts, OTT accounts, bank messages or family members' phones. It is a reminder list, not an automatic recharge detector.

## CleanYourTV integration

The activity declares the module-card contract from `docs/specs/card.md`. After installation, the launcher discovers the card and asks the TV owner whether to show it. The answer is controlled by the launcher and can be changed in its Module cards list.

The app also implements the documented read-only `game_day` provider. When the owner enables the countdown in this app and configures the launcher, Home can show one generic line for the nearest renewal due within seven days. The provider exposes only a generic label, a date window, a colour and a rotation duration. It does not expose member names, service names, phone/account references, amounts or notes. Because the provider is exported without a permission so Home can read it, the generic date window is visible to other apps on the TV too. Leave the in-app option off when that is not acceptable.

To show the app icon beside Wi-Fi, add its package to `top_bar_apps`. To show the generic countdown under the clock, set `game_day` to the same package. Example theme overlay:

```json
{
  "top_bar_apps": ["io.github.deepakola.familyrenewals"],
  "game_day": "io.github.deepakola.familyrenewals"
}
```

These are existing launcher settings, not card metadata. The current phone-app UI may not expose both controls; this example needs to be applied as a theme/config overlay. A top-bar app is hidden from the ordinary app grid by the launcher, so use the top-bar icon as its primary shortcut when configured.

## Privacy and storage

- No Android permissions are requested.
- No network access, analytics, advertising, account login, background service or boot receiver.
- Records are stored in the app's private SharedPreferences on the TV.
- Android backup is disabled.
- The local preferences are not encrypted in this first version. Do not store passwords, OTPs, payment credentials or other secrets. Anyone with access to the unlocked TV and this app can view the records.

## Permissions

No `uses-permission` entries are present in the manifest. The exported provider is read-only and returns only the generic next-renewal date window when the owner opts in.

## Build

This folder is a standalone Gradle project under `addons/family-renewals/`. It reuses the pinned launcher Gradle wrapper:

```sh
cd addons/family-renewals
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

Requirements: JDK 17 and Android SDK platform 34 with Build Tools 34.0.0. Do not commit APKs or signing keys. A public release should use a stable signing key kept outside GitHub and publish both the APK SHA-256 and signer certificate SHA-256. Keep the same signing key for future updates.

## Compatibility and testing status

- Minimum SDK 26 (Android 8.0); the Xiaomi Mi Box S MIBOX4 on Android TV 9 is within the declared API range.
- The source has not yet been built, installed or tested on a physical TV.
- The required real-TV screenshot, D-pad/remote validation and reboot persistence test are still outstanding. No fabricated screenshot is included.
- The module should be considered a review candidate, not a released or verified add-on, until those checks are completed.
