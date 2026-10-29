"""tools/theme.sh must write through the launcher's ThemeProvider before falling back to cp.

On Android 14 the adb shell can write into Android/data, so `cp` succeeds - but the file it creates belongs
to the shell (uid 2000) and the launcher then fails with EACCES, so a theme pushed to a fresh install (or after
the launcher was reinstalled) was silently ignored while the script still printed "is on the TV". The provider
creates the file as the launcher, and it cannot truncate a file the shell owns, so the fix removes our copy
first and writes through the provider; cp stays as the fallback for firmware without it.

These tests run the real tools/theme.sh against a stub tools/tv.py whose "TV shell" is /bin/sh on this machine
and a stub `content` command standing in for the provider, so the command order is what a TV would see.
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
TOOLS = ROOT / "tools"
DEVICE_DIR = "/sdcard/Android/data/com.gathin.tvhome/files"
STAGE = "/data/local/tmp/cyt-theme"

# --push copies like tv.py does; shell commands run on this machine after the device paths are rewritten.
FAKE_TV = r'''
import json
import os
import subprocess
import sys

LOG = os.environ["FAKE_LOG"]
DIR = os.environ["FAKE_DIR"]
TMP = os.environ["FAKE_STAGE"]
DEVICE_DIR = "/sdcard/Android/data/com.gathin.tvhome/files"


def local(command):
    return command.replace(DEVICE_DIR, DIR).replace("/data/local/tmp/cyt-theme", TMP)


class Device:
    max_chunk_size = 4096

    def shell(self, command, read_timeout_s=None):
        with open(LOG, "a") as f:
            f.write(json.dumps({"shell": command}) + "\n")
        return subprocess.run(["/bin/sh", "-c", local(command)],
                              capture_output=True, text=True).stdout

    def close(self):
        pass


def connect():
    return Device()


args = sys.argv[1:]
if args and args[0] == "--push":
    src, dst = args[1], local(args[2])
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    with open(src, "rb") as f, open(dst, "wb") as g:
        g.write(f.read())
else:
    Device().shell(" ".join(args))
'''

# Stands in for `content write/delete` against content://com.gathin.tvhome.theme, which on a TV runs inside
# the launcher's process and so creates files owned by the launcher. FAKE_NO_PROVIDER models firmware or a
# launcher version without the provider.
FAKE_CONTENT = r'''#!/usr/bin/env python3
import json
import os
import sys

args = sys.argv[1:]
with open(os.environ["FAKE_CONTENT_LOG"], "a") as f:
    f.write(json.dumps({"content": args}) + "\n")
if os.environ.get("FAKE_NO_PROVIDER"):
    sys.exit(1)
if os.environ.get("FAKE_NO_CONTENT_WRITE") and args[0] == "write":
    # Android 8 and 9: `content` has no write subcommand; it prints its usage and this line, and exits 0.
    print("usage: adb shell content [subcommand] [options]")
    print("[ERROR] Unsupported operation: write")
    sys.exit(0)
action, uri = args[0], args[args.index("--uri") + 1]
path = os.path.join(os.environ["FAKE_DIR"], uri.split("com.gathin.tvhome.theme/", 1)[1])
if action == "write":
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(sys.stdin.buffer.read())
elif action == "delete":
    if os.path.isdir(path):
        for name in os.listdir(path):
            if name.lower().endswith((".jpg", ".jpeg", ".png", ".webp")):
                os.remove(os.path.join(path, name))
    elif os.path.exists(path):
        os.remove(path)
'''


class ThemePushTest(unittest.TestCase):
    def setUp(self):
        self.base = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.base, ignore_errors=True)
        self.tools = self.base / "tools"
        self.tools.mkdir()
        for name in ("theme.sh", "b64push.py"):
            shutil.copy2(TOOLS / name, self.tools / name)
        (self.tools / "tv.py").write_text(FAKE_TV)
        venv = self.tools / "venv" / "bin"
        venv.mkdir(parents=True)
        (venv / "python").symlink_to(sys.executable)
        self.bin = self.base / "bin"
        self.bin.mkdir()
        content = self.bin / "content"
        content.write_text(FAKE_CONTENT)
        content.chmod(0o755)
        self.device = self.base / "device"
        self.device.mkdir()
        self.stage = self.base / "stage"
        self.log = self.base / "log.jsonl"
        self.content_log = self.base / "content.jsonl"
        self.theme = self.base / "aurora"
        self.theme.mkdir()
        self.config = json.dumps({"background": "#101418", "app_keys": []}, indent=2).encode()
        (self.theme / "config.json").write_bytes(self.config)

    def run_theme(self, *args, **env):
        full = {k: v for k, v in os.environ.items() if k != "TV_HOST"}
        full.update({"TV_HOST": "192.0.2.10", "FAKE_LOG": str(self.log), "FAKE_DIR": str(self.device),
                     "FAKE_STAGE": str(self.stage), "FAKE_CONTENT_LOG": str(self.content_log),
                     "PATH": f"{self.bin}:{full.get('PATH', '')}"}, **env)
        return subprocess.run(["bash", str(self.tools / "theme.sh"), *args], env=full,
                              capture_output=True, text=True, timeout=300)

    def shell_commands(self):
        if not self.log.exists():
            return []
        return [json.loads(line)["shell"] for line in self.log.read_text().splitlines()]

    def provider_calls(self):
        if not self.content_log.exists():
            return []
        return [json.loads(line)["content"] for line in self.content_log.read_text().splitlines()]

    def command_for(self, name):
        """The one device command that puts `name` on the TV, in every shape theme.sh uses it."""
        matches = [c for c in self.shell_commands() if f"{DEVICE_DIR}/{name}" in c and "content write" in c]
        self.assertEqual(len(matches), 1, f"expected exactly one put for {name}: {self.shell_commands()}")
        return matches[0]

    def test_config_is_written_through_the_provider(self):
        result = self.run_theme(str(self.theme))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual((self.device / "config.json").read_bytes(), self.config,
                         "the theme has to land on the TV")
        put = self.command_for("config.json")
        self.assertTrue(put.startswith(f"rm -f {DEVICE_DIR}/config.json"),
                        f"a file the shell owns stops the provider truncating it: {put}")
        self.assertLess(put.index("content write"), put.index("cp "),
                        f"cp creates a shell-owned file on Android 14: {put}")
        self.assertIn(["write", "--uri", "content://com.gathin.tvhome.theme/config.json"],
                      self.provider_calls())
        self.assertNotIn(f"cp {self.stage}-config.json {DEVICE_DIR}/config.json", " | ".join(self.shell_commands()),
                         "the provider is available, so cp must not run")

    def test_without_the_provider_cp_still_delivers_the_theme(self):
        result = self.run_theme(str(self.theme), FAKE_NO_PROVIDER="1")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual((self.device / "config.json").read_bytes(), self.config,
                         "firmware without the provider keeps the old path")
        self.assertTrue(self.provider_calls(), "the provider is tried first")

    def test_android_8_without_content_write_falls_back_to_cp(self):
        """Android 8/9 `content` has no write: it exits 0 after printing an error, so the status alone is not enough."""
        (self.theme / "wallpaper.jpg").write_bytes(b"\xff\xd8jpeg bytes")
        result = self.run_theme(str(self.theme), FAKE_NO_CONTENT_WRITE="1")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual((self.device / "config.json").read_bytes(), self.config,
                         "put() removes the file first, so without cp the theme would be gone")
        self.assertEqual((self.device / "wallpaper.jpg").read_bytes(), b"\xff\xd8jpeg bytes")
        self.assertEqual((self.device / "theme_revision").stat().st_size, 32)

    def test_reset_clears_the_overlay_through_the_provider(self):
        (self.device / "config.json").write_bytes(self.config)
        (self.device / "wallpaper.jpg").write_bytes(b"old wallpaper")
        result = self.run_theme("--reset")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertFalse((self.device / "config.json").exists(), "--reset removes the overlay")
        self.assertFalse((self.device / "wallpaper.jpg").exists())
        revision = self.command_for("theme_revision")
        self.assertLess(revision.index("rm -f"), revision.index("content write"),
                        f"the shell's old revision has to go before the provider writes: {revision}")
        self.assertEqual((self.device / "theme_revision").stat().st_size, 32)

    def test_wallpaper_and_captions_take_the_same_path(self):
        (self.theme / "wallpaper.jpg").write_bytes(b"\xff\xd8jpeg bytes")
        (self.theme / "wallpapers").mkdir()
        (self.theme / "wallpapers" / "dusk.jpg").write_bytes(b"\xff\xd8dusk")
        (self.theme / "wallpapers" / "captions.txt").write_text("dusk\n", encoding="utf-8")
        result = self.run_theme(str(self.theme))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual((self.device / "wallpapers" / "dusk.jpg").read_bytes(), b"\xff\xd8dusk")
        self.assertEqual((self.device / "wallpapers" / "captions.txt").read_text(), "dusk\n")
        for name in ("wallpapers/dusk.jpg", "wallpapers/captions.txt"):
            put = self.command_for(name)
            self.assertLess(put.index("content write"), put.index("cp "), put)

    def test_collection_image_extensions_are_case_insensitive(self):
        collection = self.theme / "wallpapers"
        collection.mkdir()
        extensions = ("jpg", "JPG", "JpG", "jpeg", "JPEG", "JpEg",
                      "png", "PNG", "PnG", "webp", "WEBP", "WeBp")
        images = {f"image-{index}.{extension}": b"\x00\xffimage bytes " + extension.encode()
                  for index, extension in enumerate(extensions)}
        for name, data in images.items():
            (collection / name).write_bytes(data)
        for name in ("notes.txt", "animation.gif", "image.avif", "backup.jpg.txt"):
            (collection / name).write_bytes(b"not a collection image")
        previous = self.device / "wallpapers"
        previous.mkdir()
        (previous / "old.jpg").write_bytes(b"old collection")

        result = self.run_theme(str(self.theme))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(sorted(path.name for path in previous.iterdir()), sorted(images),
                         "the collection must contain every supported image and no unsupported files")
        for name, data in images.items():
            with self.subTest(name=name):
                self.assertEqual((previous / name).read_bytes(), data)
                self.assertIn(["write", "--uri", f"content://com.gathin.tvhome.theme/wallpapers/{name}"],
                              self.provider_calls())

    def test_mixed_case_image_extension_still_checks_the_filename(self):
        collection = self.theme / "wallpapers"
        collection.mkdir()
        (collection / "bad name.JpG").write_bytes(b"image bytes")

        result = self.run_theme(str(self.theme))
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("unsupported wallpaper filename: bad name.JpG", result.stderr)
        self.assertFalse((self.device / "wallpapers" / "bad name.JpG").exists())

    def test_mixed_case_collection_is_replaced_and_reset_without_the_provider(self):
        collection = self.theme / "wallpapers"
        collection.mkdir()
        first = collection / "previous.JpG"
        first.write_bytes(b"\x00\xfffirst picture")

        pushed = self.run_theme(str(self.theme), FAKE_NO_PROVIDER="1")
        self.assertEqual(pushed.returncode, 0, pushed.stdout + pushed.stderr)
        device_collection = self.device / "wallpapers"
        self.assertEqual(sorted(path.name for path in device_collection.iterdir()), [first.name])
        self.assertEqual((device_collection / first.name).read_bytes(), first.read_bytes())

        first.unlink()
        second = collection / "next.PnG"
        second.write_bytes(b"\x00\xffsecond picture")
        replaced = self.run_theme(str(self.theme), FAKE_NO_PROVIDER="1")
        self.assertEqual(replaced.returncode, 0, replaced.stdout + replaced.stderr)
        self.assertEqual(sorted(path.name for path in device_collection.iterdir()), [second.name],
                         "without the provider, the shell must still remove the previous collection")
        self.assertEqual((device_collection / second.name).read_bytes(), second.read_bytes())

        reset = self.run_theme("--reset", FAKE_NO_PROVIDER="1")
        self.assertEqual(reset.returncode, 0, reset.stdout + reset.stderr)
        self.assertEqual(list(device_collection.iterdir()), [],
                         "reset must remove mixed-case images even without the provider")

    def test_the_windows_script_puts_files_the_same_way(self):
        """theme.ps1 cannot run here, so pin the order that the fix depends on."""
        script = (TOOLS / "theme.ps1").read_text(encoding="utf-8")
        put = next(line for line in script.splitlines() if line.startswith("function Put("))
        self.assertIn("rm -f $dir/$name", put)
        self.assertLess(put.index("rm -f $dir/$name"), put.index("content write"), put)
        self.assertLess(put.index("content write"), put.index("cp $t $dir/$name"), put)
        self.assertIn("Unsupported operation", put, "Android 8/9 exit 0 without writing; cp has to run then")

    def test_reset_clears_the_wallpaper_collection(self):
        """--reset promises the built-in config.xml, but the picker reads wallpapers/ from the TV itself."""
        (self.device / "config.json").write_bytes(self.config)
        (self.device / "wallpaper.jpg").write_bytes(b"old wallpaper")
        collection = self.device / "wallpapers"
        collection.mkdir()
        for name in ("01-dusk.jpg", "02-dunes.PNG", "03-lake.webp"):
            (collection / name).write_bytes(b"\x89picture")
        (collection / "captions.txt").write_text("01-dusk.jpg\tDusk\n", encoding="utf-8")
        result = self.run_theme("--reset")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(sorted(p.name for p in collection.iterdir()), [],
                         "the launcher's wallpaper picker lists this folder itself, so a reset that leaves "
                         "pictures in it still offers the theme it just removed")
        self.assertFalse((self.device / "config.json").exists())
        self.assertFalse((self.device / "wallpaper.jpg").exists())

    def test_a_push_and_a_reset_clear_the_collection_with_one_command(self):
        """One clear, used by both paths: two copies are how --reset came to miss the collection."""
        (self.theme / "wallpapers").mkdir()
        (self.theme / "wallpapers" / "dusk.jpg").write_bytes(b"\xff\xd8dusk")
        pushed = self.run_theme(str(self.theme))
        self.assertEqual(pushed.returncode, 0, pushed.stdout + pushed.stderr)
        push_commands = self.shell_commands()
        self.log.unlink()
        reset = self.run_theme("--reset")
        self.assertEqual(reset.returncode, 0, reset.stdout + reset.stderr)
        reset_commands = self.shell_commands()

        clear = [c for c in push_commands
                 if "content delete --uri content://com.gathin.tvhome.theme/wallpapers 2>/dev/null" in c]
        self.assertEqual(len(clear), 1, f"a push empties the collection first: {push_commands}")
        self.assertIn(f"{DEVICE_DIR}/wallpapers/*.[jJ][pP][gG]", clear[0])
        self.assertIn(f"{DEVICE_DIR}/wallpapers/*.[wW][eE][bB][pP]", clear[0],
                      "the shell fallback clears every extension spelling the script pushes")
        joined = " | ".join(reset_commands)
        self.assertIn(clear[0], joined,
                      "the reset has to run the very same clear, or the two paths drift apart again")
        self.assertIn(f"content delete --uri content://com.gathin.tvhome.theme/wallpapers/captions.txt", joined)

    def test_the_windows_script_clears_the_collection_on_reset(self):
        """theme.ps1 cannot run here, so the -Reset branch is pinned as text against theme.sh."""
        script = (TOOLS / "theme.ps1").read_text(encoding="utf-8")
        reset = next(line for line in script.splitlines() if line.strip().startswith("& $py -I tv.py \"mkdir -p $dir"))
        self.assertIn("$(Drop 'wallpapers/captions.txt')", reset)
        self.assertIn("$clearWallpapers", reset, "one shared clear, as in theme.sh")
        clear = next(line for line in script.splitlines() if line.startswith("$clearWallpapers = "))
        self.assertIn("rm -f $dir/wallpapers/*.jpg", clear)
        self.assertIn("content delete --uri $uri/wallpapers", clear)
        self.assertNotIn("captions", clear, "the captions file is dropped on its own line, as in theme.sh")


if __name__ == "__main__":
    unittest.main()
