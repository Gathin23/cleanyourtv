"""tools/relay.py reads an address with spaces around it, and refuses a port with no address.

Before this, `relay.py " 192.168.1.50"` and `relay.py :5555` both started: the relay printed
"relay 127.0.0.1:15555 -> :5555" as if it were up, and only a client that connected was told
"cannot reach :5555 ... (is the TV on, and network debugging still on?)". The first is now the
address it holds, and the second is refused with 2 before anything is bound.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import unittest

from test_relay_address_words import env_with, forwards_to, refused


class RelayBlankHostTest(unittest.TestCase):
    def test_a_port_with_no_address_is_refused(self):
        for word in (":5555", "[]:5555"):
            with self.subTest(word=word):
                result = refused(word, "--port", "0")
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertIn("no address before the port", result.stderr)
                self.assertEqual(result.stdout, "")

    def test_spaces_around_the_address_are_dropped(self):
        for word in (" 192.0.2.7", "192.0.2.7\n", " 192.0.2.7:5877 "):
            with self.subTest(word=word):
                line = forwards_to(word, "--port", "0")
                want = "192.0.2.7:5877" if "5877" in word else "192.0.2.7:5555"
                self.assertTrue(line.rstrip().endswith("-> " + want), line)

    def test_a_tv_host_of_spaces_is_asked_for(self):
        result = refused("--port", "0", env=env_with(TV_HOST="  "))
        self.assertEqual(result.returncode, 2)
        self.assertIn("give the TV's IP address", result.stderr)


if __name__ == "__main__":
    unittest.main()
