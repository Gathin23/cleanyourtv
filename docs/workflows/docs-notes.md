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
- A new page under `docs/`, in a folder of its own too, is linked from a page a reader comes through:
  `CONTRIBUTING.md`, `README.md`, `AGENTS.md`, or a page in `docs/` that is already linked.
  `tests/test_docs_pages_reachable.py` walks every page below `docs/` and fails on one nothing links to
  (`pages under docs/ that nothing links to: docs/<the new page>.md`); a path inside a code block is an
  example, not a link. That is how `docs/DOS-AND-DONTS.md` was found, a page whose own rule is "no
  screenshots or reports in `docs/` that nothing links to" and which nothing in the repository named.
- A note's commands and its `Device` table are the two places the contributor's own set gets pasted in, and
  both are read: `tests/test_doc_addresses.py` wants `<tv ip>` or `192.168.1.50` where the address of the
  unit was, and `tests/test_doc_identifiers.py` wants no MAC address and no Android ID, each reported as
  `docs/devices/<name>.md:<line>: <the value>`. A serial number has no shape a test can recognise, so it is
  left out by hand: `docs/devices/coop-cotv-0001.md` once printed one in its Device table while the notes around it
  said they left the serial out (taken out since). Both guards read the same AGENTS.md sentence as the lists do.
- Several tests pin facts in `docs/devices/` notes, `PROMPT.md` and `docs/TROUBLESHOOTING.md` (the
  `tests/test_*_note.py` files). If you reword one of those passages, run the suite and keep the fact.
- Docs and the README stay in step: a new list gets its `tv.json` (the TV tables are generated from it), and a
  theme, add-on or tool its row where the others are.

Then run the tests with the checkout's venv: `tools/venv/bin/python -m unittest discover -s tests -v`.
[`README.md`'s by-hand block](../../README.md) builds it, and it is where `tools/requirements.txt` puts the
`adb_shell` and Pillow the suite imports - under the system `python3` those checks skip and the run still says OK.
