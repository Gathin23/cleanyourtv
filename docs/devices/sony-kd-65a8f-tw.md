# Sony KD-65A8F (Taiwan firmware): compatibility report

Initial cleanup and reference-launcher handover on one Taiwan-market 65-inch
OLED TV on October 24, 2026, with owner follow-up and physical-remote checks on
October 29. Home handover passed its original reboot check. The dedicated Apps
button's regression was reproduced, then an input-layout workaround passed the
owner's physical checks without breaking held-volume repeat. The owner later
reported that the requested restart checks also passed. This does not change
the launcher's underlying routing or establish every feature's reliability.
The Google Play button's older, unrelated failure has an owner-verified native
Play Store fallback.
This remains a compatibility note, not a fully validated executable debloat
list. The retail model was supplied by the owner; Android reports a generic
platform model.

## Original Sony firmware

The firmware was recorded before any package changes. No firmware was flashed
or updated during this session.

| Field | Recorded value |
|---|---|
| Owner-reported retail model | Sony `KD-65A8F`, Taiwan |
| Android model | `BRAVIA 4K GB ATV3` |
| Product / device | `BRAVIA_ATV3_4K_TW` / `BRAVIA_ATV3_4K` |
| Android / API | `9` / `28` |
| Original Sony build display ID | `BRAVIA_ATV3_4K_TW-user 9 PTT1.190515.001.S54 672201 release-keys` |
| Build incremental | `672201` |
| Security patch | `2022-01-05` |
| Platform | MediaTek `mt5891` |

The full recorded fingerprint is:

```text
Sony/BRAVIA_ATV3_4K_TW/BRAVIA_ATV3_4K:9/PTT1.190515.001.S54/672201:user/release-keys
```

These are the exact system-reported firmware identifiers. A separate Sony
`PKG...` version from the TV's Help screen was not captured and is not inferred
from the build number. The Taiwan OLED variant is not established as equivalent
to the US/Canada or EU LCD models that share the S54 / 672201 build family.

## Launcher handover and reboot result

The reference launcher was built from commit
`cc4c7c596c089c4a16f39480fef75c3349469bf9`, version 2.7 / versionCode 8. Only
TV-specific resource configuration was changed; Java sources, manifest,
permissions, and SDK levels were unchanged. The build succeeded on a Raspberry
Pi ARM64 host using a user-local JDK 21 and Debian's native AAPT2 override.

The original Home component was:

```text
com.google.android.tvlauncher/.MainActivity
```

The replacement was installed and explicitly opened while stock Home remained
enabled. Pressing Home still returned to stock during this trial, as expected.
After the owner approved handover:

- Stock Home answered HOME at priority 2, the replacement at priority 0, and Sony
  Settings' `FallbackHome` at priority -1000. No setup wizard claimed HOME.
- `set-home-activity` reported `Success` with stock Home enabled, but both the
  HOME resolver and the focused window still selected stock Home.
- Disabling only `com.google.android.tvlauncher` and setting
  `com.gathin.tvhome/.MainActivity` again made both checks select the replacement.
  No setup wizard package was disabled.
- A subsequent reboot changed the kernel boot ID and completed normally. ADB
  reconnected, the replacement was already focused before a Home key was
  injected, and both the HOME resolver and injected Home selected it.
- The original disabled-package set plus the seven initial cleanup packages
  and stock Home remained disabled after that reboot. Other enabled packages,
  input-service registrations, display values, screensaver, timeout, keyboard,
  and accessibility settings were unchanged in the recorded comparisons.

The optional Home/Input accessibility helpers were not enabled. The owner
reported that the physical Home, Input, Settings, app shortcuts, and held-volume
checks were normal before the final launcher reboot. These were grouped owner
checks, not separate instrumented tests of every button. The October 29
follow-up found that the dedicated Apps button does not work after handover;
the earlier grouped report must not be read as individual validation of that
button. See the regression and review results below.

## Sony inputs and OLED settings

The local configuration used `com.sony.dtv.tvx` as `live_tv_package`, and hid
`com.sony.dtv.tvx` and `com.sony.dtv.tvxlauncher.programguide` only from the app
grid. Neither package nor the HDMI/tuner services were disabled.

The UI reported a physical size of 1920 x 1080 and density 320, with no overrides.
Those values were left unchanged; they describe the UI, not the OLED panel's
native resolution. The existing system screensaver and standby settings were
preserved. The launcher's clock screensaver was not selected, and its optional
Home-idle clock remained off.

## Recorded package changes, not a recommended list

Package disablement used `pm disable-user --user 0`; nothing was uninstalled and no app
data was cleared. The following seven packages were the initial cleanup batch
and were checked across reboot:

| Package | Recorded purpose |
|---|---|
| `tv.samba.ssm` | Samba TV viewing recognition |
| `com.sony.dtv.sonyselect` | Sony Select content/promotional entry point |
| `com.sony.dtv.discovery` | Sony Discover content interface; persistent-process caveat below |
| `com.google.android.tvrecommendations` | Google recommendation services |
| `com.sony.dtv.demomode` | Retail demo mode |
| `com.sony.dtv.demosupport` | Retail demo support |
| `com.sony.dtv.multiscreendemo` | Retail multi-screen demo |

Stock Home was a separate eighth change, made only after the replacement had
been tested. A later, separately backed-up batch disabled five more packages:

| Package | Recorded purpose / tradeoff |
|---|---|
| `com.sony.dtv.sonybugreportsys` | Sony bug-report integration |
| `com.google.android.tv.bugreportsender` | Google TV bug-report submission |
| `com.google.android.feedback` | Market Feedback Agent |
| `com.sony.dtv.da.service` | APK label `Remote Support`; app-activity, power, and audio status collectors; loses Sony remote support |
| `com.qterics.da.product` | Qterics Product Kit provider used by that remote-support agent |

The later five package states were read back as disabled before the owner's
October 29 restart. The owner subsequently reported normal startup and the
requested remote checks after that restart, but **the five package states were
not re-read afterward**. Functional success does not prove each disablement
persisted. Continued use since initial cleanup also does not establish that
every feature, standby path, or later change passed a long-duration test. This
report does not claim that all telemetry embedded in streaming apps or Google
services was blocked.

Two packages were already disabled before this work and were left alone:
`com.sony.dtv.smarthomesettings` and `com.google.android.inputmethod.latin`.
The original states of the initial cleanup packages, stock Home, and the later
five reporting/support packages were recorded as enabled=0 (manifest default),
with per-component overrides left intact. The separate October 29 Play Movies
change below had an original state of enabled=1, not 0. Recovery plans preserve
that distinction rather than blindly enabling every package or resetting all
of them to manifest defaults. Actual package restoration was not exercised.

## Important retained components and observations

- `com.sony.dtv.bivlinfo` was retained: its live package dump registers software
  search/download result events (`SW_SEARCH_*`, `SW_MANUAL_SEARCH_*`, and
  `SW_*_DL_*`). Its recommendation role alone was not a reason to disable it
  while preserving firmware updates.
- Chromecast, screen mirroring, voice, phone remote, Sony IP control, firmware
  updates, Play Store/Services, HDMI/CEC, audio, Bluetooth, keyboard, and
  Sony/MediaTek infrastructure were retained. Preservation is not a claim that
  every feature was individually exercised.
- Despite `com.sony.dtv.discovery` retaining user-0 enabled=3 after reboot,
  ActivityManager still showed its own process with
  `packageList={com.sony.dtv.discovery}`, `persistent=true`, and a persistent
  system process state. The APK flags include `PERSISTENT`. Successful package
  disablement did **not** establish that its process stopped running. The
  firmware startup path was not investigated or modified.
- The owner explicitly confirmed Netflix playback and later reported the
  grouped app/navigation checks as normal. HDMI signal/audio, ARC/CEC, tuner
  reception, each casting protocol, voice, updates, long standby/wake, and OLED
  maintenance were not independently verified.
- Screenshots confirmed that the replacement Home rendered and its configured
  app tiles were present. No measured performance or RAM-savings claim is made.

## October 29 owner review and remote-button follow-up

The owner reviewed the setup after continued use since October 24. The follow-up
established the following results, rather than a blanket "everything works"
claim:

- The replacement Home remains in use. The original October 24 reboot evidence
  still applies to the initial cleanup and Home handover only.
- The physical Apps button has a reproducible regression after stock Home is
  disabled. A later per-device keyboard-layout workaround passed the owner's
  physical checks and was retained; the underlying ALL_APPS route is unchanged.
- The physical Google Play button's older failure predates cleanup. A native
  Play Store fallback was applied and the owner confirmed that it now works,
  with held-volume operation still working and key filtering disabled.
- After the accepted mapping was saved, the owner performed the requested
  restart test and reported all requested startup/remote checks normal. This
  is owner-reported physical validation, not an instrumented boot-ID or
  post-reboot package/layout-state capture; details are below.
- Standby/wake, HDMI/ARC, casting, firmware updates, every remote key, and OLED
  maintenance were not newly validated. Later reporting/support package states
  were not individually re-read after the restart.

### Regression: the physical Apps button does nothing

On the recorded version 2.7 build and Sony firmware above, press Home to show
`com.gathin.tvhome/.MainActivity`, then press the physical Apps button. No app
list opens. The same failure was reproduced from Sony's network settings.
The physical key was observed as Android keycode 284 (`KEYCODE_ALL_APPS`),
scan code 580, during a temporary diagnostic experiment.

The observed launch request in both contexts was:

```text
act=android.intent.action.ALL_APPS pkg=com.android.tv.settings
```

The replacement Home was focused, but the request still targeted Sony Settings;
resolving that exact request found no activity. The disabled stock launcher had
registered `com.google.android.tvlauncher/.appsview.AppsViewActivity` for
`android.intent.action.ALL_APPS`. The installed replacement did not declare a
handler for that action.

[Android 9's `PhoneWindowManager.launchAllAppsAction()`](https://android.googlesource.com/platform/frameworks/base/+/android-9.0.0_r61/services/core/java/com/android/server/policy/PhoneWindowManager.java)
resolves HOME with `MATCH_SYSTEM_ONLY` on Leanback devices and restricts the
ALL_APPS request to the selected package. The live enabled HOME candidates were
the user-installed replacement and Sony Settings' system `FallbackHome`, which
matches the observed routing. This is an AOSP-source explanation corroborated
by this TV's resolver and launch records, not a claim that the vendor framework
was independently decompiled. Adding only a generic ALL_APPS intent filter to
the replacement would not receive a request restricted to another package.

This remains a regression in the unmodified launcher/firmware route. The
separate input-layout workaround below was accepted after physical testing;
stock Home remains disabled. The owner's earlier grouped shortcut check did
not establish that this dedicated button worked. These findings do not establish
whether a newer upstream launcher release has the same behavior.

### Rejected workaround: key filtering breaks held-volume repeat

A separate, locally built accessibility key-filter experiment restored the two
shortcuts, but the owner found that holding a volume button changed the volume
by only one step. A subsequent timed-repeat compensation attempt did not help:
the service received other keys but did not receive the physical volume events
needed to start compensation; the audio event log attributed the adjustments
to WindowManager instead.

The filter service was disabled. Settings readback and the accessibility dump
confirmed no enabled service, and the owner confirmed native held-volume repeat
returned. The experiment is **not** a working fix and is not included in this
PR; no reference-launcher code was changed. Do not treat key filtering as a
validated repair for this firmware merely because an app shortcut opens.

### Accepted workaround: an IR-device KCM overlay, without key filtering

After reviewing the [Sony X9000H Apps-layout companion source](https://github.com/Lzchyi/cleanyourtv/tree/ad1c9badcf745acbd86332c40004e53d54fc09a6/companions/sony-apps-layout)
and the [VH22 layout reports](https://github.com/Gathin23/cleanyourtv/issues/1680),
the owner approved a bounded local trial of that approach on this Android 9 TV.
The reference Bluetooth device identifiers and HID usage were **not** copied.
A fresh physical capture attributed both Apps and volume down to this TV's
`mtkinp_ir_events` device. Its original key-layout file maps scan code 580 to
`ALL_APPS`.

A separate local metadata-only provider registered this minimal key character
map (KCM), selected for that measured input device using the shell-side
InputManager interface:

```text
type OVERLAY
map key 580 CALCULATOR
```

Android's `android.intent.category.APP_CALCULATOR` launch entry was handled by
a small no-display activity that directly opened the existing local all-apps
grid, without going through Home. A resolver check found no existing handler
before installation and only the intended handler afterward. This repurposes
the application-launch category; it does not open a calculator. It may conflict
with a future calculator app or a real calculator key, so it is device-specific.

- The provider adds no accessibility service, background watcher, network, or
  storage permission. The earlier helper remains installed for its app grid,
  but its accessibility service stays disabled. No launcher code or permissions
  were changed, and stock Home was not restored.
- Only Apps scan code 580 was mapped. Volume, Home, Input, media, and other keys
  were not remapped. Original vendor key-layout and key-character-map files
  remained byte-for-byte unchanged; no root or system-file modification was used.
- The original current layout was null and its enabled-layout list was empty.
  Before owner testing, a five-second trial was allowed to restore automatically
  after ADB disconnected. An explicit reverse overlay (`580` to `ALL_APPS`) was
  applied before restoring that exact original selection and enabled list;
  API readback confirmed both were restored. Clearing a selection alone was
  not assumed to undo a merged live input map.
- In a three-minute trial, the owner confirmed that physical Apps opened the
  app grid from Home and again from YouTube, holding volume down continued to
  lower volume and stopped on release, and physical Home and Input were normal.
  The first trial expired and restored before its result could be committed;
  the identical accepted mapping was reapplied and saved, then read back.
- The short-lived setup/rollback process does not need to remain running after
  acceptance. The mapping does not require continuous ADB or a computer
  connection. The disabled-package set, HOME resolver, display values, and
  disabled accessibility state were unchanged in the final comparisons.

This is an **owner-tested local workaround**, not a reference-launcher fix or a
turnkey add-on supplied by this PR. Companion source, binaries, private device
descriptors, raw captures, and local setup/recovery scripts are not included.
The source-backed approach above is the reference, not a claim that its
Bluetooth-specific setup can be used unchanged on this IR device. The owner
later reported successful Apps and volume-up/down operation after restart, as
listed below. Standby/wake, every other remote key, alternate remotes, and
other firmware versions remain unvalidated; no post-reboot layout API readback
was made.

### Google Play button: native Store fallback, not a cleanup regression

The owner clarified that this button had only flashed a screen for a long time
before cleanup, around the retirement of the old Play Movies entry point. The
service-transition timing is owner recollection, not an independently proven
cause of the activity closing. Logs showed Sony's key handler starting
`com.google.android.videos/.tv.presenter.activity.TvLauncherActivity`, followed
by the activity finishing with `app-request`; that is not proof of a crash.

Read-only inspection of this TV's original SonySystemService APK identified
`GlobalKeyIntentService.getGooglePlayLaunchIntent()` choosing enabled
`com.google.android.videos` first, then enabled `com.android.vending` as its
fallback. With the owner's approval, only the unused old Play Movies package
was disabled:

```sh
pm disable-user --user 0 com.google.android.videos
```

The original user-0 state was enabled=1; the new state was read back as
3 (`disabled-user`). Enabled/disabled package sets changed only for that package.
HOME, display, accessibility, screensaver, and timeout checks were unchanged.
The owner then confirmed the physical Google Play button opened Play Store and
held-volume operation still worked, without enabling key filtering.

This also disables the old movie app's own entry points and related previews;
it is not a recommendation to disable an app another owner uses. The package
was not removed and its data was not cleared. Restoring this TV's recorded
original state would use `pm enable --user 0 com.google.android.videos`, not
`default-state`. The owner subsequently confirmed the Store key worked after
restart, but the package's enabled value was not re-read afterward. No Sony APK,
decompiled vendor source, diagnostic app, or raw log is published here.

### Owner-reported restart validation

After the final Apps mapping had been saved, the owner was asked to disable
USB/network debugging and use the TV's Restart command rather than a standby
power toggle. The owner reported that all requested checks were normal:

- The TV automatically entered the replacement Home after restart.
- The physical Google Play key opened Play Store.
- The physical Apps key opened the all-apps list from Home and from YouTube.
- Holding volume up or down repeated adjustment and stopped on release.
- Physical Home and Input operated normally.

These are the owner's grouped physical results from the requested post-restart
checklist. No ADB authentication, input injection, app launch, or configuration
change was performed to obtain those results. There was no post-restart boot-ID,
package-state, or selected-layout capture. The evidence supports functional
survival of the two button workarounds through the reported restart, not an
independent per-package persistence audit or a standby/wake test.

## Debugging closure and follow-up limits

The TV used its built-in Ethernet port, with no external USB accessories.
`ro.adb.secure` was 1, and the saved ADB identity was reused. On October 24, after
the owner reported switching debugging off, three TCP-only checks of the previously
reachable ADB port returned `ECONNREFUSED`, rather than timing out. No ADB
authentication or TV interaction was attempted during those closure checks.
Authorization revocation in the TV UI was not independently confirmed. That
historical closure record did not include another reboot after the later
package batch; the newer owner-reported restart is documented separately above.

The owner reopened debugging for the October 29 diagnostics and trials. After
the owner's restart report, three new TCP-only checks at 13:36 Taiwan time on
October 29 each returned `ECONNREFUSED` at the TV's last known ADB address/port,
rather than timing out. No ADB protocol or authentication was attempted. This
is new port-closure evidence, not reuse of the October 24 result. UI-level
authorization revocation was still not independently confirmed. Neither
accepted button workaround requires leaving debugging enabled.

This note deliberately omits IP addresses, device identifiers, account details,
raw dumps, private keys, screenshots, APKs, and local recovery scripts. Per the
TV-list workflow, an executable list should wait for explicit validation of the
remaining limitations, including standby/wake and the later packages' actual
post-reboot states. This PR changes documentation only; it does not claim to fix the
launcher's button handling or validate later upstream launcher versions.
