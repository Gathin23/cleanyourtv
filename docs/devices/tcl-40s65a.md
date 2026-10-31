# TCL 40S65A (Android TV 9): a compatibility note, no list yet

One owner's report (#1862), tested with CleanYourTV v3.0.0. Android reports model `40S65A`,
Android TV 9, build `PPR2.180905.006.A1 release-keys`. The report is a compatibility write-up
rather than a list: it does not name the packages that were disabled on this set, so there is no
`examples/` folder to file from it yet, and the issue stays open for the reporter's list.

## What worked

The launcher installed, launched and took Home, and Home stayed with it: Netflix and YouTube play,
and the set felt more responsive afterwards. The reporter made no further package changes after
the first pass.

## The BACK quirk

After a while inside an app, pressing BACK sometimes lands on HDMI 2 - which shows "No signal",
an onn. streaming stick in standby being plugged there - instead of the launcher. Exiting an app
right after opening it does not show the behaviour. HOME returns to the launcher immediately and
reliably, which is why the reporter left everything as it is and is monitoring it. The report does
not diagnose the cause; the handover itself is not what is failing.

## Closest tested lists

The report does not name this set's board. The tested TCL lists on the same Android version are
[examples/tcl-32s334](../../examples/tcl-32s334/) (BeyondTV5, North America, Realtek RTD2851),
[examples/tcl-43ep660](../../examples/tcl-43ep660/) (Europe, BeyondTV, Realtek,
`V8-R851T02-LF1V378`) and [examples/tcl-55c715](../../examples/tcl-55c715/) (Europe, BeyondTV2,
Realtek RTD2851, `V8-R851T02-LF1V606`). Match the build string before applying any of them; do not
apply one because the brand matches.

## From here

Once the reporter sends the packages that were disabled and has lived with them for a few days,
this becomes an `examples/` folder of its own, the way
[docs/workflows/tv-list.md](../workflows/tv-list.md) describes.
