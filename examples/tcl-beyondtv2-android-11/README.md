# TCL BeyondTV2, Android 11 (AR05): notes from a second real unit

Notes from the owner of a second real unit of this exact profile — same model, board
(`BeyondTV2` / `BeyondTV` / `rtd285o`), same fingerprint
`TCL/BeyondTV2/BeyondTV:11/PPR1.180610.011/AR05:user/release-keys` — recorded 2026-10-26.
Traced during issue #1869.

## The remote's voice button is routed by the OEM, not by VoiceInteractionService

`getevent -t -l` shows the mic button as `KEYCODE_SEARCH` (84) on the remote's `virtual-search`
input device. The chain, every hop visible in logs: TCL brings the Home app to the front
(`TclActivityManager`), `TclWinInjector` performs a **synthetic click on the Home's own voice
button**, the Home fires `ACTION_ASSIST`, katniss gets its audio stream. An assistant start from
the background (a service, a shell `am start`, an accessibility remap) is rejected on R:

```
W katniss_interactor: Recording audio is not allowed for this invocation on R.
E AudioPolicyIntefaceImpl: getInputForAttr permission denied: recording not allowed for uid 10024
```

Design consequence: a Home that hides (or lacks) its voice button loses the remote's microphone
on this firmware.

## Reinstalling the Home package breaks the voice chain until the next reboot

Reproduced twice: after `adb uninstall` + `adb install` of the package that owns HOME (the rebuild
had a different signing key), every press fails with the denial above, although the OEM chain
visibly runs. `cmd package set-home-activity` does **not** break it — tested in both directions,
0 denials. A single reboot re-syncs it: 3/3 recordings, 0 denials.

Troubleshooting line for owners: after **installing, updating or replacing** the package that owns
Home on this firmware, reboot once before judging the voice button. Not "after changing Home".

## The microphone that captures is the remote's, not the TV's

Every successful session shows `AUDIO_DEVICE_IN_BLUETOOTH_BLE` as the input device (16 kHz mono,
`AUDIO_SOURCE_VOICE_RECOGNITION`): the microphone inside the Bluetooth remote (TCL_RC802VEU2).
Consistent with the TV's far-field hotword being off on this unit (`mic_status=0` from
`content://atv.hotwordmic/togglestate`) while the button works: they are different microphones.

## The debloat can come back after a reboot

`com.tcl.bi` and `com.tcl.tv.tclhome_passive` were disabled and verified, and were found **enabled
again** the day after a reboot (`enabled=0` == DEFAULT). The list in this profile already carries
`com.tcl.overseasappshow` as the boot enabler of its family; re-check this list after reboots.
Whether disabling `overseasappshow` stops the drift for the analytics pair is not yet verified
on this unit.

## Measurement traps on this board

- `loadavg` is useless here: it sits at ~19-31 with the CPU 92% idle, because ~12 vendor kernel
  threads live in `D` state (`hw_watchdog`, `rtk_monitor_thr`, `hdmi_detect_tsk`,
  `localDimmingDem`, ...). Judge idle by CPU (`top`) and rendered frames (`dumpsys gfxinfo`),
  never by load average.
- A cold start of the package that **owns** Home cannot be measured with `am start -W`
  (`LaunchState UNKNOWN`, `WaitTime` 7-20 ms): read the system's
  `Displayed <pkg>/<activity>: +NNNms` from logcat instead.
- Assistant TTS plays on `STREAM_ASSISTANT` (stream 20), shipped at **4/15** on this unit with no
  user-facing control, and the remote's volume keys only move `STREAM_MUSIC`. Raising stream 20
  once at startup (`setStreamVolume(20, 10, 0)`, idempotent) is the cheap fix.

## A second unit: the Hitachi CDH-LE554KSMART22 re-badge

A 55" **Hitachi CDH-LE554KSMART22** sold in Argentina reports the same fingerprint as the unit
above, `TCL/BeyondTV2/BeyondTV:11/PPR1.180610.011/AR05:user/release-keys`, the same board
(`ro.board.platform rtd285o`) and the same preloaded package set. The list in this folder applies to
it unchanged. Recorded 2026-10-27, in one session with two full reboots - not days of use.

Hardware worth knowing, since the badge suggests otherwise: 2 GB RAM with
`ro.config.low_ram=false`, and a 10 GB `/data` that was 1% full. None of the install pressure the
1 GB Hitachi MT9221 set hits (`examples/hitachi-cdh-le32smart21`) applies here - the launcher APK
installed first try, with no `pm trim-caches` and no TGuard interference.

### Disabling overseasappshow first: no drift in one session

The note above leaves open "whether disabling `overseasappshow` stops the drift for the analytics
pair is not yet verified on this unit". On this one it did. `com.tcl.overseasappshow` went in the
**first** batch, before anything else; `com.tcl.bi` and `com.tcl.tv.tclhome_passive` followed in
batches one and two. After two full reboots, `pm list packages -d` still held all 13 disabled
packages, with nothing re-enabled.

That is weaker evidence than the drift it answers: the pair above came back **the day after** a
reboot, and this check spans one session. It shows the boot-time enabler is not the only thing that
has to be ruled out, not that the drift is solved. Worth repeating on a unit that has run for a week.

### Two small differences from the first unit

- **The saved Wi-Fi network belongs to `com.android.tv.settings`** here, not to `android.uid.system`
  / `com.google.uid.shared`. Either way it is not setupwraith's, so the header's "setupwraith safe"
  holds - but it is a third owner string, so read `dumpsys wifi | grep -o 'cname=[^ ]*'` on your own
  set rather than matching one of the two already written down.
- **`tools/disable.sh` refuses `android.autoinstalls.config.tcl.device`** on this firmware, with
  "the TV's dumpsys shows neither a requested nor a granted permission block". The package is a
  partner auto-install config with no code path left after first boot, so it was left enabled. It is
  not in this list, and this is a note for anyone who adds it and wonders why the script skips it.

## A third unit: the TCL 65P615 (product `BeyondTV`, ME firmware)

A 65" **TCL 65P615** reports `ro.product.model BeyondTV` - not `BeyondTV2` - but the same build,
`TCL/BeyondTV/BeyondTV:11/PPR1.180610.011/AR05:user/release-keys`, client type `TCL-ME-RT2851-S1`,
software `V8-R851T02-LF1V715`. Its preloaded package set matches this folder, so the list applies
unchanged. This firmware is also not `examples/tcl-beyondtv-rt2851`: that unit has the same product
name but runs Android 9 (`AP06`). Recorded 2026-10-28, in one session with one full reboot - not days
of use.

- **Applied in four batches** (overseasappshow and the TCL analytics/promo group first, then
  tclhome_master/notereminder and the Google attribution pair, then voice, then mirroring, phone remote
  and backdrop), a screenshot after each. Wi-Fi and the stock Home stayed up throughout.
- **Factory state differs again:** `com.tcl.smartalexa`, `smartconnect.tv` and Netflix
  (`com.netflix.ninja`) came disabled from the factory on this unit, as did Stan, Rakuten, `com.tcl.gallery`
  and 15 `uk.co.freeview.*` apps. `pm list packages -d` held 41 packages in all after the list.
- **Launcher:** installed with `launcher/build-and-install.sh`; `com.google.android.tvlauncher` was the only
  stock Home, and Home resolved to the new launcher on the first try. The Inputs menu listed HDMI 1-3, AV
  and Live TV with no `source_panel_service`.
- **After one reboot** (with `sync` and the 15 s wait first): all 41 still disabled, nothing re-enabled,
  Home on the replacement launcher, Chromecast's `mediashell` running, Wi-Fi up. A longer run would
  still be worth having for the analytics-pair drift noted above.
