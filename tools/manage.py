#!/usr/bin/env python3
"""One place for the project's add-ons, themes and switches: what each is, its screenshot, what is on the TV,
and installing, updating or removing it the way its creator wrote it down. Nothing is downloaded: everything comes
from this checkout of the repo, and every install runs the creator's own script (an add-on's install.sh or
build-and-install.sh, or the steps in addons/README.md; tools/theme.sh for themes).

    tools/venv/bin/python -I tools/manage.py list [addons|themes|switches]
    tools/venv/bin/python -I tools/manage.py show <add-on, theme or switch>
    TV_HOST=<tv ip> tools/venv/bin/python -I tools/manage.py status
    TV_HOST=<tv ip> tools/venv/bin/python -I tools/manage.py install <add-on or theme> [script arguments]
    TV_HOST=<tv ip> tools/venv/bin/python -I tools/manage.py remove <add-on>
    TV_HOST=<tv ip> tools/venv/bin/python -I tools/manage.py set <switch> <json value> --theme <your theme folder>

install on an installed add-on updates it (pm install -r). set writes the key into a theme folder of your own and
pushes it with tools/theme.sh, as themes/README.md describes; it never edits the project's themes.
"""
import json
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOOLS = ROOT / "tools"
LAUNCHER_PKG = "com.gathin.tvhome"


# ---------------------------------------------------------------- reading the repo

def table_rows(text, header_start):
    """Rows of the first Markdown table whose header starts with header_start, as lists of cell strings."""
    rows, inside = [], False
    for line in text.splitlines():
        if line.startswith(header_start):
            inside = True
            continue
        if inside:
            if not line.startswith("|"):
                if rows:
                    break
                continue
            if re.match(r"^\|[\s:|-]+\|$", line):
                continue
            rows.append([c.strip() for c in line.strip().strip("|").split(" | ")])
    return rows


def img_src(cell):
    m = re.search(r'<img src="([^"]+)"', cell or "")
    return m.group(1) if m else None


def plain(cell):
    """A Markdown cell as text: links to their words, code marks dropped."""
    s = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", cell or "")
    return s.replace("`", "").strip()


def addons(root=ROOT):
    text = (root / "addons" / "README.md").read_text(encoding="utf-8")
    out = []
    for c in table_rows(text, "| Add-on |"):
        m = re.match(r"\[([^\]]+)\]\(([^)/]+)/\)", c[0])
        if not m:
            continue
        name, folder = m.group(1), m.group(2)
        d = root / "addons" / folder
        shot = img_src(c[5]) if len(c) > 5 else None
        out.append({
            "kind": "add-on", "name": name, "id": folder, "what": plain(c[1]), "author": plain(c[2]),
            "tested": plain(c[3]), "permissions": plain(c[4]),
            "screenshot": str(root / "addons" / shot) if shot else None,
            "package": package(d), "script": script(d),
        })
    return out


def package(folder):
    for name in ("build.gradle", "build.gradle.kts"):
        f = folder / "app" / name
        if f.is_file():
            m = re.search(r"applicationId\s*=?\s*['\"]([^'\"]+)['\"]", f.read_text(encoding="utf-8"))
            if m:
                return m.group(1)
    return None


def script(folder):
    """The creator's own installer, if the add-on has one."""
    for name in ("install.sh", "build-and-install.sh"):
        if (folder / name).is_file():
            return name
    return None


def themes(root=ROOT):
    text = (root / "themes" / "README.md").read_text(encoding="utf-8")
    out = []
    for c in table_rows(text, "| Theme |"):
        folder = plain(c[0]).strip("/")
        if not folder:
            continue
        shot = img_src(c[2]) if len(c) > 2 else None
        out.append({"kind": "theme", "name": folder, "id": folder, "what": plain(c[1]),
                    "screenshot": str(root / "themes" / shot) if shot else None})
    return out


def switches(root=ROOT):
    text = (root / "themes" / "README.md").read_text(encoding="utf-8")
    keys_part = text[text.index("## Keys"):] if "## Keys" in text else text
    shots = {}
    sw = root / "docs" / "switches.md"
    if sw.is_file():
        for c in table_rows(sw.read_text(encoding="utf-8"), "| Key |"):
            src = img_src(c[1]) if len(c) > 1 else None
            if src:
                for k in re.findall(r"`([a-z_]+)`", c[0]):
                    shots[k] = str((sw.parent / src).resolve())
    out = []
    for c in table_rows(keys_part, "| Key |"):
        for k in re.findall(r"`([a-z_]+)`", c[0]):
            out.append({"kind": "switch", "name": k, "id": k, "type": plain(c[1]) if len(c) > 1 else "",
                        "what": plain(c[2]) if len(c) > 2 else "", "screenshot": shots.get(k)})
    return out


def find(name, root=ROOT):
    n = name.strip().strip("/").lower()
    for item in addons(root) + themes(root) + switches(root):
        if n in (item["id"].lower(), item["name"].lower()):
            return item
    return None


# ---------------------------------------------------------------- the TV

def tv_python():
    for p in (TOOLS / "venv" / "bin" / "python", TOOLS / "venv" / "Scripts" / "python.exe"):
        if p.is_file():
            return str(p)
    sys.exit("run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt")


def tv(command):
    if not os.environ.get("TV_HOST"):
        sys.exit("set TV_HOST to the TV IP address")
    r = subprocess.run([tv_python(), "-I", str(TOOLS / "tv.py"), command], capture_output=True, text=True)
    # A failed read is unknown, not empty: never report "not installed" for a TV that didn't answer.
    if r.returncode:
        sys.exit(r.stderr.strip() or r.stdout.strip() or f"tv.py exited with status {r.returncode}")
    return r.stdout


def installed_packages():
    return {line.split(":", 1)[1].strip() for line in tv("pm list packages").splitlines() if line.startswith("package:")}


def run(cmd, cwd):
    print("$ " + " ".join(cmd), flush=True)
    return subprocess.run(cmd, cwd=cwd).returncode


def install_addon(item, extra):
    folder = ROOT / "addons" / item["id"]
    if item["script"]:
        # The creator's own script, as their README says to run it.
        return run(["bash", str(folder / item["script"])] + extra, ROOT)
    # No script: the steps addons/README.md gives for every add-on.
    gradlew = "gradlew.bat" if os.name == "nt" else "./gradlew"
    code = run([gradlew, "assembleRelease"], folder)
    if code:
        return code
    apk = folder / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
    py = tv_python()
    code = run([py, "-I", str(TOOLS / "tv.py"), "--push", str(apk), "/data/local/tmp/addon.apk"], ROOT)
    if code:
        return code
    out = tv("pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk")
    print(out.strip())
    return 0 if "Success" in out else 1


# ---------------------------------------------------------------- commands

def show_line(item, installed=None):
    mark = ""
    if installed is not None and item["kind"] == "add-on":
        mark = "  [on the TV]" if item["package"] in installed else ""
    return f"  {item['id']:<24} {item['what'][:90]}{mark}"


def cmd_list(what):
    groups = {"addons": addons, "themes": themes, "switches": switches}
    for g in ([what] if what else ["addons", "themes", "switches"]):
        if g not in groups:
            sys.exit("list addons, themes or switches")
        items = groups[g]()
        print(f"{g} ({len(items)})")
        for item in items:
            print(show_line(item))
    return 0


def cmd_show(name):
    item = find(name)
    if not item:
        sys.exit(f"no add-on, theme or switch called {name}")
    for k in ("kind", "name", "what", "type", "author", "tested", "permissions", "package", "screenshot"):
        if item.get(k):
            print(f"{k:>12}: {item[k]}")
    if item["kind"] == "add-on":
        how = item["script"] or "the steps in addons/README.md"
        print(f"{'install':>12}: TV_HOST=<tv ip> tools/venv/bin/python -I tools/manage.py install {item['id']}  (runs {how})")
    elif item["kind"] == "theme":
        print(f"{'install':>12}: TV_HOST=<tv ip> tools/theme.sh themes/{item['id']}")
    else:
        print(f"{'set':>12}: TV_HOST=<tv ip> tools/venv/bin/python -I tools/manage.py set {item['id']} <value> --theme <your theme folder>")
    return 0


def cmd_status():
    have = installed_packages()
    print(f"launcher: {'installed' if LAUNCHER_PKG in have else 'not installed'}")
    print("add-ons on the TV:")
    for item in addons():
        if item["package"] in have:
            print(f"  {item['id']}")
    config = tv(f"cat /sdcard/Android/data/{LAUNCHER_PKG}/files/config.json 2>/dev/null")
    match = None
    if config.strip():
        try:
            pushed = json.loads(config)
            for t in themes():
                f = ROOT / "themes" / t["id"] / "config.json"
                if f.is_file() and json.loads(f.read_text(encoding="utf-8")) == pushed:
                    match = t["id"]
        except ValueError:
            pass
    print(f"theme: {match or ('your own' if config.strip() else 'none (the built-in config.xml)')}")
    return 0


def cmd_install(name, extra):
    item = find(name)
    if not item or item["kind"] == "switch":
        sys.exit(f"no add-on or theme called {name}")
    if item["kind"] == "theme":
        return run(["bash", str(TOOLS / "theme.sh"), str(ROOT / "themes" / item["id"])] + extra, ROOT)
    return install_addon(item, extra)


def cmd_remove(name):
    item = find(name)
    if not item or item["kind"] != "add-on" or not item["package"]:
        sys.exit(f"no add-on called {name}")
    out = tv(f"pm uninstall {item['package']}")
    print(out.strip())
    return 0 if "Success" in out else 1


def cmd_set(key, value, theme_folder):
    if not find(key) or find(key)["kind"] != "switch":
        sys.exit(f"no switch called {key}")
    folder = Path(theme_folder).resolve()
    if ROOT / "themes" in folder.parents or folder == ROOT / "themes":
        sys.exit("use a theme folder of your own, not one of the project's (copy one first)")
    try:
        parsed = json.loads(value)
    except ValueError:
        parsed = value
    f = folder / "config.json"
    data = json.loads(f.read_text(encoding="utf-8")) if f.is_file() else {}
    data[key] = parsed
    folder.mkdir(parents=True, exist_ok=True)
    f.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{key} = {json.dumps(parsed)} in {f}")
    return run(["bash", str(TOOLS / "theme.sh"), str(folder)], ROOT)


def main(argv):
    if not argv or argv[0] in ("-h", "--help", "help"):
        print(__doc__)
        return 0
    c, args = argv[0], argv[1:]
    if c == "list":
        return cmd_list(args[0] if args else None)
    if c == "show" and args:
        return cmd_show(" ".join(args))
    if c == "status":
        return cmd_status()
    if c == "install" and args:
        return cmd_install(args[0], args[1:])
    if c == "remove" and args:
        return cmd_remove(args[0])
    if c == "set" and len(args) >= 2:
        theme = None
        if "--theme" in args:
            i = args.index("--theme")
            theme = args[i + 1] if i + 1 < len(args) else None
            args = args[:i] + args[i + 2:]
        if not theme:
            sys.exit("set needs --theme <your theme folder>")
        return cmd_set(args[0], args[1], theme)
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
