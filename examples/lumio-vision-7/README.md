# Lumio Vision 7 (55" / 139 cm)

First Lumio list in the repo. Contributed by **Subramanya GB**.

- **Model:** Lumio Vision 7, 55" / 139 cm, India
- **Built by:** KTC (ODM), Realtek chipset
- **Device / product:** `khardi` / `KTC_LUMIO_4K_EU_23GV1`
- **OS:** Google TV 14 (Android 14, SDK 34), build `UKRA.251202.032`
- **RAM:** 2.7 GB

## Getting in

Google TV, so **Wireless debugging** (Developer options). On this unit classic `adb tcpip` port **5555**
was already open, so after the pairing/`adb connect` handshake `tools/tv.py` worked straight on 5555.

## The one thing to know: the launcher needs setupwraith disabled

The moment the stock launcher (`com.google.android.apps.tv.launcherx`) is disabled, Home falls to
`com.google.android.tungsten.setupwraith/.RecoveryActivity`, which re-asserts launcherx — so the fork
launcher will not hold Home until **setupwraith is disabled too**. You cannot disable just the Home
component (`pm disable-user … /.RecoveryActivity` returns a `SecurityException` for the shell), so the
whole package goes.

setupwraith owned one saved Wi-Fi network here, but the **active** network was system-owned
(`OwnerUid 1000`), so disabling setupwraith did **not** drop Wi-Fi or ADB. `tools/disable.sh`'s guard is
deliberately conservative: it skips setupwraith whenever setupwraith owns a saved network, used or not,
so this unit needed `FORCE_SETUPWRAITH=1`. The Apply line leaves it out, so first check which network
your unit is on and who owns it:

```sh
tools/tv.py "dumpsys wifi | grep -oE 'Currently Connected: [a-z]*|cname=[^ ]*'"
```

Each saved network prints its `Currently Connected:` flag and then its owner, and `dumpsys` lists them
more than once. A plain `grep -o 'cname=[^ ]*'` prints only the owners, so it looks the same whichever
network is in use. If the `cname=` after `Currently Connected: true` is
`com.google.android.tungsten.setupwraith`, forget and re-join that network in Settings first so it
becomes system-owned, then check again. Add `FORCE_SETUPWRAITH=1` only on Ethernet, or once the entry you
are connected to is `android.uid.system`, as it was here.

**Trade-off:** disabling setupwraith can break Chromecast (the Cast receiver `mediashell` reads device
info from it). Re-enable setupwraith (`pm enable com.google.android.tungsten.setupwraith`) to get casting
back — but then Home-stealing returns, so you would fall back to a Home-redirect instead of disabling
launcherx.

## config.xml notes

- **Live TV / tuner app:** `com.realtek.tv` → set as `live_tv_package`, and add it to `hidden_apps` so it
  is not also a grid tile (the Inputs menu already offers it).
- **Remote app buttons** (`app_keys`): the KTC LUMIO VISION remote sends **YouTube 190, Netflix 191,
  Prime Video 195, TL;DR 200**. These went to launcherx, so after the swap they need remapping, e.g.
  `190:org.smarttube.stable`. Build with `INPUT_KEY_SERVICE=1` so InputKeyService catches them (and the
  Source button opens the Inputs menu).
- HDMI/AV passthrough and the ATSC/ATV/ISDB/DTMB tuner inputs are served by the `com.realtek.*` packages;
  none are in the list.

## Other notes

- Netflix (`com.netflix.ninja`) shipped **already disabled** on this unit.
- Factory package `com.kgzn.tvfactory` is present — left alone, never opened.
- Disabling `com.google.android.katniss` (voice assistant) freed ~100 MB of RAM; it is optional in the list.
