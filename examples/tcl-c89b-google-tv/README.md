# TCL C89B and 65C89K (G08, Google TV 14)

This folder covers two European G08 units on `G08_4K_GB` firmware
`UTT2.250416.001 AU02`: the original 85-inch C89B and a TCL 65C89K in France.

The 65C89K owner used CleanYourTV for several days through 2 November 2026.
Home remained the default launcher, and the owner used Sources, Live TV
and Ethernet. Game Bar and the hearing test remain enabled
on that unit, so their entries are comments in `debloat.txt`.

`config.json` maps the gear, Netflix, Prime Video and YouTube buttons. The
second unit used its own personal theme and optional add-ons; neither is shared
here.

## Third unit: TCL 55QM8B, Germany

Same product and build (`G08_4K_GB`, `UTT2.250416.001 AU02`), on Wi-Fi, tested
in one session on 12 November 2026. The list applied as it is, plus the hearing
test, Miracast and the USB media player and gallery, which this owner does not
use; Game Bar stayed. Before the launcher lines, the saved Wi-Fi network had to
be forgotten and joined again in Settings, because it belonged to setupwraith.

The gear, Netflix, Prime Video and YouTube buttons work with this folder's
keycodes and `HOME_KEY_SERVICE=1`. With that helper bound, holding volume up or
down moved one step per press (see
`docs/devices/tcl-55p71b-g09-key-repeat.md`). `consume_volume_keys` did not
change that. `volume_pauses_filter` did, in part: tap volume once, and a hold
within the next 4 seconds ramps; the mapped buttons work again once the pause
ends. To use it, add `"volume_pauses_filter": true` to `config.json`, or set it
in `launcher/app/src/main/res/values/config.xml` before building.
