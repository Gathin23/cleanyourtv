"""The Haier H50H5F restart-loop bullet (#1578) must not present the attached list as the agent's work.

The report attached the six files PROMPT.md has the agent save before changing anything, and the reply
on the issue named eight packages from its disabled list. On the Haier-built AQUA MatrixTV EE 50, five of
those eight are disabled from the factory, so the list may be the TV's state before the debloat. The
bullet says so, keeps the way back that holds either way (the factory reset in the recovery menu), and
does not tell the reader to turn the eight on as an undo.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
TROUBLE = ROOT / "docs" / "TROUBLESHOOTING.md"
README = ROOT / "README.md"
AQUA = ROOT / "examples" / "aqua-matrixtv-ee-50" / "debloat.txt"
TITLE = "- **The TV restarts in a loop after a debloat**"

# The eight packages the reply on #1578 read off the attached disabled-package list.
EIGHT = [
    "com.cltv.mal",
    "com.cltv.hybrid",
    "com.mediatek.dtv.tvinput.isdbtuner",
    "com.mediatek.dtv.tvinput.atsctuner",
    "com.haier.gamingbar",
    "ru.haier.evo",
    "com.haier.apps.evoSmart",
    "it.hon.onboarding",
]


def bullet(text, start):
    """The markdown bullet that begins with `start`, up to the next top-level bullet, on one line."""
    lines = text.split("\n")
    first = next(i for i, line in enumerate(lines) if line.startswith(start))
    out = [lines[first]]
    for line in lines[first + 1:]:
        if line.startswith("- **") or line.startswith("## "):
            break
        out.append(line)
    return re.sub(r"\s+", " ", "\n".join(out))


def factory_disabled(path):
    """Package names the list header records after "Already disabled from the factory:"."""
    header = " ".join(line.lstrip("#").strip() for line in path.read_text(encoding="utf-8").splitlines()
                      if line.startswith("#"))
    after = header.split("Already disabled from the factory:", 1)[1].split(". ", 1)[0]
    return set(re.findall(r"[a-z][\w]*(?:\.[\w]+)+", after))


class HaierLoopBulletTest(unittest.TestCase):
    def setUp(self):
        self.text = TROUBLE.read_text(encoding="utf-8")
        self.loop = bullet(self.text, TITLE)

    def test_bullet_sits_under_working_on_the_tv(self):
        section = self.text.split("## Working on the TV", 1)[1].split("\n## ", 1)[0]
        self.assertIn(TITLE, section)

    def test_bullet_names_the_set_and_the_report(self):
        for token in ("Haier H50H5F", "Google TV 14", "seen once", "issues/1578"):
            with self.subTest(token=token):
                self.assertIn(token, self.loop)

    def test_bullet_does_not_say_the_agent_disabled_the_eight(self):
        self.assertIn("What the agent disabled is not known", self.loop)
        self.assertNotRegex(self.loop, r"(?i)the agent's list disabled")
        self.assertNotRegex(self.loop, r"(?i)\bboth tuner inputs\b")
        self.assertNotRegex(self.loop, r"(?i)likely cause")
        self.assertNotIn("`com.cltv.mal`**", self.loop, "the bold symptom must not name a cause")

    def test_bullet_says_the_list_may_be_the_backup(self):
        self.assertIn("PROMPT.md", self.loop)
        self.assertIn("before changing anything", self.loop)
        self.assertIn("may be the TV's state before the debloat", self.loop)

    def test_five_of_the_eight_are_factory_disabled_on_the_aqua(self):
        # The count in the bullet must match the AQUA list it links.
        on_aqua = factory_disabled(AQUA) & set(EIGHT)
        self.assertEqual(len(on_aqua), 5, sorted(on_aqua))
        self.assertIn("five of those eight are disabled from the factory", self.loop)
        self.assertIn("(../examples/aqua-matrixtv-ee-50/debloat.txt)", self.loop)
        self.assertTrue(AQUA.is_file())

    def test_bullet_does_not_offer_the_eight_as_the_undo(self):
        self.assertNotRegex(self.loop, r"(?i)lists all eight|re-enable all eight")
        for package in EIGHT[2:]:
            with self.subTest(package=package):
                self.assertNotIn(f"pm enable {package}", self.loop)
        self.assertIn("would switch on what the factory left off", self.loop)
        self.assertIn("each package from the list that was applied", self.loop)

    def test_bullet_keeps_the_way_back(self):
        self.assertIn("adb mdns services", self.loop)
        self.assertIn("recovery menu", self.loop)
        self.assertIn("factory reset turns every disabled package back on", self.loop)
        self.assertIn("nothing was uninstalled", self.loop)


class ReadmeSafetyAnswerTest(unittest.TestCase):
    def test_readme_points_to_the_factory_reset_and_the_bullet(self):
        text = README.read_text(encoding="utf-8")
        answer = text.split("<b>Is this safe? Can it brick the TV?</b>", 1)[1].split("</details>", 1)[0]
        answer = re.sub(r"\s+", " ", answer)
        for token in ("factory reset from its recovery menu", "issues/1578", "issues/1592",
                      "docs/TROUBLESHOOTING.md#working-on-the-tv"):
            with self.subTest(token=token):
                self.assertIn(token, answer)


if __name__ == "__main__":
    unittest.main()
