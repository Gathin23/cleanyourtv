# Changhong 58D4P / ZLM97GiT (China, Android 9): Home handover on a 1 GB Mstar set

This is an owner-observed launcher note, **not a tested debloat list** and not an
`examples/` folder. The set is a China-market Changhong running CHiQ OS, an AOSP
build with no GMS. None of the Changhong package lists already in this repository
apply: they cover overseas ODM boards on AI PONT, while this firmware is a
different stack with its own vendor packages. Every claim below was observed on
this one unit; whether the same build ships on other 58D4P batches is not
established.

| Field | Observed value |
|---|---|
| Android model / device | `ChangHong Android TV` / `ChangHong` |
| Android / API | `9` / `28` |
| Build fingerprint | `changhong/changhong_MT9632_cn/changhong_MT9632_cn:9/PPR2.180905.006.A1/V2.00203:user/dev-keys` |
| Board | `ro.board.platform=m7332`, kernel `4.9.118+` (Mstar MT9632) |
| TV software version | `ZLM97GiT-V2.00203-EMMC`, built 2025-09-15 15:52 |
| Panel parameter | `CHT-SU-C580U19-E1-E` (factory menu, index 1) |
| Application ABIs | `armeabi-v7a,armeabi` (32-bit only, no 64-bit) |
| RAM | `MemTotal: 911504 kB` (about 890 MB) |
| Display | 1920×1080 at 240 dpi reported to Android; HDMI out locked to `2160p30hz` by u-boot |
| Network ADB | **closed on this unit.** Port 5555 answers an ADB `AUTH` token but no key is ever accepted: the developer-options entry is removed from this build and the factory menu has no ADB switch |

## Home handover

The launcher was installed and made Home with `cmd package set-home-activity`,
then the stock launcher was disabled. The stock Home on this set is
`com.changhong.system.launcher.v2`; leaving it enabled is what reclaims Home, and
it does so **without asking**: after one reboot it had resolved itself back as the
preferred Home while the third-party launcher stayed installed and enabled. That
is the one thing worth copying from this note — disable the stock launcher before
the first reboot, then confirm the result rather than trusting the command's
silence.

The same reboot boundary showed a second behaviour worth knowing about. On this
firmware, `cmd package set-home-activity` issued while the TV is half-way into
standby can be lost without an error message, so a command that printed nothing
proves nothing. The check that did hold up was reading the resolved Home back
over the shell after the reboot:

```text
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell dumpsys activity activities | grep mResumedActivity
```

Three candidates came back with everything enabled: the third-party launcher,
`com.changhong.system.launcher.v2`, and
`com.android.tv.settings/.system.FallbackHome` — the framework's own last resort,
not a vendor app. With the vendor launcher disabled, only the replacement
resolves, and the physical remote's Home key kept landing on it across reboots.

## The launcher gets killed, and it is not a leak

This set reports an idle Home being killed and cold-started whenever a video app
had been open: returning to Home showed the vendor's own "Home is starting" splash.
The kernel's low-memory killer was not the cause. `MemFree` at the moment of the
kill was 42–69 MB, well above the 24 MB floor in the kernel's own
`/sys/module/lowmemorykiller/parameters/minfree`, and raising the kernel's
`adj` thresholds changed nothing. The kill came from the vendor's userspace
`lmkd`, whose thresholds live in read-only `ro.vendor.mstar.lmkd.*` properties
and are about 61 MB free, not 24 MB.

A third-party Home is a background process between app launches, so on a 890 MB
set it sits right inside that band. Protecting the Home process from outside the
launcher fixed it; the launcher itself needs no change, and nothing here suggests
this is a CleanYourTV bug, since the stock launcher was killed the same way.
Anyone running a third-party Home on an Mstar CHiQ OS set with 1 GB of RAM should
expect this and should not read it as the launcher misbehaving.

## Boot to the TV's input, with `live_tv_action`

`boot_to_live_tv` needs `live_tv_package`, and its launch intent prefers that app's
leanback entry point. This set's input application is
`com.changhong.tvap.atv.activity`, and it declares no `LEANBACK_LAUNCHER` entry, so
the leanback route finds nothing:

```text
$ adb shell cmd package resolve-activity -a android.intent.action.MAIN \
    -c android.intent.category.LEANBACK_LAUNCHER com.changhong.tvap.atv.activity
No activity found
```

Its plain `MAIN` activity does resolve, so the configuration that works is to name
that action in `live_tv_action` — no other key is involved:

```json
{"boot_to_live_tv": true,
 "live_tv_package": "com.changhong.tvap.atv.activity",
 "live_tv_action": "android.intent.action.MAIN"}
```

```text
$ adb shell cmd package resolve-activity -a android.intent.action.MAIN com.changhong.tvap.atv.activity
ActivityInfo:
  name=com.changhong.tvap.atv.activity.AtvMainActivity
  enabled=true exported=true
```

The app then opens the last used input by itself, so the first Home after a cold
boot lands on the input picture. This is written down because "the set's app has no
action of its own" invites the conclusion that `boot_to_live_tv` cannot work on it
at all: `ACTION_MAIN` is a usable action here, and naming the activity component
was not needed.

`com.changhong.source/.SourceActivity`, whose name suggests the input screen, is a
debug stub that draws a cyan test pattern and the text "Hello World,
SourceActivity!". It is not the input application.

One detail about this model's power state matters for testing it: the remote's
power key performs a real shutdown here, not standby
(`boot reason=shutdown,userrequested`), so every switch-on counts as a cold boot
and the once-per-boot behaviour is easy to observe.

Note that `com.changhong.source/.SourceActivity`, which sounds like the input
screen, is a debug stub that draws a cyan test pattern and the text
"Hello World, SourceActivity!". It is not the input application.

## Two things that are not launcher problems

**Power-off is slow and irregular.** From the remote's power key to a dark screen
takes between 10 seconds and over a minute, varying between attempts on the same
day. A display-state poll taken every 2 seconds for 16 seconds after the key
showed Android's `PowerManager` still fully `ON`, so the delay is below the
framework, in the vendor's power service or the panel's power-down sequence.
Controlled comparisons — launcher in the foreground or another app in the
foreground, the process-protection helper running or killed, vendor services
re-enabled — did not produce a stable correlation, so this note does not claim
one. Pressing the key once and waiting is what the owner settled on; repeated
presses appear to restart the timer rather than help.

**HDMI signal auto-wake is on by default, but only from the other end.** A source
device switching on did not wake the TV in three separate sessions, including one
with the vendor's own launcher back in place, which briefly suggested a firmware
defect. The cause was the source device: its HDMI-CEC control was switched off, so
no wake command was ever sent. With CEC enabled on the source, the TV wakes with
the input on screen and needs nothing from the launcher. Worth checking before
concluding a set cannot do it.

## What this note does not establish

- **No debloat list.** The vendor packages that were removed here were removed
  with `pm uninstall --user 0`, not disabled and lived with as the
  [TV list workflow](../../docs/workflows/tv-list.md) asks, so they are not
  offered as a list. A `pm disable-user` pass on this firmware behaved differently
  from other Android 9 sets in at least one case, and that was not enough evidence
  to publish a list that others would apply blind.
- **Whether the launcher's input panel, voice, casting or HDMI passthrough work
  beyond the paths described above.** The input app, the tuner and the source menu
  were left in place; the vendor's own input switching was used.
- **Anything about other ZLM97GiT batches.** 58D4P ships with at least five panel
  variants sharing this board, and the factory menu's panel parameter is the only
  reliable way to tell them apart.

Serial number, MAC address, LAN address and panel screenshots are omitted on
purpose.
