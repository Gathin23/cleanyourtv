# Noblex DR55X9500 (Skyworth SW6H, Google TV home)

A 55" QLED 4K set for Argentina built by Skyworth: `ro.product.model` "Smart TV 4K", product `SW6H_Noblex_NF`,
device and board `SW6H`, MediaTek `m7332`, Android TV 11 (API 30) with the Google TV home
(`com.google.android.apps.tv.launcherx`), firmware `RTT2.220103.001`. The board and firmware match
`examples/prismplus-p4ka-sw6h/debloat.txt`, which this list started from.

## The list

The debloat list disables 22 packages in five batches, with a Home screenshot after each one; launcherx
and setupwraith follow at the Home handover. Nothing was re-enabled. Three reboots later `pm list packages -d` was unchanged: unlike
the PRISM+ unit, this firmware did not bring launcherx back at boot.

## setupwraith and the Wi-Fi

`dumpsys wifi | grep -o 'cname=[^ ]*'` showed the saved network owned by
`com.google.android.tungsten.setupwraith`. Forgetting and re-joining the network in Settings moved it to
`com.android.tv.settings`, but two older networks saved during first setup still belonged to setupwraith,
so `tools/disable.sh` kept refusing. Once those were forgotten too (`cmd wifi forget-network <id>`),
setupwraith went off with no override and the TV stayed on Wi-Fi. Chromecast (tab and screen casting from
Chrome) kept working with setupwraith disabled.

## Remote buttons

Read on the real remote with a temporary log line in the launcher's key helper; `getevent` reports most of
them as `KEY_UNKNOWN` (scancode 240, shared) and the window manager logs only some of them, so match by
keycode, not scancode:

| Button | keyCode | HID usage | With launcherx disabled |
|---|---|---|---|
| Gear (settings) | 83 | 0x0c009f | dead |
| Profile (person icon) | 288 | 0x0c0229 | dead (launcherx's profile chooser) |
| Apps (fourth app row button) | 284 | 0x0c01a2 | dead |
| Input | 178 | 0x0c01bb | works (`com.smartdevice.dailyshortcut` input picker) |
| Netflix | 191 | 0x0c00a9 | works |
| Prime Video | 409 | 0x0c00d6 | works |
| YouTube | 291 | 0x0c00ab | works |

`config.json` here sets the gear (`settings_key_keycodes` `[83]`), which needs `HOME_KEY_SERVICE=1` on
`launcher/build-and-install.sh`. The tested set also remapped the others with `app_keys` and
`INPUT_KEY_SERVICE=1`: `284:input:com.mediatek.tvinput/.hdmi.HDMIInputService/HW5` (HDMI 2), `288` and
`191`/`409` to other apps, and `178:com.gathin.tvhome/.InputsRequestActivity` with `handle_view_inputs`
true for the launcher's Inputs menu, keeping the vendor picker on
`hold:178:com.smartdevice.dailyshortcut/.ui.MainActivity`. The profile chooser lives inside launcherx and
cannot be reached with it disabled: the TV Settings account page hangs on a spinner without it.

**The cost:** with either helper bound, a held volume key steps once per press, the trade described in
`docs/devices/tcl-55p71b-g09-key-repeat.md`. `consume_volume_keys` cannot help here: the helper never
received the volume keys. The owner kept the buttons.

## HDMI

Four HDMI inputs, all with CEC; only HDMI 1 has ARC. Input ids: HDMI 1-4 are `HW4`-`HW7` under
`com.mediatek.tvinput/.hdmi.HDMIInputService`. The firmware does not report which ports are HDMI 2.0 or
2.1; there is one global EDID setting (`tv_input_hdmi_edid_version`).

## Power

`persist.sys.fast_standby` is 1 out of the box: the power button only suspends. The owner turned off the
quick start option ("Inicio rápido") in Settings for a real power-off. After each reboot the set came up
straight in this launcher with the key helpers bound.
