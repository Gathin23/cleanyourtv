# Philips 50PUS7303/12: Philips Sources and launcher notes

The Philips 50PUS7303/12 in Europe is recorded here as a second tested model on the same product,
board and firmware build as the 55PUS7503/12. The owner reports several days of use without issues.

## Identify the exact firmware

| Field | Observed value |
|---|---|
| Retail model | Philips `50PUS7303/12`, Europe |
| Board | `TPM17XE_96` |
| Product | `PH7M_EU_5596` |
| SoC / memory | MediaTek MT5891 / approximately 2 GB |
| Android / SDK | Android TV 8.0.0 / 26 |
| Build display | `OC release-keys`, incremental `4718` |
| Android security patch | 2021-01-05 |
| Fingerprint | `Philips/PH7M_EU_5596/PH7M_EU_5596:8.0.0/OC/4718:user/release-keys` |

This is the TPM171E generation. The build values above were read from the original backup; no
marketing firmware version was recorded. The 65PUS8102 list also uses this product code and build,
but its additional package choices have not been validated on this 50PUS7303.

## Tested package choices

The 50PUS7303 setup disabled the seven feedback, tutorial, promotional, logging and recommendation
packages in the first sections of [debloat.txt](debloat.txt). The stock launcher was replaced by CleanYourTV.
It also disabled Inscape ACR and the Philips DAC pair (`org.droidtv.candeebug`, `org.droidtv.gel`),
which the list now disables on both models, and the five packages shown as commented options at the
end of that file: store-demo stickers, Twitter sharing, the NetTV app gallery, Prime Video and Alexa.
Those options remain commented to preserve the 55PUS7503 list's defaults and to leave streaming apps
and voice assistants available unless an owner chooses otherwise.

Use the Apply and Undo instructions in the list header. The 55PUS7503's HDMI-port, YPbPr and
Netflix checks are not test claims for the 50PUS7303.

## Home and Philips Sources

The original Home was `com.google.android.tvlauncher`; CleanYourTV is now Home. Live TV is
`org.droidtv.playtv`.

`org.droidtv.eum` (Philips Help) must remain enabled for the Philips Sources menu on this set. With it
disabled, pressing the physical Input button opened
`org.droidtv.channels/.sources.SourcesDrawerActivity`, its default target, but the drawer immediately
closed with a `NullPointerException` in `bindToEumService`. Re-enabling `org.droidtv.eum` restored the
menu, confirmed with the physical remote.

After some launcher reinstalls, `HomeKeyService` was configured but unbound; one reboot restored it.
Later reinstalls connected successfully, so this does not establish that a reboot is always required.

## Remote button scancodes

These Linux input scancodes were observed on the remote used with this 50PUS7303/12. They are not
Android keycodes and have not been verified on other remotes or TPM171E models.

| Button | Scancode |
|---|---|
| Red | `398` |
| Green | `399` |
| Yellow | `400` |
| Blue | `401` |
| Input | `610` |

The Input button's default target is the Philips Sources drawer described above; keep
`org.droidtv.eum` enabled for it. Custom app assignments and tap/hold mappings are not included here.

## Package and testing limits

- Keep the tuner/HDMI stack, Philips Sources, remote and Bluetooth services, keyboard, Play Store and
  Play Services, Chromecast, Assistant, firmware updates and Ambilight enabled.
- The owner confirmed HDMI playback, tuner reception and USB media playback through the built-in app.
- Other functions are not claimed as tested.

## Third unit: Philips 49PUS7502/12 (one day of use)

Same product, board and firmware as above, read from the TV: `TPM171E`, `PH7M_EU_5596`, Android TV 8.0.0 (SDK 26),
build `OC release-keys` 4718, security patch 2021-01-05, fingerprint
`Philips/PH7M_EU_5596/PH7M_EU_5596:8.0.0/OC/4718:user/release-keys`, 1920x1080, density 320. This was one
afternoon and evening, not several days; the list's claims above are not repeated for this unit.

**`tools/disable.sh` refused two packages of this list on this TV.** `org.droidtv.usagelogger` and
`org.droidtv.demome` both hold `android.permission.TV_INPUT_HARDWARE`, so its input guard skips them
("the software layer under the HDMI/AV/tuner inputs"). They stayed enabled here and nothing was forced. If the
50PUS7303 and 55PUS7503 owners disabled them by another route, that is their result; this unit did not test it.

What was applied here, with the guard's two skips left enabled:

- The telemetry group (Inscape ACR, the DAC pair, the framework logger, Partner Setup, Market Feedback).
- The Philips promo group and the Android TV tutorials, including the three options the list keeps commented
  (`org.droidtv.sticker`, `org.droidtv.twittershare`, `org.droidtv.nettv.market`).
- The stock launcher and its two recommendation engines, replaced by the launcher in `launcher/`.
- Outside this list, as the owner's own choice: `com.google.android.videos`, `com.google.android.music` and
  `com.google.android.play.games`.

Kept: Netflix, Prime Video, YouTube, ZDF and ARD, Assistant, Alexa, Chromecast, firmware updates,
`org.droidtv.eum`, Ambilight, DLNA and Play Store. No Private DNS was set.

Checked on this unit, with screenshots from the TV and key presses over ADB: the replacement Home as Home
(the resumed activity and Home resolution), D-pad moves of one row, the Inputs pill opening HDMI 4 (`org.droidtv.playtv`
in front, empty screenshot as expected), Netflix, ZDF and ARD launching from their tiles and Home returning, a
reboot with the disabled list identical before and after, and the Philips control API still answering on port
1926 (`tools/philips-api.py`). The owner pressed the real remote's Home, Input and Netflix buttons and held
volume up: all worked with the Home key service bound. Not tested: tuner reception, USB media, Ambilight/Hue,
DLNA.

**Power-on lands on Live TV.** After a cold boot the TV opened `org.droidtv.playtv` by itself, so Home was not
what came up. In `launcher/app/src/main/res/values/config.xml`, `power_on_home` alone did not move it; with
`power_on_home_always` also true, and the launcher installed with the Home key service, the next cold reboot came up
on Home. This matches the note for the Philips 65OLED706 in `docs/TROUBLESHOOTING.md`. The replacement launcher
used `org.droidtv.playtv` as `live_tv_package`; Watch TV is also in the Inputs pill.

**Android 8 has no Wireless debugging.** USB debugging alone opens port 5555. No "Allow USB debugging?" prompt appeared on the first attempt
(the handshake timed out); after a full restart (not standby) the connection worked, as the list header suggests.
Switching USB debugging off closed the port again.

## Fourth unit: Philips 55PUS7363/12 (one evening)

Same product, board and firmware as above, read from the TV: `TPM171E`, `PH7M_EU_5596`, Android TV 8.0.0 (SDK 26),
build `OC release-keys` 4718, software version `TPM171E_R.107.001.143.000`, security patch 2021-01-05. The rating
label names the chassis `TPM18.1E LA`; the firmware reports itself as TPM171E all the same. This unit has its own
list, [debloat-55pus7363.txt](debloat-55pus7363.txt), because its owner went further than `debloat.txt`: the
Opera TV SDK sample app and, as commented options, Alexa, the old Google Play apps, the whole NetTV app gallery
with its web apps, and DLNA. Its header has the Apply and Undo lines and what was checked.

**The RSA prompt** did not appear until the USB debugging authorisations were revoked, debugging was switched off
and on, and the TV was power-cycled at the mains. A plain reconnect, and a reconnect after a mains restart
without the revoke, left the connection `unauthorized`.

**`org.droidtv.usagelogger` and `org.droidtv.demome`** were disabled with `TVINPUT_ALLOW=1` after checking that
neither serves a TV input (`cmd package query-services -a android.media.tv.TvInputService` lists only
`com.mediatek.tvinput`, `com.mediatek.agent` and VLC). Home, the real Source button, HDMI 2 and a reboot were
checked afterwards.

**NetTV gallery.** The "Philips TV Collection" tile is `org.droidtv.nettv.market`. The owner switched to Play Store
apps and turned off the gallery, its three service packages and the three preinstalled web apps together;
`org.droidtv.nettvbrowser` stayed on. Netflix and MagentaTV still started from Home afterwards.

**A paused app in the background.** The ZDF Mediathek, left paused with Home rather than closed with Back, held
about 78% CPU in `dumpsys cpuinfo`, and the whole TV sat near 54% busy; after `am force-stop` the TV dropped to
about 5%. This is the app, not the debloat. Android's three animation scales were set to 0.5 on this set at the
owner's request (`settings put global window_animation_scale 1` and its two siblings undo it).

**Power-on** came back on the last source (HDMI 2), not Home, and the owner kept it that way; the real Home button
opened the replacement launcher without `HOME_KEY_SERVICE`. With no aerial connected, `live_tv_package` was left
empty and `org.droidtv.playtv` stayed in `hidden_apps`. Not tested here: tuner reception, Ambilight, USB playback,
Chromecast sending.
