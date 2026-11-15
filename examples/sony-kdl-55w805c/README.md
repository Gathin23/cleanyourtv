# Sony KDL-55W805C (BRAVIA 2015, EU firmware 5.433)

One unit, build `SVPDTV15_EU-user 7.0 NRD91N.S34 5.433`, set up with a coding agent from 25 October to
1 November 2026. The list is in [`debloat.txt`](debloat.txt); these are the things around it.

## Home on Android 7

The published launcher needs Android 8 (minSdk 26). This set runs the current launcher rebuilt with minSdk 24
and the four changes in [`docs/devices/mtek-mk32cs1nb.md`](../../docs/devices/mtek-mk32cs1nb.md): core library
desugaring, and guards around `getChangedPackages`, `Resources.getFont`, `Files.readAllLines` and
`Bitmap.Config.HARDWARE`. `testReleaseUnitTest`, `lintRelease` (no NewApi errors) and `assembleRelease` pass;
the APK is about 715 KB, over the size gate, so it stays a local build (the
[`cleanyourtv-legacy`](../../SHOWCASE.md) fork does the same for this platform).

- Home, the Home key, the D-pad from every edge, Inputs to HDMI 1 and themes work.
- D-pad along the dock: median frame 5 ms and about 15 % janky frames, the same on Aurora, Minimal and the
  default look; the launcher takes about 33 MB.
- A module card installed while Home was running asked "Show card" only after the launcher restarted
  (`am force-stop com.gathin.tvhome`, then Home).

## Album and the Inputs row

`com.sony.dtv.osat.album` answers `ACTION_VIEW` with the type `vnd.android.cursor.item/channel` and no data,
so `tools/disable.sh` keeps it as the TV's input handler. With the stock home (or a third-party home that opens
inputs through it) keep it. The repo launcher's Inputs popup sends the passthrough URI with that type, and on
this set it resolves to `com.sony.dtv.tvx/.tvplayer.main.TvxLauncherActivity`: with Album disabled
(`HANDLERS_ALLOW=1`), HDMI 1 still opened from the popup. It is in the list's last commented block.

## HbbTV off over ADB

Settings → Channel setup → Digital setup is a native MediaTek menu (`NativeMTKActivity`) that neither
`uiautomator` nor `screencap` can read, next to the channel scan. The same switch has an ordinary Android screen:

```bash
TV_HOST=<tv ip> tools/tv.py "am start -a com.sony.dtv.intent.action.HBBTV_CONFIRMATION"
```

It asks "Enable interactive broadcast application". No calls `setHbbTvDisagree`, which sets the MtkTvConfig
value `g_menu__hbbtv` to 0 (logcat shows it). The native HbbTV browser
(`/3rd/internet_browser/browser -x hbbtv`, about 13 MB) stopped and did not come back after a reboot. Yes on
the same screen turns HbbTV on again.

## Sony's content recognition

`com.sony.dtv.interactivetvplatform` runs `SambaTvRCR`, `NativeVideoShm` (video frames over shared memory) and
discovery plugins for Kantar, Kuyun and ShopTV. `com.sony.dtv.interactivetvutil` runs log collectors (broadcast,
application, action, internal events). The platform is in the list's last commented block; `interactivetvutil` stays, as
other Sony apps read its token provider (see `examples/sony-bravia-4k-vh21-eu`).

## A USB soundbar

A USB speaker (a Dell AC511 here) enumerates as an ALSA card, and Android lists it as
`AUDIO_DEVICE_OUT_USB_DEVICE` with an open output, yet nothing plays on it. MediaTek's policy engine
(`Engine::getAudioOutputDevice` in `libaudiopolicyenginedefault.so`) builds the media output from the speaker,
headphone, line-out, ARC/SPDIF and A2DP only, keyed on `persist.svp.aout` (`tv_speakers` or `audio_system`), so
no Sound setting reaches USB.

- **Apps:** a small app that keeps one silent media track routed to the bar with `AudioTrack.setPreferredDevice`
  pulls the whole media strategy to it (Android 7's explicit routing), so YouTube, Prime Video, Spotify, AirPlay
  and the menu clicks play on the bar. The firmware applies no volume to the USB output, so the app sets the
  stream volume on that thread through `IAudioFlinger` (`MODIFY_AUDIO_SETTINGS`) and the remote's volume keys
  work; it also sets the bar's own UAC volume to its maximum (it powers up at −39 dB, Windows uses −13 dB).
  Source and notes: [tv-usb-speaker](https://github.com/ansar-rezaei/tv-usb-speaker).
- **Live TV and HDMI** cannot move to the bar without root. Their sound is mixed in the MediaTek chip, and the
  only copy an app can read (the HAL's `tv_record` input, opened as a TV tuner or HDMI input with
  `RECORD_AUDIO`) comes after the TV speakers' volume and switch: music index 40 gave −29 dBFS, index 1 gave
  −78 dBFS, and Speakers → Audio system silenced it. While the bar holds the media route the TV speakers'
  volume stays where it was, so the app lets go of the route while `com.sony.dtv.tvx` is in front.

## AirPlay

[jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server) v0.0.36 (minSdk 23) builds
for `armeabi-v7a` (8.8 MB) and runs here: iPhone screen mirroring works, with its sound on the USB bar. Building
it in a container takes `python3`, `patch` and `gcc` besides the Android SDK; NDK 27 and SDK 37 download on
the first build. `AirCast` (`com.smartdevice.tv.aircast`, Play Store), the receiver the owner had before, is a
Coocaa/Skyworth app with log endpoints at `sc-clog.skysrt.com` and `cloud.coocaa.com`; the owner removed it.

## Play Store on this set

Checked in November 2026 on the TV's own Play Store:

- Offers Install: ARD Mediathek, ZDF, Pluto TV, Plex, VLC, Kodi, TuneIn Radio, Twitch, Send files to TV,
  Aerial Views.
- "Your device isn't compatible with this version": Radio Garden, ARTE, X-plore.
- "Isn't available on Google Play on this device": WELT (its live channel plays in the YouTube app).
- Not offered in this country: Joyn.

## Screensaver

`screensaver_components` pointed at `com.google.android.backdrop/.Backdrop`, which was already disabled, so
the set had no screensaver. Aerial Views from the Play Store works as one:

```bash
TV_HOST=<tv ip> tools/tv.py "settings put secure screensaver_components com.neilturner.aerialviews/.ui.screensaver.DreamActivity"
```
