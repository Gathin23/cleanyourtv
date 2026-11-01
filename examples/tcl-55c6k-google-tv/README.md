# TCL 55C6K (G08, Google TV 12), European firmware

TCL 55C6K QD-Mini LED 55" set, model "Smart TV Pro", product `G08_4K_GB`, firmware `STT2.221228.001 AS35` (`V8-T653T01-LF1V531`, `TCL-EU-MT9653-S1`), MediaTek MT9653 (`mt5893`), Android 12.

| File | What it is |
|---|---|
| `debloat.txt` | The 33 packages that were disabled, grouped and commented |

## What was tested

Tested on a live unit on Wi-Fi on 1 November 2026.
- All 33 packages disabled (`pm list packages -d` verified).
- Stock launcher `com.google.android.apps.tv.launcherx` replaced with the reference launcher `com.gathin.tvhome`.
- Wi-Fi saved during initial setup belonged to `setupwraith`: forgotten and re-joined in Settings before `setupwraith` was disabled.
- Remote buttons verified on real remote: Input button opens the Inputs switcher, Settings button opens Settings, Back and D-pad navigate properly without needing accessibility key services.
- Live TV app configured to `com.tcl.tv` and accessible from the Inputs dropdown alongside HDMI 1-4.
- Kept streaming apps: YouTube, Netflix, Prime Video, and JioHotstar.

## Apply & Undo

Apply:

```bash
TV_HOST=<tv ip> tools/disable.sh examples/tcl-55c6k-google-tv/debloat.txt
```

Undo:

```bash
TV_HOST=<tv ip> tools/restore.sh examples/tcl-55c6k-google-tv/debloat.txt com.google.android.apps.tv.launcherx
```
