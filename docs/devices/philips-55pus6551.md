# Philips 55PUS6551/12: same-session launcher handover and cleanup

This is a report from one European Philips `55PUS6551/12`, not a reusable debloat list. The cleanup,
launcher handover, cold reboot and remote checks were completed in one session, so the disabled packages
have not been lived with for several days. Do not treat this report as a replacement for the
[`philips-49pus6561`](../../examples/philips-49pus6561/debloat.txt) list, even though the platform and
firmware match: that list deliberately keeps packages that this owner chose to disable.

## Identified firmware

| Field | Observed value |
|---|---|
| Retail model | Philips `55PUS6551/12` |
| Product / board | `QM16XE_U` / `QM163E`; `ro.tpvision.product.hwboard` is `2K16EU4_10` |
| SoC | MediaTek MT5890 (`mt5890`), `armeabi-v7a` |
| Android / SDK | Android TV 8.0.0 / 26 |
| Firmware | `QM163E.0.26.70.1`, Android build `OPR5.170623.014` incremental `026701` |
| Security patch | 2019-09-01 |
| Stock Home | `com.google.android.tvlauncher/.MainActivity` |
| Live TV | `org.droidtv.zapster/.playtv.activity.PlayTvActivity` |

This is the same QM163E/QM16XE_U firmware family as the existing
[`philips-49pus6561`](../../examples/philips-49pus6561/debloat.txt) example. It is not the later
TPM171E platform used by the 55PUS7503 and 65PUS8102 lists.

## What was disabled

Every change used `pm disable-user --user 0`; nothing was uninstalled. The final disabled state, read
back after the reboot, contained these ten packages:

```
org.droidtv.tv.frameworklogger
org.droidtv.nettvbrowser
org.droidtv.contentexplorer
org.droidtv.nettvrecommender
org.droidtv.nettvapps
org.droidtv.nettv.market
org.droidtv.nettvregistration
com.google.android.feedback
com.google.android.tv.bugreportsender
com.google.android.tvlauncher
```

The stock launcher was disabled only after the replacement launcher had opened and was confirmed as the
focused Home activity. `org.droidtv.usagelogger` was left enabled because the tool reported a possible
TV-input-service dependency. The TV/input services, remote and Bluetooth services, keyboard, Play Store,
Play Services, Chromecast, Assistant, firmware updater and installed streaming apps were kept enabled.

Unlike the shared QM163E list, this session disabled `org.droidtv.nettvbrowser` and
`org.droidtv.contentexplorer`. HbbTV/red-button content and USB-media playback were not tested afterwards,
which is why neither package belongs in a reusable list from this report.

## Home and validation

The reference launcher was built from source and made the default Home. The owner selected Home at power-on.
This Philips firmware resumes `org.droidtv.zapster` briefly after a cold boot; the launcher's enabled
Home helper subsequently brought Home to the foreground. A no-keypress reboot check observed the launcher
as the resumed activity after that transition.

The checked launcher configuration used `org.droidtv.zapster` as Live TV and listed HDMI 1 through 4/MHL,
SCART, component and Live TV in Inputs. The owner confirmed the physical remote's Home, held Volume Up,
Input, Settings and dedicated app buttons. The final Home screenshot showed the launcher normally rendered
with no clipping. Live TV screenshots were protected/empty, so its activity state was checked instead.

After the final reboot, `tools/adb-closed.py <tv ip>` reported that TCP 5555 refused the connection. USB
Debugging was turned off and ADB authorizations were revoked.

## Limits

This report records a successful same-session result only. It does not establish standby/wake behavior,
HDMI playback with a connected source, tuner playback, HbbTV/red-button content, USB media playback or
several-day package-state retention. Keep or re-enable the packages above if any of those functions are
needed.
