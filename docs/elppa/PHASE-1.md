# Phase 1 — cinematic Home on Sony X75

Target: original Sony KD-50X75 (2021), Android TV; exact firmware and app coverage await device testing.

## Implementation order

1. [x] Add a separately installable `:elppa` application, retaining the upstream `:app` and notices.
2. [x] Build the cinematic hero, horizontal apps dock, horizontal Continue Watching / Play Next rail, clock, network and settings controls.
3. [x] Implement read-only Watch Next / preview-channel discovery, permission handling, filtering, provider selection and diagnostics.
4. [x] Add bounded asynchronous artwork loading, cache, lifecycle cancellation and safe app/title launching.
5. [x] Add app order/visibility controls, demo mode, motion/rotation preferences and deterministic D-pad focus.
6. [x] Add explicit ADB install / make-Home / restore commands with backup and no package disabling.
7. [x] Run unit tests, lint and release builds. Native UI acceptance remains open below.
8. [ ] Validate on the real X75: physical mic, phone remote, Sony picture/sound settings, Input, held volume, deep links, standby and reboot.

Steps 1–7 are implementation work. Step 8 is a device gate, not something an emulator or injected key can certify.

## Scope and behaviour

- Reuse CleanYourTV's native Java/resources and settings/remote-launch integration.
- `io.github.sanskarixd.elppa` is separate from `com.gathin.tvhome`; namespace stays `com.gathin.tvhome` so shared helpers compile without duplication.
- The fork adds INTERNET and READ_TV_LISTINGS to its own manifest only. No microphone, usage access, accessibility, root or all-EPG permission is needed for Phase 1.
- Hero recommendations come from accessible app-published preview programs, with Watch Next as a fallback. This is not Google's proprietary personalised feed.
- Continue Watching depends on the source app publishing readable rows. Do not claim Netflix, Prime or JioHotstar coverage before device evidence.
- Demo content is explicitly labelled and opt-in. It never impersonates viewing history and cannot launch playback.
- Keep stock Assistant, Remote Service, Cast, TV-provider/input services and Sony settings enabled. First install does not switch Home.
- No third-party catalogue/account API or artificial watch-progress tracking in this phase.

## Acceptance checks

- App launch and Home remain useful without content permission, network or published rows.
- Focus remains visible, each row remembers position, hidden items do not leave focus stranded, and Back does not trap the user in menus.
- Source artwork is decoded to display size; requests/cache have limits; queries/downloads run off the main thread.
- Settings expose permission recovery, source selection, refresh and local diagnostics without dumping private titles to public logs.
- Content links are package-scoped and fall back to opening the source app.
- Build/sign/update instructions identify the correct package and explicit activity class.
- Compare latency, jank and PSS on the actual TV before any performance claim.

## Evidence

- `:elppa:testReleaseUnitTest`: 7 tests, no failures/errors.
- `:app:testReleaseUnitTest`: 315 tests, no failures/errors.
- Both modules: `lintRelease` and `assembleRelease` passed. Lint has zero errors; warnings include inherited reference code/resources and TV-specific choices. No claim of warning-free lint.
- `:elppa:assembleDebug` passed after the review fixes.
- Optimised APK sizes: elppA **964,457 bytes**; unchanged reference app **587,321 bytes**, below its 589,824-byte gate. Size is not a runtime-performance measurement.
- Full Python run: 3,214 tests, 74 skips, initially two failures (the upstream single-module expectation and the new tool's missing README row). Updated the fork's explicit allowed module list without permitting add-ons, added the tool row, then reran all 26 tests in the affected add-on, tools-table and elppA setup suites: all passed.
- Setup tests cover malformed components, recovery endpoints, a default-Home chooser, failed restoration and zero-exit security errors.
- Code review fixes: reduced Watch Next metadata keeps its type, wide cards prefer wide art, hero images stay visible while replacement art loads and crossfade with motion enabled, enlarged-text title space adapts, initial loading differs from empty data.
- Native emulator capture, full D-pad interaction and font-scale 1.3 review are pending; the Android TV emulator ran without hardware acceleration but did not reach a usable ADB state, so no native capture or UI pass is claimed. Physical Sony testing has not been performed.
- The build environment used the pinned Gradle 8.9 distribution (checksum verified), JDK 17 and SDK/build-tools 34. Public Maven dependencies were fetched through an environment-local download bridge because direct Java networking was unavailable. The bridge and SDK provisioning are not part of the repository or required by normal builds.

## Next device session

Follow [SETUP.md](SETUP.md). First install as an app, record provider counts for each installed OTT, and verify title links. Then choose Home with the recovery record and test the real mic, Google Home phone remote, Sony settings/inputs, standby and reboot. Keep package lists and screenshots with personal identifiers out of commits. No broad debloat is needed.

## Later phases

Only after the X75 baseline: improve ranking based on explicit local preferences, consider a licensed catalogue/availability source for missing recommendations, and decide whether a separate in-launcher search adds value. These are distinct from the stock Assistant and are not shipped as Google's recommendation engine in Phase 1.
