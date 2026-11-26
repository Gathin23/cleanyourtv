# Acerpure 32-inch 720p Google TV (DAC41F)

This note records one owner's [report on issue #4438](https://github.com/Gathin23/cleanyourtv/issues/4438). It is a single failure, not a tested debloat list: the report carries no firmware build and no package list, and the session broke before either was written down.

| Field | Reported value |
|---|---|
| Retail model | Acerpure 32-inch 720p Google TV (DAC41F) |
| Board | `anglelake` |
| Android / SDK | Google TV on Android 11 / API 30 |
| Firmware | not reported |
| Debloat list | none: no list has been validated on this unit |

## The stock launcher has to stay enabled

Disabling `com.google.android.apps.tv.launcherx` on this unit "triggers a black screen / system hang until re-enabled", in the owner's words, and the way back was ADB rather than the remote:

```sh
TV_HOST=<tv ip> tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'
```

then a power cycle. Their conclusion, kept here rather than widened: keep the stock launcher enabled on this set and open `com.gathin.tvhome` from it, instead of handing Home over and disabling launcherx.

That is the shape of the "Black screen at every boot after the launcher change" entry in [TROUBLESHOOTING](../TROUBLESHOOTING.md), and the two commands it prints are what settle it before anything is disabled: one asks whether an activity answers HOME while the user is still locked, the other whether this launcher declares that it can start in that state. Where nothing answers, the stock launcher stays and the new one is reached from it; `stock_home_redirect` in [themes/README.md](../../themes/README.md) is the switch that keeps the stock Home enabled while Home presses land on this launcher.

## What the report does not settle

No firmware build, no package list, and no word on whether the new launcher was Home at the moment launcherx was disabled, or whether the screen came back without the power cycle. So this note does not claim another DAC41F behaves the same way, and it names no package to disable on this board: it records one unit where the Home handover has to be checked first, and a way back that needs ADB already open.

## Debloat status

No debloat list has been validated for this TV. The stock launcher remains enabled.
