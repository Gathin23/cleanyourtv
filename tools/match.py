#!/usr/bin/env python3
"""How the tested lists in examples/ treat each package a TV has, read offline.

    match.py <saved pm list packages> [--examples <folder>]

Save the TV's packages once (`tools/venv/bin/python -I tools/tv.py 'pm list packages' > packages.txt`),
then run this: for every package in that file it prints how many of the lists in examples/ disable it,
how many name it commented out (kept on), and the trailing comment those lines give it most often. A
folder can hold a second list beside its debloat.txt - google-chromecast-gtv-4k/debloat-keep-cast.txt,
sony-bravia-4k-ur3/debloat-display-only.txt - and tools/disable.sh applies one like any other, so every
debloat*.txt is read rather than the first only: a package that one of them alone disables is a package
some tested set turned off, and counting it as 0 would read as no list having tried it. A package that
disable.sh refuses as one of Android's core packages (#884) is marked `never`, whatever any list says.

It reads files only: no TV, no ADB client, nothing written. It ranks nothing and suggests no list; the
counts are what the lists say, not a verdict for this TV.

Exit codes: 0 the table was printed, 2 the packages file, the examples folder or a list in it could not
be read.
"""
import argparse
from collections import Counter
import os
from pathlib import Path
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import recovery  # noqa: E402  (needs the tools/ directory on the path first)

EXAMPLES = Path(__file__).resolve().parents[1] / "examples"


def read_packages(path):
    """The package names in a saved `pm list packages` answer, sorted and without repeats.

    `package:<name>` lines are what pm prints; with -f the line is `package:<apk path>=<name>`. A bare
    name on its own line is taken too, so a hand-trimmed file works.
    """
    try:
        text = Path(path).read_text(encoding="utf-8-sig", errors="replace")
    except OSError as e:
        raise SystemExit(f"could not read {path} ({e.strerror or e}): pass a saved `pm list packages`")
    names = set()
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("package:"):
            line = line[len("package:"):].rsplit("=", 1)[-1].strip()
        if recovery.PACKAGE_RE.fullmatch(line):
            names.add(line)
    if not names:
        raise SystemExit(f"{path}: no package names in it: pass a saved `pm list packages`")
    return sorted(names)


def read_list(text):
    """({package: note} disabled, {package: note} commented out) for one debloat list.

    A disabled line is a package name, with an optional `# note`. A commented-out line is `#`, one
    package name and nothing else but an optional `# note`, so a header sentence that happens to start
    with a package name is not counted.
    """
    disabled, commented = {}, {}
    for line in text.splitlines():
        line = line.strip()
        target = disabled
        if line.startswith("#"):
            line = line[1:].strip()
            target = commented
        body, _, note = line.partition("#")
        words = body.split()
        if not words or not recovery.PACKAGE_RE.fullmatch(words[0]):
            continue
        if target is commented and len(words) != 1:
            continue
        target.setdefault(words[0], note.strip())
    for name in disabled:
        commented.pop(name, None)
    return disabled, commented


def tally(packages, examples):
    """{package: (disabled, commented, most common note)} over every examples/*/debloat*.txt."""
    # The same discovery tests/test_examples.py and tests/test_stock_launcher_undo.py sweep with: a folder
    # may ship a second list beside debloat.txt, and tools/disable.sh takes it like the first.
    # glob() suppresses directory read errors, which would silently produce a partial tally.
    lists = []
    root = Path(examples)
    try:
        with os.scandir(root) as folders:
            for folder in folders:
                if folder.is_dir():
                    with os.scandir(folder.path) as entries:
                        lists.extend(Path(entry.path) for entry in entries
                                     if Path(entry.name).match("debloat*.txt"))
    except OSError as e:
        raise SystemExit(f"could not read {e.filename or examples} ({e.strerror or e}): no counts printed")
    lists.sort()
    if not lists:
        raise SystemExit(f"{examples}: no */debloat*.txt lists in it")
    wanted = set(packages)
    disabled, commented = Counter(), Counter()
    notes = {name: Counter() for name in packages}
    for path in lists:
        try:
            text = path.read_text(encoding="utf-8-sig", errors="replace")
        except OSError as e:
            # One unreadable list stops the whole tally: partial counts would read as the real ones.
            raise SystemExit(f"could not read {path} ({e.strerror or e}): no counts printed")
        off, kept = read_list(text)
        for counter, entries in ((disabled, off), (commented, kept)):
            for name, note in entries.items():
                if name not in wanted:
                    continue
                counter[name] += 1
                if note:
                    notes[name][note] += 1
    result = {}
    for name in packages:
        common = notes[name].most_common(1)
        result[name] = (disabled[name], commented[name], common[0][0] if common else "")
    return result, len(lists)


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="For each package in a saved `pm list packages`, how many tested lists in examples/ "
                    "disable it or keep it commented out. Reads files only.")
    parser.add_argument("packages", help="a saved `pm list packages` answer")
    parser.add_argument("--examples", default=str(EXAMPLES),
                        help="the folder of tested lists (default: this checkout's examples/)")
    args = parser.parse_args(argv)
    try:
        packages = read_packages(args.packages)
        counts, read = tally(packages, args.examples)
    except SystemExit as e:
        sys.stderr.write(f"{e.code}\n")
        return 2
    never = set(recovery.core_packages(packages))
    width = max(len("package"), *(len(name) for name in packages))
    print(f"{'package':<{width}}  disabled  commented  note")
    for name in packages:
        if name in never:
            print(f"{name:<{width}}  never")
            continue
        off, kept, note = counts[name]
        print(f"{name:<{width}}  {off:>8}  {kept:>9}  {note}".rstrip())
    sys.stderr.write(f"{len(packages)} packages, counted over {read} lists in {args.examples}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
