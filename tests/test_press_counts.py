"""Keeps the press numbers in docs/PRESS.md, the README cards and the card source in step.

The article total is written in every place that states it: the PRESS.md intro, its per-country index
line, the alt text of the card in README.md, the alt text of the same card in README.nl.md (in Dutch)
and in README.de.md (in German), and tools/readme_featured.py (which draws the line onto
docs/featured.png). Adding one article by hand means editing all of them; this checks they agree, so a
PR that adds a row and forgets the counts fails here instead of shipping a card that says 73 over a
list of 74.

The Videos section at the end of the same page is read the same way, and had nothing reading it: the
intro ends "[229 videos](#videos) follow the articles", the section's own line says when the view
counts were read ("views as of 30 Oct"), and both numbers are derived from the rows underneath - a
press PR that adds a video (229 of them so far) has to move them, and a video dated after the day the
views were read leaves the line above the table claiming an older read. The article half is held down
to one link and three cells per row and no repeated URL; the video half is held to the same, to
newest first (the section says so) and to those two numbers.

The same three numbers are written a second time, in prose, at the end of the story section of every
front page that shows the card ("The coverage: 351 articles from 46 countries and 229 videos on
YouTube and Bilibili, all in docs/PRESS.md") - and nothing read that sentence, so a press sweep that
moved the intro, the card's alt text and the card source left the four pages telling a reader one
number in the picture and another in the story section two screens below it. The clause is read here too, in the order every
translation writes it (article total, countries, videos), so the page and the list cannot disagree.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
PRESS = ROOT / "docs" / "PRESS.md"
README = ROOT / "README.md"
FEATURED = ROOT / "tools" / "readme_featured.py"
PRESS_RECIPE = ROOT / "docs" / "workflows" / "press.md"

LATAM = "Latin America (Spanish-language)"
ONLINE = "Online (newsletters, blogs, AI news)"
ROW = re.compile(r"^\| (?!Outlet\b)(?!--)")
LINK = re.compile(r"\[[^\]]+\]\((https?://[^)]+)\)")
TOC_ENTRY = re.compile(r"\[([^\]]+)\]\(#[^)]+\)&nbsp;(\d+)")
# The Videos half of the page: the count the intro states, the day the views were read, and a row's
# date, which is "27&nbsp;Oct" in the table and "27 Oct" in the sentence above it.
VIDEO_COUNT = re.compile(r"\[(\d+) videos?\]\(#videos\)")
VIEWS_DATE = re.compile(r"views as of (\d+) (\w{3})")
DATE_CELL = re.compile(r"^(\d+)&nbsp;(\w{3})$")
MONTHS = {name: number for number, name in enumerate(
    ("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"), 1)}
# A video row links its YouTube or Bilibili watch page.
VIDEO_LINK = re.compile(r"\((https?://[^)\s]*(?:youtube\.com|bilibili\.com)/[^)\s]+)\)")
# The card image the numbers are drawn on, wherever a README shows it: README.md in English, README.nl.md
# in Dutch and README.de.md in German, and any translation that copies the picture later. The words
# around the numbers are translated; the numbers are not.
CARD_ALT = re.compile(r'src="docs/card(?:-halloween)?\.jpg"[^>]*?alt="([^"]*)"')
# The story paragraph's closing sentence, which repeats the three numbers in prose: "The coverage: 351
# articles from 46 countries and 229 videos on YouTube and Bilibili, all in [docs/PRESS.md](docs/PRESS.md)".
# Every translation writes its own words around the numbers but keeps them in that order, so they are read
# as tokens out of the clause after the paragraph's last colon - no word table that a new language, or a
# rewording, quietly falls out of.
PRESS_LINK = re.compile(r"\[docs/PRESS\.md\]\(docs/PRESS\.md\)")
NUMBER = re.compile(r"\d[\d.,]*")


def card_alt_texts():
    """{file name: alt text} for every README whose card image carries an alt text."""
    out = {}
    for path in sorted(ROOT.glob("README*.md")):
        match = CARD_ALT.search(path.read_text(encoding="utf-8"))
        if match:
            out[path.name] = match.group(1)
    return out


def paragraphs(text):
    """A page split into paragraphs, one per entry, a sentence wrapped over two lines joined back up.

    README.md and README.uk.md break the coverage sentence after "articles from"; README.nl.md puts the
    whole story in one line. Reading paragraph by paragraph is what makes the four pages look alike.
    """
    out, current = [], []
    for line in text.split("\n"):
        if line.strip():
            current.append(line.strip())
        elif current:
            out.append(" ".join(current))
            current = []
    if current:
        out.append(" ".join(current))
    return out


def coverage_numbers(text):
    """(articles, countries, videos) the story paragraph states, or None when the page has no such paragraph.

    The paragraph is the one naming both docs/PRESS.md and YouTube: the end of the story section on every
    front page that shows the press card. Its last colon opens the clause that carries the three numbers,
    in the order every translation uses, so the numbers are read without knowing the words around them.
    A tuple of any other length is returned as it was read, so a sentence that grew a fourth number is
    reported with what the reader found instead of being trusted.
    """
    for paragraph in paragraphs(text):
        if "docs/PRESS.md" not in paragraph or "YouTube" not in paragraph:
            continue
        link = PRESS_LINK.search(paragraph)
        if link is None:
            continue
        colon = paragraph.rfind(":", 0, link.start())
        if colon < 0:
            return ()
        found = NUMBER.findall(paragraph[colon + 1:link.start()])
        return tuple(int(number.replace(",", "").replace(".", "")) for number in found)
    return None


def sections():
    """(heading, rows) for every article section, in file order. The Videos section at the end is not articles."""
    out, current = [], None
    for line in PRESS.read_text(encoding="utf-8").split("\n"):
        if line.strip() == "## Videos":
            break
        if line.startswith("## "):
            current = (line[3:].strip(), [])
            out.append(current)
        elif current is not None and ROW.match(line):
            current[1].append(line)
    return out


def intro():
    """The four numbers the PRESS.md intro states, as ints."""
    text = PRESS.read_text(encoding="utf-8").split("\n")[2]
    total = re.search(r"\*\*(\d+) articles\*\*", text)
    countries = re.search(r"(\d+) of them from outlets in (\d+) countries", text)
    latam = re.search(r"(\d+) from Spanish-language sites", text)
    online = re.search(r"(\d+) from newsletters", text)
    for name, match in (("total", total), ("countries", countries), ("Latin America", latam), ("online", online)):
        if match is None:
            raise AssertionError(f"docs/PRESS.md line 3 no longer states the {name} number")
    return {
        "total": int(total.group(1)),
        "country_articles": int(countries.group(1)),
        "countries": int(countries.group(2)),
        "latam": int(latam.group(1)),
        "online": int(online.group(1)),
    }


def toc():
    """{heading: number} from the one-line index under the intro."""
    line = next(l for l in PRESS.read_text(encoding="utf-8").split("\n") if l.startswith("[United States]"))
    return dict((name, int(n)) for name, n in TOC_ENTRY.findall(line))


def logos():
    source = FEATURED.read_text(encoding="utf-8")
    block = re.search(r"^LOGOS = \[(.*?)\]$", source, re.S | re.M).group(1)
    return re.findall(r'"([^"]+)"', block)


def videos(text=None):
    """The rows of the Videos section, as lists of cells: [channel, video, date, views].

    A row is a table line after the `| --- |` separator, so the header is skipped by where it sits rather
    than by the word in its first cell: a channel really can be called "Channel"."""
    text = PRESS.read_text(encoding="utf-8") if text is None else text
    out, inside, in_table = [], False, False
    for line in text.split("\n"):
        if line.strip() == "## Videos":
            inside = True
        elif inside and line.startswith("## "):
            break
        elif inside and line.startswith("| ---"):
            in_table = True
        elif inside and in_table and line.startswith("| "):
            out.append([cell.strip() for cell in line.strip().strip("|").split("|")])
    return out


def video_count(text=None):
    """The number the intro states as "[N videos](#videos)", or None when it states none."""
    text = PRESS.read_text(encoding="utf-8") if text is None else text
    match = VIDEO_COUNT.search(text)
    return int(match.group(1)) if match else None


def views_as_of(text=None):
    """(day, month) the Videos section says its view counts were read, or None when it does not say."""
    text = PRESS.read_text(encoding="utf-8") if text is None else text
    match = VIEWS_DATE.search(text)
    return (int(match.group(1)), match.group(2)) if match else None


def row_date(cell):
    """(month, day) for a row's date cell ("27&nbsp;Oct"), or None when the cell is not a date."""
    match = DATE_CELL.match(cell)
    if match is None or match.group(2) not in MONTHS:
        return None
    return MONTHS[match.group(2)], int(match.group(1))


def video_problems(text=None):
    """Why the Videos section and the two numbers above it disagree; [] when they agree.

    The count in the intro and the day the views were read are both derived from the rows, and the
    section says its rows run newest first; each of those is a claim a reader can check against the
    table, so each one is read here rather than trusted."""
    text = PRESS.read_text(encoding="utf-8") if text is None else text
    rows, problems = videos(text), []
    stated = video_count(text)
    if stated is None:
        problems.append("the intro no longer states '[N videos](#videos)', so nothing claims the count "
                        "of the rows below it")
    elif stated != len(rows):
        problems.append(f"the intro says {stated} videos and the Videos section holds "
                        f"{len(rows)} rows")
    as_of = views_as_of(text)
    newest = row_date(rows[0][2]) if rows and len(rows[0]) > 2 else None
    if as_of is None:
        problems.append("the Videos section no longer says when the views were read "
                        "('views as of <date>')")
    elif newest is not None and as_of[1] in MONTHS and newest > (MONTHS[as_of[1]], as_of[0]):
        problems.append(f"the views are 'as of {as_of[0]} {as_of[1]}' and the newest video is dated "
                        f"{rows[0][2].replace('&nbsp;', ' ')}")
    seen = {}
    dated = []
    for number, cells in enumerate(rows, 1):
        where = f"video row {number}"
        if len(cells) != 4:
            problems.append(f"{where} has {len(cells)} cells, not 4 (channel, video, date, views)")
            continue
        channel, video, date, views = cells
        if not channel:
            problems.append(f"{where} has no channel")
        found = VIDEO_LINK.findall(video)
        if len(found) != 1:
            problems.append(f"{where} needs exactly one YouTube or Bilibili link, this one has {len(found)}")
        elif found[0] in seen:
            problems.append(f"{found[0]} is listed twice, in video row {seen[found[0]]} and {number}")
        else:
            seen[found[0]] = number
        if row_date(date) is None:
            problems.append(f"{where} has {date!r} where a date goes, not '27&nbsp;Oct'")
        else:
            dated.append((number, row_date(date), date.replace("&nbsp;", " ")))
        if not re.fullmatch(r"[\d,]+", views):
            problems.append(f"{where} has {views!r} where a view count goes, not a number")
    for (first, first_date, first_cell), (second, second_date, second_cell) in zip(dated, dated[1:]):
        if second_date > first_date:
            problems.append(f"video row {second} ({second_cell}) is dated later than video row {first} "
                            f"({first_cell}) above it, and the section runs newest first")
            break
    return problems


class PressCountsTest(unittest.TestCase):
    def setUp(self):
        self.sections = sections()
        self.counts = dict((name, len(rows)) for name, rows in self.sections)
        self.numbers = intro()
        self.index = toc()

    def test_index_matches_the_sections(self):
        self.assertEqual(list(self.index), [name for name, _ in self.sections])
        for name, rows in self.sections:
            with self.subTest(section=name):
                self.assertEqual(self.index[name], len(rows), f"{name}: index says {self.index[name]}, table has {len(rows)} rows")

    def test_total_matches_the_rows(self):
        self.assertEqual(sum(self.counts.values()), self.numbers["total"])

    def test_the_split_adds_up(self):
        countries = [n for n in self.counts if n not in (LATAM, ONLINE)]
        country_rows = sum(self.counts[n] for n in countries)
        with self.subTest(part="country sections"):
            self.assertEqual(len(countries), self.numbers["countries"])
        with self.subTest(part="country rows"):
            self.assertEqual(country_rows, self.numbers["country_articles"])
        with self.subTest(part="Latin America"):
            self.assertEqual(self.counts[LATAM], self.numbers["latam"])
        with self.subTest(part="online"):
            self.assertEqual(self.counts[ONLINE], self.numbers["online"])
        with self.subTest(part="sum"):
            self.assertEqual(country_rows + self.counts[LATAM] + self.counts[ONLINE], self.numbers["total"])

    def test_readme_alt_text_matches_the_list(self):
        alt = re.search(r'and (\d+) more: (\d+) articles from (\d+) countries', README.read_text(encoding="utf-8"))
        self.assertIsNotNone(alt, "the README card alt text no longer states 'and N more: N articles from N countries'")
        more, articles, countries = (int(g) for g in alt.groups())
        self.assertEqual(articles, self.numbers["total"], "README alt text and docs/PRESS.md disagree on the article count")
        self.assertEqual(countries, self.numbers["countries"], "README alt text and docs/PRESS.md disagree on the country count")
        self.assertEqual(more, self.numbers["total"] - len(logos()), "README alt text and the logos in tools/readme_featured.py disagree on the '+N more'")

    def test_every_readme_card_states_the_same_numbers(self):
        # The card is in README.md, README.nl.md and README.de.md, and all three alt texts state the
        # total, the country count and the "+N more" - in English, Dutch and German. Only the English
        # one was checked, so the press commit that took the list to 296 articles had to change the
        # Dutch alt text by hand as well (6cc2fdc9), and a PR written from docs/workflows/press.md left
        # the Dutch front page claiming an older count. The German page is the case the recipe still
        # missed: it carries the same picture, so the next article had to be added there by hand too.
        alts = card_alt_texts()
        self.assertLessEqual({"README.md", "README.nl.md", "README.de.md"}, set(alts),
                             "README.md, README.nl.md and README.de.md no longer show docs/card.jpg with an alt text")
        numbers = (("article total", self.numbers["total"]),
                   ("country count", self.numbers["countries"]),
                   ("'+N more'", self.numbers["total"] - len(logos())))
        for name, alt in alts.items():
            for what, number in numbers:
                with self.subTest(readme=name, number=what):
                    self.assertRegex(alt, rf"(?<!\d){number}(?!\d)",
                                     f"{name}'s card alt text does not state the {what} ({number}) "
                                     f"that docs/PRESS.md and tools/readme_featured.py carry")

    def test_the_press_recipe_names_every_place_the_counts_live(self):
        # The recipe is what a press PR follows, and the numbers live in every page that shows the card:
        # naming the English and Dutch pages only left the German one at the previous count, which this
        # file then failed on with nothing in the recipe to point at - the same gap #2132 closed for the
        # Dutch page. The list comes from the pages, so a translation that copies the card is covered
        # without this test being edited first.
        step = PRESS_RECIPE.read_text(encoding="utf-8").split("\n2. ")[1].split("\n3. ")[0]
        for name in sorted(card_alt_texts()):
            with self.subTest(readme=name):
                self.assertIn(name, step, f"docs/workflows/press.md step 2 does not name {name}")

    def test_the_card_source_draws_the_same_numbers(self):
        more = re.search(r'^MORE = "(.*)"$', FEATURED.read_text(encoding="utf-8"), re.M)
        self.assertIsNotNone(more, "tools/readme_featured.py no longer has a MORE line")
        expected = "+%d more  ·  %d articles from %d countries" % (
            self.numbers["total"] - len(logos()), self.numbers["total"], self.numbers["countries"])
        self.assertEqual(more.group(1), expected,
                         "tools/readme_featured.py is out of step with docs/PRESS.md; "
                         "run it and tools/readme_card.py to redraw docs/featured.png and docs/card.jpg")

    def test_every_row_is_a_linked_article(self):
        for name, rows in self.sections:
            for row in rows:
                with self.subTest(section=name, row=row[:60]):
                    self.assertEqual(len(LINK.findall(row)), 1, "row needs exactly one article link")
                    cells = [c.strip() for c in row.strip().strip("|").split("|")]
                    self.assertEqual(len(cells), 3, "row needs outlet, article and date")
                    self.assertTrue(cells[0] and cells[2], "outlet and date must not be empty")

    def test_no_article_is_listed_twice(self):
        seen = {}
        for name, rows in self.sections:
            for url in LINK.findall("\n".join(rows)):
                self.assertNotIn(url, seen, f"{url} is in both {seen.get(url)} and {name}")
                seen[url] = name


class PressVideosTest(unittest.TestCase):
    """The Videos section, which nothing read: its count, its day of views, its rows' shape and order."""

    def setUp(self):
        self.text = PRESS.read_text(encoding="utf-8")

    def test_the_page_agrees_with_its_videos(self):
        problems = video_problems(self.text)
        self.assertEqual([], problems, "\n".join(problems))

    def test_the_walk_reads_the_videos_this_file_was_written_for(self):
        # A reader that found no rows would pass the rule above; the section holds 229 of them.
        rows = videos(self.text)
        self.assertGreaterEqual(len(rows), 100, f"the Videos section has {len(rows)} rows")
        self.assertGreaterEqual(len([cells for cells in rows if row_date(cells[2])]), 100,
                                "fewer than 100 video rows carry a date the reader understands")
        self.assertIsNotNone(video_count(self.text),
                             "the intro no longer states '[N videos](#videos)'")
        self.assertIsNotNone(views_as_of(self.text),
                             "the Videos section no longer says when the views were read")


class FrontPageCoverageTest(unittest.TestCase):
    """The story paragraph's closing sentence, which repeats the three numbers in prose and nothing read."""

    def setUp(self):
        text = PRESS.read_text(encoding="utf-8")
        self.numbers = intro()
        self.expected = (self.numbers["total"], self.numbers["countries"], len(videos(text)))
        self.pages = card_alt_texts()

    def test_every_page_that_shows_the_card_repeats_the_counts(self):
        # The clause is the page's own copy of the three numbers its card's alt text states, so one page can show
        # a count in the picture and a stale one in prose; the pages come from the card, like the alt-text test, so
        # a translation that copies the picture is read without this list being edited first.
        self.assertLessEqual({"README.md", "README.de.md", "README.nl.md", "README.uk.md"}, set(self.pages),
                             "README.md, README.de.md, README.nl.md and README.uk.md no longer show "
                             "docs/card-halloween.jpg with an alt text")
        for name in sorted(self.pages):
            read = coverage_numbers((ROOT / name).read_text(encoding="utf-8"))
            with self.subTest(readme=name):
                self.assertIsNotNone(
                    read, f"{name} has no story paragraph that names docs/PRESS.md and YouTube, so the "
                          f"coverage numbers it used to repeat are gone")
                self.assertEqual(
                    self.expected, read,
                    f"{name} states (articles, countries, videos) {read} in its coverage sentence and "
                    f"docs/PRESS.md holds {self.expected}")

    def test_the_walk_reads_the_sentence_this_file_was_written_for(self):
        # A reader answering () or None for every page would pass nothing above; each page carries three
        # numbers today, and the English one is the page this file's own example is quoted from.
        self.assertGreaterEqual(len(self.pages), 4, f"only {len(self.pages)} front pages show the card")
        for name in ("README.md", "README.de.md", "README.nl.md", "README.uk.md"):
            read = coverage_numbers((ROOT / name).read_text(encoding="utf-8"))
            with self.subTest(readme=name):
                self.assertEqual(3, len(read or ()), f"{name}'s coverage sentence reads as {read}")


class CoverageReaderTest(unittest.TestCase):
    """The reader, over pages written here: the languages, a wrap, the separators, a stale number."""

    @staticmethod
    def page(clause, extra=""):
        return ("# CleanYourTV\n\n"
                "Two weeks on, it was the #1 trending repository on GitHub, with over 46K stars.\n\n"
                f"The coverage: {clause}, all in [docs/PRESS.md](docs/PRESS.md){extra}\n"
                "(numbers as of 30 October 2026).\n")

    def test_the_four_languages_are_read_the_same_way(self):
        for clause in ("351 articles from 46 countries and 229 videos on YouTube and Bilibili",
                       "351 Artikel aus 46 Ländern und 229 Videos auf YouTube und Bilibili",
                       "351 artikelen uit 46 landen en 229 video's op YouTube en Bilibili",
                       "351 стаття з 46 країн і 229 відео на YouTube та Bilibili"):
            with self.subTest(clause=clause):
                self.assertEqual((351, 46, 229), coverage_numbers(self.page(clause)))

    def test_a_sentence_wrapped_over_two_lines_is_read(self):
        page = self.page("351 articles from 46 countries and 229 videos on YouTube and Bilibili")
        self.assertEqual((351, 46, 229), coverage_numbers(page.replace("and 229 videos", "\nand 229 videos")))

    def test_a_thousand_separator_is_not_read_as_a_smaller_number(self):
        # README.nl.md writes its other counts the Dutch way; a country count of 46 written 46.000 is 46000.
        self.assertEqual((351, 46000, 229),
                         coverage_numbers(self.page("351 artikelen uit 46.000 landen en 229 video's op YouTube")))

    def test_a_stale_number_is_read_as_it_is_written(self):
        self.assertEqual((351, 46, 188),
                         coverage_numbers(self.page("351 articles from 46 countries and 188 videos on YouTube")))

    def test_a_sentence_with_a_fourth_number_is_reported(self):
        self.assertEqual((351, 46, 229, 30),
                         coverage_numbers(self.page("351 articles from 46 countries and 229 videos on YouTube, "
                                                   "read on 30 Oct")))

    def test_a_page_without_the_paragraph_is_quiet_about_it(self):
        self.assertIsNone(coverage_numbers("# CleanYourTV\n\nNo story section here.\n"))

    def test_a_paragraph_that_names_no_link_is_not_read_as_one(self):
        page = self.page("351 articles from 46 countries and 229 videos on YouTube")
        self.assertIsNone(coverage_numbers(page.replace("[docs/PRESS.md](docs/PRESS.md)", "docs/PRESS.md")))


class VideoReaderTest(unittest.TestCase):
    """The rules, over a page written here: a count that moved, a stale day, a row out of order, a repeat."""

    def page(self, rows, count=1, as_of="27 Oct", article=True):
        intro = ("# CleanYourTV in the press\n\n**3 articles**, 3 of them from outlets in 3 countries. "
                 f"[{count} videos](#videos) follow the articles.\n\n")
        articles = ("## United States\n\n| Outlet | Article | Date |\n| --- | --- | --- |\n"
                    "| One | [A](https://example.com/a) | 26&nbsp;Oct |\n\n") if article else ""
        table = ("## Videos\n\nOn YouTube, newest first; views as of " + as_of + ".\n\n"
                 "| Channel | Video | Date | Views |\n| --- | --- | --- | --- |\n")
        return intro + articles + table + "".join(rows)

    @staticmethod
    def row(number=1, date="27&nbsp;Oct", views="10", channel="Channel"):
        return f"| {channel} | [A video](https://www.youtube.com/watch?v={number}) | {date} | {views} |\n"

    def test_a_page_in_step_is_quiet(self):
        rows = [self.row(1, "27&nbsp;Oct", "10"), self.row(2, "26&nbsp;Oct", "1,024")]
        self.assertEqual([], video_problems(self.page(rows, count=2)))

    def test_the_article_rows_are_not_read_as_videos(self):
        # The real page opens with 351 article rows over 48 sections; only what follows '## Videos' is a video.
        problems = video_problems(self.page([self.row(1)], count=1))
        self.assertEqual([], problems)
        self.assertEqual(1, len(videos(self.page([self.row(1)], count=1))))

    def test_a_count_that_does_not_match_names_both_numbers(self):
        rows = [self.row(1), self.row(2)]
        problems = video_problems(self.page(rows, count=3))
        self.assertIn("the intro says 3 videos and the Videos section holds 2 rows", problems)

    def test_a_page_that_states_no_count_is_refused(self):
        text = self.page([self.row(1)]).replace("[1 videos](#videos) ", "")
        problems = video_problems(text)
        self.assertIn("the intro no longer states '[N videos](#videos)', so nothing claims the count "
                      "of the rows below it", problems)

    def test_a_video_dated_after_the_day_the_views_were_read_is_named(self):
        problems = video_problems(self.page([self.row(1, "28&nbsp;Oct")], as_of="27 Oct"))
        self.assertIn("the views are 'as of 27 Oct' and the newest video is dated 28 Oct", problems)

    def test_a_row_out_of_order_is_named(self):
        rows = [self.row(1, "26&nbsp;Oct"), self.row(2, "27&nbsp;Oct")]
        problems = video_problems(self.page(rows, count=2, as_of="26 Oct"))
        self.assertIn("video row 2 (27 Oct) is dated later than video row 1 (26 Oct) above it, and the "
                      "section runs newest first", problems)

    def test_a_row_without_a_link_is_named(self):
        problems = video_problems(self.page(["| Channel | just a title | 27&nbsp;Oct | 10 |\n"]))
        self.assertIn("video row 1 needs exactly one YouTube or Bilibili link, this one has 0", problems)

    def test_a_row_without_a_view_count_is_named(self):
        problems = video_problems(self.page(["| Channel | [A](https://www.youtube.com/watch?v=9) | "
                                             "27&nbsp;Oct |  |\n"]))
        self.assertIn("video row 1 has '' where a view count goes, not a number", problems)

    def test_a_row_with_five_cells_is_named(self):
        problems = video_problems(self.page([self.row(1)[:-2] + "| extra |\n"]))
        self.assertIn("video row 1 has 5 cells, not 4 (channel, video, date, views)", problems)

    def test_a_repeated_video_is_named(self):
        row = self.row(7)
        problems = video_problems(self.page([row, row], count=2))
        self.assertIn("https://www.youtube.com/watch?v=7 is listed twice, in video row 1 and 2", problems)

    def test_a_section_that_does_not_say_when_the_views_were_read_is_named(self):
        text = self.page([self.row(1)]).replace("views as of 27 Oct", "views")
        problems = video_problems(text)
        self.assertIn("the Videos section no longer says when the views were read "
                      "('views as of <date>')", problems)


if __name__ == "__main__":
    unittest.main()
