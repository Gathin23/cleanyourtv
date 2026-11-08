# elppA module

This is the owner's separately installable cinematic fork of CleanYourTV. The user's fork scope authorises network artwork and TV-listings access here. The upstream offline `launcher/app` and all cleanup/Home-protection scripts retain their original scope and safeguards.

- Native Java and RecyclerView; API 26 minimum. Share upstream helpers/resources through sourceSets; do not modify the reference app for elppA-only behaviour.
- Follow root PRODUCT.md and DESIGN.md, the user's cinematic mockup, and docs/elppa/PHASE-1.md.
- Keep Google Assistant, TV Remote Service, Cast, TV providers, Sony settings and input services enabled. No root, accessibility remapper or debloat is part of Phase 1.
- Display only app-published, accessible content. Demo entries are opt-in and visibly labelled. Never imply universal OTT history, entitlement or Google's personalised recommendation engine.
- Provider queries and artwork decoding stay off the UI thread; cap caches, response sizes and work queues. No video backgrounds or hidden-Home polling.
- Scope title links to their source package; retain app-launch fallback. No playback-password or subscription handling.
- From launcher/: run `./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease`. Keep the upstream APK below its existing gate. Run affected Python suites for setup/documentation changes.
- Native captures and actual Sony remote/phone/standby tests remain acceptance gates. A successful build or injected key is not evidence of physical-mic compatibility.
- Do not commit APKs, signing keys, personal TV identifiers, backups or test screenshots. Draft PRs must distinguish passed automated checks from pending TV tests.
