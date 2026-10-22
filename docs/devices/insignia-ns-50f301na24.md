# Insignia NS-50F301NA24: read-only inventory and debloat proposal

This is an **untested proposal, not an `examples/` debloat list**. On 2026-10-22, ADB was used only to read
device properties and installed package names. No packages were disabled, uninstalled, or otherwise changed.
The owner explicitly asked to preserve Amazon Home, System UI, HDMI inputs and the Live TV tuner.

## Identify the firmware

| Field | Observed value |
|---|---|
| Retail model | Insignia NS-50F301NA24 (owner-reported) |
| `ro.product.model` / `ro.product.device` | `AFTDCT31` / `duckie` |
| Fire OS / Android / SDK | Fire OS 7 / Android 9 / 28 |
| Build display | `PS7715.5585N` |
| `ro.adb.secure` | `1` |

The internal model and build match the [Insignia NS-55F301NA25 list](../../examples/insignia-ns55f301na25/debloat.txt),
but this is a different retail SKU and has not been tested over time. The full read-only inventory contained 260
packages and is kept locally in the ignored `work/installed-packages.txt`; it is not included in this commit.

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

Fire OS keeps Home for Amazon's launcher; do not attempt to replace or disable it. This note contains no apply
commands. Before any future package changes, review the exact package functions, record existing disabled states,
get the owner's approval, and test changes in small reversible batches with HDMI, tuner and Home left intact.

## Validation limits

The package inventory and properties were read over ADB. No package was changed, and no launcher, streaming,
voice, casting, HDMI picture, tuner reception, reboot behavior or multi-day use was tested. Do not promote this
proposal to `examples/` until the list has been approved, applied, and lived with for several days.
