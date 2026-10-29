# TCL 75T7 (G08, Google TV, Android 14), US firmware

TCL 75" T7 Series 4K 144Hz QLED (`75T7`), device `G08`, model "Smart TV Pro", product `G08_4K_US`, firmware
`UTT2.250416.001 AU16`, MediaTek `tcl9618` / `mt5896`, about 2.4 GB RAM. The same G08 board as the C755, QM6K
and 85RM7L lists, on the US build.

| File | What it is |
|---|---|
| `debloat.txt` | The 37 packages that were disabled, grouped and commented |

**How much testing is behind it:** one set, 20-21 October 2026. The first 35 packages were disabled and survived
a reboot; the stock launcher and the setup wizard were disabled afterwards for a custom launcher and have not been
through a reboot yet. It was done by hand with `pm disable-user`, not with `tools/disable.sh`, so the Apply and
Undo lines in the list header are the repo's standard ones, not something run on this set.

## What is different from the other G08 lists

- **US streaming preloads** (Disney+, Hulu, Tubi, Max, Prime Video, YouTube TV, Google TV, Play Games) are on
  the set and are off here by choice. Comment them out if you use them.
- **`com.tcl.dashboard`, `com.tcl.hearaid` and `com.tcl.t_solo`** are disabled here; the C755 list keeps them.
  No problem was noticed in light use (Home, YouTube, Netflix and other apps opening), but HDMI inputs, Live TV,
  voice, casting, TCL's wireless speakers and the hearing helper were not specifically retested afterwards.
  `com.tcl.channelplus` (the TCL Live FAST input) was disabled, so check that Live TV still opens for you.
- **Left alone:** `com.tcl.ttvs`, `com.tcl.partnercustomizer`, `com.tcl.interactive`, `com.tcl.smartlink.core`.
  The C755 list disables some of them; they were not tried on this set.

## Notes for this set

- The saved Wi-Fi network belongs to `com.google.android.tungsten.setupwraith` (`dumpsys wifi | grep cname`).
  Forget the network and join it again in Settings before disabling the wizard, or it disappears with it and ADB
  goes with it.
- Disabling just the wizard's `RecoveryActivity` is refused: `Shell cannot change component state`. With
  launcherx off and the wizard still on, Home falls to `setupwraith/.RecoveryActivity`.
- Wireless debugging switches itself off on every reboot and comes back on a new port: note the port from
  Developer options each time.
- The setup wizard, launcherx and the Ambient screensaver (`dreamx`, left enabled here) are the ones to look at
  first if Home or the screensaver misbehaves after a system update.
