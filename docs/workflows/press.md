# Workflow: Add a press entry

**When to use this:** an article, or a YouTube video, about this project.

1. Add a row `| Outlet | [Headline](<url>) | Date |` to the right country section of `docs/PRESS.md`, in the
   headline's own language. The link must open and show the article: no syndicated copies, no auto-generated
   listings, nothing that cannot be checked.
2. Update the counts in every place `tests/test_press_counts.py` checks: the `docs/PRESS.md` intro line, its
   index line, the card's alt text in every `README*.md` that shows `docs/card.jpg` - today `README.md` ("and N
   more: N articles from N countries"), the Dutch `README.nl.md` ("nog N media: N artikelen uit N landen") and
   the German `README.de.md` ("und N weitere: N Artikel aus N Ländern") and the Ukrainian
   `README.uk.md` ("та ще N видань: N статей із N країн") - and `MORE` in
   `tools/readme_featured.py`. Every page shows the same picture, so a translation that copies it states the
   same numbers in its own words: the three named here are the pages that carry it today, not the rule.
   The end of each page's story section repeats the three numbers once more, in prose ("The coverage: 351
   articles from 46 countries and 229 videos on YouTube and Bilibili, all in `docs/PRESS.md`"), and the same
   test reads that sentence too: the page states the count twice, once in the card's alt text and once in
   that sentence, so a reader who sees 352 in the picture and 351 in the story section is looking at a page
   that was half updated. Leave the counts out of the sentence and the test says the paragraph no longer
   carries them.
   Redrawing `docs/featured.png` and `docs/card.jpg` needs Pillow, which the performance requirements install:
   `tools/venv/bin/pip install -r tools/requirements-perf.txt`, then
   `tools/venv/bin/python -I tools/readme_featured.py Fredoka.ttf docs/featured.png` and
   `tools/venv/bin/python -I tools/readme_card.py Fredoka.ttf docs/card.jpg`. Fredoka is the variable TTF from Google Fonts and its
   file is not in the repo, so that first argument is the path of the copy you downloaded; each script says
   so when the path is wrong. If you cannot redraw them, say so in the PR.
3. A video goes in the `## Videos` section at the end of the same page, not in a country section:
   `| Channel | [Title](<url>) | Date | Views |`, in the table's date form (`27&nbsp;Oct`), newest first.
   Two numbers above that table are read from the rows by `tests/test_press_counts.py` and move with it:
   the `[N videos]` count at the end of the intro line (its link points at the section itself), and the day
   in `views as of <date>` - no earlier than the newest row's date, so a video posted after the last read leaves the line
   stale ("the views are 'as of 27 Oct' and the newest video is dated 28 Oct"). A row added without moving
   the count fails as "the intro says 229 videos and the Videos section holds 230 rows".

Then run the tests: `python3 -m unittest discover -s tests -v`.
