"""A helper that is on the accessibility list but does not filter, because the global flag is off (#1047).

Android can leave `enabled_accessibility_services` naming the launcher's `HomeKeyService` while
`accessibility_enabled` reads `0`: on a TCL C6K Pro (`G08`, Android 14) that is what a reboot left
behind, so nothing was bound, the Home button opened Google TV, and the page's two existing cases (a
force stop that empties the list, and a firmware `APP_AUTO_START` denial) did not describe it. The
launcher's own `restore_key_services` does not reach this state either: `ServiceRepair.restore()` writes
`ACCESSIBILITY_ENABLED` only after it has put a missing helper back on the list, and returns early when
the list is already complete.

Documentation only, so the tests pin what a reader has to be able to act on - both settings, the dump,
the three commands, the device it was seen on - and pin the claim about `ServiceRepair` to its own code,
so the note cannot go stale if the launcher starts repairing that state too. A misspelled `settings`
key or a wrong class name sends a reader back to "nothing changed".

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
TROUBLE = ROOT / "docs" / "TROUBLESHOOTING.md"
SERVICE_REPAIR = (ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome"
                  / "ServiceRepair.java")
THEMES_README = ROOT / "themes" / "README.md"
START = "- **The list still names the helper but"
SECTION = "## Accessibility helper enabled but the remote button does nothing"
LINK = re.compile(r"\]\(([^)\s]+)\)")


def bullet(text, start):
    """The markdown bullet that begins with `start`, its continuation lines and any indented block."""
    lines = text.split("\n")
    first = next(i for i, line in enumerate(lines) if line.startswith(start))
    out = [lines[first]]
    for line in lines[first + 1:]:
        if line.startswith("- **") or line.startswith("## "):
            break
        out.append(line)
    return "\n".join(out)


def section(text, heading):
    """The body under a `## ` heading, up to the next `## `."""
    lines = text.split("\n")
    start = next(i for i, line in enumerate(lines) if line.startswith(heading))
    out = []
    for line in lines[start + 1:]:
        if line.startswith("## "):
            break
        out.append(line)
    return "\n".join(out)


def flag_written_after_the_list_check(java):
    """True when `ServiceRepair` writes the flag only once it has also finished the enabled list.

    The note tells a reader that `restore_key_services` cannot repair a complete list, which is true
    only while the flag write sits after the `if (!missing) return;` early exit. Once the launcher
    repairs this state itself, this test fails and the note has to say so instead.
    """
    body = java.split("static void restore(Context context)")[1]
    early_return = body.find("if (!missing) return;")
    flag_write = body.find("ACCESSIBILITY_ENABLED")
    return early_return != -1 and flag_write != -1 and early_return < flag_write


class AccessibilityFlagNoteTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.trouble_text = TROUBLE.read_text(encoding="utf-8")
        cls.note = bullet(cls.trouble_text, START)
        cls.section_text = section(cls.trouble_text, SECTION)
        cls.java = SERVICE_REPAIR.read_text(encoding="utf-8")
        cls.themes = THEMES_README.read_text(encoding="utf-8")

    def test_bullet_exists_once(self):
        self.assertEqual(self.trouble_text.count(START), 1,
                         "the state must live in one bullet so its two settings cannot drift apart")

    def test_bullet_lives_in_the_accessibility_helper_section(self):
        self.assertIn(START, self.section_text,
                      "a reader who lands on the helper section has to find this state there")

    def test_bullet_names_both_settings_and_the_values(self):
        # The whole point: the enabled list looks right, so only the flag tells the two apart.
        self.assertIn("enabled_accessibility_services", self.note)
        self.assertIn("accessibility_enabled", self.note)
        self.assertIn("`0`", self.note)
        self.assertIn("Bound services:{}", self.note)

    def test_bullet_gives_the_three_commands_verbatim(self):
        for command in ("tools/tv.py 'settings get secure accessibility_enabled'",
                        "tools/tv.py 'settings put secure accessibility_enabled 1'",
                        "tools/tv.py 'dumpsys accessibility'"):
            with self.subTest(command=command):
                self.assertIn(command, self.note)

    def test_bullet_names_the_component_a_reader_will_see(self):
        self.assertIn("com.gathin.tvhome/com.gathin.tvhome.HomeKeyService", self.note)

    def test_bullet_names_the_device_and_build_it_was_seen_on(self):
        self.assertIn("TCL C6K Pro", self.note)
        self.assertIn("`G08`", self.note)
        self.assertIn("Android 14", self.note)

    def test_bullet_links_the_issue(self):
        self.assertIn("[#1047](https://github.com/Gathin23/cleanyourtv/issues/1047)", self.note)

    def test_bullet_says_restore_key_services_does_not_cover_it(self):
        self.assertIn("restore_key_services", self.note)
        self.assertIn("ServiceRepair", self.note)

    def test_service_repair_still_returns_before_writing_the_flag(self):
        # The note's claim, checked against the code it names rather than against the wording.
        self.assertTrue(flag_written_after_the_list_check(self.java),
                        "ServiceRepair now writes the flag on a complete list too: the note says it "
                        "does not, so reword the note (and this test) with the launcher change")
        self.assertIn("ENABLED_ACCESSIBILITY_SERVICES", self.java)

    def test_themes_readme_still_scopes_restore_key_services_to_a_force_stop(self):
        row = next(line for line in self.themes.split("\n")
                   if line.startswith("| `restore_key_services`"))
        self.assertIn("force stop", row,
                      "the key's own row is where a theme author reads what it repairs")

    def test_bullet_keeps_the_honest_limits(self):
        # Neither the cause nor persistence was established on the reporter's set; promising a fix
        # would be the kind of claim the repository asks contributors not to make.
        self.assertIn("not retested", self.note)
        self.assertIn("not established", self.note)

    def test_bullet_is_not_read_as_licence_to_rewrite_the_list(self):
        # The section forbids rewriting the enabled-services list as a blanket fix; this bullet writes
        # one secure setting instead and has to stay compatible with that sentence.
        self.assertIn("do not rewrite the enabled-services list", self.section_text)

    def test_bullet_links_resolve(self):
        broken = [target for target in LINK.findall(self.note)
                  if not target.startswith(("http://", "https://", "#"))
                  and not (TROUBLE.parent / target.split("#", 1)[0]).exists()]
        self.assertEqual(broken, [], "the bullet must not link a page that does not exist")
        for target in LINK.findall(self.note):
            if target.startswith(("http://", "https://")):
                self.assertEqual(target, "https://github.com/Gathin23/cleanyourtv/issues/1047")

    def test_bullet_extraction_stops_at_the_next_bullet(self):
        sample = "\n".join([START + " one", "  two", "- **Next** three"])
        self.assertEqual(bullet(sample, START), START + " one\n  two")

    def test_bullet_extraction_stops_at_the_next_heading(self):
        sample = "\n".join([START + " one", "  two", "## Next section", "text"])
        self.assertEqual(bullet(sample, START), START + " one\n  two")

    def test_bullet_extraction_keeps_an_indented_block(self):
        sample = "\n".join([START + " one", "", "  ```sh", "  tools/tv.py 'x'", "  ```", "", "prose"])
        self.assertIn("tools/tv.py 'x'", bullet(sample, START))

    def test_section_extraction_stops_at_the_next_heading(self):
        sample = "\n".join(["## One", "body", "## Two", "other"])
        self.assertEqual(section(sample, "## One"), "body")

    def test_the_flag_check_reads_the_early_return(self):
        # A checker that returned True for any file would let the note go stale unnoticed.
        self.assertTrue(flag_written_after_the_list_check(
            "static void restore(Context context) {\nif (!missing) return;\n"
            "Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);\n}"))
        self.assertFalse(flag_written_after_the_list_check(
            "static void restore(Context context) {\n"
            "Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);\n"
            "if (!missing) return;\n}"))
        self.assertFalse(flag_written_after_the_list_check("static void restore(Context context) {}"))


if __name__ == "__main__":
    unittest.main()
