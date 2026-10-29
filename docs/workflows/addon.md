# Workflow: Add an add-on

**When to use this:** a widget or tile that can be its own small app, shown through a module card.

1. Create `addons/<name>/` as its own Gradle project (`settings.gradle`, its own `gradlew` and `gradlew.bat`
   wrappers and an `app` module), with its own package name. Copy the two wrappers from an add-on that is
   already here: the add-on build names `./gradlew` on macOS and Linux and `gradlew.bat` in a Command Prompt,
   and `tests/test_addons.py` refuses a folder without either one.
2. Copy a neighbour's `.gitignore` into the folder as well, so a build leaves nothing to commit: it keeps
   the Gradle output and the local.properties file Android Studio writes with your own SDK path out of
   git. `tests/test_addon_local_properties_ignored.py` asks git about each of those.
3. It talks to the launcher only through the module-card contract (`docs/modules.md`, `docs/specs/card.md`).
   Nothing under `launcher/` changes, and `launcher/` never mentions `addons/`. If the idea needs a launcher
   change, it is not an add-on: open an issue.
4. Its `README.md` has a Permissions section listing every permission and why, in the names its
   `AndroidManifest.xml` uses (`RECEIVE_BOOT_COMPLETED`, `SYSTEM_ALERT_WINDOW`), plus any network access or
   background service. No telemetry without an opt-in, no ads, your own art.
5. Add `addons/<name>/screenshot.jpg`: the add-on on a real TV, its card in the grid or its own screen, a
   JPEG, 16:9 (1280x720 is plenty) and under 300 KB, with Wi-Fi names and anything personal blurred out.
   `tests/test_addons.py` fails without it.
6. Add one row to the table in `addons/README.md` linking `<name>/`, with the TV it was tested on, the
   permissions its manifest asks for in the Permissions column (in the words the table already uses:
   "start at boot", "overlay"), and the screenshot in the row's Screenshot column. `tests/test_addons.py`
   checks the row and the folder, and `tests/test_addon_permissions_index.py` holds the add-on's own page
   and this column to the manifest.
7. No APKs in the repo. `tests/test_addons.py` checks all of this; `.github/workflows/addons.yml` builds the
   add-ons that changed with `./gradlew --no-daemon assembleRelease`.

Then run the tests: `python3 -m unittest discover -s tests -v`.
