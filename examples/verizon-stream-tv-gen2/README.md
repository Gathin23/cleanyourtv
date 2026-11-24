# Verizon Stream TV, 2nd generation (ARRIS VIP7802W, f354)

Android TV 14 (Google TV), build `UTT1.250115.001`, Broadcom BCM72174. A streaming box with no HDMI inputs.
The list is `debloat.txt`; the commands to apply and undo it are in its header.

## Wake from standby shows a Play Store "Item unavailable" page

With the stock launcher (`com.verizon.qlv.launcher`) disabled, `com.verizon.qlv.services` still tries to open it
when the box wakes. The launcher is not on the public Play Store, so the fallback shows "Item unavailable". Stop the
service from drawing over the new home. Run it once the stock launcher is off (running it earlier does no harm):

```
TV_HOST=<tv ip> tools/tv.py "appops set com.verizon.qlv.services SYSTEM_ALERT_WINDOW ignore"
```

Check it took:

```
TV_HOST=<tv ip> tools/tv.py "appops get com.verizon.qlv.services SYSTEM_ALERT_WINDOW"
```

It prints `SYSTEM_ALERT_WINDOW: ignore`. The setting is per package and survives reboots, and re-enabling the stock
launcher does not change it. After it, the box wakes straight to the new launcher. To undo it:

```
TV_HOST=<tv ip> tools/tv.py "appops set com.verizon.qlv.services SYSTEM_ALERT_WINDOW default"
```

## Kept

`com.verizon.qlv.content` is the Fios Guide and Live TV, and works as a dock tile. The remote services
(`com.arris.remoteservice`, `com.arris.globalkeyactions`, `com.arris.quickactions`), Wi-Fi services, firmware
updaters, Assistant and the Backdrop screensaver stay on. The Guide and Home buttons on the real remote work.

## Tested on

Verizon Stream TV 2nd gen, Android TV 14, build 14.06.15.08.17. AdGuard Private DNS works.
