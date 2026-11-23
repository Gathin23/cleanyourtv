# Turbo-X TXV-AU4395SMT (Vestel shandao, Android TV 14)

Sold by Plaisio in Greece, built by Vestel on the MediaTek `shandao` board (product `EMIR`) with UK-market
firmware (`UKNV.260304.001`), the same build as [`cecotec-shandao/`](../cecotec-shandao/). The debloat list is
in [debloat.txt](debloat.txt); this page covers what differs from that set and the home screen this one runs.

## The retail demo owns the Input key here

The Cecotec list disables `fusion.android.tv.demo`. On this unit `dumpsys package fusion.android.tv.demo` shows
the same package receiving `com.mstar.android.intent.action.TV_INPUT_BUTTON`, which is the remote's Input
button, as it does on the Vestel `menlo` sets ([`turbo-x-40as60t/`](../turbo-x-40as60t/),
[`jvc-android-tv-menlo/`](../jvc-android-tv-menlo/)). It is left enabled, and with it the Input button keeps
working after `launcher/` takes Home, with no key helper installed. Check that line on your own unit before
you copy either list:

```bash
TV_HOST=<tv ip> tools/tv.py 'dumpsys package fusion.android.tv.demo' | grep TV_INPUT_BUTTON
```

## Remote buttons after the stock launcher is gone

Checked on the real remote by the owner: Input, Settings, Netflix and YouTube all work with
`com.google.android.tvlauncher` disabled and neither `HOME_KEY_SERVICE` nor `INPUT_KEY_SERVICE` enabled. The
Prime Video button does nothing only because the list disables Prime Video; comment that line out to keep it.

## Live TV first, for a set shared with older viewers

The owner's household watches broadcast TV first, so [config.json](config.json) puts the Live TV app in the
first dock slot and brings the TV back to Live TV whenever it comes on:

- `dock_apps` starts with `com.mediatek.wwtv.tvcenter`, and `hidden_apps` is emptied, because the launcher's
  built-in list hides that app from the tiles.
- `boot_to_live_tv` opens Live TV on the first Home after a boot.
- The remote's power button puts this set into standby with no boot, so `boot_to_live_tv` alone leaves the
  TV on whatever was open. `open_on_start_choice` with `open_on_start_on_wake` covers that: pick the Live TV
  app once on the TV (hold OK on Settings, Launcher settings, Open on start), and every screen-on opens it.
  Checked with `input keyevent SLEEP` and `WAKEUP` from Home and from another app, then by the owner with the
  remote's power button.
- `show_stats` adds the line under the clock. Apps cannot read `/proc/stat` on this firmware, so it shows
  memory and free storage and leaves the CPU figure out.

Push it on its own, or copy its keys into a theme's `config.json` (the owner runs them inside
`themes/ambient-glass` with `wallpaper_dim` at 30, which keeps the clock and the round buttons readable on the
bright photos):

```bash
TV_HOST=<tv ip> tools/theme.sh examples/turbo-x-txv-au4395smt
```

## Inputs

The TV registers the tuner and three HDMI ports with the TV input framework and nothing else, so the
launcher's Inputs menu lists HDMI 1, HDMI 2, HDMI 3 and Live TV. There is no AV row to miss.
