# TCL BeyondTV (RT2851, Android TV 9): the Netflix and T buttons reach only the focused window

Measured on one unit on 2026-10-26. This is a note about how the remote's vendor buttons and the HDMI
inputs behave on this firmware, not a debloat list: the list for this firmware is
[`examples/tcl-beyondtv-rt2851/`](../../examples/tcl-beyondtv-rt2851/debloat.txt). The
[BeyondTV AP08 report](tcl-beyondtv-ap08.md) is the same board on a different build, so match the
firmware below before relying on anything here.

## Identify the firmware

| Field | Observed value |
|---|---|
| Model | TCL BeyondTV |
| Board | `RTD2851` |
| Android | Android TV 9 |
| Software | `V8-R851T02-LF1V606` |
| Panel | 1920x1080, 60 Hz |

## The Netflix (4062) and T (4020) buttons never reach an accessibility key filter

On the Google TV TCLs in the other notes, an accessibility service that filters key events (the
launcher's `InputKeyService`) receives these two buttons and can answer them through `app_keys`. On this
firmware it does not: they reach only the focused window. `logcat` shows, for each press,

```
WindowManager: Unhandled key: win=... keyCode=4062
```

followed by "No fallback." So `app_keys` entries for 4062 and 4020 do nothing from inside another app, or
from an HDMI input, however the helper is set up.

## What TCL's key service does instead

`com.tcl.keycustomfunctionservice` handles both buttons itself.

- **T (4020).** On every real press it sends the non-protected broadcast `tcl.sys.intent.action.TKey` and,
  from uid 1000, starts the action `com.tcl.partnercustomizer.tkey`, which `TKeyActivity` and
  `TKeyGalaActivity` in `com.tcl.partnercustomizer` answer. An `input keyevent 4020` injection does **not**
  trigger it: only a press on the real remote does, so a test with `tools/tv.py` proves nothing here (the
  same caution as the "An injected key is not proof" rule in [AGENTS.md](../../AGENTS.md)).
- **Netflix (4062).** It sends the broadcast `com.netflix.ninja.intent.action.NETFLIX_KEY`, guarded by the
  permission `com.netflix.ninja.permission.NETFLIX_KEY`, so no third-party app can receive it. With
  Netflix disabled the button does nothing inside apps.

## The window manager sees every key

`WindowManager` logs `interceptKeyTi keyCode=N down=true` and `down=false` for every key, whatever has
focus, injected keys included. That makes it the one place on this firmware where the two buttons are
visible to anything other than the focused window. Reading it needs the `READ_LOGS` permission, and
`pm grant <package> android.permission.READ_LOGS` over ADB is enough for it on Android 9.

## HDMI inputs

HDMI goes through `com.tcl.tvpassthrough/.TvPassThroughService`:

| Port | Input id |
|---|---|
| HDMI 1 | `com.tcl.tvpassthrough/.TvPassThroughService/HW1413744128` |
| HDMI 2 | `com.tcl.tvpassthrough/.TvPassThroughService/HW1413744384` |
| HDMI 3 | `com.tcl.tvpassthrough/.TvPassThroughService/HW1413744640` |
| HDMI 4 | `com.tcl.tvpassthrough/.TvPassThroughService/HW1413745664` |

A `VIEW` of the passthrough URI for one of these resolves to `com.tcl.tv/.TVActivity`, so `com.tcl.tv`
is load-bearing: the example list keeps it, and so should anything built on it.

`dumpsys tv_input` reported an empty HDMI port (HDMI 1 and HDMI 3 here) as state 1,
`INPUT_STATE_CONNECTED_STANDBY`, and HDMI 2 with a Chromecast on it as state 0, `INPUT_STATE_CONNECTED`. An
empty port therefore does not read as disconnected on this firmware, and a device asleep in standby reports
the same state 1 as an empty port, so the two cannot be told apart from the state alone.

## The firmware updater

`com.tcl.versionUpdateApp` is TCL's system updater. It is disabled on this unit and in the example list
(firmware stays on the installed build); re-enable it for firmware updates.

## Another build, V237: the arrows and OK stay locked until Live TV starts

Seen on a TCL 50P8M (Australia, `ro.product.model` BeyondTV, Android TV 9, software `V8-R851T02-LF1V237`,
security patch 2019-07-05) on 2026-10-26; the empty property below came back after a reboot on 2026-11-03.
The V606 unit above did not show it.

With the reference launcher as Home and the stock launcher disabled, the remote's arrows and OK did nothing
after a cold boot, while volume, Input and Settings still worked. Each D-pad or OK press injected over ADB
logged

```
WindowManager: disable all key except volume and power
```

and `getprop sys.enable_key` was empty. Starting the Live TV app sets it to `true` and the keys come back
until the next boot:

```
tools/tv.py 'am start -n com.tcl.tv/.TVActivity'
```

`tools/tv.py 'setprop sys.enable_key true'` released the lock too. Before the stock launcher was disabled
the keys worked, but which part of the stock setup started Live TV, or set the property, was not traced.

The lasting fix is `boot_to_live_tv` (`config.xml` or a theme) with `live_tv_package` set to `com.tcl.tv`:
the first Home after boot opens Live TV, which releases the lock, and Home works from then on. The cost is
that a cold boot lands on Live TV, or on the last HDMI input, which is what `com.tcl.tv` resumes; press Home
once.
