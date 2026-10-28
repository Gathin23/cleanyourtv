# App Library ordering

Feature specification and work log for ordered, pinned App Library entries.
All work remains local until explicitly delivered.

## Specs

- S1: "Però manca una funzionalità poter spostare fissare ed ordinare le app nella lista Vabbè, ok. potrebbe essere una PR da implementare, testala anche localmente."

## Tasks

- T1 — S1 — Define the smallest interaction and persistence model for pinning and manual App Library order — commit: a194bb2
- T2 — S1 — Add bounded persistence and deterministic ordering helpers with unit tests — commit: a194bb2
- T3 — S1 — Add App Library controls for pin, unpin, and moving entries — commit: a194bb2
- T4 — S1 — Update user-facing launcher documentation and run local verification — commit: a194bb2

## Log

- L1: Però manca una funzionalità poter spostare fissare ed ordinare le app nella lista Vabbè, ok. potrebbe essere una PR da implementare, testala anche localmente.
- L2: Verified the RED test with a checksum-validated JDK 17 and Android SDK 34 provisioned temporarily through Pi Workflow and a Herdr pane.
- L3: Implemented LibraryOrder persistence, deterministic sorting, pin/unpin, move-earlier/later controls, and launcher-guide documentation.
- L4: Focused `:app:testDebugUnitTest --tests com.gathin.tvhome.LibraryOrderTest` and full `:app:testDebugUnitTest` both passed through Herdr; `:app:assembleDebug` passed and produced the APK.
- L5: Installed the APK on the paired Chromecast; Home foreground, ClockDream screensaver, App Library, options menu, and pin/unpin persistence were verified on-device.
