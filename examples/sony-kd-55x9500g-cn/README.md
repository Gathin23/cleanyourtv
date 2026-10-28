# Sony BRAVIA KD-55X9500G — China firmware

Tested on the retail KD-55X9500G, reported by Android as `BRAVIA 4K UR2`,
product `BRAVIA_UR2_4K_CN`, Android 10 / API 29,
firmware `QTG3.200305.006.S97 662301`.

This is a conservative CN-specific list. The existing
[UR2 example](../sony-bravia-4k-ur2/debloat.txt) describes UC/EU firmware;
the CN unit has a different stock launcher and application set.

- The seven entries in `debloat.txt` were disabled on 2026-10-21. The owner
  confirmed normal day-to-day use on 2026-10-26, with none of those entries
  needing re-enabling.
- The tested custom launcher was v2.5, set as the default Home. Reboot preserved
  the default Home and disabled-package states. Physical Home, D-pad, OK, Back,
  Settings and Input buttons, playback/audio-video checks, and long-pressing OK
  on the launcher's Settings button were confirmed working.
- The original Home is `com.dangbei.TVHomeLauncher`, kept enabled as a fallback.
  Use `STOCK_LAUNCHER=none` when building/installing to preserve that choice.
- Set `live_tv_package` to `com.sony.dtv.tvx`. Keep it and the TV input services:
  TVX handles HDMI as well as television playback.
- The list preserves firmware updates, networking, keyboards, remote/Bluetooth,
  streaming apps, voice and built-in casting packages. Built-in casting and
  voice were not validated as part of this list.

Additional apps removed later by personal preference are not part of this
shared disable list. No core Android packages are listed.

Background and installation report: [discussion #589](https://github.com/Gathin23/cleanyourtv/discussions/589).
