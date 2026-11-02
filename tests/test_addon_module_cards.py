"""Every module card the add-ons ship is one the launcher will accept.

`docs/specs/card.md` is the contract an app declares its card against, and `Cards.java` refuses a
card whole: one character outside the set a field allows and `parse()` throws, `read()` catches it per
card and logs it under `TvHomeCards`, and the app falls back to its ordinary tile. Nothing in the
repository read the cards the tree itself ships - `grep -rn module_card tests/` found the string in
two files and neither opens one - so addons/bluetooth-connect's caption, "Connect / disconnect",
carried a slash, which no caption may use:

    com.<pkg>: caption has characters outside A-Z a-z 0-9 space . & + ' -

and the card its README promises ("its tile reads \\"Bluetooth\\" with a teal glow") never appeared on
a TV, with nothing anywhere to notice. `docs/modules.md` - the how-to a card author follows - gave
the label's characters and said only "up to 32 characters" for the caption, so the how-to produced
that caption.

The limits and the alphabet are read out of `Cards.java` instead of being copied in, and the two
lengths are read out of the spec's own table, so a card this test passes is one the launcher takes,
and the how-to and the contract are held to the same numbers.

Half of the contract was still read by nothing: the card file can be perfect and the launcher never
reaches it. `docs/specs/card.md`'s "Rules for the declaration" say the filter and the `meta-data` sit
on the activity that already carries `LEANBACK_LAUNCHER`, that it is `exported` and enabled, and that a
package declares one card - and `Cards.read()` refuses all of that in words a TV owner never sees
(`card activity is not exported and enabled`, `card activity has no LEANBACK_LAUNCHER intent filter`,
`no <meta-data android:name="com.gathin.tvhome.card" android:resource=...>`, `declares a second card`),
one `TvHomeCards` line per Home. So an add-on could ship the card file this file reads, point its
`meta-data` at another activity, drop `android:exported`, or leave the `DOCK_CARD` filter off the
activity the launcher queries and the card would never appear - with every test here green. This file
now reads each add-on's `AndroidManifest.xml` beside its card and holds the declaration to those rules,
so a folder whose manifest and card disagree is named before a TV owner finds it.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ADDONS = ROOT / "addons"
CARDS_JAVA = ROOT / "launcher/app/src/main/java/com/gathin/tvhome/Cards.java"
SPEC = ROOT / "docs" / "specs" / "card.md"

FIELDS = ("spec", "label", "caption", "accent")

# The three names Cards.discover() and Cards.read() look for, in the manifest rather than in the card.
MANIFEST_ANDROID = "{http://schemas.android.com/apk/res/android}"
CARD_META_DATA = "com.gathin.tvhome.card"          # Cards.META_DATA
DOCK_CARD = "com.gathin.tvhome.category.DOCK_CARD"  # Cards.CATEGORY
MAIN_ACTION = "android.intent.action.MAIN"
LEANBACK_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER"
# The elements PackageManager may hand back from its MAIN + DOCK_CARD query: an <activity> and the
# <activity-alias> that stands for one. A meta-data anywhere else is never read.
ACTIVITY_TAGS = ("activity", "activity-alias")


def card_files(root=ADDONS):
    """Every module card the add-ons in `root` ship, newest path order."""
    return sorted(Path(root).glob("*/app/src/main/res/xml/module_card.xml"))


def java_constants(path=CARDS_JAVA):
    """The limits, the alphabet and the reserved names, read out of Cards.java.

    A copy of these numbers here would be a second place to keep in step; the launcher's own
    constants are what a card is judged by.
    """
    text = Path(path).read_text(encoding="utf-8")

    def number(name):
        m = re.search(r"\b%s\s*=\s*(\d+)\s*;" % name, text)
        assert m, "%s is not a number in Cards.java" % name
        return int(m.group(1))

    def string(name):
        m = re.search(r'\b%s\s*=\s*"([^"]*)"\s*;' % name, text)
        assert m, "%s is not a string in Cards.java" % name
        return m.group(1)

    m = re.search(r"RESERVED_ANYWHERE\s*=\s*\{([^}]*)\}", text)
    assert m, "RESERVED_ANYWHERE is not a literal array in Cards.java"
    anywhere = tuple(re.findall(r'"([^"]+)"', m.group(1)))

    m = re.search(r"RESERVED_EXACT\s*=\s*new HashSet<>\(Arrays\.asList\(([^)]*)\)", text)
    assert m, "RESERVED_EXACT is not an inline list in Cards.java"
    exact = set(re.findall(r'"([^"]+)"', m.group(1)))

    return {
        "spec": number("SPEC_SUPPORTED"),
        "label_max": number("LABEL_MAX"),
        "caption_max": number("CAPTION_MAX"),
        "punctuation": string("TEXT_PUNCTUATION"),
        "anywhere": anywhere,
        "exact": exact,
        "prefix": string("RESERVED_PREFIX"),
    }


def spec_lengths(path=SPEC):
    """(label, caption) maxima as the contract's Fields table states them."""
    text = Path(path).read_text(encoding="utf-8")
    label = re.search(r"^\|\s*`label`\s*\|[^|]*\|\s*1 to (\d+) characters", text, re.M)
    caption = re.search(r"^\|\s*`caption`\s*\|[^|]*\|[^|]*?Up to (\d+) characters", text, re.M)
    assert label, "the spec's Fields table states no label length"
    assert caption, "the spec's Fields table states no caption length"
    return int(label.group(1)), int(caption.group(1))


def fold(value):
    """The folded form Cards.fold() compares: lower case, letters and digits only."""
    return re.sub(r"[^a-z0-9]", "", value.lower())


def is_text(value, punctuation):
    """Whether every character is one a label or caption may hold: ASCII letter, digit, space, punctuation."""
    return all(c.isascii() and (c.isalnum() or c == " " or c in punctuation) for c in value)


def read(path):
    """The root element of the card at `path`, or (None, [the reason XML refused it])."""
    try:
        return ET.parse(str(path)).getroot(), []
    except ET.ParseError as e:
        return None, ["it is not XML the resource compiler would take: %s" % e]


def problems(path, limits=None, lengths=None, labels=None):
    """Why the launcher would drop the card at `path`; [] when it takes it.

    `labels` maps a folded label to the (add-on, label) already using it, which is how a second card
    ends up dropped on the TV: two cards share a folded name and neither has an owner decision.
    """
    limits = limits or java_constants()
    lengths = lengths or spec_lengths()
    root, reasons = read(path)
    if root is None:
        return reasons

    addon = Path(path).parents[5].name   # addons/<name>/app/src/main/res/xml/module_card.xml
    if root.tag != "card":
        reasons.append("the root element is <%s>, and the launcher reads <card>" % root.tag)

    for name in root.attrib:
        if "}" in name:
            reasons.append("the attribute %s is in an XML namespace, and the launcher reads none" % name)
        elif name not in FIELDS:
            reasons.append("the attribute %r is not one the launcher reads (%s), so it is ignored "
                           "and the card shows without it" % (name, ", ".join(FIELDS)))

    attrs = {k: v for k, v in root.attrib.items() if "}" not in k}
    spec = attrs.get("spec")
    if spec != str(limits["spec"]):
        reasons.append("spec is %r, and this launcher reads spec %d"
                       % (spec, limits["spec"]))

    label = attrs.get("label")
    if not label:
        reasons.append("there is no label, and a card without one is dropped")
    else:
        folded = fold(label)
        if len(label) > lengths[0]:
            reasons.append("the label %r is longer than %d characters" % (label, lengths[0]))
        if not is_text(label, limits["punctuation"]):
            reasons.append("the label %r has characters outside A-Z a-z 0-9 space %s"
                           % (label, limits["punctuation"]))
        if label != label.strip():
            reasons.append("the label %r starts or ends with a space" % label)
        if not folded:
            reasons.append("the label %r has no letter or digit" % label)
        else:
            for word in limits["anywhere"]:
                if word in folded:
                    reasons.append("the label %r claims endorsement (%r)" % (label, word))
            if folded.startswith(limits["prefix"]) or folded in limits["exact"]:
                reasons.append("the label %r is a name the launcher's own UI uses" % label)
            if labels is not None:
                if folded in labels and labels[folded][0] != addon:
                    reasons.append("the label %r is also %s's, and two cards that share a folded "
                                   "name are dropped until an owner has decided one"
                                   % (label, labels[folded][0]))
                labels.setdefault(folded, (addon, label))

    caption = attrs.get("caption")
    if caption:
        if len(caption) > lengths[1]:
            reasons.append("the caption %r is longer than %d characters" % (caption, lengths[1]))
        if not is_text(caption, limits["punctuation"]):
            reasons.append("the caption %r has characters outside A-Z a-z 0-9 space %s"
                           % (caption, limits["punctuation"]))
        if caption != caption.strip():
            reasons.append("the caption %r starts or ends with a space" % caption)
        for word in limits["anywhere"]:
            if word in fold(caption):
                reasons.append("the caption %r claims endorsement (%r)" % (caption, word))

    accent = attrs.get("accent")
    if accent and not re.fullmatch(r"#[0-9A-Fa-f]{6}", accent):
        reasons.append("the accent %r is not #RRGGBB, so the card is dropped" % accent)
    return reasons


class ShippedCardsTest(unittest.TestCase):
    def test_the_walk_reads_the_cards_this_file_was_written_for(self):
        found = card_files()
        self.assertGreaterEqual(len(found), 25, "the add-ons ship fewer cards than this file knows about")
        names = {p.parents[5].name for p in found}
        self.assertIn("bluetooth-connect", names, "the walk no longer reaches the add-ons")
        self.assertIn("quiet-hours", names)

    def test_the_how_to_and_the_contract_state_the_launcher_s_limits(self):
        limits = java_constants()
        label, caption = spec_lengths()
        self.assertEqual(label, limits["label_max"],
                         "docs/specs/card.md lets a label be %d characters and Cards.java takes %d"
                         % (label, limits["label_max"]))
        self.assertEqual(caption, limits["caption_max"],
                         "docs/specs/card.md lets a caption be %d characters and Cards.java takes %d"
                         % (caption, limits["caption_max"]))

    def test_every_shipped_card_is_one_the_launcher_accepts(self):
        limits = java_constants()
        lengths = spec_lengths()
        labels = {}
        for path in card_files():
            with self.subTest(card=str(path.relative_to(ROOT))):
                self.assertEqual(problems(path, limits, lengths, labels), [])

    def test_every_shipped_card_has_a_label(self):
        for path in card_files():
            with self.subTest(card=str(path.relative_to(ROOT))):
                root, reasons = read(path)
                self.assertEqual(reasons, [])
                self.assertIsNotNone(root)
                self.assertTrue((root.attrib.get("label") or "").strip())


class CardReaderTest(unittest.TestCase):
    """The same readers over cards written here, one shape per test."""

    def check(self, card_xml, addon="demo", limits=None, lengths=None, labels=None):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / addon / "app/src/main/res/xml" / "module_card.xml"
            path.parent.mkdir(parents=True)
            path.write_text(card_xml, encoding="utf-8")
            return problems(path, limits, lengths, labels)

    def test_the_caption_the_tree_shipped_is_named(self):
        reasons = self.check('<card spec="1" label="Bluetooth" caption="Connect / disconnect" />',
                             addon="bluetooth-connect")
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("caption", reasons[0])
        self.assertIn("outside A-Z a-z 0-9 space", reasons[0])

    def test_a_caption_of_32_characters_is_taken_and_33_is_not(self):
        wait = "abcdefghij" * 3 + "ab"
        self.assertEqual(len(wait), 32)
        self.assertEqual(self.check('<card spec="1" label="Demo" caption="%s" />' % wait), [])
        self.assertEqual(len(self.check('<card spec="1" label="Demo" caption="%sx" />' % wait)), 1)

    def test_a_label_of_24_characters_is_taken_and_25_is_not(self):
        label = "abcdefghij" * 2 + "abcd"
        self.assertEqual(len(label), 24)
        self.assertEqual(self.check('<card spec="1" label="%s" />' % label), [])
        reasons = self.check('<card spec="1" label="%sx" />' % label)
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("longer than 24 characters", reasons[0])

    def test_a_spec_this_launcher_does_not_read_is_named(self):
        reasons = self.check('<card spec="2" label="Demo" />')
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("spec is '2'", reasons[0])

    def test_an_attribute_the_launcher_ignores_is_named(self):
        reasons = self.check('<card spec="1" label="Demo" capiton="Watch" />')
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("'capiton'", reasons[0])
        self.assertIn("ignored", reasons[0])

    def test_an_accent_that_is_not_rrggbb_is_named(self):
        self.assertEqual(self.check('<card spec="1" label="Demo" accent="#E8A33D" />'), [])
        reasons = self.check('<card spec="1" label="Demo" accent="E8A33D" />')
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("not #RRGGBB", reasons[0])

    def test_a_reserved_label_is_named(self):
        for label in ("Home", "HDMI 2", "Live TV", "cleanyourtv"):
            with self.subTest(label=label):
                reasons = self.check('<card spec="1" label="%s" />' % label)
                self.assertEqual(len(reasons), 1, reasons)
                self.assertIn("the launcher's own UI uses", reasons[0])

    def test_an_endorsement_word_is_named_in_the_label_and_the_caption(self):
        reasons = self.check('<card spec="1" label="Official Retro" />')
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("endorsement", reasons[0])
        reasons = self.check('<card spec="1" label="Retro" caption="Verified list" />')
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("endorsement", reasons[0])

    def test_two_add_ons_sharing_a_label_are_named(self):
        labels = {}
        self.assertEqual(self.check('<card spec="1" label="Retro" />', addon="one", labels=labels), [])
        reasons = self.check('<card spec="1" label="RETRO" />', addon="two", labels=labels)
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("also one's", reasons[0])

    def test_a_root_that_is_not_card_is_named(self):
        reasons = self.check('<module spec="1" label="Demo" />')
        self.assertIn("the root element is <module>", reasons[0])

    def test_a_card_that_is_not_xml_is_named(self):
        reasons = self.check('<card spec="1" label="Demo"')
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("not XML", reasons[0])

    def test_a_card_in_step_is_quiet(self):
        self.assertEqual(self.check('<card spec="1" label="Retro" caption="NES games" accent="#E8A33D" />'), [])


# ---- the declaration the card hangs off ----------------------------------------------------------

def manifest_of(card_path):
    """The AndroidManifest.xml of the app that ships the card: addons/<name>/app/src/main/."""
    return Path(card_path).parents[2] / "AndroidManifest.xml"


def intent_filters(element):
    """(actions, categories) for each <intent-filter> directly under `element`."""
    found = []
    for f in element.findall("intent-filter"):
        actions = {a.get(MANIFEST_ANDROID + "name") for a in f.findall("action")}
        categories = {c.get(MANIFEST_ANDROID + "name") for c in f.findall("category")}
        found.append((actions, categories))
    return found


def carries(element, action, category):
    """Whether one filter of `element` holds both the action and the category."""
    return any(action in actions and category in categories
               for actions, categories in intent_filters(element))


def card_carriers(application):
    """Every element under <application> that carries the card meta-data, in file order."""
    found = []
    for element in application:
        for meta in element.findall("meta-data"):
            if meta.get(MANIFEST_ANDROID + "name") == CARD_META_DATA:
                found.append(element)
                break
    return found


def named(element):
    """What a message should call an element: its android:name, or its tag when it has none."""
    return element.get(MANIFEST_ANDROID + "name") or "<%s>" % element.tag


def declaration_problems(card_path):
    """Why the launcher never reads the card at `card_path`, going by the manifest beside it.

    [] when the manifest declares it the way `docs/specs/card.md`'s "Rules for the declaration" and
    `Cards.read()` ask: the meta-data and the DOCK_CARD filter on the activity that also carries
    LEANBACK_LAUNCHER, that activity exported and enabled, and the resource this folder ships.
    """
    card_path = Path(card_path)
    manifest = manifest_of(card_path)
    where = manifest.relative_to(ROOT) if str(manifest).startswith(str(ROOT)) else manifest
    if not manifest.is_file():
        return ["%s is not there, so nothing installs the card" % where]
    try:
        root = ET.parse(str(manifest)).getroot()
    except ET.ParseError as e:
        return ["%s is not XML the build takes: %s" % (where, e)]
    application = root.find("application")
    if application is None:
        return ["%s has no <application>, so it declares no card" % where]

    reasons = []
    if application.get(MANIFEST_ANDROID + "enabled") == "false":
        reasons.append("the <application> is android:enabled=\"false\", and Cards.read() answers "
                       "'card activity is not exported and enabled'")
    for meta in application.findall("meta-data"):
        if meta.get(MANIFEST_ANDROID + "name") == CARD_META_DATA:
            reasons.append("the card meta-data sits on <application>, and Cards.read() loads it with "
                           "loadXmlMetaData() off the activity that answers the card query")

    carriers = card_carriers(application)
    wanted = "@xml/" + card_path.stem
    if not carriers:
        return reasons + ["no element carries <meta-data android:name=\"%s\">, so the card this folder "
                          "ships is never read" % CARD_META_DATA]
    if len(carriers) > 1:
        reasons.append("%d elements carry the card meta-data (%s), and Cards.read() answers "
                       "'declares a second card; one per package is read' for the second"
                       % (len(carriers), ", ".join(named(e) for e in carriers)))

    for element in carriers:
        name = named(element)
        if element.tag not in ACTIVITY_TAGS:
            reasons.append("<%s> %s carries the card meta-data, and PackageManager's card query only "
                           "returns activities" % (element.tag, name))
            continue
        if element.get(MANIFEST_ANDROID + "exported") != "true":
            reasons.append("%s is not android:exported=\"true\", and Cards.read() answers 'card activity "
                           "is not exported and enabled'" % name)
        if element.get(MANIFEST_ANDROID + "enabled") == "false":
            reasons.append("%s is android:enabled=\"false\", so Cards.read() answers 'card activity is "
                           "not exported and enabled'" % name)
        if not carries(element, MAIN_ACTION, DOCK_CARD):
            reasons.append("%s has no MAIN + %s intent-filter, and Cards.discover() finds a card by "
                           "querying exactly that" % (name, DOCK_CARD))
        if not carries(element, MAIN_ACTION, LEANBACK_CATEGORY):
            reasons.append("%s has no MAIN + %s intent-filter, so it is not a tile, and Cards.read() "
                           "answers 'card activity has no LEANBACK_LAUNCHER intent filter'"
                           % (name, LEANBACK_CATEGORY))
        for meta in element.findall("meta-data"):
            if meta.get(MANIFEST_ANDROID + "name") != CARD_META_DATA:
                continue
            resource = meta.get(MANIFEST_ANDROID + "resource")
            if resource is None:
                reasons.append("the meta-data on %s uses android:value=%r, and Cards.read() reads "
                               "android:resource only" % (name, meta.get(MANIFEST_ANDROID + "value")))
            elif resource != wanted:
                reasons.append("the meta-data on %s names %s, and this folder ships %s"
                               % (name, resource, wanted))

    # The other direction: a second activity that answers the card query with no meta-data is asked
    # for a card on every Home and logged against ('no <meta-data ...>'), and never shows one.
    for element in application:
        if element.tag not in ACTIVITY_TAGS or element in carriers:
            continue
        if carries(element, MAIN_ACTION, DOCK_CARD):
            reasons.append("%s carries MAIN + %s and no card meta-data, so it is queried for a card "
                           "every Home and answers with nothing" % (named(element), DOCK_CARD))
    return reasons


class ShippedDeclarationsTest(unittest.TestCase):
    """Every card the add-ons ship is reached by the launcher's query, not dropped on the way."""

    def test_the_walk_reads_the_manifests_this_file_was_written_for(self):
        read = []
        for path in card_files():
            manifest = manifest_of(path)
            with self.subTest(card=str(path.relative_to(ROOT))):
                self.assertTrue(manifest.is_file(), "%s has no AndroidManifest.xml" % manifest.parents[2])
            if manifest.is_file():
                read.append(path)
        self.assertGreaterEqual(len(read), 25, "the walk reached %d manifests" % len(read))

    def test_every_shipped_card_is_declared_on_a_tile_a_tv_can_open(self):
        for path in card_files():
            with self.subTest(card=str(path.relative_to(ROOT))):
                self.assertEqual(declaration_problems(path), [])


class DeclarationReaderTest(unittest.TestCase):
    """The same reader over manifests written here, one shape per test."""

    CARD = '<card spec="1" label="Demo" />'

    def check(self, manifest, card="module_card"):
        with tempfile.TemporaryDirectory() as tmp:
            addon = Path(tmp) / "demo"
            xml_folder = addon / "app/src/main/res/xml"
            xml_folder.mkdir(parents=True)
            (xml_folder / (card + ".xml")).write_text(self.CARD, encoding="utf-8")
            (addon / "app/src/main/AndroidManifest.xml").write_text(manifest, encoding="utf-8")
            return declaration_problems(xml_folder / (card + ".xml"))

    def manifest(self, activity, application_attrs="", extra=""):
        return ('<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
                '<application%s>%s%s</application></manifest>' % (application_attrs, activity, extra))

    TILE = ('<activity android:name=".Main" android:exported="true">'
            '<intent-filter><action android:name="android.intent.action.MAIN" />'
            '<category android:name="android.intent.category.LEANBACK_LAUNCHER" /></intent-filter>'
            '<intent-filter><action android:name="android.intent.action.MAIN" />'
            '<category android:name="com.gathin.tvhome.category.DOCK_CARD" /></intent-filter>'
            '<meta-data android:name="com.gathin.tvhome.card" android:resource="@xml/module_card" />'
            '</activity>')

    def test_the_shipped_shape_is_quiet(self):
        self.assertEqual(self.check(self.manifest(self.TILE)), [])

    def test_an_activity_that_is_not_exported_is_named(self):
        activity = self.TILE.replace(' android:exported="true"', "")
        reasons = self.check(self.manifest(activity))
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("is not android:exported", reasons[0])
        self.assertIn("not exported and enabled", reasons[0])

    def test_an_activity_that_is_disabled_is_named(self):
        activity = self.TILE.replace('android:exported="true"',
                                     'android:exported="true" android:enabled="false"')
        reasons = self.check(self.manifest(activity))
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn('android:enabled="false"', reasons[0])

    def test_a_disabled_application_is_named(self):
        reasons = self.check(self.manifest(self.TILE, ' android:enabled="false"'))
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("the <application> is android:enabled", reasons[0])

    def test_a_card_without_the_leanback_filter_is_named(self):
        activity = self.TILE.replace('<category android:name="android.intent.category.LEANBACK_LAUNCHER" />',
                                     "")
        reasons = self.check(self.manifest(activity))
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("no LEANBACK_LAUNCHER intent filter", reasons[0])

    def test_a_card_without_the_dock_card_filter_is_named(self):
        activity = self.TILE.replace('<category android:name="com.gathin.tvhome.category.DOCK_CARD" />', "")
        reasons = self.check(self.manifest(activity))
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("Cards.discover() finds a card by querying exactly that", reasons[0])

    def test_a_meta_data_on_another_activity_is_named(self):
        # The meta-data now sits on a second activity that is not the tile: the launcher reads it there
        # and drops the card, and the tile answers the card query with nothing.
        activity = self.TILE.replace(
            '<meta-data android:name="com.gathin.tvhome.card" android:resource="@xml/module_card" />', "")
        other = ('<activity android:name=".Settings"><meta-data android:name="com.gathin.tvhome.card" '
                 'android:resource="@xml/module_card" /></activity>')
        reasons = self.check(self.manifest(activity + other))
        self.assertTrue(any(".Settings has no MAIN + com.gathin.tvhome.category.DOCK_CARD" in r
                            for r in reasons), reasons)
        self.assertTrue(any(".Main carries" in r for r in reasons), reasons)

    def test_a_meta_data_that_names_another_resource_is_named(self):
        reasons = self.check(self.manifest(self.TILE.replace("@xml/module_card", "@xml/card")))
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("@xml/card", reasons[0])
        self.assertIn("@xml/module_card", reasons[0])

    def test_a_meta_data_that_uses_value_is_named(self):
        reasons = self.check(self.manifest(
            self.TILE.replace('android:resource="@xml/module_card"', 'android:value="@xml/module_card"')))
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("android:resource only", reasons[0])

    def test_a_card_on_a_service_is_named(self):
        activity = self.TILE.replace(
            '<meta-data android:name="com.gathin.tvhome.card" android:resource="@xml/module_card" />', "")
        reasons = self.check(self.manifest(activity + '<service android:name=".Sync">'
                                           '<meta-data android:name="com.gathin.tvhome.card" '
                                           'android:resource="@xml/module_card" /></service>'))
        self.assertEqual(len(reasons), 2, reasons)   # the tile now answers a card query with nothing
        self.assertTrue(any("only returns activities" in r for r in reasons), reasons)

    def test_a_second_card_in_one_package_is_named(self):
        extra = ('<activity android:name=".Second" android:exported="true"><intent-filter>'
                 '<action android:name="android.intent.action.MAIN" />'
                 '<category android:name="android.intent.category.LEANBACK_LAUNCHER" />'
                 '<category android:name="com.gathin.tvhome.category.DOCK_CARD" /></intent-filter>'
                 '<meta-data android:name="com.gathin.tvhome.card" android:resource="@xml/module_card" />'
                 '</activity>')
        reasons = self.check(self.manifest(self.TILE + extra))
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("one per package is read", reasons[0])

    def test_a_card_filter_with_no_card_is_named(self):
        extra = ('<activity android:name=".Settings" android:exported="true"><intent-filter>'
                 '<action android:name="android.intent.action.MAIN" />'
                 '<category android:name="com.gathin.tvhome.category.DOCK_CARD" /></intent-filter>'
                 '</activity>')
        reasons = self.check(self.manifest(self.TILE + extra))
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn(".Settings", reasons[0])
        self.assertIn("answers with nothing", reasons[0])

    def test_a_manifest_that_is_not_there_is_named(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp) / "addons/demo/app/src/main/res/xml"
            folder.mkdir(parents=True)
            card = folder / "module_card.xml"
            card.write_text(self.CARD, encoding="utf-8")
            reasons = declaration_problems(card)
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("nothing installs the card", reasons[0])


if __name__ == "__main__":
    unittest.main()
