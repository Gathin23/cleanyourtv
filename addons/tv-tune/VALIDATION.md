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

- **Physical Xiaomi:** MiTV-MOOQ0 / croods, Android TV 10 (API 29), firmware
  `QTG3.200305.006.5135`. A locally built APK was installed and tested using ADB
  input on 2026-10-28; see the dated results below. Physical-remote and
  instrumented emitted-light checks remain separate. Picture settings were not changed.
- **Physical iPhone:** exact model/browser and real capture are unverified.
- **Mac camera:** not used. Browser frames were simulated, not a physical sensor.
- **Reference instrument:** availability is unknown; no meter measurements, camera
  characterisation, held-out accuracy study or emitted-light validation exists.
- **Real-TV screenshot:** supplied from this physical TV as `screenshot.jpg`,
  1280×720 JPEG, 77,475 bytes, updated for native 0.1.1. The native ADB source capture is 1920×1080.
  The screenshot and tested-on repository checks now pass in the local test copy.

## Review and build notes

### Public companion, 2026-10-24

- URL: https://tv-tune-kalt2212.vercel.app/ — public contributor-hosted development preview.
- Web version: 0.1.1; deployed source commit: `15721fb690a3a4963e5bc9c39b86ed17943e3c51`.
- Production deployment `dpl_3hMMU61FD7SPEk7JS21vZ6cZrpub` reached READY; static build completed
  in 48 ms. No backend, analytics, image upload or authentication was added.
- All seven static app assets and the MIT licence were retrieved over normally validated
  HTTPS without cookies, credentials or a deployment bypass; their SHA-256 hashes matched source.
- Both browser scripts passed against the public URL, including the synthetic 45-frame
  capture, portrait/landscape frame geometry, JSON download, Stop, keyboard/fullscreen,
  mobile overflow and automated accessibility checks. Physical-device validation is still pending.
- First improvements reproduced before their fixes: an old cancelled capture could unlock
  a new one, and Stop left an active-controls label. The deterministic lifecycle smoke test
  now verifies both behaviours on the local server and public site. Unit tests remain 30/30.

### Independent review and native build

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

- [x] Build locally, install on the explicitly connected owned TV and confirm launch.
- [ ] Test hidden/shown module-card behaviour and return to Home.
- [ ] Walk every screen with the real D-pad, including all edges, notes and deletion.
- [ ] Check Back, Home, ordinary picture-menu interruption and resume.
- [x] Check the five patterns, field IDs, OK label toggle and two-minute pause/resume (ADB input on the physical TV).
- [ ] Confirm screen-on flags and timers stop after leaving the app.
- [ ] Confirm SDR output path; inspect actual light with a reference before colour claims.
- [x] Capture a sanitised real-TV image as `screenshot.jpg`, 16:9 and at most 300 KB.
- [x] Replace the draft tested-on/index entry with the measured model/OS/firmware scope.
- [x] Run the complete repository suite and add-on checks again.

## Physical phone acceptance checklist

- [ ] Serve the companion on trusted HTTPS and record phone model, OS and browser.
- [ ] Confirm rear camera, observed manual-control capabilities and actual settings.
- [ ] Verify denied permission, Stop, background/restore and late permission cleanup.
- [ ] Confirm manual patch ID, sample distinct frames and inspect report before export.
- [ ] Compare with an independent reference instrument before interpreting RGB as colour.
- [ ] Validate a separate device pair before any cross-model or cross-brand support claim.

### Cloud simulation follow-up, 2026-10-24 (web 0.1.2)

- Fresh checkout matched remote `37aea7e7552af633908f6a9ad679f72c80a9907f`;
  baseline public assets and LICENSE matched source over unauthenticated HTTPS.
- New deterministic DOM/event simulations reproduced two failures before fixes:
  withdrawing patch confirmation during capture still generated a confirmed report,
  and restoring the TV presenter after pagehide left its inactivity timer stopped.
  The capture now aborts on withdrawal; presenter restoration owns exactly one timer.
  A third failing regression showed that the phone sampling-frame ResizeObserver
  was not reattached after restoration; pageshow now reattaches and refreshes it.
  These simulations exercise production wiring with fake DOM/media/clock objects;
  they are not Chromium, Safari, camera-sensor or panel evidence.
- Android English/Spanish phone-field instructions now point to the public companion,
  require manual patch ID/RGB matching and retain the no-pairing/accuracy limitations.
  Source-contract tests cover both languages and exact parity of all eleven Android
  and web patch IDs/RGB triples. XML parsing passed for all eight Android XML files;
  the manifest still requests no permissions or services.
- All eleven existing pure-Java JUnit tests passed using Eclipse ECJ with Java 17
  target on the available Java 21 runtime. This is not a Gradle Android build.
  Full Android build/lint and emulator UI were not rerun: SDK/adb/emulator are absent,
  and Gradle's JVM dependency bootstrap failed with a network-unreachable error.
- Local Chromium launch was blocked by the execution sandbox before UI testing;
  the attempted supported escalation failed during sandbox setup. Neither result
  is an application test failure or a browser pass. No physical camera or TV was used.
- The initial Python run without adb-shell discovered 744 tests (739 passed, four
  skipped, one screenshot failure). After installing tools/requirements.txt in an
  isolated environment, then adding Pillow for optional image checks, the final
  suite ran 747 tests: 743 passed, three skipped and one failed for the missing
  real-TV screenshot. Skips were the main-branch-only model count, a non-loopback
  network-interface check and an optional recorded Perfetto trace. No test was weakened.
- [MEASUREMENT_PLAN.md](MEASUREMENT_PLAN.md) adds a proposed reference-instrument
  protocol with held-out data, uncertainty and preregistered engineering limits.
  No study was performed and no accuracy or automatic-correction claim was added.

- Final JavaScript/source-contract suite: 38/38 pass; syntax checks and both browser
  script shell checks pass. Pure modules have 100% line and 92.91% branch coverage;
  this coverage excludes the dynamically executed app DOM wiring. Independent diff
  review found no blocking issues.
- Cloud Chromium UI checks on the baseline verified English page/presenter loading,
  graceful camera-unavailable handling, Enter/arrow patch navigation, expected RGB
  and approximately 10% patch area. Fullscreen did not enter in that environment.
  Synthetic camera configuration and automated accessibility injection were not
  available, so the prior full browser smoke/accessibility results were not rerun.

### Web 0.1.2 publication verification

- Code commit: `75b65a21a72a74414d7f803db0a77dbe810070b1`, verified as the remote
  `feat/tv-tune` head after an atomic expected-head update.
- Production deployment: `dpl_J12NxTRQ4uMUvkSpCsN7gvaskaKg`, READY, with
  https://tv-tune-kalt2212.vercel.app/ assigned and no alias error.
- Only the seven public static assets and repository MIT LICENSE were deployed.
  Every public response returned 200 over normal HTTPS and matched source SHA-256,
  without authentication, cookies or protection bypass.
- GitHub reported zero workflow runs, check runs and commit statuses for this commit;
  this is no CI evidence, not a green CI result.
- Cloud Chromium confirmed Enter-based patch navigation and actual browser Back
  restoration: the presenter returned Paused and resumed the preserved patch without
  advancing. This was actual UI/navigation, not injected page events.
- Live inactivity protection passed after actual Back restoration: resumed grey-192
  at 11:48:21 UTC and observed Paused at 11:50:45 UTC, without clock/event injection.
  This validates timeout behaviour in that cloud browser, not physical TV operation.

### Language handoff follow-up (web 0.1.3)

- Reproduced English resetting to Spanish through the presenter return link and
  phone brand/home link. Both own-home links now retain English; Spanish remains
  the default. External links and the explicit language switch are unchanged.
- Four new source/DOM regressions cover both links in English and Spanish. All
  42 JavaScript/source-contract tests pass, along with syntax and shell checks.
  These are software checks, not additional physical-camera or panel evidence.
- Web 0.1.3 source `41a0c3c8ad7c68102105b10674eea727574e0c05` is published in
  production deployment `dpl_G7mCHB9TEKmagjwZoqybyZLWaeh6` (READY, public alias assigned).
  All seven static assets and LICENSE again matched source SHA-256 without auth.
- Actual cloud-browser round trips passed: English presenter → phone → Home retains
  English; switching to Spanish and presenter → phone retains Spanish. External
  source/proposal links are unchanged. Independent patch review found no blockers.
- GitHub had no workflow runs or check runs for this source commit; no green CI is claimed.

### Android cloud toolchain follow-up

- Downloaded official JDK 17, Gradle 8.9 and Android SDK archives into isolated cloud
  tooling directories. All eleven existing JVM tests passed again with actual JDK 17
  javac/runtime. No global configuration, device connection or launcher change.
- Gradle resolved AGP 8.5.2 dependencies from official repositories with normal TLS
  verification using the environment's existing Java truststore.
- Full `testReleaseUnitTest lintRelease assembleRelease` stopped before task execution:
  Android SDK Platform 34 installation required acceptance of the
  [Android SDK License Agreement](https://developer.android.com/studio/terms).
  Acceptance was not performed. No license hashes were fabricated, no protection
  bypassed and no APK produced. Full Android build/lint remain unverified in this run
  pending the owner's decision on that agreement; this is not an application test failure.

### Android build completed after SDK approval, 2026-10-24

The owner explicitly approved the linked Android SDK License Agreement. Only that
agreement was accepted in the isolated cloud toolchain; the preceding license gate
is now resolved. No additional agreement, physical-device access or security bypass
was needed.

- JDK 17, Gradle 8.9, SDK platform 34/build-tools 34.0.0:
  `testReleaseUnitTest lintRelease assembleRelease` **passed** in 59 seconds,
  53 tasks executed. This supersedes the earlier build-not-run limitation.
- Gradle JUnit XML: eleven tests, zero failures, zero errors, zero skipped.
- Android lint: zero errors and five warnings: large vector (`VectorRaster`), fixed
  landscape (`DiscouragedApi`), two unused strings (`UnusedResources`) and programmatic
  View constructor (`ViewConstructor`). No suppressions or rule changes were added.
  The target-age warning from the historical run was not emitted in this run.
- Built APK inspection: `io.github.kalt2212.tvtune`, minSdk 26/targetSdk 34, leanback
  activity and zero requested permissions. Size: 29,440 bytes. The local test APK was
  moved outside the repository before general checks; no APK was committed, deployed
  or made available as a public download.
- No application source change was needed for this build. Web remains version 0.1.3
  at source commit `41a0c3c8ad7c68102105b10674eea727574e0c05` and its verified deployment.
- This build is not emulator, real-TV, phone-camera or reference-instrument evidence.
  The real-TV screenshot and physical acceptance gates remain unchanged.
- Post-build regression run: JavaScript 42/42 and syntax checks passed; complete Python
  suite 747 tests, 743 passed, three skipped, one missing-real-TV-screenshot failure.
  No APK remained under addons and no repository evidence gate was relaxed.

### Maintainer feedback reconciliation, 2026-10-24

[Maintainer comment](https://github.com/Gathin23/cleanyourtv/discussions/1140#discussioncomment-19163702)
asks for the independent module card, explicit web data destinations and optional
phone use with standalone TV checks. The manifest/module card and TV guide already
satisfy the architectural boundaries; the README now states the offline/optional
behaviour and separates Vercel HTTP request metadata from local camera processing,
reviewed JSON downloads and optional GitHub navigation. It makes no zero-host-log
or hosting-retention promise.

Three new source-contract tests guard the Android permission/module-card boundary,
static same-origin assets/CSP and absence of application networking/upload APIs.
They are not a runtime traffic audit or a guarantee about provider logs. Existing
export allowlist tests still cover exclusion of private fields. No runtime assets
or Android implementation changed; web 0.1.3 does not need redeployment.

JavaScript: 45/45 passed. Full Python suite: 747 tests, 743 passed, three skipped,
one expected missing-real-TV-screenshot failure. The invitation to submit when ready
is not a screenshot waiver; no PR was opened and no new Discussion reply was posted.

### Patch handoff milestone (web 0.2.0)

- TV and phone links now carry one allowlisted patch ID in the URL fragment and
  preserve language. The receiving phone preselects the patch without starting its
  camera or confirming the observed TV. Fragment data is not sent in HTTP requests.
- The presenter exposes a copyable phone link. Copy is user-triggered, never reads
  the clipboard and has a visible-link fallback; stale copy completion cannot claim
  the newly selected patch was copied. Generated links strip credentials and unknown
  query fields from self-hosted URLs. A copied link is a snapshot, not synchronisation.
- Opening the handoff panel pauses/hides the uniform patch. Closing it leaves the
  presenter paused until explicitly resumed, avoiding instruction overlap with an
  active patch. History/fragment changes invalidate capture confirmation and cancel
  in-flight capture. Jumping to the camera section does not erase the chosen patch.
- Initial feature tests failed on the prior implementation. Regression coverage also
  catches anchor navigation resetting the patch, malformed/duplicate IDs, history
  races, clipboard denial and handoff-panel state. DOM simulations are not camera
  or rendered-layout evidence. Pure modules retain 100% line / 93.38% branch coverage;
  dynamically executed application DOM wiring is excluded from that coverage figure.
- Android application code is unchanged; its prior verified build/lint result applies.
  No Android permissions, launcher changes, backend or new runtime dependencies.

Catalogue preparation: a non-mutating merge simulation against upstream main
`eb0131a32fd8567086f39002f1782f7c1b078ca6` found an add-on-table conflict caused by our
new row bordering an upstream edit. Moving only the unchanged TV Tune row after
Weather resolves that conflict in the simulation. No upstream merge was performed,
no unrelated rows were changed, and the real-TV tested-on/screenshot gate remains.

Pre-publication checks: 59/59 JavaScript/source-contract tests and syntax/shell checks
passed. Full Python suite: 747 tests, 743 passed, three skipped, one expected failure
for the missing real-TV screenshot. Independent review found no remaining blocking
issues. Keyboard focus is preserved when jumping to camera controls or closing the
handoff panel with Escape. Live browser verification is recorded after deployment.

Web 0.2.0 publication: code `839917198586b17087f623e8bb42765addbce26a`, production
`dpl_3UzvU2KJZqG61vtjEu7iHXrfNfoo` READY with the public alias assigned. All seven
static app assets and MIT LICENSE matched source SHA-256 over unauthenticated HTTPS.
GitHub reported no check runs for this commit, not a green CI result.

Cloud Chromium live QA passed red/blue deep links, matching RGB, English/Spanish
handoffs, invalid-ID fallback, camera-off/unconfirmed arrival, camera-section jump,
phone selection updating TV links, paused/hidden pattern with handoff open and Escape
focus restoration. Copy reported success and the manual link remained available.
Visual desktop inspection at 1180×757 found no horizontal overflow or active-patch
obstruction. Forced clipboard denial, mobile viewport, camera capture and a fresh
automated accessibility audit were not run in this cloud browser; its documented
API lacks those simulation/viewport controls. No physical-phone scanning is claimed.

## Local QR handoff, companion 0.2.1 (2026-10-24)

The web TV handoff panel now renders the current fragment-only phone link as a
local QR, while hiding and pausing the measurement patch. No QR service, runtime
CDN, new permission or Android change is involved. Manual links remain available,
including when an unusually long self-hosted URL exceeds the encoder's bounded
capacity. Phone arrival still requires explicit patch confirmation and camera start.
The pinned MIT encoder and reproduction instructions are documented in
`companion/THIRD_PARTY.md`; its license remains in the deployed source.

Pre-publication verification: 65/65 JavaScript tests, syntax checks and diff checks
passed. Independent jsQR decoding passed all 154 synthetic cases: 11 patches × two
languages × seven image variants (matrix, SVG rasterization, multiple sizes and
rotation), plus a blank-image negative control. Reproduce with the optional
`companion/test/qr-decode.mjs` instructions. These checks do not prove physical TV
or iPhone scanning. Full Python suite: 747 tests, 743 passed, three skipped, one
failure for the unchanged missing real-TV screenshot gate. Android was unchanged;
its earlier successful build/lint/unit-test evidence is not a fresh build claim.

Publication verified: code `c54166590919bd0f199322d98cce8aa004bd64bc`, production
`dpl_5yMkr2w8EBbahFY5A45ySY5JhDUL` READY with the public alias assigned. All nine
static app assets plus MIT LICENSE matched source hashes over unauthenticated HTTPS.
GitHub reported zero check runs for this exact commit; local tests are not CI.
Independent final diff review found no remaining blockers after a keyboard-focus
regression was fixed and covered by tests.

Live cloud Chromium verification passed visible QR rendering, pause/hide behaviour,
repeated Next with retained button focus, QR replacement after changing patch,
Escape focus restoration with the pattern still paused, and phone arrival with
camera off and confirmation unchecked. Independent jsQR decoding of three actual
1180×757 browser screenshots returned exact English red, English green and Spanish
blue links. This supplements the 154 generated-image checks, but is still software
rendering, not physical TV/phone scanning. Narrow viewport layout was reviewed in
CSS only; physical glare/distortion, optical scanning and camera measurements were
not run. The real-TV contribution gate is unchanged; no upstream PR was opened.

## Camera-readiness clarity, companion 0.2.2 (2026-10-24)

Advertised manual capability is now separate from last-observed exposure, white
balance and focus modes. Missing browser settings are unknown, not inferred manual
or automatic. The UI explains that modes are sampled at camera start and capture
boundaries, not continuously. Known allowlisted start/end differences add a
`camera_settings_changed` warning; matching or unknown snapshots do not prove a
camera lock. No controls are applied and the v1 report structure is unchanged.

Ten new regression failures were observed before implementation. Final companion
checks: 76/76 tests, syntax and diff checks passed; independent review found no
blocking issues. The dependency-complete Python environment ran the comparable
747-test suite: 743 passed, three skipped, one failure for the missing real-TV
screenshot. A separate system-Python run discovered 744 tests with four skips
because `adb_shell` was absent; it is not the full dependency-complete baseline.
No Android, launcher, workflow, permission or runtime dependency changes occurred.

Publication verified: code `adc30fd23d9c3191de7c47283855366f425cff1c`, production
`dpl_AaDWwLKFBgmznK5WpPZaDnJTwj3y` READY with public alias assigned. All ten allowed
files (nine static assets plus MIT LICENSE) matched source SHA-256 over public HTTPS.
GitHub reported no check runs for this commit, not a CI pass.

Live cloud Chromium checks passed English/Spanish camera-off capability and
last-observed rows, visible non-continuous/no-lock explanations, and missing-camera
error recovery in both languages without permission grants. Start remained enabled;
Stop and capture stayed disabled. Desktop 1180×757 layout was readable without
horizontal overflow. QR regression passed: the actual Spanish red browser screenshot
independently decoded to the correct patch link, with the patch paused/hidden.
Active-camera readiness transitions were tested through mocks, not physical capture;
mobile viewport and a full accessibility audit were not run. No additional defect
was found in the reviewed bounded workflows. Real-TV D-pad/visual evidence and
reference-instrument validation remain separate, unresolved external gates.

### Android TV emulator in the cloud VM, 2026-10-25

This run used the assistant's isolated cloud VM, not an owner's computer or TV.
The tested application source was `f99a5934a1ac2ec08e045686ec20681986440b3a`;
no application or launcher code changed for these checks.

- A fresh official JDK 17 / Gradle 8.9 / SDK 34 build completed
  `testReleaseUnitTest lintRelease assembleRelease`: eleven JVM tests passed,
  lint reported zero errors and the same five warnings, and the local test APK
  was 29,440 bytes. Its SHA-256 was
  `cbd43dc6f2ebccbc44f94f75c05dc8efa781a1789ab4f26abf52b4f621db86c1`.
  The APK remains outside the repository and is not a public download.
- Official Android Emulator 37.2.12 ran the Android TV API 31 x86 revision 4
  image, Android 12 build `STT9.221129.002/9351024`, at 1280×720. ADB reported
  television, leanback and leanback-only features, `ro.kernel.qemu=1` and
  `sys.boot_completed=1`. Shell identity was uid 2000, not root.
  The VM has no usable KVM; CPU emulation used `-accel off` and graphics used
  SwiftShader. This is not a responsiveness or physical-device performance test.
- The fresh API 31 boot took about twenty minutes and included a framework
  Watchdog restart before TV Tune was installed. Retained logcat identifies
  system_server's main thread waiting for `WindowManagerGlobalLock` during
  `systemReady`/`resumeTopActivities`. It subsequently booted without disabling
  the watchdog, SELinux, verification or authentication. This is an environment
  startup event, not a TV Tune crash. An earlier API 34 boot was stopped before
  completion; no API 34 application-runtime result is claimed.
- The APK installed successfully and launched. The existing English-language
  `companion/test/android-ui-smoke.mjs` assertions passed using injected D-pad
  events: welcome focus, preparation, all five check/pattern screens,
  pattern Back/Skip, checklist, experiment fields and return to welcome with Resume.
  The original 20-second subprocess limit timed out during an initial UIAutomator dump call;
  an external harness copy changed only its two timeouts to 120 seconds.
  No assertion was removed. All five checklist outcomes were deliberately
  Skipped; notes entry, mixed outcomes, deletion and persistence were not tested.
- Native emulator screenshots of welcome, preparation, geometry, checklist
  and a grey-192 field were inspected. Captures were 1280×720 with no private
  identifiers. The other four guide patterns were traversed and checked for the
  generic "Show pattern" accessibility description, not pixel-level pattern checks.
- A separate D-pad traversal captured all eleven experiment fields. Every
  central pixel matched its expected RGB triple exactly, and OCR matched all
  displayed patch IDs/RGB labels. These are framebuffer app-code checks, not
  emitted-light, SDR-path, camera or colour-accuracy measurements.
- Additional field checks passed both selection bounds, an Up/Down sequence preserving the
  selection, and OK hiding/restoring labels with byte-identical restoration.
  The unlabelled blue patch was 304×304 pixels, with top-left at (488, 208) and
  centre at (640, 360): 10.028% of the framebuffer, with black outside the patch.
- A real elapsed-time inactivity observation, without input or clock injection,
  found the pause screen after 126.03 seconds of observed guest uptime following
  an active-red capture. The app's window held `KEEP_SCREEN_ON` while active and
  cleared it while paused. OK restored red without advancing the selected field.
  This establishes eventual timeout behaviour, not an exact timer-latency bound.
- Injected Home left TV Tune paused and moved focus to the stock TV launcher.
  Returning with `--activity-reorder-to-front` preserved the same activity token
  and showed the paused screen; OK restored the selected green field. Window
  dumps confirmed `KEEP_SCREEN_ON` was absent after Home and after Back to
  instructions. This does not validate a physical remote or module-card integration.

Fresh unchanged-source regressions also passed 76/76 JavaScript tests and syntax
checks. The dependency-complete Python suite ran 747 tests: 743 passed, three
skipped and the same missing-real-TV-screenshot test failed. That evidence gate
was not relaxed. Emulator captures stay outside the repository; none is supplied
as `screenshot.jpg`. No new SDK agreement beyond the previously approved
`android-sdk-license`, physical-device access, upstream PR, APK publication,
launcher change or web redeployment was involved.


### Physical Xiaomi TV through ADB, 2026-10-28 (initial native 0.1.0 pass)

Source: fork `kalt2212/cleanyourtv`, branch `feat/tv-tune`, commit
`2ed9dc26f660b79f5367a45e02540d0c4949f7a2`. The old local checkout was
at `37aea7e`; it was left untouched. Tests used a separate checkout of the current
fork. Native application code was unchanged; only validation documentation, the
catalogue row and the real-TV screenshot were prepared locally.

- Device: Xiaomi MiTV-MOOQ0 (`croods`), Android TV 10 / API 29, firmware
  `QTG3.200305.006.5135`. Android reported 1920×1080 and density 320;
  native captures and the app's render-size label agreed on 1920×1080.
- A fresh JDK 17 / SDK 34 build completed `testReleaseUnitTest lintRelease assembleRelease`
  with eleven JVM tests passing, zero lint errors and six documented warnings.
  Test APK SHA-256: `2fa3ec503f673c76442a39f5b63692783462a9b33da5f7b579b12f0129b8b9f7`.
  Its manifest requested no permissions. TV Tune was absent before installation;
  the source-built app installed successfully and launched in 971 ms according
  to `am start -W`. This single cold launch is not a performance benchmark.
- Spanish welcome, preparation, all five guide/pattern screens, Back, summary,
  experiment instructions and fields were exercised on the physical TV through
  injected D-pad events. All five checklist results were deliberately Skipped;
  no picture adjustment or visual colour-accuracy judgement was recorded.
- Original/revised synthetic notes were entered and saved. Force-stopping only
  TV Tune and launching it again preserved both notes and the completed checklist.
  Android TV's keyboard consumed arrows while open; Back closed it before normal
  D-pad navigation resumed. This is not a physical-remote or microphone test.
  The delete dialog was cancelled once, preserving Resume, then confirmed to
  remove only this new installation's synthetic checklist/notes. The app returned
  to a fresh welcome. TV Tune was absent from the retained crash log buffer.
- Native screenshots were inspected. Framebuffer samples matched every shadow
  code (0, 2, 4, 8, 12, 16, 24, 32), highlight code (223, 231, 239, 247, 251,
  253, 255) and sixteen-step grayscale code. These are rendered app-code checks,
  not measurements of panel light or confirmation of the OEM SDR processing path.
- All eleven experiment fields matched their expected central RGB values. Blue
  remained unchanged after Right at the upper bound and an Up/Down sequence.
  OK hid labels and restored the exact original RGB image. The unlabelled blue
  patch occupied (732, 312)–(1188, 768), 456×456 pixels, 10.028% of the framebuffer.
- After 137.51 seconds of observed device uptime without injected input, the
  inactivity screen was present and `KEEP_SCREEN_ON` was cleared. OK restored
  the same blue field. The active field held `KEEP_SCREEN_ON` before the pause.
  This proves eventual pause/resume, not an exact timer-latency bound.
- Home moved focus to `com.gathin.tvhome`; the TV Tune window cleared
  `KEEP_SCREEN_ON`. Reordering TV Tune to the front preserved the activity,
  showed the pause screen and restored blue on OK. Back to instructions also
  cleared the screen-on flag. This does not test the physical Home button or
  hidden/shown launcher module-card preferences.
- The physical-TV welcome was captured directly with ADB. Xiaomi's screencap
  prepended a non-image diagnostic line; the PNG signature was used to separate
  those transport bytes without changing image pixels. The verified PNG was
  converted to the required 1280×720 JPEG with macOS sips. No mockup, emulator,
  generated image, account details or private device identifiers were included.
- Full repository suite: 747 tests, 736 passed, eleven skipped. Skips cover main-only
  counts and optional Pillow/perfetto checks. The sandbox initially blocked twelve
  loopback socket tests; the permitted run outside the sandbox passed. No test or
  screenshot requirement was relaxed. JavaScript tests: 76/76; syntax checks passed.
- The public companion's nine static assets and MIT licence matched the current
  source hashes over normal unauthenticated HTTPS. No web redeployment was needed.

Remaining scope: a physical remote, hidden/shown launcher module-card preferences,
ordinary picture-menu interruption, physical-phone camera capture and independent
reference-instrument validation. No launcher settings, system/picture settings,
security/debugging configuration or pre-existing user data were changed. No commit,
push, upstream PR, public evidence upload or release was performed in this test run.

Owner-assisted follow-up: the owner confirmed opening the experiment with the
physical remote and later confirmed that Right changed the field. A read-only
ADB capture showed the native grey-128 experiment screen during this exchange.
The complete physical-remote traversal, physical OK label toggle and phone-camera
capture are still unverified; this follow-up does not close those broader gates.

### Native 0.1.1 QR and app-only correction prototype, 2026-10-28

Following the owner's request to create a correction prototype, the native app now
adds a separate paired-chart activity and an explicit ADB report-import helper.
This is a processed-RGB-code experiment confined to app-owned patches. It does not
change panel settings, other apps, inputs, video players or Android security.

- Fresh JDK 17 / SDK 34 build: 17 JVM tests passed; lint zero errors and nine
  warnings (the six previously documented warnings, the new landscape activity
  and two generated QR path-length warnings). No lint rule was disabled.
  Final locally built APK SHA-256:
  `2e7db752fda3feeee535fad94b47a1d332ab8533fc20e9157980225ca2952108`.
- Four host-importer tests passed, including unconfirmed/non-neutral input,
  low-quality captures, huge/non-finite/boolean values, warnings and file bounds.
  Decimal string intent extras preserve double precision at the gain limit;
  imported sample provenance is explicitly unverified in the native UI.
- Companion regressions: 76/76. Full repository suite: 747 tests, 736 passed,
  eleven skipped. The required real-TV screenshot test passes without exceptions.
- The old TV instructions contained no QR; an independent decoder failed to find
  one before implementation. Both generated localized vectors independently
  decoded to their expected public URLs. The actual 1920×1080 Spanish Xiaomi
  screenshot then decoded to `https://tv-tune-kalt2212.vercel.app/`. All instructions,
  QR, fallback URL and buttons were visible. This is native rendering/decoding
  evidence, not an optical phone-scanning or camera-permission test.
- The synthetic neutral reference 144/128/112 produced gains 0.889/1/1.143. On the
  real TV framebuffer, all twelve corrected chart cells matched independent
  expected codes; grey 128 became 114/128/146. The original chart was unchanged.
  Toggling off restored the complete original RGB screenshot byte-for-byte.
  This is a deterministic synthetic-reference check, not a measured optical gain.
- The ADB host demo command opened/updated the preview and produced the same
  expected codes. A zero channel was refused by the native input guard, showing
  the rejection screen; Back returned to the main app. No checklist was cleared.
- A native 0.1.1 inactivity observation found the lab paused after 183.85 seconds
  without injected input, with `KEEP_SCREEN_ON` cleared. OK retained the enabled
  transform. A bare reorder-to-front intent initially reset the view; this was
  reproduced on the TV and corrected so only explicit sample extras replace the
  preview. The final APK passed Home → paused return → OK with the same enabled
  transform. The decimal-boundary sample 16.3/24.45/24.45 was accepted and rendered
  the expected corrected codes; invalid string input was refused.
- Independent review found an instruction-order defect (QR hidden after opening
  fields); it was fixed by opening the phone page first. A second review found no
  Important/Critical issues; its integer-overflow, float-precision and sample-
  provenance observations were corrected before the final build.

Physical-phone capture, the final emitted-light before/after comparison and a
reference-instrument study remain pending. The input-quality heuristics do not
prove camera accuracy, a stable camera lock or correct display calibration. The
lab does not linearise light or solve gamma/D65; highlight clipping is disclosed.
