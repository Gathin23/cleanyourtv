# Xiaomi MiTV4I (`magnolia`): launcher setup and app order

Tested on a Xiaomi MiTV4I (`magnolia`), Android TV 9 / API 28, firmware `PI.3698`.
This is a launcher-only record; no debloat list was applied.

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
