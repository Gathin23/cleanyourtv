"""The core-package bullet names the TV middleware the tools refuse now (#884, #1578).

`tools/disable.sh`, `disable.ps1` and `check.py --fix` refused the core Android packages of an original
Mi TV Stick (#884) and nothing else until a Haier H50H5F restarted in a loop after a debloat with
`com.cltv.mal` off (#1578). The guard covers four TV middleware packages now, PROMPT.md says so, and
`docs/TROUBLESHOOTING.md` is where an owner whose set is stuck reads what the tools refuse: its #884
bullet enumerated every guarded name except those four, and the #1578 bullet under it lists
`com.cltv.mal` among eight packages without saying it is one of the guarded names today.

Both are pinned here against the guard itself, so a name that reaches CORE_RE without the page - or a
page that claims a name the guard does not hold - fails: the four names are read out of PROMPT.md's
sentence, refused through recovery.core_packages(), matched against the bash and PowerShell copies of
the pattern, and required in the bullet. The names on the #1578 list that are *not* guarded are pinned
too, because the bullet says so: `com.cltv.hybrid` (Live TV) and the tuner inputs are what tested lists
keep or turn off by hand, not what the tools refuse.
"""
import re
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import recovery  # noqa: E402

MIDDLEWARE = ["com.cltv.mal", "com.mediatek.tis", "com.mediatek.tv.service", "com.mediatek.tv.agent"]
# The rest of the eight packages the #1578 report's disabled-package list names: live TV, the two tuner
# inputs and the Haier apps. None of them is a name the tools refuse.
NOT_GUARDED = ["com.cltv.hybrid", "com.mediatek.dtv.tvinput.isdbtuner",
               "com.mediatek.dtv.tvinput.dvbtuner", "com.haier.apps"]

PAGE = ROOT / "docs" / "TROUBLESHOOTING.md"
CORE_BULLET = "- **The TV no longer boots after a list that disabled Android's core packages**"
LOOP_BULLET = "- **The TV restarts in a loop after a debloat**"


def bullet(start):
    """The bullet that begins with `start`, up to the next `- **` at the left margin."""
    text = PAGE.read_text(encoding="utf-8")
    rest = text[text.index(start) + len(start):]
    end = re.search(r"\n- \*\*", rest)
    return start + (rest[:end.start()] if end else rest)


def core_bullet():
    return bullet(CORE_BULLET)


def loop_bullet():
    return bullet(LOOP_BULLET)


def script_pattern(path, regex):
    match = re.search(regex, path.read_text(encoding="utf-8"), re.M)
    if not match:
        raise AssertionError(f"no guard pattern found in {path}")
    return re.compile(match.group(1))


class GuardedMiddlewareTest(unittest.TestCase):
    def test_the_guard_refuses_the_four(self):
        self.assertEqual(MIDDLEWARE, recovery.core_packages(MIDDLEWARE))

    def test_the_live_tv_and_tuner_names_are_not_guarded(self):
        # The #1578 bullet says so, and tested lists keep com.cltv.hybrid on purpose.
        self.assertEqual([], recovery.core_packages(NOT_GUARDED))

    def test_the_bash_powershell_and_python_patterns_all_hold_them(self):
        # The #884 bullet names three tools that refuse a list; the four have to be in each pattern.
        patterns = [recovery.CORE_RE,
                    script_pattern(ROOT / "tools" / "disable.sh", r"^CORE_RE='([^']+)'$"),
                    script_pattern(ROOT / "tools" / "disable.ps1", r"^\$coreRe = '([^']+)'$")]
        for name in MIDDLEWARE:
            for pattern in patterns:
                with self.subTest(name=name, pattern=pattern.pattern[:40]):
                    self.assertTrue(pattern.match(name), f"{name} is not refused by {pattern.pattern}")

    def test_the_core_bullet_names_them(self):
        text = core_bullet()
        self.assertIn("pm enable", text, "the bullet is not the one about a set that will not boot")
        for name in MIDDLEWARE:
            self.assertIn(f"`{name}`", text,
                          f"the core-package bullet does not name {name}, which the tools refuse (#1578)")
        self.assertIn("#884", text)

    def test_the_core_bullet_cites_the_haier_issue_and_the_message(self):
        text = core_bullet()
        self.assertIn("issues/1578", text,
                      "the bullet names the middleware without saying why, or linking #1578")
        self.assertIn("core Android or TV middleware", text,
                      "the bullet does not quote the message a refusal of one of the four prints")

    def test_the_prompt_md_sentence_names_the_same_four(self):
        text = (ROOT / "PROMPT.md").read_text(encoding="utf-8")
        block = re.search(r"TV\s+middleware \(([^)]*)\)", text)
        self.assertIsNotNone(block, "PROMPT.md no longer describes the TV middleware packages")
        named = re.findall(r"`(com\.[a-z0-9.]+)`", block.group(1))
        self.assertEqual(sorted(MIDDLEWARE), sorted(set(named)),
                         "PROMPT.md and this test disagree about what the middleware is")
        for name in named:
            self.assertIn(f"`{name}`", core_bullet(), f"PROMPT.md names {name} and the page does not")

    def test_the_loop_bullet_says_which_of_its_eight_names_is_guarded(self):
        text = loop_bullet()
        self.assertIn("`com.cltv.mal`", text)
        self.assertIn("the bullet above", text,
                      "the loop bullet does not send the reader to the guarded list")
        self.assertIn("not guarded", text,
                      "the loop bullet does not say the other seven names are not refused")
        self.assertNotIn("`com.cltv.hybrid`", core_bullet(),
                         "the core-package bullet lists Live TV, which tested lists keep or drop by hand")

    def test_the_page_still_carries_both_bullets(self):
        text = PAGE.read_text(encoding="utf-8")
        self.assertEqual(1, text.count(CORE_BULLET))
        self.assertEqual(1, text.count(LOOP_BULLET))
        self.assertGreater(len(text), 50_000, "the page was read as something much shorter than itself")


if __name__ == "__main__":
    unittest.main()
