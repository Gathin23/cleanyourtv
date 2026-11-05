"""The launcher's standby opt-in is described somewhere a reader with the symptom can find it.

`GuardKeep` can take `HomeKeyService` off the accessibility list while the screen is off and put it back
when the screen lights, for a TV whose power key never wakes the set while a key-filtering helper stays
bound (TCL tc8000_ay30a2, #1387). Nothing turned it on except a Java comment: `grep -rn guard_off_in_standby`
found the broadcast in `GuardConfig.java` and nowhere else, and
`docs/TROUBLESHOOTING.md` used the words "the standby opt-in" once, in the direct-boot bullet, with no page
behind them - so a reader whose remote's power button does nothing had no way to reach the three writes
that arm it, and no way to learn that two of them (`WRITE_SECURE_SETTINGS`, the `GuardRevival` listener)
are what make the helper come back at all.

The identifiers are not copied in here: the action comes from `GuardKeep.ACTION`, the extra from
`GuardConfig.hasExtra(...)`, the component names from the files and the manifest, the permission from the
receiver's `android:permission`, and the install flag from `launcher/build-and-install.sh`, so a launcher
that renames any of them fails this file instead of leaving the page quietly wrong. The section is found
through the link in the direct-boot bullet rather than by its heading, so the wording stays free.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "docs" / "TROUBLESHOOTING.md"
JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome"
MANIFEST = ROOT / "launcher" / "app" / "src" / "main" / "AndroidManifest.xml"
INSTALL = ROOT / "launcher" / "build-and-install.sh"

# A level-2 heading, the shape this page's troubleshooting entries take.
HEADING = re.compile(r"^##\s+(.+?)\s*$")
# A fenced block, so a command list can be told from the prose around it.
FENCE = re.compile(r"^```([a-zA-Z0-9]*)[ \t]*$")


def slug(heading):
    """GitHub's anchor for a heading: lower case, no punctuation, spaces as hyphens."""
    text = re.sub(r"[^\w\s-]", "", heading.strip().lstrip("#").strip().lower())
    return re.sub(r"\s+", "-", text)


def sections(page):
    """{anchor: [lines]} for every level-2 section, headings inside a fence not counted."""
    found, current, fenced = {}, None, False
    for line in page.splitlines():
        if FENCE.match(line):
            fenced = not fenced
        elif not fenced:
            match = HEADING.match(line)
            if match:
                current = slug(match.group(1))
                found[current] = []
                continue
        if current is not None:
            found[current].append(line)
    return found


def link_targets(text):
    """Every in-page anchor a piece of markdown links to, in file order."""
    return re.findall(r"\]\(#([^)\s]+)\)", text)


def fenced_lines(text):
    """Every line inside a fenced block, which is where these commands live."""
    out, fenced = [], False
    for line in text.splitlines():
        if FENCE.match(line):
            fenced = not fenced
            continue
        if fenced:
            out.append(line)
    return out


def bullet_around(page, needle):
    """The list item that carries `needle`, joined into one line, or '' when no bullet does.

    The direct-boot bullet wraps its link over its own line, so the item is read as a whole.
    """
    items, current = [], None
    for line in page.splitlines():
        if line.startswith("- "):
            if current is not None:
                items.append(current)
            current = line
        elif current is not None and line.startswith("  "):
            current += " " + line.strip()
        else:
            if current is not None:
                items.append(current)
            current = None
    if current is not None:
        items.append(current)
    for item in items:
        if needle in item:
            return item
    return ""


class Source(unittest.TestCase):
    """What the launcher and the installer actually say, read rather than copied in."""

    @classmethod
    def setUpClass(cls):
        cls.keep = (JAVA / "GuardKeep.java").read_text(encoding="utf-8")
        cls.config = (JAVA / "GuardConfig.java").read_text(encoding="utf-8")
        cls.revival = (JAVA / "GuardRevival.java").read_text(encoding="utf-8")
        cls.manifest = MANIFEST.read_text(encoding="utf-8")
        cls.install = INSTALL.read_text(encoding="utf-8")
        cls.doc = DOC.read_text(encoding="utf-8")

        action = re.search(r'ACTION\s*=\s*"([^"]+)"', cls.keep)
        cls.action = action.group(1) if action else ""
        extra = re.search(r'hasExtra\("([^"]+)"\)', cls.config)
        cls.extra = extra.group(1) if extra else ""
        listener = re.search(r"allow_listener\s+([^\s*}]+)", cls.revival)
        cls.listener = listener.group(1) if listener else ""
        cls.namespace = re.search(r"^\s*namespace\s+'([^']+)'",
                                  (ROOT / "launcher" / "app" / "build.gradle").read_text(encoding="utf-8"),
                                  re.M)
        cls.namespace = cls.namespace.group(1) if cls.namespace else ""
        # The helper that stands down is the one that calls GuardKeep.stepOut().
        cls.step_out = [path.stem for path in JAVA.glob("*.java")
                        if "GuardKeep.stepOut(" in path.read_text(encoding="utf-8")]
        grant = re.search(r'\$\{(RESTORE_KEY_SERVICES):-0\}', cls.install)
        cls.install_flag = grant.group(1) if grant else ""
        # The guard receiver, and the permission it is declared with.
        blocks = re.findall(r"<receiver[\s\S]*?</receiver>", cls.manifest)
        cls.guard = [block for block in blocks if 'android:name=".GuardConfig"' in block]
        perm = re.search(r'android:permission="([^"]+)"', cls.guard[0]) if cls.guard else None
        cls.receiver_permission = perm.group(1) if perm else ""

    def test_the_sources_this_page_depends_on_are_still_there(self):
        self.assertTrue(self.action, "GuardKeep.ACTION is gone")
        self.assertTrue(self.extra, "GuardConfig no longer reads an extra")
        self.assertTrue(self.listener, "GuardRevival's javadoc no longer gives the allow_listener command")
        self.assertTrue(self.namespace, "launcher/app/build.gradle has no namespace")
        self.assertEqual(self.step_out, ["HomeKeyService"],
                         "the helper that calls GuardKeep.stepOut() moved")
        self.assertTrue(self.install_flag, "build-and-install.sh no longer takes RESTORE_KEY_SERVICES")
        self.assertTrue(self.receiver_permission.endswith("WRITE_SECURE_SETTINGS"),
                        "the guard receiver no longer needs WRITE_SECURE_SETTINGS")

    def test_the_receiver_is_still_declared_for_the_action(self):
        self.assertEqual(len(self.guard), 1, "the guard receiver is gone or declared twice")
        self.assertIn(f'android:name="{self.action}"', self.guard[0])


class TheLink(Source):
    """The bullet that used the words has to lead somewhere."""

    def test_the_direct_boot_bullet_links_the_opt_in(self):
        bullet = bullet_around(self.doc, "standby opt-in")
        self.assertTrue(bullet, "no bullet mentions the standby opt-in any more")
        self.assertTrue(link_targets(bullet),
                        "the standby opt-in is named with no link: " + bullet[:160])

    def test_the_link_resolves_to_a_section(self):
        bullet = bullet_around(self.doc, "standby opt-in")
        targets = link_targets(bullet)
        self.assertTrue(targets, "no link in the standby-opt-in bullet")
        found = sections(self.doc)
        for target in targets:
            self.assertIn(target, found,
                          f"#{target} is linked and no heading answers to it")

    def test_the_section_is_a_troubleshooting_entry(self):
        bullet = bullet_around(self.doc, "standby opt-in")
        targets = link_targets(bullet)
        self.assertTrue(targets, "the standby opt-in is named with no link to follow")
        found = sections(self.doc)
        if targets[0] not in found:
            self.fail(f"#{targets[0]} is linked and no heading answers to it")
        body = found[targets[0]]
        self.assertGreaterEqual(len(body), 8, "the section is a stub")
        self.assertGreaterEqual(len(fenced_lines("\n".join(body))), 3,
                                "the section gives no commands to run")


class TheSection(Source):
    """Three writes, in the shape GuardConfig and GuardKeep expect."""

    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        bullet = bullet_around(cls.doc, "standby opt-in")
        targets = link_targets(bullet)
        found = sections(cls.doc)
        # Empty when the bullet links nothing, or links an anchor no heading answers: every test below
        # then fails with its own message instead of an error from here.
        cls.body = "\n".join(found[targets[0]]) if targets and targets[0] in found else ""
        cls.commands = fenced_lines(cls.body)

    def test_the_section_is_reachable_from_the_bullet(self):
        self.assertTrue(self.body, "no section is linked from the standby-opt-in bullet")
        self.assertTrue(self.commands, "the linked section carries no commands")

    def test_the_broadcast_names_the_action_the_receiver_and_the_extra(self):
        broadcasts = [line for line in self.commands if "am broadcast" in line]
        self.assertTrue(broadcasts, "no am broadcast line")
        for line in broadcasts:
            self.assertIn(f"-a {self.action}", line, "a broadcast line does not name the action")
            self.assertIn(f"-n {self.namespace}/.GuardConfig", line,
                          "a broadcast line does not name the receiver")
        self.assertTrue(any(f"--ez {self.extra} true" in line for line in broadcasts),
                        f"no broadcast turns {self.extra} on")

    def test_the_opt_in_can_be_turned_off_again(self):
        self.assertTrue(any(f"--ez {self.extra} false" in line for line in self.commands),
                        "the page never says how to turn the opt-in off")

    def test_the_page_gives_the_listener_command(self):
        self.assertTrue(any(self.listener in line for line in self.commands),
                        f"no command enables {self.listener}")

    def test_the_page_gives_the_grant_that_makes_the_helper_come_back(self):
        wanted = f"pm grant {self.namespace} {self.receiver_permission}"
        self.assertTrue(any(wanted in line for line in self.commands),
                        f"no command carries {wanted!r}")
        self.assertIn(self.install_flag, self.body,
                      "the install flag that already grants it is not named")

    def test_the_page_names_the_helper_that_stands_down(self):
        component = f"{self.namespace}/.{self.step_out[0]}"
        self.assertIn(component, self.body,
                      f"the section does not name {component}")


class Readers(unittest.TestCase):
    """The readers above, on text written here, so a broken one cannot pass quietly."""

    def test_slug_follows_the_github_rule(self):
        self.assertEqual(slug("## The power key does not wake the TV"), "the-power-key-does-not-wake-the-tv")
        self.assertEqual(slug("A key helper is bound, and a button stopped"), "a-key-helper-is-bound-and-a-button-stopped")
        self.assertEqual(slug("  Wi-Fi 6: what (if anything)"), "wi-fi-6-what-if-anything")

    def test_sections_stop_at_the_next_heading(self):
        page = "intro\n\n## One thing\nbody a\n\n## Two things\nbody b\n"
        found = sections(page)
        self.assertEqual(sorted(found), ["one-thing", "two-things"])
        self.assertEqual(found["one-thing"], ["body a", ""])
        self.assertEqual(found["two-things"], ["body b"])

    def test_a_heading_inside_a_fence_is_not_a_section(self):
        page = "## Real\n```sh\n## Not a heading\n```\nmore\n"
        found = sections(page)
        self.assertEqual(sorted(found), ["real"])
        self.assertIn("## Not a heading", found["real"])

    def test_link_targets_reads_anchors_only(self):
        text = "see [it](#a-b) and [there](../x.md) and [one](#c)"
        self.assertEqual(link_targets(text), ["a-b", "c"])

    def test_fenced_lines_are_the_commands(self):
        text = "prose\n```sh\ncmd one\ncmd two\n```\nprose\n"
        self.assertEqual(fenced_lines(text), ["cmd one", "cmd two"])

    def test_bullet_around_joins_a_wrapped_item(self):
        page = ("- **First.** One line.\n- **Second.** Names the opt-in\n  and [links it](#here).\n"
                "- **Third.** Nothing.\n")
        item = bullet_around(page, "opt-in")
        self.assertIn("links it](#here)", item)
        self.assertNotIn("Third", item)
        self.assertEqual(bullet_around(page, "absent"), "")


if __name__ == "__main__":
    unittest.main()
