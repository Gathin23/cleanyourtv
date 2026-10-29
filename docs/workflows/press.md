# Workflow: Add a press entry

**When to use this:** an article about this project.

1. Add a row `| Outlet | [Headline](<url>) | Date |` to the right country section of `docs/PRESS.md`, in the
   headline's own language. The link must open and show the article: no syndicated copies, no auto-generated
   listings, nothing that cannot be checked.
2. Update the counts in all four places `tests/test_press_counts.py` checks: the `docs/PRESS.md` intro line,
   its index line, the README card's alt text ("and N more: N articles from N countries") and `MORE` in
   `tools/readme_featured.py`. Redrawing `docs/featured.png` and `docs/card.jpg` needs Pillow, which the
   performance requirements install:
   `tools/venv/bin/pip install -r tools/requirements-perf.txt`, then
   `tools/readme_featured.py Fredoka.ttf docs/featured.png` and
   `tools/readme_card.py Fredoka.ttf docs/card.jpg`. If you cannot redraw them, say so in the PR.

Then run the tests: `python3 -m unittest discover -s tests -v`.
