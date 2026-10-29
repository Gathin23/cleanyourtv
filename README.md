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
