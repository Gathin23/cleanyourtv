# Validation ledger

This ledger distinguishes software checks from hardware evidence. No accurate-colour,
universal-compatibility or automatic-correction claim is made.

## Automated and local checks

- **JavaScript:** 30 tests pass for capability interpretation, numerical aggregates,
  invalid frames, privacy allowlists, camera lifecycle, sampling geometry and pattern navigation.
  The initial missing behaviours were observed failing before implementation.
  Coverage of the three loaded pure modules (`probe`, `capture`, `patterns`) is
  100% of lines and 91.49% of branches. This does not include browser DOM wiring.
- **Android:** eleven JVM tests pass for explicit session start, state/restore, invalid outcomes,
  patch bounds and inactivity. Missing behaviours were observed failing first.
  `testReleaseUnitTest lintRelease assembleRelease` completed successfully with
  JDK 17 / SDK 34. The APK was inspected as a leanback app with no requested permissions.
- **Browser:** Chromium with explicitly simulated camera devices completed Start →
  Confirm patch → Capture → Review report. A 45-frame report was produced with
  clipping/variation observations and `colourMeasurementValidated: false`.
  Stop clears the stream. Spanish and English interfaces and a 390×844 phone layout
  were inspected. The TV presenter responds to arrows; a 1920×1080 surface produced
  a 455.36-pixel square (10% of surface area) and the expected RGB code.
  The reproducible `companion/test/browser-smoke.sh` also verified the downloaded JSON,
  a fresh patch confirmation after Stop and the absence of horizontal mobile overflow.
  Keyboard activation of Next, fullscreen (Enter and Space) and the return link are
  covered after reproducing and fixing intercepted button activation.
- **Accessibility:** both automated WCAG A/AA passes reported zero violations;
  manual-review items included the silent camera preview and report-region semantics.
  Report-region semantics were corrected before the second check. An automated audit does not
  establish complete accessibility or usability on a real TV.

## Hardware boundary

- **Android emulator:** the locally built APK installed and launched in a read-only
  Android 14 tablet emulator. Its welcome UI was captured and inspected. A reproducible
  D-pad smoke test then traversed preparation, every check/pattern, Back, summary and
  phone fields. It caught a focusable decorative cover view before the fix and passed
  afterwards. The saved
  emulator image is not modified. This is not a real TV, panel-light validation or
  a substitute for the required real-TV screenshot.

- **Physical Xiaomi:** previously identified as MiTV_MOOQ0 / croods. The current
  ADB connection did not respond. The owner cannot run the physical test now.
  No package was installed or picture setting changed on that TV in this run.
- **Physical iPhone:** exact model/browser and real capture are unverified.
- **Mac camera:** not used. Browser frames were simulated, not a physical sensor.
- **Reference instrument:** availability is unknown; no meter measurements, camera
  characterisation, held-out accuracy study or emitted-light validation exists.
- **Real-TV screenshot:** missing. This blocks the repository's new-add-on screenshot
  test and a merge-ready submission. No mockup is substituted for it.

## Review and build notes

An independent whole-branch review found two important issues: intercepted keyboard
activation and a sampling frame that did not account for video letterboxing. Both
were reproduced before their fixes. The frame now maps the same central 20% of the
decoded video that capture reads, and the instructions require the uniform patch to
  cover the entire sampling frame. Geometry tests and the browser smoke check cover
  landscape and portrait video, including an actual synthetic-stream reconfiguration;
  the downloaded report contains the updated dimensions.

The full repository suite ran 747 tests: 735 passed, 11 skipped and one failed because
the required real-TV screenshot is absent. That evidence gate remains open.

Android Lint reports zero errors and six warnings: SDK34 target age, large TV banner
vector, landscape orientation, two unused strings and a programmatic View constructor.
SDK34/landscape follow the repository's small TV add-on target; no lint rule is disabled.
The warnings do not prove newer-Android compatibility or real-device readiness.

The repository's no-APK check scans the working directory, including ignored build
outputs. Keep local APK artifacts outside `addons/` before running that suite; no binary
belongs in the contribution. The absent real-TV screenshot is a separate unsatisfied
evidence requirement, not a reason to weaken the check.

## Physical TV acceptance checklist

Record only model, Android version and firmware; do not publish network addresses,
serials, account information or private device names.

- [ ] Build locally, install on the explicitly connected owned TV and confirm launch.
- [ ] Test hidden/shown module-card behaviour and return to Home.
- [ ] Walk every screen with the real D-pad, including all edges, notes and deletion.
- [ ] Check Back, Home, ordinary picture-menu interruption and resume.
- [ ] Check the five patterns, field IDs, OK label toggle and two-minute pause/resume.
- [ ] Confirm screen-on flags and timers stop after leaving the app.
- [ ] Confirm SDR output path; inspect actual light with a reference before colour claims.
- [ ] Capture a sanitised real-TV image as `screenshot.jpg`, 16:9 and at most 300 KB.
- [ ] Replace the draft tested-on/index entry with the measured model/OS/firmware scope.
- [ ] Run the complete repository suite and add-on checks again.

## Physical phone acceptance checklist

- [ ] Serve the companion on trusted HTTPS and record phone model, OS and browser.
- [ ] Confirm rear camera, observed manual-control capabilities and actual settings.
- [ ] Verify denied permission, Stop, background/restore and late permission cleanup.
- [ ] Confirm manual patch ID, sample distinct frames and inspect report before export.
- [ ] Compare with an independent reference instrument before interpreting RGB as colour.
- [ ] Validate a separate device pair before any cross-model or cross-brand support claim.
