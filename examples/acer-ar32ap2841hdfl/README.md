# Acer P Series AR32AP2841HDFL 32" HD (Android TV 11)

Tested on an Acer P Series 32" HD Android TV (`ro.product.model=ATV R1`, `ro.product.device=R1`, manufacturer SCBC) powered by the Realtek RTD2841 board (`rtd284o`, `rt41`), running Android TV 11 on build `RTT7.220308.001` (incremental `1014`, software version `V8-T841TNF-LF2V029`).

## What this folder contains

| File | What it is |
|---|---|
| `debloat.txt` | The 16 packages the list disables, grouped and commented; the owner also had the two updaters, Sony LIV and YouTube Music off, commented out here |
| `config.json` | Dock configuration with 4 streaming apps and frosted glass dock |
| `tv.json` | Entry for the CleanYourTV supported TV models index |

## How to Apply

```sh
TV_HOST=<tv ip> tools/disable.sh examples/acer-ar32ap2841hdfl/debloat.txt
```
On Windows:
```powershell
tools\disable.ps1 -TvHost <tv ip> examples\acer-ar32ap2841hdfl\debloat.txt
```

## How to Restore

```sh
TV_HOST=<tv ip> tools/restore.sh examples/acer-ar32ap2841hdfl/debloat.txt
```
On Windows:
```powershell
tools\restore.ps1 -TvHost <tv ip> examples\acer-ar32ap2841hdfl\debloat.txt
```

## Notes for this TV

1. **Launcher & Home behavior:**
   - Stock launcher is `com.google.android.tvlauncher`.
   - The stock launcher is disabled only after installing the replacement launcher.
   - Live TV and HDMI inputs (`com.realtek.tv.passthrough`, `com.realtek.dtv`, `com.android.tv`) remain completely functional.

2. **Wi-Fi & Connectivity:**
   - Saved Wi-Fi network credentials are owned by `android.uid.system`, not `setupwraith`.
   - Bluetooth remote key handlers and pairing (`com.realtek.keyinterceptserver`, `com.apps.smartkey`, `com.apps.virtualkey`, `com.apps.bluetoothremote`, `com.android.bluetooth`) are kept intact.
   - AdGuard Private DNS (`dns.adguard-dns.com`) works in hostname mode without issues.
