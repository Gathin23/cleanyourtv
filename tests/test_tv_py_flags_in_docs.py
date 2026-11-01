"""Every `tools/tv.py --flag` a page hands a reader is a flag tv.py has.

`tools/tv.py` answers a flag it does not have with its own usage and exit 2, so a page cannot tell a
reader to run one and have it work: `tv.py --proob` used to reach the TV's shell as a command, and
since that was closed the flag stops the run before the TV is touched. `docs/devices/skyworth-7t861-a23.md`
told a reader to install the launcher with `tools/tv.py --install <apk>`, and there has never been an
`--install` flag: on the one OS this page is about (Coocaa OS keeps Home in a system property, not in
an intent), step 2 of its only route to the new launcher failed with a usage line, for the launcher and
for the shim both. The APK goes up with `tools/b64push.py` and the TV's own `pm install` runs it, which
is what the page says now.

The flags are read out of `tools/tv.py` itself (`USAGE`, `ARITY` and the `--wait` that belongs to
`--screenshot`), not copied in, so a flag the client gains or loses is followed here.
"""
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parent.parent
TV_PY = ROOT / "tools" / "tv.py"
# The one page this file was written for; the sweep is over every page in the tree.
NOTE = ROOT / "docs" / "devices" / "skyworth-7t861-a23.md"

FLAG = re.compile(r"(?<![\w-])--[a-z][a-z-]*")
# Where a shell command starts: everything after it is the TV's, not tv.py's, so `pm install --user 0`
# inside the quotes is not read as a flag of the client.
QUOTES = ("'", '"')


def accepted_flags(source):
    """The flags tools/tv.py accepts, read out of its own text."""
    usage = re.search(r'USAGE = """(.*?)"""', source, re.S)
    if usage is None:
        raise AssertionError("tools/tv.py has no USAGE block to read its flags from")
    flags = set(FLAG.findall(usage.group(1)))
    # The flags handled outside USAGE: ARITY's keys (--probe, --push, --pull, --batch, --reboot).
    flags |= set(re.findall(r'"(?P<flag>--[a-z-]+)":', source))
    return flags


def calls(text):
    """(line number, flag) for every flag a page hands to tools/tv.py, once per line."""
    found = []
    for number, line in enumerate(text.splitlines(), 1):
        if "tv.py" not in line:
            continue
        on_line = []
        rest = line
        while "tv.py" in rest:
            rest = rest.split("tv.py", 1)[1]
            head = re.split(r"['\"]", rest, maxsplit=1)[0]
            for flag in FLAG.findall(head):
                if flag not in on_line:
                    on_line.append(flag)
        found.extend((number, flag) for flag in on_line)
    return found


def unknown(text, flags):
    return [(number, flag) for number, flag in calls(text) if flag not in flags]


def pages():
    found = [path for path in ROOT.rglob("*.md")
             if ".git" not in path.parts and "venv" not in path.parts]
    return sorted(found)


class AcceptedFlags(unittest.TestCase):
    def setUp(self):
        self.flags = accepted_flags(TV_PY.read_text(encoding="utf-8"))

    def test_the_usage_flags_are_read_out_of_the_script(self):
        for flag in ("--probe", "--screenshot", "--wait", "--batch", "--push", "--pull", "--reboot",
                     "--help"):
            self.assertIn(flag, self.flags)

    def test_install_is_not_a_flag_of_the_client(self):
        # tv.py pushes a file and the TV's pm installs it; an --install flag does not exist.
        self.assertNotIn("--install", self.flags)

    def test_a_page_that_tells_a_reader_to_install_is_reported(self):
        page = ("Install it:\n\n"
                "```bash\n"
                "TV_HOST=<tv ip> tools/tv.py --install app-release.apk\n"
                "```\n")
        self.assertEqual([(4, "--install")], unknown(page, self.flags))

    def test_a_flag_the_client_has_is_quiet(self):
        page = "TV_HOST=<tv ip> tools/tv.py --push app.apk /data/local/tmp/app.apk\n"
        self.assertEqual([], unknown(page, self.flags))

    def test_a_shell_flag_inside_the_command_is_not_read_as_a_flag(self):
        # `--user 0` is pm's, not tv.py's: the invocation's own flags end at the quote.
        page = "TV_HOST=<tv ip> tools/tv.py 'pm install -r --user 0 /data/local/tmp/app.apk'\n"
        self.assertEqual([], unknown(page, self.flags))

    def test_two_invocations_on_one_line_are_both_read(self):
        page = ("TV_HOST=<tv ip> tools/tv.py --probe; TV_HOST=<tv ip> tools/tv.py --install x.apk\n")
        self.assertEqual([(1, "--probe"), (1, "--install")], calls(page))
        self.assertEqual([(1, "--install")], unknown(page, self.flags))

    def test_screenshot_keys_are_not_read_as_flags(self):
        page = "tools/tv.py --screenshot shots/01.png DPAD_DOWN --wait 2\n"
        self.assertEqual([], unknown(page, self.flags))

    def test_a_page_without_the_client_is_quiet(self):
        self.assertEqual([], unknown("# A page\ntools/disable.sh examples/x/debloat.txt\n",
                                     self.flags))

    def test_a_script_that_lost_its_usage_is_refused(self):
        with self.assertRaises(AssertionError):
            accepted_flags("ARITY = {'--probe': 1}\n")


class EveryPage(unittest.TestCase):
    def test_every_flag_a_page_uses_is_a_flag_of_tv_py(self):
        flags = accepted_flags(TV_PY.read_text(encoding="utf-8"))
        read = pages()
        used = 0
        wrong = []
        for page in read:
            found = calls(page.read_text(encoding="utf-8"))
            used += len(found)
            for number, flag in found:
                if flag not in flags:
                    wrong.append(f"{page.relative_to(ROOT)}:{number}: {flag}")
        # Read guards: a tree that was not walked, or pages that stopped naming the client, would
        # pass this test by finding nothing.
        self.assertGreaterEqual(len(read), 150, "pages read")
        self.assertGreaterEqual(used, 40, "flags handed to tv.py")
        self.assertEqual([], wrong, "a page hands tv.py a flag it does not have")

    def test_the_coocaa_note_installs_with_the_commands_that_exist(self):
        text = NOTE.read_text(encoding="utf-8")
        step = text.split("### Step 2", 1)[1].split("### Step 3", 1)[0]
        self.assertIn("tools/b64push.py", step, "the APK goes up with b64push")
        self.assertIn("pm install -r", step, "the TV's own pm install runs it")
        self.assertNotIn("--install", step)
        # The shim is not in this repository, so the page has to say where it comes from.
        self.assertIn("coocaa-home-shim", step)
        self.assertRegex(step, r"https://github\.com/[A-Za-z0-9_.-]+/coocaa-home-shim")

    def test_the_note_still_builds_with_the_id_its_home_property_points_at(self):
        # Step 1's gradle flag is the one launcher/app/build.gradle reads, and Step 3 points the
        # property at the shim's package, so neither half can be renamed on its own.
        text = NOTE.read_text(encoding="utf-8")
        gradle = (ROOT / "launcher" / "app" / "build.gradle").read_text(encoding="utf-8")
        self.assertIn("launcherApplicationId", gradle)
        self.assertIn("-PlauncherApplicationId=com.coocaa.tvhome", text)
        self.assertIn("com.ccos.tv.homepage", text)


if __name__ == "__main__":
    unittest.main()
