"""tools/docker-build.md's settings are the ones both twins and the image it builds use.

`tools/docker-build.md` is the page a contributor with no JDK follows: it lists the four environment
variables the build takes with a default each, the toolchain the image carries, one example per twin
and the two install commands. Every one of those is written somewhere else as well -
`tools/docker-build.sh` and `tools/docker-build.ps1` each fall back to a default of their own for
IMAGE, GRADLE_VOLUME and KEY_DIR and mount HOST_KEY_DIR themselves, `tools/docker/Dockerfile` carries
the JDK, the compile SDK and the build-tools version, and `launcher/build-and-install.sh` / `.ps1`
carry SKIP_BUILD and TV_HOST - and nothing read either side: `grep -rn docker tests/` printed nothing,
so the page and the three files it describes were the only copies of each value, kept in step by hand.

The failure this is for is quiet. The Gradle volume is where the debug signing key lives, so a
KEY_DIR row that has moved in one twin and not the other points Gradle at another folder, it makes a
fresh key, and the install over the app already on the TV fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE
- the one error the page exists to explain. A build-tools version that moved in the Dockerfile with
the page still saying the old one sends a contributor looking for a compiler error that is not there,
and the page's two examples are the only place a reader learns which project and task words the twins
take, so an example added to one twin and not the other is half a page.

What is read, and from where: the table under the page's Build section (header `Variable | Default |
What it changes`), the `NAME="${NAME:-default}"` lines of the shell twin, the `if ($env:NAME) { ...
} else { 'default' }` lines of the PowerShell twin, the `FROM`, `ARG COMPILE_SDK` and `ARG
BUILD_TOOLS` lines of the Dockerfile, the two `Building the <image> image once (JDK 17, Android SDK
34, build-tools 34.0.0; ...)` lines the twins print, the page's examples in both code blocks, and the
two install commands beside `launcher/build-and-install.sh` / `.ps1`. Nothing here runs docker, reads
a TV or needs one: it is six files of text.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "tools" / "docker-build.md"
SH = ROOT / "tools" / "docker-build.sh"
PS1 = ROOT / "tools" / "docker-build.ps1"
DOCKERFILE = ROOT / "tools" / "docker" / "Dockerfile"
INSTALL_SH = ROOT / "launcher" / "build-and-install.sh"
INSTALL_PS1 = ROOT / "launcher" / "build-and-install.ps1"

TABLE_HEADER = re.compile(r"^\|\s*Variable\s*\|\s*Default\s*\|\s*What it changes\s*\|\s*$", re.M)
# A separator row: pipes, dashes and the odd colon, and nothing that names a setting.
SEPARATOR = re.compile(r"^[\s|:-]+$")
# IMAGE="${IMAGE:-cleanyourtv-android-build}" - an empty fallback is a variable the twin reads but
# leaves unset (HOST_KEY_DIR), so the reader keeps the name and drops the empty default.
SH_DEFAULT = re.compile(r'^(?P<name>[A-Z][A-Z0-9_]*)="\$\{(?P=name):-(?P<value>[^}]*)\}"', re.M)
PS1_DEFAULT = re.compile(r"\$(?P<var>\w+) = if \(\$env:(?P<name>[A-Z][A-Z0-9_]*)\) "
                         r"\{ \$env:(?P=name) \} else \{ '(?P<value>[^']*)' \}")
# The same three numbers on the page and in both twins: "JDK 17, Android SDK 34 and build-tools
# 34.0.0" on the page, "JDK 17, Android SDK 34, build-tools 34.0.0;" in the line a twin prints.
TOOLCHAIN = re.compile(r"JDK (?P<jdk>\d+), Android SDK (?P<sdk>\d+)(?: and |, )"
                       r"build-tools (?P<tools>\d+(?:\.\d+)*)")
FROM_LINE = re.compile(r"^FROM\s+\S+?:(?P<version>\d+)[-\w]*\s*$", re.M)
ARG = re.compile(r"^ARG\s+(?P<name>\w+)=(?P<value>\S+)\s*$", re.M)
SH_EXAMPLE = re.compile(r"^tools/docker-build\.sh(?P<rest>[^\n]*)$", re.M)
PS1_EXAMPLE = re.compile(r"^tools\\docker-build\.ps1(?P<rest>[^\n]*)$", re.M)
# The twin's HOST_KEY_DIR branch: where the folder from this computer is mounted, and the KEY_DIR it
# forces once it is. Both are read, because only the second is what the key is actually looked up in.
SH_MOUNT = re.compile(r'-v "\$HOST_KEY_DIR:(?P<path>[^"]+)"')
SH_KEY_DIR = re.compile(r"^\s*KEY_DIR=(?P<path>/\S+)\s*$", re.M)
PS1_MOUNT = re.compile(r"-v', \"\$\(\$env:HOST_KEY_DIR\):(?P<path>[^\"]+)\"")
PS1_KEY_DIR = re.compile(r"^\s*\$KeyDir = '(?P<path>/\S+)'\s*$", re.M)
# The two install commands the page gives, one per twin.
INSTALL_SH_LINE = re.compile(r"^(?P<line>[^\n]*launcher/build-and-install\.sh[^\n]*)$", re.M)
INSTALL_PS1_LINE = re.compile(r"^(?P<line>[^\n]*launcher\\build-and-install\.ps1[^\n]*)$", re.M)

DEFAULTS = "IMAGE GRADLE_VOLUME KEY_DIR HOST_KEY_DIR".split()


def table(text):
    """{variable: (default, what the page says it changes)} from the guide's settings table.

    The table is found by its header rather than by its position, and reading stops at the first line
    that is not a row, so a second table further down is not folded into this one.
    """
    header = TABLE_HEADER.search(text)
    if header is None:
        return {}
    rows = {}
    for line in text[header.end():].lstrip("\r\n").splitlines():
        stripped = line.strip()
        if not stripped.startswith("|"):
            break
        if SEPARATOR.match(stripped):
            continue
        cells = [cell.strip() for cell in stripped.strip("|").split("|")]
        if len(cells) != 3:
            continue
        rows[cells[0].strip("`")] = (cells[1].strip("`"), cells[2])
    return rows


def sh_defaults(text):
    """{variable: the default tools/docker-build.sh falls back to}, an empty one left out."""
    rows = {}
    for match in SH_DEFAULT.finditer(text):
        if match.group("value"):
            rows[match.group("name")] = match.group("value")
    return rows


def ps1_defaults(text):
    """{variable: the default tools/docker-build.ps1 falls back to}."""
    return {match.group("name"): match.group("value") for match in PS1_DEFAULT.finditer(text)}


def toolchain(text):
    """(JDK, Android SDK, build-tools) as the page or a twin states them, or None."""
    match = TOOLCHAIN.search(text)
    if match is None:
        return None
    return (int(match.group("jdk")), int(match.group("sdk")), match.group("tools"))


def image_toolchain(text):
    """(JDK, Android SDK, build-tools) the Dockerfile installs, read out of the file itself."""
    from_line = FROM_LINE.search(text)
    args = {match.group("name"): match.group("value") for match in ARG.finditer(text)}
    if from_line is None or "COMPILE_SDK" not in args or "BUILD_TOOLS" not in args:
        return None
    return (int(from_line.group("version")), int(args["COMPILE_SDK"]), args["BUILD_TOOLS"])


def mount_points(text, mount, key_dir):
    """(where HOST_KEY_DIR is mounted, the KEY_DIR the twin forces) - either may be None."""
    mounted = mount.search(text)
    forced = [match.group("path") for match in key_dir.finditer(text)]
    return (mounted.group("path") if mounted else None,
            # The last plain assignment is the one after the mount; the default is the first.
            forced[-1] if forced else None)


def sh_examples(text):
    """{(project, tasks)} from the page's bash examples, a trailing comment dropped."""
    rows = set()
    for match in SH_EXAMPLE.finditer(text):
        rest = match.group("rest").split("#", 1)[0]
        words = rest.split()
        project = words[0] if words else "launcher"
        tasks = tuple(words[1:]) if len(words) > 1 else ("assembleRelease",)
        rows.add((project, tasks))
    return rows


def ps1_examples(text):
    """{(project, tasks)} from the page's PowerShell examples, the twins' own defaults filled in."""
    rows = set()
    for match in PS1_EXAMPLE.finditer(text):
        rest = match.group("rest").split("#", 1)[0]
        project = re.search(r"-Project\s+(?P<project>\S+)", rest)
        tasks = re.search(r"-Tasks\s+(?P<tasks>\S+)", rest)
        rows.add((project.group("project").replace("\\", "/") if project else "launcher",
                  tuple(tasks.group("tasks").split(",")) if tasks else ("assembleRelease",)))
    return rows


def install_commands(text):
    """(the names the bash install line writes, the names the PowerShell one writes)."""
    bash = INSTALL_SH_LINE.search(text)
    powershell = INSTALL_PS1_LINE.search(text)
    shell_names = set()
    if bash:
        for word in bash.group("line").split():
            name = re.match(r"(?P<name>[A-Z][A-Z0-9_]*)=", word)
            if name:
                shell_names.add(name.group("name"))
    windows_names = set()
    if powershell:
        windows_names |= set(re.findall(r"\$env:(?P<name>[A-Z][A-Z0-9_]*)", powershell.group("line")))
        windows_names |= set(re.findall(r"(?P<flag>-[A-Z]\w*)", powershell.group("line")))
    return shell_names, windows_names


class DockerBuildGuideTest(unittest.TestCase):
    """The page, both twins, the Dockerfile and the install scripts, read against each other."""

    @classmethod
    def setUpClass(cls):
        cls.page = GUIDE.read_text(encoding="utf-8")
        cls.sh = SH.read_text(encoding="utf-8")
        cls.ps1 = PS1.read_text(encoding="utf-8")
        cls.dockerfile = DOCKERFILE.read_text(encoding="utf-8")

    def test_the_guide_and_the_files_it_describes_are_all_there(self):
        for path in (GUIDE, SH, PS1, DOCKERFILE, INSTALL_SH, INSTALL_PS1):
            self.assertTrue(path.is_file(), f"{path.relative_to(ROOT)} is gone")
        self.assertIn("tools/docker-build.sh", self.page)
        self.assertIn("tools/docker-build.ps1", self.page)
        self.assertIn("tools/docker/Dockerfile", self.page)

    def test_the_settings_table_names_the_four_variables(self):
        rows = table(self.page)
        self.assertEqual(sorted(DEFAULTS), sorted(rows),
                         f"tools/docker-build.md lists {sorted(rows)} and the twins take {DEFAULTS}")

    def test_every_variable_in_the_table_is_one_both_twins_read(self):
        for name in table(self.page):
            self.assertIn(f'"${{{name}:-', self.sh,
                          f"tools/docker-build.md documents {name} and tools/docker-build.sh never reads it")
            self.assertIn(f"$env:{name}", self.ps1,
                          f"tools/docker-build.md documents {name} and tools/docker-build.ps1 never reads it")

    def test_each_default_is_the_one_both_twins_use(self):
        rows = table(self.page)
        shell = sh_defaults(self.sh)
        windows = ps1_defaults(self.ps1)
        for name in ("IMAGE", "GRADLE_VOLUME", "KEY_DIR"):
            stated = rows.get(name, ("", ""))[0]
            self.assertEqual(stated, shell.get(name),
                             f"tools/docker-build.md states {name} defaults to {stated} and "
                             f"tools/docker-build.sh falls back to {shell.get(name)}")
            self.assertEqual(stated, windows.get(name),
                             f"tools/docker-build.md states {name} defaults to {stated} and "
                             f"tools/docker-build.ps1 falls back to {windows.get(name)}")

    def test_a_variable_with_no_default_is_unset_in_both_twins(self):
        self.assertEqual("(none)", table(self.page).get("HOST_KEY_DIR", ("", ""))[0],
                         "the page gives HOST_KEY_DIR a default it does not have")
        self.assertIn('"${HOST_KEY_DIR:-}"', self.sh,
                      "tools/docker-build.sh reads HOST_KEY_DIR as if it had a default")
        self.assertIn("if ($env:HOST_KEY_DIR)", self.ps1,
                      "tools/docker-build.ps1 reads HOST_KEY_DIR as if it had a default")

    def test_the_key_folder_is_mounted_where_the_key_dir_points(self):
        sh_mount, sh_dir = mount_points(self.sh, SH_MOUNT, SH_KEY_DIR)
        ps1_mount, ps1_dir = mount_points(self.ps1, PS1_MOUNT, PS1_KEY_DIR)
        self.assertIsNotNone(sh_mount, "tools/docker-build.sh no longer mounts HOST_KEY_DIR")
        self.assertIsNotNone(ps1_mount, "tools/docker-build.ps1 no longer mounts HOST_KEY_DIR")
        self.assertEqual(sh_mount, ps1_mount,
                         f"the twins mount HOST_KEY_DIR at {sh_mount} and {ps1_mount}")
        self.assertEqual(sh_mount, sh_dir,
                         f"tools/docker-build.sh mounts HOST_KEY_DIR at {sh_mount} and points "
                         f"KEY_DIR at {sh_dir}")
        self.assertEqual(ps1_mount, ps1_dir,
                         f"tools/docker-build.ps1 mounts HOST_KEY_DIR at {ps1_mount} and points "
                         f"KEY_DIR at {ps1_dir}")

    def test_the_key_dir_is_what_the_container_looks_up_the_key_in(self):
        self.assertIn('-e ANDROID_USER_HOME="$KEY_DIR"', self.sh,
                      "the shell twin never hands KEY_DIR to Gradle as ANDROID_USER_HOME, so the "
                      "page's KEY_DIR row changes nothing")
        self.assertIn('-e "ANDROID_USER_HOME=$KeyDir"', self.ps1,
                      "the PowerShell twin never hands KEY_DIR to Gradle as ANDROID_USER_HOME, so "
                      "the page's KEY_DIR row changes nothing")

    def test_the_toolchain_on_the_page_is_the_one_the_image_carries(self):
        stated = toolchain(self.page)
        installed = image_toolchain(self.dockerfile)
        self.assertIsNotNone(stated, "tools/docker-build.md no longer states the toolchain")
        self.assertIsNotNone(installed, "tools/docker/Dockerfile no longer states FROM and both ARGs")
        self.assertEqual(stated, installed,
                         f"tools/docker-build.md promises JDK {stated[0]}, Android SDK {stated[1]} and "
                         f"build-tools {stated[2]}, and tools/docker/Dockerfile installs JDK "
                         f"{installed[0]}, Android SDK {installed[1]} and build-tools {installed[2]}")

    def test_both_twins_print_the_toolchain_the_page_promises(self):
        stated = toolchain(self.page)
        for twin, source in ((SH, self.sh), (PS1, self.ps1)):
            self.assertEqual(stated, toolchain(source),
                             f"{twin.relative_to(ROOT)} tells the reader "
                             f"{toolchain(source)} while the page promises {stated}")

    def test_each_example_on_the_page_has_its_twin_s_form(self):
        shell = sh_examples(self.page)
        windows = ps1_examples(self.page)
        self.assertGreaterEqual(len(shell), 3, "the page no longer shows three bash examples")
        self.assertEqual(shell, windows,
                         f"the page shows {sorted(shell)} for tools/docker-build.sh and "
                         f"{sorted(windows)} for tools/docker-build.ps1")

    def test_the_install_commands_name_what_the_install_scripts_read(self):
        shell_names, windows_names = install_commands(self.page)
        install_sh = INSTALL_SH.read_text(encoding="utf-8")
        install_ps1 = INSTALL_PS1.read_text(encoding="utf-8")
        self.assertEqual({"SKIP_BUILD", "TV_HOST"}, shell_names,
                         f"tools/docker-build.md's bash install line writes {sorted(shell_names)}")
        self.assertEqual({"SKIP_BUILD", "-TvHost"}, windows_names,
                         f"tools/docker-build.md's PowerShell install line writes "
                         f"{sorted(windows_names)}")
        for name in ("SKIP_BUILD", "TV_HOST"):
            self.assertIn(f"${{{name}", install_sh,
                          f"launcher/build-and-install.sh never reads {name}, which the page sets")
        self.assertIn("$env:SKIP_BUILD", install_ps1,
                      "launcher/build-and-install.ps1 never reads SKIP_BUILD, which the page sets")
        self.assertIn("$TvHost = $env:TV_HOST", install_ps1,
                      "launcher/build-and-install.ps1 no longer takes -TvHost")


class ReaderTest(unittest.TestCase):
    """The same readers over pages, twins and Dockerfiles written here."""

    def test_the_table_is_read_by_its_header_and_stops_at_the_next_section(self):
        page = ("| Variable | Default | What it changes |\n"
                "|---|---|---|\n"
                "| `IMAGE` | `my-image` | The image name |\n"
                "| `KEY_DIR` | /gradle/.key | Where the key is |\n"
                "\n"
                "## Install\n"
                "\n"
                "| Variable | Default | What it changes |\n"
                "|---|---|---|\n"
                "| `OTHER` | `x` | Another table |\n")
        self.assertEqual({"IMAGE": ("my-image", "The image name"),
                          "KEY_DIR": ("/gradle/.key", "Where the key is")}, table(page))

    def test_a_page_with_no_table_reads_as_no_settings(self):
        self.assertEqual({}, table("# Building in Docker\n\nNothing here.\n"))

    def test_the_shell_reader_keeps_an_unset_variable_out_of_the_defaults(self):
        source = ('IMAGE="${IMAGE:-cyt-image}"\n'
                  'KEY_DIR="${KEY_DIR:-/gradle/.android}"\n'
                  'HOST_KEY_DIR="${HOST_KEY_DIR:-}"\n'
                  'ROOT="$(cd "$(dirname "$0")/.." && pwd)"\n')
        self.assertEqual({"IMAGE": "cyt-image", "KEY_DIR": "/gradle/.android"}, sh_defaults(source))

    def test_the_powershell_reader_reads_the_default_out_of_the_inline_form(self):
        source = ("$Image = if ($env:IMAGE) { $env:IMAGE } else { 'cyt-image' }\n"
                  "$KeyDir = if ($env:KEY_DIR) { $env:KEY_DIR } else { '/gradle/.key' }\n"
                  "$Project = 'launcher'\n")
        self.assertEqual({"IMAGE": "cyt-image", "KEY_DIR": "/gradle/.key"}, ps1_defaults(source))

    def test_the_mount_reader_takes_the_key_dir_forced_after_the_mount(self):
        source = ('KEY_DIR="${KEY_DIR:-/gradle/.android}"\n'
                  'if [ -n "${HOST_KEY_DIR:-}" ]; then\n'
                  '  KEY_ARGS=(-v "$HOST_KEY_DIR:/key")\n'
                  '  KEY_DIR=/key\n'
                  'fi\n')
        self.assertEqual(("/key", "/key"), mount_points(source, SH_MOUNT, SH_KEY_DIR))

    def test_the_toolchain_reader_reads_the_sentence_and_the_image(self):
        page = "a container that has JDK 21, Android SDK 35 and build-tools 35.0.1.\n"
        self.assertEqual((21, 35, "35.0.1"), toolchain(page))
        self.assertIsNone(toolchain("no versions here\n"))
        dockerfile = ("FROM eclipse-temurin:21-jdk\n"
                      "ARG COMPILE_SDK=35\n"
                      "ARG BUILD_TOOLS=35.0.1\n")
        self.assertEqual((21, 35, "35.0.1"), image_toolchain(dockerfile))
        self.assertIsNone(image_toolchain("FROM eclipse-temurin:17-jdk\n"))

    def test_the_example_readers_drop_a_trailing_comment_and_fill_the_defaults(self):
        page = ("```bash\n"
                "tools/docker-build.sh              # the launcher (assembleRelease)\n"
                "tools/docker-build.sh addons/foo   # an add-on\n"
                "```\n"
                "\n"
                "```powershell\n"
                "tools\\docker-build.ps1\n"
                "tools\\docker-build.ps1 -Project addons\\foo\n"
                "```\n")
        self.assertEqual({("launcher", ("assembleRelease",)), ("addons/foo", ("assembleRelease",))},
                         sh_examples(page))
        self.assertEqual(sh_examples(page), ps1_examples(page))

    def test_the_install_reader_sees_both_kinds_of_name(self):
        page = ("SKIP_BUILD=1 TV_HOST=<tv ip> launcher/build-and-install.sh\n"
                "$env:SKIP_BUILD = '1'; launcher\\build-and-install.ps1 -TvHost <tv ip>\n")
        self.assertEqual(({"SKIP_BUILD", "TV_HOST"}, {"SKIP_BUILD", "-TvHost"}),
                         install_commands(page))


if __name__ == "__main__":
    unittest.main()
