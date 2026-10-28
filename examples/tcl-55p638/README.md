# TCL 55P638: Live TV in Inputs

On this BeyondTV4 set (Android TV 11, build `RP1A.200622.001 release-keys`),
the tuner app is `com.tcl.tv`, with launch activity `.TVActivity`. The launcher's
built-in `live_tv_package` points to `com.mediatek.wwtv.tvcenter`, so Live TV is
missing from Inputs even though `com.tcl.tv` and `com.tcl.tvinput` are enabled.

[config.json](config.json) sets `live_tv_package` to `com.tcl.tv`. From the repository root,
apply it to an installed launcher with:

```sh
TV_HOST=<tv-ip> tools/theme.sh examples/tcl-55p638
```

This replaces the current theme configuration and wallpapers. If you already use a custom
theme, add `"live_tv_package": "com.tcl.tv"` to that theme's `config.json` and apply it instead.

Verified on 24 October 2026: ADB confirmed the tuner service and the Live TV launch activity;
after applying this setting and restarting the launcher, the owner confirmed that Live TV
appears in Inputs and opens correctly. No package needs to be re-enabled or disabled for this fix.
