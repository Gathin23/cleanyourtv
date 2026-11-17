# Impex evoQ 2K (`dangni`): no profile in the app yet, and the setup wizard owns the saved Wi-Fi

Read from one owner report, the Clean Your TV app's own TV report on 2026-11-12 (app 0.4.8, full).
Nothing was disabled and no launcher was installed: this is an observed-state note, not a list, and
the evoQ 4K that already has a folder in `examples/` is a different product.

| Field | Observed value |
|---|---|
| Retail model | `IMPEX evoQ 2K`, owner-supplied |
| Brand / manufacturer | `Impex` / `Changhong` |
| Product | `GTVU65IP` |
| Device | `dangni` |
| Android / SDK | Google TV on `14` / `34` |
| Build | `Impex/GTVU65IP/dangni:14/UKN6.260116.054/15121168:user/release-keys` |
| System apps read | 141 |

## It is not the evoQ 4K already in the tree

[`impex-evoq-4k/`](../../examples/impex-evoq-4k/debloat.txt) is the same brand on another product:
`GTVU64IP` / board `alimos` / MediaTek mt5896 on `UKN3.250415.053`, sold in India. The 2K here reports
`GTVU65IP` / `dangni` on `UKN6.260116.054`, so a different product, board, SoC and build. Its list is
not evidence for this set, and nothing below says which packages a 2K can lose.

## What the app did on this set

- `Profile: none`, answered as "Nothing to switch off": no package was disabled by the app.
- `Home switch: blocked WIZARD_WIFI`.
- Home resolved to `com.google.android.apps.tv.launcherx/.home.HomeActivity`.

## Why the Home switch is blocked

The set reports three HOME claimants: `com.google.android.apps.tv.launcherx/.home.HomeActivity` at
priority 2, `com.google.android.tungsten.setupwraith/.RecoveryActivity` at priority 1, and
`com.android.tv.settings/.system.FallbackHome` at -1000. The stock Google TV home wins (higher
priority), but the same wizard owns the Wi-Fi network saved at first setup: disabling it deletes that
network and takes the TV off Wi-Fi, ADB and the phone remote with it. That is the pair the app's
`WIZARD_WIFI` answer is about, and the reason `tools/disable.sh` refuses
`com.google.android.tungsten.setupwraith` on such a set.

The way through for an owner whose network in use belongs to something else is the one in
[troubleshooting](../../docs/TROUBLESHOOTING.md): read the owner with
`dumpsys wifi | grep -o 'cname=[^ ]*'`, and when it is not the wizard, forget the network and join it
again before the handover. Nothing here checked that on this unit: the ownership above is the app's
reading, not a `dumpsys` from the set.

## What the set reports about itself

- Already off: `com.netflix.ninja` (`disabled-user` by an owner) and
  `com.mediatek.wwtv.tvcenter.plus` (`disabled`).
- Enabled: `com.android.vending`, `com.cltv.hybrid`, `com.google.android.katniss`.
- Live TV is not identified: `com.mediatek.wwtv.tvcenter`, `com.cltv.hybrid`,
  `com.gdch.android.tv.inputplayer` and `com.google.android.tv.inputplayer` are all enabled, where the
  4K sibling names `com.cltv.hybrid` its Live TV app.
- The vendor stack is Changhong's: `com.homwee.*` (store, help, factory, upgrade, `dmt.upgrade`,
  `dynamicbacklight`, `mmp.screenshare`, `multiscreenshare`, `overlay.tvservice`), `com.gdch.*`,
  `com.mstar.android.tv.disclaimercustomization` and the MediaTek services the 4K sibling's list
  already names.

None of that is a recommendation. It records what a future list for this folder starts from, so
whoever writes one does not have to ask the owner for the same report again.

## Not checked

No package was disabled, so nothing here is lived-with: no launcher install, no reboot, no standby
wake, no input, tuner or casting check, and no audio or remote-button check. A list for this set
belongs in `examples/impex-evoq-2k/` once someone has applied one and can say what a reboot brings
back; [`docs/workflows/tv-list.md`](../workflows/tv-list.md) is how that folder arrives.
