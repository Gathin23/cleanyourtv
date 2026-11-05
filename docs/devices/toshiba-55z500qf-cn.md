# Toshiba 55Z500QF (China, Hisense firmware): device notes

This is an owner's device report, not a tested debloat list. The packages
below were removed with `pm uninstall --user 0` in September 2026, not
disabled with `pm disable-user`, so they are recorded as history and are
**not** offered as a list for `tools/disable.sh`. Do not repeat the
uninstalls: this repo only disables, and a package disabled with
`pm disable-user` has not been tested on this firmware for any of them. The
reference launcher was not installed on this set.

Sources: the owner's operation record kept since 2026-09-01 (with a
diagnostic baseline saved before any change), and a transcript of a read-only
ADB check on 2026-10-22 (package states, properties, Home resolution; nothing
was changed). The October output survives only as a transcript of the
session: no raw output file and no per-command exit status were kept.
Statements resting on the owner's record say so.

## Observed firmware

| Field | Observed value |
|---|---|
| Retail model | Toshiba `55Z500QF` (China) |
| Android model / device | `Toshiba_TV` / `T963D4` |
| Android / API | `11` / `30` |
| Fingerprint | `Toshiba/Toshiba_TV/T963D4:11/RD2A.211001.002/07.02.70.202503211952:user/release-keys` |
| Platform | `ro.board.platform=t5m`, `ro.hardware=amlogic` |
| Application ABIs | `armeabi-v7a,armeabi` (no 64-bit) |
| RAM | 2.9 GB (`MemTotal: 2978936 kB`) |
| UI | 1920×1080 at 320 dpi on a 3840×2160 panel |
| Google services | none (no GMS, no Play Store) |

Toshiba's TV business in China is run by Hisense, and this is Hisense
firmware: `com.hisense.*` and `com.jamdeo.*` packages throughout, the stock
launcher is JuUI `com.jamdeo.tv.vod`, and the boot hook logs as
`HisenseStartManagerUtil`. It shares that namespace with
[`vidda-mt9653`](../../examples/vidda-mt9653/debloat.txt), but the SoC
(Amlogic `t5m` against MediaTek) and the build (`RD2A.211001.002`,
`07.02.70` against `RP1A.200720.011`, `02.02.70.01`) differ, so that list's
choices are not established for this set.

## Getting ADB

The settings menu has no Developer options entry. `com.android.settings` is
disabled both in the owner's pre-change baseline and in the October
transcript; when and why it was disabled is not known.

The route is to start the Developer options screen directly. These are the
targets, in the order the owner's helper tried them:

1. the intent action `android.settings.APPLICATION_DEVELOPMENT_SETTINGS`
2. the activity `com.android.tv.settings/.system.development.DevelopmentActivity`
3. the activity `com.android.settings/.Settings$DevelopmentSettingsActivity`

Any app that can start an activity or an intent action, such as a public
activity launcher installed from a USB stick, should reach the same screens.
On this set only the owner's own helper was used: a small, locally built APK
(not published) installed from a USB stick, after which debugging was
turned on from the screen it opened. Which of the three targets answered was
not recorded.

Network ADB answers on TCP 5555. The transcribed output reports
`ro.adb.secure=1`, and on 2026-10-22 a computer with a new adb key stayed
`unauthorized` until the "Allow USB debugging?" prompt was accepted on the
TV.

## Home

- The stock Home `com.jamdeo.tv.vod/com.ju.main.launcher.LauncherActivity`
  declares `priority=50`. `cmd package set-home-activity` writes a preferred
  record (visible in `dumpsys package preferred-xml`), but resolution still
  picks the stock Home (owner's record). Disabling the whole package
  (`pm disable-user --user 0 com.jamdeo.tv.vod`) handed Home over; the
  owner's record has it staying disabled across reboots, and the
  transcribed output still lists it as disabled.
- A third-party launcher (Couchy, `com.conreo.couchytv`) installed over
  `adb install` and owns Home with the stock one disabled; per the owner's
  record the Home key and leaving an app both return to it. The transcribed
  output resolves Home to it. The reference launcher was not tried.
- `com.meetingroom` (Hisense's commercial home: HDMI picker and casting) is
  the first screen after boot in the owner's record, and one press of Home
  leaves it. The owner disabled it once and uninstalled it for user 0 once;
  each time it was back after the next reboot. Neither method held, and no
  persistent method has been found. Why it takes the first screen was not
  established. The `vidda-mt9653` list disables this package; on this set,
  that did not survive a reboot.
- Settings stays reachable through
  `com.android.tv.settings/com.hisense.systemsettings.MainActivity`.

## Boot source

Per the owner's record, a vendor boot hook (`HisenseStartManagerUtil` in the
boot log) brought Live TV (`com.jamdeo.tv.livetv`) to the front at boot.
Setting 设置 → 通用 → 开关机设置 → 开机信号源 from 上次信号源 (last source) to
主页 (home) stopped that; the record notes the logged `start up source`
value changing from `2562` to `2304`. Writing `persist.str.use_boot_app=1`
with the system settings `startupUrl` / `strStartupUrl` had no effect and
was rolled back.

## Removed packages (history, not a list)

Removed for user 0 with `pm uninstall --user 0`. The owner's record has all
of them still removed after a reboot with no crashes. The 2026-10-22
transcribed output reports all 23 as installed on the system but not for
user 0, at 7 days of uptime; that is one point in time. The owner has not
seen any come back. Each can be restored with
`cmd package install-existing --user 0 <package>`.

| Package | Group | What was given up on this set |
|---|---|---|
| `com.hisense.hitv.das` | telemetry | nothing observed |
| `com.hisense.platformreport` | telemetry | nothing observed |
| `com.hisense.logupload` | telemetry | nothing observed |
| `com.hisense.service.ad` | ads | nothing observed |
| `com.hisense.service.message` | vendor push | nothing observed |
| `com.hisense.demonstrate` | retail demo | nothing observed |
| `com.hisense.hotelmenu` | hotel mode | nothing observed |
| `com.hisense.electronictutorial` | e-manual | the e-manual |
| `com.aispeech.dui.dds.tvassistant` | voice assistant | voice assistant; how much of voice still works was not tested |
| `com.hisense.msvoicetome` | voice assistant | as above |
| `com.hisense.tv.aivirtualassistant` | voice assistant | as above |
| `com.hispeech.ai.area` | voice assistant | as above |
| `com.intelligent.speech` | voice assistant | as above |
| `com.keylab.speech.core.vidaa` | voice core | as above; the `vidda-mt9653` notes keep it enabled |
| `com.keylab.speech.view.vidaa` | voice UI | as above |
| `com.speech.enchatroom` | voice assistant | as above |
| `com.vt.toshibaassistant` | voice assistant | as above |
| `com.vt.mic.service` | microphone | remote microphone presumed gone; not tested |
| `com.jamdeo.updateservice` | OTA | firmware updates through this service; whether the USB upgrade path still works was not tested |
| `com.kookong.tvplus` | listed among the casting stacks in `vidda-mt9653` | casting impact not tested |
| `com.hisense.playerupdate` | not examined | nothing observed |
| `com.hisense.singlelisten` | not examined | nothing observed |
| `com.hisense.smartimages` | not examined | nothing observed |

`com.hisense.business.ad` (the ad and shopping SDK host the Vidda list
disables) is a **user app** on this set, with no copy under `/system`. It was
removed outright, so `install-existing` cannot bring it back; only
reinstalling the APK would. Check `pm list packages -3` before removing
anything that looks preinstalled: a few store-installed apps on this set
look like vendor preloads.

## Other firmware behaviour

- Per the owner's record, `settings get global favorite_black_list` holds a
  60-entry list of app packages the firmware treats as blocked (mostly IPTV
  apps).
- Live TV, HDMI and the source panel were left alone; no package in the
  `vidda-mt9653` "Do NOT disable" set other than those listed above was
  touched.

## Validation scope

- Checked: Home handover and the Home key with a third-party launcher,
  Settings reachable, reboots with the removals in place (owner's record);
  package states and Home resolution on 2026-10-22 (read-only, transcript
  only).
- Not checked: the reference launcher, `pm disable-user` on the 23 packages
  above, voice and microphone, casting, OTA, CEC, standby/wake behaviour and
  restoring the stock state end to end.

This report omits network addresses, serials, account details, installed-app
preferences and raw dumps.
