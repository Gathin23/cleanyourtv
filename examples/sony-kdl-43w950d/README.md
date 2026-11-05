# Sony BRAVIA KDL-43W950D

India-market unit, **Android TV 8.0.0 (API 26)**, build
`SVPDTV15_PA/SVP-DTV15:8.0.0/OPR2.170623.027.S16/6.827`. Android's generic model
label is "BRAVIA 2015"; match the retail model and firmware build too, not the
label alone.

Same build number (`6.827`) as [`examples/sony-xbr-55x850d`](../sony-xbr-55x850d),
and the same SVP-DTV15/MediaTek platform as the other Sony 2015/2016 `SVP-DTV15`
lists in this repo, just a different retail model and market (India).

The [debloat list](debloat.txt) disables 71 packages: everything the owner does
not use, since they install their own apps from Play Store afterwards, plus the
stock-launcher chain (deferred until the replacement Home was confirmed, per the
list's own last section).

## Confirmed on this unit

- `com.sony.dtv.b2b.hotelmode` must stay enabled: `com.android.tv.settings`
  reads its `hotelsettings` content provider while building the Settings menu.
  Disabled, Settings opens to a blank screen. Not in the list.
- `com.sony.dtv.tvx` plays Live TV and every HDMI input; keeping it enabled and
  pointing `live_tv_package` at it was correct, Settings and the Inputs menu
  both worked afterwards.
- `com.google.android.tungsten.setupwraith` owned no saved Wi-Fi network on this
  unit (`dumpsys wifi | grep cname` showed nothing); `tools/disable.sh` disabled
  it cleanly with no warning.
- The debloat list and the launcher install both survived a full reboot, with
  the Wi-Fi connection, Home (`com.gathin.tvhome`), and every disabled package
  state intact afterwards.
- D-pad navigation was verified by injected key events: Right/Left move across
  the dock with the glass highlight and ambient glow following; Up from the dock
  reaches the top bar (Inputs pill); Left/Right at the top bar's ends do not
  fall through. The Inputs pill's dropdown correctly lists HDMI 1/MHL, HDMI 2,
  HDMI 3, HDMI 4/ARC, Video 1, Video 2/Component and Live TV.
- Settings opened correctly (confirming the hotelmode finding above) both before
  and after the stock-launcher-chain packages were disabled.

## Launcher configuration

Only `config.json` in this folder was changed; no launcher Java code was
changed. Dock: Google Play Store, TV (`com.sony.dtv.tvx`), Programme Guide,
Timer — 4 columns, the owner's choice, since no streaming apps were kept
installed to dock instead.

## Not yet confirmed

This list and launcher configuration are freshly applied. The owner has not yet
lived with the set day to day; no multi-day observation window, no physical
remote-button check (only injected key events so far), and no HDMI source
playback test. This README will be updated once that happens, per the
project's "list only packages you lived with for a few days" convention.
