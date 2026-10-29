Start here
You have	Go to
An Android TV or Google TV	Quick start: a coding assistant does the work in about fifteen minutes.
A Samsung, LG, Roku, Vizio or Hisense VIDAA TV	cleanyour.tv/not-android: CleanYourTV can't run there, but you can turn off the tracking with your remote.
Only an Android phone	The community-built Clean Your TV app: no laptop, no coding agent.
Want to see it first	What you get: screenshots of the new home screen.
Official links: cleanyour.tv and this repo. Other domains using the name, such as cleanyourtv.com, are not run by this project (#1004).

How it works · Quick start · What you get · Supported TVs · Doing it by hand · The launcher · Restoring · Firmware updates · Questions · Contributing

How it works
Android TVs have the same debug bridge as Android phones. Switch on USB debugging in the TV's developer options, allow your computer once, and the rest is standard Android tooling:

Step	What happens
Back up	The list of installed packages and settings.
Disable	The manufacturer's telemetry, ad services, content rows and preloaded junk, with pm disable-user. Nothing is uninstalled, so a restore script can undo every step.
Install	A small native launcher, made the default home screen.
No root, no cable, nothing uninstalled, and a one-line restore if you change your mind.

The bloat differs by brand, so the prompt has your coding assistant discover it on your TV and ask before touching anything you might use.

Quick start
You have	Do this
A computer	Use the prompt with a coding assistant: the three steps below.
A computer, no coding assistant	Run the three steps yourself: the commands are in Doing it by hand.
Only an Android phone	Use the community-built Clean Your TV app by @udhaykumarbala: connect, analyse, clean, in about five minutes. No laptop, no coding agent.
The three steps below are written for a coding assistant: the computer needs to be on the same Wi-Fi as the TV, with a shell on it, plus Python 3, a JDK from 17 through 22, and Android SDK platform 34 with build-tools 34 (the assistant builds the launcher there). Without an assistant, Doing it by hand is the same job, run by you, and it has the install commands.

Any of these work: Claude Code (on 0G Private Computer or an Anthropic plan), Codex, Gemini CLI or Antigravity CLI.

Where	What to do
1. On the TV	Settings → Device Preferences → About → press OK on Build seven times → back → Developer options → USB debugging on. If you see a Network or ADB debugging toggle, turn that on too. Keep the TV on.
2. On your computer	Clone the repo and open that folder in your assistant (git clone https://github.com/Gathin23/cleanyourtv), then paste PROMPT.md and fill in your TV model.
3. Follow along	Accept "Allow USB debugging?" on the TV, then answer the checklist: which apps you use, the dock size and app order, whether to keep the voice assistant, Chromecast and updates.
Cloning matters: the assistant reuses this repo's tools/disable.sh and launcher/build-and-install.sh instead of writing its own, and that is where the backup, the Home guard and the restore record live.

The assistant backs up, debloats, builds and installs the launcher, and checks each step with screenshots pulled from the TV. With this repo as context it takes about fifteen minutes.

What you get
A home screen that looks like an Apple TV and gets out of the way:

Part	What it looks like
A pitch-black home	A dock of your four favourite apps in a rounded band, where Apple TV puts it.
A top shelf	Names the focused app in large type, with an ambient glow in a colour taken from its artwork.
The rest of your apps in a grid	Slides up when you press Down.
One floating glass highlight	Springs from tile to tile. The focused tile lifts and shows its name.
Three frosted buttons	Inputs (HDMI ports under the names you gave them in the TV's settings, AV, Live TV), Wi-Fi or Ethernet with the network name, Settings.
A greeting, a thin clock and the date	In Outfit, and a matching Clock screensaver. Set show_stats in config or a theme for one line under the clock: CPU, free memory and free storage. It stays off unless you turn it on, and it refreshes only while Home is on screen.
Themes and module cards	Themes change colours, wallpaper, dock and columns without a rebuild, and module cards let other apps plug a tile in, only with your say-so.
And a TV that stops working against you: no sponsored rows, no "Discover" nags, no analytics phoning home, no apps you never asked for.

Doing it by hand
Everything the assistant does, you can run yourself. Two things have to be on the computer first:

You need	For
Python 3 (any 3.x)	The tools. The venv command below installs what they import.
JDK 17 through 22 (a full JDK, including jlink) and Android SDK platform 34 with build-tools 34.0.0 or any newer 34.x	Building the launcher. JDK 23 and newer stops Gradle 8.9. Platform 34 is the one the build reads; a newer platform does not replace it. On macOS, when none of these is installed: brew install openjdk@17 android-commandlinetools, then sdkmanager "platforms;android-34" "build-tools;34.0.0". On Linux the install script looks in ~/Android/Sdk and for java on your PATH.
tools/tv.py talks to network ADB on port 5555. If that port stays closed and the TV offers Wireless debugging with a pairing code (the Chromecast with Google TV and the Google TV Streamer do), pair it once with the stock adb and run adb tcpip 5555 first, as Troubleshooting describes.

python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50                                   # your TV's IP address

tools/venv/bin/python -I tools/tv.py --probe                 # model, Android version, firmware
tools/venv/bin/python -I tools/tv.py 'pm list packages'      # one shell command on the TV
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt        # apply a debloat list
tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt        # undo it, stock launcher back
tools/venv/bin/python -I tools/check.py examples/xiaomi-mi-tv-4a/debloat.txt   # what a firmware update undid
TV_HOST=192.168.1.50 launcher/build-and-install.sh           # build the launcher and make it Home
tools/theme.sh themes/aurora                                 # push a theme; --reset removes it
Tool	What it does
tools/tv.py	Pure-Python ADB client: shell, batch, push, pull, reboot, and screenshots, optionally pressing remote keys first. Works where the stock adb binary is blocked. Optional paired Google ADB backend for encrypted Wireless debugging.
tools/docker-build.sh	Builds the launcher or an add-on in Docker (JDK 17, Android SDK 34), so this computer needs nothing but Docker. Install the result with SKIP_BUILD=1. Guide.
tools/b64push.py	Uploads large files such as APKs in base64 chunks and verifies the checksum.
tools/relay.py	Forwards a local port to the TV's ADB port, for assistants whose sandbox can't reach your network. Listens on this computer only. Guide.
tools/disable.sh, restore.sh	Apply or undo a debloat list, the undo from the state recorded before the change.
tools/recovery.py	Writes the record disable.sh needs to undo itself, and prints the restore batch.
tools/check.py	Compares the TV with the list that was applied, for what a firmware update undid; --fix puts it back.
tools/adb-closed.py	Checks that the TV's network ADB port is closed once debugging is off: exits 0 when the TV refuses the connection, 1 while it is still open, 2 when nothing answered or the check itself could not run. No nc needed.
tools/privacy.py	Audits the TV for voice, ACR and telemetry candidates and prints the evidence for each; --write turns the findings into a list disable.sh and restore.sh already understand.
tools/theme.sh, theme_restart.py	Push a folder from themes/ to the launcher and restart Home.
tools/manage.py	List every add-on, theme and switch with its screenshot, see what is on the TV, and install, update or remove one by running its creator's own script.
tools/bench.py	Measures the launcher that answers HOME: key latency, how far the focus moved, janky frames, cold start, return to Home, and the idle CPU and PSS it costs. Changes nothing on the TV and writes the raw per-key numbers to JSON; no extra install.
tools/trace.py, trace_report.py	Records Perfetto traces of the launcher (return to Home, cold start, D-pad) and reports where its time goes, per run and before against after. Optional: needs requirements-perf.txt. Guide.
tools/flamechart.py	Draws a before/after flame chart from those traces, for a pull request: one lane per thread on a shared time scale, the work a change targets in magenta with its total. Optional, like the trace tools. Guide.
tools/screendiff.py	Compares a before and an after screenshot pixel by pixel, leaving out the clock or anything you mask, and writes the pair with your Wi-Fi name blurred. Optional, like the trace tools. Guide.
tools/race_calendar.py	Writes this season's F1 calendar into a theme for race-weekend mode, from the free Jolpica API.
launcher/build-and-install.sh	Builds a shrunk release (about 532 KB), installs it, switches Home, and rolls back if Home does not land on it.
The scripts never leave the TV without a Home app: they only disable a stock launcher they recognise, and skip the Home app in a list until another launcher answers HOME. Your own lists can live outside the repository: list paths are relative to the directory you run from, and absolute paths work too.

Windows (PowerShell)
How the scripts protect Home
The microphone, and what can be said about it
"Which of these packages is listening?" has no honest one-line answer: a permission scan shows fifty system packages with RECORD_AUDIO that record nothing, and ACR clients see the screen with no audio permission at all. tools/privacy.py reports what the TV actually says, with the evidence for each voice, ACR and telemetry candidate: TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/privacy.py, and --write turns them into a normal debloat list. What the audit cannot do is prove intent: docs/privacy.md explains why.

The launcher
A small native home screen for Android TV: about 532 KB, plain Java, built for TVs with 1 GB of RAM. No blur, no video, no network permission, no background services. The TV needs Android 8.0 (API 26) or newer, the minSdk in launcher/app/build.gradle; what is left on an older set is in Troubleshooting.

What	How it works
Configure	Dock order, hidden apps, Live TV app, 4 to 7 tiles per row, reduced motion. All in config.xml
Settings on the TV	Hold OK on Settings for launcher settings. Optional launcher_settings_button makes a normal click open them directly; TV settings remain on the settings page and their app tile.
Themes	Background, wallpaper and layout from a config.json pushed over Wi-Fi, no rebuild. eleven ship in themes/, the built-in default/ included
30 languages	Follows the TV's language, with a fully mirrored right-to-left layout for Hebrew
App library	Optional: browse every installed app and add or remove Home shortcuts. See the app library guide
Tile menu	Optional (tile_menu): hold OK on a tile for Move, Remove from Home and App info. Off, holding OK opens App info
Module cards	Any TV app can offer its own tile; you approve each one once. See docs/modules.md
Add-ons	Optional apps that plug in as module cards, kept in addons/ and never built into the launcher
Clock screensaver	A drifting clock over the glow, so nothing burns in
Remote keys	Keeps the Input, YouTube and gear buttons working after the stock launcher is gone
Themes, switches and add-ons together, with screenshots: cleanyour.tv/addons.

More: docs/launcher.md has the launcher settings page row by row, changing the default home, the languages, remote keys, the Clock screensaver, navigation and building it yourself.

Restoring your TV
Keep the list you applied. One line puts the TV back the way it was:

TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt                                    # Android TV
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt com.google.android.apps.tv.launcherx  # Google TV
What	How
A record comes first	Before tools/disable.sh changes anything it writes down what the TV looked like, in ~/.cleanyourtv/.
Restore replays it	A package you had disabled yourself stays disabled, and Home goes back to the component that owned it.
The launcher stays installed	Remove it from the TV's Settings → Apps if you want it gone.
No computer?	If the TV is unreachable, its own Settings → Apps page can re-enable packages too.
More: docs/restore.md has what the record holds, when the stock launcher argument counts, and what happens without a record.
