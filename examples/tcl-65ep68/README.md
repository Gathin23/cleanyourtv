# TCL 65EP68

Android TV 11 (API 30), firmware `V8-R851T02-LF1V722` / build `AR06`. A Realtek RTD2851 set, the same
platform as `tcl-43ep660/`, `tcl-50ep680/` and `tcl-55ep640/`, on a later firmware build (AR06 rather
than AR04/AR05). `ro.product.model` and `ro.product.device` are both `BeyondTV`, so a `tools/tv.py
--probe` only names the platform; the marketing model comes off the set.

`config.json` here is what `tools/theme.sh` pushes (dock, hidden apps, Live TV, wallpaper colour):

```sh
TV_HOST=<tv ip> tools/theme.sh examples/tcl-65ep68
```

## Things worth knowing

- **`com.tcl.tv` must stay enabled.** It is the only app on this firmware that answers
  `android.intent.action.VIEW` with `vnd.android.cursor.item/channel`, which is what the launcher's
  Inputs menu sends to open an HDMI or AV port. Disable it and the ports still list but opening one
  does nothing. It is hidden from the grid instead, because the Inputs menu already covers it.
- **`com.tcl.tvinput` carries both inputs.** `.passthroughinput.TvPassThroughService` serves HDMI and
  AV, `.TDTVInputService` serves the tuner. There is no way to keep one and drop the other, and
  dropping the package takes the HDMI ports with it.
- **The stock launcher is `com.google.android.tvlauncher`** at Home priority 2. A vendor
  `FallbackHome` (`com.tcl.keycustomfunctionservice/.FallbackHome`) and
  `com.android.tv.settings/.system.FallbackHome` also claim HOME at negative priorities; both are
  placeholders, so `tools/disable.sh`'s Home guard still refuses to leave the set without a launcher.
- **`com.google.android.tungsten.setupwraith` owns the saved Wi-Fi network**, so the list does not
  touch it and `tools/disable.sh` would skip it anyway. Forget and re-join the network in Settings if
  you want it gone.
- **The firmware updater is kept, but there is nothing to fetch.** TCL's OTA hosts for this platform
  (`eu-update.cedock.com`, `na-update.cedock.com`, `as-update.cedock.com`) do not answer, and the
  community serves later builds through its own channels. A USB flash of a full IMG wipes `/data`, so
  the list and the launcher have to be applied again afterwards.
- **The remote's dedicated app buttons are global settings, not packages.** The firmware maps them
  with `CustomKey_*` entries (`KEYCODE_TCL_IQIYI`, `KEYCODE_TCL_PLUTOTV` and the like) that open a
  preloaded app or a Play Store link. Disabling those apps leaves the buttons doing nothing, which is
  what this list does.
- **Inputs seen over `dumpsys tv_input`:** HDMI 1-4 as `hdmi_port=1..4` and the tuner as
  `com.tcl.tvinput/.TDTVInputService`, the HDCP/audio routing living in `com.tcl.tvinput`.
