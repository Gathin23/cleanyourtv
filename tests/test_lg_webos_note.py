"""An LG webOS set is documented as out of reach for this repo's tools, with settings plus DNS as the route.

#3092 offered one owner's notes on a 2026 NU850B. As with Tizen, the answer to "can this repo clean my
LG?" is no: 5555 is closed, so there is no `pm disable-user`, no screenshot and no Home takeover. The
useful part is the DNS filter, and that holds only with a firewall rule against other resolvers, so the
page has to keep that rule. Root is outside what the repo guides people through, so the page must not
point at an exploit.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import unittest


PAGE = Path(__file__).resolve().parents[1] / "docs" / "devices" / "lg-webos.md"


def page():
    return PAGE.read_text(encoding="utf-8")


def ports():
    found = {}
    for line in page().split("\n"):
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) == 3 and cells[0].isdigit():
            found[cells[0]] = cells[1].lower()
    return found


class LgWebosPageTest(unittest.TestCase):
    def test_page_names_the_set_and_the_report(self):
        text = page()
        self.assertIn("65NU850BPSA", text)
        self.assertIn("webOS 26", text)
        self.assertIn("#3092", text)

    def test_no_adb(self):
        self.assertEqual(ports().get("5555"), "closed")

    def test_the_page_says_why_the_lists_cannot_be_applied(self):
        text = page()
        self.assertRegex(text, r"(?i)no\s+`pm disable-user`")
        self.assertRegex(text, r"(?i)no screenshot")
        self.assertRegex(text, r"(?i)no home takeover")

    def test_the_dns_filter_keeps_its_bypass_rule(self):
        text = page()
        self.assertIn("853", text, "DNS-over-TLS has to be blocked too")
        self.assertRegex(text, r"(?i)UDP \| any \| 53")
        self.assertIn("us.security.lgtvsdp.com", text, "blocking the clock host breaks the set")

    def test_the_page_does_not_guide_rooting(self):
        text = page().lower()
        for word in ("slopbro", "own-your-glass", "jsserver", "exploit"):
            self.assertNotIn(word, text)


if __name__ == "__main__":
    unittest.main()
