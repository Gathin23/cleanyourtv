"""A list's Apply command that sets FORCE_SETUPWRAITH=1 says, in the same block, when that is safe.

`tools/disable.sh` and `disable.ps1` skip setupwraith while it owns a saved Wi-Fi network, because Android
deletes an app's saved networks when the app is disabled: the TV drops off Wi-Fi, and ADB and the phone remote
go with it. Their own messages offer the override only "on Ethernet" (on Wi-Fi they say to forget the network
and join it again), and AGENTS.md allows `FORCE_SETUPWRAITH=1` only in the situation those messages describe.

The Apply line is the command an owner of that model copies. One that carries the override with no condition
switches the guard off for every other unit, including the ones whose network in use belongs to setupwraith.
So when an Apply block (the `# Apply:` line and the indented lines under it) sets the override, the same block
has to say when it is safe: Ethernet, the owner check (`cname`, `dumpsys wifi`), or forgetting and re-joining
the network. A note further down the header does not count; it is not what gets pasted.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

# `FORCE_SETUPWRAITH=1` for disable.sh, `$env:FORCE_SETUPWRAITH=1` or `="1"` for disable.ps1.
OVERRIDE = re.compile(r"\bFORCE_SETUPWRAITH\s*=\s*[\"']?1\b")
CONDITION = re.compile(r"(?i)\bethernet\b|\bcname\b|dumpsys wifi|\bre-?join|join (?:it|the network) again")
# `# Apply:`, `# Apply (bash):`, `# Apply (PowerShell):`.
APPLY = re.compile(r"#\s*Apply\b[^:]*:")
# A line under it that carries on the same command: a comment indented past the labels, e.g.
# `#          FORCE_SETUPWRAITH=1 only on Ethernet ...`. `# Undo:` or a bare `#` ends the block.
CONTINUATION = re.compile(r"#\s{3,}\S")


def apply_blocks(text):
    """(line number, lines) for every Apply block of a list: the label line and its continuation lines."""
    lines = text.splitlines()
    blocks = []
    for index, line in enumerate(lines):
        if not APPLY.match(line):
            continue
        block = [line]
        for following in lines[index + 1:]:
            if not CONTINUATION.match(following):
                break
            block.append(following)
        blocks.append((index + 1, block))
    return blocks


def override_problems(text):
    """Why an Apply block in this list switches the Wi-Fi guard off without a condition, one sentence each."""
    problems = []
    for number, block in apply_blocks(text):
        joined = " ".join(block)
        if OVERRIDE.search(joined) and not CONDITION.search(joined):
            problems.append(
                f"line {number}: the Apply command sets FORCE_SETUPWRAITH=1 but does not say when that is "
                f"safe (on Ethernet, after checking the Wi-Fi owner, or after re-joining the network)")
    return problems


class ApplyOverrideCaveatTest(unittest.TestCase):
    def test_every_apply_override_says_when_it_is_safe(self):
        lists = blocks = overrides = 0
        failures = []
        for path in sorted((ROOT / "examples").glob("*/debloat*.txt")):
            lists += 1
            text = path.read_text(encoding="utf-8")
            found = apply_blocks(text)
            blocks += len(found)
            overrides += sum(1 for _, block in found if OVERRIDE.search(" ".join(block)))
            for problem in override_problems(text):
                failures.append(f"{path.relative_to(ROOT).as_posix()}: {problem}")
        self.assertGreaterEqual(lists, 250, "the sweep read too few lists to be meaningful")
        self.assertGreaterEqual(blocks, 250, "the sweep found too few Apply lines")
        self.assertGreaterEqual(overrides, 2, "the sweep found no Apply block with the override, so it reads nothing")
        self.assertEqual([], failures, "\n".join(failures))

    def test_an_override_with_no_condition_is_reported(self):
        text = (
            "# Apply:   TV_HOST=<tv ip> FORCE_SETUPWRAITH=1 tools/disable.sh examples/x/debloat.txt\n"
            "# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/x/debloat.txt com.google.android.apps.tv.launcherx\n"
        )
        problems = override_problems(text)
        self.assertEqual(1, len(problems))
        self.assertIn("line 1", problems[0])

    def test_an_ethernet_condition_under_the_apply_line_is_quiet(self):
        text = (
            "# Apply:   FORCE_SETUPWRAITH=1 TV_HOST=<tv ip> tools/disable.sh examples/x/debloat.txt\n"
            "#          FORCE_SETUPWRAITH=1 only on Ethernet with no saved Wi-Fi: disable.sh otherwise skips setupwraith\n"
            "# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/x/debloat.txt com.google.android.apps.tv.launcherx\n"
        )
        self.assertEqual([], override_problems(text))

    def test_a_rejoin_first_condition_is_quiet(self):
        text = (
            "# Apply:   TV_HOST=<tv ip> tools/disable.sh examples/x/debloat.txt\n"
            "#          (forget and re-join Wi-Fi first, see setupwraith below; then FORCE_SETUPWRAITH=1)\n"
        )
        self.assertEqual([], override_problems(text))

    def test_the_powershell_form_counts_as_the_override(self):
        text = (
            "# Apply (PowerShell): $env:FORCE_SETUPWRAITH=\"1\"; tools\\disable.ps1 -TvHost <tv ip> examples\\x\\debloat.txt\n"
        )
        self.assertEqual(1, len(override_problems(text)))

    def test_a_condition_outside_the_block_does_not_count(self):
        # The Undo line ends the block, and the setupwraith note further down is not what gets pasted.
        text = (
            "# Apply:    FORCE_SETUPWRAITH=1 tools/disable.sh examples/x/debloat.txt\n"
            "# Restore:  tools/restore.sh examples/x/debloat.txt com.google.android.apps.tv.launcherx\n"
            "#\n"
            "# setupwraith: disable.sh skips it while it owns the saved Wi-Fi network; on Ethernet that is fine.\n"
        )
        self.assertEqual([(1, [text.splitlines()[0]])], apply_blocks(text))
        self.assertEqual(1, len(override_problems(text)))

    def test_an_apply_line_without_the_override_needs_nothing(self):
        text = "# Apply:   TV_HOST=<tv ip> tools/disable.sh examples/x/debloat.txt\n"
        self.assertEqual([], override_problems(text))


if __name__ == "__main__":
    unittest.main()
