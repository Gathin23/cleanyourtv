# Bell Fibe TV (ARRIS VIP7802): Android TV 11

Bell Canada's IPTV set-top box, built by ARRIS (now CommScope), running Android TV 11 (SDK 30),
firmware `RTT4.240509.001` (build `11.02.02.02.160-RC.149`, `release-keys`). It is a TV receiver, not
a TV: it has no screen and no tuner of its own, only HDMI out to whatever display it's plugged into.
The launcher (`com.custom.tvlauncher`) and the remote's button and Netflix-key routing
(`com.arris.globalkeyactions`) are ARRIS/Bell's own, and there is no HDMI/AV tuner input to protect -
`pm query-services --components -a android.media.tv.TvInputService` names nothing on this box. Fibe TV
content plays through `ca.bell.chiclet.fibehelper`, kept enabled - see "Not disabled" below.

## What this list actually removes: 13 packages, no apps

`debloat.txt` disables ARRIS/CommScope telemetry and reporting (6 packages, the ones registering
`BOOT_COMPLETED` or reporting usage back to CommScope), two packages confirmed to be dead stubs
(`com.arris.android.stb.fti`, which ships as `SetupCustomizerRecoveryStub`, a stub rather than the
real first-time-install app; and `com.arris.quickactions`, whose name suggests a quick-actions menu
but which declares no activity, service or receiver that could show one), and five Google TV
services most owners would not miss but which are real, separate things, not telemetry:

| Package | What it is |
|---|---|
| `com.google.android.play.games` | Google Play Games: achievements, leaderboards, cloud saves for Android games - rarely used on a TV box. |
| `com.google.android.videos` | Google Play Movies & TV, Google's own movie rental/purchase storefront. Doesn't touch Netflix, Prime Video or any Bell app. |
| `com.google.android.youtube.tvmusic` | YouTube Music, a separate app from the main YouTube app (`com.google.android.youtube.tv`, which is untouched). |
| `com.google.android.feedback` | Android TV's own crash/feedback uploader to Google, separate from anything Bell/ARRIS collects. |
| `com.google.android.tvrecommendations` | Populates the stock Google TV home screen's "recommended for you" rows; this box runs Bell's own launcher, not Google's, so these rows likely aren't even shown. Re-enables itself at every boot on this firmware (see "What a reboot actually showed" below); disabling it again after each boot only lasts until the next one. |

Comment out any of these five if you want them back; that's the convention this repo's other lists
use for something that's genuinely a personal call (see `examples/anker-nebula-d2140`,
`examples/xiaomi-mi-tv-4a`, `examples/onn-4k-streaming-box-2023` for the same packages treated the
same way: disabled by default with an inline comment, not held back separately).

This box genuinely does not carry much ARRIS/CommScope bloat beyond the telemetry and the two dead
stubs. Compared with this repo's Fire TV lists, which run 150-250+ lines because Amazon ships
metrics, crashmanager, logmanager, wirelessmetrics, telemetry.emitter and a dozen more standalone
telemetry packages plus sponsored-content services on the launcher itself, this box's confirmed
telemetry footprint is six packages, and there's no sponsored row visible on this launcher to block
with DNS either.

## What a reboot actually showed

Earlier drafts of this README said the content apps and the Google TV services "checked once, not
over sustained use" and left it there. Two actual reboot cycles (enable the 13, reboot, measure;
disable the 13, reboot, measure again) turned up real, specific behaviour worth recording instead of
a general caveat:

- **`com.google.android.tvrecommendations` re-enables itself at every boot on this firmware.**
  Disabled with `pm disable-user`, confirmed `enabled=3` (disabled) right before each reboot,
  confirmed `enabled=1` (enabled again) right after - on two separate reboots. Same pattern this
  repo's `examples/anker-nebula-d2426` and `examples/anker-nebula-mars-3-air` document for
  `com.google.android.katniss`: disabling it after each boot only lasts until the next one. It is
  still worth listing, since `tools/venv/bin/python -I tools/check.py --fix` (or re-running this
  list) puts it back, and this box's own launcher has nowhere to show the rows it would feed
  anyway.
- **Three packages kept running for a while even after being disabled.** `com.commscope.report.bell`,
  `com.commscope.notification.bell.merlin` and `com.arris.quickactions` all carry the `PERSISTENT`
  manifest flag. After a reboot where they'd already been set to `pm disable-user`, the *next* time
  they were checked they were still alive as processes (`ps -A` showed them `S`leeping, each holding
  4-7 MB PSS), and `am force-stop` could not kill them either - Android protects `PERSISTENT`
  processes from both. They were confirmed gone only after a further full reboot. If you apply this
  list and immediately check `ps -A`, don't be surprised to see these three for one more boot cycle;
  a second reboot clears them.
- **On a freshly booted box with all 13 re-enabled**, six processes started automatically without
  anyone opening anything: the three `PERSISTENT` packages above, plus
  `com.google.android.tvrecommendations`, `com.google.android.videos` and
  `com.google.android.play.games`, together holding about 103 MB PSS (9+4+11+18+34+27 MB). The other
  seven of the 13 (the remaining telemetry/stub packages, YouTube Music, feedback) did not start on
  their own; they only run if something invokes them. Disabling all 13 removes that ~103 MB and the
  CPU/boot time of six fewer processes starting, modest on a 3.9 GB box but real, not a theoretical
  savings.

## Not disabled by default, but a real opt-in line each: preloaded content apps

Earlier drafts of this list put CTV, TSN, Noovo, TOU.TV, the Weather Network, iHeartRadio, Tubi and
Bell's own Fibe player in the default tier, on the reasoning that Bell preloaded them. That was wrong
and has been corrected: being preloaded does not make an app bloat. These are free, real services
people in Canada use, and each now has its own commented-out line in `debloat.txt` if you want to
remove it - prose alone, with no line to uncomment, isn't a real option:

| Package | What it is |
|---|---|
| `ca.bell.chiclet.fibehelper` | The Fibe TV player itself. Disabling it removes live TV entirely. |
| `ca.bellmedia.cravetv` | CraveTV, a paid Bell Media service - needs a subscription, like Netflix (opt-in further down, not disabled by default either). |
| `ca.ctv.ctvgo` | CTV, Bell Media's free ad-supported network app. |
| `ca.tsn.mobile.android` | TSN: free scores and highlights, a subscription tier for live games. |
| `com.vmediagroup.noovo` | Noovo, Bell Media's free French-language network. |
| `tv.toutv.androidtv` | ICI TOU.TV, Radio-Canada's free streaming app. |
| `com.clearchannel.iheartradio.tv` | iHeartRadio: free radio, not Bell-specific but still a real service. |
| `com.pelmorex.WeatherEyeAndroid.tv` | The Weather Network: Canada's most-used weather app. |
| `com.tubitv` | Tubi: free ad-supported movies and TV, not Bell-specific. |
| `com.quickplay.android.bellmediaplayer` | Also content, not telemetry; see its permissions below. |

None of these auto-launches a player or a UI at boot on their own. Six of the seven register
`androidx.work.impl.background.systemalarm.RescheduleReceiver`, the AndroidX WorkManager library's
own boilerplate for rescheduling background jobs after a reboot - standard for any app that uses the
library, not evidence of app-specific behaviour. Two (`tv.toutv.androidtv`,
`com.clearchannel.iheartradio.tv`) also register a recommendations-refresh receiver of their own,
which could populate a row on a launcher that reads it; this launcher doesn't. None of the ten showed
up in `ps -A` across the two reboot cycles described in "What a reboot actually showed" above, where
six unrelated packages did start on their own - a real point of comparison, not just a one-time
snapshot.

This mirrors how this repo's own Fire TV lists treat Amazon's preloads: Prime Video, Amazon Music,
Amazon Kids and Luna stay enabled by default, and a list only notes "owner's choice, keep them if
used" rather than disabling them.

One repo example disables its preloaded streaming apps by default: `examples/homeplus-stb-6252c`,
an Android TV set-top box with 1.85 GB RAM, whose maintainer found those apps resident at 130-170 MB
idle each after boot - the justification given is RAM pressure on a memory-tight box, not simply
being preloaded. This box has about 3.9 GB RAM, and across two full reboot cycles none of the ten
content apps above started on their own the way six unrelated Google/ARRIS packages did (see "What a
reboot actually showed") - no evidence of the same resident-process cost, so they stay opt-in rather
than disabled by default here. If you notice any of them resident and heavy after real use, that
would be grounds to revisit this, the same way Home+'s list did.

## Opt-in: three ARRIS packages with a real, if skippable, function

A commented-out block holds three packages that do something real, just not something everyone
needs: `com.arris.upnpapp` (UPnP/DLNA discovery - lets other devices on your network find this box,
and lets it find UPnP media servers; skip this line if you use neither), `com.arris.customizer.rro.merlin`
(the Runtime Resource Overlay for `com.arris.customizer`, below - disabling the overlay without its
app leaves `customizer` running on default resources, so disable them together if you disable
either), and `android.autoinstalls.config.arris.vip7802w` (Play Auto Install config: on a fresh box
or after a factory reset, this is what silently re-pushes Bell's chosen app set from Play Store;
it already did its one job if your box is past first setup, harmless to disable now, but re-enable
it first if you ever want the Bell app set restored automatically after a reset).

## Not in this file at all: six ARRIS/CommScope packages that aren't bloat

An earlier draft of this list offered `com.arris.gtvs`, `com.arris.chmframework`,
`com.commscope.rcu.bell`, `com.commscope.android.fti.bell.merlin`,
`com.commscope.network.bell.merlin` and `com.commscope.nexusservice` as opt-in, the same tier as the
Google TV features below. That was also wrong: `debloat.txt` is a bloat list, and none of these six
is bloat by any definition - each backs a normal device function, the same category CONTRIBUTING.md
tells contributors to leave out entirely for AOSP's own core packages:

| Package | What it does |
|---|---|
| `com.arris.gtvs` | Ships a `MasterClearReceiver` (factory reset) and a `CustomizerReceiver`. |
| `com.arris.chmframework` | Ships a `DiagReceiver` and a `DevUnlockReceiver`: diagnostics and developer unlock. |
| `com.commscope.rcu.bell` | The remote's own management app: `FindRemoteReceiver` ("find my remote") and a `FirmwareUpgradeReceiver` for the remote's own firmware. |
| `com.commscope.android.fti.bell.merlin` | The box's out-of-box network/Wi-Fi setup wizard (`NetworkSetupActivity`, `WifiHandoffWaitActivity`, `UpgradeActivity`) - the Bell/ARRIS equivalent of Google TV's own setupwraith, below. |
| `com.commscope.network.bell.merlin` | Has a live `MainActivity`, not a dormant service - likely a network-diagnostics screen Bell support could ask you to open. |
| `com.commscope.nexusservice` | Requests `RECEIVE_KEY_PRESS`, `RECEIVE_POWER_BUTTON_OVERRIDE`, `SWITCH_DISPLAY_MODES` and `HDMI_CEC`: remote-key, power-button and display-mode handling. |

`com.arris.customizer` (requests `com.netflix.ninja.permission.NETFLIX_KEY` alongside its own
reboot/recovery permissions) and `com.arris.remoteservice` (ships `ArrisRemoteBroadcastReceiver` and
`ArrisRemoteContentProvider`, most likely the Bluetooth-remote pairing helper rather than the
always-on key routing, which is `com.arris.globalkeyactions` and stays enabled) are excluded for the
same reason: both touch the remote, and neither is telemetry or a content app.

This section exists so a reviewer or a future contributor can see what was looked at and ruled out,
without re-running the same checks. None of the eight showed anything resembling telemetry or RAM
pressure; they're absent because a debloat list isn't the place to offer them, not because they're
risky.

## Held back: the firmware updater

`com.arris.arus.updater` is commented out, so firmware updates keep arriving by default, per this
repo's own rule against disabling updates for everyone. Uncomment the line yourself if you want to
stop them; see `docs/firmware-updates.md`.

## Not offered at all: three mainstream Google TV features

Assistant voice search, Chromecast casting and the phone-as-remote app are not in `debloat.txt`,
commented out or otherwise. An earlier draft offered them as opt-in, next to UPnP and Play Auto
Install; on reflection that doesn't hold up, because unlike those two, these are mainstream features
most owners would reach for and miss immediately, not minor conveniences:

| Package | What it is | What you'd lose if you disabled it anyway |
|---|---|---|
| `com.google.android.katniss` | Google Assistant's voice-search backend on Android TV/Google TV. | The remote's mic button stops doing anything; no voice search anywhere on the box. |
| `com.google.android.apps.mediashell` | Chromecast built-in: lets a phone or laptop cast video/audio to this box over Wi-Fi. | Casting from any phone app (YouTube, Spotify, a browser tab) to this box stops working. |
| `com.google.android.tv.remote.service` + `.rro` | Lets the "Android TV Remote" phone app control this box over Wi-Fi. | A phone can no longer act as a second remote or keyboard. Bell's own physical remote (`com.arris.globalkeyactions`) is a separate package, unaffected either way. |

## Opt-in: mainstream streaming apps

A commented-out block holds `com.netflix.ninja` (Netflix), `com.amazon.amazonvideo.livingroom`
(Prime Video) and `com.google.android.youtube.tv` (YouTube). These are the services most people on
this box actually use, so they're not disabled by default - but several other lists in this repo
offer Netflix and Prime Video as removable too (`examples/acer-ar3-r3`, `examples/benq-qs01`,
`examples/dexp-a651`), for an owner who genuinely doesn't use one. Uncomment only what you're sure
you don't use. Disabling `com.netflix.ninja` does not touch `com.arris.globalkeyactions`, the Bell
remote's dedicated Netflix button, either way - that package stays enabled regardless, since it also
handles ordinary button routing.

The box this was tested on runs the 13-package default list plus the opt-in ARRIS lines, with
Netflix, Prime Video and YouTube all kept - that is one owner's own choice, not the recommended
default. Most people should stop at the 13 and decide the rest line by line.

## Not offered at all: the setup wizard (`com.google.android.tungsten.setupwraith`)

An earlier draft of this list put this in the opt-in Google TV block, next to Chromecast and
Assistant. On reflection that doesn't hold up, and it's not offered here: this is Google's own
first-run setup flow (Wi-Fi pairing, Google account sign-in, phone-assisted setup), and it runs once,
during initial setup. A factory reset restores the system partition to its factory image, which
brings this package straight back regardless of whatever `pm disable-user` state it was in before
the reset - so disabling it today doesn't survive the one event (a reset) where it would matter
again. What's left is only the downside, not the upside:

- If the Wi-Fi network was saved during setupwraith's own first-run flow, disabling it deletes that
  network and takes the box off Wi-Fi.
- On Google TV, if no other launcher is installed and enabled yet, it can take HOME itself and
  crash-loop `system_server` (`docs/TROUBLESHOOTING.md`).

Dead weight once setup is done, real risk for no lasting gain - not worth offering as a trade.

## Status

Applied and reboot-checked: across two full reboot cycles (13 enabled then rebooted; 13 disabled
then rebooted again), `pm list packages -d` held 12 of the 13 - `com.google.android.tvrecommendations`
re-enables itself at every boot on this firmware, see "What a reboot actually showed" above - Settings
opened from the launcher (`com.android.tv.settings/.MainSettings`, still the only app answering
`android.settings.SETTINGS`), and `com.custom.tvlauncher` remained the sole app answering HOME
throughout. `com.google.android.tungsten.setupwraith` happened to already be disabled on the tester's
box from earlier, unrelated work, with the saved Wi-Fi network already forgotten and rejoined
beforehand; nothing broke, but see above for why this isn't offered as something to do on purpose.
Provisional beyond these reboot cycles: nobody has lived with this list day to day yet; please add a
note here if you have.

## Kept deliberately, not offered even as opt-in

| Package | Why |
|---|---|
| `com.arris.globalkeyactions` | Requests `ca.bell.fiberemote.permission.BUTTON` and `com.netflix.ninja.permission.NETFLIX_KEY`: the Bell remote's button and Netflix-key routing. |
| `com.broadcom.nrdphelper` | Requests `android.permission.HDMI_CEC`. |
| `com.custom.tvlauncher` | The only package that answers HOME on this box. |
| `com.apple.atve.androidtv.appletv` | Preloaded Apple TV app with EPG write access (`com.android.providers.tv.permission.WRITE_EPG_DATA`); content, not telemetry, same as the Bell-preloaded apps above. |
| `ca.bell.chiclet.fibehelper`, `ca.bellmedia.cravetv`, `ca.ctv.ctvgo`, `ca.tsn.mobile.android`, `com.vmediagroup.noovo`, `tv.toutv.androidtv`, `com.clearchannel.iheartradio.tv`, `com.pelmorex.WeatherEyeAndroid.tv`, `com.tubitv`, `com.quickplay.android.bellmediaplayer` | Preloaded content apps, opt-in - see "Not disabled by default" above. |
| `com.netflix.ninja`, `com.amazon.amazonvideo.livingroom`, `com.google.android.youtube.tv` | Mainstream streaming apps, opt-in - see "Opt-in: mainstream streaming apps" above. |
| `com.google.android.katniss`, `com.google.android.apps.mediashell`, `com.google.android.tv.remote.service` + `.rro` | Mainstream Google TV features, not offered even as opt-in - see "Not offered at all" above. |
| The `*.rro.merlin` packages not in this file at all (`com.arris.arus.updater.rro.merlin`, `com.commscope.tvsettings.bell.rro.merlin`, `com.android.wifi.resources.bell.merlin`) | Runtime Resource Overlays, not apps, and not telemetry agents in their own right (unlike `com.arris.customizer.rro.merlin`, which is opt-in - see "three ARRIS packages with a real, if skippable, function" above). |

Side-loaded apps already on this box (Termux styling, F-Droid Firefox, a Colemak keyboard layout) are
the owner's own installs, not Bell/ARRIS preloads, and are not part of this list.

## `com.quickplay.android.bellmediaplayer`'s permissions

This is the actual Fibe TV player (`ca.bell.fiberemote.tv.MainTvActivity`), not a side app, which is
why it's kept rather than disabled. Its permission set is still worth knowing about: it declares a
`NotificationListenerService` (`SystemNotificationListenerService`), which, if ever granted, could
read the content of every notification on the box. `settings get secure
enabled_notification_listeners` came back empty on this box, meaning nothing is currently registered
as an active listener through that path - declaring the capability in the manifest is not the same as
using it, and this was not observed actively reading notifications. It also holds
`ACCESS_ALL_EPG_DATA` and `SYSTEM_ALERT_WINDOW` (draw over other apps), both plausibly explained by a
now-playing overlay and needing the full programming guide, and owns two notification channels of its
own (`REMINDERS_CHANNEL_ID`, `DOWNLOAD_AND_GO_CHANNEL_ID`), i.e. it posts reminders and download
status, ordinary for a streaming app. None of this is disabled here; it's recorded so an owner who
wants to look closer knows where to start.

## Tested after the debloat

Settings opened from the launcher, the launcher stayed Home through two reboots, and
`adb shell getprop ro.adb.secure` reads `1` (network ADB still requires the on-screen "Allow"
prompt). HDMI-CEC and the remote's Netflix key were not separately exercised with real hardware
events this session; both packages were left enabled specifically because of the permissions above,
not because they were tested and found necessary.
