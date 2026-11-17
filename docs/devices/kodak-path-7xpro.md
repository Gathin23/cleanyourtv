# Kodak PATH_7XPRO / LEDTV32HD: Android TV 9 observations

One owner-operated TV, tested on 31 October 2026. These are same-day observations,
not a several-day debloat list or a compatibility claim for every Kodak TV.
See [CONTRIBUTING](../../CONTRIBUTING.md) before promoting this report to a list.

## Identify the firmware

| Property | Observed value |
|---|---|
| Brand reported by the owner | Kodak |
| Manufacturer | SPPL |
| Model | `PATH_7XPRO` |
| Product / device | `LEDTV32HD` |
| Android / SDK | 9 / 28 |
| Build display ID | `PI.20210601 release-keys` |
| Build fingerprint | `THOMSON_KODAK/LEDTV32HD/LEDTV32HD:9/PI/20210601:user/release-keys` |
| RAM | Approximately 1 GB |

The computer and TV used Wi-Fi. No USB Ethernet adapter was in use.
Match the model and firmware before treating these notes as applicable to another set.

## Home handover and input packages

The session used the reference launcher based on commit `8ae8df8`, with local font,
theme and focused-card-motion customizations. Those customizations are not part of this note.

The stock Home package was `com.google.android.tvlauncher`. Setting the replacement
Home activity while stock Home remained enabled printed `Success`, but the resolver
still selected stock Home. The existing guarded `launcher/build-and-install.sh`
procedure started the replacement, disabled stock Home and assigned the new Home.
`com.gathin.tvhome/.MainActivity` was confirmed as the default and returned after reboot.
Do not treat the command's success message alone as proof of a handover.

The input-handler guard retained `com.google.android.tv`: package inspection showed
HDMI/AV/channel VIEW handlers. `com.android.tv`, `com.droidlogic.droidlivetv` and
`com.droidlogic.tvinput` were also retained. These are not candidates for removal just
because the owner does not watch broadcast TV. The Inputs menu enumerated HDMI1-3,
AV1-2 and Live TV; external-source playback was not tested.

## Owner-selected cleanup

These six packages were disabled in the session and remained disabled after a reboot:

- `com.google.android.videos` (Google Movies)
- `com.jio.media.stb.ondemand` (JioCinema)
- `com.google.android.play.games` (Play Games)
- `com.sonyliv` (SonyLIV)
- `com.google.android.tvrecommendations`
- `com.google.android.leanbacklauncher.recommendations`

The first four were the owner's app choices, not universal bloat recommendations.
Stock Home was disabled separately as part of the guarded handover. Package/settings
backups and the applied lists were saved locally. Nothing was uninstalled; no root,
firmware flashing or factory/service menu was used. This report does not establish a
several-day stability or restore test, so it deliberately ships no executable debloat list.

Play Store, Play Services, the Google voice app, casting and firmware-update packages
were retained. The owner confirmed streaming video, sound and phone casting worked.
YouTube, Prime Video, JioHotstar, Apple TV and FanCode each passed launch/foreground checks;
that is not a DRM or account-access test of every app.

## Separate aerial screensaver

Aerial Views 1.8.5 was installed separately; it is not part of this launcher.
1080p H.264 was used after the initially selected HEVC playback produced decoder errors.
The owner confirmed moving scenery on the TV even when ADB screenshots missed the
hardware video layer. Do not diagnose a black video-layer screenshot alone as failed playback.

The dream component was `com.neilturner.aerialviews/.ui.screensaver.DreamActivity`.
The system idle timeout was 300000 ms. A full five-minute idle test reached Dreaming
with that component, and the owner confirmed the real remote's Home button exited it.
In Aerial Views, Settings → Overlays → Auto show/hide options → Overlay visibility →
Always hidden removed clock and location text. The videos streamed over the internet.

## Sound and picture menus

`com.droidlogic.tv.settings/.SoundActivity` opened Audio Output. Its Sound row opened
`com.droidlogic.tv.soundeffectsettings/.SoundModeActivity`. Available sound presets were
standard, music, news, movie, game and user. The owner chose a dialogue-focused setup;
news was selected, balance stayed at 50% and output stayed on TV Speakers. Selecting
standard in the same menu restores the previous preset. No acoustic improvement was measured.

The sound panel appeared to dismiss during slower remote sessions. Opening it, waiting
for the activity to draw, and navigating within one ADB connection allowed the setting
to be changed and read back. An old screencap could otherwise look like the menu was
still open after focus had returned to Home.

The normal Device Preferences menus exposed no picture adjustments, and
`com.droidlogic.tv.settings/.display.DisplayActivity` showed an empty Display page.
The owner also could not reach picture controls through the remote. Picture settings
were left unchanged; this does not prove the hardware has no picture controls.

## Voice remote remains unresolved

The Google app `com.google.android.katniss` opened with
`android.intent.action.SEARCH_LONG_PRESS`; RECORD_AUDIO permission and its app-op were
allowed. The remote opened Assistant, but speech was not received. Bluetooth reported
a saved `TV BLE Remote` bond with no active connection. The remote package
`com.droidlogic.BluetoothRemote` remained enabled.

The normal `com.droidlogic.tv.settings/.BtSetupActivity` screen instructed the owner to
press Volume Down and Volume Up together for pairing. The attempted reconnection did
not succeed, and the owner deferred further diagnosis. Held volume and Home were
confirmed separately; functioning buttons do not establish that microphone audio works.
No Bluetooth bond or Assistant data was cleared. No cause is attributed to the cleanup.

## End-of-session state

The owner revoked debugging authorizations, disabled debugging and restarted the TV.
`tools/adb-closed.py` then reported port 5555 closed with connection refused. No personal
network names, addresses, device serials, ADB keys, raw logs or account details are
included here. Long-term stability, external HDMI playback and voice recovery remain untested.
