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
4. Add one row to the table in `examples/README.md` (`` | `<folder>/` | TV | OS | Notes | ``) and one to the
   TV table in `README.md` (`| TV | OS |`). The TV cell must be the same text in both; `tests/test_examples.py`
   compares them.
5. Leave the counts alone (the `TV models` number in the card's alt text, the brand line): CI checks them only
   on `main`, and they are fixed up on merge.
6. A `README.md` for TV-specific notes and a `config.json` with the dock are welcome in the folder. Do not add
   install or restore scripts, Private DNS or AdGuard setup, or `pm uninstall` steps: the folder only
   disables, and `tools/disable.sh` and `tools/restore.sh` do the rest.

A model with notes but no several-day list goes in `docs/devices/<brand-model>.md`, with one line under the notes
list at the end of `examples/README.md`. A folder in `examples/` must have packages in it.

## Update an existing list

Keep the header accurate: add the second unit's model, firmware and region rather than replacing the first.
Explain each added or re-enabled package in a comment. A list that disables the firmware updater says why in the
header; keeping it enabled is preferred. `pm hide` or `pm block` needs the matching undo in the header. Edits to
someone else's list are judgement calls, so say in the PR which TV you tested on and for how long.

Then run the tests: `python3 -m unittest discover -s tests -v`.
