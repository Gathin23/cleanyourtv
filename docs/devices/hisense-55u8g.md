# Hisense 55U8G (Q0506): identifiers and original Inputs handler

One owner-identified US Hisense 55U8G was inspected in October 2026. This adds
retail/vendor identifiers to the related platform report; it is not a
several-day package-disable list.

| Identifier | Observed value |
|---|---|
| Retail model | Hisense 55U8G |
| ADB product model / device | `SmartTV 4K FFM` / `huangshan` |
| Android | Android TV 10 / SDK 29; build `QTG3.200902.001` |
| System Message Software / Extended Version | `V0000.01.00U.Q0506` / `D01` |
| System Message service-code prefix | `55U6FUA`, not the ADB product model |
| Inspected stock Home | `com.google.android.tvlauncher`, `7.7.15-968255986-f`, code `1010943512` |

The vendor software version and Android build ID are separate fields. Read the
former in normal System Message settings, not a factory/service menu; do not
publish the full service code.

The original Home was `com.google.android.tvlauncher/.MainActivity`. The action
`com.android.tv.action.VIEW_INPUTS` resolved to
`com.google.android.tvlauncher/.inputs.InputsPanelActivity`. Stock Home remained
enabled/default in the inspected configuration. This corroborates the existing
profile's Input-provider dependency; it does not establish that another launcher
cannot supply its own handler.

An ordinary preferred-Home change did not keep the physical Home button on the
replacement during earlier locally modified v2.6 trials. This is a scoped
observation, not a reproduction against current upstream.

The [huangshan SmartTV 4K FFM / US U9DG profile](../../examples/hisense-smarttv-4k-ffm/debloat.txt)
shares platform identifiers. Its complete disable list and regional preloads
were not validated on this 55U8G. Keep its existing SmartSense/AIoT warning
attributed to that report; no recovery failure was reproduced here.

On this unit, package inspection associated `com.hisense.remoteupgrade` with
Bluetooth remote-connection integration and `com.vt.tv.usbota` with a local-file
upgrade service. These observations do not establish interchangeable TV-network
updater roles or justify disabling either package.
