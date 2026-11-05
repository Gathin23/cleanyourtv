"""bench.py passes its selected host and port to tools/tv.py's client.

A local tv.py records its environment and returns no Home component, so the CLI stops
before measuring anything. No TV, network connection or ADB key is needed.
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]


class BenchTargetTest(unittest.TestCase):
    def run_bench(self, args, target_env=None):
        env = os.environ.copy()
        for key in ("TV_HOST", "TV_PORT"):
            env.pop(key, None)
        env.update(target_env or {})
        env["BENCH_TARGET_TEST"] = "keep me"
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            shutil.copyfile(ROOT / "tools" / "bench.py", folder / "bench.py")
            # bench.py imports tv.py and calls connect(); this one records the target and answers nothing.
            (folder / "tv.py").write_text(
                "import json, os\n"
                "from pathlib import Path\n"
                "HOST = PORT = None\n"
                "class Device:\n"
                "    def shell(self, command, read_timeout_s=None, decode=True):\n"
                "        return b''\n"
                "    def close(self):\n"
                "        pass\n"
                "def connect():\n"
                "    target = {key: os.environ.get(key) for key in "
                "('TV_HOST', 'TV_PORT', 'BENCH_TARGET_TEST')}\n"
                "    Path(__file__).with_name('target.json').write_text(json.dumps(target))\n"
                "    return Device()\n",
                encoding="utf-8")
            result = subprocess.run([sys.executable, str(folder / "bench.py"), *args],
                                    cwd=tmp, env=env, capture_output=True, text=True, timeout=10)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("no component answers HOME on this TV", result.stderr)
            target = json.loads((folder / "target.json").read_text(encoding="utf-8"))
        self.assertEqual(target.pop("BENCH_TARGET_TEST"), "keep me")
        return target

    def test_host_option_without_environment_uses_default_port(self):
        target = self.run_bench(["--host", "192.0.2.20"])
        self.assertEqual(target, {"TV_HOST": "192.0.2.20", "TV_PORT": "5555"})

    def test_options_override_environment(self):
        target = self.run_bench(["--host", "127.0.0.1", "--port", "15555"],
                                {"TV_HOST": "192.0.2.10", "TV_PORT": "5555"})
        self.assertEqual(target, {"TV_HOST": "127.0.0.1", "TV_PORT": "15555"})

    def test_environment_defaults_reach_child(self):
        target = self.run_bench([], {"TV_HOST": "192.0.2.20", "TV_PORT": "15555"})
        self.assertEqual(target, {"TV_HOST": "192.0.2.20", "TV_PORT": "15555"})

    def test_port_option_keeps_environment_host(self):
        target = self.run_bench(["--port", "15555"],
                                {"TV_HOST": "127.0.0.1", "TV_PORT": "5555"})
        self.assertEqual(target, {"TV_HOST": "127.0.0.1", "TV_PORT": "15555"})

    def test_host_option_keeps_environment_port(self):
        target = self.run_bench(["--host", "192.0.2.20"],
                                {"TV_HOST": "192.0.2.10", "TV_PORT": "15555"})
        self.assertEqual(target, {"TV_HOST": "192.0.2.20", "TV_PORT": "15555"})


if __name__ == "__main__":
    unittest.main()
