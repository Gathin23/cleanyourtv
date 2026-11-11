"""A `package=value` list `tools/manage.py set` writes is one the launcher keeps, entry by entry.

themes/README.md gives `app_labels`, `app_banners` and `launch_actions` entries as `"package=Name"`,
`"package=banner_name"` and `"package=action"`, and the set script's own docstring promises it "refuses one the
launcher would ignore". Config.stringList() keeps such an entry; the map the launcher builds from it is
Config.packageMap():

    int eq = entry.indexOf('=');
    if (eq <= 0) continue;
    String pkg = entry.substring(0, eq).trim(), value = entry.substring(eq + 1).trim();
    if (!pkg.isEmpty() && !value.isEmpty()) out.put(pkg, value);

so an entry with no '=', with the '=' first (`=banner_tv`) or with nothing after it (`com.netflix.ninja=`) is
skipped without a word, and the tile keeps the name, banner or action it had. The left half is the key looked up
against installed package names (`appLabels.containsKey(pkg)`), so a label written as though the app's own name
were the key - `TV=MagentaTV` - is in the map and never read. Nothing on the TV says which entry went missing, so
such a list reads as written and changes nothing: set reported the key as written and the theme as pushed.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import manage  # noqa: E402

CONFIG_JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "Config.java"
# The launcher's tile artwork reader and the drawables it draws from: the other half of an app_banners entry is
# held to both, since a name bundledBanner() answers null for is a banner the theme says is set and the tile
# never shows.
APP_ADAPTER = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "AppAdapter.java"
LAUNCHER_RES = ROOT / "launcher" / "app" / "src" / "main" / "res"

# The pairs the Keys rows hand out, plus the shapes the shipped themes and lists use.
GOOD = {
    "app_labels": [
        "com.telekom.onetv.tv.pl=TV",
        "com.netflix.ninja=Netflix",
        "ru.sberdevices.starlauncher=Салют ТВ",
        "com.mediatek.wwtv.tvcenter=Set-top box",
    ],
    "app_banners": ["com.cvte.tv.setting=banner_tv", "com.mediatek.wwtv.tvcenter=banner_tv"],
    "launch_actions": [
        "com.telekom.onetv.tv.pl=com.telekom.onetv.tv.pl.action.PROGRAM_GUIDE",
        "com.mediatek.wwtv.tvcenter=input:com.mediatek.tvinput/.hdmi.HDMIInputService/HW5",
        "fr.bouyguestelecom.tv.bbui=view:bbox://live",
    ],
}

# Every list a Keys row writes with a `package=` entry.
PAIR_KEYS = tuple(GOOD)


class ThemeSetCase(unittest.TestCase):
    """A theme folder of its own, and `set` driven the way main() does before anything reaches the TV."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.theme = Path(self.tmp.name) / "my-theme"
        self.theme.mkdir()
        self.config = self.theme / "config.json"
        # cmd_set ends by pushing the theme with tools/theme.sh; the tests stop before the TV.
        patcher = mock.patch.object(manage, "run")
        self.push = patcher.start()
        self.push.return_value = 0
        self.addCleanup(patcher.stop)

    def set_key(self, value, key):
        """Runs `set <key>` the way main() does; returns the refusal text, or None when the value was written."""
        try:
            with mock.patch("sys.stdout"):
                manage.cmd_set(key, value, str(self.theme))
        except SystemExit as stop:
            return str(stop.code)
        return None

    def refused(self, value, key, *expected):
        refusal = self.set_key(value, key)
        text = refusal if refusal is not None else ""
        self.assertIsNotNone(refusal, f"{key} {value} was written, and the launcher skips an entry of it")
        for part in expected:
            self.assertIn(part, text)
        self.assertEqual({}, self.written(), "a refused list must not touch the theme")
        self.push.assert_not_called()
        return text

    def written(self):
        if not self.config.is_file():
            return {}
        return json.loads(self.config.read_text(encoding="utf-8"))


class PackageListRefusalTest(ThemeSetCase):

    def test_an_entry_without_an_equals_sign_is_refused(self):
        # The shape a reader types when they name the app instead of the pair: the whole entry is the key.
        for key in PAIR_KEYS:
            with self.subTest(key=key):
                self.refused('["com.netflix.ninja"]', key, "has no '=' in it")

    def test_an_entry_with_nothing_before_the_equals_sign_is_refused(self):
        # indexOf('=') is 0 here, and packageMap() answers `if (eq <= 0) continue`.
        self.refused('["=banner_tv"]', "app_banners", "names no package before the '='")
        self.refused('["=TV"]', "app_labels", "names no package before the '='")

    def test_an_entry_with_nothing_after_the_equals_sign_is_refused(self):
        self.refused('["com.netflix.ninja="]', "app_labels", "nothing to set for it")
        self.refused('["com.x="]', "launch_actions", "nothing to set for it")

    def test_a_key_that_is_not_a_package_name_is_refused(self):
        # The app's own name where its package goes: in the map, and no tile is ever looked up under it.
        self.refused('["TV=MagentaTV"]', "app_labels", "'TV' is not a package name")
        self.refused('["youtube=view:bbox://live"]', "launch_actions", "is not a package name")

    def test_the_first_bad_entry_is_named(self):
        text = self.refused('["com.netflix.ninja=Netflix", "TV=TV"]', "app_labels", "TV=TV")
        self.assertNotIn("com.netflix.ninja=Netflix", text)

    def test_an_entry_that_is_not_a_string_is_refused(self):
        self.refused("[1]", "app_labels", "is not a string")

    def test_an_empty_entry_is_refused(self):
        self.refused('[" "]', "app_banners", "an empty entry")

    def test_the_pairs_the_launcher_keeps_are_written(self):
        for key, entries in GOOD.items():
            with self.subTest(key=key):
                self.assertIsNone(self.set_key(json.dumps(entries), key))
                self.assertEqual(entries, self.written()[key])

    def test_a_padded_pair_is_written_as_the_map_reads_it(self):
        # packageMap() trims both halves; the entry itself is trimmed the way stringList() keeps it.
        self.assertIsNone(self.set_key('[" com.netflix.ninja = Netflix "]', "app_labels"))
        self.assertEqual(["com.netflix.ninja = Netflix"], self.written()["app_labels"])

    def test_an_empty_list_is_taken(self):
        # `[]` is how an owner clears the labels or the banners a theme added.
        for key in PAIR_KEYS:
            with self.subTest(key=key):
                self.assertIsNone(self.set_key("[]", key))
                self.assertEqual([], self.written()[key])

    def test_another_list_is_not_held_to_pairs(self):
        # dock_apps is a list of package names, read by stringList() alone: no '=' is expected in it, and a name
        # that is not on the TV is skipped by the launcher rather than dropped from the list.
        self.assertIsNone(self.set_key('["com.example.player", "com.example.tv"]', "dock_apps"))
        self.assertEqual(["com.example.player", "com.example.tv"], self.written()["dock_apps"])


class BannerNameTest(ThemeSetCase):
    """The right half of an app_banners pair names a banner_* drawable the launcher ships."""

    def test_a_name_that_is_not_a_banner_is_refused(self):
        # A colour, or the app's own name, where a bundled drawable's name goes: bundledBanner() answers null,
        # and the tile shows the app's own art while the theme says the banner is set.
        self.refused('["com.netflix.ninja=magenta"]', "app_banners", "does not start with `banner_`")
        self.refused('["com.netflix.ninja=Netflix"]', "app_banners", "does not start with `banner_`")

    def test_a_banner_the_launcher_does_not_ship_is_refused(self):
        text = self.refused('["com.netflix.ninja=banner_tv2"]', "app_banners", "banner_tv2")
        # The refusal names the names that would work, so the reader does not have to look them up.
        self.assertIn("banner_tv", text)

    def test_the_banner_the_launcher_draws_is_written(self):
        for entries in (["com.cvte.tv.setting=banner_tv"],
                        ["com.cvte.tv.setting=banner_tv", "com.mediatek.wwtv.tvcenter=banner_tv"]):
            with self.subTest(entries=entries):
                self.assertIsNone(self.set_key(json.dumps(entries), "app_banners"))
                self.assertEqual(entries, self.written()["app_banners"])

    def test_a_padded_banner_name_is_read_as_the_map_reads_it(self):
        self.assertIsNone(self.set_key('[" com.cvte.tv.setting = banner_tv "]', "app_banners"))
        self.assertEqual(["com.cvte.tv.setting = banner_tv"], self.written()["app_banners"])

    def test_the_other_two_lists_are_not_held_to_a_banner_name(self):
        # A label and a launch action are free text after the '=': only app_banners names a bundled drawable.
        self.assertIsNone(self.set_key('["com.netflix.ninja=magenta"]', "app_labels"))
        self.assertEqual(["com.netflix.ninja=magenta"], self.written()["app_labels"])
        self.assertIsNone(self.set_key('["fr.bouyguestelecom.tv.bbui=view:bbox://live"]', "launch_actions"))

    def test_the_entries_the_pair_rule_refuses_are_left_to_it(self):
        # No '=', an empty half: packageMap() never reaches the value, and the pair refusal already names it.
        text = self.refused('["com.netflix.ninja"]', "app_banners", "has no '=' in it")
        self.assertNotIn("banner_", text.replace("banner_name", ""))


class BannerNameRuleTest(unittest.TestCase):
    """The rule is read out of the launcher, so the refusal cannot drift from the code it stands for."""

    def test_the_rule_is_the_one_app_adapter_reads(self):
        source = APP_ADAPTER.read_text(encoding="utf-8")
        for line in ('if (name == null || !name.startsWith("banner_")) return null;',
                     'getIdentifier(name, "drawable", ctx.getPackageName())',
                     "int id = ctx.getResources()"):
            with self.subTest(line=line):
                self.assertIn(line, source)

    def test_the_names_come_out_of_the_launcher_resources(self):
        names = manage.bundled_banners()
        self.assertIn("banner_tv", names)
        self.assertEqual([n for n in names if not n.startswith("banner_")], [],
                         "a name that bundledBanner() would refuse is in the list")
        self.assertEqual(sorted(names), names, "the refusal prints them in a stable order")
        # Read out of res/ rather than a list in manage.py: the folder the drawable sits in proves it.
        shipped = sorted(p.stem for p in LAUNCHER_RES.rglob("banner_*")
                         if p.is_file() and p.suffix.lower() in manage.BANNER_SUFFIXES)
        self.assertEqual(shipped, names)

    def test_a_theme_folder_with_no_launcher_beside_it_has_no_names(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual([], manage.bundled_banners(tmp))
            self.assertIsNotNone(manage.banner_problem(["com.x=banner_tv"], tmp))


class PackagePairProblemTest(unittest.TestCase):
    def test_the_entries_the_map_keeps(self):
        for key, entries in GOOD.items():
            with self.subTest(key=key):
                self.assertIsNone(manage.package_pair_problem(entries))
        self.assertIsNone(manage.package_pair_problem([]))
        # A value may hold '=' after the first one: packageMap() splits at the first only.
        self.assertIsNone(manage.package_pair_problem(["com.x=view:http://tv.local/?a=b"]))

    def test_the_halves_the_map_drops(self):
        for entry in ("com.x", "=TV", "com.x=", "TV=TV", " x =y"):
            with self.subTest(entry=entry):
                self.assertIsNotNone(manage.package_pair_problem([entry]))

    def test_the_rule_is_the_one_config_java_reads(self):
        # Read out of the launcher, so the refusal cannot drift from the reader it stands for.
        source = CONFIG_JAVA.read_text(encoding="utf-8")
        for line in ("int eq = entry.indexOf('=');", "if (eq <= 0) continue;",
                     "if (!pkg.isEmpty() && !value.isEmpty()) out.put(pkg, value);"):
            with self.subTest(line=line):
                self.assertIn(line, source)
        self.assertIn("an entry without both halves is skipped", source)


class ShippedPairsTest(unittest.TestCase):
    def test_every_shipped_pair_survives_the_rule(self):
        # The refusal must not turn a theme or a list this checkout ships into one set cannot write.
        files = sorted(ROOT.glob("themes/*/config.json")) + sorted(ROOT.glob("examples/*/config.json")) \
            + sorted(ROOT.glob("examples/*/theme/config.json"))
        read = carrying = 0
        for path in files:
            try:
                config = json.loads(path.read_text(encoding="utf-8-sig"))
            except ValueError:
                continue
            if not isinstance(config, dict):
                continue
            read += 1
            for key in PAIR_KEYS:
                entries = config.get(key)
                if entries is None:
                    continue
                carrying += 1
                with self.subTest(path=str(path), key=key):
                    self.assertIsNone(manage.package_pair_problem(entries))
                    if key == "app_banners":
                        self.assertIsNone(manage.banner_problem(entries),
                                          "a banner this checkout ships is one set refuses to write")
        self.assertGreaterEqual(read, 100, "the walk stopped reading the checkout's config.json files")
        self.assertGreaterEqual(carrying, 8, "the walk found fewer pair lists than the tree ships")
