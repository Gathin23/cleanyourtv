# Hisense 40E2F (VIDAA_TV / MTK9255), China, Android 9

China-market Vidaa/WASU set, Android 9 (SDK 28), build `PPR2.180905.006.A1` / `02.02.60.01`, no Google
services. The [debloat list](debloat.txt) disables 23 packages: Hisense ads and telemetry, the whole voice
stack, the hotel menu, a demo app and OTA.

What stays enabled, and why:

- `com.jamdeo.tv.vod`, the stock VIDAA Home. On this board it also answers the input-source handover, so it
  stays enabled even with a replacement launcher set as Home. At a cold boot the firmware starts it itself
  from `uid 0`, a few seconds before the replacement Home comes forward; the remote's power button is
  standby and shows no such flash. See the note below.
- `com.jamdeo.tv.livetv` and `com.jamdeo.tv.service`. `dumpsys tv_input` lists no inputs at all on this
  build, so the Live TV app's own source panel is the way to HDMI 1 and 2.
- `com.hisense.tv.tiletransition`. Disabling it stops HDMI 1/HDMI 2 switching: `AppTransitionManager`
  binds to its `TileAnimationService`. Tested on this unit, and re-enabled.
- Casting, all of it (`com.multiscreen.airplay`, `com.hisense.wifidisplay`,
  `com.hisense.multiscreen.screenhelper`, `com.ms.weshare`). Casting needs one setting on the TV itself;
  the note below says which and why an app cannot set it.

The HDMI/input, casting and boot-animation observations for this build are in
[docs/devices/hisense-40e2f.md](../../docs/devices/hisense-40e2f.md).

Applied on 2026-11-28 in small batches with a screenshot after each, reboot-checked four times; the
owner's HDMI, Live TV, casting and remote checks passed afterwards. It has not been lived with for a week
yet.
