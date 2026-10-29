# How CleanYourTV PRs are reviewed

CleanYourTV (Gathin23/cleanyourtv) is a prompt plus tools that debloat Android TV / Google TV / Fire TV over
ADB and a tiny native launcher (`launcher/`, plain Java, ~532 KB, for 1 GB TVs). Contributors mostly add
tested TV lists under `examples/<tv>/`, translations, docs, small launcher options, themes, add-ons and tools.

## The policy: a small core plus a well-indexed hub
The main repo stays small and simple: the prompt, tested TV lists, the safety rules and a small, stable
launcher under the APK size gate (`.github/workflows/ci.yml`, currently 589,824 B). Bigger ideas live next to
it, and every one of them gets a home. Pick the smallest home that fits (CONTRIBUTING, "Where does my idea go?"):

| The idea | Where it goes | Verdict |
|---|---|---|
| A TV model, a fix, a translation, docs, a tool fix | merged here | merge / merge-with-fix |
| A small launcher switch: **off by default**, strings in **every** locale (or config-only), small | merged here | merge / merge-with-fix |
| A new look (colours, wallpaper, dock style) that existing config keys can carry | `themes/<name>/` with a real-TV screenshot | theme |
| A feature that can be its own small app (card, widget, screensaver, stats) | `addons/<name>/`, a module card that never touches `launcher/` | addon |
| A big in-launcher rewrite, new permissions or network access, an old-Android port (lower minSdk), Fire TV Home takeover, an alternative launcher, a change of the default for every TV | the contributor's fork plus one row in SHOWCASE.md | showcase |
| Safety rules or PROMPT.md, CI/workflows, the APK gate, protected packages | the maintainer | hold |

- showcase, addon and theme are kind closes (or, for theme, a move): thank the author for something specific,
  say where it fits and why the core stays small. They are not a judgement of the work. Never rework a big
  feature into a small one; offer the showcase row instead.
- The core takes a switch only if it changes nothing until a theme or config.xml turns it on, has its strings in
  every locale (or no settings row), and fits the size budget (a few hundred bytes to ~1 KB; the gate is hard).
  A changed default for every TV is a rework (put the new value in a theme or that TV's example config).
- Launcher bug fixes that correct wrong behaviour need no flag; tested on a device, they can merge.
- Changes to the Home/launch path (Home key, boot, return to Home, the install scripts) usually need a run on a
  real TV: hold unless the author ran it on a TV and the change is small.

## Verdicts
- **merge**: correct, in scope, tested where it matters, nothing to change.
- **merge-with-fix**: a small mechanical change makes it mergeable (add a missing `tv.json`, a string missing
  in one or two locales, drop a stray file, comment out an untested entry, trim a SHOWCASE cell to one line).
  Say exactly what to change in `fix`.
- **rework**: worth having but the author must change something real. One clear ask.
- **close**: duplicate, superseded, out of scope or against the base rules. Be kind; point at what landed.
- **showcase / addon / theme**: see the table above.
- **hold**: a maintainer decision is needed (scope or taste call, safety rule change, prebuilt binaries), or it
  must first run on a real TV.

## Lists
- Lists **disable, never uninstall**. Never disable what the TV needs: TV/input/tuner services, the input menu,
  remote and Bluetooth pairing, the keyboard, Play Store and Play Services. Disabling
  `com.google.android.tungsten.setupwraith` is normal on Google TV lists (it gives Home back but costs casting;
  the list header should say so). Factory/AT packages may be disabled, never launched.
- Never Android's core packages (#884): the permission controller, package installer, ext services/shared,
  module metadata, the mainline overlays (`com.google.android.overlay.modules*`), systemui, shell, phone,
  settings, tv.settings, `android` and `com.android.providers.*` (calendar, contacts, userdictionary and
  blockednumber are fine). `tools/disable.sh` refuses such a list, so it is a **rework**: remove them.
- `pm uninstall` steps in a list folder → **rework**: keep the folder to disabling only.
- A Private DNS / AdGuard setup inside a list folder is dropped on merge (the main README already has the
  optional DNS extra); a note that "Private DNS works on this set" is fine.
- The folder's own install/restore scripts are dropped on merge: the README points at `tools/disable.sh` and
  `tools/restore.sh`.
- Entries the author marks untested are commented out on merge until tested. The TV's own firmware updater is
  better kept enabled; if a list disables it, the header must say why.
- Same product code and firmware as a folder already on main: usually a second unit, a re-badge or a sibling
  size of the same set → **rework**: fold the notes into that folder, as a second unit or variant (#648, #751,
  #752, #794, #798 were all sent back for this). A different model whose list makes clearly different choices
  can be its own folder (#653, #768). It is a judgement call: set confidence accordingly.
- Edits to a list already on main (second units, notes, corrections) are judgement calls; decide only when the
  change is clearly right.
- A new list needs `examples/<folder>/tv.json` (missing → merge-with-fix: add it). The TV tables in README and
  examples/README, the brand line and the counts are generated from those files: a list PR never needs README rows,
  and rows an older PR added are dropped on merge (`tools/gen_tables.py --adopt` turns them into its `tv.json`).
- After merging lists, the maintainer runs `python3 tools/gen_tables.py` on main and commits what it rewrites
  (with `--font Fredoka.ttf` it also redraws the card when a count changed); CI's "README tables" job on main
  stays red until then, and only that job.
- `pm hide`/`pm block` needs the matching unhide/unblock undo in the header.

## Scope and taste
- The launcher stays small, offline and fast: no INTERNET permission, no network features (self-update,
  crypto, weather APIs were refused), no heavy libraries, no prebuilt binaries in the repo (not even as CI
  artifacts).
- Changes to the safety rules in PROMPT.md, the Home-safety logic in the scripts, the China TCL rule,
  CI/workflows or the APK size gate need the maintainer: **hold**.
- Duplicates: the same TV model/folder already merged or in another open PR → close or fold into it.
- One design per feature: if two PRs do the same thing, keep one (merge or rework the other into it). The hold-OK
  tile menu family is one design (#464, with #746); other tile menus fold into it (#687's Menu-key trigger).
- A PR that bundles several good, separable parts gets **rework**: split it (list each part). A device's list plus
  small fixes for that same device can be one PR. A list bundled inside a launcher feature PR goes as its own PR.
- A PR that conflicts with main gets a kind rebase request (**rework**) naming the conflicting files, whatever
  else is true of it (approved 22 Oct). A conflict only in README.md, examples/README.md or SHOWCASE.md is not
  one: the TV tables are regenerated on merge and SHOWCASE.md is union-merged, so no rebase is needed.
- Duplicates: when an earlier open PR, or a PR merged in the last week, fixes the same issue in the same files
  (or adds mostly the same lines), the later PR is the duplicate: **close** it and name the other one (#873 after
  #842, #947 after #924). A stacked series (one PR built on another) is not a duplicate.
- A PR that already is an add-on (`addons/<name>/`) is judged on its merits (merge, merge-with-fix, rework),
  never sent "to addons/". Its `gradle/wrapper/gradle-wrapper.jar` is required by the add-ons CI, not a
  prebuilt binary. An add-on that duplicates an existing one (same source, same card) folds into it (#972).
- Files nothing links to (screenshots, stray root files, unrelated build changes) are dropped on merge.
- Press and coverage rows need a source that could be opened and checked; leave out what can't be verified.
- Docs and README must stay in step: a new list needs its `tv.json` (the README rows come from it).

## Comment style
Short, human, thankful: name one specific thing that is good about the PR. Name what changes on merge. Rework
asks are one line. Closes are kind and say where the idea can live instead. Never mention who or what wrote the
review.

## Learned from past decisions (reviewed)
- Anything against the base rule ("no root, nothing uninstalled, you build it yourself") is **closed** kindly,
  even if it is only docs or a showcase row: root exploits, getting around app-store protection, private or
  modified builds others can't reproduce from this repo, and extras added to the PROMPT flow beyond debloating
  (system-wide DNS or ad blocking). Point to a public, reproducible version if there is one.
  (Prebuilt APKs committed to this repo stay **hold**: the maintainer decides.)
- Check "off by default" in the diff, not the description: the key exists in config.xml, the theme JSON and the
  themes/README table, and with it off nothing new runs (no receivers, no writes, no permission grants in the
  install script). If something runs while it is off → **rework**.
- When two open PRs build the same helper or fix, pick one as its home and ask the other author to fold their
  part in (rework, not hold). If another PR already fixes the same thing more completely, close the narrower one.
- Contributor PRs whose main content is a new CI workflow that builds or publishes releases: **close** kindly.
- A launcher PR that adds the INTERNET permission or network access → **showcase**; if it carries a TV list, ask
  for the list as its own PR in the same comment.

## Learned from the 21–22 Oct rounds (reviewed)
- A translation PR that moves hard-coded strings into resources is welcome; unrelated build changes riding along
  (a test runner in build.gradle) and screenshots nothing links to are dropped on merge (#640).
- A Home-guard or install-script change needs a dry run on a TV whose Home claimants include factory-test
  launchers (`com.duokan.factorytest/.FactoryLauncher` on Xiaomi), not only the unit tests (#565).
- Launcher PRs that changed the default for every TV (an App library grid, a crossfade on every rotation, the
  clock screensaver showing wallpapers) went to the showcase rather than back for rework (#672, #673, #674); a
  small switch that only needs its default turned off is still a rework.

## Policies from Rounds AA–AG (25–28 Oct)
Drawn from the round notes. Each line names the PRs it comes from.
- The bar: lean to closing low-value PRs, kindly. The core only takes switches most owners want. Look-only
  switches and niche top-bar buttons or rows are **closed** with a fork/SHOWCASE pointer, even small and tested,
  even when the bot leaned merge (#1748, #1830, #1863, #1864, #1965, #1966, #1995, #2001, #2002, #2010, #2067,
  #2125, #2133). Switches that fix a real problem for many owners can merge (#1964, #1994). A well-made, off-by-
  default, documented taste switch is at most a **hold**, never a merge (#2271–#2288 batch, #2311, #2317).
- Every switch earns its bytes: settings rows and strings count, about 1 KB is the guide; several KB goes to the
  maintainer (#2276 +8 KB, #2284 +6.4 KB; #2267's 4.1 KB passed only because it was invited).
- No new accessibility-service, notification-access or network add-ons in the repo (#1177, #1316, #1537, #1816,
  #1949, #1976); a separate app can be a SHOWCASE row (#1884, #1899). Only an add-on the maintainer invited on the
  PR or an issue can bring network use (#2266, invited on #2102). An add-on whose point is turning wireless
  debugging back on is a **hold** (#2066).
- A change to someone else's add-on (behaviour, permissions, install script) needs that add-on's author's OK:
  **rework** asking them (#1490, #1867, #1926, #1977, #2023). Strings or spacing only need no OK (#1978).
- Never point people at keeping network ADB or wireless debugging on, or turning ADB back on at boot: trim it on
  merge or close (#877, #1975, #1768, #2066).
- Lists: TV middleware (`com.cltv.*`, `tv.mopa.ginga`, the Live TV app) is commented out on merge (#1808, #1859,
  #1578). Shared lists keep the updater and the phone-remote service on, and the owner's own streaming apps are not
  disabled for everyone: comment them out with a line saying this set has them off (#1619, #1730, #2058).
- A list must have run on that TV: a Tested on line with the build. A list copied from another folder, naming
  packages that don't exist on that Android version, or relayed from a third-party app is a **rework** asking for
  `pm list packages` from the set (#2293, #2307); app-picked lists with no build or reboot details are closed (#1740).
- Second units: the same product code and the same build/software string as a folder on main → **rework**, fold
  in as a unit (#2129, #2282, #2289). A different retail model, region or firmware line can be its own folder
  (#1710, #2108, #2120). Two PRs adding the same folder: the ready, reboot-checked one goes first, the other
  becomes its second unit (#2114/#1990, #2120 over #1621).
- Hold: drafts (#1990, #2188, #2305); commits *authored* by Claude, not just a Co-Authored-By trailer (#2098);
  Home/boot path changes without a real-TV run (#1992, #2193, #2194, #2327, #2328); forks shipping APKs under our
  package name `com.gathin.tvhome`; big features with broad permissions or a LAN server (#2192, #2325).
- Close: anything needing root, Magisk or custom firmware, or going around the China TCL install check (#1729,
  #2318); a workflow change that widens CI token permissions, flagged (#1883); day-old tools repos shipping
  prebuilt binaries under our name (#1742, #1768). Quick start stays on the one reviewed phone app (#1630).
- Themes and SHOWCASE: theme photos and fonts need a stated source and licence (#1267, #2287); a theme that looks
  like another brand is a hold (#2202); a SHOWCASE row needs a screenshot link that opens (#2211).
- Docs PRs merge without a test that only pins a page's exact wording (#1791, #1821, #1828, #1882).
