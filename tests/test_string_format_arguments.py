"""The arguments the launcher hands a formatted string are the ones the string and its translations take.

A launcher string with a specifier is not printed raw: `getString(R.string.race_kicker, race.round,
race.shortName(), race.place)` hands three arguments to Java's `Formatter`, and `values/strings.xml` gives
it `Round %1$d · %2$s · %3$s`. Two mistakes on either side throw on the line that formats the string, and
nothing notices until a TV shows it:

  * a call that hands fewer arguments than the string names - `MissingFormatArgumentException`. The row,
    the panel or the toast is never drawn; the rest of the screen is fine, and the build is green.
  * a translation whose specifier moved (`%1$d` written `%1$s` for the round, a number) -
    `IllegalFormatConversionException`; or one that names an argument the English string does not have.
    That is a crash on a TV set to that language and nowhere else.

aapt2 checks the escaping and the XML, not the specifiers, so neither is a build error, and nothing read
them: `tests/test_locales_registered.py` reads the folder names (which languages Android may offer),
`tests/test_translation_recipe_files.py` the files a folder adds beside `strings.xml`,
`tests/test_launcher_settings_rows.py` the text of the settings row before its specifier, and no test in
`tests/` names a specifier or `Formatter` at all.

So this reads the three sides and holds them together:

  * the arguments every `getString`/`getQuantityString` call hands, from the call site in
    `launcher/app/src/main/java`, against the specifiers of the English string or plural item it names - a
    call may hand more than the string takes (Java ignores the rest) but never fewer;
  * the specifiers of every `values-*/strings.xml` string, and of every `<item>` of a `<plurals>` whose
    quantity English also has, against the English string's own. A specifier the translation drops is left
    alone (a shorter sentence is a translation choice), reordering with explicit indices (`%2$s ... %1$s`)
    is left alone (Java reads by the index), a `few`/`many` quantity Russian or Arabic adds is that
    language's plural rules, and `%%` is a literal per cent sign (`CPU %1$d%%`), not an argument;
  * and the names themselves: a string a translation has and the default does not is read by nothing, so
    it is either a word that stays English (a typo in the name) or, if it lands on another key's name, a
    correct string replaced by the wrong text.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "launcher" / "app" / "src" / "main" / "res"
JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java"

COMMENT = re.compile(r"<!--.*?-->", re.S)
STRING = re.compile(r"<string\b([^>]*)>(.*?)</string>", re.S)
PLURALS = re.compile(r"<plurals\b([^>]*)>(.*?)</plurals>", re.S)
ITEM = re.compile(r"<item\b([^>]*)>(.*?)</item>", re.S)
ATTRIBUTE = re.compile(r'\b([a-z]+)="([^"]*)"')
# %1$s, %s, %1$.1f, %1$d%% - group 1 is the argument index, group 2 the conversion letter.
SPECIFIER = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z%])")
# getString(R.string.x, ...), res.getString(...), ctx.getString(...), getQuantityString(R.plurals.x, n, ...)
# group 2 is `string` or `plurals`, group 3 the name, group 4 the word after it (',' with arguments, ')' without).
CALL = re.compile(r"\b(?:getString|getQuantityString)\(\s*(R\.(string|plurals)\.([a-z0-9_]+))\s*([,)])")


def attribute(attrs, name):
    """The value of `name` in a tag's attributes, or None."""
    for key, value in ATTRIBUTE.findall(attrs):
        if key == name:
            return value
    return None


def catalog(path):
    """({name: body}, {(plurals name, quantity): body}) for one strings.xml, XML comments aside."""
    text = COMMENT.sub("", Path(path).read_text(encoding="utf-8"))
    strings = {}
    for match in STRING.finditer(text):
        name = attribute(match.group(1), "name")
        if name:
            strings[name] = match.group(2)
    plurals = {}
    for block in PLURALS.finditer(text):
        name = attribute(block.group(1), "name")
        if not name:
            continue
        for item in ITEM.finditer(block.group(2)):
            quantity = attribute(item.group(1), "quantity")
            if quantity:
                plurals[(name, quantity)] = item.group(2)
    return strings, plurals


def specifiers(body):
    """[(argument position, conversion letter, as written)] in the order Java reads them.

    Java's Formatter keeps its own counter for the specifiers written without an index (`%s`, `%d`), and an
    explicit index (`%2$s`) does not advance it, so the two are counted apart, as it does.
    """
    out, implicit = [], 0
    for match in SPECIFIER.finditer(body):
        index, conversion = match.group(1), match.group(2)
        if conversion == "%":
            continue                      # %% is a literal per cent sign, not an argument
        if index is None:
            implicit += 1
            out.append((implicit, conversion, match.group(0)))
        else:
            out.append((int(index), conversion, match.group(0)))
    return out


def arguments(body):
    """How many arguments a format string takes: the highest position any of its specifiers names."""
    return max((position for position, _, _ in specifiers(body)), default=0)


def specifier_problems(english, translated):
    """[why] for each specifier the translation names that the English string does not take."""
    takes = {}
    for position, conversion, _ in specifiers(english):
        takes.setdefault(position, conversion)
    highest = max(takes) if takes else 0
    out = []
    for position, conversion, written in specifiers(translated):
        if position not in takes:
            arguments_word = "argument" if highest == 1 else "arguments"
            out.append(f"names argument {position} ({written}) and the English string takes "
                       f"{highest} {arguments_word}")
        elif takes[position] != conversion:
            out.append(f"gives argument {position} as %{conversion} ({written}) where the English string "
                       f"takes %{takes[position]}")
    return out


def format_problems(res=RES):
    """[one line per translated string or plural item that states its arguments wrongly]."""
    default_strings, default_plurals = catalog(Path(res) / "values" / "strings.xml")
    plural_names = {name for name, _ in default_plurals}
    out = []
    for folder in sorted(p for p in Path(res).iterdir() if p.is_dir() and p.name.startswith("values-")):
        page = f"{folder.name}/strings.xml"
        if not (folder / "strings.xml").is_file():
            out.append(f"{page}: the folder has no strings.xml")
            continue
        strings, plurals = catalog(folder / "strings.xml")
        for name, body in sorted(strings.items()):
            if name not in default_strings:
                out.append(f"{page}: `{name}` is not a string values/strings.xml has, so nothing reads it "
                           "(a name typed wrong, or one that was renamed and left behind)")
                continue
            for why in specifier_problems(default_strings[name], body):
                out.append(f"{page}: `{name}` {why}, and values/strings.xml says "
                           f"{default_strings[name].strip()!r}")
        for (name, quantity), body in sorted(plurals.items()):
            if name not in plural_names:
                out.append(f"{page}: the plurals `{name}` is not one values/strings.xml has, so nothing "
                           "reads it")
                continue
            if (name, quantity) not in default_plurals:
                continue                  # a quantity this language's plural rules need and English has not
            for why in specifier_problems(default_plurals[(name, quantity)], body):
                out.append(f"{page}: the `{quantity}` item of `{name}` {why}, and values/strings.xml says "
                           f"{default_plurals[(name, quantity)].strip()!r}")
    return out


def handed(body, start):
    """How many arguments the call whose '(' is just before `start` hands, nesting counted once."""
    depth, i, commas, content = 1, start, 0, False
    while i < len(body) and depth:
        char = body[i]
        if char in "([{":
            depth += 1
        elif char in ")]}":
            depth -= 1
        elif char == "," and depth == 1:
            commas += 1
        elif depth == 1 and not char.isspace():
            content = True
        i += 1
    return commas + (1 if content else 0)


def call_sites(root=JAVA):
    """[(file, 'string' or 'plurals', name, arguments handed to the formatter)] for every call.

    A call's own words are the resource id first, and for a plural the quantity second; neither reaches
    Java's Formatter, so both are taken off here and what is left is what the format string must fit.
    """
    out = []
    for path in sorted(Path(root).rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        for match in CALL.finditer(text):
            kind, name, closer = match.group(2), match.group(3), match.group(4)
            # The arguments start after the '(' that follows the call's own name.
            opened = text.index("(", match.start())
            written = 0 if closer == ")" else handed(text, opened + 1)
            out.append((path.relative_to(Path(root)).as_posix(), kind, name,
                        max(written - (2 if kind == "plurals" else 1), 0)))
    return out


def call_problems(res=RES, java=JAVA):
    """[one line per call that hands fewer arguments than the string it formats names]."""
    strings, plurals = catalog(Path(res) / "values" / "strings.xml")
    takes = {name: arguments(body) for name, body in strings.items()}
    plural_takes, plural_bodies = {}, {}
    for (name, _), body in plurals.items():
        if arguments(body) >= plural_takes.get(name, 0):
            plural_takes[name] = arguments(body)
            plural_bodies[name] = body
    out = []
    for where, kind, name, given in call_sites(java):
        if kind == "string":
            if name not in takes or takes[name] == 0:
                continue                  # read from config.xml, or a string with nothing to format
            wanted, call, source = takes[name], f"getString(R.string.{name}, ...)", strings[name]
        else:
            if name not in plural_takes or plural_takes[name] == 0:
                continue
            wanted = plural_takes[name]
            call, source = f"getQuantityString(R.plurals.{name}, ...)", plural_bodies[name]
        if given < wanted:
            out.append(f"{where}: {call} hands {given} argument{'s' if given != 1 else ''} and "
                       f"values/strings.xml's `{name}` takes {wanted} ({source.strip()!r}): Java's "
                       "Formatter throws on the line that draws it")
    return out


class ShippedStringsTest(unittest.TestCase):
    def test_every_translation_carries_the_specifiers_the_english_string_takes(self):
        problems = format_problems()
        self.assertEqual([], problems,
                         "a translated string asks Java's Formatter for arguments the launcher does not "
                         "hand it, which throws on a TV set to that language:\n  " + "\n  ".join(problems))

    def test_every_call_hands_the_arguments_the_string_it_formats_takes(self):
        problems = call_problems()
        self.assertEqual([], problems,
                         "a getString call hands fewer arguments than its string names, so the line that "
                         "draws it throws:\n  " + "\n  ".join(problems))

    def test_the_readers_read_the_shipped_tree(self):
        # Readers that found nothing would make both checks above pass for anything, so this asks them
        # for content - and for the one relation below, without pinning any string's own text: a rewrite
        # of a sentence or a call that moves its arguments together stays green.
        default_strings, default_plurals = catalog(RES / "values" / "strings.xml")
        self.assertGreaterEqual(len(default_strings), 140, "values/strings.xml was not read")
        self.assertTrue(default_plurals, "the plurals were not read")
        self.assertTrue(any(specifiers(body) for body in default_strings.values()),
                        "no specifier was read out of values/strings.xml")
        folders = [p for p in RES.iterdir() if p.is_dir() and p.name.startswith("values-")]
        self.assertGreaterEqual(len(folders), 30, "the language folders were not read")
        pairs = with_specifiers = 0
        for folder in folders:
            strings, _ = catalog(folder / "strings.xml")
            for name, body in strings.items():
                if name in default_strings:
                    pairs += 1
                    if specifiers(default_strings[name]):
                        with_specifiers += 1
        self.assertGreaterEqual(pairs, 3000, "the translated strings were not read")
        self.assertGreaterEqual(with_specifiers, 500, "the strings with specifiers were not found")
        calls = call_sites()
        self.assertGreaterEqual(len(calls), 40, "the call sites were not read")
        # The call reader has to count arguments, not only find calls: several calls in the launcher hand
        # two or three, and a reader that stopped at the resource id would report none of them.
        self.assertGreaterEqual(len([1 for _, _, _, given in calls if given >= 2]), 3,
                                "no call was read as handing the string it formats two arguments")


class TranslationReaderTest(unittest.TestCase):
    """The same readers over a tree written here, so a reader that stops checking fails these."""

    def tree(self, english, translated, plurals=None):
        root = Path(tempfile.mkdtemp())
        for folder, strings in (("values", english), ("values-ru", translated)):
            (root / folder).mkdir()
            body = "".join(f'  <string name="{n}">{b}</string>\n' for n, b in strings.items())
            if plurals and folder in plurals:
                body += plurals[folder]
            (root / folder / "strings.xml").write_text(f"<resources>\n{body}</resources>\n", encoding="utf-8")
        return root

    def test_a_conversion_letter_that_moved_is_reported(self):
        root = self.tree({"race_kicker": "Round %1$d · %2$s"}, {"race_kicker": "Гонка %1$s · %2$s"})
        problems = format_problems(root)
        self.assertEqual(1, len(problems), problems)
        self.assertIn("gives argument 1 as %s (%1$s) where the English string takes %d", problems[0])

    def test_an_argument_the_english_string_does_not_take_is_reported(self):
        root = self.tree({"race_next": "%1$s in %2$s"}, {"race_next": "%1$s через %2$s (%3$s)"})
        problems = format_problems(root)
        self.assertEqual(1, len(problems), problems)
        self.assertIn("names argument 3 (%3$s) and the English string takes 2 arguments", problems[0])

    def test_reordered_indices_are_left_alone(self):
        root = self.tree({"race_next": "%1$s in %2$s"}, {"race_next": "%2$s, затем %1$s"})
        self.assertEqual([], format_problems(root))

    def test_a_dropped_specifier_is_left_alone(self):
        root = self.tree({"stats_memory": "Memory %1$d / %2$d MB"}, {"stats_memory": "Память %1$d MB"})
        self.assertEqual([], format_problems(root))

    def test_a_literal_per_cent_is_not_an_argument(self):
        root = self.tree({"stats_cpu": "CPU %1$d%%"}, {"stats_cpu": "ЦП %1$d%%"})
        self.assertEqual([], format_problems(root))
        self.assertEqual([(1, "d", "%1$d")], specifiers("CPU %1$d%%"))
        self.assertEqual(1, arguments("CPU %1$d%%"))

    def test_a_precision_and_a_width_keep_their_letter(self):
        root = self.tree({"storage_gb_decimal": "%1$.1f GB"}, {"storage_gb_decimal": "%1$.2f ГБ"})
        self.assertEqual([], format_problems(root))
        self.assertEqual([(1, "f", "%1$.1f")], specifiers("%1$.1f GB"))

    def test_a_specifier_in_an_xml_comment_is_not_read(self):
        root = self.tree({"race_next": "%1$s in %2$s"}, {"race_next": "%1$s через %2$s"})
        page = root / "values-ru" / "strings.xml"
        page.write_text(page.read_text(encoding="utf-8").replace(
            "</resources>", "  <!-- was %1$s after %2$s and %3$s -->\n</resources>\n"), encoding="utf-8")
        self.assertEqual([], format_problems(root))

    def test_a_name_the_default_does_not_have_is_reported(self):
        root = self.tree({"race_next": "%1$s in %2$s"}, {"race_nex": "%1$s через %2$s"})
        problems = format_problems(root)
        self.assertEqual(1, len(problems), problems)
        self.assertIn("`race_nex` is not a string values/strings.xml has", problems[0])

    def test_a_string_the_translation_leaves_out_is_left_alone(self):
        root = self.tree({"race_next": "%1$s in %2$s", "race_live": "%1$s · live"},
                         {"race_next": "%1$s через %2$s"})
        self.assertEqual([], format_problems(root))

    def test_a_plural_item_is_read_and_its_own_quantities_left_alone(self):
        english = ('  <plurals name="library_app_count">\n'
                   '    <item quantity="one">%1$d app</item>\n'
                   '    <item quantity="other">%1$d apps</item>\n  </plurals>\n')
        russian = ('  <plurals name="library_app_count">\n'
                   '    <item quantity="one">%1$d приложение</item>\n'
                   '    <item quantity="few">%1$d приложения</item>\n'
                   '    <item quantity="other">%1$s приложения</item>\n  </plurals>\n')
        root = self.tree({}, {}, plurals={"values": english, "values-ru": russian})
        problems = format_problems(root)
        self.assertEqual(1, len(problems), problems)
        self.assertIn("the `other` item of `library_app_count` gives argument 1 as %s (%1$s)", problems[0])


class CallSiteReaderTest(unittest.TestCase):
    """The same readers over Java written here: the arguments handed must not fall short."""

    def tree(self, java):
        root = Path(tempfile.mkdtemp())
        (root / "a").mkdir()
        (root / "a" / "Main.java").write_text(java, encoding="utf-8")
        return root

    def problems(self, java, english):
        root = self.tree(java)
        res = Path(tempfile.mkdtemp())
        (res / "values").mkdir()
        (res / "values" / "strings.xml").write_text(
            "<resources>\n" + "".join(f'  <string name="{n}">{b}</string>\n' for n, b in english.items())
            + '  <plurals name="apps">\n    <item quantity="other">%1$d apps</item>\n  </plurals>\n'
            + "</resources>\n", encoding="utf-8")
        return call_problems(res, root)

    def test_a_call_one_argument_short_is_reported(self):
        problems = self.problems("void f() { t.setText(getString(R.string.kicker, round)); }\n",
                                 {"kicker": "Round %1$d · %2$s · %3$s"})
        self.assertEqual(1, len(problems), problems)
        self.assertIn("getString(R.string.kicker, ...) hands 1 argument and values/strings.xml's `kicker` "
                      "takes 3", problems[0])

    def test_a_call_with_every_argument_is_quiet(self):
        self.assertEqual([], self.problems(
            "void f() { t.setText(getString(R.string.kicker, round, shortName, place)); }\n",
            {"kicker": "Round %1$d · %2$s · %3$s"}))

    def test_a_call_with_more_arguments_than_the_string_takes_is_quiet(self):
        self.assertEqual([], self.problems(
            "void f() { t.setText(getString(R.string.kicker, round, shortName)); }\n",
            {"kicker": "Round %1$d"}))

    def test_a_context_call_and_a_nested_argument_are_counted(self):
        self.assertEqual([], self.problems(
            "void f() { t.setText(ctx.getString(R.string.names, card.label, name(card.pkg, 1))); }\n",
            {"names": "The card is '%1$s'. The app is '%2$s'."}))

    def test_a_plural_call_hands_the_quantity_besides_the_string_arguments(self):
        # getQuantityString's quantity picks the item, it does not reach the Formatter: a call that passes
        # only the quantity hands the format string nothing.
        problems = self.problems(
            "void f() { t.setText(getQuantityString(R.plurals.apps, total)); }\n", {})
        self.assertEqual(1, len(problems), problems)
        self.assertIn("getQuantityString(R.plurals.apps, ...) hands 0 arguments and values/strings.xml's "
                      "`apps` takes 1", problems[0])
        self.assertEqual([], self.problems(
            "void f() { t.setText(getQuantityString(R.plurals.apps, total, total)); }\n", {}))

    def test_the_call_sites_are_read_with_their_file(self):
        sites = call_sites(self.tree("void f() { getString(R.string.a, 1, 2); }\n"))
        self.assertEqual([("a/Main.java", "string", "a", 2)], sites)


if __name__ == "__main__":
    unittest.main()
