"""A run's column is named after its file, whole, whatever the file is called."""
import unittest

from tools import trace_report


def no_trace(path):
    return (lambda sql: []), (lambda: None)


class RunNameTest(unittest.TestCase):
    def names(self, *paths):
        return [name for name, _ in trace_report.measure(list(paths), "com.gathin.tvhome", no_trace)]

    def test_pftrace_drops_the_extension(self):
        self.assertEqual(self.names("traces/home-1.pftrace"), ["home-1"])

    def test_a_file_named_otherwise_keeps_its_name(self):
        # A trace passed by name need not end in .pftrace; cutting eight characters off
        # "home-1.trace" left "home", and two runs could share one column name.
        self.assertEqual(self.names("a/home-1.trace", "a/home-2.trace"), ["home-1.trace", "home-2.trace"])


if __name__ == "__main__":
    unittest.main()
