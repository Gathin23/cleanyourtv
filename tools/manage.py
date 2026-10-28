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
pushes it with tools/theme.sh, as themes/README.md describes; it never edits a folder in this checkout, so pointing
--theme at the project's themes/ or examples/ is refused before anything is written. set reads the value the way
the key's own type in the Keys table needs - true or false for a bool, a JSON list, object or number where the
table says so, the text itself for a string - and refuses one the launcher would ignore or a number outside the
range the table states. A theme's config.json is hand-edited, so one that cannot be read, is not JSON, or is not a
JSON object is named before the key is written, and a write it cannot make says so instead of ending in a traceback.
set's own arguments are read first: a switch without a value, a --theme without the folder behind it, and a word
left over are answered with the usage line and exit 2 before a theme folder is read or written. list, status,
show, install and remove read their own words the same way, before anything is read from this checkout or asked
of the TV.

Exit status: 0 the command ran, 1 a command on the TV or a script this checkout runs failed, 2 the command line
asked for something this script does not do - a command it does not have, or a word the command would drop - which
is answered before the READMEs are read or the TV is asked anything.
"""
import json
import math
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


# ---------------------------------------------------------------- a switch's value

def declared_kind(item):
    """How the launcher reads a switch, from the type column of the Keys table in themes/README.md.

    Every reader in Config.java is strict about the JSON type - bool() takes a Boolean and nothing else,
    string() a String, integer() a Number in its range, the list readers an array - and a value of another type
    is not an error there: the built-in default comes back, so the key silently does nothing on the TV while set
    reports it as written. The table states the type of every key, so set reads the value the way that type needs
    and refuses one the launcher would drop.
    """
    cell = (item.get("type") or "").strip()
    if "config.xml only" in cell:
        return "config-only"
    if cell == "bool":
        return "bool"
    if cell.startswith("list"):
        return "list"
    if cell == "object":
        return "object"
    if declared_range(cell) != (None, None):
        return "number"
    return "string" if cell else None


def declared_range(cell):
    """The (low, high) of a range cell such as '1 to 8', or (None, None) when the cell is not a range."""
    m = re.fullmatch(r"(-?\d+) to (-?\d+)", cell or "")
    return (int(m.group(1)), int(m.group(2))) if m else (None, None)


def number_in_range(key, item, parsed, text):
    """An int the launcher's integer() would keep: truncated like Number.intValue(), inside the table's range.

    integer() is `int i = ((Number) v).intValue(); return i < min || i > max ? fallback : i;`, so a float is
    truncated on the TV and anything outside the range is silently the default. The range comes from the Keys
    table, so the value set writes is one the launcher reads back. (lunar_utc_offset's floor in Config.java is
    -1, the "by locale" sentinel the table's Meaning column calls Unset rather than a number to pass.)
    """
    cell = (item.get("type") or "").strip()
    low, high = declared_range(cell)
    if low is None or high is None:
        # declared_kind() only sends a range cell here; a cell whose shape changed must not pass unchecked.
        sys.exit(f"{key} has no range in the Keys table, so there is nothing to check {text} against")
    # json.loads accepts NaN and Infinity; int() has no answer for them and integer() keeps the default.
    if isinstance(parsed, float) and not math.isfinite(parsed):
        sys.exit(f"{key} takes {cell}, so pass a finite number, not {text}")
    value = int(parsed)
    if not low <= value <= high:
        sys.exit(f"{key} takes {cell}, so {text} is out of range and the launcher would keep the default")
    return value


def value_as(key, item, text):
    """The argument read the way the key's type needs, or a refusal naming what the key takes."""
    kind = declared_kind(item)
    if kind == "config-only":
        sys.exit(f"{key} is only read from config.xml, not from a theme, so a theme push cannot set it")
    if kind == "bool":
        if text.strip().lower() not in ("true", "false"):
            sys.exit(f"{key} is a bool, so pass true or false, not {text}")
        return text.strip().lower() == "true"
    if kind in (None, "string"):
        try:
            parsed = json.loads(text)
        except ValueError:
            return text
        # A string key is read as a String only, so `set greeting_name 42` means the text 42, not a number.
        return parsed if isinstance(parsed, str) else text
    try:
        parsed = json.loads(text)
    except ValueError:
        parsed = None
    wanted = {"list": list, "object": dict, "number": (int, float)}[kind]
    # A Python bool is an int, so it would pass the number test while the launcher's integer() drops it.
    if not isinstance(parsed, wanted) or isinstance(parsed, bool):
        example = {"list": '["a", "b"]', "object": '{"key": "value"}', "number": "6"}[kind]
        sys.exit(f"{key} takes {item.get('type')}, so pass {example}, not {text}")
    if kind == "number":
        return number_in_range(key, item, parsed, text)
    return parsed


# ---------------------------------------------------------------- the theme folder set writes into

# Both folders hold config.json files this checkout ships and its tests hold to the launcher's readers
# (tests/test_shipped_config_values.py): a theme author who pointed --theme at one would rewrite a tracked
# file, and the edit would ride along in their next pull request.
PROJECT_FOLDERS = ("themes", "examples")


def reason(error):
    """The short reason from an OSError or a decoding error, without the errno prefix Python repeats."""
    return getattr(error, "strerror", None) or str(error)


def json_kind(value):
    """What a parsed JSON value is, in the words a reader would use."""
    for kind, name in ((bool, "a boolean"), (list, "an array"), (str, "a string"), (int, "a number"),
                       (float, "a number"), (type(None), "null")):
        if isinstance(value, kind):
            return name
    return type(value).__name__


def own_folder_problem(folder):
    """Why set may not write into `folder`, or None: it is one of the folders this checkout ships, or a file."""
    for name in PROJECT_FOLDERS:
        project = ROOT / name
        if folder == project or project in folder.parents:
            return (f"{folder} is inside this checkout's {name}/: set writes into a theme folder of your own, "
                    f"so copy one somewhere else first. Nothing was written or pushed.")
    if folder.exists() and not folder.is_dir():
        return f"{folder} is a file, not a folder: --theme takes a theme folder of your own."
    return None


def theme_config(folder):
    """(data, problem): the theme folder's config.json as an object, or why set cannot use it.

    The file is the owner's own, hand-edited and pushed by tools/theme.sh, so the ways it can be unreadable are
    the owner's next problem to fix: a comma left after the last key, a list where the launcher reads an object,
    a file saved in another encoding. Each is named with its path before the key is written, and nothing is
    pushed, rather than a JSONDecodeError traceback from halfway through the command.
    """
    path = folder / "config.json"
    if not path.is_file():
        return {}, None            # a folder of their own with no config.json yet: set starts the file
    try:
        text = path.read_text(encoding="utf-8-sig")
    except (OSError, UnicodeDecodeError) as e:
        return {}, f"cannot read {path}: {reason(e)}. Nothing was written or pushed."
    try:
        data = json.loads(text)
    except ValueError as e:
        return {}, (f"{path} is not valid JSON ({e}); fix it, then run set again. "
                    f"Nothing was written or pushed.")
    if not isinstance(data, dict):
        return {}, (f"{path} holds {json_kind(data)}, not a JSON object, and a theme's config.json is an object "
                    f"of keys. Nothing was written or pushed.")
    return data, None


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
    # Windows resolves a relative program against this process's directory, not cwd, so give the full path.
    gradlew = str(folder / "gradlew.bat") if os.name == "nt" else "./gradlew"
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
    for g in ([what] if what else list(LIST_GROUPS)):
        if g not in groups:
            sys.stderr.write(f"list takes addons, themes or switches, not {g!r}: {USAGE_LINES['list']}\n")
            return 2
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
    item = find(key)
    if not item or item["kind"] != "switch":
        sys.exit(f"no switch called {key}")
    folder = Path(theme_folder).resolve()
    problem = own_folder_problem(folder)
    if problem:
        sys.exit(problem)
    data, problem = theme_config(folder)
    if problem:
        sys.exit(problem)
    parsed = value_as(key, item, value)   # before the file is written or anything is pushed
    data[key] = parsed
    f = folder / "config.json"
    try:
        folder.mkdir(parents=True, exist_ok=True)
        f.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    except OSError as e:
        # A folder that is read-only, a path with a file in the middle of it, a full disk: the key is not
        # written and the theme on the TV is untouched, so say which file and why rather than a traceback.
        sys.exit(f"cannot write {f}: {reason(e)}. Nothing was pushed.")
    print(f"{key} = {json.dumps(parsed)} in {f}")
    return run(["bash", str(TOOLS / "theme.sh"), str(folder)], ROOT)


SET_USAGE = ("set takes a switch, its value and --theme <your theme folder>, e.g. "
             "set hidden_apps '[\"a\", \"b\"]' --theme ~/my-theme")


class Usage(Exception):
    """A command line that cannot run: the message goes to stderr and the status is 2."""


# What each command's own line may carry, and the usage line it answers with. `list` prints one of the three
# groups: its usage line has one word because `list addons themes` printed the add-ons and exited 0, which reads as
# "both are listed". `show` and `install` take any number of words on purpose - an add-on's name in
# addons/README.md can be several ("Frigate Glance"), and install passes what is left to the creator's script.
# `remove` is the reason this is worth judging: `remove weather --dry-run` ran pm uninstall on the TV with a word
# the command never read, so the set was changed by a command line that asked for something else.
WORDS = {
    "list": (0, 1, "addons, themes or switches"),
    "show": (1, None, "an add-on, theme or switch"),
    "status": (0, 0, "no words"),
    "install": (1, None, "an add-on or a theme"),
    "remove": (1, 1, "one add-on"),
}

USAGE_LINES = {
    "list": "list [addons|themes|switches]",
    "show": "show <add-on, theme or switch>",
    "status": "status",
    "install": "install <add-on or theme> [script arguments]",
    "remove": "remove <add-on>",
}

LIST_GROUPS = ("addons", "themes", "switches")


def own_words(command, args):
    """The words `command` reads, or Usage naming what is missing or what it would drop there.

    Every command here used to read the first word or two of its own line and ignore the rest, while set has
    answered a leftover word with the usage line and exit 2 since it was given one: `list addons themes` listed
    the add-ons and exited 0, `status --json` printed the status it prints without the flag, and `remove
    weather --dry-run` uninstalled the add-on. A word the command does not read is a command line that asked for
    something this script does not do, so it is answered before a README is read or the TV is asked anything.
    """
    least, most, what = WORDS[command]
    if len(args) < least:
        raise Usage(f"{command} needs {what}: {USAGE_LINES[command]}")
    if most is not None and len(args) > most:
        dropped = " ".join(args[most:])
        raise Usage(f"{command} takes {what}, and would drop {dropped!r}: {USAGE_LINES[command]}")
    if command == "list" and args and args[0] not in LIST_GROUPS:
        raise Usage(f"list takes addons, themes or switches, not {args[0]!r}: {USAGE_LINES[command]}")
    return args


def set_args(args):
    """(key, value, theme) from set's own arguments, or Usage naming what is missing.

    --theme is lifted out wherever it sits, as it always was, and what it leaves has to be exactly the switch
    and its value. `set hidden_apps --theme <your folder>`, the value left out, reached cmd_set with one
    argument and ended in an IndexError traceback with exit 1 - the status the script otherwise keeps for a
    theme.sh push that failed, so a word left out read as "the theme was pushed and the TV refused it". A
    --theme with no folder behind it, a second --theme, and any word left over are the same mistake, and every
    one of them is judged here, before a theme folder is read or written.
    """
    rest = list(args)
    theme = None
    if not rest:
        raise Usage(f"set needs a switch, its value and --theme <your theme folder>: {SET_USAGE}")
    if "--theme" in rest:
        i = rest.index("--theme")
        if i + 1 == len(rest):
            raise Usage(f"--theme needs the folder it writes into: {SET_USAGE}")
        theme = rest[i + 1]
        del rest[i:i + 2]
        if "--theme" in rest:
            raise Usage(f"set takes one --theme and was given twice: {SET_USAGE}")
    if len(rest) > 2:
        raise Usage(f"set takes one switch and one value, and {len(rest)} words were given: {SET_USAGE}")
    missing = []
    if not rest:
        missing.append("a switch and its value")
    elif len(rest) == 1:
        missing.append(f"the value of {rest[0]}")
    if not theme:
        missing.append("--theme <your theme folder>")
    if missing:
        raise Usage(f"set needs {' and '.join(missing)}: {SET_USAGE}")
    return rest[0], rest[1], theme


def main(argv):
    if not argv or argv[0] in ("-h", "--help", "help"):
        print(__doc__)
        return 0
    c, args = argv[0], argv[1:]
    if c == "set":
        try:
            key, value, theme = set_args(args)
        except Usage as usage:
            sys.stderr.write(f"{usage}\n")
            return 2
        return cmd_set(key, value, theme)
    if c not in WORDS:
        print(__doc__)
        return 2
    try:
        args = own_words(c, args)
    except Usage as usage:
        sys.stderr.write(f"{usage}\n")
        return 2
    if c == "list":
        return cmd_list(args[0] if args else None)
    if c == "show":
        return cmd_show(" ".join(args))   # a name can be several words: "Frigate Glance"
    if c == "status":
        return cmd_status()
    if c == "install":
        return cmd_install(args[0], args[1:])
    return cmd_remove(args[0])


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
