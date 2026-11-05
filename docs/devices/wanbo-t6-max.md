# Wanbo T6 Max: single-session launcher and package notes

Observed on one projector in a single session, not several days of ordinary use.
This is a device report, **not a validated debloat list** for this model or other
Wanbo firmware. The Wanbo X5 Pro example uses a different Google TV platform;
do not apply its package list to this unit by analogy.

| Field | Observed value |
|---|---|
| Retail model | Wanbo T6 Max; Android reports `T6 Max` |
| Android / SDK | `9` / `28` |
| Firmware incremental build | `20220419` |
| Reference launcher revision tested | `3e34f40f42728284bbb3d4d7bea57c3f504c3b31` |

The reference launcher was built with device-specific resource values, without
changes to its Java code. Its release APK, with minSdk 26 and targetSdk 34,
installed successfully using `pm install -r` in the TV shell.

## Connection: check the advertised port

Network ADB was advertised through `_adb._tcp` on **3333**, while 5555 refused
connections. Use the port actually advertised by the projector rather than
assuming 5555; the repo tools accept `TV_HOST` and `TV_PORT=3333`.
The connection used Wi-Fi. USB ADB and USB peripheral power recovery were not
verified.

## Home: keep the stock launcher and setupwraith enabled

These HOME candidates were observed:

| Component | Priority |
|---|---:|
| `com.google.android.tvlauncher/.MainActivity` | 2 |
| `com.google.android.tungsten.setupwraith/.RecoveryActivity` | 1 |
| `com.gathin.tvhome/.MainActivity` | 0 |

`cmd package set-home-activity` printed `Success`, but HOME still resolved to the
stock launcher. After disabling setupwraith and the stock launcher, the resolver
named the replacement, but an injected Home key did not leave Settings or the
HDMI source activity. Both packages were re-enabled. This is an injected-key
observation, **not a measurement of the physical remote**.

The working automated path kept **both packages enabled** and used the existing
HomeKeyService stock-home window-change redirect. The tested overrides in
`launcher/app/src/main/res/values/config.xml` were:

```xml
<bool name="key_filter">false</bool>
<string name="stock_home_redirect" translatable="false">com.google.android.tvlauncher</string>
```

After installing and explicitly starting the replacement launcher, its Home
button accessibility service was enabled; InputKeyService stayed disabled.
Keep stock Home enabled for this approach rather than accepting an installer's
stock-launcher-disable step. A successful installer or preferred-Home command
alone is not proof that this redirect is enabled, bound or working.

After a reboot, the enabled `AccessibilityServiceInfo` for
`com.gathin.tvhome/.HomeKeyService` reported `flags=0x0`, no
`FLAG_REQUEST_FILTER_KEY_EVENTS`, and event type 32
(`TYPE_WINDOW_STATE_CHANGED`). Capability 8 means it *can* request key filtering,
not that filtering is active. This does **not** establish that held-volume
repeat or other real remote buttons work on this firmware.

With that observer bound, injected Home returned to the new launcher from
Settings, a streaming app and the HDMI source activity. Returning from the
streaming app retained its dock selection; returning from HDMI selected the
first dock tile. Physical Home, app buttons, Input, Settings, mic and held-volume
repeat remain unverified; do not enable filtering/remaps to guess at them.

Restoration was exercised too: removing only this Home observer from the enabled
accessibility services, re-enabling the session's package changes, restoring
stock Home and explicitly starting `com.google.android.tvlauncher/.MainActivity`
rendered the original launcher. Turn the observer off before checking stock
Home, or its window-change redirect can immediately send the screen back.
Preserve any unrelated accessibility services when doing so.

## Inputs: player package versus hardware provider

The tested Live TV resource was:

```xml
<string name="live_tv_package" translatable="false">com.android.tv</string>
```

Keep `com.android.tv`, `com.droidlogic.tvinput` and
`com.droidlogic.tv.settings` enabled. The hardware provider is
`com.droidlogic.tvinput`, not the player package. The standard channel MIME type
`vnd.android.cursor.item/channel` resolved to `com.android.tv/.MainActivity`;
selecting HDMI opened `com.droidlogic.tv.settings/.TvSourceActivity`.
The Inputs dropdown exposed HDMI, AV and Live TV entries.

Opening that activity does not prove an HDMI signal or picture/audio playback.
An HDMI screenshot was empty; check the resumed activity as described in
[Troubleshooting](../TROUBLESHOOTING.md), then verify the actual picture on the
projector. HDMI/AV signal, tuner reception and channel scanning were not tested.
The factory package `com.luxcine.factorytest` was not opened or disabled; hiding
its tile is not the same as changing its package state.

## Package changes observed, not a general disable list

Only these three packages were disabled with `pm disable-user --user 0` in the
final configuration:

| Package | Role on this firmware |
|---|---|
| `com.google.android.tvrecommendations` | Google TV recommendation service |
| `com.google.android.leanbacklauncher.recommendations` | Older Leanback recommendation provider |
| `com.overseas.store.appstore` | Vendor App Store |

The baseline had no disabled packages. All three remained disabled after a
reboot independently verified by a changed kernel boot ID and
`sys.boot_completed=1`; none needed disabling again. All original packages
remained installed. The restoration exercise returned to an empty disabled list
before the final three-package configuration was reapplied.

No standalone manufacturer telemetry package was confirmed. Streaming apps,
voice, casting, firmware updates, input services, projector controls, keyboards,
remote/Bluetooth services, Play Store and Play services were retained. Their
package state is not a functional test of each service. Nothing was uninstalled,
rooted or flashed. DNS and display size/density were unchanged.

## Validation limits and closing maintenance access

Screenshots pulled from the projector were inspected. Injected D-pad checks
covered one-row Up/Down movement, top-bar boundaries, Inputs and Settings.
The new launcher appeared after the verified functionality reboot without an
explicit launch, and the observer remained bound without key filtering.

There was no physical remote or projected-picture confirmation. Streaming
playback, casting, CEC, standby/wake and several-day stability were not verified.
Do not promote these observations into a several-day `examples/` list yet.

At the end, debugging was disabled with Android's standard shell settings
client. Both **3333 and 5555** explicitly refused connections while the projector
was reachable, including a later check beyond the boot window. For another
unit, close debugging and check both the advertised and standard ports:

```sh
tools/venv/bin/python tools/adb-closed.py "<tv ip>" 3333
tools/venv/bin/python tools/adb-closed.py "<tv ip>" 5555
```

A timeout alone is not equivalent to a refusal; first establish that the
projector is on and connected. The final shutdown included a reboot command,
but a new boot ID and the post-closure screen could not be read once ADB was
off. Intermittent Wi-Fi loss is not independent proof of a reboot. The earlier
functionality reboot, not the final post-closure state, is the independently
verified reboot in this report.
