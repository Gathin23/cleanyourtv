# Insignia NS-50F301NA24: inventory and debloat notes

This is a **device note, not a tested `examples/` debloat list**. On 2026-10-22, ADB was used to read device
properties and installed package names. On 2026-10-31, the owner approved three reversible disable batches. Thirteen
packages were disabled; one protected package was left enabled. Nothing was uninstalled. This was not multi-day
testing.

## Identify the firmware

| Field | Observed value |
|---|---|
| Retail model | Insignia NS-50F301NA24 (owner-reported) |
| `ro.product.model` / `ro.product.device` | `AFTDCT31` / `duckie` |
| Fire OS / Android / SDK | Fire OS 7 / Android 9 / 28 |
| Initial inventory build display | `PS7715.5585N` |
| `ro.adb.secure` | `1` |

The internal model and build match the [Insignia NS-55F301NA25 list](../../examples/insignia-ns55f301na25/debloat.txt),
but this is a different retail SKU and has not been tested over time. The full read-only inventory contained 260
packages and is kept locally in the ignored `work/installed-packages.txt`; it is not included here. On 2026-10-31,
the TV reported build `PS7717.5741N`.

## Conservative proposal

Every package below was present in the inventory. These are candidates for review only; this note is not
authorization to disable them. The telemetry and advertising descriptions follow the sibling Insignia list and
Fire OS 7 notes; functions on this retail model have not been verified.

Telemetry and advertising measurement:

- `com.amazon.tv.acr` — automatic content recognition.
- `com.amazon.client.metrics` and `com.amazon.client.metrics.api` — client metrics.
- `com.amazon.dp.logger` — Amazon diagnostic logging.
- `com.amazon.perfcollection` and `com.amazon.perfc` — performance collection.
- `com.amazon.tv.fw.metrics` and `com.amazon.wirelessmetrics.service` — firmware and wireless metrics.
- `com.amazon.hybridadidservice` — advertising ID service.

Promotions and recommendations:

- `com.amazon.media.recommendations` — stock-home recommendations.
- `com.amazon.tv.democontent.provider` — retail/demo content.
- `com.amazon.sneakpeek` — promoted previews.

Only if the owner does not use Amazon Shopping:

- `com.amazon.shoptv.client`
- `com.amazon.shoptv.firetv.client`

Do not add `com.amazon.device.metrics` or `com.amazon.ftvads.deeplinking` to a proposed list: Fire OS 7 notes on
related sets report them as protected. `com.amazon.device.logmanager` and `com.amazon.device.crashmanager` are
also left out to preserve diagnostics.

## Packages to keep enabled

The proposal excludes these installed packages and functions:

- Amazon Home: `com.amazon.tv.launcher` and `com.amazon.firehomestarter`.
- System UI: `com.android.systemui`.
- Live TV, tuner and inputs: `com.amazon.tv.livetv`, `com.amazon.mediatek.livetv`, `com.amazon.tv.channelscan`,
  `com.amazon.franktvinput`, `com.mediatek.tvinput`, `com.mediatek.tvinputservice.arbitratorservice` and
  `com.amazon.tv.settings.v2`.
- Remote and Bluetooth services are not candidates; remote keyboard `com.amazon.tv.ime` stays enabled.
- Streaming apps, Alexa, casting and firmware updates, including `com.amazon.device.software.ota`,
  `com.amazon.device.software.ota.override` and `com.amazon.tv.forcedotaupdater.v2`.

Keep Amazon Home enabled on this Fire TV. The owner later asked to use the custom launcher as Home.
`cmd package set-home-activity` returned
`Success`, but Home continued to resolve to Amazon's `com.amazon.tv.launcher/.ui.HomeActivity_vNext`. Amazon Home
was left enabled and set back as the explicit default. The custom launcher was installed as a manually launched
app; no takeover workaround was used.

## Approved disable batches

The original enabled/disabled states were recorded locally before the changes. Each package was handled with
`pm disable-user --user 0`; none was uninstalled.

Batch 1:

- Disabled: `com.amazon.tv.acr`, `com.amazon.client.metrics.api`, `com.amazon.dp.logger`,
  `com.amazon.perfcollection`, `com.amazon.perfc`, `com.amazon.tv.fw.metrics`,
  `com.amazon.wirelessmetrics.service` and `com.amazon.hybridadidservice`.
- Left enabled: `com.amazon.client.metrics`; Fire OS refused the disable as protected.

Batch 2:

- Disabled: `com.amazon.sneakpeek`, `com.amazon.tv.democontent.provider`, `com.amazon.shoptv.client`
  and `com.amazon.shoptv.firetv.client`.

Batch 3:

- Disabled: `com.amazon.media.recommendations`.

After the batches and a restart, the owner reported that Home, a streaming app, HDMI, Live TV and restart worked.
Alexa was not tested. The 13 disabled packages were still reported disabled in a later ADB check. This is a
single-session report, not multi-day validation.

## Validation limits

The inventory and package states were read over ADB. The owner reported basic Home, streaming, HDMI, Live TV and
restart checks; voice, casting, HDMI picture quality, tuner reception over time and multi-day use were not
independently verified. Keep this as a device note until the list has been lived with for several days and the
results are confirmed.
