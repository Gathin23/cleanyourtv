# Workflow: Add a theme

**When to use this:** a new look that the keys in `themes/README.md` already carry.

1. Create `themes/<name>/config.json` using only keys from the table in `themes/README.md`;
   `themes/default/config.json` is the starting point.
2. Add `wallpaper.jpg` or a `wallpapers/` folder if it uses them: dark, 1920x1080, under about 300 KB each,
   and art you have the right to use.
3. Push it to a real TV with `tools/theme.sh themes/<name>` and take `screenshot.jpg` of Home with the focus
   on the dock: a JPEG at 1280x720 (16:9), at most 300 KB. Not a mock-up, not an emulator.
   `tests/test_themes.py` fails without it.
4. Add a row to the table in `themes/README.md` showing the screenshot, like the others.
5. A look that needs a new key is a launcher switch first, in its own PR.

Then run the tests: `python3 -m unittest discover -s tests -v`.
