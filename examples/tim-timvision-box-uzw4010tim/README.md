# TIM TIMVISION Box (uzw4010tim)

Technicolor's Android TV 8.0 Oreo set-top box for Telecom Italia (TIM) (`uzw4010tim`, board `skipper_tim`, Marvell Berlin BG4CT SoC, 2 GB RAM, 8 GB Flash, integrated DVB-T/T2 tuner), debloated and running CleanYourTV, with antenna TV still working.

Everything is disabled safely with `pm disable-user`, nothing is uninstalled from system partitions.

---

## Antenna TV without TIMVISION

On the TIM Box, `timvision.launcher` is two things at once:
1. the stock operator launcher, registered with `category.HOME` at `priority=2`, so on Android 8.0 it keeps Home as long as it is enabled;
2. the app that hosts TIMVISION's own antenna player (`com.canal.player.ui.tv.dvb.BoxPlayerV2Activity`, not exported).

The tuner itself does not live there. It is a standard Android TV Input Framework service, `com.technicolor.android.dtvinput/.DtvInputService`, which runs on its own and accepts any client.

The separate **[Digitale Terrestre](https://github.com/Thevirtualmask/timbox-dtt)** app plays it through a `TvView` and scans channels through the tuner's own installer broadcast. With that app installed, `timvision.launcher` is disabled like the rest, Home goes to CleanYourTV natively, and the antenna keeps working.

That app is not part of this repository. It is its own project with its own permissions (it uses the box's local ADB to read the scanned channel list), described in its README.

Without it, disabling `timvision.launcher` also removes antenna TV; an IPTV app such as **OpenTV** (`app.opentv`, dock slot 3) covers the Italian channels over the internet.

---

## Quick Setup

### 1. Enable ADB on the TIM Box
1. Open *Impostazioni (Settings)* → *Informazioni (About)*.
2. Scroll to *Build* and press **OK** on the remote 7 times until Developer mode is activated.
3. Go back to *Opzioni sviluppatore (Developer options)* and turn on **Debug USB**.
4. Find the box's IP address in *Impostazioni* → *Rete*.

### 2. Apply Debloat List

From the repository root on Linux or macOS:
```bash
TV_HOST=<tv ip> tools/disable.sh examples/tim-timvision-box-uzw4010tim/debloat.txt
```

On Windows (PowerShell):
```powershell
tools\disable.ps1 examples/tim-timvision-box-uzw4010tim/debloat.txt -TvHost <tv ip>
```

### 3. (Optional) Install the Digitale Terrestre app for antenna TV

Install it before the next step and open it once: the first start scans the antenna channels. See its README for the steps and the permissions it asks for.

### 4. Install CleanYourTV Launcher and disable the stock one

```bash
TV_HOST=<tv ip> STOCK_LAUNCHER=timvision.launcher launcher/build-and-install.sh
```

The script disables `timvision.launcher` only after the new launcher has started, and rolls back if Home does not follow.

`config.json` puts Digitale Terrestre in dock slot 4, maps the remote's TV key (keycode 170) to it and adds a **Sintonia DTT** tile (`it.alessio.dtt.sintonia`) for rescans. Without the app those entries simply don't show.

---

## Undo Everything (Restore)

To restore all factory packages and return to the stock TIMVISION launcher:

On Linux or macOS:
```bash
TV_HOST=<tv ip> tools/restore.sh examples/tim-timvision-box-uzw4010tim/debloat.txt timvision.launcher
```

On Windows (PowerShell):
```powershell
tools\restore.ps1 examples/tim-timvision-box-uzw4010tim/debloat.txt -Stock timvision.launcher -TvHost <tv ip>
```

---

## Background Auto-Installers Note

The TIM Box firmware includes `com.technicolor.app.ota` (ApplicationOta) and `com.google.android.partnersetup`. These services silently re-download commercial preloads (Disney+, Mediaset Infinity, Paramount+/CBS, Hayu, etc.) in the background at night; ApplicationOta also updates `timvision.launcher` itself. The debloat list disables both to keep the system clean. Real firmware updates (`com.technicolor.ota`, `com.technicolor.checkota`) are kept.
