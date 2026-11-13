# Workflow: Add a theme

**When to use this:** a new look that the keys in `themes/README.md` already carry.

1. Create `themes/<name>/config.json` using only keys from the table in `themes/README.md`;
   `themes/default/config.json` is the starting point.
2. Add `wallpaper.jpg` or a `wallpapers/` folder if it uses them: dark, 1920x1080, under about 300 KB each,
   and art you have the right to use. `tests/test_theme_wallpapers.py` holds every picture to those numbers
   (a smaller 16:9 picture is fine, the launcher scales it) and names one the launcher would never draw: a
   `wallpapers/` file that is not a JPEG, PNG or WebP, or a single picture not called `wallpaper.jpg`.
   Pictures you did not draw need a `CREDITS.md` beside them, the format in `themes/sweden/CREDITS.md`:
   `tests/test_theme_picture_credits.py` holds each picture the folder ships to a line there.
3. Push it to a real TV with `TV_HOST=<tv ip> tools/theme.sh themes/<name>` and take `screenshot.jpg` of
   Home with the focus on the dock: a JPEG at 1280x720 (16:9), at most 300 KB. Not a mock-up, not an
   emulator. `tests/test_themes.py` fails without it.
4. Add a row to the table in `themes/README.md` showing the screenshot, like the others. Two sentences the
   neighbours' rows carry are read back by `tests/test_theme_row_claims.py`: a rotation ("a new one every
   20 minutes") against that theme's `wallpaper_rotation_minutes`, and a licence ("CC BY, not MIT")
   against its `CREDITS.md`, which the folder then has to ship.
5. Bump the count in the Themes feature row of every front page that states it: today `README.md` ("`N ship
   in themes/`"), `README.de.md`, `README.nl.md`, `README.uk.md`, `README.tr.md`, `README.pt-BR.md`,
   `README.he.md`, `README.uz.md`, `README.fr.md` and `README.es.md` (the last six in digits), each in its own words; the ten are
   today's pages rather than the rule.
   `tests/test_readme_theme_count.py` counts the folders that carry a `config.json` and fails on a page
   whose number is not that count, naming the page and the folders (`README.nl.md says 10 themes and
   themes/ holds 11: aurora, ...`). A language whose number words the test does not carry yet is a page it
   cannot read, so a new translation that states the count needs those words in its table - or writes the
   count in digits, which needs no table line. The link is read with or without the code spans around the
   folder, so a page that writes `themes/` as a plain link is judged like the others rather than left out
   of the count.
6. A look that needs a new key is a launcher switch first, in its own PR.

Then run the tests with the checkout's venv: `tools/venv/bin/python -m unittest discover -s tests -v`.
[`README.md`'s by-hand block](../../README.md) builds it, and it is where `tools/requirements.txt` puts the
`adb_shell` and Pillow the suite imports - under the system `python3` those checks skip and the run still says OK.
