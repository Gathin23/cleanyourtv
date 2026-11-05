# VISIO 32" (VISIO TV / Iris 2K TV)

- **Brand / Model**: VISIO 32" Smart Android TV (`ro.product.brand=VISIO`, `ro.product.model=VISIO TV`)
- **OEM / Board**: Skyworth ODM (`SW1` board, `shinagawa` device, `ro.board.platform=m5621`)
- **SoC**: MediaTek MT5621 / MT9638 family (quad-core ARMv7, 1 GB RAM, Mali GPU)
- **Display**: 720p HD (1280x720 @ 60Hz)
- **OS**: Android TV 11 (API 30, build `RTO9.231120.001`)

## Notes

- **Stock launcher**: `com.google.android.tvlauncher` (Leanback).
- **Live TV & Media**: Handled by MediaTek's `com.mediatek.wwtv.mediaplayer` and `com.mediatek.wwtv.tvcenter`. Kept enabled so HDMI inputs and TV tuner work normally.
- **Backlight Adjust**: `com.skyworth.backligthadjust` controls the hardware display backlight via remote; kept enabled.
- **ADB**: Enable Developer options by clicking Build Number 7 times, then turn on USB debugging.
- **Screenshot Quirk**: An adbd stdout wrapper outputs `Init wrapper sys mutex successful. Pid:xxxx` before raw PNG bytes when running `adb exec-out screencap -p`. Capture to `/sdcard/screencap.png` and pull via `adb pull` instead.
