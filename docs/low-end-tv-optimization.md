# Android TV & Streaming Device Optimization Guide

> **Tested and verified on real hardware:**
> - **Budget TV (2 GB RAM):** TCL 43S434 (Realtek RTD2851, Android TV 11 AR05 / V8-R851T02-LF1V728).
> - **High-Performance Box (3 GB RAM):** Walmart Onn. Streaming Device 4K Pro (Amlogic S905X4, Google TV 14 URO4.260304.092).
> - **Streaming Stick (1.5 GB RAM):** Amazon Fire TV Stick 4K 1st Gen (MediaTek MT8695, Fire OS 6.7.1.1 / Android 7.1.2).
> 
> **Applies to:** Any 1 GB – 3 GB Android TV, Google TV, or Fire TV set/box (TCL, Xiaomi, Hisense, Sony, Philips, Onn, Nvidia Shield, Fire TV) powered by Realtek, Amlogic, or MediaTek SoCs.

---

## 1. The Hardware Reality: Why Budget TVs Lag

Many users assume a 2 GB Android TV has 2 GB of memory available for applications. In reality:

```
[ Total Physical RAM: 2,048 MB ]
 ├── Hardware CMA (Contiguous Memory Allocator):  ~608 MB (Reserved for 4K video decoders, HDMI inputs, SurfaceFlinger)
 ├── GPU Shared VRAM & Widevine L1 (OP-TEE):       ~350 MB (Mali GPU graphics + DRM crypto)
 ├── Core OS & Drivers (SystemServer, Bluetooth):  ~300 MB
 ├── Custom Clean Launcher (tvhome):                ~54 MB (vs. ~180 MB for stock Google TV launcher)
 └── Available App RAM Headroom:                  ~520 - 550 MB (Maximum heap space for streaming apps)
```

When heavy streaming apps like **Sling TV, Hulu, or YouTube TV** launch, they consume **380 MB to 510 MB** of RAM just decoding video and caching UI views. 

If stock manufacturer bloatware, background recommendation scrapers, and always-listening microphone services run concurrently, the available memory drops below 150 MB. This forces the Linux kernel into **Low Memory Killer (`lmkd`) thrashing**, causing:
- Frame times ballooning to 60–120 ms (sluggish 8–15 FPS navigation).
- 850 ms Garbage Collection pauses freezing the remote control.
- Random app restarts when switching between apps.

---

## 2. Before vs. After Optimization Benchmarks

Measured on device using Android's hardware rendering pipeline monitor (`dumpsys gfxinfo com.sling`):

| Metric | Stock Preload (Before) | Optimized (After) | Impact |
|---|---|---|---|
| **50th Percentile Frame Time** | **61 ms** (~16 FPS) | **19 ms** (Target: 16.6 ms for 60 FPS) | **3.2x Faster UI Rendering** |
| **90th Percentile Frame Time** | **117 ms** | **56 ms** | **2.1x Faster Navigation** |
| **99th Percentile Worst Frame** | **301 ms** | **172 ms** | **42.8% Faster Worst Case** |
| **Severe UI Freezes (>700 ms)** | **226 freeze events** | **14 freeze events** | **93.8% Reduction in lockups** |
| **Background Network CPU Storm** | **89% – 171% CPU** (Conviva OkHttp loop) | **~0% CPU** | **Eliminated retry storm** |
| **Voice Interactor Background CPU** | **25% – 50% CPU** (Hotword listener) | **0% CPU** (Mic button preserved) | **Zero idle CPU waste** |
| **Launcher RAM Footprint** | **~180 MB** (Stock Google TV) | **54 MB** (`com.gathin.tvhome`) | **70% Less launcher RAM** |

---

## 3. The Five Core Optimizations

### Optimization 1: Enforce Strict Background Process Limits

Budget TVs cannot sustain multi-tasking in RAM. Giving the active streaming app exclusive access to system memory prevents frame drops and UI freezes.

```bash
# Force Android to limit cached background apps to 1:
adb shell settings put global standard_background_process_limit 1
```

*Result:* The moment you launch Sling, Netflix, or YouTube, Android gracefully clears idle cached apps out of the app heap, leaving the entire ~550 MB sandbox for the active foreground app.

---

### Optimization 2: Throttle Voice Assistant Background Polling

Google Assistant (`com.google.android.katniss:interactor`) attempts to run an always-listening hotword microphone loop in software on TVs lacking hardware DSPs. On budget quad-core SoCs, this consumes **25% to 50% of an entire CPU core** around the clock.

```bash
# Restrict Katniss from running unconstrained in the background:
adb shell cmd appops set com.google.android.katniss RUN_IN_BACKGROUND ignore
adb shell cmd appops set com.google.android.katniss RUN_ANY_IN_BACKGROUND ignore
```

*Result:* Background CPU drops to 0%. **The physical Google Assistant microphone button on the remote still works instantaneously** when pressed.

---

### Optimization 3: Fix DNS Telemetry Sinkholes (AdGuard Home / Pi-hole)

If your TV sits behind a network-level adblocker (AdGuard Home, Pi-hole, pfSense), you may experience severe, unexplainable UI freezing on streaming apps like Sling TV.

#### The Problem:
Apps like Sling TV use analytics frameworks (Conviva `cws.conviva.com` and MoveTV `tics.movetv.com`). When AdGuard Home blocks these using the **Default `127.0.0.1` Sinkhole**, the app receives an immediate `ECONNREFUSED` on port 443. Sling's OkHttp library treats this as a network disconnect and spins an aggressive infinite retry loop across 4 threads, consuming **up to 171% CPU load** and triggering 850 ms GC freezes.

#### The Fix:
1. **Change Blocking Mode to `NXDOMAIN`:**
   In AdGuard Home (*Settings → DNS settings → Blocking mode*), select **NXDOMAIN**.  
   *Why:* Returning `NXDOMAIN` (non-existent domain) signals to OkHttp that the host does not exist, causing it to back off and fail gracefully without retrying.
2. **Or Add DNS Allowlist Exceptions:**
   If using strict domain blocking, add allowlist exceptions for the streaming service's metrics endpoints:
   ```text
   @@||cws.conviva.com^
   @@||movetv.com^
   ```

---

### Optimization 4: Ahead-Of-Time (AOT) Native Code Compilation

By default, Android TV uses a Just-In-Time (JIT) compiler with profile-guided compilation (`speed-profile`). This requires the low-power CPU to compile bytecode into machine instructions while you are trying to stream 4K video, while also occupying valuable RAM for JIT code caches.

Compile heavy streaming apps to native ARM machine instructions ahead of time:

```bash
# Precompile the launcher and Sling TV to native ARM machine instructions:
adb shell cmd package compile -m speed -f com.gathin.tvhome
adb shell cmd package compile -m speed -f com.sling

# Optional: Precompile other heavy media apps:
adb shell cmd package compile -m speed -f com.google.android.youtube.tv
adb shell cmd package compile -m speed -f com.netflix.ninja
```

*Result:* App startup times decrease by 30–50%, navigation is noticeably snappier, and in-memory JIT caching overhead is eliminated.

---

### Optimization 5: Disable Manufacturer & Recommendation Bloat

Stock TV skins run continuous recommendation scrapers that poll content providers and cache promotional artwork.

```bash
# Apply model-specific debloat list:
TV_HOST=<tv ip> tools/disable.sh examples/tcl-43s434/debloat.txt
```

> [!CAUTION]
> **Never disable hardware display dependencies!**  
> On TCL Realtek RTD2851 platforms (`AR05` firmware), `com.tcl.gallery` and `com.tcl.ui_mediaCenter` are bound to `TV_INPUT_HARDWARE`. Disabling them will cause black screens on HDMI inputs. Always check your model's notes in `examples/`.

---

## 4. High-Performance Streaming Boxes (Walmart Onn 4K Pro, Amlogic S905X4 on Android 14)

Even higher-end devices with 3 GB of RAM can suffer from severe lag after major updates like Google TV 14 (Build `URO4.260304.092`). Our live diagnostics on the **Walmart Onn 4K Pro** revealed three distinct bottlenecks:

### Live Before vs. After Benchmarks (Onn 4K Pro)

| Metric | Stock State (Before) | Optimized State (After) | Improvement |
|---|---|---|---|
| **Audio Service CPU (`audio.service-droidlogic`)** | **~28% – 32% CPU** | **5.8% CPU** | **~25% CPU freed up immediately** (Dolby MS12 software loop killed) |
| **Sling 99th Percentile Worst Frame** | **150 ms** | **69 ms** | **2.2x Faster rendering (smoother motion)** |
| **Sling Janky Frame Rate** | **9.16%** | **6.52%** | **~29% reduction in dropped frames** |
| **Slow UI Thread Freezes** | **22 events** | **10 events** | **54.5% Reduction in UI thread lockups** |
| **Remote Input Latency Events** | **964 delayed events** | **441 delayed events** | **54.3% Reduction in remote input lag** |
| **Raw Free Memory** | ~286 MB | **~415 MB – 445 MB** | **+130 MB to +160 MB more RAM** (~1.38 GB available) |
| **System Idle CPU Capacity** | **~8% – 20% idle** | **170% – 240% idle** | **Massive CPU headroom restored** |

### Key Optimizations for Onn 4K Pro:

1. **Kill the Dolby MS12 Audio Transcoding Loop (Saves ~25% CPU):**
   - On stock settings, *Surround sound* defaults to **Automatic**, forcing the CPU to continuously run Dolby Multistream 12 (`DOLBY_MS12`) transcoding.
   - Go to **Settings → Display & Sound → Advanced sound settings → Surround sound**:
     - If using TV speakers or a 2.0/2.1 setup, select **None (Never use surround sound)**.
     - If using an external surround soundbar or AVR, select **Manual**.
   - *Result:* Drops `audio.service-droidlogic` from ~32% down to **5.8% CPU** while keeping the always-on microphone 100% active.

2. **Cap Background Streaming Leaks:**
   - In Android 14, minimized apps like Sling continue decoding at 100%+ CPU in the background when you press Home.
   - Enforce process limit and background restrictions:
     ```bash
     adb shell settings put global standard_background_process_limit 1
     adb shell cmd appops set com.sling RUN_IN_BACKGROUND ignore
     adb shell cmd appops set com.sling RUN_ANY_IN_BACKGROUND ignore
     ```

3. **Client-Specific Ad-Blocker Allowlist Rules:**
   - Lock telemetry allowlists specifically to your streaming device IPs in AdGuard Home or Pi-hole:
     ```text
     @@||cws.conviva.com^$client=<TV_IP>
     @@||movetv.com^$client=<TV_IP>
     @@||bf.dynatrace.com^$client=<TV_IP>
     ```
   - *Zero ad impact:* None of these domains serve commercial ads. Conviva monitors video bitrate/QoE, MoveTV delivers Sling channel posters (`p-img.movetv.com`), and Dynatrace monitors app crash stack traces.

4. **The ASR / AI Super Resolution Reality:**
   - In Android 14, the `AI-PQ/AI-SR` toggle in Amlogic's hidden settings is grayed out on the Onn 4K Pro because the S905X4 lacks the dedicated on-chip NPU of the newer S905X5 generation.
   - However, the S905X4's hardware Video Processing Unit (VPU) automatically performs bicubic 4K60 upscaling in silicon with 0% CPU overhead. High-end TVs (such as the TCL QM8 Mini-LED) will further enhance the incoming 4K feed using their own dedicated AIPQ deep-learning engines.

---

## 5. Streaming Sticks (Amazon Fire TV Stick 4K `mantis`, 1.5 GB RAM)

Fire TV sticks feature capable 4K HDR media decoding (MediaTek MT8695), but suffer from severe memory bottlenecks due to only having 1.5 GB of RAM. On stock Fire OS 6, the 383 MB zRAM partition is routinely **99.98% saturated**, causing kernel `kswapd0` thrashing and remote input delays.

### Live Before vs. After Benchmarks (Fire TV Stick 4K)

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

### Key Optimizations for Fire TV:
1. **Enforce `standard_background_process_limit 1`**: Prevents Netflix (137 MB), SmartTube (92 MB), and Sling (76 MB) from holding memory simultaneously.
2. **Disable `com.amazon.tv.livetv`**: Completely eliminates Amazon's background channel guide crawler that wastes 14% CPU.
3. **Disable Forced OTA Updater (`com.amazon.tv.forcedotaupdater.v2`)**: Protects your custom launcher from being uninstalled by background Amazon updates.
4. **AppOps Background Restrictions**: Set `cmd appops set <pkg> RUN_IN_BACKGROUND ignore` on `com.sling`, `com.amazon.client.metrics`, and `com.amazon.device.crashmanager`.
5. **AOT Native Compilation**: Compile `com.gathin.tvhome`, `com.sling`, and `org.smarttube.stable` to native ARM machine code (`cmd package compile -m speed -f <pkg>`).

---

## 6. Automation Script

To apply all system-level optimizations (process limits, AppOps restrictions, and AOT compilation) in one pass, run:

```powershell
# Windows PowerShell
powershell -File .\tools\optimize-low-end.ps1 -TvHost <TV_IP>
```

```bash
# macOS / Linux
bash tools/optimize-low-end.sh <TV_IP>
```
