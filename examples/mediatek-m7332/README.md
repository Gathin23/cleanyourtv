# MediaTek m7332 Android TV board (Android 11, Chinese "RP" firmware)

An unbranded Chinese TV board: `ro.product.model` is `m7332`, `ro.product.brand` is `MediaTek`, and the
firmware is the vendor's "RP" build (`m7332_eu-userdebug 11 RP1A.200720.011`, test-keys). The vendor
apps are all `com.rp.*` and the stock launcher is `com.rp.launcher`, which is how you tell this
firmware apart from the other MediaTek boards in `examples/`. ~1.8 GB RAM, `ro.config.low_ram=true`,
32-bit, 1920x1080.

| File | What it is |
|---|---|
| `debloat.txt` | The 18 packages that were disabled, grouped and commented, with what to keep and why |

## Install

All commands run from the repo root.

1. Point Home at a launcher you like. The vendor launcher hides sideloaded apps and holds about 150 MB,
   so a lighter one is worth it; `launcher/build-and-install.sh` builds and installs the Clean Your TV
   launcher and does the handover itself. Projectivy (`com.spocky.projengmenu`, about 11 MB) also works here.
2. Disable the list:
   `TV_HOST=<tv ip> tools/disable.sh examples/mediatek-m7332/debloat.txt`
   Run it after step 1: the vendor launcher's line is only disabled once another launcher answers HOME.
3. Push a theme if you want one: `TV_HOST=<tv ip> tools/theme.sh themes/onyx`

Done when HOME shows your launcher and Settings still opens the vendor settings app.

## Notes

- **The cast hub.** `com.android.toofifi` with `com.mphotool.apiservice` is the AirPlay, Chromecast and
  DLNA receiver of a cheap Chinese board. Disabling both closes the LAN ports it holds open and stops
  phones casting to this TV; leave them enabled if you cast.
- **The crash loop.** `com.rp.uhd` (the vendor's 4K demo app) held a persistent service that
  `ClassNotFoundException`'d and was restarted every few hundred milliseconds. That alone kept a
  four-core box at a load average above 60 and ~150-220 MB of RAM, and no `am force-stop` cleared it:
  disabling the package is the only lever. Worth checking with
  `adb shell logcat -d | grep 'Scheduling restart of crashed service'` on any similar board.
- **adb root works** on this test-keys firmware. Nothing in this list needs it, and it should be
  switched off again (`adb unroot`) for everyday use.
- **The remote dongle.** Switching developer options on can leave a 2.4 GHz USB remote receiver
  enumerated but without `/dev/input` event nodes. That is a kernel-side wedge and needs the dongle
  moved to another USB port plus a power cycle at the wall; a soft reboot does not fix it. Have the
  Android TV Remote Control app on a phone ready before switching debugging on.
- **Sideloaded apps.** The vendor launcher only shows what it was shipped with; after the handover the
  new launcher's app library (or Settings -> Apps -> See all) is where sideloaded apps appear.
