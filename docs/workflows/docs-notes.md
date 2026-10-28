# Workflow: Docs and troubleshooting notes

**When to use this:** a troubleshooting entry, a fix to `PROMPT.md`, or any other docs change.

- `docs/TROUBLESHOOTING.md` is grouped under headings (Connecting, Working on the TV, then one per larger
  problem). An entry is a bullet that opens with the symptom in bold, then the cause and the fix, naming the
  TV model and firmware it was seen on.
- A fix for something your agent got stuck on usually belongs in `PROMPT.md` as one extra sentence, kept
  brand-neutral. Brand-specific knowledge goes in `examples/` or `docs/devices/`. Changes to the safety rules
  themselves go to the maintainer.
- A note in `docs/devices/` is linked from a page a reader comes through: the row for its folder in
  `examples/README.md` when the model has a list of its own, and the "Notes for models that have no tested list
  yet" section of that page when it has not. `tests/test_device_notes_index.py` fails on a note nothing links,
  which is how three of them were found sitting unread. That section is a claim as well: an entry filed there
  says its model has no list, so once the list lands the link moves into the row, where
  `index_notes_with_their_own_list` wants it.
- Several tests pin facts in `docs/devices/` notes, `PROMPT.md` and `docs/TROUBLESHOOTING.md` (the
  `tests/test_*_note.py` files). If you reword one of those passages, run the suite and keep the fact.
- Docs and the README stay in step: a new list gets its `tv.json` (the TV tables are generated from it), and a
  theme, add-on or tool its row where the others are.

Then run the tests: `python3 -m unittest discover -s tests -v`.
