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

**Provenance of the identity block.** The set did not answer for this pass: `adb connect` to its LAN
address timed out, ping lost 100 % of two packets, and 5555, 6466 and 6467 came back filtered, so it is
described here as being in standby — an inference from those three results, not a reading of the panel.
It was not woken and no command ran on it. The build and property values below are from a full `getprop`
dump taken on this same unit over ADB on **2026-10-30 21:55 JST**, kept with the owner's change log;
they were not re-read on 2026-11-01. Anything that needed the screen or a live shell that day is listed
under [what was not tested](#what-was-not-tested) instead of guessed at.

| Field | Observed value |
|---|---|
| Retail model / Android model | `4T-C70CN1` (owner's change record) / `ro.product.model=AQUOS-TVJ19` |
| Product device / name / board | `tcmeruj` / `tcmeruj` / `balto`, `ro.board.platform=mt5893` (MediaTek MT5598) |
| Android / API | `10` / `29` |
| Build | `ro.build.display.id=55200Q QTG3.210505.001`, built `Tue Jul 25 02:30:49 JST 2023` |
| Fingerprint | `SHARP/tcmeruj/tcmeruj:10/QTG3.210505.001/20230725:user/release-keys` (user, release-keys) |
| Characteristics | `nosdcard,tv` — the build declares no SD card slot; this says nothing about USB storage |
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

`abilist64` is empty: the userspace is 32-bit only, so an APK has to carry `armeabi-v7a` or be
universal. That is not the only thing that decides an install — density, `minSdk` and a matching
signature decide it too.

## The tuner is Sharp's app, and `live_tv_package` has to say so

The launcher's default names MediaTek's TV centre:

```xml
<!-- launcher/app/src/main/res/values/config.xml, this repository -->
<string name="live_tv_package" translatable="false">com.mediatek.wwtv.tvcenter</string>
```

That package is **not installed on this set**. The 2026-10-30 capture of `pm list packages` lists the
MediaTek pieces this firmware does keep — `com.mediatek.tvinput`, `com.mediatek.tunerservice`,
`com.mediatek.wwtv.mediaplayer`, `com.mediatek.tvinputservice.arbitratorservice` — and no
`com.mediatek.wwtv.tvcenter`. The tuner app is Sharp's:

```text
# these ten lines, in this order, out of 176: `pm list packages` on this set, 2026-10-30
package:jp.co.sharp.av.android.uhdtvinputtif
package:com.mediatek.tvinput
package:jp.co.sharp.av.android.aquostvapp
package:com.mediatek.backgrounddetection
package:com.mediatek.wwtv.mediaplayer
package:com.mediatek.tunerservice
package:com.mediatek.tvinputservice.arbitratorservice
package:com.mediatek.network
package:com.mediatek.tv.agent
package:jp.co.sharp.av.android.dtvinputtif
```

`dtvinputtif` and `uhdtvinputtif` are the TV input framework providers behind the terrestrial and 4K
tuner rows — an inference from the package names, since `dumpsys tv_input` never ran on this set — and
`aquostvapp` is the app the Live TV row launches.
This unit's build sets the key to it (the checkout the APK for this set was built from):

```xml
<!-- the owner's build checkout, 2026-10-30 -->
<string name="live_tv_package" translatable="false">jp.co.sharp.av.android.aquostvapp</string>
```

The same key is set on the sibling [4T-C43DL1](../../examples/sharp-4t-c43dl1/debloat.txt)
(`AQUOS-TVJ21-2`, Android TV 11) of the same Sharp platform, whose debloat list records that the Inputs
menu then "lists HDMI 1-4 and 入力5 through the TV input framework and switches without extra config".

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
TV row. The Inputs pill stays visible when `liveTvIntent() != null`, when `sourcePanelService` is set,
when the Coocaa source list is non-empty, or when any non-hidden passthrough input comes back from
`TvInputManager` (`MainActivity.java:3198`-`:3216`, `updateInputsButton()`); `live_tv_action` is not a
visibility condition, and this set has HDMI ports, so the failure is quiet: an Inputs menu that opens
onto the HDMI ports it finds and the aerial set, with the tuner missing from it. That degraded state was
**not** seen on this unit; the key was already corrected when the launcher was built for it.
A set whose tuner app also declares no `LEANBACK_LAUNCHER` entry needs `live_tv_action` as well, as the
[Changhong 58D4P](changhong-58d4p.md) note shows; whether `aquostvapp` declares one was not checked
here, because the key works and the resolve call was not run against the set.

## Network ADB

This build sets `service.adb.tcp.port=5555` (`ro.adb.secure=1`, so a new computer needs the on-screen OK); turn network debugging off when the session is done.

## Inputs and the source list

`dumpsys tv_input` / `cmd tv_input` output was **not captured** on this unit, so this note does not
publish a source list. What the package inventory says is that the inputs come from the TV input
framework providers above (`dtvinputtif`, `uhdtvinputtif`, `com.mediatek.tvinput`) rather than from a
vendor source-panel service, and the launcher reads `TvInputManager`, so a set with the tuner correctly
named should need no `source_panel_*` key — plausible for this family, not measured on this set. The
command to settle the list is:

```text
$ adb shell dumpsys tv_input
```

One consequence worth knowing before testing it: the Inputs pill is hidden outright when the list would
be empty (`updateInputsButton()`), so "no Inputs button" on a Sharp set is most often a wrong
`live_tv_package` with no HDMI source to fall back on; `show_inputs_pill=false` hides the pill at the
same place and looks identical (`MainActivity.java:3199`-`:3202`, default `true` at `config.xml:355`,
themable through `Config.java:586`), so check that key before blaming the tuner.

## Module cards and the card prompt

Nothing about module cards was observed on this set in this pass; the mechanism is the repository's,
not Sharp's. With `module_cards` on (the default), an app that declares a `com.gathin.tvhome.card`
meta-data on an activity that also carries a `MAIN` + `DOCK_CARD` filter is offered **once** as a card
and the owner answers Show or Hide on the TV; a Hide is remembered. The decision is keyed on package +
signer digest + folded label (`Cards.java:312`-`:317`, [`card.md`](../specs/card.md) §3-5), so it
survives an update that changes neither, and the same card re-asks when either the signer or the label
changes. The `DOCK_CARD` category is what discovery queries by, so a card without it is never seen at
all (`card.md:24`). Reserved names — the launcher's own UI names and the input names `HDMI*` and `AV` —
are enforced on the **label**, not the caption (`Cards.java:325`); captions are checked only for
length, characters, resource references and the words `verified`/`official` (`Cards.java:452`-`:459`),
and `logcat -s TvHomeCards` prints one line per rejection. The contract is
[`docs/specs/card.md`](../specs/card.md) and the short version is [`docs/modules.md`](../modules.md).

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
- **Whether ADB survives a cold boot**, voice/Assistant, casting, HDMI-CEC, the 4K tuner,
  recording and HDR. Casting is already absent on this unit at the source, so nothing here should be
  read as a statement about Cast on this model.

The set's serial number, MAC address and LAN address are left out on purpose; the LAN address above is
`<tv ip>` because it has been renumbered at least once since the set was first reached.
