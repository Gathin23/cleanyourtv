"""Every christmas_greetings string in launcher/app/src/main/res has the seven |-separated slots and the language they are in Festival.pick reads."""
import glob
import unittest
import xml.etree.ElementTree as ET

RES = "launcher/app/src/main/res/values*/strings.xml"


class ChristmasGreetingsTest(unittest.TestCase):
    def test_seven_slots_everywhere(self):
        found = 0
        for path in glob.glob(RES):
            for s in ET.parse(path).getroot().iter("string"):
                if s.get("name") == "christmas_greetings":
                    found += 1
                    self.assertEqual(7, (s.text or "").count("|"), path)
        self.assertGreater(found, 1)

    def test_default_is_english_so_untranslated_languages_are_told_apart_by_the_last_item(self):
        root = ET.parse("launcher/app/src/main/res/values/strings.xml").getroot()
        text = [s.text for s in root.iter("string") if s.get("name") == "christmas_greetings"][0]
        self.assertTrue(text.endswith("|en"), text)


if __name__ == "__main__":
    unittest.main()
