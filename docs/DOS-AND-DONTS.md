# Do's and don'ts

These are the reasons pull requests most often came back for changes or were closed. They come from the
maintainer's reviews, and each line links an example. None of this replaces [CONTRIBUTING.md](../CONTRIBUTING.md):
it is the short list of mistakes to skip. When in doubt, ask in an issue before you build. "Thanks for asking
first" is a common reply.

## Before you start: pick the right home

| Do | Don't |
|---|---|
| Put a widget or tile that can be its own app in `addons/`, as a module card ([#704](https://github.com/Gathin23/cleanyourtv/pull/704), [#1086](https://github.com/Gathin23/cleanyourtv/pull/1086), [#1226](https://github.com/Gathin23/cleanyourtv/pull/1226)) | Build it into `launcher/`, which stays small, offline and under a size budget for 1 GB TVs ([#173](https://github.com/Gathin23/cleanyourtv/pull/173), [#309](https://github.com/Gathin23/cleanyourtv/pull/309)) |
| Put a whole launcher, a restyle, a port or a big feature in your own repo, plus one row in [SHOWCASE.md](../SHOWCASE.md) ([#773](https://github.com/Gathin23/cleanyourtv/pull/773), [#1229](https://github.com/Gathin23/cleanyourtv/pull/1229)) | Send a 2,000-line PR to the core and hope it fits ([#1109](https://github.com/Gathin23/cleanyourtv/pull/1109), [#1138](https://github.com/Gathin23/cleanyourtv/pull/1138)) |
| Search open and merged PRs first, and build on what's there ([#972](https://github.com/Gathin23/cleanyourtv/pull/972), [#1279](https://github.com/Gathin23/cleanyourtv/pull/1279)) | Add a second key, map or add-on for something main already does: two would fight ([#864](https://github.com/Gathin23/cleanyourtv/pull/864), [#1173](https://github.com/Gathin23/cleanyourtv/pull/1173), [#1288](https://github.com/Gathin23/cleanyourtv/pull/1288), [#271](https://github.com/Gathin23/cleanyourtv/pull/271)) |
| Send one idea per PR, and split the rest out ([#326](https://github.com/Gathin23/cleanyourtv/pull/326), [#488](https://github.com/Gathin23/cleanyourtv/pull/488), [#1235](https://github.com/Gathin23/cleanyourtv/pull/1235)) | Mix a TV list, a launcher change and a theme in one PR ([#268](https://github.com/Gathin23/cleanyourtv/pull/268), [#325](https://github.com/Gathin23/cleanyourtv/pull/325)) |

## Never, anywhere

- **No uninstalling, root or bypasses.** Everything here disables with `pm disable-user`, so `tools/restore.sh`
  undoes it. No `pm uninstall`, no `pm hide` without its undo, no root, and no way around a maker's install
  block ([#706](https://github.com/Gathin23/cleanyourtv/pull/706), [#472](https://github.com/Gathin23/cleanyourtv/pull/472),
  [#678](https://github.com/Gathin23/cleanyourtv/pull/678), [#830](https://github.com/Gathin23/cleanyourtv/pull/830),
  [#347](https://github.com/Gathin23/cleanyourtv/pull/347)).
- **No prebuilt binaries.** No APKs (not even as workflow artifacts), `.so` files or zip uploads: everyone builds
  from source ([#761](https://github.com/Gathin23/cleanyourtv/pull/761), [#398](https://github.com/Gathin23/cleanyourtv/pull/398),
  [#637](https://github.com/Gathin23/cleanyourtv/issues/637), [#1298](https://github.com/Gathin23/cleanyourtv/pull/1298)).
- **No self-updaters or release signing** ([#143](https://github.com/Gathin23/cleanyourtv/pull/143), [#978](https://github.com/Gathin23/cleanyourtv/pull/978)).
- **No personal details.** No LAN IPs, Wi-Fi names, your own city or your own dock in shared files
  ([#25](https://github.com/Gathin23/cleanyourtv/pull/25), [#268](https://github.com/Gathin23/cleanyourtv/pull/268),
  [#1212](https://github.com/Gathin23/cleanyourtv/pull/1212), [#312](https://github.com/Gathin23/cleanyourtv/issues/312)). Blur them in screenshots.
- **No CI or workflow edits** that come along with a feature. An invalid workflow stops every build
  ([#1265](https://github.com/Gathin23/cleanyourtv/pull/1265), [#1298](https://github.com/Gathin23/cleanyourtv/pull/1298), [#513](https://github.com/Gathin23/cleanyourtv/pull/513)).
- **No agent or tool config.** Don't commit `.claude/`, `.codex/`, `.cursor/` hooks or similar: they run
  commands for anyone who opens the repo ([#286](https://github.com/Gathin23/cleanyourtv/pull/286)).
- **No logging of key presses.** It turns logcat into a keystroke log ([#176](https://github.com/Gathin23/cleanyourtv/pull/176)).
- **No leaving ADB open.** Nothing should keep network ADB listening after a reboot ([#877](https://github.com/Gathin23/cleanyourtv/pull/877)).

## Debloat lists (`examples/`)

| Do | Don't |
|---|---|
| List only packages you disabled and lived with for a few days, with the model, Android version, firmware and a dated `Status:` line ([#228](https://github.com/Gathin23/cleanyourtv/pull/228), [#286](https://github.com/Gathin23/cleanyourtv/pull/286)) | Leave the template's draft line in, or name a different TV in the header than the one you tested ([#228](https://github.com/Gathin23/cleanyourtv/pull/228)) |
| Fold a TV on the same firmware into the existing folder as a second unit ([#1085](https://github.com/Gathin23/cleanyourtv/pull/1085), [#1148](https://github.com/Gathin23/cleanyourtv/pull/1148), [#1210](https://github.com/Gathin23/cleanyourtv/pull/1210)) | Open a new folder for the same build, or rename an existing one ([#228](https://github.com/Gathin23/cleanyourtv/pull/228), [#800](https://github.com/Gathin23/cleanyourtv/pull/800)) |
| Comment out firmware updaters, with a note that owners can turn them off ([#1306](https://github.com/Gathin23/cleanyourtv/pull/1306), [#858](https://github.com/Gathin23/cleanyourtv/pull/858)) | Disable updates for everyone because you chose to |
| Keep the keyboard, the input/source packages, the factory menus and the only Home ([#686](https://github.com/Gathin23/cleanyourtv/pull/686), [#934](https://github.com/Gathin23/cleanyourtv/pull/934), [#1210](https://github.com/Gathin23/cleanyourtv/pull/1210)) | List Android's core packages: the tools refuse them, because a TV can stop booting ([#884](https://github.com/Gathin23/cleanyourtv/issues/884)) |
| Say on the setupwraith line that it can own the saved Wi-Fi and that, on Google TV, casting stops without it ([#1146](https://github.com/Gathin23/cleanyourtv/pull/1146), [#1257](https://github.com/Gathin23/cleanyourtv/pull/1257), [#1088](https://github.com/Gathin23/cleanyourtv/pull/1088)) | Tell people to disable Home or setupwraith by hand; `launcher/build-and-install.sh` does the handover |
| Use `tools/restore.sh`, `tools/disable.sh` and `launcher/build-and-install.sh` ([#806](https://github.com/Gathin23/cleanyourtv/pull/806), [#813](https://github.com/Gathin23/cleanyourtv/pull/813)) | Ship a folder's own install or restore scripts, a backup folder, or private-DNS or AdGuard steps: lists only change packages ([#518](https://github.com/Gathin23/cleanyourtv/pull/518), [#656](https://github.com/Gathin23/cleanyourtv/pull/656), [#775](https://github.com/Gathin23/cleanyourtv/pull/775)) |
| Put the packages in the list itself, as commented lines if needed ([#1259](https://github.com/Gathin23/cleanyourtv/pull/1259)) | Link a list in another repo, or a PR number that may close, instead of a page here ([#1175](https://github.com/Gathin23/cleanyourtv/pull/1175)) |
| Write findings that describe a firmware as a `docs/devices/` note ([#1106](https://github.com/Gathin23/cleanyourtv/issues/1106), [#1185](https://github.com/Gathin23/cleanyourtv/issues/1185)) | Put uninstall steps in a note, even as a fallback ([#1135](https://github.com/Gathin23/cleanyourtv/pull/1135), [#1355](https://github.com/Gathin23/cleanyourtv/issues/1355)) |

## Launcher switches (`launcher/`)

| Do | Don't |
|---|---|
| Keep a new switch off by default, with today's behaviour unchanged when it's off, byte for byte ([#464](https://github.com/Gathin23/cleanyourtv/pull/464), [#327](https://github.com/Gathin23/cleanyourtv/pull/327), [#146](https://github.com/Gathin23/cleanyourtv/pull/146)) | Change a default, the dock, columns or a click for every TV ([#1104](https://github.com/Gathin23/cleanyourtv/pull/1104), [#261](https://github.com/Gathin23/cleanyourtv/pull/261), [#221](https://github.com/Gathin23/cleanyourtv/pull/221)) |
| Make it a `config.xml` key a theme can set ([#167](https://github.com/Gathin23/cleanyourtv/pull/167), [#401](https://github.com/Gathin23/cleanyourtv/pull/401)) | Keep on-TV toggles in SharedPreferences that override a pushed theme ([#167](https://github.com/Gathin23/cleanyourtv/pull/167), [#189](https://github.com/Gathin23/cleanyourtv/pull/189)) |
| Add strings in every locale, or keep it config-only ([#836](https://github.com/Gathin23/cleanyourtv/pull/836), [#1218](https://github.com/Gathin23/cleanyourtv/pull/1218)) | Ship English-only rows in Launcher settings |
| Add a real-TV screenshot with its row in `docs/switches.md` ([#1132](https://github.com/Gathin23/cleanyourtv/pull/1132), [#1246](https://github.com/Gathin23/cleanyourtv/pull/1246)) | Put a new key on `SCREENSHOT_WANTED`: that list is for keys from before the rule ([#1117](https://github.com/Gathin23/cleanyourtv/pull/1117)) |
| Count the bytes: the APK has a size gate, and short strings help ([#908](https://github.com/Gathin23/cleanyourtv/pull/908), [#173](https://github.com/Gathin23/cleanyourtv/pull/173)) | Add a library or a lower minSdk; the launcher stays on Android 8.0 ([#608](https://github.com/Gathin23/cleanyourtv/pull/608), [#857](https://github.com/Gathin23/cleanyourtv/pull/857)) |
| Run it on a real TV, especially anything that touches Home or the install path ([#891](https://github.com/Gathin23/cleanyourtv/pull/891), [#566](https://github.com/Gathin23/cleanyourtv/pull/566), [#1024](https://github.com/Gathin23/cleanyourtv/pull/1024)) | Add INTERNET, Bluetooth, storage, notification-listener or secure-settings grants to the launcher, even behind a switch ([#149](https://github.com/Gathin23/cleanyourtv/pull/149), [#311](https://github.com/Gathin23/cleanyourtv/pull/311), [#173](https://github.com/Gathin23/cleanyourtv/pull/173), [#670](https://github.com/Gathin23/cleanyourtv/pull/670)) |
| Keep heavy work (hashing, decoding) off the main thread, and cap bitmaps at 1920 px ([#378](https://github.com/Gathin23/cleanyourtv/pull/378), [#174](https://github.com/Gathin23/cleanyourtv/pull/174)) | Swallow volume, mute or media keys, or Record in every app ([#257](https://github.com/Gathin23/cleanyourtv/pull/257), [#244](https://github.com/Gathin23/cleanyourtv/pull/244)) |
| Use public APIs, and call a control what it does ([#1337](https://github.com/Gathin23/cleanyourtv/pull/1337)) | Read hidden fields, or check a component with a test that always passes ([#407](https://github.com/Gathin23/cleanyourtv/pull/407), [#891](https://github.com/Gathin23/cleanyourtv/pull/891)) |

## Themes (`themes/`)

| Do | Don't |
|---|---|
| Add `screenshot.jpg` of Home on a real TV with the theme, 1280×720 under 300 KB, with the focus on the dock ([#484](https://github.com/Gathin23/cleanyourtv/pull/484)) | Use a mock-up, an emulator, or a frame of an ad ([#450](https://github.com/Gathin23/cleanyourtv/pull/450)) |
| Use your own pictures, or ones with a stated free licence (CC0, CC BY), credited in `CREDITS.md` ([#1114](https://github.com/Gathin23/cleanyourtv/pull/1114), [#1260](https://github.com/Gathin23/cleanyourtv/issues/1260)) | Use wallpaper sites without a licence, brand logos or vendor artwork. Credit alone isn't permission ([#511](https://github.com/Gathin23/cleanyourtv/pull/511), [#1114](https://github.com/Gathin23/cleanyourtv/pull/1114)) |
| Keep the dock generic: the big streaming apps ([themes/README](../themes/README.md#adding-a-theme)) | Show piracy-adjacent apps in the dock or the screenshot ([#1113](https://github.com/Gathin23/cleanyourtv/pull/1113), [#1114](https://github.com/Gathin23/cleanyourtv/pull/1114)) |
| Use only keys that exist on main, and wait for a key's own PR before a theme that needs it ([#1257](https://github.com/Gathin23/cleanyourtv/pull/1257), [#1114](https://github.com/Gathin23/cleanyourtv/pull/1114)) | Rely on a key the launcher doesn't have: it's silently ignored |
| Make a theme for a self-built launcher explicitly opaque ([#1385](https://github.com/Gathin23/cleanyourtv/issues/1385)) | Restyle every screen for everyone; that's a fork ([#937](https://github.com/Gathin23/cleanyourtv/pull/937)) |

## Add-ons (`addons/`)

| Do | Don't |
|---|---|
| Make it its own Gradle project with a module card, its permissions in its README, and no change under `launcher/` ([addons/README](../addons/README.md), [#1329](https://github.com/Gathin23/cleanyourtv/issues/1329)) | Commit an APK or build output |
| Add `screenshot.jpg` from a real TV, 16:9, under 300 KB, plus its cell in the add-ons table ([#1358](https://github.com/Gathin23/cleanyourtv/pull/1358)) | Leave out which TV you tested on ([#823](https://github.com/Gathin23/cleanyourtv/pull/823)) |
| Say in the README exactly what it sends where if it uses the network, and keep the backend self-hosted ([#1329](https://github.com/Gathin23/cleanyourtv/issues/1329), [#1221](https://github.com/Gathin23/cleanyourtv/pull/1221)) | Sign in to an account or keep a session key on the TV ([#1221](https://github.com/Gathin23/cleanyourtv/pull/1221)) |
| Use official, documented APIs | Depend on unofficial catalogue APIs that can break without notice ([#1269](https://github.com/Gathin23/cleanyourtv/pull/1269)) |
| Keep permissions narrow: an accessibility service with no window content where possible ([#1072](https://github.com/Gathin23/cleanyourtv/pull/1072)) | Give a phone full control of the TV (screen capture without a prompt, typing into any window, an ADB switch) ([#1349](https://github.com/Gathin23/cleanyourtv/pull/1349)) |
| Leave memory to Android | Ship a RAM cleaner or app killer: the lists switch those off, because they make TVs slower ([#1356](https://github.com/Gathin23/cleanyourtv/pull/1356)) |
| Ask for only the permissions the feature needs | Ask for fine location just to show the Wi-Fi name ([#1356](https://github.com/Gathin23/cleanyourtv/pull/1356)) |
| Keep it to your own cameras and streams | Play M3U/IPTV playlists over every app, even if the add-on itself is neutral: it reads next to piracy questions ([#1196](https://github.com/Gathin23/cleanyourtv/pull/1196)) |
| Use a neutral icon | Use another project's or brand's logo, which would mean carrying a brand licence ([#1279](https://github.com/Gathin23/cleanyourtv/pull/1279)) |
| Extend the existing add-on for the same service, with its maintainer ([#972](https://github.com/Gathin23/cleanyourtv/pull/972), [#1279](https://github.com/Gathin23/cleanyourtv/pull/1279)) | Change a shared default (a city, a server) to your own ([#1212](https://github.com/Gathin23/cleanyourtv/pull/1212)) |
| Show what happens to Home if the add-on registers as a Home app: a cold reboot with it off, uninstalling it, and both enabled ([#1215](https://github.com/Gathin23/cleanyourtv/pull/1215)) | Assume the Home handover just works |

## Tools (`tools/`)

| Do | Don't |
|---|---|
| Go through `tools/tv.py`, since the stock adb is blocked on many Macs ([#437](https://github.com/Gathin23/cleanyourtv/pull/437)) | Hardcode one TV's activities, a JAVA_HOME path or a fallback IP ([#36](https://github.com/Gathin23/cleanyourtv/pull/36), [#286](https://github.com/Gathin23/cleanyourtv/pull/286)) |
| Run the same safety checks as `disable.sh` before anything that disables ([#434](https://github.com/Gathin23/cleanyourtv/pull/434)) | Add a second install or update path for the launcher; `launcher/build-and-install.sh` is the one way in ([#1170](https://github.com/Gathin23/cleanyourtv/pull/1170)) |
| Keep tools to what the main install path needs, and anything optional out of `tools/requirements.txt` ([#564](https://github.com/Gathin23/cleanyourtv/issues/564), [#1206](https://github.com/Gathin23/cleanyourtv/pull/1206)) | Write tests that pin the exact wording of prose, or that are always true ([#745](https://github.com/Gathin23/cleanyourtv/pull/745), [#858](https://github.com/Gathin23/cleanyourtv/pull/858), [#891](https://github.com/Gathin23/cleanyourtv/pull/891)) |

## Showcase rows (`SHOWCASE.md`)

- **Do** run it on a real TV first, keep the row to one short sentence at the bottom of its section, and say what
  it connects to and its permissions ([#338](https://github.com/Gathin23/cleanyourtv/pull/338), [#1349](https://github.com/Gathin23/cleanyourtv/pull/1349)).
- **Do** add a LICENSE file to your repo: the showcase asks for open source ([#222](https://github.com/Gathin23/cleanyourtv/issues/222)).
- **Do** tell your users when a feature sends data to a third party, such as an IP-location service ([#365](https://github.com/Gathin23/cleanyourtv/pull/365)).
- **Don't** reword someone else's row ([#856](https://github.com/Gathin23/cleanyourtv/pull/856)).
- **Don't** expect a row for anything root-based or that gets around app-store protection ([#347](https://github.com/Gathin23/cleanyourtv/pull/347)).

## Keeping a PR mergeable

- **Rebase when asked.** Main moves fast. PRs that conflict and get no reply for about three days are closed, and
  you can reopen them ([#311](https://github.com/Gathin23/cleanyourtv/pull/311), [#244](https://github.com/Gathin23/cleanyourtv/pull/244)).
- **Write a description.** Say what changes and why, and on which TV. An empty or test PR is closed
  ([#803](https://github.com/Gathin23/cleanyourtv/pull/803), [#1199](https://github.com/Gathin23/cleanyourtv/pull/1199)).
- **Leave out unlinked files.** No screenshots or reports in `docs/` that nothing links to, and no
  `.gitignore` churn ([#640](https://github.com/Gathin23/cleanyourtv/pull/640), [#1148](https://github.com/Gathin23/cleanyourtv/pull/1148)).
- **Write code comments in English** ([#402](https://github.com/Gathin23/cleanyourtv/pull/402)).
- **Don't edit generated tables or counts** in the README; they come from each folder's `tv.json`
  ([CONTRIBUTING](../CONTRIBUTING.md#1-add-your-tv-model)).
