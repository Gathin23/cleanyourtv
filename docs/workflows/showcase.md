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
   it used to point at.
2. Bump the count by one everywhere it is stated: `SHOWCASE.md` opens with an `N projects so far.` line,
   and the "Built something on top?" row carries the same number on six translated front pages -
   `README.md` ("... projects already live") and `README.pt-BR.md` ("que já reúne ... projetos") spell it
   in words, while `README.de.md`, `README.nl.md`, `README.uk.md`, `README.uz.md` and `README.fr.md` write
   it in digits, the shape a German compound or an Uzbek classified numeral cannot be read out of.
   `tests/test_showcase_project_count.py` counts the rows and reads every page in its COUNT_PAGES table
   with the parser or the digits reader that page needs, and refuses a row that leaves any of them
   behind; a translation that starts stating the count joins that table.
3. Keep each cell to one line: what it is, what it was built and tested on, and the author.
4. The project is open source with a link people can read and build, says which TV it was tested on, collects
   nothing beyond what its README says, and is yours or listed with the author's blessing.
5. Put a screenshot in the PR description; it is linked from the row.

Then run the tests: `python3 -m unittest discover -s tests -v`.
