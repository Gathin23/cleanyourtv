"""Pins the 2019 webOS menu paths in `docs/devices/lg-webos.md` (#2989).

That note was written from a 2026 set, and its settings section is the 2026 list: Live Plus under
General, the user agreements on a page of their own, the ad switches under System. A 2019 set has no
System level in General at all, so an owner who follows the page on one looks for a screen that is not
there and stops. On 1 November 2026 an owner of an LG 50UM7300AUE typed out the four pages its switches
live on (#2989) and the maintainer answered "We'll add those paths for 2019 TVs"; the page is where the
repository already keeps this LG settings list, so the paths belong here beside the 2026 ones.

What these tests hold: the unit and the issue the paths come from, each path as the owner typed it,
each switch name, what differs from the 2026 menu (which is the whole point of the section), and the
honest line that no LG set was tested for this page. Whitespace is flattened before matching because
the page wraps its prose.
"""
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
NOTE = ROOT / "docs" / "devices" / "lg-webos.md"
README = ROOT / "README.md"


def flat(path):
    return re.sub(r"\s+", " ", path.read_text(encoding="utf-8"))


def sections(text):
    """(heading, flattened body) for every '## ' section, so a fact that moved is not read as present."""
    out = []
    for block in re.split(r"(?m)^## ", text)[1:]:
        heading, _, body = block.partition("\n")
        out.append(("## " + heading.strip(), re.sub(r"\s+", " ", body)))
    return out


class LGWebOS2019NoteTest(unittest.TestCase):
    def setUp(self):
        self.page = NOTE.read_text(encoding="utf-8")
        self.flat = flat(NOTE)
        self.by_heading = dict(sections(self.page))

    def test_names_the_unit_the_issue_and_the_year(self):
        for needle in ("#2989", "issues/2989", "50UM7300AUE", "`UM7300`", "2019"):
            with self.subTest(needle=needle):
                self.assertIn(needle, self.flat)

    def test_carries_each_path_as_the_owner_typed_it(self):
        for path in (
            "Settings → All Settings → General → Additional Settings → Live Plus",
            "Settings → All Settings → General → About This TV → User Agreements",
            "Settings → All Settings → General → Additional Settings → Advertisement",
            "Settings → All Settings → General → Additional Settings → Home Settings → Home Promotion",
            "Settings → All Settings → General → AI Service",
        ):
            with self.subTest(path=path):
                self.assertIn(path, self.flat)

    def test_keeps_every_switch_name_the_owner_listed(self):
        for name in (
            "Live Plus (ACR)",
            "Viewing information",
            "Voice information",
            "Interest-based & Cross-device advertising",
            "Who.Where.What?",
            "Limit AD Tracking",
            "Reset AD ID",
            "Home Promotion",
            "AI Recommendation",
        ):
            with self.subTest(name=name):
                self.assertIn(name, self.flat)

    def test_says_where_this_menu_differs_from_the_2026_one(self):
        # The section's reason to exist: a reader on a 2019 set is looking for menus the 2026 page
        # describes and will not find them. Each difference below is one the owner's paths show.
        self.assertIn("has no System level at all", self.flat)
        self.assertIn("sit under **About This TV**", self.flat)
        self.assertIn("share the one **Additional Settings** page", self.flat)
        self.assertIn("only switch under **AI Service**", self.flat)

    def test_is_honest_about_where_the_paths_come_from(self):
        self.assertIn("These are the owner's paths, not a run of ours", self.flat)
        self.assertIn("no LG set was tested for this page", self.flat)
        self.assertIn("nothing here says whether a firmware update puts any of them back", self.flat)

    def test_sits_between_the_2026_menu_and_the_dns_half(self):
        headings = list(self.by_heading)
        settings = headings.index("## The settings, with the remote")
        older = headings.index("## The same switches on a 2019 set")
        adguard = headings.index("## AdGuard Home for the TV only")
        self.assertLess(settings, older)
        self.assertLess(older, adguard)

    def test_the_2026_menu_it_sits_beside_is_untouched(self):
        self.assertIn("cleanyour.tv/not-android#lg-2026", self.flat)
        self.assertIn("Live Plus (ACR) off.", self.flat)
        self.assertIn("Limit AD Tracking on and the AD ID reset", self.flat)

    def test_the_note_is_still_one_the_front_page_points_at(self):
        # A note nothing links is found only by browsing the folder; README.md is where the
        # unsupported platforms row sends an LG owner.
        self.assertIn("(docs/devices/lg-webos.md)", README.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
