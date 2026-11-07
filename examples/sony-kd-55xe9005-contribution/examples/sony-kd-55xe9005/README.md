# Sony BRAVIA KD-55XE9005 — Android TV 9

Tested on a Sony BRAVIA KD-55XE9005, platform `BRAVIA_ATV2_EU`, Android TV 9 (SDK 28), build `PTT1.190515.001.S54/672201`, using Windows 11 and ADB over Wi-Fi. CleanYourTV 3.4 was installed with the Aurora theme.

## Verified changes

The four packages in `debloat.txt` were disabled with `pm disable-user --user 0`, not uninstalled. All remained disabled after reboot. The stock launcher is `com.google.android.tvlauncher`; the Live TV application is `com.sony.dtv.tvx` and is kept.

Setting `com.gathin.tvhome/.MainActivity` with `cmd package set-home-activity` alone did not change the HOME remote-button behaviour: Android still resolved and opened the Google launcher. The official Windows `launcher/build-and-install.ps1` script, using `-StockLauncher com.google.android.tvlauncher`, successfully disabled the old launcher and selected CleanYourTV. This persisted across reboot.

The owner checked Live TV, EPG, HbbTV, Netflix, Chromecast, Google Assistant/voice, HDMI inputs, BRAVIA Sync/HDMI-CEC, remote HOME and Sony settings after the changes. No regression was reported in these checks. This does **not** establish multi-day reliability; confirm stability over several days before opening a pull request.

## Restore

From the repository root on Linux/macOS (once ADB is enabled and authorized):

```sh
TV_HOST=<tv-ip> tools/restore.sh examples/sony-kd-55xe9005/debloat.txt com.google.android.tvlauncher
```

On Windows use the repository's `tools/restore.ps1` according to its parameter documentation. Re-enable debugging only for the duration of maintenance and verify that ADB port 5555 is closed afterward.

## Aurora visual feedback

The Aurora wallpaper showed noticeable banding/grain in dark gradients on the 55-inch TV. This is cosmetic feedback, not a measured launcher performance regression or a proven JPEG root cause. A separate theme issue or PR with a real-TV screenshot would be appropriate.
