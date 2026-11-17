# HAAM HM4K75S11TND (SCBC R4)

Tested on one retail **HM4K75S11TND**, Android TV 11 / API 30, Realtek
`rtd2851m`, build `RTT7.221026.001/3172`, software `V8-T851MGL-0001103`.
Android reports the model as `R4`; that name alone does not identify a matching
retail model or firmware. This is not a firmware compatibility recommendation.

The list disables **5 packages**: stock Home recommendations, Google feedback,
two retail demonstration apps and TV Booster. All five were disabled on October
20, 2026 and remained disabled in subsequent checks through November 2, 2026.
These were repeated checks on one owner's TV, not continuous automated monitoring.
There is no performance claim for this five-package subset.

## Apply and undo

After the repository's backup and connection setup, run from its root:

```sh
TV_HOST=<tv ip> tools/disable.sh examples/haam-hm4k75s11tnd/debloat.txt
TV_HOST=<tv ip> tools/restore.sh examples/haam-hm4k75s11tnd/debloat.txt
```

PowerShell equivalents:

```powershell
tools\disable.ps1 -TvHost '<tv ip>' examples\haam-hm4k75s11tnd\debloat.txt
tools\restore.ps1 -TvHost '<tv ip>' examples\haam-hm4k75s11tnd\debloat.txt
```

Use the same list and retain the tools' recovery record. No package is uninstalled.

## What was checked and what remains enabled

The owner confirmed satellite Live TV picture and sound and the physical Home
button repeatedly after launcher changes and restarts. The latest check on
November 2 also confirmed the launcher's hold-OK app menu. Earlier checks verified
HDMI picture, USB storage and Bluetooth mouse operation. These results describe
the complete customized setup; they do not isolate the effect of each disabled app.

The tuner/player and input services, Settings, remote and Bluetooth services,
keyboard, streaming/IPTV apps, casting, voice, Play Store/Services and firmware
updater were kept. Keeping a package is not a claim that every one of its features
was retested in the latest session.

The owner's customized launcher also replaced the stock Home, and the private
setup disabled `com.google.android.tvlauncher` and
`com.realtek.leanbacklauncher.partnercustomizer`. Those two packages are deliberately
absent here: launcher handover belongs to the guarded installation procedure, and
this session did not establish that removing the partner customizer is necessary
or safe for other owners' input shortcuts. No package in this shared list had to
be re-enabled during the recorded checks.

## Satellite routing

The separate [HAAM satellite input notes](../../docs/devices/haam-r4.md) document
the verified channel-collection route and the exact input ID for this firmware.
Use that only after checking your own registered input and connected dish. This
cleanup list does not scan, reorder channels, change dish settings or replace the
tuner service.
