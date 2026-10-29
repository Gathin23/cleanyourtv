"""The calendar's Hebrew controls must stay complete as its English UI evolves."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

RES = Path(__file__).resolve().parents[1] / "addons/jewish-calendar/app/src/main/res"

class CalendarTranslationsTest(unittest.TestCase):
    def test_hebrew_has_every_control_and_greeting(self):
        for file in ("strings.xml", "jewish_greetings.xml"):
            default = ET.parse(RES / "values" / file).getroot()
            hebrew = {value.get("name"): value for value in ET.parse(RES / "values-iw" / file).getroot()}
            for value in default:
                with self.subTest(resource=value.get("name")):
                    self.assertIn(value.get("name"), hebrew)
                    if value.tag == "string-array":
                        self.assertEqual(len(value), len(hebrew[value.get("name")]))
                    else:
                        self.assertTrue(hebrew[value.get("name")].text)
