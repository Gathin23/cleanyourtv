# Workflow: Add a theme

**When to use this:** a new look that the keys in `themes/README.md` already carry.

1. Create `themes/<name>/config.json` using only keys from the table in `themes/README.md`;
   `themes/default/config.json` is the starting point.
2. Add `wallpaper.jpg` or a `wallpapers/` folder if it uses them: dark, 1920x1080, under about 300 KB each,
   and art you have the right to use.
3. Push it to a real TV with `TV_HOST=<tv ip> tools/theme.sh themes/<name>` and take `screenshot.jpg` of
   Home with the focus on the dock: a JPEG at 1280x720 (16:9), at most 300 KB. Not a mock-up, not an
   emulator. `tests/test_themes.py` fails without it.
4. Add a row to the table in `themes/README.md` showing the screenshot, like the others.
5. Bump the count in the Themes feature row of every front page that states it: today `README.md`
   ("`N ship in themes/`"), `README.de.md` and `README.nl.md`, each in its own words; the three are
   today's pages rather than the rule. `tests/test_readme_theme_count.py` counts the folders that carry a
   `config.json` and fails on a page whose number is not that count, naming the page and the folders
   (`README.nl.md says 10 themes and themes/ holds 11: aurora, ...`).
6. A look that needs a new key is a launcher switch first, in its own PR.

Then run the tests: `python3 -m unittest discover -s tests -v`.
