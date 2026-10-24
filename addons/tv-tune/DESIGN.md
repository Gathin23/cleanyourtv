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
