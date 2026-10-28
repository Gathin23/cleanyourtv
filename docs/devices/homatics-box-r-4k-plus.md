# Homatics Box R 4K Plus: encrypted ADB and first-session checks

Observed on one Box R 4K Plus, reported by Android as SEI Robotics, product `SEI804HM`,
device `YYJ`, Android TV 14 / SDK 34, build `UKG3.250803.001.8963`. These are observations
from one setup session, not a several-day tested debloat list. The related
[Nokia 8010 profile](../../examples/nokia-streaming-box-8010/debloat.txt) uses the same
YYJ platform, but its package choices are not a substitute for checking this box.

## Connection

The working connection used the box's **Wireless debugging** screen and Google's ADB
client. The pairing dialog's port is separate from the main screen's connection port.
Pair once, connect to the main screen's current endpoint, and use the optional
[`TV_ADB_BINARY` backend](../../tools/native-adb.md). Pairing persisted over a cold boot;
the connection port changed, so it had to be read again. The Python client's plain
transport cannot speak TLS, and this setup did not need `adb tcpip 5555`.

The computer used Ethernet and the box used Wi-Fi on a different routed subnet.
Explicit IP/port connections worked once the network permitted the TCP traffic.
Multicast ADB discovery remained empty. Successful ping alone did not establish that
the pairing or connection port could be reached. The `service.adb.tcp.port` property
also reported 5555; a property is not proof that a service is listening there.

## Home and package changes

The original Home was `com.google.android.tvlauncher/.MainActivity`. Before installation,
the only other HOME claimant was Settings' `FallbackHome`; setupwraith did not claim
HOME and was left enabled. The standard installer started CleanYourTV before handing
over Home and disabling the stock launcher. A five-app dock was applied with the theme
tool. Screenshot capture needed time for the first new frame to settle after restart.

Twelve selected packages, including the stock launcher, accepted `pm disable-user`
through the existing safety checks. They were still disabled after a cold boot, Home
still resolved to `com.gathin.tvhome/.MainActivity`, and the theme persisted. The owner
confirmed physical Home/D-pad/app buttons, Kodi and streaming playback. All streaming
apps and the remote, audio, HDMI, input and firmware-update services checked stayed
enabled. The originally disabled `com.nes.seilivetv` was left alone.

Vendor services were kept: `com.nes.blerc`, `com.nes.tvglobalkeyhandler`,
`com.nes.remoteota.*`, `com.nes.coreservice`, `com.nes.skywayclient`,
`com.nes.sound.daemon`, `com.sei.hdmi.services`, the Amlogic `com.droidlogic.*`
packages and `org.dtvkit.inputsource`. Optional voice and casting were off by owner
choice. No factory/test activity was launched and no system package was uninstalled.

The full original package/settings backup and pre-change recovery record were saved.
The restore plan restores the original Home; live restoration was not exercised.
The applied list remains local until it has been used normally for several days.

## Security and closing the session

This unit reported a locked bootloader, green Verified Boot, SELinux Enforcing,
`ro.adb.secure=1`, `ro.debuggable=0`, and a production user build. System and vendor
security patch levels were both `2026-04-05`. These checks are not a vulnerability
audit, and cleanup does not update the vendor firmware. Update services were retained.

The installed launcher declared no Internet permission. Its location permission was
revoked and no accessibility key helper was enabled. At the end, the owner forgot the
paired computer, switched Wireless debugging and USB debugging off, and revoked USB
debugging authorizations. The noted connection port and 5555 both refused connections
while the box still answered ping. After another restart, the owner confirmed Home
worked and the debugging toggles stayed off.
