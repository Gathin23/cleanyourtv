"""tools/tv.py takes a TV_HOST with spaces around it, and refuses one with no address before the port.

An address copied off a page or out of a file often carries a trailing space or newline. Before this,
`TV_HOST="192.168.1.50 "` reached the socket as a host name and the run answered "could not reach
192.168.1.50 :5555 ([Errno -2] Name or service not known). Is the TV on and on this network?". And
`TV_HOST=:5555` (the IP lost from a variable) was the same sentence for the host "". Both are about the
argument, not the TV, so the first is now read as the address it holds and the second is refused with 2
before anything reaches the network.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import unittest

from test_tv_address_words import load_tv, refused


class TvHostBlankPartsTest(unittest.TestCase):
    def test_spaces_and_a_newline_around_the_address_are_dropped(self):
        for host in ("192.168.1.50 ", " 192.168.1.50", "192.168.1.50\n", " 192.168.1.50:5877 "):
            with self.subTest(host=host):
                tv = load_tv({"TV_HOST": host})
                self.assertEqual(tv.HOST, "192.168.1.50")
                self.assertEqual(tv.PORT, 5877 if "5877" in host else 5555)

    def test_a_port_with_no_address_is_refused_before_connecting(self):
        for host in (":5555", "[]:5555", " :5555"):
            with self.subTest(host=host):
                printed, status = refused(host)
                self.assertEqual(status, 2, printed)
                self.assertIn("no address before the port", printed)

    def test_a_host_of_only_spaces_is_asked_for_like_an_unset_one(self):
        printed, status = refused("   ")
        self.assertEqual(status, 1)
        self.assertIn("Set TV_HOST", printed)


if __name__ == "__main__":
    unittest.main()
