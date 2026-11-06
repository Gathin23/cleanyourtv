"""tools/privacy.py --write looks at the file it was given before it asks the TV anything.

The check that the file is new ran only after connect() and the whole audit, which is up to 25
`dumpsys package` calls, slow on old firmware, and as many `appops` calls. Then --write refused a
file that was there all along, and a name in a folder that does not exist ended in a
FileNotFoundError traceback. Both are now refused first, saying that nothing was asked of the TV.
The check when writing stays, for a file that appears during the audit.

With --include-low, the list also said nothing about the name matches the audit's probe cap left
out, though the report counts them, so it looked complete. The terminal and the list header now say
how many were left out.

tools/tv.py is the fake from test_privacy_audit.py, so the real audit runs and only ADB is simulated.
An unreachable fake TV is how a test sees that the TV was not asked: connecting to it fails.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_privacy_audit import FAKE_TV, OWN_DUMP  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import privacy  # noqa: E402
import recovery  # noqa: E402

# The fake TV, plus one thing a real run can meet: the file appears while the audit runs.
APPEARING_TV = FAKE_TV.replace("def main():", '''_connect = connect


def connect():
    if state.get("connect_log"):
        with open(state["connect_log"], "a") as f:
            f.write("connect\\n")
    device = _connect()
    if state.get("appears"):
        with open(state["appears"], "w") as f:
            f.write("# written during the audit\\n")
    return device


def main():''', 1)
KATNISS = "com.google.android.katniss"
MATCHES = [f"com.example.voice{i:02d}" for i in range(30)]
SENTINEL = b"# existing hand edits\ncom.example.keep\n"

# Exercise the CLI with entries inserted at the actual creation/publication operation, after both
# preflight checks. The audit still runs against the same fake TV as the ordinary subprocess tests.
WRITE_HOOK = r'''
import builtins
import errno
import importlib.util
import os
from pathlib import Path
import sys
import tempfile

script = sys.argv.pop(1)
sys.argv[0] = script
sys.path.insert(0, str(Path(script).parent))
spec = importlib.util.spec_from_file_location("privacy_probe", script)
privacy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(privacy)
target = Path(sys.argv[2]).absolute()
action = os.environ["PRIVACY_WRITE_HOOK"]
checks = []
original_check = privacy.target_problem

def checked(path):
    problem = original_check(path)
    checks.append(problem)
    return problem

def before_publication():
    assert checks == [None, None], checks
    if action == "file":
        target.write_bytes(bytes.fromhex(os.environ["PRIVACY_SENTINEL"]))
    elif action in ("symlink", "dangling"):
        target.symlink_to(target.parent / "referent.txt")
    elif action == "deny":
        raise PermissionError(errno.EACCES, os.strerror(errno.EACCES))

class FailedWriter:
    def __init__(self, stream):
        self.stream = stream
        self.name = stream.name
    def __enter__(self):
        return self
    def write(self, text):
        if action == "write_error":
            self.stream.write(text[:12])
            self.stream.flush()
            raise OSError(errno.ENOSPC, os.strerror(errno.ENOSPC))
        return self.stream.write(text)
    def __exit__(self, *exc):
        try:
            if action == "close_error" and exc[0] is None:
                self.stream.flush()
                raise OSError(errno.ENOSPC, os.strerror(errno.ENOSPC))
        finally:
            self.stream.close()

original_open = builtins.open
def opening(path, mode="r", *args, **kwargs):
    if os.path.abspath(path) == str(target) and mode in ("w", "x"):
        before_publication()
        if action == "creation_error":
            raise FileNotFoundError(errno.ENOENT, os.strerror(errno.ENOENT))
        stream = original_open(path, mode, *args, **kwargs)
        return FailedWriter(stream) if action in ("write_error", "close_error") else stream
    return original_open(path, mode, *args, **kwargs)

original_rename = os.rename
def rename(source, destination, *args, **kwargs):
    if os.path.abspath(destination) == str(target):
        before_publication()
    return original_rename(source, destination, *args, **kwargs)

original_temporary_file = tempfile.NamedTemporaryFile
def temporary_file(*args, **kwargs):
    assert checks == [None, None], checks
    if action == "creation_error":
        raise FileNotFoundError(errno.ENOENT, os.strerror(errno.ENOENT))
    stream = original_temporary_file(*args, **kwargs)
    return FailedWriter(stream) if action in ("write_error", "close_error") else stream

privacy.target_problem = checked
privacy.open = opening
os.rename = rename
tempfile.NamedTemporaryFile = temporary_file
sys.exit(privacy.main())
'''


class PrivacyWriteTargetTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        tools = self.repo / "tools"
        tools.mkdir(parents=True)
        shutil.copy2(ROOT / "tools" / "privacy.py", tools / "privacy.py")
        (tools / "tv.py").write_text(APPEARING_TV)
        self.script = tools / "privacy.py"
        self.state_path = self.base / "tv-state.json"
        self.tv()

    def tv(self, installed=(KATNISS, "com.example.plain"), **overrides):
        state = {
            "installed": list(installed),
            "disabled": [],
            "voice": {"voice_interaction_service":
                      "com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService"},
            "dumps": {KATNISS: OWN_DUMP},
            "appops": {},
        }
        state.update(overrides)
        self.state_path.write_text(json.dumps(state))

    def write(self, target, *args, hook=None):
        env = dict(os.environ, TV_HOST="192.168.1.50", FAKE_TV_STATE=str(self.state_path))
        command = [sys.executable, str(self.script), "--write", str(target), *args]
        if hook:
            env.update(PRIVACY_WRITE_HOOK=hook, PRIVACY_SENTINEL=SENTINEL.hex())
            command = [sys.executable, "-c", WRITE_HOOK, *command[1:]]
        return subprocess.run(command,
                              cwd=self.repo, env=env, capture_output=True, text=True,
                              stdin=subprocess.DEVNULL, timeout=120)

    def symlink(self, link, target):
        try:
            link.symlink_to(target)
        except (OSError, NotImplementedError) as error:
            self.skipTest(f"cannot make a symlink here: {error}")

    def assert_recoverable_failure(self, result):
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("could not be written", result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertNotIn("wrote", result.stdout)
        self.assertIn("Privacy audit written by tools/privacy.py", result.stdout)
        saved = self.base / "saved.txt"
        saved.write_text(result.stdout)
        self.assertEqual(recovery.read_list(saved), [KATNISS])

    # The file, before the TV

    def test_an_existing_file_is_refused_before_the_tv_is_asked(self):
        target = self.repo / "privacy.txt"
        target.write_text("# hand edits\n")
        self.tv(unreachable=True)
        result = self.write("privacy.txt")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("privacy.txt already exists; move it aside or choose another name.",
                      result.stderr)
        self.assertIn("Nothing was asked of the TV.", result.stderr)
        self.assertNotIn("cannot reach the TV", result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertEqual(target.read_text(), "# hand edits\n")

    def test_a_missing_folder_is_refused_before_the_tv_is_asked(self):
        self.tv(unreachable=True)
        result = self.write("no-such-folder/privacy.txt")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertIn("there is no folder no-such-folder", result.stderr)
        self.assertIn("Nothing was asked of the TV.", result.stderr)
        self.assertNotIn("cannot reach the TV", result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertFalse((self.repo / "no-such-folder").exists())

    def test_a_folder_that_is_a_file_is_refused_too(self):
        (self.repo / "lists").write_text("not a folder\n")
        self.tv(unreachable=True)
        result = self.write("lists/privacy.txt")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertIn("there is no folder lists", result.stderr)
        self.assertNotIn("cannot reach the TV", result.stderr)
        self.assertEqual((self.repo / "lists").read_text(), "not a folder\n")

    def test_a_new_file_in_a_folder_that_exists_is_still_written(self):
        (self.repo / "lists").mkdir()
        result = self.write("lists/privacy.txt")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(recovery.read_list(self.repo / "lists" / "privacy.txt"), [KATNISS])

    def test_a_file_that_appears_during_the_audit_is_not_replaced(self):
        target = self.repo / "privacy.txt"
        self.tv(appears=str(target))
        result = self.write("privacy.txt")
        self.assertEqual(result.returncode, 1)
        self.assertIn("privacy.txt already exists", result.stderr)
        # The TV was asked this time, so the refusal must not say it was not.
        self.assertNotIn("Nothing was asked of the TV", result.stderr)
        self.assertEqual(target.read_text(), "# written during the audit\n")

    def test_symlinks_are_refused_before_the_tv_is_asked(self):
        for kind in ("existing", "dangling", "missing_parent"):
            with self.subTest(kind=kind):
                folder = self.repo / kind
                if kind != "missing_parent":
                    folder.mkdir()
                referent = folder / "referent.txt"
                if kind == "existing":
                    referent.write_bytes(SENTINEL)
                target = self.repo / f"{kind}.txt"
                self.symlink(target, referent)
                link_value = os.readlink(target)
                marker = self.base / f"{kind}-connect.txt"
                self.tv(connect_log=str(marker))
                result = self.write(target)
                self.assertEqual(result.returncode, 2,
                                 f"referent exists: {referent.exists()}\n"
                                 + result.stdout + result.stderr)
                self.assertIn("already exists", result.stderr)
                self.assertIn("Nothing was asked of the TV", result.stderr)
                self.assertEqual(result.stdout, "")
                self.assertFalse(marker.exists())
                self.assertTrue(target.is_symlink())
                self.assertEqual(os.readlink(target), link_value)
                if kind == "existing":
                    self.assertEqual(referent.read_bytes(), SENTINEL)
                else:
                    self.assertFalse(referent.exists())

    def test_entries_created_at_publication_are_not_replaced(self):
        for kind in ("file", "symlink", "dangling"):
            with self.subTest(kind=kind):
                folder = self.repo / kind
                folder.mkdir()
                target = folder / "privacy.txt"
                referent = folder / "referent.txt"
                if kind != "file":
                    probe = folder / "probe.txt"
                    self.symlink(probe, referent)
                    link_value = os.readlink(probe)
                    probe.unlink()
                if kind == "symlink":
                    referent.write_bytes(SENTINEL)
                unrelated = folder / "unrelated.txt"
                unrelated.write_bytes(SENTINEL)
                result = self.write(target, hook=kind)
                expected = hashlib.sha256(SENTINEL).hexdigest()
                actual = hashlib.sha256(target.read_bytes()).hexdigest() if target.is_file() else None
                self.assertEqual(result.returncode, 1,
                                 f"sentinel SHA-256: {expected}; after: {actual}\n"
                                 + result.stdout + result.stderr)
                self.assert_recoverable_failure(result)
                if kind == "file":
                    self.assertEqual(target.read_bytes(), SENTINEL)
                    self.assertEqual(actual, expected)
                else:
                    self.assertTrue(target.is_symlink())
                    self.assertEqual(os.readlink(target), link_value)
                    if kind == "symlink":
                        self.assertEqual(referent.read_bytes(), SENTINEL)
                        self.assertEqual(actual, expected)
                    else:
                        self.assertFalse(referent.exists())
                self.assertEqual(unrelated.read_bytes(), SENTINEL)
                self.assertEqual(list(folder.glob(".privacy-*")), [])

    def test_failed_publication_keeps_the_audit_and_cleans_staging(self):
        target = self.repo / "privacy.txt"
        unrelated = self.repo / "unrelated.txt"
        unrelated.write_bytes(SENTINEL)
        result = self.write(target, hook="deny")
        self.assert_recoverable_failure(result)
        self.assertIn(os.strerror(13), result.stderr)
        self.assertFalse(target.exists())
        self.assertEqual(unrelated.read_bytes(), SENTINEL)
        self.assertEqual(list(self.repo.glob(".privacy-*")), [])

    @unittest.skipUnless(os.name == "nt", "Windows stages the output before publication")
    def test_windows_write_and_close_failures_leave_no_staging_files(self):
        for action in ("write_error", "close_error"):
            with self.subTest(action=action):
                target = self.repo / f"{action}.txt"
                result = self.write(target, hook=action)
                self.assert_recoverable_failure(result)
                self.assertFalse(target.exists())
                self.assertEqual(list(self.repo.glob(".privacy-*")), [])

    def test_unicode_paths_keep_output_bytes_and_creation_permissions(self):
        name = "audit-客廳-Trịnh.txt"
        normal = self.repo / name
        result = self.write(normal)
        self.assertEqual(result.returncode, 0, result.stderr)
        folder = self.repo / "客廳 Trịnh"
        folder.mkdir()
        target = folder / name
        result = self.write(target)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stderr, "")
        self.assertIn(f"wrote 1 packages to {target}", result.stdout)
        self.assertIn(f"tools/disable.sh {target}", result.stdout)
        self.assertIn(f"tools/restore.sh {target}", result.stdout)
        self.assertEqual(target.read_bytes(), normal.read_bytes())
        self.assertEqual(recovery.read_list(target), [KATNISS])
        ordinary = folder / "ordinary.txt"
        with open(ordinary, "w") as stream:
            stream.write("ordinary creation\n")
        self.assertEqual(stat.S_IMODE(target.stat().st_mode), stat.S_IMODE(ordinary.stat().st_mode))
        self.assertEqual(list(folder.glob(".privacy-*")), [])

    @unittest.skipUnless(os.name == "nt" and shutil.which("powershell"), "needs Windows ACLs")
    def test_windows_creation_inherits_the_destination_acl(self):
        prepare = r'''
$ErrorActionPreference='Stop'
$folder=[Console]::In.ReadToEnd() | ConvertFrom-Json
$acl=Get-Acl -LiteralPath $folder
$users=New-Object System.Security.Principal.SecurityIdentifier('S-1-5-32-545')
$rule=New-Object System.Security.AccessControl.FileSystemAccessRule($users,'ReadAndExecute','ContainerInherit,ObjectInherit','None','Allow')
$acl.AddAccessRule($rule)
Set-Acl -LiteralPath $folder -AclObject $acl
'''
        command = ["powershell", "-NoProfile", "-NonInteractive", "-Command"]
        prepared = subprocess.run(command + [prepare], input=json.dumps(str(self.repo)),
                                  capture_output=True, text=True, timeout=30)
        self.assertEqual(prepared.returncode, 0, prepared.stderr)
        ordinary = self.repo / "ordinary.txt"
        with open(ordinary, "w") as stream:
            stream.write("ordinary creation\n")
        target = self.repo / "privacy.txt"
        result = self.write(target)
        self.assertEqual(result.returncode, 0, result.stderr)
        inspect = r'''
$ErrorActionPreference='Stop'
$paths=[Console]::In.ReadToEnd() | ConvertFrom-Json
$normal=Get-Acl -LiteralPath $paths[0]
$written=Get-Acl -LiteralPath $paths[1]
$access=[System.Security.AccessControl.AccessControlSections]::Access
$users=@($written.GetAccessRules($true,$true,[System.Security.Principal.SecurityIdentifier]) | Where-Object {$_.IdentityReference.Value -eq 'S-1-5-32-545' -and $_.AccessControlType -eq 'Allow' -and $_.IsInherited -and (($_.FileSystemRights -band [System.Security.AccessControl.FileSystemRights]::ReadData) -ne 0)})
[pscustomobject]@{
    same_rules=($normal.GetSecurityDescriptorSddlForm($access) -ceq $written.GetSecurityDescriptorSddlForm($access))
    same_owner=($normal.Owner -ceq $written.Owner)
    normal_protected=$normal.AreAccessRulesProtected
    written_protected=$written.AreAccessRulesProtected
    inherited_users_read=($users.Count -gt 0)
} | ConvertTo-Json -Compress
'''
        inspected = subprocess.run(command + [inspect], input=json.dumps([str(ordinary), str(target)]),
                                   capture_output=True, text=True, timeout=30)
        self.assertEqual(inspected.returncode, 0, inspected.stderr)
        self.assertEqual(json.loads(inspected.stdout), {
            "same_rules": True, "same_owner": True, "normal_protected": False,
            "written_protected": False, "inherited_users_read": True,
        })

    # What the probe cap left out

    def test_include_low_says_how_many_name_matches_were_not_probed(self):
        self.tv(installed=[KATNISS] + MATCHES)
        target = self.repo / "low.txt"
        result = self.write("low.txt", "--include-low")
        self.assertEqual(result.returncode, 0, result.stderr)
        # katniss and 24 name matches fill the 25 probes; 6 of the 30 matches are left.
        self.assertEqual(len(recovery.read_list(target)), privacy.MAX_PROBES)
        self.assertIn("wrote 25 packages to low.txt", result.stdout)
        self.assertIn("6 more name matches were not probed (the audit stops at 25)", result.stdout)
        self.assertIn("`dumpsys package <name>`", result.stdout)
        text = target.read_text()
        self.assertIn("# 6 more name matches were not probed (the audit stops at 25)", text)
        header = text.split("\n\n", 1)[0]
        self.assertIn("dumpsys package <name>", header)
        self.assertNotIn("192.168.1.50", text)

    def test_the_count_matches_the_report(self):
        self.tv(installed=[KATNISS] + MATCHES)
        env = dict(os.environ, TV_HOST="192.168.1.50", FAKE_TV_STATE=str(self.state_path))
        report = subprocess.run([sys.executable, str(self.script)], cwd=self.repo, env=env,
                                capture_output=True, text=True, stdin=subprocess.DEVNULL,
                                timeout=120).stdout
        self.assertIn("6 more name matches were not probed", report)
        result = self.write("low.txt", "--include-low")
        self.assertIn("6 more name matches were not probed", result.stdout)

    def test_without_include_low_the_list_has_no_name_matches_to_count(self):
        self.tv(installed=[KATNISS] + MATCHES)
        target = self.repo / "privacy.txt"
        result = self.write("privacy.txt")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(recovery.read_list(target), [KATNISS])
        self.assertNotIn("not probed", result.stdout)
        self.assertNotIn("not probed", target.read_text())

    def test_nothing_is_said_when_every_match_was_probed(self):
        self.tv(installed=[KATNISS] + MATCHES[:5])
        target = self.repo / "low.txt"
        result = self.write("low.txt", "--include-low")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len(recovery.read_list(target)), 6)
        self.assertNotIn("not probed", result.stdout)
        self.assertNotIn("not probed", target.read_text())


if __name__ == "__main__":
    unittest.main()
