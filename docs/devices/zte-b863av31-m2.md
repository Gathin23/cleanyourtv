# ZTE ZXV10 B863AV3.1-M2 (Amlogic S905L3A, Android 9): launcher handover, no debloat list

Owner-observed notes from 2026-11-27, re-checked over ADB on 2026-11-28. The box runs a
rebranded third-party ATV image; this records one unit and does not establish that other
S905L3A boxes or stock ZTE firmware behave the same way. No package was disabled or uninstalled,
so there is no tested debloat list for this set.

| Field | Observed value |
|---|---|
| Retail model | ZTE ZXV10 B863AV3.1-M2, China Mobile-branded carrier box (S905L3A-B, 2 GB RAM, 8 GB flash, 5621DS Wi-Fi/BT module) |
| `ro.product.model` / `device` | `S905l3a` / `S905l3a` |
| SoC / CPU | Amlogic S905L3A-B; 4× ARM Cortex-A53 (`cpu part 0xd03`), ARMv7 32-bit, max 1.8 GHz, `interactive` governor |
| Memory / storage | 2 GB RAM (`MemTotal` 2055160 kB); 8 GB flash, `/data` 3.3 G |
| Android / API | 9 / 28 |
| Build fingerprint | `MECOOL/KM9PRO/KM9PRO:9/PI/V1.20190329:user/release-keys` |
| `ro.build.display.id` | `HarmonyOS` (rebrand label only; the system is still Android 9) |
| Board / ABI | `S905l3a` / `armeabi-v7a` (32-bit) |
| Build date | 2019-10-09 |
| Stock launcher | `com.ikukan.launcher` 3.5.00.5 (`.ui.home.activity.HomeActivity`; `.receiver.BootReceiver`) |
| Network | Built-in `eth0` Ethernet only on this image. The 5621DS Wi-Fi is removed (`persist.vendor.wifi_remove=true`); the Bluetooth stack is present but pairing was not tested. ADB over LAN on port 5555, shell root |

## How this image got here (owner history of this unit)

- The stock unit shipped as a China Mobile carrier box with the ZTE stock firmware. It was
  reflashed over USB with the Amlogic USB Burning Tool using a third-party ATV image; the board
  entered burning mode through the contact point on the back of the board.
- The write used erase-flash (normal erase) with bootloader verification; reset-after-success
  and key overwrite were left off.
- The first write booted into a looping boot animation and never reached Home. A second write
  with erase flash enabled reached the ATV home.
- The resulting image carries the MECOOL KM9PRO (S905X2 g12a) fingerprint rather than a native
  S905L3A build. Its leftover X2 media nodes are why the VP9 OMX shell listed below fails on this
  L3A silicon. Local-only details (image file, machine paths, MAC, serial) are deliberately
  left out of this note.

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
- Owner-installed media apps (all enabled; their tiles come from the app library):
  - `org.jellyfin.androidtv` 0.20.1 (versionCode 200199) — Jellyfin TV client; home rows and the
    movie wall load from a LAN Jellyfin server.
  - `org.xbmc.kodi` 21.3 (versionCode 2103000, armeabi-v7a)
  - `org.smarttube.stable` 32.80 (versionCode 2470)
  - `ak.helper` 4.0 (TVBox)
  - `com.jerry.live.pro` 2.6.15 (IP live TV)
- Google Play stack was already present with the third-party ATV image (not installed during this
  session): `com.android.vending` 35.2.19-23 (versionCode 83521938; the TV `TvMainActivity`
  resolves), `com.google.android.gms` 20.50.16, and `com.google.android.gsf`; all enabled.
- Official ATV YouTube `com.google.android.youtube.tv` 7.12.300 (versionCode 712300320,
  minSdk 24, armeabi-v7a) was sideloaded over ADB on 2026-11-28. The APK came from a third-party
  mirror; its v2/v3 signer cert SHA-1 `61226bdb57cc32c8a2a9ef71f7bc9548e95dcc0b` matches the
  official Google YouTube signer. On first launch any video or home preview selected the dead
  VP9 OMX shell: the Starboard player hit an unrecoverable MediaCodec error (`0x80001009`),
  reset the video surface, and the screen went black. The official app exposes no decoder/codec
  or AVC-preference toggle. As a workaround, the two `OMX.amlogic.vp9.decoder.awesome` entries
  (plain and `.secure`) were commented out in `/vendor/etc/media_codecs.xml`, together with the
  matching performance entry in `media_codecs_performance.xml`; the original two xmls were
  backed up on the box before the edit. After reboot the codec list no longer reports VP9
  hardware: the YouTube client negotiates an H.264 MP4 track (`avc1.64001F`, 1280x720@30),
  opens `OMX.amlogic.avc.decoder.awesome` to Executing, and reaches Presenting with no
  `0x80001009` and no surface reset. VP9/AV1 tracks remain unavailable; the Google software VP9
  decoder stays capped at 1280×1280. Revert by restoring the backed-up xmls.
- The Inputs pill is hidden: TvInputManager reports no HDMI/AV/tuner hardware on this box, and
  `live_tv_package` is empty.
- Granted to tvhome: `WRITE_SECURE_SETTINGS`, `ACCESS_FINE_LOCATION`; AppOps
  `AUTO_START` / `ASSOC_START` allowed; `enabled_accessibility_services` =
  `com.gathin.tvhome/.HomeKeyService`, `accessibility_enabled` = 1.

## Observed and verified (ADB session)

- MainActivity renders: clock/date, Ethernet pill, Settings pill, dock and grid. `DPAD_RIGHT`
  moves focus through the dock; the media tiles launch and resume, and Back returns Home. An
  injected `KEYCODE_HOME` lands on tvhome.
- After one owner-consented reboot, `eth0` came back on its own; the HOME resolver and resumed
  activity were both `com.gathin.tvhome/.MainActivity`; the helper was bound. Re-checked
  2026-11-28: 86 packages present, disabled list empty (0 disabled).
- Standby/wake: the first wake, before `power_on_home_always` was enabled, landed back on the
  stock launcher (late or missing stock-home window event; see the standby-wake entry in
  [TROUBLESHOOTING.md](../TROUBLESHOOTING.md)). After enabling `power_on_home_always` and
  reinstalling, a retested wake landed on tvhome. ADB goes offline while the box sleeps; reconnect
  with `adb connect <box>:5555` after waking.
- No TvInput/HDMI/tuner input is registered, so the HDMI-misroute scenario (HDMI 2 opening
  HDMI 1) does not apply; no input switching was configured.

## Playback codecs (owner-verified)

- Hardware decode works for H.264 (`OMX.amlogic.avc.decoder.awesome`) and HEVC/H.265
  (`OMX.amlogic.hevc.decoder.awesome`); the HEVC decoder produced a first frame in Kodi after
  selecting it directly.
- `OMX.amlogic.vp9.decoder.awesome` is a leftover shell from the MECOOL KM9PRO (S905X2) image
  this firmware is rebranded from: the VP9 instance reaches Executing, then fails on the first
  input (`EINVAL`); no VP9 kernel vdec instance or firmware loads, and playback ends in
  `CodecNameUnknown`. There is no AV1 decoder on this silicon.
- The software Google VP9 decoder is capped at 1280×1280, so 1080p/4K VP9 or AV1 tracks are not
  playable on this box. In practice the YouTube client is held to a 720p30 AVC profile; H.264 and
  HEVC files play through the hardware decoders.
- Before the workaround the official ATV YouTube TV client hit the same VP9 shell on home
  previews and playback: Starboard opened a VP9 WebM stream, the decoder failed unrecoverably
  (`0x80001009`), the surface reset and the app showed a black screen. Unlike SmartTube, the
  official app has no force-traditional-decoder or AVC-preference setting. After stripping the
  amlogic VP9 nodes the client falls back to the AVC hardware decoder (see the YouTube bullet
  in [What was installed](#what-was-installed)).
- Observed on this one rebranded image only; do not generalize to other S905L3A builds.

## Not yet verified

- Physical remote buttons (Home, Inputs, Settings, Volume, Channel, app shortcuts) were not
  pressed on the real remote. Injected ADB keyevents do not prove the firmware-level global key
  route reaches the helper; check with the physical remote before relying on them.
- One initial session plus a follow-up re-check; still no several-day ordinary-use follow-up and
  no before/after RAM or storage measurement. Voice, casting and Bluetooth pairing were not
  checked.
- This unit's image is a rebranded third-party ATV build; do not copy these conclusions to other
  boards.

## Rollback

Nothing was disabled, so no restore record is needed for the launcher: switch the default Home
back to `com.ikukan.launcher` from the box's Home picker or Settings → Apps, turn the
`HomeKeyService` accessibility service off, and uninstall `com.gathin.tvhome` if desired.

The two codec xmls in `/vendor/etc` were edited (the amlogic VP9 nodes are commented out);
restore the backed-up originals to revert that change. The H.264 and HEVC hardware nodes were
left untouched.

See also the other ZTE notes: [ZTE zBox 1](zte-zbox-1-b866v2k.md) (Android TV 14) and
[ZTE ZXV10 B866F](zte-b866f.md) (Android TV 14); both differ from this Android 9 box.
