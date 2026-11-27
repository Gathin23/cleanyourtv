# ZTE ZXV10 B863AV3.1-M2 (Amlogic S905L3A, Android 9): launcher handover, no debloat list

Owner-observed single-session note from 2026-11-27. The box runs a rebranded third-party ATV
image; this records one unit and does not establish that other S905L3A boxes or stock ZTE
firmware behave the same way. No package was disabled or uninstalled, so there is no tested
debloat list for this set.

| Field | Observed value |
|---|---|
| Retail model | ZTE ZXV10 B863AV3.1-M2 (S905L3A-B, 2 GB RAM, 8 GB flash) |
| `ro.product.model` / `device` | `S905l3a` / `S905l3a` |
| Android / API | 9 / 28 |
| Build fingerprint | `MECOOL/KM9PRO/KM9PRO:9/PI/V1.20190329:user/release-keys` |
| Board / ABI | `S905l3a` / `armeabi-v7a` (32-bit) |
| Build date | 2019-10-09 |
| Stock launcher | `com.ikukan.launcher` (`.ui.home.activity.HomeActivity`; `.receiver.BootReceiver`) |
| Network | Built-in `eth0` Ethernet; no Wi-Fi |

## What was installed

- The reference launcher `com.gathin.tvhome` 3.6 (versionCode 15) was built locally from source
  (JDK 17, SDK platform 34, build-tools 34.0.0) and installed over ADB, then set as the preferred
  HOME.
- The stock launcher `com.ikukan.launcher` stayed enabled by choice. The Home handover uses the
  accessibility `HomeKeyService` rather than disabling the stock home:
  - `stock_home_redirect` = `com.ikukan.launcher`: when the stock home window comes up, the
    service sends the box back to tvhome; a short Home press on the stock home returns too.
  - `restore_key_services` = `true`: when Home starts again after a force-stop, it re-adds the
    helper the system dropped from the enabled list.
  - `power_on_home_always` = `true`: Home comes up about a second after the screen turns on,
    whatever was in front; a key pressed first wins.
- Dock: `org.jellyfin.androidtv`, `org.xbmc.kodi`, `org.smarttube.stable`; 4 columns. The Inputs
  pill is hidden: TvInputManager reports no HDMI/AV/tuner hardware on this box, and
  `live_tv_package` is empty.
- Granted to tvhome: `WRITE_SECURE_SETTINGS`, `ACCESS_FINE_LOCATION`; AppOps
  `AUTO_START` / `ASSOC_START` allowed; `enabled_accessibility_services` =
  `com.gathin.tvhome/.HomeKeyService`, `accessibility_enabled` = 1.

## Observed and verified (ADB session)

- MainActivity renders: clock/date, Ethernet pill, Settings pill, dock and grid. `DPAD_RIGHT`
  moves focus through the dock; the three dock apps launch and resume, and Back returns Home. An
  injected `KEYCODE_HOME` lands on tvhome.
- After one owner-consented reboot, `eth0` came back on its own; the HOME resolver and resumed
  activity were both `com.gathin.tvhome/.MainActivity`; the helper was bound; 85 packages were
  present and the disabled list was empty.
- Standby/wake: the first wake, before `power_on_home_always` was enabled, landed back on the
  stock launcher (late or missing stock-home window event; see the standby-wake entry in
  [TROUBLESHOOTING.md](../TROUBLESHOOTING.md)). After enabling `power_on_home_always` and
  reinstalling, a retested wake landed on tvhome. ADB goes offline while the box sleeps; reconnect
  after waking.
- No TvInput/HDMI/tuner input is registered, so the HDMI-misroute scenario (HDMI 2 opening
  HDMI 1) does not apply; no input switching was configured.

## Not yet verified

- Physical remote buttons (Home, Inputs, Settings, Volume, Channel, app shortcuts) were not
  pressed on the real remote. Injected ADB keyevents do not prove the firmware-level global key
  route reaches the helper; check with the physical remote before relying on them.
- One same-day session; no several-day ordinary-use follow-up and no before/after RAM or storage
  measurement. Voice, casting and Bluetooth pairing were not checked in this session.
- This unit's image is a rebranded third-party ATV build; do not copy these conclusions to other
  boards.

## Rollback

Nothing was disabled, so no restore record is needed: switch the default Home back to
`com.ikukan.launcher` from the box's Home picker or Settings → Apps, turn the `HomeKeyService`
accessibility service off, and uninstall `com.gathin.tvhome` if desired.

See also the other ZTE notes: [ZTE zBox 1](zte-zbox-1-b866v2k.md) (Android TV 14) and
[ZTE ZXV10 B866F](zte-b866f.md) (Android TV 14); both differ from this Android 9 box.
