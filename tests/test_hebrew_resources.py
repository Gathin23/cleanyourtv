"""Guard the Hebrew Home layout's required views."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


RES = Path(__file__).resolve().parents[1] / "launcher/app/src/main/res"
ANDROID_ID = "{http://schemas.android.com/apk/res/android}id"


def views(folder):
    return {v.attrib[ANDROID_ID]: v.tag
            for v in ET.parse(RES / folder / "activity_main.xml").iter()
            if ANDROID_ID in v.attrib}


class HebrewResourcesTest(unittest.TestCase):
    def test_hebrew_strings_cover_default_and_keep_format_arguments(self):
        default = ET.parse(RES / "values/strings.xml").getroot()
        hebrew = {v.attrib["name"]: v for v in ET.parse(RES / "values-iw/strings.xml").getroot()}
        placeholders = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]")
        for value in default:
            if value.tag not in ("string", "plurals") or value.get("translatable") == "false":
                continue
            name = value.attrib["name"]
            with self.subTest(string=name):
                self.assertIn(name, hebrew)
                if value.tag == "plurals" and name in hebrew:
                    default_other = value.find("item[@quantity='other']")
                    translated_other = hebrew[name].find("item[@quantity='other']")
                    self.assertIsNotNone(translated_other)
                    if translated_other is not None:
                        self.assertEqual(sorted(placeholders.findall(default_other.text or "")),
                                         sorted(placeholders.findall(translated_other.text or "")))
                elif name in hebrew:
                    self.assertEqual(sorted(placeholders.findall(value.text or "")),
                                     sorted(placeholders.findall(hebrew[name].text or "")))

    def test_hebrew_home_keeps_all_default_view_ids_and_types(self):
        default, hebrew = views("layout"), views("layout-iw")
        for view_id, tag in default.items():
            with self.subTest(view=view_id):
                self.assertEqual(tag, hebrew.get(view_id))


if __name__ == "__main__":
    unittest.main()
