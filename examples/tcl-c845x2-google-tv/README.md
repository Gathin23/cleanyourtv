# TCL 75C845X2 (UK/Europe), Google TV 14

- **TV:** TCL 75C845X2, UK/Europe build. Model string "Smart TV Pro", product `G08_4K_GB`, MediaTek `tcl9618`, Android TV OS 14 (Google TV).
- **Firmware:** `V8-0012T01-LF1V667`, fingerprint `TCL/G08_4K_GB/G08:14/UTT2.250416.001/AU04:user/release-keys`.
- **Based on:** [`tcl-c755-google-tv`](../tcl-c755-google-tv/debloat.txt), the same G08 board and package set.
- **Tested:** applied on a real 75C845X2 on 2026-10-20. Home, the launcher and its dock apps worked afterwards.

## Differences from the C755 list

- YouTube (`com.google.android.youtube.tv`) and Google Play Movies & TV (`com.google.android.videos`) are disabled, because SmartTube is used instead.
- Chromecast, AirPlay (three packages) and the game bar are kept (commented out).
- `com.tcl.hotelmenu` and `au.com.stan.and` are not on this UK build. The "Unknown package" errors for them are harmless.

## Known issue: Settings > Accounts & profiles is empty

Disabling the stock launcher (`com.google.android.apps.tv.launcherx`) empties this page, because on this
firmware launcherx serves it. The accounts are not deleted. How to switch profile anyway (enable launcherx
for a moment, then disable it and set Home again) is in [TROUBLESHOOTING](../../docs/TROUBLESHOOTING.md),
under "Accounts & profiles is empty after the stock launcher is disabled". Most account switching can also be
done inside each app (YouTube, Plex, SmartTube).

Not confirmed: `pm list users` also shows `configured_user` (10) and `new_user` (11). Whether other Google profiles run as separate Android users was not tested (`am get-current-user` while on each profile would show it).
