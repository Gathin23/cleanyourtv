# Workflow: Add a press entry

**When to use this:** an article, or a YouTube video, about this project.

1. Add a row `| Outlet | [Headline](<url>) | Date |` to the right country section of `docs/PRESS.md`, in the
   headline's own language. The link must open and show the article: no syndicated copies, no auto-generated
   listings, nothing that cannot be checked.
2. Update the counts in every place `tests/test_press_counts.py` checks: the `docs/PRESS.md` intro line, its
   index line, the card's alt text in every `README*.md` that shows `docs/card.jpg` - today `README.md` ("and N
   more: N articles from N countries"), the Dutch `README.nl.md` ("nog N media: N artikelen uit N landen") and
   the German `README.de.md` ("und N weitere: N Artikel aus N Ländern"), the Ukrainian
   `README.uk.md` ("та ще N видань: N статей із N країн") and the Turkish `README.tr.md`
   ("ve N yayın daha: N ülkeden N makale", the country count before the article total) - and `MORE` in
   `tools/readme_featured.py`. Every page shows the same picture, so a translation that copies it states the
   same numbers in its own words: the pages named here are the ones that carry it today, not the rule.
   The end of each page's story section repeats the three numbers once more, in prose ("The coverage:
   <articles> articles from <countries> countries and <videos> videos on YouTube and Bilibili, all in
   `docs/PRESS.md`"), and the same test reads that sentence too: the page states the count twice, once in
   the card's alt text and once in that sentence, so a reader who sees one number in the picture and
   another in the story section is looking at a page that was half updated. Leave the counts out of the
   sentence and the test says the paragraph no longer carries them. That sentence dates the numbers as
   well, in a parenthetical just after the press link ("(numbers as of 31 October 2026)", "(Stand: 31.
   Oktober 2026)", "(stand 31 oktober 2026)", "(станом на 31 жовтня 2026 року)", "(rakamlar 31 Ekim 2026
   itibarıyla)", "(dados de 31 de outubro de 2026)", "(chiffres au 9 novembre 2026)", "(datos del 9 de
   noviembre de 2026)"), so the day moves
   with the sweep: it may not name a day earlier than the newest article row in `docs/PRESS.md`, the rule
   step 3 already applies to "views as of", because a count read on the 30th cannot hold an article
   published on the 31st.
   `tests/test_press_as_of_date.py` reads that parenthetical in each page's own words, off every page whose
   story paragraph carries the link, and a page that writes none is not judged: `README.he.md` dates the
   stars it had in the sentence before, and its coverage sentence ends at the link. That paragraph is also
   read on the five front pages that have it without the card:
   `README.pt-BR.md`, the Hebrew `README.he.md`, the Uzbek `README.uz.md`, the French `README.fr.md` and the
   Spanish `README.es.md` show no `docs/card.jpg`, so their numbers are held to `README.md`'s alt text and to
   `docs/PRESS.md` on their own, and their coverage clause has to open with a colon like the others ("A
   repercussão: 412 artigos ...", "סיקור בתקשורת: 412 כתבות ...", "Matbuotdagi aks: 412 maqola ...", "La
   couverture médiatique : 534 articles ..."), since that colon is what the reader cuts the three numbers at.
   The Uzbek page dates them too ("(raqamlar 2026-yil 31-oktabr holatiga)"), the year before the day and the
   month in its own language, and the date check reads that shape like the other eight. A page counts as one
   of them by its paragraph, so a translation that copies the story without the picture is read without the
   test being edited - the French and Spanish pages, which landed last, needed their month names in the
   check's table and their names in the pins beside it.
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

Then run the tests with the checkout's venv: `tools/venv/bin/python -m unittest discover -s tests -v`.
[`README.md`'s by-hand block](../../README.md) builds it, and it is where `tools/requirements.txt` puts the
`adb_shell` and Pillow the suite imports - under the system `python3` those checks skip and the run still says OK.
