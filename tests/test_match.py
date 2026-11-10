"""tools/match.py reads a saved `pm list packages` and counts how the tested lists treat each package.

It reads files only: a package file and a folder of */debloat*.txt lists. For each package it prints how
many lists disable it, how many name it commented out, and the note those lines give it most often; a
package disable.sh refuses as one of Android's core packages (#884) is printed as `never`. A packages
file or examples folder that cannot be used ends the run at 2 with one line, no traceback.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import codecs
import errno
import io
import os
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import match  # noqa: E402

MATCH = ROOT / "tools" / "match.py"


class MatchCase(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.examples = self.base / "examples"
        self.examples.mkdir()

    def write(self, name, text, encoding="utf-8"):
        path = self.base / name
        path.write_text(text, encoding=encoding)
        return path

    def add_list(self, folder, text):
        path = self.examples / folder / "debloat.txt"
        path.parent.mkdir(parents=True)
        path.write_text(text, encoding="utf-8")
        return path


class ReadPackagesTest(MatchCase):
    def test_package_lines_are_read_sorted_without_repeats(self):
        path = self.write("p.txt", "package:com.b.two\npackage:com.a.one\npackage:com.b.two\n")
        self.assertEqual(["com.a.one", "com.b.two"], match.read_packages(path))

    def test_the_f_form_takes_the_name_after_the_last_equals(self):
        path = self.write("p.txt", "package:/system/app/Foo/base=1.apk=com.example.foo\n"
                                   "package:/data/app/x/base.apk=com.example.bar\n")
        self.assertEqual(["com.example.bar", "com.example.foo"], match.read_packages(path))

    def test_bare_names_and_crlf_and_bom_are_taken(self):
        path = self.base / "p.txt"
        path.write_bytes(b"\xef\xbb\xbfcom.example.first\r\n  com.example.second  \r\n")
        self.assertEqual(["com.example.first", "com.example.second"], match.read_packages(path))

    def test_utf16_package_files_with_a_bom_are_read(self):
        text = ("package:com.example.second\r\n"
                "package:/system/app/Foo/base.apk=com.example.first\r\n"
                "com.example.second\r\n")
        for encoding, bom in (("utf-16-le", codecs.BOM_UTF16_LE),
                              ("utf-16-be", codecs.BOM_UTF16_BE)):
            with self.subTest(encoding=encoding):
                path = self.base / "p.txt"
                path.write_bytes(bom + text.encode(encoding))
                self.assertEqual(["com.example.first", "com.example.second"], match.read_packages(path))

    def test_lines_that_are_not_package_names_are_skipped(self):
        path = self.write("p.txt", "error: device offline\n\npackage:com.example.ok\nnot_a_package\n"
                                   "rm -rf /\npackage:bad;name.x\n")
        self.assertEqual(["com.example.ok"], match.read_packages(path))

    def test_a_file_with_no_names_stops(self):
        path = self.write("p.txt", "error: no devices/emulators found\n")
        with self.assertRaises(SystemExit) as caught:
            match.read_packages(path)
        self.assertIn("no package names", str(caught.exception.code))

    def test_a_missing_file_stops_naming_the_path(self):
        path = self.base / "gone.txt"
        with self.assertRaises(SystemExit) as caught:
            match.read_packages(path)
        self.assertIn(str(path), str(caught.exception.code))
        self.assertIn("could not read", str(caught.exception.code))


class ReadListTest(unittest.TestCase):
    def test_disabled_lines_carry_their_note(self):
        disabled, commented = match.read_list("com.example.ads  # ads\ncom.example.music\n")
        self.assertEqual({"com.example.ads": "ads", "com.example.music": ""}, disabled)
        self.assertEqual({}, commented)

    def test_a_commented_out_package_counts_as_kept(self):
        disabled, commented = match.read_list("# com.example.store  # needed for updates\n#com.example.x\n")
        self.assertEqual({}, disabled)
        self.assertEqual({"com.example.store": "needed for updates", "com.example.x": ""}, commented)

    def test_a_header_sentence_starting_with_a_package_name_is_not_counted(self):
        disabled, commented = match.read_list("# com.example.store is kept because it updates apps\n")
        self.assertEqual(({}, {}), (disabled, commented))

    def test_plain_comments_and_blank_lines_are_ignored(self):
        self.assertEqual(({}, {}), match.read_list("# My TV\n\n#\n# tested on firmware 1.2\n"))

    def test_a_package_both_disabled_and_commented_counts_as_disabled(self):
        disabled, commented = match.read_list("# com.example.ads  # maybe\ncom.example.ads  # off\n")
        self.assertEqual({"com.example.ads": "off"}, disabled)
        self.assertEqual({}, commented)

    def test_the_first_note_for_a_repeated_name_is_kept(self):
        disabled, _ = match.read_list("com.example.ads  # first\ncom.example.ads  # second\n")
        self.assertEqual({"com.example.ads": "first"}, disabled)


class TallyTest(MatchCase):
    def test_counts_over_every_list(self):
        self.add_list("a", "com.example.ads  # ads\n# com.example.store\n")
        self.add_list("b", "com.example.ads  # ads\ncom.example.store  # store\n")
        self.add_list("c", "com.example.ads  # telemetry\n# com.example.store  # updates\n")
        counts, read = match.tally(["com.example.ads", "com.example.store", "com.example.other"],
                                   self.examples)
        self.assertEqual(3, read)
        self.assertEqual((3, 0, "ads"), counts["com.example.ads"])
        self.assertEqual(1, counts["com.example.store"][0])
        self.assertEqual(2, counts["com.example.store"][1])
        self.assertEqual((0, 0, ""), counts["com.example.other"])

    def test_packages_the_tv_lacks_are_not_reported(self):
        self.add_list("a", "com.example.ads\ncom.example.elsewhere\n")
        counts, _ = match.tally(["com.example.ads"], self.examples)
        self.assertEqual(["com.example.ads"], list(counts))

    def test_every_list_beside_debloat_txt_is_read(self):
        # A folder may ship a second list beside debloat.txt - google-chromecast-gtv-4k/debloat-keep-cast.txt,
        # sony-bravia-4k-ur3/debloat-display-only.txt - and tools/disable.sh applies one like any other, so
        # the tally reads it too. One folder down only, as before: a bare debloat.txt at the top of
        # examples/, or a list inside a subfolder, is not one of examples/'s lists.
        self.add_list("a", "com.example.ads\n")
        (self.examples / "a" / "debloat-extra.txt").write_text("com.example.ads\ncom.example.extra\n")
        (self.examples / "debloat.txt").write_text("com.example.ads\n")
        (self.examples / "a" / "nested" / "debloat.txt").parent.mkdir()
        (self.examples / "a" / "nested" / "debloat.txt").write_text("com.example.ads\n")
        counts, read = match.tally(["com.example.ads", "com.example.extra"], self.examples)
        self.assertEqual(2, read)
        self.assertEqual((2, 0, ""), counts["com.example.ads"])
        self.assertEqual((1, 0, ""), counts["com.example.extra"])

    def test_a_package_only_a_second_list_disables_is_counted(self):
        # The shape examples/oppo-k9-55-oppocnm632/debloat-extra.txt has: com.changba.sd is on that list
        # alone, and the tally printed 0 for it while the tool read debloat.txt only.
        self.add_list("a", "com.example.ads\n")
        (self.examples / "a" / "debloat-extra.txt").write_text("com.changba.sd  # karaoke\n")
        counts, read = match.tally(["com.changba.sd"], self.examples)
        self.assertEqual(2, read)
        self.assertEqual((1, 0, "karaoke"), counts["com.changba.sd"])

    def test_a_second_list_counts_as_its_own_list(self):
        # The counts are per list, as the closing line says, so a package both of a folder's lists carry
        # reads as two lists rather than one folder.
        self.add_list("a", "com.example.ads\n")
        (self.examples / "a" / "debloat-keep-two.txt").write_text("com.example.ads\n")
        counts, read = match.tally(["com.example.ads"], self.examples)
        self.assertEqual(2, read)
        self.assertEqual((2, 0, ""), counts["com.example.ads"])

    def test_a_folder_without_lists_stops(self):
        with self.assertRaises(SystemExit) as caught:
            match.tally(["com.example.ads"], self.examples)
        self.assertIn("no */debloat*.txt", str(caught.exception.code))

    def test_a_list_that_is_a_directory_stops_the_whole_tally(self):
        self.add_list("a", "com.example.ads\n")
        bad = self.examples / "b" / "debloat.txt"
        bad.mkdir(parents=True)
        with self.assertRaises(SystemExit) as caught:
            match.tally(["com.example.ads"], self.examples)
        self.assertIn("could not read", str(caught.exception.code))
        self.assertIn(str(bad), str(caught.exception.code))

    def test_a_list_that_cannot_be_read_stops_the_whole_tally(self):
        self.add_list("a", "com.example.ads\n")
        bad = self.add_list("b", "com.example.ads\n")
        real_read_text = Path.read_text

        def read_text(path, *args, **kwargs):
            if path == bad:
                raise PermissionError(13, "Permission denied", str(path))
            return real_read_text(path, *args, **kwargs)

        with mock.patch.object(Path, "read_text", read_text):
            with self.assertRaises(SystemExit) as caught:
                match.tally(["com.example.ads"], self.examples)
        self.assertIn(str(bad), str(caught.exception.code))
        self.assertIn("Permission denied", str(caught.exception.code))


class MainTest(MatchCase):
    def run_main(self, *args):
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            code = match.main(list(args))
        return code, out.getvalue(), err.getvalue()

    def test_the_table_lists_every_package_with_its_counts(self):
        self.add_list("a", "com.example.ads  # ads\n# com.example.store  # updates\n")
        self.add_list("b", "com.example.ads  # ads\n")
        packages = self.write("p.txt", "package:com.example.store\npackage:com.example.ads\n"
                                       "package:com.example.none\n")
        code, out, err = self.run_main(str(packages), "--examples", str(self.examples))
        self.assertEqual(0, code, err)
        lines = out.splitlines()
        self.assertEqual(["package", "disabled", "commented", "note"], lines[0].split())
        rows = {line.split()[0]: line.split() for line in lines[1:]}
        self.assertEqual(["com.example.ads", "2", "0", "ads"], rows["com.example.ads"])
        self.assertEqual(["com.example.none", "0", "0"], rows["com.example.none"])
        self.assertEqual(["com.example.store", "0", "1", "updates"], rows["com.example.store"])
        self.assertEqual(["com.example.ads", "com.example.none", "com.example.store"],
                         [line.split()[0] for line in lines[1:]])
        self.assertTrue(all(line == line.rstrip() for line in lines))
        self.assertIn("3 packages, counted over 2 lists", err)

    def test_core_packages_show_as_never_whatever_a_list_says(self):
        self.add_list("a", "com.android.packageinstaller  # bad idea\ncom.example.ads\n")
        packages = self.write("p.txt", "package:com.android.packageinstaller\npackage:com.example.ads\n"
                                       "package:com.android.providers.calendar\n")
        code, out, err = self.run_main(str(packages), "--examples", str(self.examples))
        self.assertEqual(0, code, err)
        rows = {line.split()[0]: line.split()[1:] for line in out.splitlines()[1:]}
        self.assertEqual(["never"], rows["com.android.packageinstaller"])
        self.assertEqual(["1", "0"], rows["com.example.ads"])
        self.assertEqual(["0", "0"], rows["com.android.providers.calendar"])

    def test_columns_line_up_with_the_longest_name(self):
        self.add_list("a", "com.example.a\n")
        packages = self.write("p.txt", "package:com.example.a\npackage:com.example.much.longer.name\n")
        code, out, _ = self.run_main(str(packages), "--examples", str(self.examples))
        self.assertEqual(0, code)
        width = len("com.example.much.longer.name")
        for line in out.splitlines():
            self.assertEqual("  ", line[width:width + 2], line)
        self.assertTrue(out.splitlines()[-1].startswith("com.example.much.longer.name  "))

    def test_a_missing_packages_file_is_2(self):
        self.add_list("a", "com.example.ads\n")
        code, out, err = self.run_main(str(self.base / "gone.txt"), "--examples", str(self.examples))
        self.assertEqual(2, code)
        self.assertEqual("", out)
        self.assertIn("could not read", err)

    def test_an_examples_folder_without_lists_is_2(self):
        packages = self.write("p.txt", "package:com.example.ads\n")
        code, out, err = self.run_main(str(packages), "--examples", str(self.examples))
        self.assertEqual(2, code)
        self.assertEqual("", out)
        self.assertIn("no */debloat*.txt", err)

    def test_an_unreadable_directory_refuses_partial_counts(self):
        self.add_list("a", "com.example.common\n")
        hidden = self.add_list("b", "com.example.hidden\n").parent
        packages = self.write("p.txt", "package:com.example.common\npackage:com.example.hidden\n")
        real_scandir = os.scandir
        for denied in (self.examples, hidden):
            with self.subTest(directory=denied.name):
                def scandir(path):
                    if Path(path) == denied:
                        raise PermissionError(errno.EACCES, "Permission denied", str(denied))
                    return real_scandir(path)

                with mock.patch.object(os, "scandir", side_effect=scandir):
                    code, out, err = self.run_main(str(packages), "--examples", str(self.examples))
                self.assertEqual(2, code, out + err)
                self.assertEqual("", out, "a partial tally must not be presented as complete")
                self.assertIn(str(denied), err)
                self.assertIn("Permission denied", err)
                self.assertNotIn("Traceback", err)

    def test_the_default_examples_folder_is_this_checkout(self):
        self.assertEqual(ROOT / "examples", match.EXAMPLES)


class CommandLineTest(MatchCase):
    def run_script(self, *args):
        return subprocess.run([sys.executable, "-I", str(MATCH), *args], capture_output=True,
                              text=True, stdin=subprocess.DEVNULL, timeout=60)

    def test_runs_isolated_and_exits_0(self):
        self.add_list("a", "com.example.ads  # ads\n")
        packages = self.write("p.txt", "package:com.example.ads\n")
        result = self.run_script(str(packages), "--examples", str(self.examples))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("com.example.ads", result.stdout)
        self.assertNotIn("Traceback", result.stderr)

    def test_package_encodings_produce_the_same_tally(self):
        self.add_list("a", "com.example.ads  # ads\n# com.example.store  # updates\n"
                           "com.android.packageinstaller\n")
        text = ("package:com.example.store\r\npackage:com.example.ads\r\n"
                "package:com.android.packageinstaller\r\npackage:com.example.ads\r\n")
        expected = None
        for encoding, bom in (("utf-8", b""), ("utf-8-sig", b""),
                              ("utf-16-le", codecs.BOM_UTF16_LE),
                              ("utf-16-be", codecs.BOM_UTF16_BE)):
            with self.subTest(encoding=encoding):
                packages = self.base / "p.txt"
                original = bom + text.encode(encoding)
                packages.write_bytes(original)
                result = self.run_script(str(packages), "--examples", str(self.examples))
                self.assertEqual(0, result.returncode, result.stderr)
                if expected is None:
                    expected = result.stdout
                self.assertEqual(expected, result.stdout)
                rows = {line.split()[0]: line.split()[1:] for line in result.stdout.splitlines()[1:]}
                self.assertEqual(["1", "0", "ads"], rows["com.example.ads"])
                self.assertEqual(["0", "1", "updates"], rows["com.example.store"])
                self.assertEqual(["never"], rows["com.android.packageinstaller"])
                self.assertIn("3 packages, counted over 1 lists", result.stderr)
                self.assertEqual(original, packages.read_bytes())

    def test_an_empty_examples_path_uses_the_current_directory(self):
        self.add_list("a", "com.example.ads  # ads\n# com.example.store  # updates\n")
        self.add_list("b", "com.example.ads  # ads\ncom.example.store  # updates\n")
        (self.examples / "a" / "debloat-extra.txt").write_text(
            "com.example.ads  # alternate\ncom.example.extra  # secondary\n", encoding="utf-8")
        packages = self.write("p.txt", "package:com.example.ads\npackage:com.example.store\n"
                                       "package:com.example.extra\npackage:com.example.none\n")
        previous = os.getcwd()
        try:
            os.chdir(self.examples)
            results = [self.run_script(str(packages), "--examples", root) for root in ("", ".")]
        finally:
            os.chdir(previous)
        for result in results:
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("4 packages, counted over 3 lists", result.stderr)
        self.assertEqual(results[0].stdout, results[1].stdout)
        rows = {line.split()[0]: line.split()[1:] for line in results[0].stdout.splitlines()[1:]}
        self.assertEqual({"com.example.ads": ["3", "0", "ads"],
                          "com.example.extra": ["1", "0", "secondary"],
                          "com.example.none": ["0", "0"],
                          "com.example.store": ["1", "1", "updates"]}, rows)

    def test_an_unreadable_file_exits_2_without_a_traceback(self):
        result = self.run_script(str(self.base / "gone.txt"), "--examples", str(self.examples))
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertEqual("", result.stdout)

    def test_a_list_that_is_a_directory_exits_2_without_a_traceback(self):
        self.add_list("a", "com.example.ads\n")
        (self.examples / "b" / "debloat.txt").mkdir(parents=True)
        packages = self.write("p.txt", "package:com.example.ads\n")
        result = self.run_script(str(packages), "--examples", str(self.examples))
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertIn("could not read", result.stderr)
        self.assertEqual("", result.stdout)

    def test_a_dangling_list_symlink_exits_2_without_a_traceback(self):
        folder = self.examples / "a"
        folder.mkdir()
        try:
            (folder / "debloat.txt").symlink_to(self.base / "gone.txt")
        except (OSError, NotImplementedError) as e:
            self.skipTest(f"cannot create a symlink here: {e}")
        packages = self.write("p.txt", "package:com.example.ads\n")
        result = self.run_script(str(packages), "--examples", str(self.examples))
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertEqual("", result.stdout)

    @unittest.skipUnless(os.name == "posix", "POSIX directory permissions are needed")
    def test_a_directory_without_read_permission_exits_2(self):
        if os.geteuid() == 0:
            self.skipTest("root can enumerate a directory without its read permission")
        self.add_list("a", "com.example.common\n")
        hidden = self.add_list("b", "com.example.hidden\n").parent
        packages = self.write("p.txt", "package:com.example.common\npackage:com.example.hidden\n")
        hidden.chmod(0)
        try:
            result = self.run_script(str(packages), "--examples", str(self.examples))
        finally:
            hidden.chmod(0o700)
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertEqual("", result.stdout)
        self.assertIn(str(hidden), result.stderr)
        self.assertNotIn("Traceback", result.stderr)

    def test_the_checkouts_own_examples_are_read_by_default(self):
        packages = self.write("p.txt", "package:com.example.unlikely.to.exist\n")
        result = self.run_script(str(packages))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn(str(ROOT / "examples"), result.stderr)

    def test_the_readme_lists_the_tool(self):
        self.assertIn("[`tools/match.py`](tools/match.py)", (ROOT / "README.md").read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
