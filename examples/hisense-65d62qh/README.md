# Hisense 65D62QH (China, JuUI 9.0.10.845)

**Debloat only.** This list leaves the stock launcher (`com.jamdeo.tv.vod`, 聚好看) enabled and does
not replace Home; every package below is disabled with `pm disable-user`, none is uninstalled.

| Field | Value |
|---|---|
| Retail model | Hisense 65D62QH, 65", China market |
| UI | JuUI 9.0.10.845 |
| Android model / device | `VIDAA_TV` / `MT9653` |
| Android / build | 11 (API 30) / `RP1A.200720.011` |
| GMS | none (no Play Store, no Google account) |

Same platform and build as `../vidda-mt9653/` and `../hisense-e5q-pro/`; this list was derived from
theirs and then re-checked package by package against this set's own `pm list packages`
(131 packages, nothing disabled at the factory).

- `debloat.txt` — five batches, each followed by a cold reboot, with the input panel switching HDMI,
  the remote, Wi-Fi, Bluetooth pairing and the IME checked before the next batch went on. 26 packages
  stick. `com.android.calendar`, `.deskclock` and `.music` are re-enabled by the firmware at every
  boot, so they are noted in the list instead of disabled again after each one.
- `config.json` — the vendor source panel (`dumpsys tv_input` is empty on this firmware, so there is
  no input for the launcher's Inputs pill to list), Live TV, and the tiles that should stay off Home.

Applied on 2026-10-22 and in everyday use since, always with the stock Home in place: eight cold
boots in total between the batches and afterwards, then a full day of ordinary viewing. Normal boot
every time, no crash; HDMI switching, casting, the remote and the IME all still working.

```bash
TV_HOST=<tv ip> tools/disable.sh examples/hisense-65d62qh/debloat.txt
TV_HOST=<tv ip> tools/restore.sh examples/hisense-65d62qh/debloat.txt
TV_HOST=<tv ip> tools/theme.sh examples/hisense-65d62qh
```

`com.jamdeo.tv.vod` is deliberately **not** in the list: it is the stock Home, and with it disabled
this firmware starts no Home at all at boot and the remote's Home button stops responding. What that
looks like, and what it takes to replace Home on this set, is written down in
[`docs/devices/hisense-65d62qh.md`](../../docs/devices/hisense-65d62qh.md) for anyone weighing a
launcher swap on their own TV.
