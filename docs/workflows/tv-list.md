# Workflow: Add or update a TV list

**When to use this:** you tested a TV's debloat list for a few days and want to share it, or you have a second unit or a fix for a list that exists.

1. Check `examples/README.md` first. The same product code and firmware as an existing folder is usually a
   second unit or a re-badge: add your notes to that folder instead of a new one.
2. Create `examples/<brand>-<model>/debloat.txt`, lower-case with hyphens. Copy the format of
   `examples/xiaomi-mi-tv-4a/debloat.txt`:
   - The first line is a comment with the exact model, Android version and firmware build
     (`tools/tv.py --probe` prints them).
   - Then the Apply and Undo lines, the stock launcher and Live TV package if they differ, and what was kept and why.
   - One package per line, grouped under a comment that names the group, with a trailing comment on anything
     non-obvious. No duplicates, nothing but package names outside comments.
3. List only packages you actually disabled and lived with for a few days. Note anything you re-enabled.
   Mark untested entries as comments.
4. Add `examples/<folder>/tv.json` beside the list, like `examples/xiaomi-mi-tv-4a/tv.json`:
   ```json
   {
     "tv": "Xiaomi Mi TV 4A (MiTV-MOOQ1)",
     "os": "Android TV 10",
     "brand": "Xiaomi",
     "notes": "PatchWall, Mi analytics, SmartShare, MiLink"
   }
   ```
   `tv` and `os` fill the folder's row in the TV tables, `brand` the README's brand grid (spelled as it is there),
   `notes` the Notes cell in `examples/README.md`: one line of Markdown, links relative to `examples/`, `\|`
   for a pipe. `readme_os` is the one optional key: it is the OS cell of the two-column table in `README.nl.md`,
   for a set whose row there should read shorter than the same set's row in `examples/README.md` - that table
   has room for `Google TV launcher on Android TV 11`, the short one for `Google TV 11`.
   `tests/test_examples.py` checks the file.
5. Change nothing outside the folder. The tables in `examples/README.md` and `README.nl.md`, the brand grid in
   `README.md`, `README.nl.md` and `README.de.md`, and the counts (the `TV models` number in the card's alt text)
   are generated from every `tv.json` by `tools/gen_tables.py`, which a maintainer runs after merging; CI compares
   them only on `main`.
6. A `README.md` for TV-specific notes and a `config.json` with the dock are welcome in the folder. That
   `config.json` takes only the keys `themes/README.md`'s Keys table says a theme can set: the rows it marks
   `config.xml` only - the vendor settings screens, the icon tiles - are never read out of a pushed file, so
   `theme.sh` drops them without a word and the launcher keeps its own value. A set that needs one wants that
   line in the folder's `README.md`, for the owner to set in `launcher/app/src/main/res/values/config.xml`
   before building. `tests/test_shipped_config_keys_are_read.py` holds every shipped `config.json` to the
   table. Do not add install or restore scripts, Private DNS or AdGuard setup, or `pm uninstall` steps: the
   folder only disables, and `tools/disable.sh` and `tools/restore.sh` do the rest.

A model with notes but no several-day list goes in `docs/devices/<brand-model>.md`, with one line under the notes
list at the end of `examples/README.md`. A folder in `examples/` must have packages in it.

## Update an existing list

Keep the header accurate: add the second unit's model, firmware and region rather than replacing the first.
Explain each added or re-enabled package in a comment. A list that disables the firmware updater says why in the
header; keeping it enabled is preferred. `pm hide` or `pm block` needs the matching undo in the header. Edits to
someone else's list are judgement calls, so say in the PR which TV you tested on and for how long. If the row
should say something new, change the folder's `tv.json`, not the tables.

Then run the tests: `python3 -m unittest discover -s tests -v`.
