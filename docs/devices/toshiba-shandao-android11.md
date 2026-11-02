# Toshiba Android TV 11 (Vestel `shandao`): an observed-state note, no list yet

One owner-reported read on 2026-10-25 (#1829), taken with the phone app (0.3.4) before any change
was made. This is an observed-state note, not a debloat list: nothing has been disabled on this set
yet. Android identifies it as brand `Toshiba`, manufacturer `My`, device `shandao`, Android 11 /
SDK 30, firmware `Toshiba/EMIR_Toshiba/shandao:11/RTM6.230109.207/10391326:user/release-keys`, with
138 system packages and the stock Home on `com.google.android.tvlauncher` (Android TV Leanback).
The report does not name the retail model: the model string alone reads `Android TV`.

It is the third unit of the Vestel `shandao` family in the repo.
[examples/cecotec-shandao](../../examples/cecotec-shandao/) is the same `shandao` board on Android
TV 14 (MediaTek), and its second unit is already a Toshiba (65QA7D63DG, Norway, `UKNV.260528.001`,
where `com.google.android.tvrecommendations` comes back enabled at every boot); this set is the
older Android TV 11 firmware, carrying Freeview. The closest tested list is
[examples/toshiba-cosmos-android-tv](../../examples/toshiba-cosmos-android-tv/) (Vestel Cosmos170
`yeoksam`, Android TV 11, Spain): its groups cover the Vestel telemetry and the retail demo named
below. Do not apply either list solely because the brand or the board name matches.

## What the read showed

| Package | State | What it is |
|---|---|---|
| `com.vestel.smartlogger` | default | Vestel analytics |
| `com.vestel.vestelreporter` | default | Vestel telemetry reporter |
| `com.vestel.smartcenter` | default | the app the Cecotec note watched: no UI, `GET_TASKS` + `INTERNET` |
| `com.mediatek.tv.freeviewplay` | default | Freeview Play orchestration |
| `com.google.android.tvrecommendations` | default | stock launcher recommendation rows |
| `com.google.android.feedback` | default | crash / feedback reporting |
| `com.amazon.amazonvideo.livingroom` | default | Prime Video |
| `fusion.android.tv.demo` | default | retail demo loop |
| `uk.co.freeview.*` (14 packages) | disabled | UK catch-up and backing services |

The report names packages and states only; the descriptions come from the Cecotec and Cosmos notes
for the same packages. Every `uk.co.freeview.*` package ships disabled from the factory on this
Freeview (UK-market) firmware, the same behaviour both those folders record. A list must not name
them: `disable.sh` would have nothing to do, and `tools/restore.sh` would turn them back on.

The phone app matched no profile, and its Home switch was blocked at the Wi-Fi setup wizard
(`WIZARD_WIFI`): the wizard owns the saved Wi-Fi, the same situation as
[the XGIMI note](xgimi-xr13a.md) describes, and the app leaves the wizard on. No launcher was
installed and no package changed during the read.

## From here

The candidates the report names - the Vestel telemetry, `com.google.android.tvrecommendations`,
the retail demo - are the groups the Cosmos list already disables, and the Freeview catch-up apps
need nothing, being off already. Once this set's owner has run a list on the TV and lived with it
for a few days, it becomes an `examples/` folder of its own, the way
[docs/workflows/tv-list.md](../workflows/tv-list.md) describes.
