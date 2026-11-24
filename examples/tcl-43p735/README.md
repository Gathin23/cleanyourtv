# TCL 43P735

Tested on one BeyondTV4 (Smart TV Pro), Android TV 11 / SDK 30, firmware
V8-R51MT05-LF1V620.021315, build RP1A.200622.001 release-keys, security patch 2024-03-05.
ADB was used over built-in Ethernet. The 50P735 list was used as a reference.

Both entries in debloat.txt remained disabled after reboot. Google TV Home and
the setup wizard remained enabled, along with the updater, input stack and
remote services. The TCL Channel button opens Waterfall through Partner
Customizer, so both packages were kept.

The tested launcher configuration used stock_home_redirect set to
com.google.android.apps.tv.launcherx, home_long_press_home enabled, and the
HomeKeyService accessibility helper. On this build the helper needed
APP_AUTO_START allowed and persisted to start after reboot. Google TV briefly
appeared before CleanYourTV took over. See the repository Troubleshooting
guide for the TCL auto-start setting.

HDMI 1, optical audio with a streaming app, Play Store access, held volume,
and the Netflix, Prime Video, Guard, Media, YouTube and TCL Channel buttons
were checked during the session. TCL Channel, Play Store and Home were
specifically rechecked after the second disable. This is a single-device
report. No lasting speed improvement was established.
