"""The launcher promises no network, and this is the file where that permission would appear.

`AGENTS.md`'s hard rules say the launcher carries no `INTERNET` permission and no network features,
`docs/DOS-AND-DONTS.md` puts `INTERNET`, Bluetooth, storage and notification-listener grants on its
Don't side "even behind a switch", and `.github/maintainer/MAINTAINER.md` sends any launcher pull
request that adds the permission or network access to `SHOWCASE.md` instead. Nothing read the file
that decides it: the checks over `launcher/` are the `minSdk` floor, the APK size gate, the card
contract, the Home path and the key helper, and `grep -rn uses-permission tests/` answered only the
add-on pages, which read *their* manifests. `tests/test_locales_registered.py` opens this manifest too,
for the locale config line alone.

Two things make the gap worth a test rather than a review habit:

- **A permission can arrive without anyone editing the manifest.** Android merges every dependency's
  own `AndroidManifest.xml` into the built one, so a library added to `app/build.gradle` can bring
  `INTERNET` in with it while this file still reads clean. The suite cannot see that merge (it needs a
  Gradle build), but `app/build.gradle`'s dependency block is where the change would be made, and the
  permission it merges in is judged here as soon as someone adds it to the source manifest too.
- **The rule erodes one switch at a time.** A Bluetooth or storage grant is small on its own and the
  promise is what the project is judged on, so the second half here is the permissions the launcher
  declares at all: the five it ships (`ACCESS_NETWORK_STATE` and `ACCESS_WIFI_STATE` for the pill,
  `ACCESS_FINE_LOCATION` for the Wi-Fi name on Android 10+, `WRITE_SECURE_SETTINGS` for the remote-key
  helper the install script grants, and YaOS's `ACCESS_TV_SOURCE` for its live TV app). A sixth is a
  permission an owner's TV will be asked for, so it is a line in this file with its reason, not a
  silent addition.

The third part is the code half of the same promise: no Java source in `launcher/` reaches for a
socket, `java.net`, or a web view. `NET_CAPABILITY_INTERNET` is the one `INTERNET`-shaped name the
launcher does use - `MainActivity` reads it off `NetworkCapabilities` to tell a connected Wi-Fi network
from an unconnected one, which asks for no permission - so it stays quiet, and one stand-in test below
keeps it that way.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LAUNCHER = ROOT / "launcher" / "app" / "src" / "main"
MANIFEST = LAUNCHER / "AndroidManifest.xml"
SOURCES = LAUNCHER / "java"

ANDROID = "{http://schemas.android.com/apk/res/android}name"
# Both tags declare a permission: `uses-permission-sdk-23` is the same grant asked for on API 23 and
# later, which is how a TV-only manifest asks for a modern permission without the older one.
PERMISSION_TAGS = ("uses-permission", "uses-permission-sdk-23")

INTERNET = "android.permission.INTERNET"
# What the launcher ships today, each name beside the reason it is there (see this file's docstring).
SHIPPED = {
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.WRITE_SECURE_SETTINGS",
    "yandex.tv.permission.ACCESS_TV_SOURCE",
}

# The shapes a network connection is opened with, in plain Java and in the libraries that wrap it. A
# mention is enough: `java.net.` covers URL, URLConnection and the socket classes, and an import of
# okhttp or retrofit is a network client whatever the line below it says.
NETWORK_CODE = (
    "java.net.",
    "HttpURLConnection",
    "okhttp3",
    "okhttp4",
    "retrofit2",
    "com.android.volley",
    "java.nio.channels.SocketChannel",
    "WebView",
    "javax.net.ssl.",
)
# `NET_CAPABILITY_INTERNET` is a capability constant, not a permission: the launcher reads it to know
# whether the Wi-Fi network it shows is connected. It is the only INTERNET-shaped name in the sources.
CAPABILITY = "NET_CAPABILITY_INTERNET"


def permissions_in(text):
    """The permissions a manifest's text declares, as `{name: tag}`, or raises ValueError on bad XML."""
    root = ET.fromstring(text)
    found = {}
    for element in root.iter():
        if element.tag in PERMISSION_TAGS:
            name = element.get(ANDROID)
            if name:
                found[name] = element.tag
    return found


def network_permissions(names):
    """The declared permissions that let the launcher open a connection on its own, or [].

    `INTERNET` is the one that does; the check also takes a vendor's own copy of the name (its last
    segment is `INTERNET`) because a re-badged firmware can define the grant under its own prefix. A
    second permission would not add reach - `ACCESS_NETWORK_STATE` and `ACCESS_WIFI_STATE` report on
    the network without reaching it, which is what the pill needs.
    """
    return sorted(name for name in names
                  if name == INTERNET or name.rsplit(".", 1)[-1] == "INTERNET")


def unreviewed_permissions(names):
    """The declared permissions the launcher does not ship, or [] - each one is an owner-visible grant."""
    return sorted(set(names) - SHIPPED)


def network_calls(text):
    """The network shapes in one Java file's text, `NET_CAPABILITY_INTERNET` aside, or []."""
    body = text.replace(CAPABILITY, "")
    return [shape for shape in NETWORK_CODE if shape in body]


def java_files(root=None):
    """Every Java source the launcher ships, so a sweep cannot pass by reading nothing."""
    return sorted((root or SOURCES).rglob("*.java"))


class TreeTest(unittest.TestCase):
    def test_the_launcher_declares_no_network_permission(self):
        found = network_permissions(permissions_in(MANIFEST.read_text(encoding="utf-8")))
        self.assertEqual([], found,
                         "launcher/app/src/main/AndroidManifest.xml declares %s: the launcher is "
                         "offline on purpose (AGENTS.md, docs/DOS-AND-DONTS.md), and a build that "
                         "needs the network belongs in SHOWCASE.md, not here" % ", ".join(found))

    def test_the_manifest_declares_only_the_permissions_it_ships(self):
        names = permissions_in(MANIFEST.read_text(encoding="utf-8"))
        self.assertGreaterEqual(len(names), 3, "no permission was read out of the manifest")
        added = unreviewed_permissions(names)
        self.assertEqual([], added,
                         "AndroidManifest.xml asks for %s, which the launcher did not ship before: "
                         "every permission is shown on the owner's TV, so one is added here together "
                         "with its reason in this file" % ", ".join(added))

    def test_no_java_source_opens_a_connection(self):
        files = java_files()
        self.assertGreaterEqual(len(files), 60, "the sweep read %d Java files" % len(files))
        bad = []
        for path in files:
            for shape in network_calls(path.read_text(encoding="utf-8")):
                bad.append("%s: %s" % (path.relative_to(ROOT), shape))
        self.assertEqual([], bad, "a launcher source reaches for the network:\n" + "\n".join(bad))


class TheManifestReader(unittest.TestCase):
    """permissions_in() and its two filters over manifests written here, so the rule keeps its teeth."""

    HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n')
    TAIL = "</manifest>\n"

    def manifest(self, *lines):
        return self.HEAD + "".join("    %s\n" % line for line in lines) + self.TAIL

    def test_an_internet_permission_is_reported(self):
        text = self.manifest('<uses-permission android:name="android.permission.INTERNET" />')
        self.assertEqual({"android.permission.INTERNET": "uses-permission"}, permissions_in(text))
        self.assertEqual(["android.permission.INTERNET"], network_permissions(permissions_in(text)))

    def test_the_sdk_23_tag_is_read_too(self):
        text = self.manifest('<uses-permission-sdk-23 android:name="android.permission.INTERNET" />')
        self.assertEqual(["android.permission.INTERNET"], network_permissions(permissions_in(text)))

    def test_a_vendors_copy_of_the_name_is_reported(self):
        text = self.manifest('<uses-permission android:name="com.example.permission.INTERNET" />')
        self.assertEqual(["com.example.permission.INTERNET"], network_permissions(permissions_in(text)))

    def test_a_permission_that_reports_on_the_network_is_not_one(self):
        text = self.manifest('<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />',
                             '<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />')
        self.assertEqual([], network_permissions(permissions_in(text)))

    def test_a_grant_the_launcher_does_not_ship_is_reported(self):
        text = self.manifest('<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />',
                             '<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />')
        self.assertEqual(["android.permission.BLUETOOTH_CONNECT"],
                         unreviewed_permissions(permissions_in(text)))

    def test_the_shipped_manifest_is_quiet(self):
        names = permissions_in(MANIFEST.read_text(encoding="utf-8"))
        self.assertEqual([], network_permissions(names))
        self.assertEqual([], unreviewed_permissions(names))


class TheSweepOverTheSources(unittest.TestCase):
    """network_calls() over Java written here, so a connection cannot come back unnoticed."""

    def test_an_import_of_java_net_is_reported(self):
        self.assertEqual(["java.net."], network_calls("import java.net.URL;\nclass A {}\n"))

    def test_a_socket_library_is_reported(self):
        self.assertEqual(["okhttp3"], network_calls("import okhttp3.OkHttpClient;\n"))
        self.assertEqual(["WebView"], network_calls("  WebView view = new WebView(this);\n"))
        self.assertEqual(["WebView"], network_calls("import android.webkit.WebView;\n"))

    def test_the_capability_the_launcher_reads_is_not_a_connection(self):
        self.assertEqual([], network_calls(
            "if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;\n"))
        self.assertEqual(["java.net."], network_calls(
            "if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;\n"
            "new java.net.URL(\"http://example\");\n"))


if __name__ == "__main__":
    unittest.main()
