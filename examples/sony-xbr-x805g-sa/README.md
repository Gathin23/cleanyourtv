# Sony BRAVIA XBR-x805G (BRAVIA_ATV3_4K_SA)

Android TV 9 on MediaTek MT5891, Latin America product `BRAVIA_ATV3_4K_SA`. Same firmware build
number as [`sony-xbr-65x900f`](../sony-xbr-65x900f/) (UC), different regional image.

The debloat list disables 57 packages. Apply and undo:

```bash
TV_HOST=<tv ip> tools/disable.sh examples/sony-xbr-x805g-sa/debloat.txt
TV_HOST=<tv ip> tools/restore.sh examples/sony-xbr-x805g-sa/debloat.txt
```

Stock Home is `com.google.android.tvlauncher` (priority 2). Install the launcher with
`launcher/build-and-install.sh`, which disables that package last. After the first Home switch,
reboot once before relying on HDMI: this board can leave the TV HAL without a surface until then.

`config.json` hides Sony's Live TV app and program guide, sets `live_tv_package` to
`com.sony.dtv.tvx`, and turns on `inputs_connected_only` (per-port status is real on this firmware).
