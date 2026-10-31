"""A simulated TV for the profile tests: tools/tv.py replaced by a script that reads JSON state.

One state file per host, so a test can hold a master and a target at the same time: the fake picks
the file from TV_HOST, which is exactly how tools/profile.py addresses the two units.
"""

import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
MASTER = "192.168.1.50"
TARGET = "192.168.1.60"

FAKE_TV = r'''
import json
import os
import sys

STATES = os.environ["FAKE_TV_STATES"]


def path(host):
    return os.path.join(STATES, host.replace(":", "_") + ".json")


def load():
    with open(path(os.environ["TV_HOST"])) as f:
        return json.load(f)


def save(state):
    with open(path(os.environ["TV_HOST"]), "w") as f:
        json.dump(state, f)


def answer(state, command):
    if command == "pm list packages":
        return "".join(f"package:{name}\n" for name in state["installed"])
    if command == "pm list packages -3":
        return "".join(f"package:{name}\n" for name in state["installed"] if name in state["third_party"])
    if command == "pm list packages -d":
        return "".join(f"package:{name}\n" for name in state["disabled"])
    if command.startswith("pm path "):
        return "".join(f"package:{p}\n" for p in state["paths"].get(command.split()[-1], []))
    if command.startswith("getprop "):
        return state.get("props", {}).get(command.split()[-1], "") + "\n"
    return ""


def apply(state, command):
    state["commands"].append(command)
    words = command.split()
    if command.startswith("pm install-create"):
        state["session"] = {"id": "7", "pkgs": []}
        return "Success: created install session [7]\n"
    if command.startswith("pm install-write "):
        pkg = state["apks"].get(words[-1])
        if pkg and pkg not in state["session"]["pkgs"]:
            state["session"]["pkgs"].append(pkg)
        return "Success: streamed\n"
    if command.startswith("pm install-commit "):
        if state.get("commit_fails"):
            return "Failure [INSTALL_FAILED_INVALID_APK]\n"
        for pkg in state["session"]["pkgs"]:
            if pkg not in state["installed"]:
                state["installed"].append(pkg)
                state["third_party"].append(pkg)
        state["session"] = {"id": "", "pkgs": []}
        return "Success\n"
    if command.startswith("pm install-abandon "):
        state["session"] = {"id": "", "pkgs": []}
        return "Success\n"
    if command.startswith("pm disable-user --user 0 "):
        name = command.split()[-1]
        if name not in state["disabled"]:
            state["disabled"].append(name)
    elif command.startswith("pm enable --user 0 "):
        name = command.split()[-1]
        if name in state["disabled"]:
            state["disabled"].remove(name)
    elif command.startswith("pm install -r "):
        name = state["apks"].get(command.split()[-1])
        if name and name not in state["installed"]:
            state["installed"].append(name)
            state["third_party"].append(name)
    return ""


class Device:
    def shell(self, command, read_timeout_s=None):
        current = load()
        words = command.split()
        if words and words[0] == "pm" and len(words) > 1 and words[1] in (
                "disable-user", "enable", "install", "install-create", "install-write",
                "install-commit", "install-abandon"):
            out = apply(current, command)
            save(current)
            return out
        return answer(current, command)

    def close(self):
        pass


def connect():
    host = os.environ["TV_HOST"]
    if not os.path.exists(path(host)):
        raise ConnectionError("could not connect to " + host)
    return Device()


def main():
    args = sys.argv[1:]
    state = load()
    if not args:
        return 2
    if args[0] == "--batch":
        for line in sys.stdin:
            line = line.strip()
            if line and not line.startswith("#"):
                apply(state, line)
        save(state)
        return 0
    if args[0] == "--pull":
        remote, local = args[1], args[2]
        if remote not in state["files"]:
            sys.stderr.write("no such file on the TV: " + remote + "\n")
            return 1
        with open(local, "w") as f:
            f.write(state["files"][remote])
        return 0
    if args[0] == "--push":
        local, remote = args[1], args[2]
        with open(local) as f:
            state["files"][remote] = f.read()
        save(state)
        return 0
    if args[0] == "--probe":
        for key in ("ro.product.model", "ro.product.device", "ro.build.version.sdk"):
            print(key + " = " + state.get("props", {}).get(key, ""))
        return 0
    command = " ".join(args)
    words = command.split()
    if words and words[0] == "pm" and len(words) > 1 and words[1] in (
            "disable-user", "enable", "install", "install-create", "install-write",
            "install-commit", "install-abandon"):
        out = apply(state, command)
        save(state)
        sys.stdout.write(out)
        return 0
    sys.stdout.write(answer(state, command))
    return 0


if __name__ == "__main__":
    sys.exit(main())
'''

STS = "com.google.android.tvlauncher"


def tv_state(installed, third_party, disabled=(), paths=None, props=None):
    """One simulated unit: the packages it has, which are third-party, and their APK paths."""
    return {
        "installed": list(installed),
        "third_party": list(third_party),
        "disabled": list(disabled),
        "paths": dict(paths or {}),
        "props": dict(props or {"ro.product.model": "Chromecast", "ro.product.device": "sabrina",
                                "ro.build.version.sdk": "34"}),
        "files": {},
        "apks": {},
        "session": {"id": "", "pkgs": []},
        "commands": [],
    }


class Harness:
    """A temp repo holding tools/profile.py over the fake TV, plus one state file per unit."""

    def __init__(self, case):
        tmp = tempfile.TemporaryDirectory()
        case.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        self.tools = self.repo / "tools"
        self.tools.mkdir(parents=True)
        (self.tools / "tv.py").write_text(FAKE_TV)
        self.states = self.base / "states"
        self.states.mkdir()
        self.home = self.base / "home"
        self.home.mkdir()
        self.profile = self.base / "profiles" / "sabrina"

    def put(self, host, state):
        (self.states / (host.replace(":", "_") + ".json")).write_text(json.dumps(state))

    def state(self, host):
        return json.loads((self.states / (host.replace(":", "_") + ".json")).read_text())

    def commands(self, host):
        return self.state(host)["commands"]

    def run(self, *args, host=MASTER):
        shutil.copy2(ROOT / "tools" / "profile.py", self.tools / "profile.py")
        env = dict(os.environ, TV_HOST=host, FAKE_TV_STATES=str(self.states), HOME=str(self.home))
        return subprocess.run([sys.executable, str(self.tools / "profile.py"), *args],
                              cwd=self.repo, env=env, capture_output=True, text=True, timeout=120)


def package_lines(path):
    """The package lines of a list, read the way tools/disable.sh reads one."""
    return [line.split()[0] for line in Path(path).read_text().splitlines()
            if line.strip() and not line.lstrip().startswith("#")]
