# Xiaomi Fire TV (Fire OS 8.1.8.5)

Xiaomi Fire TV, Amazon model `AFTNE4E43F`, codename `ginzaeu`, Fire OS 8.1.8.5 on Android 11 (SDK 30), build
`RS8185.3885N`. Indian set with HDMI 1-3 and Composite inputs. Done over Wi-Fi on 2 November 2026 with adb.

| File | What it is |
|---|---|
| `debloat.txt` | The 38 packages that stayed disabled, grouped and commented |

All commands run from the repo root with the TV's IP in `TV_HOST`.

## What worked and what did not

**Debloat: yes.** Amazon ACR (which also reads HDMI input), the metrics, perf and log uploads, Xiaomi's own
analytics app (`com.miui.tv.analytics`), the Wi-Fi password sync, the advertising ID, sponsored rows and
recommendation rows, Fire TV Channels, Shopping, miniTV, the game hub and Luna, the remote tutorials, Silk and
its home-screen shortcuts, and the Amazon device-setup helpers are off. The list was applied twice, the second
time after a factory reset, and was still fully disabled after two reboots. Home, YouTube, HDMI switching,
Display Mirroring from an Android phone and Developer options all kept working.

**Kept:** Alexa voice, casting (DIAL, Miracast, AirPlay), Prime Video, Amazon Photos, Amazon Kids, firmware
updates, Live TV and the input stack.

**Refused:** the six packages in the header of `debloat.txt` are protected on this build and keep running.

**Home: not by debloat.** `com.amazon.tv.launcher` is protected and keeps HOME; the hero banner and Amazon's own
promo strips come from its feed and stay.

**ADB:** on this set network ADB stops listening after every reboot, even with ADB Debugging still on. Turn
ADB Debugging off and on again in Settings > Device & Software > Developer options to reconnect.

## Undo

```bash
TV_HOST=<tv ip> tools/restore.sh examples/xiaomi-fire-tv-aftne4e43f/debloat.txt com.amazon.tv.launcher
```
