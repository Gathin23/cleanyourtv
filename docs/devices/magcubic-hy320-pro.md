# Magcubic HY320 Pro

Observed on one owner's unit: **Android 14 (SDK 34)**, firmware **`Projector_20260713.153921`**.
The separately maintained launcher source also recognises **`Projector_20261014.163241`**.
That allowlist entry does not establish equivalent physical testing of all controls or the initial
debugging route on the newer firmware; the observations below refer to the original build.
The similarly named HY320, HY320 Mini, HY300 and HY300 Pro reports do not establish compatibility
with this firmware. No tested package-disable list is supplied; streaming, casting, app stores,
firmware updates and unidentified projector components were kept.

## Home and projector controls

Ordinary user APK installation and a third-party HOME handler worked with the owner's customised
CleanYourTV-based launcher. Physical Home/Back, volume including held repeat, projector-control
access, HDMI picture/audio and streaming playback were owner-confirmed during development.
Normal reboot into that customised Home was also confirmed. These observations are not a physical
validation of every current upstream build or the new helper-free edition split, which awaits physical verification.

Keep stock MagcubicOS installed and enabled. Its Home component is
`com.htc.magcubicos/com.htc.magcubicos.activity.MainActivity`; it provides normal manufacturer
settings and projector-control routes. Do not disable it on the basis of an unrelated model's list.

## Debugging and separate launcher implementation

The ordinary vendor Developer mode page exposes distinct USB debug and ADB debug switches; those
are not the same as Android's paired Wireless debugging page. Before installing the custom launcher,
the owner used **Activity Launcher** (installed package `de.szalkowski.activitylauncher.oss`) to open
the normal Android Developer options page. This is a one-time installation/setup route, not a
recommendation to leave network debugging enabled. The saved baseline records this exact route:

1. In Activity Launcher, find the Android Settings package **`com.android.tv.settings`** and its
   **`com.android.tv.settings.system.development.DevelopmentActivity`** activity.
   The full component is `com.android.tv.settings/.system.development.DevelopmentActivity`.
2. Open that activity, then **Wireless debugging** and **Pair device with pairing code**.
   Accept Android's normal network/debugging consent prompts on the projector.
3. Use the displayed pairing endpoint with `adb pair <projector-ip>:<pairing-port>` and enter the
   code at the terminal prompt. Then use `adb connect <projector-ip>:<connection-port>` with the
   separate connection endpoint shown by Wireless debugging. Do not publish codes or endpoints.
4. After installation and verification, turn **Wireless debugging off**, remove the computer's
   pairing and revoke debugging authorisations. Disable any vendor ADB/USB debugging enabled for
   setup. Check the used Wireless debugging port with `tools/adb-closed.py <projector-ip> <port>`
   before a normal reboot, then check port 5555 afterward with `tools/adb-closed.py <projector-ip>`.
   Only an explicit connection refusal establishes closure; a timeout is inconclusive.
   `adb disconnect` alone does not switch off the projector's debugging service.

Activity Launcher is the activity-opening tool used on this unit, not an ADB client or a requirement
to replace Home. Installing Projectivy or Bare launcher alone did not expose the pairing page.
Do not substitute a factory/service activity if this activity is absent or inaccessible.
USB debugging is not needed for this paired Wi-Fi route; on some firmware it can affect USB host
operation. Retain authentication and close debugging access after use. No factory menu, root,
firmware flashing or system-partition modification was used.

The separately maintained [Magcubic HY320 Pro Launcher](https://github.com/KeyErrorFinn/cleanyourtv-magcubic-hy320pro-launcher)
provides custom Home with either simple stock settings or restyled pages that open stock controls
where direct operations are unavailable. These default-branch editions operate with debugging off
after setup. Their source, permissions, compatibility limits and recovery instructions are documented
in that project. Do not copy another owner's calibration or private state.
