# Sharp AQUOS 4T-C70CN1 / AQUOS-TVJ19 (tcmeruj, Android TV 10, Japan): Live TV is Sharp's own app

Owner-observed note for a Japan-market 70-inch AQUOS running Sharp's own MediaTek platform
(`jp.co.sharp.av.*`, `tcmeruj`), not the CVTE boards of the UK/EU AQUOS lists in `examples/`
(`sharp-aquos-tve19c` is `sindang`, `sharp-2k-eu` is `martin` — different vendors' boards again). It
runs this launcher as Home, so the one thing worth writing down first is the key that makes the Inputs
menu do its job here: this set's tuner app is `jp.co.sharp.av.android.aquostvapp`, and the
repository's default `live_tv_package` (`com.mediatek.wwtv.tvcenter`) names an app this firmware does
not ship.

This is **not a debloat list** and not a tested list of any kind. Packages have been disabled on this
unit, but not through the process the [TV list workflow](../workflows/tv-list.md) asks for, so nothing
below is offered as one.

**Provenance of the identity block.** The set was in standby for this pass — `adb connect` to its LAN
address timed out and the panel was not woken, because turning the TV on needs the owner's permission.
The build and property values below are from a full `getprop` dump taken on this same unit over ADB on
**2026-10-30 21:55 JST**, kept with the owner's change log; they were not re-read on 2026-11-01.
Anything that needed the screen or a live shell that day is listed under
[what was not tested](#what-was-not-tested) instead of guessed at.

| Field | Observed value |
|---|---|
| Retail model / Android model | `4T-C70CN1` on the set / `ro.product.model=AQUOS-TVJ19` |
| Product device / name / board | `tcmeruj` / `tcmeruj` / `balto`, `ro.board.platform=mt5893` (MediaTek MT5598) |
| Android / API | `10` / `29` |
| Build | `ro.build.display.id=55200Q QTG3.210505.001`, built `Tue Jul 25 02:30:49 JST 2023` |
| Fingerprint | `SHARP/tcmeruj/tcmeruj:10/QTG3.210505.001/20230725:user/release-keys` (user, release-keys) |
| Characteristics | `nosdcard,tv` — no SD slot, so there is no removable storage to move apps or media to |
| Application ABI | `armeabi-v7a` only (`abilist32=armeabi-v7a,armeabi`, `abilist64` empty): sideloads must be 32-bit |
| RAM | `MemTotal: 3027948 kB` (about 2.9 GB), `MemAvailable: 893844 kB` at capture |
| `/data` | `/dev/block/mmcblk0p37` 9,080,192 KB, 89 % used before the owner's changes |
| Density / framebuffer | `ro.sf.lcd_density=320`; captures from this set are 1920×1080 |
| Packages | 176 at capture (`pm list packages`), 52 of them `jp.co.sharp.*` |
| ADB | `service.adb.tcp.port=5555`, `ro.adb.secure=1` (key-authorized, not open) |

```text
# adb shell getprop (full dump), 2026-10-30 21:55 JST
[ro.product.model]: [AQUOS-TVJ19]
[ro.product.device]: [tcmeruj]
[ro.product.brand]: [SHARP]
[ro.build.version.release]: [10]
[ro.build.version.sdk]: [29]
[ro.build.display.id]: [55200Q QTG3.210505.001]
[ro.build.fingerprint]: [SHARP/tcmeruj/tcmeruj:10/QTG3.210505.001/20230725:user/release-keys]
[ro.build.characteristics]: [nosdcard,tv]
[ro.product.cpu.abilist]: [armeabi-v7a,armeabi]
[ro.product.cpu.abilist64]: []
[service.adb.tcp.port]: [5555]
[ro.adb.secure]: [1]
```

`abilist64` is empty: the userspace is 32-bit only, and that is the one value that decides whether an
APK will install.

## The tuner is Sharp's app, and `live_tv_package` has to say so

The launcher's default names MediaTek's TV centre:

```xml
<!-- launcher/app/src/main/res/values/config.xml, this repository -->
<string name="live_tv_package" translatable="false">com.mediatek.wwtv.tvcenter</string>
```

That package is **not installed on this set**. `pm list packages` on 2026-10-30 lists the MediaTek
pieces this firmware does keep — `com.mediatek.tvinput`, `com.mediatek.tunerservice`,
`com.mediatek.wwtv.mediaplayer`, `com.mediatek.tvinputservice.arbitratorservice` — and no
`com.mediatek.wwtv.tvcenter`. The tuner app is Sharp's:

```text
$ adb shell pm list packages | grep -E "aquostvapp|tvinput|tunerservice"
package:jp.co.sharp.av.android.aquostvapp
package:jp.co.sharp.av.android.dtvinputtif
package:jp.co.sharp.av.android.uhdtvinputtif
package:com.mediatek.tvinput
package:com.mediatek.tunerservice
```

`dtvinputtif` and `uhdtvinputtif` are the TV input framework providers behind the terrestrial and 4K
tuner rows, which is what the Inputs menu reads; `aquostvapp` is the app the Live TV row launches.
This unit's build sets the key to it (the checkout the APK for this set was built from):

```xml
<string name="live_tv_package" translatable="false">jp.co.sharp.av.android.aquostvapp</string>
```

The same key is set on the sibling [4T-C43DL1](../../examples/sharp-4t-c43dl1/config.json)
(`AQUOS-TVJ21-2`, Android TV 11) of the same Sharp platform, whose note records that the Inputs menu
then "lists HDMI 1-4 and 入力5 through the TV input framework and switches without extra config".

`live_tv_package` is also a theme key: a `config.json` pushed with `tools/theme.sh` overlays
`config.xml`, so a theme written for another family and carrying the MediaTek default wins over the
build's own value and takes the Live TV row away again. Worth checking on any theme pushed to a Sharp
set, since nothing on screen reports the substitution.

**What the default costs.** `liveTvIntent()` is the only path to the Live TV row, and it starts with the
package name:

```java
// launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java
private Intent liveTvIntent() {
    if (liveTvPkg.isEmpty()) return null;
    Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
    if (i != null || liveTvAction.isEmpty()) return i;
    Intent byAction = new Intent(liveTvAction).setPackage(liveTvPkg);
    return getPackageManager().resolveActivity(byAction, 0) != null ? byAction : null;
}
```

With a package that is not installed, both branches return null, so the Inputs menu simply has no Live
TV row. The Inputs pill itself survives as long as there is something else to list —
`updateInputsButton()` keeps it visible for a `live_tv_action`, a source panel, or any non-hidden
passthrough input from `TvInputManager`, and this set has HDMI ports — so the failure is quiet: an
Inputs menu that opens onto HDMI 1–4 and the aerial set, with the tuner missing from it. That degraded
state was **not** seen on this unit; the key was already corrected when the launcher was built for it.
A set whose tuner app also declares no `LEANBACK_LAUNCHER` entry needs `live_tv_action` as well, as the
[Changhong 58D4P](changhong-58d4p.md) note shows; whether `aquostvapp` declares one was not checked
here, because the key works and the resolve call was not run against the set.

## Network ADB

`service.adb.tcp.port=5555` is a property of this build and `ro.adb.secure=1`, so the first connection
from a computer needs the on-screen authorization. Port 5555 was open over the LAN through the
2026-10-30 session and was still listening after that evening's reboot, which is the only reboot
evidence there is for this set — and it comes from the session's own change record, kept outside this
repository, not from a check repeated for this note. On 2026-11-01 the set was in standby and
`adb connect` timed out, so **whether the port survives a cold boot on this unit is not established
here**.

```text
$ adb connect <tv ip>:5555
failed to connect to '<tv ip>:5555': Connection timed out    # 2026-11-01, set in standby
```

The control path on 6466 (Android TV Remote Service v2) is a separate service and was not exercised for
this note either.

## Inputs and the source list

`dumpsys tv_input` / `cmd tv_input` output was **not captured** on this unit, so this note does not
publish a source list. What the package inventory says is that the inputs come from the TV input
framework providers above (`dtvinputtif`, `uhdtvinputtif`, `com.mediatek.tvinput`) rather than from a
vendor source-panel service, which is why the launcher's Inputs menu works on this family at all — it
reads `TvInputManager`, so a set with the tuner correctly named needs no `source_panel_*` key. The
command to settle the list is:

```text
$ adb shell dumpsys tv_input
```

One consequence worth knowing before testing it: the Inputs pill is hidden outright when the list would
be empty (`updateInputsButton()`), so "no Inputs button" on a Sharp set is a symptom of a wrong
`live_tv_package` plus no HDMI, not of the pill being switched off.

## Module cards and the card prompt

Nothing about module cards was observed on this set in this pass; the mechanism is the repository's,
not Sharp's. With `module_cards` on (the default), an app that declares a
`com.gathin.tvhome.card` meta-data is offered **once** as a card and the owner answers Show or Hide on
the TV; a Hide is remembered, and the same card key later re-asks only if its `card_key` changes. A
card whose label or caption repeats a name the launcher already uses (or an input name such as HDMI) is
dropped rather than prompted for, and `logcat -s TvHomeCards` prints one line per rejection. The
contract is [`docs/specs/card.md`](../specs/card.md) and the short version is
[`docs/modules.md`](../modules.md).

## What was not tested

- **The live shell on 2026-11-01.** The set was in standby; `adb connect` timed out and it was not
  woken. Every current-state value in this note (package counts, disabled set, storage, IP) may have
  moved since the 2026-10-30 capture.
- **The Inputs list, Live TV launch and 入力切替.** `dumpsys tv_input` was not captured and the Inputs
  menu was not opened for this note, so the mapping between the tuner providers and the rows a viewer
  sees is not written down here.
- **The physical remote.** No key was surveyed, so this note claims nothing about which keys can or
  cannot be remapped through the launcher's `app_keys` mapping. Sharp's own keys (入力切替, 番組表,
  データ, ネット動画, 録画リスト) are the ones a remap would have to argue with; the key-code survey
  that would have answered it was planned for 2026-10-31 and its result is not recorded anywhere this
  note can cite.
- **Any debloat list, and the disabled packages.** The disabled set was not read on 2026-11-01, so it
  is not reproduced here; publishing a set that was never re-checked across reboots is what this
  repository's workflow exists to prevent.
- **Whether ADB survives a cold boot** (see above), voice/Assistant, casting, HDMI-CEC, the 4K tuner,
  recording and HDR. Casting is already absent on this unit at the source, so nothing here should be
  read as a statement about Cast on this model.

The set's serial number, MAC address and LAN address are left out on purpose; the LAN address above is
`<tv ip>` because it has been renumbered at least once since the set was first reached.
