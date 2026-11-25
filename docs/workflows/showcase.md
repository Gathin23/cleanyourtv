# Workflow: Add a showcase row

**When to use this:** a project built on this repo that lives in its own repository.

1. Add one row to the section of `SHOWCASE.md` that fits (home screens, forks, screensavers and add-ons,
   tools, other platforms), and the project under its TV brand in "Find a project by TV brand" - or under
   "Any Android TV (emulator or generic)" when it names no single brand, as the phone apps do. Each brand
   keeps one line in that index: add the link to the line the brand already has, never a second bullet for
   the same brand. `tests/test_showcase_brand_index.py` fails on a brand named twice - "a brand is listed
   twice in 'Find a project by TV brand', so a project on one of the lines is hard to find" - and prints
   the links only one of the two lines carries, which is a fork nobody scrolling the first line sees. It
   reads the index back the other way too: a link whose address no table row carries is reported, so a
   project renamed or repinned in the tables is dropped from the line rather than left there at the commit
   it used to point at. The row itself is a table row, inside the table of the section it belongs to, not a
   line at the end of that section or below the brand index: GitHub renders a "|" line outside a table as
   plain text, so the project is unreadable there, and `tests/test_showcase_tables.py` fails on it by line
   number ("these lines start with '|' but are not in a table, so GitHub renders them as text"). The same
   check holds every row to the four cells the header names - the project link first, then what it is, the
   TV or platform, and the author link last - so a row pasted in with three cells stops the suite as well.
   A fork goes under the one of Forks' four `###` groups that fits best (Looks and restyles, Added
   features, Made for one TV or brand, Ports to older Android), at the end of that group's table.
2. Bump the count by one everywhere it is stated: `SHOWCASE.md` opens with an `N projects so far.` line,
   and the "Built something on top?" row carries the same number on eight translated front pages -
   `README.md` ("... projects already live") and `README.pt-BR.md` ("que já reúne ... projetos") spell it
   in words, while `README.de.md`, `README.nl.md`, `README.uk.md`, `README.uz.md`, `README.fr.md`,
   `README.es.md` and `README.el.md` write it in digits, the shape a German compound or an Uzbek classified numeral cannot be
   read out of. `tests/test_showcase_project_count.py` counts the rows and reads every page in its
   `COUNT_PAGES` table with the parser or the digits reader that page needs, and refuses a row that leaves
   any of them behind: the pages named here are the ones that carry the number today, and that table is the
   list a bump has to cover - a translation that starts stating the count joins it, and this step with it.
3. Keep each cell to one line: what it is, what it was built and tested on, and the author. A project that
   borrows a name - another company's product or TV brand, or this repo's own - says it is unofficial in
   its "what it is" cell: "Unofficial, fan-made tvOS-style fork (not affiliated with Apple)". A reader has
   to be able to tell a look-alike from the company's own app, and a row that leaves it out is asked for it
   on the pull request (#4258, #4314).
4. The project is open source with a link people can read and build, says which TV it was tested on, collects
   nothing beyond what its README says, and is yours or listed with the author's blessing. A project that has
   only run on an emulator says that in its Built on cell instead of naming a TV it has not run on -
   `Android TV 11 emulator (API 30, 1080p); not yet run on a TV`, the shape rows here already use - and a
   screenshot taken on an emulator says so too.
5. Put a screenshot in the PR description; it is linked from the row, and that link has to open: the
   SHOWCASE rule in `.github/maintainer/MAINTAINER.md` holds a row to a screenshot link that opens
   (#2211). Two shapes answer 404 - a picture committed to a folder the project's own gitignore keeps
   out of its repository, so it was never pushed with it, and a link written against a branch that has
   since been renamed or force-pushed. Commit the screenshot somewhere the project tracks and point the
   link at that commit rather than at the branch, so the row keeps working after the project moves.

Then run the tests with the checkout's venv: `tools/venv/bin/python -m unittest discover -s tests -v`.
[`README.md`'s by-hand block](../../README.md) builds it, and it is where `tools/requirements.txt` puts the
`adb_shell` the suite imports - under the system `python3` those checks skip and the run still says OK. The
picture, card and wallpaper checks need Pillow as well: `tools/requirements-perf.txt` adds it, and without it
they are reported as skips, not failures.
