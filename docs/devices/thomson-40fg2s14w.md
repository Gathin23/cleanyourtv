# Thomson 40FG2S14W (Expressluck `nagata`, CVTE board, Android TV 14): the launcher runs, Home stays with Google TV

Two owner-reported ADB sessions on one unit: the first ([#1035](https://github.com/Gathin23/cleanyourtv/issues/1035))
installed and ran the launcher, the second ([#1350](https://github.com/Gathin23/cleanyourtv/issues/1350)) ran the
read-only boot checks below and changed nothing. The
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
- The two checks in [TROUBLESHOOTING.md](../TROUBLESHOOTING.md) that decide whether the stock launcher can be
  disabled without a boot that never finishes were run afterwards ([#1350](https://github.com/Gathin23/cleanyourtv/issues/1350),
  [below](#the-two-boot-checks-run-afterwards)). They answer the fallback half - this board has two - and not
  the Home-before-unlock half, and the boot itself has still not been tried on this board. Treat the set as
  debloat-only.

## The two boot checks, run afterwards

A later session on the same set ([#1350](https://github.com/Gathin23/cleanyourtv/issues/1350)) ran the two
read-only checks the [CVTE bullet](../TROUBLESHOOTING.md) asks for, and changed nothing on the TV: no fallback
was launched, the stock launcher was not disabled, and no handover was attempted.

`cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME` answered
five HOME activities on this board:

| HOME activity | Priority |
|---|---|
| `com.google.android.apps.tv.launcherx/.home.HomeActivity` | 2 |
| `com.google.android.tungsten.setupwraith/.RecoveryActivity` | 1 |
| `com.gathin.tvhome/.MainActivity` | 0 |
| `com.express.at.factory.factorymanager/.FallbackHome` | -999 |
| `com.android.tv.settings/.system.FallbackHome` | -1000 |

So this board is not one of the sets without a fallback: the ODM's factory manager and the platform's
TvSettings each declare one. The stock launcher also carries the highest HOME priority of the five, which is
what the `set-home-activity` attempts above ran into.

`dumpsys package com.gathin.tvhome | grep -o "[A-Z_]*DIRECT_BOOT_AWARE"` printed
`PARTIALLY_DIRECT_BOOT_AWARE`, twice. That is the answer for the package, not for the activity Home starts
in: in the launcher's manifest `HomeKeyService` is the one component marked
`android:directBootAware="true"`, and `com.gathin.tvhome/.MainActivity` is not marked, so the line does not
say whether Home can start before the first unlock.

What that session leaves open is the boot the CVTE rule is about: with the stock launcher disabled, does this
set finish booting, and does Home survive the next reboot. Neither was tried, so the set stays debloat-only.

Debloat-only is not the same as unusable. The owner reports afterwards ([#1350](https://github.com/Gathin23/cleanyourtv/issues/1350),
24 Oct 2026) that normal use of the launcher on this set, with the stock launcher left enabled, works: it opens
and runs, and Home still goes to Google TV. Neither `FallbackHome` activity was launched for it and a boot with
the stock launcher disabled has still not been tried, so that report says the debloat-only setup is day-to-day
usable, not that the handover is safe here.

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

- One unit, one firmware, two sessions: no cold boot, no reboot after the Home attempts, no screenshot,
  and no HDMI, Live TV, casting, voice or physical-remote check.
- The Home result belongs to `UKNV.260317.001` on `nagata`. A board whose stock launcher is
  `com.cvte.tv.launcher.*`, or a later build, can hand Home over differently - and an owner-report on such a set
  is worth a section here rather than a copy of this paragraph.
- There is no `examples/` folder for this model: a list needs several days of use first (CONTRIBUTING).
- ADB was switched off after the session.
