# Xiaomi F Pro L55MB-FPEU — initial device report

Tested on 6 November 2026 in one session; no multi-day testing yet.

- Retail model: L55MB-FPEU (Xiaomi F Pro).
- ADB model: AFTBR92D74; device: ginzaeu.
- Fire OS 8.1.8.5, Android 11 / SDK 30; build RS8185.3885N.
- Connected over Wi-Fi; satellite tuner used occasionally.

## Packages confirmed disabled after reboot

```text
com.amazon.tv.acr
com.miui.tv.analytics
com.amazon.client.metrics.api
com.amazon.wirelessmetrics.service
com.amazon.perfcollection
com.amazon.perfc
com.amazon.dp.logger
com.amazon.ceviche
com.amazon.tv.developer.dataservice
com.amazon.neo.minerva.logcat
com.amazon.device.telemetry.emitter
com.amazon.wifilocker
com.amazon.tmm.tutorial
com.amazon.storm.lightning.tutorial
com.amazon.spiderpork
com.amazon.gamehub
com.amazon.hedwig
com.amazon.minitv.android.app
com.amazon.cloud9
com.amazon.tv.website_launcher
```

All 20 packages above remained disabled after reboot. Only `pm disable-user --user 0` was used; nothing was uninstalled, rooted or flashed. Optional apps were disabled by owner choice.

The owner reported normal use appeared to work after the final reboot. All ten packages originally listed as third-party apps remained enabled, including Plex, ARD, ZDF, RTL+, YouTube and Audible. Prime Video, Amazon Music, `com.amazon.mediatek.livetv`, firmware updates and the stock launcher remained enabled. Alexa and casting were retained. Dedicated HDMI, casting and satellite tests were not individually recorded.

## Packages that returned

These accepted disable-user initially but were enabled again after reboot on this unit:

- `com.amazon.kso.blackbird`
- `com.amazon.sneakpeek`
- `com.amazon.hybridadidservice`
- `com.amazon.media.recommendations`
- `com.amazon.shoptv.client`

The large hero advertisement remained on the stock Home screen. No third-party launcher was installed or made Home.

## ADB and recovery

Network ADB stopped listening after reboot. Toggling ADB debugging off/on restored access. At the end, debugging was switched off and a connection-refused check confirmed port 5555 closed.

Use the original CleanYourTV disable/restore tools with a list of the packages above and retain their recovery record. Stock Home is `com.amazon.tv.launcher`; see the repository README for apply and restore commands.

This report records one session, not several days of reliability. It belongs under `docs/devices` until longer testing is available. No private backups, network addresses, account data or screenshots are included.
