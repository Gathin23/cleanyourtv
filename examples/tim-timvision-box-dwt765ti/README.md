# TIM TIMVISION Box (Technicolor DWT765TI)

TIM's Android TV box for TIMVISION in Italy: Technicolor DWT765TI, codename `dwt765ti`, board `skipper_tim`
(Marvell Berlin), Android TV 8.0, firmware OTT1.201231.001 (6.0.5.0421). It has HDMI out only, so the
launcher's Inputs pill has nothing to switch to; the tested unit hides it with `show_inputs_pill` set to false.

## Same board as the UZW4010TIM

[`../tim-timvision-box-uzw4010tim/`](../tim-timvision-box-uzw4010tim/) is the same TIMVISION firmware line on
the same `skipper_tim` board (6.0.5.0412 there), under another product code. The two lists agree on what is
telemetry and what the box needs. This unit had a few more operator-pushed apps (Disney+, Apple TV, DAZN,
Mediaset Infinity) and no TIM smart-home or mirroring packages.

## Launcher

The stock Home is the TIMVISION app (`timvision.launcher`, HOME priority 2). With it disabled, the reference
launcher took Home at once and kept it across a reboot:

```bash
STOCK_LAUNCHER=timvision.launcher TV_HOST=<tv ip> launcher/build-and-install.sh
```

The remote's Home, Back and volume (held volume keeps repeating) worked without either key helper. The
TIMVISION and app buttons do nothing once their apps are disabled.

## Undo

```bash
TV_HOST=<tv ip> tools/restore.sh examples/tim-timvision-box-dwt765ti/debloat.txt timvision.launcher
```
