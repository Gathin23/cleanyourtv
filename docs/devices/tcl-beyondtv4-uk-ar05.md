# TCL Smart TV Pro, UK Freeview Play (BeyondTV4 / BeyondTV6, AR05), Android TV 11

From @seancondev's report in #1110: one session on one set, an 11-package debloat in four batches with a
screenshot after each, then a reboot. The packages are in
[`examples/tcl-beyondtv4-uk/debloat.txt`](../../examples/tcl-beyondtv4-uk/debloat.txt).
It records what differs on this firmware from the
[`tcl-50c725k`](../../examples/tcl-50c725k/debloat.txt) list, which is the same platform on a later build.

| Field | Observed value |
|---|---|
| `ro.product.model` / device / product | `Smart TV Pro` / `BeyondTV4` / `BeyondTV6` |
| Board | `rtd288o`, 2 GB |
| Android / API | Android TV 11 / 30 |
| Build | `RP1A.200622.001` / `AR05` (`TCL/BeyondTV6/BeyondTV4:11/RP1A.200622.001/AR05:user/release-keys`) |
| Region | UK, Freeview Play |
| Stock launcher | `com.google.android.tvlauncher` |
| Live TV / inputs | `com.tcl.tv` / `com.tcl.tvinput` |

The 15 `uk.co.freeview.*` packages and `tv.wuaki.apptv` were already disabled at the factory.

## What differs from the AR10 list

- **Media apps request `TV_INPUT_HARDWARE`.** On AR05, `com.tcl.gallery`, `com.tcl.imageplayer`,
  `com.tcl.audioplayer`, `com.tcl.videoplayer`, `com.tcl.ui_mediaCenter` and `com.tcl.t_solo` all request it,
  and the PowerShell disable script skipped them on that check. The AR10 list disables `com.tcl.gallery`; on this
  build it was left enabled.
- **`com.tcl.system.server` turns packages back on at every boot.** It holds `CHANGE_COMPONENT_ENABLED_STATE`
  and a `BootReceiver`, and re-enabled Play Movies, YouTube Music, Play Games, backdrop, Messaging, Camera and
  Print spooler after each reboot. Keep `com.tcl.system.server` enabled anyway: it is on the AR10 list's
  never-disable line. Those seven were re-enabled and restricted from running in the background instead:

  ```bash
  tools/venv/bin/python -I tools/tv.py 'cmd appops set <package> RUN_ANY_IN_BACKGROUND ignore'
  # undo: cmd appops set <package> RUN_ANY_IN_BACKGROUND allow
  ```

- **The Assistant hotword holds the microphone path.** `dumpsys audio` showed `src:HOTWORD` and
  `ECHO_REFERENCE` held permanently by `com.google.android.katniss`, at about 28% of one core, although
  `pm list features` has no `android.hardware.microphone` on this set. Broadcasting `HOTWORD_DISABLE` does nothing
  here (an Activity handles it), and neither `HotwordSilentToggleActivity --ez enable false`, `am force-stop` nor
  `cmd appops set ... RECORD_AUDIO ignore` stopped it. Revoking the permission did (28% to 0.4% of one core):

  ```bash
  tools/venv/bin/python -I tools/tv.py 'pm revoke com.google.android.katniss android.permission.RECORD_AUDIO'
  # undo: pm grant com.google.android.katniss android.permission.RECORD_AUDIO
  ```

  Voice search from the remote needs that permission, so leave it if you use the mic button.

## Launcher

Built with `launcher/build-and-install.ps1`, Home handed over after verification. Dock: Netflix, Prime Video,
YouTube, Play Store; `live_tv_package` `com.tcl.tv`; a theme with `reduced_motion: true` and
`focus_overshoot: false`.

## Checked, and not checked

After the reboot all 11 packages were still disabled, Home was the replacement launcher, Wi-Fi was up, the crash
buffer was empty, four HDMI inputs were listed, Live TV opened from the Inputs menu and the Play Store opened and
returned Home. The owner confirmed Home, Input/Source, the gear, Netflix and YouTube on the real remote, and that
holding Volume Up still ramps.

Not checked: HDMI picture and sound with a live source, and tuner reception.
