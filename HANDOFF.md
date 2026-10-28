# Quiet Machine — handoff to VS Code Codex

## State at pause (28 October 2026)

The user requested a pause and a committed handoff to continue locally with TV access.
**This is a buildable implementation checkpoint, not a completed or TV-validated redesign.**

- Repository: https://github.com/davygit67/cleandavystv
- Branch: `feature/quiet-machine-rams-ui`
- GitHub REST metadata confirmed `fork: true`, parent `Gathin23/cleanyourtv`.
- Only `origin` pointing at Davy's fork is configured. Never push to upstream or merge
  to main without an explicit request. No force push.
- Read `AGENTS.md`, `docs/quiet-machine.md`, and the original user brief before continuing.
- User wants the provided “02 / Quiet Machine” three-panel reference followed closely.
  The reference was visible in chat, but no separate reference PNG was available as a
  workspace file. Have the user attach it in the new VS Code conversation if needed.
- Latest visual change: **LA, USC and Florence photographs**, replacing the initial
  Manhattan request. Three licensed photos and attribution are in
  `launcher/quiet-machine-wallpapers/`.
- User authorized routine decisions and asked to stop repeated approval questions.
  Sandbox execution approvals are separate from task authorization. Do not repeatedly
  ask about decisions already authorized. User intends to continue with physical TV
  access locally; inspect the actual target, backup state and signing key before any
  installation. Do not infer that a connected device is the intended TV.

## Implemented in this checkpoint

- `QuietMachineView.java`: native Java Home/Library/Control surface, persistent rail,
  ivory/graphite palette, thin borders, thicker orange focus outlines and inset fills.
- Home: time-aware personalized greeting (existing name or Davy), device clock/date,
  downsampled grayscale wallpaper, WATCH wired to existing HDMI/live-TV picker,
  existing adapter tiles and order, real adapter launch actions.
- Library/Search: installed launchable apps, real bounded-size app icons, three columns,
  label/package search and music-app filtering. Unsupported media catalogs explicitly
  say they are not connected. No fictional production media catalog.
- Control: actual media volume when supported, disabled/unconnected ambient light,
  persistent Focus modes, display/sound/network/system settings links, launcher
  preferences, HDMI and Classic launcher recovery.
- Focus sets launcher-only dimming plus a best-effort window-brightness request;
  no global picture, permission or service changes.
- Photo selection cycles LA/USC/Florence by OK on the panorama, persisted privately.
  Photo pack is separate from APK to preserve the size budget. Existing
  `wallpaper.jpg` is the fallback. Photo installation instructions preserve config.
- MainActivity retains its existing identity, lifecycle, boot/remote/HDMI paths;
  Quiet Machine is a native overlay. `AppAdapter` exposes a snapshot and its existing
  launch implementation. Classic launcher remains accessible and has a return button.
- `QuietMachineModelTest.java`: greeting boundaries, truthful filters, brightness policy.
- Documentation: `docs/quiet-machine.md`, linked from README; photo credits included.

## Validation and remaining work

Completed before pause:

- Final repository-wide Python suite: **2,132 tests, 36 skipped, OK**.
- Final offline Java test/lint/release build: **BUILD SUCCESSFUL** (47 seconds).
- Final lint: **0 errors, 100 warnings**; final APK: **557,893 bytes**.

- JDK 17 + platform 34 + build-tools 34.0.0 configured in the cloud workspace.
- `testReleaseUnitTest lintRelease assembleRelease` passed during implementation.
- 297 JVM tests, zero failures. Lint completed with zero errors (warnings remain,
  including English text formatting and the programmatically constructed custom view).
- Release APK measured 557,893 bytes on the final successful build, below
  589,824-byte / 576-KiB gate. Check the final size again after further edits.
- No manifest, permission, dependency, minSdk, package identity, Home guard,
  install/restore script, system package or physical TV changes.

**Not completed:**

- No screenshots of implemented screens yet. No visual comparison/refinement cycle.
- No runtime verification of D-pad/OK/Back/Home, actual app launches, HDMI, volume,
  classic recovery, screen sizing or screensaver on a TV or working emulator.
- Software Android TV API 30 emulator was started without KVM; it remained offline
  during checks. Do not describe it as a successful emulator test.
- Photos are committed as a separate pack, not installed on a TV. Without the pack or
  an existing wallpaper, the panorama is blank. Improve that empty state before delivery.

Priority next steps:

1. Pull this branch, inspect the diff, and build locally using the original signing key.
2. Review overlay integration: underlying launcher focus/animation callbacks may still
   contend with the new surface. Exercise all rail/content edges, return-from-app focus,
   package changes, Home intents, settings return, and HDMI popup anchoring. Fix observed
   issues without bypassing existing recovery/remote logic.
3. Check 720p/1080p/4K and real TV font scaling. Control cards/footers may need further
   spacing refinement. Library currently constructs native rows rather than recycling
   them; profile a large installed-app list before making performance claims.
4. Confirm volume behavior on the actual TV, including fixed-volume/CEC and denied
   access. Ensure the fallback remains honest. Confirm Focus feedback on firmware that
   ignores window brightness.
5. Add photo pack safely (merge individual images through the existing ThemeProvider;
   do not replace the user's config.json), improve absent-photo behavior, capture all
   three real screens, compare to the provided reference, refine until close.
6. Verify that Classic launcher retains configuration, module cards, ordering, screensaver,
   installed-app detection, source input access and physical remote helpers.
7. Finish full tests/lint/build, inspect size/permissions, document real device/firmware
   results and limitations. Commit and push only this fork branch.

## Cloud paths (not assumed to exist on the local machine)

Repository `/workspace/cleandavystv`; SDK `/workspace/android-sdk`; JDK
`/workspace/jdk17`; Gradle cache `/workspace/gradle`.
Build logs `/tmp/quiet-build.log`, `/tmp/quiet-build-final.log`;
Python suite `/tmp/repo-tests.log`; emulator `/tmp/emulator.log`.
These paths and logs are ephemeral and are not committed. Build output APKs, SDKs,
keys and screenshots containing device identifiers must not be committed.

## Resume prompt

> Continue the Quiet Machine redesign in my fork `davygit67/cleandavystv`, branch
> `feature/quiet-machine-rams-ui`. Pull the branch and read HANDOFF.md, AGENTS.md and
> docs/quiet-machine.md first. This is a buildable checkpoint, not a finished redesign.
> Preserve my current app configuration, package identity, HDMI/remote/Home recovery
> and rollback paths. Follow the attached three-panel reference, using the updated LA,
> USC and Florence photo direction. Finish the native UI, runtime tests, screenshots
> and visual refinement. I authorize routine implementation and testing decisions;
> don't ask repeatedly for decisions already approved. You may inspect my connected
> TV and prepare installation, but identify the correct device, back up existing state
> and verify the original signing key before updating. Do not uninstall/disable system
> packages or change the default Home app. Push commits to my fork's feature branch
> only. Report exactly what was tested and what still needs physical remote verification.
