# TCL 32SF560 (TCL Fire TV Edition, Fire OS 7.7.1.7)

TCL 32" Fire TV Edition, Amazon model `AFTSHN02`, codename `shine`, Fire OS 7.7.1.7 on Android 9 (SDK 28),
build `PS7717.5741N.0036408028416` - the same build as `toshiba-fire-tv-aftha004/`, which together with
`amazon-fire-tv-afttiff43/`, `amazon-fire-tv-stick-lite-2020/`, `insignia-ns55f301na25/` and
`amazon-fire-tv-afteu014/` was the starting point for this list. European set with a DVB tuner. Done over
Wi-Fi on 24 October 2026 with `tools/tv.py` (adb-shell, no adb binary; port 5555 opens as soon as ADB
debugging is on, no pairing), in four batches with a screenshot after each and a reboot at the end.

| File | What it is |
|---|---|
| `debloat.txt` | The 83 packages that were disabled, grouped and commented, plus the 10 that Fire OS protects |

All commands run from the repo root with the TV's IP in `TV_HOST`.

## What worked and what did not

**Debloat: yes.** Amazon ACR (which also reads HDMI input), every metrics and log upload, the advertising ID
and ad bidding, Shopping, retail demo content, the remote tutorials, the promos and popups, the recommendation
rows, Fire TV Channels, Amazon Music, Photos, Kids, miniTV and Luna, Silk and its website shortcut, the account
sync services, the `dummy.*` placeholders, the Alexa helpers, the Miracast and DIAL receivers, printing, and the
forced-OTA promoter. Applied in four batches with a screenshot after each, then rebooted: all 83 stayed
disabled across that reboot, and HDMI, Live TV, Settings and the sideloaded streaming apps were confirmed on
the set afterwards. Ten packages are protected (`Cannot disable a protected package`) and are left out of the
list; the header of `debloat.txt` names them.

**Launcher: no.** This is Fire OS, not Google TV. `com.amazon.tv.launcher` claims Home at priority 950 and is a
protected package, `cmd package set-home-activity` prints Success and changes nothing, and a third-party
launcher's HOME filter stays at 0 because Fire OS ignores priority from an app it did not sign. So
`launcher/build-and-install.sh` does not apply here: the stock launcher keeps Home and the hero banner at the
top of it, which is the launcher's own feed. The recommendation rows under that banner are gone once
`media.recommendations` and `kso.blackbird` are off. Launchers that put themselves *on top* of Amazon's Home
with an Accessibility service are a fork's job and live in [the showcase](https://github.com/Gathin23/cleanyourtv/blob/main/SHOWCASE.md),
not in this list.

## Apply and undo

```
TV_HOST=<tv ip> tools/disable.sh examples/tcl-32sf560-fire-tv/debloat.txt
TV_HOST=<tv ip> tools/restore.sh examples/tcl-32sf560-fire-tv/debloat.txt com.amazon.tv.launcher
TV_HOST=<tv ip> tools/tv.py 'pm enable com.amazon.cloud9'      # one app back
```

## Notes

- `com.amazon.vizzini` (Alexa voice) is not listed: Fire OS re-enables it at every boot on 7 and 8, so the
  microphone button comes and goes. `com.amazon.vizzini.ftvcds` stays off. Setting `secure voice_disabled=1`
  does not survive a reboot either.
- The enforced OTA packages are protected, so firmware and security updates keep running; only
  `com.amazon.tv.forcedotaupdater.v2`, the update promoter, is disabled.
- One setting was changed by hand, and it is not part of the list:
  `settings put global com.amazon.tv.settings.featured_item_video_enabled 0` turns off the autoplaying ad video
  in Home's hero banner. `limit_ad_tracking` and `amz_limit_ad_tracking` were already `1`.
- Private DNS was deliberately not set: the router already filters by domain, and Private DNS would send the
  TV around those rules. The Sponsored rows are the launcher's own feed and are only partly affected by either.
- The favourites bar on Home keeps tiles for packages that are now disabled (Music, Photos, Kids, Silk):
  highlight one, press the ☰ button and remove it.
- The screen recorder returns black frames for Netflix and other DRM apps (`screencap` is not a bug in this
  list); check `dumpsys activity activities | grep topResumedActivity` instead.
- Wait about 20 seconds after the last `pm disable-user` before rebooting: Android writes the package state to
  disk with a delay, and a reboot right away can lose the last change.
