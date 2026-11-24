# Skyworth 70A9 (Coocaa OS, Android 8) — two firmwares, two different reasons `stock_home_redirect` never arms

Notes for the China-market Skyworth 70A9. It ships Coocaa OS, so the stock home is
`com.tianci.movieplatform`, and Home and the boot flow follow `persist.service.homepage.pkg` / `.cls`
rather than Android's HOME resolution. Two units on different firmware fail the launcher's Home redirect
for different reasons, and both failures are silent. Source: [#1351](https://github.com/Gathin23/cleanyourtv/issues/1351),
which follows [#1339](https://github.com/Gathin23/cleanyourtv/issues/1339); the second firmware's full
write-up is @v2j4b68z5's
[skyworth-70a9-cleanup](https://github.com/v2j4b68z5/skyworth-70a9-cleanup).

Read alongside them [`examples/skyworth-7s60-a5`](../../examples/skyworth-7s60-a5/README.md), the same
board (`6A658B`) and build string `022.008.220`, where a homepage shim does reach Home, and
[`docs/devices/coocaa-14k-8s61.md`](coocaa-14k-8s61.md), the oldest Coocaa family member, where
`pm disable-user` is refused outright. Neither 70A9 unit has an `examples/` folder: the debloat sets
below are their owners' measurements and have not been through this repository's tests.

## The two units

`7S70_A9`:

| Field | Value |
| :--- | :--- |
| Retail model | Skyworth 70A9, China domestic, 70" panel |
| System model | `7S70_A9` |
| Board / device | `6A658B` |
| SoC | MStar `m7221`, `armeabi-v7a` only |
| Android / SDK | 8.0.0 / 26, Coocaa OS 7.0 |
| Build | `021.008.180` / `OPR5.170623.014`, dev-keys, dated 2021-08-18 |
| RAM | 1.8 GB |
| Stock home | `com.tianci.movieplatform/com.coocaa.homepage.vast.HomePageActivity` |

`7S86_A9`:

| Field | Value |
| :--- | :--- |
| Retail model | Skyworth 70A9, China domestic |
| `ro.product.model` | `7S85_A9` |
| Product / board | `full_sky848_7s86_A9` / `6A658B` |
| SoC | MStar `sky848` (`m7221` family), `armeabi-v7a` only |
| Android / SDK | 8.0.0 / 26 |
| Build | `022.008.220` |
| RAM | 1.85 GiB usable (`MemTotal: 1938832 kB`) |
| Stock home | `com.tianci.movieplatform/com.coocaa.homepage.vast.HomePageActivity` |

## Why the redirect does nothing, on either firmware

Three layers, and none of them logs anything:

- **The stock home declares no `android.intent.category.HOME`.** `HomeKeyService` looks the stock
  launcher up by that category, so the query comes back empty on Coocaa:

  ```
  $ adb shell dumpsys package com.tianci.movieplatform | grep -A4 'android.intent.action.MAIN'
    78d594a com.tianci.movieplatform/com.ccos.cooui.movieplatform.MainActivity filter a827301
      Category: "android.intent.category.HOME.CC"
      Category: "android.intent.category.HOME.CC_MAIN"
  ```

- **The activity the boot flow actually puts in front is not that one.** It is
  `com.coocaa.homepage.vast.HomePageActivity`, which declares no home category at all and is named only
  in a system property: `getprop persist.service.homepage.cls` answers it.
- **A service that found no stock home turns itself off without a word.** `stockHome` stays empty, so
  `onAccessibilityEvent` returns early, `KEYCODE_HOME` is skipped and the window-state subscription is
  never added. The only evidence is the `dumpsys accessibility` line, whose event list is empty:

  ```
  services:{Service[label=Home button, ..., capabilities=8, eventTypes=, ...]}
                                                      ^^^^^^^^^^^ empty: the redirect is off
  ```

Three commands answer which of these a set is up against, before changing anything:
`dumpsys package com.tianci.movieplatform` (the categories), `getprop persist.service.homepage.cls` (the
boot activity) and `dumpsys accessibility` (whether the service armed).

## `7S70_A9`, build `021.008.180`: Home works, with two lookups fixed

The owner worked the three silent failures out one at a time. Beyond the lookup above, the boot side
needs a change too: `probeAtBoot` opens `ProbeActivity` once per boot hoping the screen behind it comes
back and emits a window event, but here the stock home is already in front before the service binds and
it never changes windows, so nothing comes back — pressing Home on it does not help either, the
firmware keeps it there. The third failure was the Inputs list (see the provider section below).

What was run on the set: the two Home-side changes (ask for all three categories, add the activity
`persist.service.homepage.cls` names when it belongs to the stock package, and bring the launcher up
once per boot instead of relying on the probe round trip). Home pressed from Settings reached the
launcher, and two cold boots reached it with nothing pressed, with the stock launcher **not** disabled,
`persist.service.homepage.pkg` left at the firmware's own value, and no root (`ro.debuggable=0`,
`ro.secure=1`). The `CoocaaSources` half was written from the provider queries below and compiles, but
the owner did not replace the working install just to re-check a list whose contents they had already
read off the provider. The patch itself is in
[#1351](https://github.com/Gathin23/cleanyourtv/issues/1351).

Two things that cost that owner time:

- **Test the Home key from a third app.** Pressing Home with the stock home in front is a no-op, and
  pressing it with the new launcher in front is also a no-op, because that launcher is what the HOME
  intent already resolves to. Both look like "the setting did nothing". Open Settings first, then press
  Home, then read `dumpsys window | grep mCurrentFocus`.
- **`setprop persist.service.homepage.pkg` / `.cls` work at runtime here**, but the firmware resets them
  on every boot from `/system/pcfg/7S70_A9/config/general_config.xml` (`DEFAULT_HOMEPAGE`), which is
  read-only — fine for a one-off check, useless as a permanent fix. That is also why the usual
  "edit `DEFAULT_HOMEPAGE`" recipe from the Coocaa threads does not apply to this board.

Debloat on this unit: five packages, each boot-tested, stock home still renders normally after a full
reboot.

```
com.tianci.ad
com.tianci.push
com.tianci.autoinstaller
com.coocaa.guide
com.coocaa.messageprovider
```

## `7S86_A9`, build `022.008.220`: no accessibility service binds at all

Every step answered success and nothing bound:

```
$ settings get secure enabled_accessibility_services
com.coocaa.tvhome/com.gathin.tvhome.HomeKeyService
$ settings get secure accessibility_enabled
1
$ dumpsys accessibility
           services:{}]
$ dumpsys activity services com.coocaa.tvhome
ACTIVITY MANAGER SERVICES (dumpsys activity services)
  (nothing)
```

The component itself is registered (`dumpsys package com.coocaa.tvhome | grep HomeKeyService` answers
its filter), so the declaration is not the problem. Cycling `accessibility_enabled` 0 → 1, and starting
the launcher first so the package is not in the stopped state, changed nothing, on that boot or after a
reboot with the setting persisted. With no service connected, neither the key helper nor the redirect
can run at all: this is the failure above, one layer lower.

The same firmware refuses the other routes as well:

- **`persist.service.model.*` is cleared at boot here.** Both properties were written, the TV rebooted,
  and `getprop persist.service.model.pkg` answered nothing while `persist.service.homepage.pkg` had been
  rewritten to the stock home. The 7S77_G650P in [#1938](https://github.com/Gathin23/cleanyourtv/issues/1938)
  keeps that property; this build does not.
- **The boot home is hardcoded on a read-only partition.**
  `/system/pcfg/7S86_A9/config/general_config.xml` carries `DEFAULT_HOMEPAGE` for the stock home, beside
  `NEW_BOOT_FLOW` and `BOOT_AD`.
  `settings put global SKY_SYSTEM_ENV_BOOT_START_HOME_PAGE` accepts the launcher's component, is listed
  in that folder's `setting_config.xml` and survives a reboot, but the firmware ignores it, and
  `cmd package set-home-activity` prints `Success` and changes nothing across a reboot.
- **`BOOT_COMPLETED` never reaches a third-party receiver**, so the boot-watch half of a homepage shim
  cannot run: the shim's log directory was never created. Note that
  [`examples/skyworth-7s60-a5`](../../examples/skyworth-7s60-a5/README.md) reports the same board and
  build string with a shim that does reach Home, so two units on `022.008.220` disagree here — read both
  before assuming which one a set behaves like.

What does work on this unit:

- **Debloat**, 14 packages, verified across reboots. `com.tianci.ad` comes back on some boots, so
  re-disable it if it reappears:

  ```
  com.tianci.ad
  com.tianci.push
  com.coocaa.sysreport
  com.tianci.autoinstaller
  com.baidu.oases
  com.coocaa.screensaver
  com.coocaa.os.thememanager
  com.coocaa.messageprovider
  com.coocaa.teampresent
  com.coocaa.educate
  com.coocaa.lifeassistant
  com.coocaa.tvmanager
  com.skyworth.skymarket
  com.coocaa.guide
  ```

- **The boot video**: `setprop persist.sys.bootvideo.time 1`.
- **Home from outside the TV**: a machine on the LAN polls `dumpsys window | grep mCurrentFocus` and,
  while the stock home is in front, sends `am start -n com.coocaa.tvhome/com.gathin.tvhome.MainActivity`.
  The owner measured the launcher up about 25 s after power-on, and an app in use is never pulled out of,
  because the watcher only fires while the stock home is in front. It needs a machine on the network and
  is not part of this repository; the write-up above has the detail.

Launcher build notes for this set: build it as `com.coocaa.tvhome`, because Coocaa's memory manager
force-stops third-party apps shortly after they leave the screen and spares `com.coocaa.*`. Turn
`app_library` on — almost nothing here declares `LEANBACK_LAUNCHER`, so a fresh Home was a grid of one
tile and the dock fell back to the first grid apps — and set `coocaa_sources` for the Inputs menu, which
is the `com.skyworth.tv` source list described below.

## Sideloading

An ADB install on this family can stop at Coocaa's on-TV confirmation dialog (the newer
[`7T634_P3H`](skyworth-7t634-p3h.md) page describes the dialog). Letting it time out or leaving its
refusal selected answers `INSTALL_FAILED_USER_RESTRICTED`, which is a refusal, not an installation.

## The source provider

`CoocaaSources` used to query one authority, which answers nothing on these sets. The list is on the SDK
authority next to it, and a projection is mandatory there — without one it returns no cursor at all,
which is why it reads like "the provider has no data" when it has four rows:

```
$ adb shell content query --uri content://com.skyworth.tv.provider/system/source_list \
    --projection sourceName:sourceIndex:displayName
No result found.
$ adb shell content query --uri content://com.skyworth.tv.sdk.provider/system/source_list
No result found.
$ adb shell content query --uri content://com.skyworth.tv.sdk.provider/system/source_list \
    --projection sourceName:sourceIndex:displayName:signalState
Row: 0 sourceName=VOD,  sourceIndex=-1, displayName=电视,              signalState=1
Row: 1 sourceName=AV,   sourceIndex=-1, displayName=视频,              signalState=1
Row: 2 sourceName=HDMI, sourceIndex=0,  displayName=STB,               signalState=0
Row: 3 sourceName=HDMI, sourceIndex=1,  displayName=4K HDMI2 2.0/ARC,  signalState=1
```

Three details matter to a reader: the launcher asks the SDK authority when the plain one returns
nothing (added for #1351); `displayName` is live rather than a port label, since row 2 read
`4K HDMI1 2.0` before a set-top box was probed and `STB` after, so a cached name will be wrong; and
Coocaa numbers HDMI from 0 while the sources without an index of their own (`AV`, `VOD`) are `-1`, so
guessing 1/2 for HDMI 1/2 selects the wrong port. Switching was never broken, only listing:
`METHOD_GOTO_SOURCE` takes the name and index and answers `ret=true`.

## Open questions

- Whether the `7S70_A9` Home fixes hold on other `7S70_A9` builds, and whether the same two lookups are
  enough on the newer `7S86_A9` firmware, where the service never binds.
- What decides whether a Coocaa build binds an accessibility service at all — the settings stick and the
  component is registered, so the refusal is somewhere below `AccessibilityManagerService`.
- Why two units on `022.008.220` differ on the boot-watch shim.
