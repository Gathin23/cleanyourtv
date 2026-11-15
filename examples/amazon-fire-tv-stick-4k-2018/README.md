# Amazon Fire TV Stick 4K (1st Gen, 2018 - `AFTMM` / `mantis`)

> **Hardware Specifications:**
> - **SoC:** MediaTek MT8695 (Quad-core ARM Cortex-A53 @ 1.7 GHz)
> - **GPU:** Imagination PowerVR GE9215 @ 650 MHz
> - **RAM:** 1.5 GB LPDDR4 (~1.25 GB usable by Android)
> - **Storage:** 8 GB eMMC 5.1
> - **OS:** Fire OS 6.7.1.1 (Android 7.1.2 Nougat, API 25)

---

## 1. The Bottleneck: 1.5 GB RAM & zRAM Exhaustion

On stock Fire OS 6, streaming apps like Netflix (`com.netflix.ninja`), Sling TV (`com.sling`), and SmartTube (`org.smarttube.stable`) frequently sit cached simultaneously in the background. Because the device only has 1.5 GB of physical memory:

1. **zRAM Swap Saturation:** The 383 MB zRAM swap partition reaches **99.98% saturation** (under 100 KB free).
2. **Kernel Thrashing (`kswapd0`):** The Linux kernel worker `kswapd0` constantly spins trying to compress and swap memory pages, causing input freezes and dropped frames.
3. **Background CPU Drains:** Daemons like Amazon Live TV Guide (`com.amazon.tv.livetv`) continuously consume **14% CPU** syncing channel data, and Sling TV holds background wake-locks.

---

## 2. Before vs. After Optimization Benchmarks

Measured live on device via `dumpsys meminfo`, `dumpsys cpuinfo`, and `dumpsys gfxinfo`:

| Metric | Stock State (Before) | Optimized State (After) | Improvement |
|---|---|---|---|
| **Available RAM (`MemAvailable`)** | **159 MB** (Only ~12% headroom) | **425 MB** | **+266 MB RAM (+2.7x more free memory)** |
| **Physical Free Memory (`MemFree`)** | **89 MB** | **281 MB** | **3.1x more physical free RAM** |
| **zRAM Swap Free** | **56 KB** (99.98% saturated) | **132,796 KB (~130 MB)** | **Swap thrashing eliminated** |
| **Live TV Background CPU (`com.amazon.tv.livetv`)** | **14.0% CPU** (72,884 page faults) | **0% CPU** (Disabled) | **Killed constant CPU churn** |
| **Audio Server CPU (`audioserver`)** | **19.0% CPU** | **4.0% CPU** | **~15% CPU load eliminated** |
| **Sling Median Frame Time** | Variable (stuttering) | **5 ms** (60 FPS target is 16.6 ms) | **Ultra smooth 60 FPS playback** |
| **Sling 90th Percentile Frame Time** | >60 ms | **14 ms** | **Zero UI lag during navigation** |
| **CleanYourTV Median Frame Time** | Variable | **7 ms** | **Instant D-pad responsiveness** |

---

## 3. Recommended Optimization Steps

### Step 1: Cap Background Processes
```bash
adb shell settings put global standard_background_process_limit 1
```

### Step 2: Speed Up UI Animations
```bash
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5
```

### Step 3: Restrict AppOps for Sling & Telemetry Daemons
```bash
adb shell cmd appops set com.sling RUN_IN_BACKGROUND ignore
adb shell cmd appops set com.amazon.client.metrics RUN_IN_BACKGROUND ignore
adb shell cmd appops set com.amazon.device.crashmanager RUN_IN_BACKGROUND ignore
adb shell cmd appops set com.amazon.device.software.ota RUN_IN_BACKGROUND ignore
adb shell cmd appops set com.amazon.ftvads.deeplinking RUN_IN_BACKGROUND ignore
```

### Step 4: Disable Heavy Amazon Background Services
```bash
# Disable Live TV channel guide syncer (saves 14% CPU and 46 MB RAM):
adb shell pm disable-user --user 0 com.amazon.tv.livetv

# Disable forced OTA updater (protects custom launcher from being uninstalled by Amazon):
adb shell pm disable-user --user 0 com.amazon.tv.forcedotaupdater.v2

# Disable background logging:
adb shell pm disable-user --user 0 com.amazon.dp.logger
```

### Step 5: Ahead-Of-Time (AOT) Machine Code Compilation
Compile apps from JIT bytecode to native ARM instructions:
```bash
adb shell cmd package compile -m speed -f com.gathin.tvhome
adb shell cmd package compile -m speed -f com.sling
adb shell cmd package compile -m speed -f org.smarttube.stable
adb shell cmd package compile -m speed -f com.netflix.ninja
```
