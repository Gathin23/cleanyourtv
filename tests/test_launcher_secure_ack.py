"""The launcher installers accept the WRITE_SECURE_SETTINGS marker only as a line the TV sent back.

tools/tv.py does not hand back the TV shell's exit status, so build-and-install.sh and .ps1 decide by
the marker. `tv.py --batch` echoes each command as `$ <command>`, and the command itself contains
`echo <marker>`, so a check that finds the marker anywhere in the output passes on the echo alone, with
a grant that failed or a revoke the TV ignored. Both acknowledgement checks are run here, taken from
the scripts as written, against client output that holds the echo, a prefix, a suffix or CRLF.

Run from the repository root: python3 -m unittest discover -s tests -v
The PowerShell cases need `pwsh` on PATH and skip without it; CI's Ubuntu runner has it.
"""
import os
from pathlib import Path
import shutil
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[1]
SH = ROOT / "launcher" / "build-and-install.sh"
PS1 = ROOT / "launcher" / "build-and-install.ps1"
PWSH = shutil.which("pwsh")
REVOKE = "secure-settings-revoked"
GRANT = "restore-granted"

# (output from tv.py, marker, accepted)
CASES = [
    (f"{REVOKE}\n", REVOKE, True),
    (f"{REVOKE}\r\n", REVOKE, True),
    (f"Success\n{GRANT}\n", GRANT, True),
    (f"$ pm revoke x; s=$(dumpsys package x) && case \"$s\" in *) echo {REVOKE} ;; esac\n", REVOKE, False),
    (f"$ pm grant x && echo {GRANT}\nException occurred while executing 'grant'\n", GRANT, False),
    (f"not-{REVOKE}\n", REVOKE, False),
    (f"{REVOKE}-later\n", REVOKE, False),
    (f"{GRANT}\n", REVOKE, False),
    ("", GRANT, False),
]


def bash_check():
    """The lines of build-and-install.sh from ACK_FOUND=0 to the fi that closes the error branch."""
    lines = SH.read_text(encoding="utf-8").splitlines()
    start = lines.index("ACK_FOUND=0")
    end = next(i for i in range(start, len(lines)) if lines[i] == "fi")
    return "\n".join(lines[start:end + 1])


def ps_check():
    """The line of build-and-install.ps1 that throws when the marker is missing."""
    return next(line for line in PS1.read_text(encoding="utf-8").splitlines()
                if "$mark missing" in line and line.lstrip().startswith("if"))


class SecureAckTest(unittest.TestCase):
    def test_bash_accepts_only_a_whole_returned_line(self):
        if not shutil.which("bash"):
            raise unittest.SkipTest("needs bash")
        check = bash_check()
        for out, mark, accepted in CASES:
            with self.subTest(out=out, mark=mark):
                result = subprocess.run(
                    ["bash", "-c", f'OUT="$1"; MARK="$2"\n{check}\necho accepted', "ack", out, mark],
                    capture_output=True, text=True, timeout=30,
                )
                self.assertEqual(result.returncode == 0 and "accepted" in result.stdout, accepted,
                                 result.stderr)

    def test_powershell_accepts_only_a_whole_returned_line(self):
        if not PWSH:
            raise unittest.SkipTest("build-and-install.ps1 needs pwsh on PATH")
        check = ps_check()
        for out, mark, accepted in CASES:
            with self.subTest(out=out, mark=mark):
                script = ("$out = [Environment]::GetEnvironmentVariable('ACK_OUT'); "
                          "$mark = [Environment]::GetEnvironmentVariable('ACK_MARK')\n"
                          f"{check}\nWrite-Output accepted")
                env = dict(os.environ, ACK_OUT=out, ACK_MARK=mark)
                result = subprocess.run([PWSH, "-NoProfile", "-Command", script],
                                        capture_output=True, text=True, timeout=60, env=env)
                self.assertEqual(result.returncode == 0 and "accepted" in result.stdout, accepted,
                                 result.stderr)


if __name__ == "__main__":
    unittest.main()
