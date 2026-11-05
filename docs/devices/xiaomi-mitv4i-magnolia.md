# Xiaomi MiTV4I (`magnolia`): launcher setup and app order

Tested on a Xiaomi MiTV4I (`magnolia`), Android TV 9 / API 28, firmware `PI.3698`.
This page is the launcher-only record of that setup: no debloat was applied in the session it describes.
The list this model ships is
[`examples/xiaomi-mitv4i-magnolia/`](../../examples/xiaomi-mitv4i-magnolia/debloat.txt) - 22 packages on
the same firmware (Xiaomi and Google telemetry, PatchWall and its Sensara rows, TV Manager and the cleaner
services, preloaded video and music), with the phone remote and the firmware updater left as comments and
the before/after memory figures in the folder's README. What this page adds to it: the dock and app-grid
order set here, the inputs the set showed, and the ADB state it was left in.

## Launcher and inputs

CleanYourTV was installed from this repository and confirmed as the Home launcher.
The Inputs menu showed HDMI 1–3 and AV, and YouTube was available.

## Configured layout

The dock was configured as Live TV, YouTube, TV Manager and Media Player.

The app grid was reordered to this sequence: Disney+ Hotstar, Prime Video, Sony LIV,
AirConsole, Google Play Store, YouTube Music, Google Play Movies & TV and Sensy TV Guide.
The dock order was set in the local `config.xml`; the app-grid order was saved on the TV.
Both orders were verified on screen.

## ADB and limits

Network ADB was temporarily enabled for the setup and layout changes, then disabled.
The TV's ADB port was checked and found closed.
