# Stay Awake

Keeps the screen on while another app is playing music or video. The sleep timer and screensaver run
again as soon as playback pauses or stops. Off until you turn it on.

Package `io.github.callmejm.stayawake`. You see it as **Stay Awake**. On cleanyourtv's Home it is also a
module card, **Stay Awake**, which opens the on/off screen.

The launcher itself does not take `WAKE_LOCK`. This app does, and only while you have left it on and
something is actually playing.

## Install

JDK 17 and Android SDK 34, as for the launcher. Android 8 or later (`registerAudioPlaybackCallback`
is API 26).

```sh
cd addons/stay-awake && ./gradlew assembleRelease
```

Then install the release APK with `tools/tv.py`, as in [addons/README.md](../README.md). Press Home
and answer **Show card** if you want the card. Open it and choose **Turn on**.

## Permissions

- **`WAKE_LOCK`**: a screen wake lock while music or video is in the started state. Released as soon
  as playback pauses. Nothing takes the lock while Stay Awake is off.
- **Foreground service** (`FOREGROUND_SERVICE`, and `FOREGROUND_SERVICE_SPECIAL_USE` on Android 14):
  the watcher has to keep running after you leave this app, or the screen can sleep while Spotify is
  in front. A quiet ongoing notification says it is watching. The service stops when you choose
  **Turn off**.
- **Start at boot** (`RECEIVE_BOOT_COMPLETED`): after a reboot, the watcher comes back only if you
  left it on.
- No network. No accessibility service. The service is not exported.

Turning it off unregisters the playback callback and drops the lock. A playback event that was
already queued cannot acquire the lock after that.

## Tested on

The same wake behaviour was checked on a Xiaomi TV A Pro 75" 2026 (MiTV-MZTU0), Google TV 14,
firmware V816.0.26.6.2.6.URTMIXM, while it still lived in the launcher, and on a Philips
65PUS8102/12 (Android TV 8.0) by another person in
[pull request 1226](https://github.com/Gathin23/cleanyourtv/pull/1226). This package is that
behaviour moved into its own app.
