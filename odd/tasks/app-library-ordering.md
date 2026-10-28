# App Library ordering

Feature specification and work log for ordered, pinned App Library entries.
All work remains local until explicitly delivered.

## Specs

- S1: "Però manca una funzionalità poter spostare fissare ed ordinare le app nella lista Vabbè, ok. potrebbe essere una PR da implementare, testala anche localmente."
- S2: "Devi permettere anche di riorganizzare le app e porle fisse nella brra top anche nel menu principale della home e prosegui coi task (ti congilgio di fare fork del repo remoto e creare il mio fork con i commit locali e le aggiunte ovvio non in git oersonale )"
- S3: "Aggiunginal task anche la possibilita di isntallre shizuku next ed il suo store per poter in fururo installre app utili legte ad asb wireless"

## Tasks

- T1 — S1 — Define the smallest interaction and persistence model for pinning and manual App Library order — commit: 0bd1454
- T2 — S1 — Add bounded persistence and deterministic ordering helpers with unit tests — commit: 0bd1454
- T3 — S1 — Add App Library controls for pin, unpin, and moving entries — commit: 0bd1454
- T4 — S1 — Update user-facing launcher documentation and run local verification — commit: 0bd1454
- T5 — S2 — Surface pinned App Library apps in the Home top bar while preserving configured top_bar_apps — commit: a7fdb3f
- T6 — S2 — Add regression tests, documentation, build, and Chromecast verification for pinned top-bar apps — commit: a7fdb3f
- T7 — S2 — Create and push the user's remote fork — commit: blocked on explicit remote destination and push authorization
- T8 — S3 — Evaluate a safe installation path for Shizuku Next and its store for future ADB-wireless utility apps — commit: pending

## Log

- L1: Però manca una funzionalità poter spostare fissare ed ordinare le app nella lista Vabbè, ok. potrebbe essere una PR da implementare, testala anche localmente.
- L2: Verified the RED test with a checksum-validated JDK 17 and Android SDK 34 provisioned temporarily through Pi Workflow and a Herdr pane.
- L3: Implemented LibraryOrder persistence, deterministic sorting, pin/unpin, move-earlier/later controls, and launcher-guide documentation.
- L4: Focused `:app:testDebugUnitTest --tests com.gathin.tvhome.LibraryOrderTest` and full `:app:testDebugUnitTest` both passed through Herdr; `:app:assembleDebug` passed and produced the APK.
- L5: Installed the APK on the paired Chromecast; Home foreground, ClockDream screensaver, App Library, options menu, and pin/unpin persistence were verified on-device.
- L6: The next extension will reuse the existing top_bar_apps button path; pinned LibraryOrder entries will be merged into that bar and hidden from the Home grid, while configured top_bar_apps remain unchanged.
- L7: Remote fork creation and push are tracked separately and remain blocked until the user authorizes the exact remote destination and write operation.
- L8: T5/T6 implementation and verification were committed locally as a7fdb3f; unrelated themes/salotto/, probe-artifacts/, and the tracking document remain outside that work-unit commit.
- L9: Aggiunginal task anche la possibilita di isntallre shizuku next ed il suo store per poter in fururo installre app utili legte ad asb wireless
