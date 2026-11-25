# Vestel shandao (Android TV 14): Cecotec, Toshiba and Turbo-X

The Cecotec VQU10065S and the Toshiba 65QA7D63DG share [debloat.txt](debloat.txt). The Turbo-X TXV-AU4395SMT,
sold by Plaisio in Greece on the same board and the same build (`UKNV.260304.001`), has its own list,
[debloat-turbo-x-txv-au4395smt.txt](debloat-turbo-x-txv-au4395smt.txt). This page covers what differs on that
unit and the home screen its owner runs.

## The retail demo app and the Input key

`debloat.txt` disables `fusion.android.tv.demo`, the retail demo app, and the Cecotec owner reports the Input
button working without it. On the Turbo-X the same package declares a receiver for
`com.mstar.android.intent.action.TV_INPUT_BUTTON`, the action Vestel's menlo sets send for the Input button
([turbo-x-40as60t](../turbo-x-40as60t/README.md)), so its list keeps the app rather than test the button without
it. With it on, Input, Settings, Netflix and YouTube all work on the real remote after `launcher/` takes Home, with
no key helper installed. To see whether your unit declares the receiver:

```bash
TV_HOST=<tv ip> tools/tv.py 'dumpsys package fusion.android.tv.demo' | grep TV_INPUT_BUTTON
```

## Live TV first, for older viewers

The Turbo-X household watches broadcast TV first, so the owner puts the Live TV app in the first dock slot and
brings the TV back to Live TV whenever it comes on. In a theme's `config.json`:

```json
{
  "dock_apps": ["com.mediatek.wwtv.tvcenter", "gr.cosmote.cosmotetv.androidtv", "gr.antenna.ant1.screen",
                "com.netflix.ninja", "com.google.android.youtube.tv"],
  "hidden_apps": [],
  "boot_to_live_tv": true,
  "open_on_start_choice": true,
  "open_on_start_on_wake": true,
  "show_stats": true
}
```

- `hidden_apps` is emptied because the launcher's built-in list hides the Live TV app from the tiles.
- `boot_to_live_tv` opens Live TV on the first Home after a boot. The remote's power button is a standby with no
  boot, so `open_on_start_choice` with `open_on_start_on_wake` covers waking up: pick the Live TV app once on the
  TV (hold OK on Settings, Launcher settings, Open on start), and every screen-on opens it.
- `show_stats` adds the line under the clock. Apps cannot read `/proc/stat` on this firmware, so it shows memory
  and free storage without a CPU figure.

Push it with `tools/theme.sh` and the theme folder you keep it in.

## Inputs

The TV registers the tuner and three HDMI ports with the TV input framework and nothing else, so the launcher's
Inputs menu lists HDMI 1, HDMI 2, HDMI 3 and Live TV, with no AV row.
