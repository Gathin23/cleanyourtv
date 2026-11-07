<p align="center">
  <a href="https://cleanyour.tv"><img src="https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/banner.png" alt="CleanYourTV. Your TV, minus the ads. Strip the bloatware from any Android TV or Google TV over Wi-Fi and replace the ad-filled home screen with a clean launcher that looks like an Apple TV. No root, no cable, nothing uninstalled, and a one-line restore." width="800"></a>
</p>

<p align="center">
  Read in: <a href="README.he.md" lang="he" dir="rtl">עברית</a> · <a href="README.zh-CN.md">简体中文</a> · <a href="README.zh-TW.md">正體中文</a> · <a href="README.vi.md">Tiếng Việt</a> · <a href="README.ja.md">日本語</a> · <a href="README.it.md">Italiano</a> · <a href="README.pt-BR.md">Português</a> · <a href="README.nl.md">Nederlands</a> · <a href="README.de.md">Deutsch</a> · <a href="README.uk.md">Українська</a> · <a href="README.tr.md">Türkçe</a>
</p>

<p align="center">
  <a href="https://github.com/Gathin23/cleanyourtv/stargazers"><img src="https://img.shields.io/github/stars/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Stars"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/pulls?q=is%3Amerged"><img src="https://img.shields.io/github/issues-search?query=repo%3AGathin23%2Fcleanyourtv%20is%3Apr%20is%3Amerged&style=flat&cacheSeconds=1800&label=merged%20PRs&color=cfffcf&labelColor=15181e" alt="Merged PRs"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/network/members"><img src="https://img.shields.io/github/forks/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Forks"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/graphs/contributors"><img src="https://img.shields.io/github/contributors/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Contributors"></a>
  <a href="https://buymeacoffee.com/gathint"><img src="https://img.shields.io/badge/support-%E2%9D%A4-ff5e8a?style=flat&labelColor=15181e" alt="Support, buy me a coffee"></a>
  <a href="https://www.producthunt.com/products/cleanyourtv"><img src="https://img.shields.io/badge/Product%20Hunt-upvote%20%E2%96%B2-da552f?style=flat&logo=producthunt&logoColor=white&labelColor=15181e" alt="CleanYourTV on Product Hunt"></a>
</p>

<p align="center">
  <a href="https://trendshift.io/repositories/310225"><img src="docs/trending-badge.png" alt="#1 trending repo of the week, Oct 19–25 2026, via trendshift.io" width="360"></a>
</p>

<p align="center">
  <a href="docs/PRESS.md"><img src="docs/card.jpg" alt="456 TV models · 122 brands · 31 languages · 572 KB launcher. Featured in TechCrunch, TechRadar, Tom's Guide, Mashable, Android Authority, XDA, FlatpanelsHD, It's FOSS, Phandroid, GIGAZINE, Xataka, WinFuture, Frandroid, Macitynet, Techaeris, TechPP and 396 more: 412 articles from 54 countries. Before: stock Google TV home with a sponsored ad. After: the Clean Your TV launcher." width="800"></a>
</p>

## Start here

| You have | Go to |
|---|---|
| **An Android TV or Google TV** | [Quick start](#quick-start): a coding assistant does the work in about fifteen minutes. |
| **A Samsung, LG, Roku, Vizio or Hisense VIDAA TV** | [cleanyour.tv/not-android](https://cleanyour.tv/not-android): CleanYourTV can't run there, but you can turn off the tracking with your remote. |
| **A phone or laptop, no coding agent** | One of the [community apps](https://cleanyour.tv/app) for Android phones, Mac and Windows, made by independent developers. |
| **Want to see it first** | [What you get](#what-you-get): screenshots of the new home screen. |

**Official links:** [cleanyour.tv](https://cleanyour.tv) and this repo. Other domains using the name, such as
cleanyourtv.com, are not run by this project ([#1004](https://github.com/Gathin23/cleanyourtv/issues/1004)).
CleanYourTV publishes no app in any app store, and clean-your-tv.net is not ours either.

[How it works](#how-it-works) · [Quick start](#quick-start) · [What you get](#what-you-get) ·
[Supported TVs](#supported-tvs) · [Doing it by hand](#doing-it-by-hand) · [The launcher](#the-launcher) ·
[Restoring](#restoring-your-tv) · [Firmware updates](#after-a-firmware-update) ·
[Questions](#questions-people-ask) · [Contributing](#contributing)

## How it works

Android TVs have the same debug bridge as Android phones. Switch on USB debugging in the TV's
developer options, allow your computer once, and the rest is standard Android tooling:

| Step | What happens |
|---|---|
| **Back&nbsp;up** | The list of installed packages and settings. |
| **Disable** | The manufacturer's telemetry, ad services, content rows and preloaded junk, with `pm disable-user`. Nothing is uninstalled, so a restore script can undo every step. |
| **Install** | A small native launcher, made the default home screen. |

**No root, no cable, nothing uninstalled, and a one-line restore if you change your mind.**

The bloat differs by brand, so the prompt has your coding assistant discover it on your TV and ask
before touching anything you might use.

## Quick start

| You have | Do this |
|---|---|
| **A computer** | Use the prompt with a coding assistant: the three steps below. |
| **A computer, no coding assistant** | Run the three steps yourself: the commands are in [Doing it by hand](#doing-it-by-hand). |
| **A phone or laptop, no coding assistant** | Pick one of the [community apps](https://cleanyour.tv/app): free, open-source apps built on these TV lists by independent developers. |

The three steps below are written for a coding assistant: the computer needs to be on the same Wi-Fi as
the TV, with a shell on it, plus Python 3, a JDK from 17 through 22, and Android SDK platform 34
with build-tools 34 (the assistant builds the launcher there).
Without an assistant, [Doing it by hand](#doing-it-by-hand)
is the same job, run by you, and it has the install commands.

Any of these work: Claude Code (on [0G Private Computer](https://pc.0g.ai/?utm_source=cleanyourtv&utm_medium=github&utm_campaign=readme) or an [Anthropic plan](https://claude.com/pricing)), [Codex](https://github.com/openai/codex), [Gemini CLI](https://github.com/google-gemini/gemini-cli) or [Antigravity CLI](https://antigravity.google/product/antigravity-cli).

| Where | What to do |
|---|---|
| **1. On the TV** | Settings → Device Preferences → About → press OK on **Build** seven times → back → Developer options → **USB debugging** on. If you see a Network or ADB debugging toggle, turn that on too. Keep the TV on. |
| **2. On your computer** | Clone the repo and open that folder in your assistant (`git clone https://github.com/Gathin23/cleanyourtv`), then paste [`PROMPT.md`](PROMPT.md) and fill in your TV model. |
| **3. Follow along** | Accept "Allow USB debugging?" on the TV, then answer the checklist: which apps you use, the dock size and app order, whether to keep the voice assistant, Chromecast and updates. |

Cloning matters: the assistant reuses this repo's `tools/disable.sh` and `launcher/build-and-install.sh`
instead of writing its own, and that is where the backup, the Home guard and the restore record live.

The assistant backs up, debloats, builds and installs the launcher, and checks each step with
screenshots pulled from the TV. With this repo as context it takes about fifteen minutes.

## What you get

A home screen that looks like an Apple TV and gets out of the way:

| Part | What it looks like |
|---|---|
| **A pitch-black home** | A dock of your four favourite apps in a rounded band, where Apple TV puts it. |
| **A top shelf** | Names the focused app in large type, with an ambient glow in a colour taken from its artwork. |
| **The rest of your apps in a grid** | Slides up when you press Down. |
| **One floating glass highlight** | Springs from tile to tile. The focused tile lifts and shows its name. |
| **Three frosted buttons** | Inputs (HDMI ports under the names you gave them in the TV's settings, AV, Live TV), Wi-Fi or Ethernet with the network name, Settings. |
| **A greeting, a thin clock and the date** | In [Outfit](https://fonts.google.com/specimen/Outfit), and a matching Clock screensaver. Set `show_stats` in config or a theme for one line under the clock: CPU, free memory and free storage. It stays off unless you turn it on, and it refreshes only while Home is on screen. |
| **Themes and module cards** | Themes change colours, wallpaper, dock and columns without a rebuild, and module cards let other apps plug a tile in, only with your say-so. |

And a TV that stops working against you: no sponsored rows, no "Discover" nags, no analytics
phoning home, no apps you never asked for.

| | |
|---|---|
| ![The top shelf names the focused app over an ambient glow in its colour](docs/focus-2-up-netflix.png) | ![The Inputs popup: HDMI ports with what is connected, AV and Live TV](docs/inputs-menu.jpg) |
| *Top shelf and ambient glow* | *Inputs: HDMI with connection state, AV, Live TV* |
| ![The grid with the glass highlight on a tile and its name beneath](docs/focus-1-fancode.png) | ![The Clock screensaver: a thin clock drifting over the glow](docs/clock-screensaver.png) |
| *The grid, one glass highlight, the tile's name* | *Clock screensaver, drifts so nothing burns in* |
| ![The aurora theme: a wallpaper and tinted pills, pushed without a rebuild](themes/aurora/screenshot.jpg) | ![Six columns and a six-app dock from a theme's config.json](themes/six-columns/screenshot.jpg) |
| *Aurora theme, pushed with `tools/theme.sh`* | *Six columns and a six-app dock, same launcher* |
| ![Moving a tile: held OK, chose Move, the tile is picked up and the others dim](docs/tile-move.jpg) | ![The App library: installed apps, one press adds an app back to Home](docs/app-library.jpg) |
| *Hold OK on a tile, Move: arrows carry it, Back puts it down* | *App library: one press puts a removed app back on Home* |

## Supported TVs

Anything running **Android TV** or **Google TV**.

| Platform | What works |
|---|---|
| **Android TV, Google TV** | The debloat and the launcher. |
| **Fire TV** | The debloat half only, since Fire OS will not hand Home to a third-party launcher. And on the 2015 Fire TV Box (Fire OS 5) even that half fails: every `pm` route is refused. |
| **A few China-market TCL sets** | They refuse to install any launcher at all; their lists say so in the header. |
| **Samsung (Tizen), LG (webOS), Roku, Vizio, Hisense VIDAA** | Not supported (no ADB). Turn off the tracking instead: [cleanyour.tv/not-android](https://cleanyour.tv/not-android). See [SHOWCASE.md](SHOWCASE.md#other-platforms-fire-tv-and-lg-webos) for four webOS ports, and [docs/devices/samsung-tizen.md](docs/devices/samsung-tizen.md) for what a 2021 Samsung set answers over Wi-Fi (no ADB, so no `pm disable-user`) if you are weighing a Tizen port. |

Tested lists from contributors, one folder per model in [`examples/`](examples/). The brands with the
most lists, each linked to its models on the website:

<!-- tv-brands:start: generated by tools/gen_tables.py from examples/*/tv.json; edit those, not this -->

| | | | | | |
|---|---|---|---|---|---|
| [TCL](https://cleanyour.tv/tvs?q=TCL) · 83 | [Sony](https://cleanyour.tv/tvs?q=Sony) · 49 | [Xiaomi](https://cleanyour.tv/tvs?q=Xiaomi) · 43 | [Philips](https://cleanyour.tv/tvs?q=Philips) · 23 | [Skyworth](https://cleanyour.tv/tvs?q=Skyworth) · 17 | [Hisense](https://cleanyour.tv/tvs?q=Hisense) · 16 |
| [SHARP](https://cleanyour.tv/tvs?q=SHARP) · 10 | [Amazon Fire TV](https://cleanyour.tv/tvs?q=Amazon%20Fire%20TV) · 9 | [Panasonic](https://cleanyour.tv/tvs?q=Panasonic) · 7 | [BenQ](https://cleanyour.tv/tvs?q=BenQ) · 6 | [Haier](https://cleanyour.tv/tvs?q=Haier) · 6 | [JVC](https://cleanyour.tv/tvs?q=JVC) · 6 |
| [Toshiba](https://cleanyour.tv/tvs?q=Toshiba) · 6 | [Acer](https://cleanyour.tv/tvs?q=Acer) · 4 | [Formovie](https://cleanyour.tv/tvs?q=Formovie) · 4 | [Nokia](https://cleanyour.tv/tvs?q=Nokia) · 4 | [OnePlus](https://cleanyour.tv/tvs?q=OnePlus) · 4 | [Thomson](https://cleanyour.tv/tvs?q=Thomson) · 4 |
| [Artel](https://cleanyour.tv/tvs?q=Artel) · 3 | [FFalcon](https://cleanyour.tv/tvs?q=FFalcon) · 3 | [Google](https://cleanyour.tv/tvs?q=Google) · 3 | [Grundig](https://cleanyour.tv/tvs?q=Grundig) · 3 | [Motorola](https://cleanyour.tv/tvs?q=Motorola) · 3 | [Noblex](https://cleanyour.tv/tvs?q=Noblex) · 3 |
| [NVIDIA Shield](https://cleanyour.tv/tvs?q=NVIDIA%20Shield) · 3 | [onn](https://cleanyour.tv/tvs?q=onn) · 3 | [RCA](https://cleanyour.tv/tvs?q=RCA) · 3 | [Redmi](https://cleanyour.tv/tvs?q=Redmi) · 3 | [RockTek](https://cleanyour.tv/tvs?q=RockTek) · 3 | [Vu](https://cleanyour.tv/tvs?q=Vu) · 3 |

<details>
<summary>+78 more brands and 14 projector brands</summary>

**Challenger** 2 · **CHiQ** 2 · **EKO** 2 · **Hitachi** 2 · **Insignia Fire TV** 2 · **Kalley** 2 · **Kogan** 2 ·
**Master-G** 2 · **PRISM+** 2 · **Sankey** 2 · **Sber** 2 · **Síragon** 2 · **TIM** 2 · **Vidda** 2 ·
AiPro2K · AiPro4K · AIWA · AQUA · ASUS · Beko · Bell Canada · BGH · Caxium · Cecotec · Compac · Dcolor · DEXP · Dialog ·
Durabrand · DYON · Essentiel b · Freebox · HAAM · Hikeen · Home+ · Impex · INCLO · Jio · Karbonn · Kodak · Koryo · KTC ·
KUNFT · Liberton · Lumio · LYNCAST · Magyar Telekom · MarQ · MECOOL · MTC · Next · OK. · OPPO · Panavox · PEAQ ·
Philco · Polytron · PPTV · Prosonic · Salora · SANYO · Seleco · Smarttech · STRONG · SunWind · Super General ·
TD Systems · Tele System · Tesla · TiVo · Tornado · Turkcell · Tuvio · Verizon · VISIO · Vision · Vityas · X.Vision ·
Alwtniet, Anker Nebula, BYINTEK, Chihi, Epson, Gadnic, HOTACK, HY320, JMGO, Magcubic, Portronics, Stellamore, Wanbo and XGIMI projectors

</details>

<!-- tv-brands:end -->

**Find your TV:** search [cleanyour.tv/tvs](https://cleanyour.tv/tvs) or the full table in
[examples/README.md](examples/README.md).

Your TV not here? The prompt discovers the bloat itself. Add your list afterwards and the next
person with your model skips that step: [CONTRIBUTING.md](CONTRIBUTING.md).

## Doing it by hand

Everything the assistant does, you can run yourself. Two things have to be on the computer first:

| You need | For |
|---|---|
| **Python 3** (any 3.x) | The tools. The venv command below installs what they import. |
| **JDK 17 through 22** (a full JDK, including `jlink`) and **Android SDK platform 34** with **build-tools 34.0.0 or any newer 34.x** | Building the launcher. JDK 23 and newer stops Gradle 8.9. Platform 34 is the one the build reads; a newer platform does not replace it. On macOS, when none of these is installed: `brew install openjdk@17 android-commandlinetools`, then `sdkmanager "platforms;android-34" "build-tools;34.0.0"`. On Linux the install script looks in `~/Android/Sdk` and for `java` on your PATH. |

`tools/tv.py` talks to network ADB on port 5555. If that port stays closed and the TV offers
Wireless debugging with a pairing code (the Chromecast with Google TV and the Google TV Streamer
do), pair it once with the stock `adb`. Then either run `adb tcpip 5555` to open the port, which
usually lasts only until the TV reboots, or keep it closed: point `TV_ADB_BINARY` at that same
`adb` and `TV_HOST` at the port the Wireless debugging screen shows, and the tools reach the TV
through the paired connection with nothing listening on 5555
([tools/native-adb.md](tools/native-adb.md)). [Troubleshooting](docs/TROUBLESHOOTING.md#connecting)
has both, with the pairing steps.

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50                                   # your TV's IP address

tools/venv/bin/python -I tools/tv.py --probe                 # model, Android version, firmware
tools/venv/bin/python -I tools/tv.py 'pm list packages'      # one shell command on the TV
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt        # apply a debloat list
tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt        # undo it, stock launcher back
tools/venv/bin/python -I tools/check.py examples/xiaomi-mi-tv-4a/debloat.txt   # what a firmware update undid
launcher/build-and-install.sh                                # build the launcher and make it Home (needs the TV_HOST export above)
tools/theme.sh themes/aurora                                 # push a theme; --reset removes it
python3 tools/wallpaper_prep.py ~/Pictures/trip mytheme/wallpapers   # your photos, sized and darkened for the TV
```

| Tool | What it does |
|---|---|
| [`tools/tv.py`](tools/tv.py) | Pure-Python ADB client: shell, batch, push, pull, reboot, and screenshots, optionally pressing remote keys first. Works where the stock `adb` binary is blocked. Optional [paired Google ADB backend](tools/native-adb.md) for encrypted Wireless debugging. |
| [`tools/docker-build.sh`](tools/docker-build.sh) | Builds the launcher or an add-on in Docker (JDK 17, Android SDK 34), so this computer needs nothing but Docker. Install the result with `SKIP_BUILD=1`. [Guide](tools/docker-build.md). |
| [`tools/b64push.py`](tools/b64push.py) | Uploads large files such as APKs in base64 chunks and verifies the checksum. |
| [`tools/relay.py`](tools/relay.py) | Forwards a local port to the TV's ADB port, for assistants whose sandbox can't reach your network. Listens on this computer only. [Guide](tools/relay.md). |
| [`tools/disable.sh`](tools/disable.sh), [`restore.sh`](tools/restore.sh) | Apply or undo a debloat list, the undo from the state recorded before the change. |
| [`tools/recovery.py`](tools/recovery.py) | Writes the record `disable.sh` needs to undo itself, and prints the restore batch. |
| [`tools/check.py`](tools/check.py) | Compares the TV with the list that was applied, for what a firmware update undid; `--fix` puts it back. |
| [`tools/match.py`](tools/match.py) | Reads a saved `pm list packages` and prints, for each package, how many lists in `examples/` disable it, how many keep it commented out, and their most common note; Android's core packages show as `never`. Offline, changes nothing (`tools/venv/bin/python -I tools/match.py packages.txt`). |
| [`tools/adb-closed.py`](tools/adb-closed.py) | Checks that the TV's network ADB port is closed once debugging is off: exits 0 when the TV refuses the connection, 1 while it is still open, 2 when nothing answered or the check itself could not run. No `nc` needed. |
| [`tools/philips-api.py`](tools/philips-api.py) | Philips only: checks that the JointSpace control API Home Assistant uses still answers, before and after a debloat (`tools/venv/bin/python -I tools/philips-api.py <tv ip>`). Port 1926 over HTTPS first, then 1925; reads only what answers without pairing. |
| [`tools/privacy.py`](tools/privacy.py) | Audits the TV for voice, ACR and telemetry candidates and prints the evidence for each; `--write` turns the findings into a list `disable.sh` and `restore.sh` already understand. |
| [`tools/theme.sh`](tools/theme.sh), [`theme_restart.py`](tools/theme_restart.py) | Push a folder from [`themes/`](themes/) to the launcher and restart Home. |
| [`tools/theme-catalog.sh`](tools/theme-catalog.sh) | Put several themes on the TV to switch between in Launcher settings, and keep this TV's own keys in a `device.json` laid over every theme ([how](themes/README.md#switching-themes-on-the-tv)). |
| [`tools/wallpaper_prep.py`](tools/wallpaper_prep.py) | Turns a folder of your own photos (JPEG, PNG, WebP, HEIC) into darkened 1280×720 JPEGs under 300 KB for a theme's `wallpapers/` folder. Needs Pillow, plus pillow-heif for HEIC (`tools/requirements-photos.txt`). Never touches the originals. [How to use it](#your-own-photos-as-the-background). |
| [`tools/manage.py`](tools/manage.py) | List every add-on, theme and switch with its screenshot, see what is on the TV, and install, update or remove one by running its creator's own script. |
| [`tools/bench.py`](tools/bench.py) | Measures the launcher that answers HOME: key latency, how far the focus moved, janky frames, cold start, return to Home, and the idle CPU and PSS it costs. Changes nothing on the TV and writes the raw per-key numbers to JSON; no extra install. |
| [`tools/trace.py`](tools/trace.py), [`trace_report.py`](tools/trace_report.py) | Records Perfetto traces of the launcher (return to Home, cold start, D-pad) and reports where its time goes, per run and before against after. Optional: needs [`requirements-perf.txt`](tools/requirements-perf.txt). [Guide](tools/trace.md). |
| [`tools/flamechart.py`](tools/flamechart.py) | Draws a before/after flame chart from those traces, for a pull request: one lane per thread on a shared time scale, the work a change targets in magenta with its total. Optional, like the trace tools. [Guide](tools/flamechart.md). |
| [`tools/screendiff.py`](tools/screendiff.py) | Compares a before and an after screenshot pixel by pixel, leaving out the clock or anything you mask, and writes the pair with your Wi-Fi name blurred. Optional, like the trace tools. [Guide](tools/screendiff.md). |
| [`tools/race_calendar.py`](tools/race_calendar.py) | Writes this season's F1 calendar into a theme for race-weekend mode, from the free Jolpica API. |
| [`launcher/build-and-install.sh`](launcher/build-and-install.sh) | Builds a shrunk release (about 572 KB), installs it, switches Home, and rolls back if Home does not land on it. |

The scripts never leave the TV without a Home app: they only disable a stock launcher they recognise,
and skip the Home app in a list until another launcher answers HOME. Your own lists can live outside
the repository: list paths are relative to the directory you run from, and absolute paths work too.

<details>
<summary><b>Windows (PowerShell)</b></summary>

Every script has a PowerShell twin next to it. Install Python 3 and a JDK from 17 through 22, and
in the SDK Manager add platform 34 and build-tools 34 (34.0.0 or any newer 34.x). The script uses
`JAVA_HOME` when it points at a JDK in that range, and otherwise Android Studio's bundled JDK when
that JDK is in the same range:

```powershell
python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.50
powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
powershell -ExecutionPolicy Bypass -File tools\restore.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost 192.168.1.50 themes\aurora
```

The last two are the undo and a theme push, the `tools/restore.sh` and `tools/theme.sh` lines the
sections above give in bash. On Google TV the undo takes the stock launcher as well:
`-Stock com.google.android.apps.tv.launcherx`.

</details>

<details>
<summary><b>How the scripts protect Home</b></summary>

The install scripts ask the TV which launcher owns Home and only disable one they recognise, the four
in `KNOWN_STOCK_LAUNCHERS`: `com.google.android.tvlauncher`, `com.google.android.apps.tv.launcherx`,
`com.google.android.leanbacklauncher` and Xiaomi's `com.mitv.tvhome` (PatchWall). Pass
`STOCK_LAUNCHER=<package>` (`-StockLauncher`) to override, or `none` to leave it alone.

`tools/disable.sh` (`disable.ps1`) applies the same rule before it disables anything:

- If the list contains the app that owns Home and no other app on the TV answers HOME, it skips that
  entry, says why, and disables the rest of the list. So a list applied before the launcher handover
  cannot leave the TV with nothing to start.
- The setup wizard, the settings apps, a vendor `FallbackHome` and factory-test apps (Xiaomi's
  `com.duokan.factorytest`) answer HOME too and don't count.
- On Google TV it also keeps the owner while setupwraith answers HOME and stays enabled (not listed, or
  kept for the Wi-Fi network): setupwraith answers ahead of an installed launcher that is not Home yet,
  so it would take Home.

Run the same list again once `launcher/build-and-install.sh` has made the new launcher Home and the
entry is applied; `FORCE_HOME=1` (`$env:FORCE_HOME`) disables it anyway.

</details>

## The microphone, and what can be said about it

"Which of these packages is listening?" has no honest one-line answer: a permission scan shows fifty
system packages with `RECORD_AUDIO` that record nothing, and ACR clients see the screen with no audio
permission at all. [`tools/privacy.py`](tools/privacy.py) reports what the TV actually says, with the
evidence for each voice, ACR and telemetry candidate:
`TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/privacy.py`, and `--write` turns them into a normal
debloat list. What the audit cannot do is prove intent: [docs/privacy.md](docs/privacy.md) explains why.

## The launcher

A small native home screen for Android TV: **about 572 KB**, plain Java, built for TVs with 1 GB of RAM.
No blur, no video, no network permission, no background services. The **TV** needs Android 8.0 (API 26)
or newer, the `minSdk` in [`launcher/app/build.gradle`](launcher/app/build.gradle); what is left on an older
set is in [Troubleshooting](docs/TROUBLESHOOTING.md).

| What | How it works |
|---|---|
| **Configure** | Dock order, hidden apps, Live TV app, 4 to 7 tiles per row, reduced motion. All in [`config.xml`](launcher/app/src/main/res/values/config.xml) |
| **Settings on the TV** | Hold OK on Settings for launcher settings. Optional `launcher_settings_button` makes a normal click open them directly; TV settings remain on the settings page and their app tile. |
| **Themes** | Background, wallpaper and layout from a `config.json` pushed over Wi-Fi, no rebuild. Twenty-eight ship in [`themes/`](themes/), the built-in `default/` included |
| **31 languages** | Follows the TV's language, with a fully mirrored right-to-left layout for Hebrew |
| **App library** | Optional: browse every installed app and add or remove Home shortcuts. See [the app library guide](docs/app-library.md) |
| **Tile menu** | On by default (`tile_menu`): hold OK on a tile for Move and App info, plus Remove from Home when the app library is on. Off, holding OK opens App info |
| **Module cards** | Any TV app can offer its own tile; you approve each one once. See [`docs/modules.md`](docs/modules.md) |
| **Add-ons** | Optional apps that plug in as module cards, kept in [`addons/`](addons/) and never built into the launcher |
| **Clock screensaver** | A drifting clock over the glow, so nothing burns in |
| **Remote keys** | Keeps the Input, YouTube and gear buttons working after the stock launcher is gone |

Themes, switches and add-ons together, with screenshots: [cleanyour.tv/addons](https://cleanyour.tv/addons).

**More:** [docs/launcher.md](docs/launcher.md) has the launcher settings page row by row, changing the
default home, the languages, remote keys, the Clock screensaver, navigation and building it yourself.

<a id="your-own-photos-as-the-background"></a>
<details>
<summary><b>Your own photos as the background</b></summary>

A theme can rotate a folder of pictures behind Home, with no rebuild. No pictures ship with the repo: you bring
your own, and you decide you have the right to show them.

1. **Start a theme folder.** Copy a theme you like from the [themes folder](themes/), for example
   `themes/aurora`, into a new folder such as `mytheme/`, or start with an empty `config.json`.
2. **Prepare the photos.** A bright 12 MP photo is heavy for a 1 to 2 GB TV and drowns the clock and tiles.
   `tools/wallpaper_prep.py` centre-crops to 16:9, saves 1280×720 JPEGs under 300 KB, and darkens the picture
   itself, so `wallpaper_dim` can stay at 0. HEIC phone photos work. Your originals are never touched.

   ```bash
   python3 -m pip install -r tools/requirements-photos.txt
   python3 tools/wallpaper_prep.py ~/Pictures/trip mytheme/wallpapers
   ```

   Options: `--brightness 0.25` (0.1 to 1.0), `--gradient 0.6` (extra black toward the dock), `--size 1280x720`,
   `--max-kb 300`.
3. **Set the rotation** in `mytheme/config.json`:

   ```json
   { "wallpaper": true, "wallpaper_rotation_minutes": 1, "wallpaper_order": "shuffle", "wallpaper_dim": 0 }
   ```

   | Key | Notes |
   |---|---|
   | `wallpaper_rotation_minutes` | Whole minutes, 0 to 1440. **1 is the fastest.** `0` means no rotation, and a fraction such as `0.25` is read as `0`, which stops it. |
   | `wallpaper_order` | `shuffle` never shows the same picture twice in a row; `sequential` follows the file names. |
   | `wallpaper_dim` | Black overlay in percent. Leave at 0 when the pictures are already darkened. |
   | `dock_glass`, `dock_opacity` | A frosted-glass dock band (30 to 100%) so the picture shows behind the tiles. |
   | `greeting_name` | A name after the greeting: "Good evening, Sam". |
   | `show_stats`, `app_library`, `reduced_motion` | A stats line under the clock, an App library pill, calmer motion for weak GPUs. |

4. **Push it to the TV** (this restarts Home once, and keeps the Home-key helper working):

   ```bash
   tools/theme.sh mytheme            # Windows: tools\theme.ps1 -TvHost 192.168.1.50 mytheme
   ```

   On the TV, **Launcher settings → Wallpaper** holds one picture, **None**, or **Theme default**, which
   rotates again.
5. **Take it off** with `tools/theme.sh --reset`.

What it cannot do: there is no crossfade (the picture changes with a cut), no rotation faster than a minute, and
the Clock screensaver shows time and date only, never photos. Without a `wallpapers/` folder the theme still
works and Home keeps its plain colour. A picture is decoded into memory while Home shows it: about 4 MB at 1080p
in the default 16-bit colour, 8 MB with `wallpaper_true_color` (see [themes/README.md](themes/README.md)), so keep
the pictures small on a 1 GB TV.

</details>

## Restoring your TV

Keep the list you applied. One line puts the TV back the way it was:

```bash
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt                                    # Android TV
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt com.google.android.apps.tv.launcherx  # Google TV
```

| What | How |
|---|---|
| **A record comes first** | Before `tools/disable.sh` changes anything it writes down what the TV looked like, in `~/.cleanyourtv/`. |
| **Restore replays it** | A package you had disabled yourself stays disabled, and Home goes back to the component that owned it. |
| **The launcher stays installed** | Remove it from the TV's Settings → Apps if you want it gone. |
| **No computer?** | If the TV is unreachable, its own Settings → Apps page can re-enable packages too. |

**More:** [docs/restore.md](docs/restore.md) has what the record holds, when the stock launcher argument
counts, and what happens without a record.

## After a firmware update

An update installs over the cleaned-up system: the listed packages come back, Home goes to the
stock launcher, and new preloads appear that no list mentions. Open ADB again as in
[Quick start](#quick-start) step 1, then compare the TV with the list you applied; `--fix` puts it back:

```bash
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py examples/<your tv>/debloat.txt
```

**More:** [docs/firmware-updates.md](docs/firmware-updates.md) has the exit codes, a weekly check, what
`--fix` changes, TVs that keep the stock launcher as Home, and lists that disable the updater.

## When something goes wrong

The full list of things that bit us is in [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md). The
three that matter most:

- 🍎 **macOS reports "No route to host" although the port is open**: Local Network privacy keeps the terminal app off the local subnet, and the permission belongs to that app rather than to the client, so the stock `adb` and `tools/tv.py` fail the same way (seen on macOS 26). Allow Terminal, iTerm2 or VS Code under System Settings → Privacy & Security → Local Network, then quit and reopen it ([Troubleshooting](docs/TROUBLESHOOTING.md#connecting)).
- ⚠️ **Disable the stock launcher only after the new one is installed and running**, or Home lands on a factory test app. The install script orders this for you.
- 🔁 **On Google TV the setup wizard** (`com.google.android.tungsten.setupwraith`) can take Home and crash-loop the system after the stock launcher is disabled. Disable it too, and list it in your `debloat.txt` so restore brings it back.
  - But check `dumpsys wifi | grep -o 'cname=[^ ]*'` first: if the Wi-Fi network was saved during setup it belongs to setupwraith, and disabling it deletes the network (re-join it in Settings first; `tools/disable.sh` skips setupwraith until you do).
  - On Google's own Chromecast and on the Google TV Streamer it also costs phone casting, see [Troubleshooting](docs/TROUBLESHOOTING.md).
  - Stuck already? `tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'`. On some sets (seen on a TCL Smart TV Pro, Android 14) this is one-way: getting back to this launcher then needs ADB, or keep launcherx and use `stock_home_redirect`, see [Troubleshooting](docs/TROUBLESHOOTING.md#working-on-the-tv).

## Questions people ask

<details>
<summary><b>I don't code and don't have a laptop. Can I still do this?</b></summary>

Yes. Independent developers have built free, open-source apps on CleanYourTV's TV lists that do the cleanup
over Wi-Fi, with no coding agent. Each shows a plan first, only switches apps off, and can put everything back:

| App | For | By |
|---|---|---|
| [Clean Your TV app](https://github.com/udhaykumarbala/cleanyourtv-app) | Android phone | [@udhaykumarbala](https://github.com/udhaykumarbala) |
| [Tellyscope](https://github.com/man15h/tellyscope) | Android phone | [@man15h](https://github.com/man15h) |
| [TidyTV](https://github.com/jolan94/tidytv) | Android phone, Google Play | [@jolan94](https://github.com/jolan94) |
| [CleanMyTV](https://github.com/wingedpanther/cleanmytv) | Mac and Windows | [@wingedpanther](https://github.com/wingedpanther) |

These apps are independent projects, not affiliated with or endorsed by CleanYourTV or its maintainer, and each
is provided as is under its own license. Report app problems in that app's repo. More at
[cleanyour.tv/app](https://cleanyour.tv/app).

</details>

<details>
<summary><b>Is this safe? Can it brick the TV?</b></summary>

Nothing is flashed, rooted or deleted; packages are disabled
in the user profile. The risk is disabling something the TV leans on, which is why lists are
applied in small batches with a restore script ready. The TV's own Settings → Apps page can
re-enable packages even without the tools. If the TV no longer starts far enough for that, a factory
reset from its recovery menu is usually the way back.

More: [docs/faq.md](docs/faq.md#is-this-safe-can-it-brick-the-tv)

</details>

<details>
<summary><b>What about firmware updates?</b></summary>

The updater is left on in most lists, so firmware updates arrive as usual. Some lists disable the
TV's own updater, and on those a firmware update arrives only when you re-enable it. When one does
land it installs over the cleaned-up system, so a package comes back and you re-run the script: see
[After a firmware update](#after-a-firmware-update).

</details>

<details>
<summary><b>Won't the apps break?</b></summary>

Streaming apps still update through the Play Store, which is left alone.
The launcher just lists what is installed and launches it with intents that have been stable for a decade.

</details>

<details>
<summary><b>How do I rearrange my apps?</b></summary>

Hold OK on **Settings** (top right) and choose **Reorder apps**. OK picks an app up, the arrows move it,
Back places it, and Back again finishes. Holding OK on a tile also offers **Move** (`tile_menu`, on by default), which
picks that app up straight away. More: [docs/launcher.md](docs/launcher.md#launcher-settings-and-the-default-home)

</details>

<details>
<summary><b>Can I do this for someone in another home?</b></summary>

Yes, over a private [Tailscale](https://tailscale.com)
link instead of the same Wi-Fi: either Tailscale on their TV, shared with you, or a computer or Apple
TV at their home routing to it. Never by opening the ADB port on their router. Step by step in
[docs/REMOTE.md](docs/REMOTE.md).

</details>

<details>
<summary><b>Isn't ADB access a security hole?</b></summary>

It is one explicit "allow this computer" tap on the TV, the
same as developing an Android app. Network ADB is plaintext on port 5555, and on some firmware any
device on the LAN gets a shell, so close it when you are done:

1. On the TV: Developer options → turn **USB debugging**, **Network/ADB debugging** and
   **Wireless debugging** off, whichever the TV has, and revoke the USB debugging authorisations.
2. Reboot the TV, then confirm the port is gone from your computer:
   `python3 tools/adb-closed.py <tv ip>` must say the port is closed.

The launcher keeps working without debugging. More, including checking Wireless debugging's own port:
[docs/faq.md](docs/faq.md#isnt-adb-access-a-security-hole)

</details>

<details>
<summary><b>Can it block ads inside apps too?</b></summary>

Partly, with Android's Private DNS (Android 9 and newer). Point
it at an ad-blocking resolver such as [AdGuard DNS](https://adguard-dns.io) and every app's ad and
tracker domains stop resolving, system-wide, surviving reboots. The trade-off: that resolver then
sees every DNS query the TV makes, and it does not remove YouTube ads.

More, with the two commands and how to switch back: [docs/faq.md](docs/faq.md#can-it-block-ads-inside-apps-too)

</details>

<details>
<summary><b>Are there switches in the TV's own Settings worth flipping too?</b></summary>

A few, and they need no ADB: the advertising ID, Location, Usage & diagnostics and Scanning always
available, plus Apps only mode where the stock launcher stays. Flip Usage & diagnostics before the
debloat; on some TVs its page goes with it.

More, with the paths for Google TV, Android TV and Fire OS:
[docs/faq.md](docs/faq.md#are-there-switches-in-the-tvs-own-settings-worth-flipping-too)

</details>

<details>
<summary><b>AirPlay?</b></summary>

Not from this repo. Chromecast built-in stays for Android phones. For iPhones,
[jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server) works; an install
script for it is in [PR #84](https://github.com/Gathin23/cleanyourtv/pull/84).

</details>

<details>
<summary><b>Can I just download the APK?</b></summary>

Build it with the one-line script in [Doing it by hand](#doing-it-by-hand); it takes a minute and you
control what goes on your TV. That needs the JDK and Android SDK named there. No prebuilt binaries.

</details>

## The story

On 17 October 2026 I posted [a before and after](https://x.com/gathint/status/2111379569030939102) of my
parents' TV, cleaned up by Claude Code over Wi-Fi. It reached 8.6M views, people started doing the same to
their own TVs, and their replies turned into the lists here: 170+ TV models from 200+ contributors in the
first four days. The repo was called tv-vibehack until the 19th; old links work.

Two weeks on, it was the #1 trending repository on GitHub for the week of 19–25 October, with over 46K stars
and 5.7K forks, and 1.4M views and 260K clones in 14 days. The lists now cover 456 TV models from 122 brands.
The coverage: 412 articles from 54 countries and 229 videos on YouTube and Bilibili, all in [docs/PRESS.md](docs/PRESS.md)
(numbers as of 31 October 2026).

## Contributing

The most useful contribution is your TV's debloat list, so the next person with that model does
not have to rediscover it. [CONTRIBUTING.md](CONTRIBUTING.md) has the format and the testing we ask for.
Contributing through a coding agent? [AGENTS.md](AGENTS.md) gives it the same rules.

| How to help | What it means |
|---|---|
| **Your TV's debloat list** | The most useful one: the next person with that model skips the discovery. |
| **Launcher and prompt** | Launcher improvements and sharper prompt wording are welcome too. |
| **Built something on top?** | A launcher, a port, a theme: add it to [SHOWCASE.md](SHOWCASE.md), where two hundred and twenty projects already live, sorted into home screens, forks, add-ons, tools and other platforms, with an index by TV brand. |

The people whose work is in this repo:

<a href="https://github.com/Gathin23/cleanyourtv/graphs/contributors"><img src="https://contrib.rocks/image?repo=Gathin23/cleanyourtv&columns=16&max=400" alt="Contributors"></a>

<details>
<summary><strong>Star history</strong></summary>

<a href="https://star-history.com/#Gathin23/cleanyourtv&Date"><img src="https://api.star-history.com/svg?repos=Gathin23/cleanyourtv&type=Date" alt="Star history chart" width="640"></a>

</details>

## Contact

| For | Where |
|---|---|
| **Questions about your TV** | [GitHub Discussions](https://github.com/Gathin23/cleanyourtv/discussions) |
| **Press and queries** | [hello@itsgathin.com](mailto:hello@itsgathin.com) |
| **Security issues** | Report them privately through the [Security tab](https://github.com/Gathin23/cleanyourtv/security), see [`SECURITY.md`](SECURITY.md) |

## Legal

- CleanYourTV is an independent open-source project. It is not affiliated with, endorsed by or sponsored
  by any TV maker or platform. Brand and model names are used only to say which TV a list was tested on,
  and remain the trademarks of their owners.
- It works only through settings the TV itself offers to its owner: Developer options, ADB debugging and
  Android's own `pm disable-user`, the same switch as Settings > Apps > Disable. It does not root the TV,
  flash firmware, or bypass any lock, DRM or copy protection, and the repo contains no manufacturer
  software, firmware or APKs.
- Nothing is uninstalled, and `tools/restore.sh` puts everything back. You run it on a TV you own, at your
  own risk, and changing a TV's software may affect its warranty or support.
- Everything here is provided as is, without warranty of any kind, under the MIT licence below.

## Licence

MIT, see [`LICENSE`](LICENSE). The Outfit typeface is under the SIL Open Font License, see
`launcher/FONT-LICENSE-OFL.txt`.
