# Hisense 43QD65QFC (Amazon model AFTOLCBC82, codename ginza)

43-inch sibling of [hisense-fire-tv-55qd6qf/](../hisense-fire-tv-55qd6qf/): the same
RS8180/3746N board family, sold in the US as the QD65QFC series. The 51 packages that were
disabled are the sister list minus `com.tubitv.ott`, and every one was verified installed on
this set with `pm list packages` first.

Verification window: same-day apply plus a reboot (`tools/check.py`: 0 drift, Home still
`com.amazon.tv.launcher/.ui.HomeActivity_vNext`), not the multi-day living-with of the sister
entry, so treat it as the sister list's equal on the same firmware rather than independently
proven. Netflix, YouTube, Prime Video, Apple TV, Spotify, casting, HDMI-CEC and the remote were
not re-tested beyond the launcher check.

## What differs on this unit

- **vizzini stayed disabled across the reboot.** The 55QD6QF note that vizzini re-enables itself
  at every boot did not reproduce here; it stayed DISABLED_USER. `check.py --fix` after a future
  reboot will say for sure.
- **Tubi is a user-installed app on US sets**, not a retailer preload, so it is absent from the
  list: disable it yourself in Settings or it stays. The other retailer preloads arrive as
  system packages here and the list handles them; their Home tiles linger after disabling until
  cleared per app from Settings > Applications > Manage all installed apps.

## ADB on this firmware (ro.adb.secure = 1)

The "Allow USB debugging?" popup only renders when a fresh client challenges **right after adbd
restarts**. A connection that sits at `unauthorized` with no popup visible is normal: start the
client, then toggle ADB debugging off and on in Developer options; the popup appears within
seconds. `adbd` serves one client, so stop other adb clients (`adb kill-server`) before
retrying. "Always allow from this computer" persists across reboots, and the ADB debugging
switch itself survived the reboot here.

Undo with `TV_HOST=<tv ip> tools/restore.sh examples/hisense-fire-tv-43qd65qfc/debloat.txt`.
The owner also runs AdGuard's Private DNS on the set, which thins the sponsored banners in the
Home feed further; the Fire TV Home rows themselves stay, as on every Fire TV.
