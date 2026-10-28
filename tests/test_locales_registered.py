"""Every language the launcher ships is one the TV can be offered.

A translation is two halves. `values-*/strings.xml` is the half the launcher reads once the TV is
already in that language; the half that lets anybody choose it is `res/xml/locales_config.xml`, which
`android:localeConfig` in the manifest points at, because Android 13+ only offers a launcher the
locales listed there - `docs/workflows/translation.md` step 3 says it in one line: "a language folder
that is not registered there cannot be chosen for the launcher alone on Android 13+". Step 4 asks for
the same language in the README's list. Neither is read by anything, so `values-da` landed with its
strings and neither announcement (807fffee, 24 Oct): Danish ships in the APK, the README counts it in
"28 languages", no TV can pick it for the launcher alone, and a Danish reader of the page does not
see their own language in the list under that count.

The set of languages is read out of the resource folders, so a language added tomorrow is covered the
day it lands, and the folder-to-locale rule is Android's own: `de`, `zh-rCN` for a language with a
region, and `b+sr+Latn` for one with a script, which `locales_config.xml` registers as `sr-Latn`.
English needs no folder: `values/` is the default.

The Languages list moved from the README into docs/launcher.md (its "Languages" section); the README
keeps the count in its launcher table and links that page.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "launcher/app/src/main/res"
CONFIG = RES / "xml/locales_config.xml"
MANIFEST = ROOT / "launcher/app/src/main/AndroidManifest.xml"
FRONT = ROOT / "docs" / "launcher.md"  # the Languages list, linked from the README
DEFAULT = "en"

# The name each language goes by in docs/launcher.md's Languages section, keyed by the locale it registers as.
# A new translation has to be named here as well as in that page: this is the only map from the
# qualifier Android reads (`sr-Latn`) to the English name the page uses ("Serbian (Latin)").
NAMES = {
    "ar": "Arabic",
    "sr-Latn": "Serbian (Latin)",
    "bn": "Bangla",
    "ca": "Catalan",
    "cs": "Czech",
    "da": "Danish",
    "de": "German",
    "es": "Spanish",
    "fi": "Finnish",
    "fr": "French",
    "in": "Indonesian",
    "it": "Italian",
    "iw": "Hebrew",
    "ko": "Korean",
    "nb": "Norwegian Bokm\u00e5l",
    "nl": "Dutch",
    "pl": "Polish",
    # values-pt is Brazilian Portuguese; values-pt-rPT holds the European wording where it differs,
    # and the page names the pair once, as the launcher's picker does.
    "pt": "Portuguese",
    "pt-PT": "Portuguese",
    "ru": "Russian",
    "th": "Thai",
    "tr": "Turkish",
    "uk": "Ukrainian",
    "sv": "Swedish",
    "uz": "Uzbek (Latin)",
    "vi": "Vietnamese",
    "zh-CN": "Simplified Chinese",
    "zh-TW": "Traditional Chinese (Taiwan)",
}


def qualifier(folder):
    """The locale Android reads for a `values-*` folder name: `de`, `zh-rCN`, or `b+sr+Latn`."""
    body = folder[len("values-"):]
    if body.startswith("b+"):
        return body[2:].replace("+", "-")
    language, _, region = body.partition("-r")
    return f"{language}-{region}" if region else language


def translated(res=RES):
    """{locale: folder} for every `values-*/strings.xml` the launcher ships."""
    return {qualifier(path.parent.name): path.parent.name
            for path in sorted(res.glob("values-*/strings.xml"))}


def registered(config=CONFIG):
    """The locales `locales_config.xml` lists, without the `android:` namespace prefix."""
    try:
        root = ET.parse(config).getroot()
    except ET.ParseError as e:
        raise AssertionError(f"{config}: not valid XML ({e})") from e
    found = set()
    for node in root.iter("locale"):
        for key, value in node.attrib.items():
            if key.rsplit("}", 1)[-1] == "name":
                found.add(value)
    return found


def unregistered(folders, locales):
    """The locales a folder ships that the config never lists."""
    return sorted(set(folders) - set(locales))


def without_a_folder(locales, folders):
    """The locales the config offers that no folder (and not `values/`) backs."""
    return sorted(set(locales) - set(folders) - {DEFAULT})


def languages_block(text):
    """The body of docs/launcher.md's Languages section, or '' when the page has no such section."""
    found = re.search(r"^## Languages\n(.*?)(?=^## |\Z)", text, re.S | re.M)
    return found.group(1) if found else ""


def unnamed(block, names=NAMES):
    """The languages the block never names."""
    return sorted({name for name in names.values() if name not in block})


class LocaleConfigTest(unittest.TestCase):
    def test_every_translated_folder_is_registered(self):
        folders, locales = translated(), registered()
        # A reader that found nothing would pass for every config.
        self.assertGreaterEqual(len(folders), 20, "no values-*/strings.xml folders were found")
        self.assertGreaterEqual(len(locales), 20, "locales_config.xml lists almost nothing")
        self.assertEqual([], unregistered(folders, locales),
                         "these languages ship in the APK and no TV can choose them for the "
                         "launcher alone: add them to res/xml/locales_config.xml")

    def test_english_is_registered(self):
        # values/ is the default language and carries no qualifier, so it is the one entry no folder
        # backs; the picker needs it all the same.
        self.assertIn(DEFAULT, registered())

    def test_a_registered_locale_has_a_folder(self):
        self.assertEqual([], without_a_folder(registered(), translated()),
                         "locales_config.xml offers a language no folder translates, or one whose "
                         "qualifier it spells differently: Android then offers a language the "
                         "launcher falls back to English for")

    def test_the_manifest_points_at_this_config(self):
        # Without this line the file is read by nobody and the picker offers nothing.
        text = MANIFEST.read_text(encoding="utf-8")
        self.assertIn('android:localeConfig="@xml/locales_config"', text)

    def test_the_readme_names_every_language(self):
        block = languages_block(FRONT.read_text(encoding="utf-8"))
        self.assertTrue(block, "docs/launcher.md has no Languages section")
        self.assertEqual([], unnamed(block),
                         "docs/launcher.md's Languages list leaves these out, though the count above it "
                         "includes them")

    def test_the_name_table_covers_every_folder(self):
        # NAMES is the only map from a qualifier to a name. A language it does not know would be
        # skipped by the README check above, which is how Danish was missed.
        self.assertGreaterEqual(len(NAMES), 20, "the name table is too small to be checked")
        self.assertEqual([], sorted(set(translated()) - set(NAMES)),
                         "a language has no English name in this test's table: add it here and to "
                         "docs/launcher.md's Languages list")


class ReaderTest(unittest.TestCase):
    """The four helpers above, against folders and files written here."""

    def test_a_folder_name_becomes_the_locale_android_reads(self):
        self.assertEqual("de", qualifier("values-de"))
        self.assertEqual("zh-CN", qualifier("values-zh-rCN"))
        self.assertEqual("sr-Latn", qualifier("values-b+sr+Latn"))
        self.assertEqual("pt-PT", qualifier("values-pt-rPT"))

    def test_a_config_is_read_as_the_locales_it_lists(self):
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "locales_config.xml"
            path.write_text('<locale-config xmlns:android="http://schemas.android.com/apk/res/android">\n'
                            '    <!-- Android uses the legacy iw qualifier for Hebrew resources. -->\n'
                            '    <locale android:name="en" />\n'
                            '    <locale android:name="iw" />\n'
                            '</locale-config>\n', encoding="utf-8")
            self.assertEqual({"en", "iw"}, registered(path))

    def test_a_folder_the_config_skips_is_reported(self):
        self.assertEqual(["da"], unregistered({"da": "values-da", "de": "values-de"}, {"de", "en"}))
        self.assertEqual([], unregistered({"de": "values-de"}, {"de"}))

    def test_a_locale_with_no_folder_is_reported(self):
        self.assertEqual(["da"], without_a_folder({"en", "da"}, {"de": "values-de"}))
        self.assertEqual([], without_a_folder({"en", "de"}, {"de": "values-de"}))

    def test_the_languages_block_is_read_and_stops_at_the_next_section(self):
        page = ("## Languages\n\nGerman and Danish.\n\n"
                "## Remote keys\n\nKorean.\n")
        self.assertEqual("German and Danish.", languages_block(page).strip())
        self.assertEqual(["Korean"], unnamed(languages_block(page), {"ko": "Korean"}))
        self.assertEqual("", languages_block("no block here"))

    def test_a_page_that_names_nothing_is_reported(self):
        self.assertEqual(["Danish", "German"], unnamed("", {"da": "Danish", "de": "German"}))
        self.assertEqual([], unnamed("German, Danish", {"da": "Danish", "de": "German"}))


if __name__ == "__main__":
    unittest.main()
