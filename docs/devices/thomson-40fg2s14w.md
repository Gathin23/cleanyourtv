# Thomson 40FG2S14W (Expressluck `nagata`, CVTE board, Android TV 14): the launcher runs, Home stays with Google TV

One owner-reported ADB session on one unit ([#1035](https://github.com/Gathin23/cleanyourtv/issues/1035)). The
replacement launcher installed and opened, and its five-app dock and D-pad navigation worked. What did not work
is Home: on this board Home stays with Google TV for as long as the stock launcher is enabled, which is the
state the CVTE rule asks for. This is **not a tested debloat list** - six privacy and feedback packages were
disabled and read back after a reboot, and no several days of use followed - and it is not a tested Home
profile either.

| Field | Observed value |
|---|---|
| Retail model | Thomson `40FG2S14W`, Android's model string `Thomson TV` |
| Maker / ODM | Expressluck (CVTE board), device `nagata` |
| System | Android TV 14 (SDK 34) |
| Firmware | build `UKNV.260317.001` |
| Stock Home | `com.google.android.apps.tv.launcherx` |
| CVTE components | `com.cvte.tv.at` present, so the board counts as CVTE for the [rule in PROMPT.md](../../PROMPT.md) even though its stock launcher is Google's |

## Home

- `cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity` printed `Success`, and the Home
  resolver still selected `com.google.android.apps.tv.launcherx`: pressing Home opened Google TV.
- Disabling `com.google.android.tungsten.setupwraith` on its own did not change that; the resolver still picked
  `launcherx`. (Before disabling that package on any Google TV set, read
  `dumpsys wifi | grep -o 'cname=[^ ]*'`: a network saved during first setup belongs to it and goes with it.)
- The system Home role reported `com.gathin.tvhome` while Home still opened Google TV, so the role holder is not
  the resolver. What Home does is `cmd package resolve-activity --brief -a android.intent.action.MAIN -c
  android.intent.category.HOME`, which is also what `tools/disable.sh`'s Home guard reads.
- The handover itself is not broken on this firmware: `launcher/build-and-install.sh`, which starts the new
  launcher and disables the stock one, did leave the launcher as the resolved Home. On a CVTE board with no
  tested profile that stock launcher stays enabled ([the JVC set that hung on its boot animation with ADB
  gone, #741](../TROUBLESHOOTING.md)), so `setupwraith` and the stock launcher were both re-enabled and Home
  went back to Google TV.
- Not tried, and what the next session on this board would need: the `FallbackHome` and direct-boot-aware checks
  in [TROUBLESHOOTING.md](../TROUBLESHOOTING.md) that decide whether the stock launcher can be disabled without
  a boot that never finishes, and whether Home survives a reboot afterwards. Until someone runs them on this
  exact board, treat the set as debloat-only.

## Disabled, and read back after a reboot

`pm disable-user --user 0`, read back after a reboot, nothing uninstalled:

- `tv.anoki.acr.anokiacroptin` - Anoki's automatic content recognition.
- `com.android.adservices.api`, `com.android.ondevicepersonalization.services`,
  `com.android.federatedcompute.services` - the Android 14 Privacy Sandbox ad and on-device ML services.
- `com.google.android.feedback`, `com.android.tv.feedbackconsent` - the bug-report uploader and the TV's consent
  dialog for it.

## Left enabled on purpose

The owner's streaming apps, the voice assistant, Chromecast, Miracast and the firmware updater. The six packages
above are the whole change; this note carries no list beyond them.

## What this note does not establish

- One unit, one firmware, one session: no cold boot, no second reboot after the Home attempts, no screenshot,
  and no HDMI, Live TV, casting, voice or physical-remote check.
- The Home result belongs to `UKNV.260317.001` on `nagata`. A board whose stock launcher is
  `com.cvte.tv.launcher.*`, or a later build, can hand Home over differently - and an owner-report on such a set
  is worth a section here rather than a copy of this paragraph.
- There is no `examples/` folder for this model: a list needs several days of use first (CONTRIBUTING).
- ADB was switched off after the session.
