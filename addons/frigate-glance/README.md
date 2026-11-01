# Frigate Glance

A community add-on, not made by or affiliated with the Frigate project.

## What it does

While you watch anything on the TV (an app, Live TV, an HDMI input), a small card slides into a corner the
moment [Frigate](https://frigate.video) raises an alert: which camera, what it saw (and who or which plate,
when Frigate knows), the picture with a box around the object, and a thin bar along the top of the picture
that is red while the event goes on and runs down once it has ended, as in Peek. The card leaves by itself
a few seconds after the event ends.

While a card is up, the remote buttons named on the card act on it: **Dismiss** (default ◀) closes it,
**Snooze** (default ▶) closes it and keeps every card away for a while (default 1 hour), and the optional
**Full screen** (default ▲) opens that camera on the whole screen until Back. Each can be set to act only
when held, against an accidental press while moving around an app; a pill then draws itself around the
action as it is held and turns green when letting go will act. Every other button keeps controlling what you
watch, and when no card is up the add-on reads no buttons at all. A badge on the picture shows whether it is
live video (a camera icon on red) or snapshots (stacked frames).

By default only Frigate **alerts** open a card, so what counts as worth interrupting a film is set once, in
Frigate's review settings (for example a person in a required zone), and the settings screen narrows it
further by camera and object.

![The add-on's card in the launcher's grid](docs/card-in-grid.jpg)

## Permissions

| Permission | Why |
|---|---|
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | To draw the card over whatever is playing. The card never takes focus or touches. Granted by `install.sh` (TCL hides it in Settings). |
| Accessibility service (`KeyService`) | To catch the card's buttons (Dismiss, Snooze, and Full screen when on) while a card is up. It switches key filtering on only while a card shows and off again after, and reads no other keys and no screen content. |
| Internet (`INTERNET`) | To reach your MQTT broker and your Frigate server, and nothing else: no telemetry, no update checks, no ads. |
| Foreground service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`) | One background service keeps the MQTT connection open (a keep-alive every 30 s, no polling). |
| Start at boot (`RECEIVE_BOOT_COMPLETED`) | To reconnect after a reboot. On TCL sets `install.sh` also allows the `APP_AUTO_START` app-op, without which TCL's firmware blocks it. |

Two components only the ADB shell can use (they need `WRITE_SECURE_SETTINGS`, as the launcher's
ThemeProvider): `ConfigProvider` takes `config.json` on firmware that locks the shell out of `Android/data`,
and `TestCardReceiver` shows a test card over whatever is playing.

## Setup

1. Make a config file from [`config.example.json`](config.example.json) and keep it outside the repo
   (`backup/` is git-ignored). Use a Frigate user with the **viewer** role (Frigate user names take no `-`)
   and an MQTT user of its own rather than your main accounts.
2. Install, from the repo root, with ADB on as for the launcher:

   ```bash
   TV_HOST=<tv ip> addons/frigate-glance/install.sh backup/frigate-glance-config.json
   ```

   It builds the add-on, installs it, grants the overlay and auto-start, pushes the config, turns on the key
   service next to any the launcher uses, and prints `MQTT: ok` and `Frigate: ok` (or what went wrong), then
   shows a test card. The pushed file is deleted on the TV once the add-on has read it.
3. Press Home and answer **Show card** when the launcher asks about the add-on's card. Open it for the
   settings, in five sections: **Overview** (snooze, connection status), **Cameras** (which cameras, and
   whether cameras added later count), **Alerts** (alerts only or detections too, which objects), **Card**
   (a test card for any camera, size, corner, how long it stays, live video, boxes) and **Remote** (each key
   picked by pressing it, hold to act, full screen). Settings changed there stay until the next config push.

## Config keys

| Key | Default | Meaning |
|---|---|---|
| `mqtt` | required | `mqtt://user:password@host:1883`; percent-encode `@ : /` in the user or password |
| `frigateUrl` | required | `https://host:8971` (authenticated port) or `http://host:5000` |
| `frigateUser`, `frigatePassword` | empty | Frigate login; the certificate is trusted on first contact and pinned. Frigate makes a new self-signed one whenever its container is recreated (an update) unless `/etc/letsencrypt` is a volume; Overview then shows **New certificate** with both fingerprints, and OK on the Frigate row trusts it |
| `topicPrefix` | `frigate` | Frigate's MQTT topic prefix |
| `cameras` | `{}` (all) | camera name to the name shown on the card; listed cameras only |
| `newCameras` | `false` | with `cameras` listed: cameras Frigate gains later open cards too |
| `camerasOff` | `[]` | cameras switched off (the settings screen writes this) |
| `labels` | `[]` (all) | objects that may open a card, e.g. `["person"]` |
| `severity` | `alerts` | `alerts`, or `alerts_and_detections` |
| `corner` | `top-right` | `top-left`, `bottom-left`, `bottom-right` |
| `cardScale` | `100` | card size in percent (50 to 200), picture and text together |
| `dismissSeconds` | `8` | seconds the card stays once the alert's objects have left (1 to 120); an object coming back stops the countdown |
| `dismissOnStationary` | `true` | a parked object counts as left, as in Peek |
| `snoozeMinutes` | `60` | length of a snooze (5 to 720) |
| `liveVideo` | `true` | live picture from go2rtc's RTSP port 8554; `false` refreshes a snapshot every second |
| `streams` | `{}` | camera to the go2rtc stream for live video, when Frigate's own live stream is too big |
| `rtspUser`, `rtspPassword` | empty | go2rtc RTSP credentials, if set |
| `boxes` | `true` | boxes around detected objects |
| `fullscreen`, `fullscreenKey` | `false`, `KEYCODE_DPAD_UP` | a third card key that opens the camera full screen; Back returns |
| `dismissHold`, `snoozeHold`, `fullscreenHold` | `false` | that key acts only when held for 0.6 s; a quick press does nothing |
| `dismissKey`, `snoozeKey` | `KEYCODE_DPAD_LEFT`, `KEYCODE_DPAD_RIGHT` | Android key names; Back, OK, Enter and Home are refused |

## Live video

Live video comes from go2rtc's RTSP port (8554) on the Frigate host, decoded by the TV's own video decoder.
While another app is playing sound, the card shows snapshots instead: on the test TV a second video stream
next to a playing YouTube video failed and made YouTube stall for a moment. Over Home and menus it plays
live, the first frame after about 1.3 s. A camera's main stream can be large (the test cameras send
2560x1920); publishing the camera's sub-stream in go2rtc and naming it in `streams` makes it lighter.

A Frigate debug replay (camera `_replay_<name>`) counts as the camera it replays and shows as "Name
(replay)". Frigate publishes no review items for a replay, so each tracked object stands in for an alert, and
go2rtc has no stream for it, so a replay card always shows snapshots.

## Tested on

TCL 65Q650G (Android TV 11, Realtek RTD2851, 2 GB) with the reference launcher 2.6, Frigate 0.18.2 and the
Home Assistant Mosquitto broker. Checked with the real remote: Dismiss and Snooze over a playing YouTube
video without it seeking, every other key passing through, the launcher's Netflix, YouTube, Prime, Settings
and Input buttons unchanged, the card over Home, YouTube and the settings screen, reconnecting after a
reboot without opening the app, hold to snooze, and a Frigate debug replay of a camera. Memory: about 25 MB idle, 40 to 53 MB with a card and live video up, about
35 MB after a card.

## Credits

The idea, the timer bar and the config keys follow [Peek](https://github.com/casi-3/peek) (MIT), the desktop
version.
