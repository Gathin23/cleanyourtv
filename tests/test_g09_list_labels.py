"""Pins the two corrected comments in the G09 list (#881).

`com.mirakulo.astrotv.service` was labelled "analytics service (data app; on the AS23 unit)". Its own
manifest says otherwise - label AstroTVService, permission `com.tcl.tv.ginga.PERMISSION`, and the APK
carries `libtqtvd_astrotv.so` and WPE WebKit - because it is Ginga, the ISDB-Tb broadcast interactive-TV
engine. On a unit whose area config leaves `Enable_ginga` on, disabling it turns broadcasters'
interactive apps off, so "analytics, safe to drop" is the wrong advice for the readers most likely to
meet it (this list is written on a set used in Brazil).

`com.google.android.tv.remote.service` said "Google TV phone-app remote" and nothing else, while it is
also the bridge for the physical remote's microphone (the remote.service bullet in
docs/TROUBLESHOOTING.md), and the phone half has limits a reader cannot discover from that label: the
keylayout the phone's virtual remote uses has no Settings/Info/Guide/Menu and no channel keys, so those
presses reach the TV as KEYCODE_UNKNOWN.

The helpers read the list the way `tools/disable.sh` does - the text before the first `#` is the package,
everything after it is a comment - so a note that drifts onto the wrong package, or a package a later
edit comments out, fails here instead of being silently unverifiable.
"""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = ROOT / "examples"
G09 = EXAMPLES / "tcl-g09-google-tv" / "debloat.txt"
MIRAKULO = "com.mirakulo.astrotv.service"
REMOTE_SERVICE = "com.google.android.tv.remote.service"


def code(line):
    """What disable.sh acts on: everything before the first '#'."""
    return line.split("#", 1)[0].strip()


def trailing(line):
    return line.split("#", 1)[1].strip() if "#" in line else ""


def package_line(path, package):
    """The one active line for a package, or None when it is absent or commented out."""
    for line in path.read_text(encoding="utf-8").split("\n"):
        if code(line) == package:
            return line
    return None


def note_above(path, package):
    """The run of comment lines written directly above a package line, flattened."""
    lines = path.read_text(encoding="utf-8").split("\n")
    for n, line in enumerate(lines):
        if code(line) != package:
            continue
        block = []
        i = n - 1
        while i >= 0 and lines[i].lstrip().startswith("#"):
            block.insert(0, lines[i].lstrip("#").strip())
            i -= 1
        return " ".join(block)
    return None


def no_analytics_claim(text):
    """"not analytics" is the correction, not a claim; anything else called analytics is wrong."""
    return "analytics" not in text.lower().replace("not analytics", "")


def mirakulo_label_ok(text):
    """A correct label names Ginga and does not call the package analytics."""
    return "ginga" in text.lower() and no_analytics_claim(text)


def remote_service_label_ok(text):
    """A correct label carries both duties: phone remote apps and the remote's microphone."""
    text = text.lower()
    return "phone" in text and "mic" in text


class G09LabelTest(unittest.TestCase):
    def label(self, package):
        line = package_line(G09, package)
        self.assertIsNotNone(line, f"{package} is no longer an active line in the G09 list")
        return trailing(line or "")

    def note(self, package):
        text = note_above(G09, package)
        self.assertIsNotNone(text, f"no comment block above {package}")
        return text or ""

    def test_mirakulo_is_not_labelled_analytics(self):
        label = self.label(MIRAKULO)
        self.assertTrue(mirakulo_label_ok(label),
                        f"the label still reads as analytics or lost Ginga: {label!r}")

    def test_mirakulo_note_carries_the_evidence_and_the_area_config(self):
        # Only identifiers and keys, never a sentence: the note can be reworded, but an edit that loses
        # the manifest evidence or the Enable_ginga reason has to fail.
        note = self.note(MIRAKULO)
        for fact in ("ISDB-Tb", "AstroTVService", "com.tcl.tv.ginga.PERMISSION", "Enable_ginga"):
            with self.subTest(fact=fact):
                self.assertIn(fact, note)

    def test_remote_service_label_names_both_duties_and_the_way_back(self):
        label = self.label(REMOTE_SERVICE)
        self.assertTrue(remote_service_label_ok(label),
                        f"the label only carries one of the two duties: {label!r}")
        self.assertIn("pm enable", label, "disabling it has to say how to undo it")

    def test_remote_service_note_gives_the_pairing_ports_and_the_key_limit(self):
        note = self.note(REMOTE_SERVICE)
        for fact in ("_androidtvremote2._tcp", "6466", "6467", "virtual-remote.kl", "KEYCODE_UNKNOWN"):
            with self.subTest(fact=fact):
                self.assertIn(fact, note,
                              "a comment-only edit must not turn the phone's limits into a promise that "
                              "phone remotes work for every button")

    def test_remote_service_note_points_at_the_mic_half(self):
        self.assertIn("docs/TROUBLESHOOTING.md", self.note(REMOTE_SERVICE),
                      "the mic half is documented there; the list should not restate it and drift apart")

    def test_both_packages_are_still_disabled(self):
        # The whole change is comments; a later edit must not swallow the lines they describe.
        for package in (MIRAKULO, REMOTE_SERVICE):
            with self.subTest(package=package):
                self.assertIsNotNone(package_line(G09, package))


class CheckerTest(unittest.TestCase):
    """The checks above can only mean something if they fail on the text they replaced."""

    def test_mirakulo_label_rejects_the_old_text(self):
        self.assertFalse(mirakulo_label_ok("analytics service (data app; on the AS23 unit)"))
        self.assertTrue(mirakulo_label_ok("Ginga (ISDB-Tb broadcast apps), not analytics"))
        # The correction must not itself read as the claim: "not analytics" is allowed, a plain
        # "analytics" is not, and a label that drops Ginga still fails.
        self.assertTrue(no_analytics_claim("Ginga (ISDB-Tb broadcast apps), not analytics"))
        self.assertFalse(no_analytics_claim("analytics service"))
        self.assertFalse(mirakulo_label_ok("Unknown service (safe to disable)"))

    def test_remote_service_label_rejects_the_old_text(self):
        self.assertFalse(remote_service_label_ok("Google TV phone-app remote"))

    def test_package_line_ignores_a_commented_out_package(self):
        afmu0 = EXAMPLES / "xiaomi-mitv-afmu0" / "debloat.txt"
        self.assertIn("# com.google.android.tv.remote.service",
                      afmu0.read_text(encoding="utf-8"),
                      "this test needs a list where the package is commented out")
        self.assertIsNone(package_line(afmu0, REMOTE_SERVICE))

    def test_no_list_labels_mirakulo_as_analytics(self):
        scanned = 0
        for path in sorted(EXAMPLES.glob("*/debloat.txt")):
            for line in path.read_text(encoding="utf-8").split("\n"):
                if code(line) != MIRAKULO:
                    continue
                scanned += 1
                with self.subTest(list=path.parent.name):
                    self.assertTrue(no_analytics_claim(trailing(line)),
                                    f"{path.parent.name} still sells Ginga as analytics: {trailing(line)!r}")
        self.assertGreaterEqual(scanned, 2, "the scan found no mirakulo lines, so it proved nothing")


if __name__ == "__main__":
    unittest.main()
