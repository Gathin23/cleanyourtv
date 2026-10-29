# Amazon Fire TV Stick AFTMA08C15: session notes

Observed on one device on 25 October 2026. The owner calls it **Fire TV Stick
4K Plus**; the device reports **AFTMA08C15**, Android 11 / SDK 30, Fire OS
**8.1.8.5**, build **RS8185.3891N**. Match the reported model and build rather
than assuming another Stick with a similar retail name behaves the same way.

These are single-session observations, not a tested debloat list or a change to
CleanYourTV's launcher. The external [dumbtv project](https://github.com/humblemuzzu/dumbtv)
was used at commit `14c74e1728360bb42dd8fa749416101fd545f4b2`, with dumbtv Home
**1.3** (`tv.dumbtv.home`, version code 4). Its settings and helper below belong
to that project, not `com.gathin.tvhome`. Follow [PROMPT.md](../../PROMPT.md)
for backups, protected packages, physical remote checks and closing ADB.

## Scope and preserving updates

Package, disabled-package, property, Home and all three settings-namespace
snapshots were saved before changes. There were no initially disabled packages.
With another Android device connected, every ADB call selected the Stick
explicitly (`adb -s <tv ip>:5555`); dumbtv scripts used `ADB_SERIAL`.

The owner kept Prime Video, Alexa, all user apps and stock YouTube. No DNS filter
was installed. Thirty-two packages from the external project's cleanup list were
disabled in four batches of eight, with screenshots between batches; none were
uninstalled and no batch reported a suspect failure. One surviving telemetry
process (`com.amazon.neo.minerva.logcat`) also received the script's background
app-op restrictions and metered-background blacklist entry. Package enabling alone
would therefore not undo every change made by that script.

To preserve firmware updates:

- `com.amazon.tv.forcedotaupdater.v2` and `com.amazon.tv.easyupgrade` were kept.
- The external `lockdown.sh` step was skipped: it also restricts updater background
  work, so merely keeping the updater packages enabled is insufficient.
- DNS/VPN configuration was left unchanged.
- The cleanup script sets window and transition animation scales to `0.5`.
  Both were restored to their backed-up `1.0`; no animation-speed benefit or
  regression was measured on this unit.

A reboot after cleanup returned to Amazon Home and restored the ADB connection.
The 32 disabled packages stayed disabled after the later launcher reboot too.
This short run does not establish long-term package safety, successful firmware
updates, or reduced telemetry traffic; no reusable debloat list is submitted.

## A passing injected Home test missed a real-remote failure

Amazon's launcher remained enabled. dumbtv's installer enabled its accessibility
service, `tv.dumbtv.home/tv.dumbtv.home.Guard`, and set the global alarm-manager
parameter `min_futurity=0`. Its verification script found the helper bound and
an injected Home key returned to dumbtv. After a reboot, dumbtv was foreground
before verification ran, and those checks passed again.

**The physical Fire TV remote nevertheless opened Amazon Home later.** At that
point the helper was still enabled and bound, `accessibility_enabled` was `1`,
and `min_futurity=0` was still present. A bound helper and a passing
`adb shell input keyevent HOME` check did not establish reliable physical Home
handling.

Logs showed several closely spaced Home launches and some intervening dumbtv
launches. This suggests a timing interaction, but does not isolate its cause or
prove that repeated presses caused the failure.

The external project's documented recovery, rerunning `scripts/launcher.sh -y`
with the correct `ADB_SERIAL`, reinstalled the same version and rebound its
helper. Fire TV Settings was then opened with:

```sh
adb -s <tv ip>:5555 shell am start -a android.intent.action.MAIN -c android.intent.category.LEANBACK_SETTINGS
```

The owner briefly pressed Home once on the physical Fire TV remote and confirmed
that dumbtv opened. That is one successful recovery test, not proof that rapid
presses, long presses, or another reboot after recovery are reliable. Do not
remove Amazon Home or disable more packages to work around this observation.

## Projector remote and HDMI-CEC

The owner reported controlling the Stick with the connected projector's remote.
After launcher installation, `dumpsys hdmi_control` still reported
`mHdmiControlEnabled: true` and `mHdmiCecVolumeControlEnabled true`; the global
`hdmi_control_enabled` setting was `1`. HDMI/CEC and remote services were left
alone. These are configuration checks: projector-remote navigation and held-volume
behavior after the changes were not subsequently confirmed by the owner.

## Wallpapers and app ordering on Fire OS 8

ADB could not read `/sdcard/Android/data/tv.dumbtv.home/files/home.conf`
(`Permission denied`). dumbtv 1.3 supports a writable alternative:

```text
/sdcard/Android/media/tv.dumbtv.home/home.conf
/sdcard/Android/media/tv.dumbtv.home/wallpapers/
```

Its [launcher documentation](https://github.com/humblemuzzu/dumbtv/blob/14c74e1728360bb42dd8fa749416101fd545f4b2/launcher/README.md)
describes this path. The media-folder configuration is merged once into existing
settings when Home returns; only supplied keys are changed. Wallpaper files in
both supported folders are considered. This is app-specific support, not a general
workaround for Android's app-data restrictions.

Three 1920 x 1080 JPEG landscapes were copied to the media wallpaper folder,
about 1.1 MB combined. This configuration put the requested apps first:

```ini
background=image
dim=40
rotate=hourly
order=custom
apps.order=com.amazon.firetv.youtube,com.netflix.ninja,com.crunchyroll.crunchyroid,in.startv.hotstar
```

All four package names were checked against this Stick's installed packages.
Leaving Home for Settings and returning applied the changes without force-stopping
the launcher. Focus initially stayed on the previously selected app, so the row
was scrolled left before assessing its order. A device screenshot confirmed the
wallpaper and **YouTube, Netflix, Crunchyroll, JioHotstar** as the first four tiles.
Hourly rotation was configured, but an hour-long rotation test was not performed.
No photos, APKs, private configuration or device screenshots are included here.

## Remaining checks and recovery boundaries

Playback, Alexa, held volume, projector-remote control, rapid/long Home presses,
and post-recovery reboot persistence remain unverified. Debugging closure was
still pending when these notes were written; an ADB connection returning after a
reboot is not evidence that debugging was closed.

The external `launcher/install.sh --undo` documents handing Home back to Amazon
while retaining dumbtv as an installed app; that undo was not exercised in this
session. Review the external full restore script against the saved state before
using it: it resets more than package enabled states. No root, system partition
writes or factory reset were used or tested.
