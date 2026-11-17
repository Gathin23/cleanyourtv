# TIM TIMVISION Box (uzw4010tim)

Technicolor's Android TV 8.0 Oreo set-top box for Telecom Italia (TIM) (`uzw4010tim`, board `skipper_tim`, Marvell Berlin BG4CT SoC, 2 GB RAM, 8 GB Flash, integrated DVB-T/T2 tuner), debloated and running CleanYourTV.

Everything is disabled safely with `pm disable-user`, nothing is uninstalled from system partitions.

---

## Operating Modes: Complete Debloat vs. Hybrid DTT

On the TIM Box, `timvision.launcher` serves two distinct roles:
1. It is the stock system operator launcher (registered with `category.HOME` at `priority=2`).
2. It hosts the hardware-accelerated Live TV video player for antenna digital terrestrial channels (`com.canal.player.ui.tv.dvb.BoxPlayerV2Activity`).

CleanYourTV supports two configurations:

### Option 1: Complete Debloat (Pure Clean)
* **How it works:** `timvision.launcher` is disabled alongside all other operator packages.
* **Home key:** CleanYourTV is the sole launcher on the system. The physical remote HOME button opens CleanYourTV in 0ms.
* **Live TV:** Use streaming IPTV apps like **OpenTV** (`app.opentv`, preconfigured in dock slot 3) to stream Italian TV channels over the internet.

### Option 2: Hybrid DTT Setup (Keep Terrestrial Antenna TV)
* **How it works:** `timvision.launcher` remains enabled so the hardware tuner and scanned DTT channels function normally.
* **Home key:** Because `timvision.launcher` has HOME priority 2 on Android 8.0 Oreo, the Home button opens TIMVISION unless redirected by an accessibility service.
* **Opening TV:** The 4th slot of the dock opens Live TV directly (`timvision.launcher`).
* **Channel Tuning:** The **Sintonia DTT** tile (`com.technicolor.android.dtvscan.project`) allows rescanning antenna channels.

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

### 3. Install CleanYourTV Launcher

For **Option 1 (Complete Debloat)**, disable the stock launcher during install:
```bash
TV_HOST=<tv ip> STOCK_LAUNCHER=timvision.launcher launcher/build-and-install.sh
```

For **Option 2 (Hybrid DTT)**, keep `timvision.launcher` enabled for antenna playback:
```bash
TV_HOST=<tv ip> launcher/build-and-install.sh
```

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

The TIM Box firmware includes `com.technicolor.app.ota` (ApplicationOta) and `com.google.android.partnersetup`. These services silently re-download commercial preloads (Disney+, Mediaset Infinity, Paramount+/CBS, Hayu, etc.) in the background at night. The debloat list disables both to keep the system clean.
