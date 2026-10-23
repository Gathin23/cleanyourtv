"""Guard the Hebrew Home layout's required views."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


RES = Path(__file__).resolve().parents[1] / "launcher/app/src/main/res"
ANDROID_ID = "{http://schemas.android.com/apk/res/android}id"


def views(folder):
    return {v.attrib[ANDROID_ID]: v.tag
            for v in ET.parse(RES / folder / "activity_main.xml").iter()
            if ANDROID_ID in v.attrib}


class HebrewResourcesTest(unittest.TestCase):
    def test_jewish_calendar_features_are_off_by_default(self):
        config = ET.parse(RES / "values/config.xml").getroot()
        for key in ("hebrew_date", "jewish_holiday_greetings"):
            self.assertEqual("false", config.find("bool[@name='" + key + "']").text)

    def test_jewish_greeting_translations_keep_the_same_order(self):
        lists = []
        for folder in ("values", "values-iw"):
            array = ET.parse(RES / folder / "jewish_greetings.xml").getroot().find("string-array")
            self.assertEqual("jewish_greetings", array.get("name"))
            lists.append([item.text for item in array])
        self.assertEqual(6, len(lists[0]))
        self.assertEqual(6, len(lists[1]))
        self.assertEqual("Shabbat shalom", lists[0][-1])
        self.assertEqual("שבת שלום", lists[1][-1])

    def test_hebrew_home_keeps_all_default_view_ids_and_types(self):
        default, hebrew = views("layout"), views("layout-iw")
        for view_id, tag in default.items():
            with self.subTest(view=view_id):
                self.assertEqual(tag, hebrew.get(view_id))


if __name__ == "__main__":
    unittest.main()
