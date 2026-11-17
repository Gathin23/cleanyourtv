# Hisense HiSmart 2K ATV4 (hamamatsucho), Android TV 11, India

| Field | Value |
|---|---|
| `ro.product.model` / device | `HiSmart 2K ATV4` / `hamamatsucho` (CVTE board) |
| Android / SDK | Android TV 11 / 30 |
| Firmware | `RTO6.250817.001` |
| RAM | 924 MB |
| Live TV app / stock launcher | `com.mediatek.wwtv.tvcenter` / `com.google.android.tvlauncher` |

Same device and firmware as [`docs/devices/hisense-hismart-2k-atv4-hamamatsucho.md`](../../docs/devices/hisense-hismart-2k-atv4-hamamatsucho.md),
which has the Home handover notes. Status and what was checked are in the header of `debloat.txt`.

- `debloat.txt`: telemetry (the ACR tracker), recommendation rows, CVTE partner storefronts, retail demo and
  a few preloads. Streaming apps and Google Assistant are commented out: uncomment the ones you don't use.
- `config.json`: the **Set-top box tile** below, plus the Live TV app for the Inputs menu.

```bash
TV_HOST=<tv ip> tools/disable.sh examples/hisense-hismart-2k-atv4-hamamatsucho/debloat.txt
TV_HOST=<tv ip> launcher/build-and-install.sh
TV_HOST=<tv ip> tools/theme.sh examples/hisense-hismart-2k-atv4-hamamatsucho
```

## The Set-top box tile: one press for older parents

*Idea by Samarth Jaiswal.* In India most TVs are watched through a cable or DTH set-top box (Airtel, Tata Play,
Jio and others) on an HDMI port. For many older people the hard part is getting there: switch on, find the
Input button, pick the right HDMI from a list. The same happens with older viewers everywhere a TV sits behind
a box.

`config.json` puts a large **Set-top box** tile first in the dock. Home opens with it already focused, so the
whole routine is: switch on the TV, press **OK**. The tile switches straight to the HDMI port through the TV
input framework, with no menu in between; **Home** brings the apps back.

![The Set-top box tile, first in the dock and focused](set-top-box-tile.jpg)

It uses keys the launcher already has, so it needs no rebuild:

| Key | Value | What it does |
|---|---|---|
| `dock_apps` | `com.mediatek.wwtv.tvcenter` first | the TV app becomes the first tile |
| `launch_actions` | `com.mediatek.wwtv.tvcenter=input:com.mediatek.tvinput/.hdmi.HDMIInputService/HW6` | the tile tunes HDMI 2 instead of opening Live TV |
| `app_labels` | `com.mediatek.wwtv.tvcenter=Set-top box` | the name under the tile and above the dock |
| `app_banners` | `com.mediatek.wwtv.tvcenter=banner_tv` | the bundled Live TV artwork |
| `hidden_apps` | `[]` | the TV app is hidden by default; this shows it |

**Your box on another port:** `HW6` is HDMI 2 on this set and `HW5` is HDMI 1. Check yours with
`tools/tv.py 'dumpsys tv_input'` (`hdmi_port=N` next to `id=` under `TvInputHardwareInfo`), change the number,
and push the folder again with `tools/theme.sh`. On other TVs the input service has another name; the
`input:` form is described at `launch_actions` in `launcher/app/src/main/res/values/config.xml`.

Checked on this set on 2026-10-30, pushed with `tools/theme.sh`: the tile sent
`content://android.media.tv/passthrough/com.mediatek.tvinput/.hdmi.HDMIInputService/HW6`, the TV tuned
`HDMI20000B` (HDMI 2, CEC physical address 2.0.0.0, where the set-top box answers), and Home came back with the
Home key. The Inputs pill still lists HDMI 1, HDMI 2 and Live TV as before. `tools/theme.sh --reset` removes it.
