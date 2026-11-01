"""A shell block that builds an add-on and pushes it to the TV has to run where it says.

`addons/README.md` is the recipe a new add-on author copies. It opened with
`cd addons/<name> && ./gradlew assembleRelease`, which leaves the shell inside the add-on folder, and
then named `tools/venv/bin/python`, `tools/tv.py` and `addons/<name>/app/...` - all paths from the repo
root - so the second and third lines died with "No such file or directory" for anyone who pasted the
three lines (checked by hand: `cd addons/weather && true; ls tools/venv/bin/python`). Two of the four
add-on READMEs that carry the same recipe had worked around it themselves, one with `&& cd ../..` and
two by reading the tools as `../../tools/...` from inside their folder, which is why nothing failed in
review.

The checker reads each fenced block that pushes an APK, follows the directory a `cd` moves the shell
to, and fails when a path under `tools/`, `launcher/`, `addons/`, `themes/`, `examples/`, `docs/` or
`tests/` no longer starts at that directory in the repo - or does not exist there. `<name>` in the
template is filled in with every add-on folder in the repo, so the template is checked the way a
reader uses it. A path that lives under `venv/`, `build/`, `traces/` or `shots/` is not required to
exist: the step that produces it creates it.
"""
import os
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ADDONS = ROOT / "addons"
BLOCK = re.compile(r"```(?:bash|sh|shell|console)\n(.*?)```", re.S)
TOP = ("tools", "launcher", "addons", "themes", "examples", "docs", "tests")
PATH_RE = re.compile(r"(?<![\w/.-])((?:%s)/[^\s\"'`;|)]*)" % "|".join(TOP))
CD_RE = re.compile(r"(?:^|&&\s*|;\s*)cd\s+([^\s;&|]+)")
GENERATED = {"venv", "build", "traces", "shots", "backup", "work", ".gradle", "__pycache__"}


def recipes(text):
    """The fenced blocks that build an add-on and push the APK to the TV."""
    return [b for b in BLOCK.findall(text) if "tools/tv.py" in b]


def unresolved(block, cwd="."):
    """Every repo path in BLOCK that does not resolve from the directory the block has cd'd to."""
    bad = []
    where = cwd
    for line in block.split("\n"):
        code = "" if line.strip().startswith("#") else line.split(" #")[0]
        if not code.strip():
            continue
        cd_args = set()
        for arg in CD_RE.findall(code):
            cd_args.add(arg)
            if arg.startswith(("/", "~")) or "<" in arg or not arg.isascii():
                where = None                      # an absolute or unknown destination: stop following
            elif where is not None:
                where = os.path.normpath(os.path.join(where, arg))
        if where is None:
            continue
        for token in PATH_RE.findall(code):
            token = token.rstrip(".,:;")
            if token in cd_args or "<" in token or not token.isascii():
                continue                          # the argument of a cd, or a placeholder
            if where != ".":
                bad.append(f"{token} runs from {where}/, not from the repo root")
            elif GENERATED.intersection(Path(token).parts):
                continue
            elif not (ROOT / token).exists():
                bad.append(f"{token} does not exist")
    return bad


def addon_names():
    return sorted(p.name for p in ADDONS.iterdir() if p.is_dir()) if ADDONS.is_dir() else []


class AddonRecipeTest(unittest.TestCase):
    def test_every_recipe_runs_where_it_says(self):
        pages = [ADDONS / "README.md"] + sorted(ADDONS.glob("*/README.md"))
        checked = 0
        for page in pages:
            text = page.read_text(encoding="utf-8")
            for block in recipes(text):
                # The template names the folder <name>; check it with each real one in place.
                variants = [block.replace("<name>", n) for n in addon_names()] if "<name>" in block else [block]
                for variant in variants:
                    checked += 1
                    with self.subTest(page=str(page.relative_to(ROOT)), block=variant.split("\n")[0]):
                        self.assertEqual(unresolved(variant), [])
        self.assertGreaterEqual(checked, len(pages),
                                "no recipe block was read: the fenced blocks or the tool name changed")


class CheckerTest(unittest.TestCase):
    def test_a_cd_that_leaves_the_next_line_at_the_root_is_reported(self):
        block = ("```bash\ncd addons/<name> && ./gradlew assembleRelease\n"
                 "TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --push app/build/x.apk /data/local/tmp/a.apk\n```")
        bad = unresolved(recipes(block)[0].replace("<name>", "weather"))
        self.assertTrue(bad, "the block that broke addons/README.md must be reported")
        self.assertIn("tools/tv.py", " ".join(bad))

    def test_coming_back_to_the_root_is_accepted(self):
        block = ("```bash\ncd addons/weather && ./gradlew assembleRelease && cd ../..\n"
                 "tools/venv/bin/python -I tools/tv.py --push addons/weather/app/build/x.apk /data/local/tmp/a.apk\n```")
        self.assertEqual(unresolved(recipes(block)[0]), [])

    def test_a_block_that_stays_inside_the_folder_is_accepted(self):
        block = ("```bash\ncd addons/weather && ./gradlew assembleRelease\n"
                 "TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py --push app/build/x.apk /data/local/tmp/a.apk\n```")
        self.assertEqual(unresolved(recipes(block)[0]), [])

    def test_a_missing_tool_is_reported(self):
        block = "```bash\ntools/venv/bin/python -I tools/tv.py --probe\ntools/venv/bin/python -I tools/gone.py --probe\n```"
        bad = unresolved(recipes(block)[0])
        self.assertTrue(bad)
        self.assertIn("does not exist", " ".join(bad))

    def test_a_path_in_a_comment_is_not_run(self):
        block = "```bash\ntools/venv/bin/python -I tools/tv.py --probe   # not tools/gone.py\n```"
        self.assertEqual(unresolved(recipes(block)[0]), [])

    def test_an_absolute_cd_stops_the_check(self):
        block = "```bash\ncd /somewhere/else\ntools/tv.py --probe\n```"
        self.assertEqual(unresolved(recipes(block)[0]), [])

    def test_a_block_without_a_push_is_not_a_recipe(self):
        self.assertEqual(recipes("```bash\ncd tools && ./gradlew assembleRelease\n```"), [])


if __name__ == "__main__":
    unittest.main()
