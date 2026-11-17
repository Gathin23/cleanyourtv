# Hisense VIDAA_TV / NOVA72691 (70V1F-S, Android 9): the stock launcher holds HOME and boot

Owner-observed note, measured on 2026-10-28/29. CleanYourTV's no-root flow **cannot** take HOME on this set.
This note is deliberately long: the failure modes on this firmware are non-obvious, and most of them look like
"the tool is broken" when they are actually the firmware refusing to let a third-party window become the home.

Everything below was reproduced with the TV on the bench, ADB over TCP, and a physical remote for the key
checks. Commands are copy-pasteable. Nothing here needs root.

---

## 1. Device

| Field | Observed value |
|---|---|
| Android model / device | `VIDAA_TV` / `NOVA72691` |
| Retail model | 70V1F-S (BOM 0008) |
| Android / API | 9 / 28 |
| Build fingerprint | `Hisense/VIDAA_TV/NOVA72691:9/PPR2.180905.006.A1/04.02.60.03:user/release-keys` |
| TV software version | 04.02.60.03 |
| Security patch | 2021-01-05 |
| SoC / ABI | Novatek n62020 (NT72691) / `armeabi-v7a` only (32-bit) |
| Kernel | `4.9.118+` #1, built 2024-02-20 |
| SELinux | `ro.boot.selinux=permissive` (permissive at runtime) |
| ADB | `ro.adb.secure=1`, `persist.adb.tcp.port=5555`, owner accepted the prompt |
| Stock launcher | `com.jamdeo.tv.vod/com.ju.main.launcher.LauncherActivity` (聚好看), HOME priority 50 |

---

## 2. TL;DR for the next person on this firmware

1. **HOME cannot be taken without root.** Not with `set-home-activity`, not with intent-filter priority, not
   with the vendor settings pointer, not with an accessibility guard. Keep the stock launcher and give the
   user a secondary entry (an app icon on the stock home screen, a remote-key shortcut, or a
   "signal source"-style mapping).
2. **Do not disable `com.jamdeo.tv.vod/com.ju.main.launcher.LauncherActivity`.** It does not free HOME; it
   removes the only window the boot sequence is willing to draw, and the TV never finishes booting. There is
   no no-root way back.
3. **An accessibility guard will not save you here.** It binds, but this firmware never delivers the stock
   launcher's window events to it (§3).
4. **One untested lead**: the vendor property `persist.vt.use_home_pkg` is reported working on Android 11
   Hisense sets in the aerial-tv notes. It was not tested on this Android 9 build (§5).

---

## 3. No-root: what was tried, and what actually happened

| # | Attempt | Command | Result |
|---|---|---|---|
| 1 | Read HOME candidates | `cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME` | Stock launcher `priority=50`; third-party HOME `priority=0` |
| 2 | Select a replacement home | `cmd package set-home-activity --user 0 <launcher>/.MainActivity` | Prints `Success`; **no effect**. `package-restrictions.xml` only gains `always="false"`; HOME still opens the stock launcher |
| 3 | Vendor "custom startup package" pointer | `settings put system settings_startup_source_custom_pkg_key <launcher>` (and `startup_source_is_custom` was already `1`) | **No effect** after two pre-seeded reboots; boot still resolved to the stock launcher |
| 4 | Remove the stock launcher from the candidate set | `pm disable --user 0 com.jamdeo.tv.vod/com.ju.main.launcher.LauncherActivity` | HOME key stops working **and** boot never completes (see §4) |
| 5 | Let the system resolve another built-in home | `pm enable com.android.settings` (enables `.FallbackHome`) + disable stock component | Boot resolves to `FallbackHome`, but it force-finishes itself in a loop and boot still never completes |
| 6 | Accessibility guard | third-party accessibility service watching `TYPE_WINDOW_STATE_CHANGED` | Service binds, but **no window events arrive** for the stock launcher |
| 7 | Inject HOME to test behaviour | `input keyevent 3` | **Swallowed by the firmware.** No `START` appears in logcat whether the stock component is enabled or disabled — key behaviour can only be checked with the physical remote |

### Why the no-root paths fail (mechanisms)

- **Priority clamp.** The firmware rewrites third-party HOME filters to priority 0; the stock launcher stays at
  50, so plain resolution never reaches a replacement.
- **Preferred activity is written non-sticky.** `set-home-activity` produces an `always="false"` entry, which
  does not override the priority comparison.
- **The vendor pointer is bookkeeping.** `settings_startup_source_custom_pkg_key` lives in the `system`
  namespace and did not move the boot launcher in two controlled reboots.
- **No window events to third-party services.** Same behaviour as the Vidda C1S report in aerial-tv (#545):
  the guard binds but never sees the stock launcher's `TYPE_WINDOW_STATE_CHANGED`, so it cannot bounce back.

---

## 4. Why the TV hangs when the stock launcher component is disabled

This is the single most important safety note in this file.

Normal boot emits an **implicit** HOME start:

```console
I ActivityManager: START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME]
  flg=0x10000100 cmp=com.jamdeo.tv.vod/com.ju.main.launcher.LauncherActivity} from uid 0
```

With the stock `LauncherActivity` disabled, **no HOME start is emitted at all**. No application window is
drawn, the boot animation never gets its first frame, and `sys.boot_completed` stays empty. Measured twice, on
two boots, sampling every 15 s for four minutes: every sample empty.

It is not "no home activity exists" — it is "nothing drew a frame". Proof: enabling the system
`com.android.settings/.FallbackHome` makes the boot emit

```console
START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME]
  flg=0x10000100 cmp=com.android.settings/.FallbackHome} from uid 0
```

so the boot path does resolve the HOME intent; `FallbackHome` simply has no real home behind it, force-finishes,
and is restarted forever.

Unbricking this state is not possible without root: the component has to be enabled again, and the only way to
do that from outside the UI is a privileged shell. Treat it as a one-way door and do not open it.

---

## 5. Field notes / gotchas

1. **Never disable the stock launcher component** (§4). This is the one change that breaks the boot sequence
   without corrupting anything, and it is not recoverable without root.
2. **`input keyevent 3` is swallowed** on this firmware; verify HOME with the remote, not with adb.
3. **`adbd` lives on `/system`** (`/system/bin/adbd`; `/sbin` only holds `charger`, `ueventd`, `watchdogd`).
   Anything that prevents `/system` from mounting also removes ADB, so treat `/system` as read-only territory.
4. **Expect the stock launcher to draw first on every boot.** It owns the first frame; any replacement has to
   be brought forward afterwards, and on this firmware the third-party app is not startable until roughly
   8–10 s after `boot_completed` (`am start` reports `Activity class ... does not exist` until then).
5. **The stock launcher's process is pinned by a keep-alive service.** `am force-stop com.jamdeo.tv.vod`
   does terminate it (~180–217 MB of PSS here), but the TV starts it again as part of the next boot — that is
   expected, not a failure of the cleanup.
6. **`persist.vt.use_home_pkg` (untested here).** aerial-tv's notes say Android 11 / MT9653 Hisense sets accept
   `adb shell setprop persist.vt.use_home_pkg <launcher package>` to choose the home package. It was **not
   tested** on this Android 9 build. If it exists here it is the one no-root path that could work, so it is
   worth checking before giving up:
   ```console
   adb shell getprop persist.vt.use_home_pkg
   adb shell setprop persist.vt.use_home_pkg <your launcher package>
   ```

---

## 6. Diagnostic cookbook

```console
# who is home, and at what priority
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME

# what the boot actually started
adb shell logcat -b system -d | grep 'START u0' | grep -i home

# did boot finish?
adb shell getprop sys.boot_completed

# is the stock launcher's process still around?
adb shell ps -A | grep com.jamdeo.tv.vod
adb shell dumpsys meminfo com.jamdeo.tv.vod | grep -m1 TOTAL
```

---

## 7. Not evaluated

No vendor package was evaluated for a debloat list in this session, and none is proposed: there is no evidence
here to disable `com.jamdeo.tv.vod` or to copy package lists from another Hisense board. This note omits the
unit's serial number, network address and About-page screenshots.
