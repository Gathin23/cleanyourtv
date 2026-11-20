# TV Tune visual design

The app is a quiet instrument for a living room: readable at a distance, explicit
about the next action and visually calm around test patterns.

## Palette and typography

| Role | Value |
|---|---|
| Main surface | `#0B1424` |
| Raised surface | `#111E34` |
| Main text | `#F1F5FB` |
| Secondary text | `#A4B4CC` |
| Focus / action | `#88CFFF` |
| Scope and observations | `#FFCA9E` |

The Android app uses system sans/sans-serif-medium. The web companion uses locally
available Avenir Next/Segoe UI/system fonts, plus monospace for patch IDs and data.
There are no remote fonts or tracking requests.

## Cover and interface

The signature illustration is an original television frame with a neutral square,
alignment corners and small spectrum swatches. It appears on the welcome screen,
in the Android icon/banner and in the web cover. It is artwork, not a before/after
photograph or device-test evidence. No Apple or third-party logo is used.

Welcome → Preparation → Five checks → Local checklist. Phone experiment fields are
a separate, clearly labelled entry. Controls use sentence case, a large focus area,
an explicit cyan focus state and deterministic normal Android D-pad traversal.

Pattern mode uses original flat fields on black, with optional labels outside the
field interiors. The cover palette, gradients, focus effects and decorative artwork
never appear inside measurement fields. Fields do not cycle automatically.

The web UI reflows to a single column on phones, supports keyboard focus and reduced
motion, and provides explicit Start / Stop / Review / Download actions. Numerical
results describe captures; there is no invented accuracy score or "calibrated" badge.

## Native TV-to-phone instructions

The native experiment screen puts a static QR next to three numbered steps and a
written fallback URL. Localized vectors are generated from the existing companion
encoder with `companion/test/generate-native-qr.mjs`; the Android app needs no network
permission or new dependency. Scanning opens the public companion with its camera
off. It does not transfer the selected field, pair devices or apply picture settings.
Manual matching and the absence of a calibration profile are stated on this screen.

Guide screens explain that Back returns from a pattern and an explicit outcome
advances the checklist. This responds to the physical test's navigation question;
patterns and recorded outcomes keep their existing behaviour.

## Bounded correction prototype

The correction lab is a separate foreground activity with an app-owned paired
chart. The left chart is unchanged; OK toggles a bounded per-channel code transform
on the right. Synthetic input is labelled as such. Reviewed camera-report means
can be passed by the explicit ADB host helper; raw images, files and identifiers
are not sent to the app. Profiles live only in memory and never write system or
picture settings. The lab pauses on inactivity/background and supports Back.

The prototype deliberately exposes its limited scope: it balances processed RGB
codes and has no optical-accuracy validation. A global OEM adapter, a calibrated
measurement pipeline and a video-player integration are separate work.
