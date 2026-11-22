"""tools/trace.py's shell adapter must accept every call tools/bench.py makes on a TV.

bench.py calls tv.shell(..., retry=True). trace.py hands bench its _Shell adapter for the
accessibility helpers (cold, coldsys and any recording that force-stops), so an adapter without
`retry` crashes those recordings with a TypeError. tests/test_trace.py fakes bench, so the real
adapter and the real bench only meet here.
"""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


def _load(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / "tools" / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


trace = _load("cyt_trace_shell", "trace.py")
bench = _load("cyt_bench_shell", "bench.py")

PACKAGE = "com.example.app"
HELPER = "com.example.app/.HomeKeyService"
SETTING_GET = "settings get secure enabled_accessibility_services"


class TraceShellWithBenchTest(unittest.TestCase):
    def test_accessibility_services_reads_the_helpers(self):
        sent = []

        def run(cmd):
            sent.append(cmd)
            return f"{HELPER}:com.other/.Svc"

        services = bench.accessibility_services(trace._Shell(run), PACKAGE)
        self.assertEqual(services, [HELPER])
        self.assertEqual(sent, [SETTING_GET])

    def test_restore_accessibility_runs_without_a_type_error(self):
        sent = []

        def run(cmd):
            sent.append(cmd)
            if cmd == SETTING_GET:
                return HELPER
            if cmd == "settings get secure accessibility_enabled":
                return "0"
            return ""

        bench.restore_accessibility(trace._Shell(run), PACKAGE, [HELPER])
        self.assertEqual(sent, [SETTING_GET, "settings get secure accessibility_enabled"])


if __name__ == "__main__":
    unittest.main()
