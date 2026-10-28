# AGENTS.md

Instructions for coding agents (Claude Code, Codex, Gemini CLI, Cursor, Copilot and others) working in
this repository. People should start with [README.md](README.md) and [CONTRIBUTING.md](CONTRIBUTING.md);
this file repeats their rules in the form an agent needs, and links to them for the detail.

## First: which job are you doing?

| You were asked to | Do this |
|---|---|
| Clean up a TV: debloat it, install the launcher, push a theme | Read [PROMPT.md](PROMPT.md) and follow it exactly, on the real TV. Use the tools in `tools/` and the launcher in `launcher/` rather than writing new ones; the only file you need to change is `launcher/app/src/main/res/values/config.xml`, or a theme's `config.json`, for this TV. Do not edit the repo's docs, tests or other code, and do not open issues or pull requests unless the person asks you to. |
| Change this repo: a TV list, a fix, a translation, a theme, docs | Skip the next section and read on from [Repo map](#repo-map). |

### Cleaning a TV: the rules that are not negotiable

[PROMPT.md](PROMPT.md) is the procedure. Its safety rules, in brief:

- **Disable, never uninstall.** `pm disable-user --user 0`, one package per shell call. No
  `pm uninstall`, no root, nothing flashed. Every step must be undone by `tools/restore.sh`.
- **Back up first.** Package list, disabled list, `getprop` and settings into `backup/` before the first
  change. `tools/disable.sh` also writes its own record in `~/.cleanyourtv/`; keep the list you applied.
- **Leave alone what the TV needs to work:** the TV app and input services behind HDMI and the tuner,
  the input/source menu, remote and Bluetooth pairing, the on-screen keyboard, Play Store and Play
  Services. When a package name says nothing, read `dumpsys package <name>`, then ask.
- **Never disable Android's core packages:** the permission controller, package installer, ext
  services/shared and module metadata (`com.android.*` and `com.google.android.*`; ext services/shared
  also as the bare `android.ext.services` and `android.ext.shared`),
  `com.google.android.overlay.modules*`, `com.android.providers.*` (calendar, contacts, user dictionary
  and blocked numbers aside), `com.android.systemui`, `com.android.shell`, `com.android.phone`,
  `com.android.settings`, `com.android.tv.settings`, `android`. Without them Android may not boot (#884);
  `tools/disable.sh`, `disable.ps1` and `check.py --fix` refuse such a list, with no override.
- **Never start a factory or test package** (`com.cvte.tv.hkcfac`, `mediatek.factorymenu.ui` and the like),
  not even to look. On some boards that flips a factory-mode flag the shell cannot undo.
- **Keep the Home guard.** The stock launcher is disabled only after the new launcher is installed and
  confirmed as Home (`dumpsys activity activities | grep mFocusedApp`). On Google TV, check
  `dumpsys wifi | grep -o 'cname=[^ ]*'` before disabling setupwraith: if it owns the saved Wi-Fi
  network, disabling it takes the TV off Wi-Fi and ADB. `tools/disable.sh` and
  `launcher/build-and-install.sh` already enforce this. Use `FORCE_HOME=1` or `FORCE_SETUPWRAITH=1` only in
  the situation the script's own message describes, and only with the person's agreement.
  - On a CVTE board (any `com.cvte.*` package), leave its stock launcher (`com.cvte.tv.launcher.*`) enabled
    unless the `examples/` folder for that exact board disables it. Make the new launcher Home with
    `cmd package set-home-activity`, reboot once, and confirm Home and ADB come back before anything else.
    Don't enable a remote-key helper in the same pass: a JVC set hung on its boot animation with ADB gone
    after both were done before one reboot (#741).
- **Ask before anything the family uses.** Streaming apps, the voice assistant, Chromecast, Miracast and
  the firmware updater go into one checklist question; disable only what the person does not tick.
- **Disable in small batches** and screenshot between them (`tools/tv.py --screenshot shots/01.png`).
  Live TV, HDMI and some streaming apps give empty screenshots; that is the TV, not damage.
- **An injected key is not proof** for a remote button that goes through an accessibility helper: ask the
  person to press the real button. `am force-stop com.gathin.tvhome` switches those helpers off.
- **Close ADB afterwards.** Wireless debugging and Network/ADB debugging off, and USB debugging unless
  the person asks to keep it; authorisations revoked. Wireless debugging has its own port
  (`adb mdns services` lists it as `_adb-tls-connect._tcp`): check it with
  `tools/adb-closed.py <tv ip> <port>` straight after switching it off, before the reboot, as it takes
  a new port each start. Then reboot: `tools/adb-closed.py <tv ip>` must report 5555 closed, with
  Wireless debugging still off. If 5555 still answers with USB debugging kept on, switch that off too.
  Only the tool's "closed" counts. Port 5555 is plaintext.
- **Stop and say so** when the TV refuses: `pm disable-user` refused for every package, a sideload that
  fails verification, or an SDK below the launcher's `minSdk` 26. Do not retry in a loop, and do not lower
  `minSdk` or hand over an APK built elsewhere.
- **Not an Android TV?** No ADB port at all, or the set identifies as Tizen, webOS, Roku OS, VIDAA or
  SmartCast: stop, nothing here applies. Send the person to https://cleanyour.tv/not-android, which turns
  off the tracking with the remote.

The person's backups (`backup/`), screenshots (`shots/`), notes (`work/`) and the ADB key
(`tools/adbkey/`) are listed in `.gitignore` and must never be committed. If the person wants to
share their TV's list afterwards, follow [`docs/workflows/tv-list.md`](docs/workflows/tv-list.md).

## Repo map

| Path | What it is |
|---|---|
| `PROMPT.md` | The prompt people paste into their agent. Brand-neutral; safety rules live here. |
| `README.md` | Front page, with the TV table (generated from `examples/*/tv.json` by `tools/gen_tables.py`) and the Tools table. `README.zh-CN.md` and `README.vi.md` are translations. |
| `CONTRIBUTING.md` | The contributor guide for people. "Where does my idea go?" is the routing table. |
| `SECURITY.md` | Private vulnerability reporting. |
| `SHOWCASE.md` | Projects built on this repo that live in their own repositories. |
| `CODE_OF_CONDUCT.md` | Includes the rule against publishing device identifiers, Wi-Fi names and IPs. |
| `examples/` | One folder per tested TV model: `debloat.txt`, `tv.json` (its row in the TV tables), sometimes a `README.md` and a `config.json`. Indexed in `examples/README.md`, whose table is generated too. |
| `docs/` | `docs/TROUBLESHOOTING.md`, the module-card contract (`docs/modules.md`), `docs/devices/` for models with notes but no tested list, and the [workflows](#workflows). |
| `launcher/` | The reference launcher, package `com.gathin.tvhome`: plain Java, AndroidX RecyclerView and DynamicAnimation, `minSdk` 26, no `INTERNET` permission. |
| `themes/` | One folder per theme: `config.json`, a required `screenshot.jpg`, optional wallpapers. Keys are documented in `themes/README.md`. |
| `addons/` | Optional module-card apps, each its own Gradle project, never built into the launcher. |
| `tools/` | The ADB client (`tv.py`) and the scripts, listed in the README's Tools table. Shell scripts have PowerShell twins. Each script's usage is in its first lines, and the larger ones have a guide beside them (`tools/*.md`): read it before running the script. |
| `tests/` | Python `unittest` suite: list format, README tables, press counts, themes, add-ons, the Home guard and the tools against fake TVs. |
| `.github/workflows/` | `ci.yml` (tests, launcher build, APK size gate), `addons.yml`, `maintainer-bot.yml`. |
| `.github/maintainer/MAINTAINER.md` | The review policy the maintainer and the PR bot apply. |
| `.github/pull_request_template.md` | The PR checklist. Fill it in. |

## Where a change goes

The core stays small on purpose. Pick the smallest home that fits (CONTRIBUTING, "Where does my idea go?"):

| The change | Where it goes |
|---|---|
| A TV list, a fix, a translation, docs, a tool fix | A pull request here |
| A small launcher switch that is off by default | A pull request here: see [`docs/workflows/launcher-switch.md`](docs/workflows/launcher-switch.md) |
| A new look: colours, wallpaper, dock, columns, that existing config keys can carry | `themes/<name>/` |
| A widget or tile that can be its own small app | `addons/<name>/`, as a module card that never touches `launcher/` |
| A whole new launcher, a heavy restyle, a port to another platform, a lower `minSdk`, new permissions or network access, Fire TV Home takeover, a changed default for every TV | Your own repository, plus one row in `SHOWCASE.md` |
| Safety rules in `PROMPT.md`, the Home-safety logic in the scripts, protected packages, CI and workflows, the APK size gate | Open an issue for the maintainer; do not change it in a PR |

Every PR is reviewed by the maintainer and by a bot that apply `.github/maintainer/MAINTAINER.md`: read its
verdicts before a non-trivial PR. Changes to the Home or launch path (Home key, boot, return to Home, the
install scripts) are usually held for a run on a real TV.

## Workflows

Step-by-step methods in `docs/workflows/`. Read the one that fits before you start; each opens with when to
use it and ends with the check to run.

- [`tv-list.md`](docs/workflows/tv-list.md): add a tested TV's debloat list with its `tv.json`, or update one.
- [`launcher-switch.md`](docs/workflows/launcher-switch.md): add an off-by-default launcher switch, within the size gate.
- [`translation.md`](docs/workflows/translation.md): add a launcher language, or fill strings missing from one.
- [`theme.md`](docs/workflows/theme.md): add a theme from existing keys, with its screenshot from a real TV.
- [`addon.md`](docs/workflows/addon.md): add a module-card app that never touches `launcher/`.
- [`showcase.md`](docs/workflows/showcase.md): list a project that lives in its own repository.
- [`press.md`](docs/workflows/press.md): add an article and update the four press counts.
- [`docs-notes.md`](docs/workflows/docs-notes.md): add a troubleshooting entry or a `PROMPT.md` fix, keeping pinned facts.
- [`perf-ab.md`](docs/workflows/perf-ab.md): an optional check of a launcher change for performance regressions with
  `tools/bench.py`, and measure an improvement or a failed check with traces, a report and flame charts.

## Before opening a PR

| Check | Command or rule |
|---|---|
| Tests, as CI runs them | `tools/venv/bin/python -m unittest discover -s tests -v` after `python3 -m venv tools/venv` and `tools/venv/bin/pip install -r tools/requirements.txt`. On `main`, CI sets `CLEANYOURTV_STRICT=1`, and its "README tables" job checks that the TV tables and counts are what `tools/gen_tables.py` writes. On Windows the suites that drive `tools/*.sh` report as skipped: they need a POSIX shell, and CI runs them. |
| Launcher, if you touched `launcher/` | JDK 17, Android SDK platform 34, build-tools 34.0.0. In `launcher/`: `./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease`. Lint must stay at zero errors. |
| APK size gate | `launcher/app/build/outputs/apk/release/app-release.apk` must be under 589,824 B (576 KB). Linux: `stat -c %s`, macOS: `stat -f %z`. |
| README rows | A new list adds only its folder, with a `tv.json`: the TV tables, the brand line and the counts are generated after merging, so leave them alone. A theme has its row in `themes/README.md`; an add-on in `addons/README.md`. |
| One topic per PR | A TV list never rides inside a launcher feature PR. A device's list plus small fixes for that same device can be one PR. |
| Tested on | Say which TV, Android version and firmware you tested on, in the PR template's "Tested on" line. |
| Real TV | Launcher changes are verified on a real TV, not only the emulator: `tools/tv.py --screenshot shots/after.png DPAD_DOWN`. D-pad from every edge (Up, Down, Left, Right). A button handled by an accessibility helper needs a press on the real remote, and a force-stop of the launcher switches the helpers off (`docs/TROUBLESHOOTING.md`). |
| Screenshots | Every visual change has a screenshot from the TV in the PR description. |
| Performance | A performance claim shows `tools/bench.py` before and after, plus trace numbers. For any other launcher change the regression check in [`docs/workflows/perf-ab.md`](docs/workflows/perf-ab.md) is optional but welcome. |
| Stray files | No files that nothing links to: screenshots belong in the PR description, not the tree. |

## Hard rules

Never do these in a PR:

- **No prebuilt APKs or binaries** in the repo, not even as CI artifacts. People build from source.
- **No uninstalling.** No `pm uninstall` in a list or a script. `pm hide` or `pm block` only with the matching
  undo written in the list header.
- **No network in the launcher:** no `INTERNET` permission, no network features, no analytics or telemetry, no
  background services or polling, no blur or video. It targets TVs with 1 GB of RAM.
- **No new launcher dependencies** without discussing them in an issue first.
- **No changes to the safety rules** in `PROMPT.md`, the Home guard and other safety checks in
  `tools/disable.sh`, `tools/check.py` and `launcher/build-and-install.sh`, or the list of packages the TV
  needs, without the maintainer.
- **No changing a default for every TV.** New behaviour is off until a theme or `config.xml` turns it on.
- **Firmware updaters are better left enabled** (commented out in the list); a list that disables one says
  why in its header.
- **No Private DNS or AdGuard setup, and no install or restore scripts, inside a list folder.** The README
  already has the optional DNS extra; a note that Private DNS works on that TV is fine.
- **No secrets or identifiers** in commits, lists or screenshots: no IP addresses, Wi-Fi names, account emails,
  serial numbers, Android IDs, MAC addresses or ADB keys. Use `<tv ip>` or `192.168.1.50` in docs.
- **Don't edit `.github/workflows/`** or the APK size gate in a normal PR. A PR whose main content is a new CI
  workflow that builds or publishes releases is closed.
- **No root, no getting around app-store protection, no private builds** others cannot reproduce from this repo.
- **No extras added to the PROMPT flow** beyond debloating and the launcher.
- **Don't relax `minSdk`** (26) or the launcher's permissions; those are forks for `SHOWCASE.md`.
- **Blur or crop** Wi-Fi names, people's names and cities (the top bar, a theme's `greeting_name`) before a
  screenshot goes anywhere public. No TV Settings screenshots that show accounts, emails or device names.
  From `tools/tv.py --probe`, `getprop` and backups, quote only the model, Android version and firmware build.
- **Security problems are reported privately**, as [SECURITY.md](SECURITY.md) describes, never in a public issue.

## Commit and PR style

- Titles are `area: what changed`, in plain words, with the issue in parentheses when there is one:
  `examples: add Epson LS800 laser projector (Askey HA90, Amlogic), Android TV 14`,
  `docs: an HDMI row opens any app that claims the channel MIME type (#805)`,
  `Launcher: show_inputs_pill, to hide the Inputs pill`. The area is the folder or file the change is in:
  `examples`, `Launcher`, `docs`, `tools`, `tests`, `themes`, `addons`, `SHOWCASE`, `README`, `Press`.
- The body is one or two sentences on what changed and the TV it was tried on, then the template's checklist.
  `Fixes #N` or `Closes #N` when it resolves an issue.
- One topic per PR. Small is fine. Be kind in reviews.
- Contributions are released under the repo's MIT licence.

## Where to ask

Questions go to [GitHub Discussions](https://github.com/Gathin23/cleanyourtv/discussions); bugs, TV lists and
ideas that need a launcher change or a new dependency go to an issue first, with the templates in
`.github/ISSUE_TEMPLATE/`.
