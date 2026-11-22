"""tools/adb-closed.py asks an open port whether it is ADB, and believes only an answer.

An open port used to be judged by its number alone: 5555 was network ADB, any other port "may be" ADB or a
service of the TV's own. On a Philips TPM191E the JointSpace API keeps 1925 open whatever the debugging
toggles say, so the check could only ever answer "still open" there (#3890). The maintainer's ask was to
greet the port the way adb does and listen: adbd answers ADB's opening CNXN with CNXN, AUTH or STLS (the
last on the Wireless debugging port), and nothing else answers with those.

So the tool now sends that greeting and reads one header back:

- an ADB answer is network ADB, on any port, and exits 1;
- an answer that is not ADB, or a hang-up, is named as a port that is open but not ADB, with the check for
  5555; it still exits 1, because only the TV's own refusal shows a port closed;
- silence, or a greeting that cannot be sent, proves nothing, so the port is judged by its number as before.

The greeting is only the CNXN: the tool never sends a key, so it cannot bring up the TV's "Allow USB
debugging?" prompt. The TV cases run the script under a stand-in for socket.create_connection that hands
back a fake connection, so they reach no network; the greeting's bytes are checked against the module itself.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import importlib.util
from pathlib import Path
import os
import struct
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "adb-closed.py"
TV = "192.0.2.1"   # a documentation address; the stand-in answers for it, nothing on the network does


def load_script():
    spec = importlib.util.spec_from_file_location("adb_closed_handshake_under_test", SCRIPT)
    assert spec is not None and spec.loader is not None, f"{SCRIPT} cannot be loaded"
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


ADB_CLOSED = load_script()

# STAND_IN_TV maps a port to what the TV does: refused, or open with how it answers the greeting.
#   adb-cnxn / adb-auth / adb-stls  answers with that ADB command's header
#   http                            answers with an HTTP error, as a web API would
#   hangup                          closes the connection without a word
#   quiet                           keeps the connection open and says nothing
#   cut-<name>                      sends part of a reply, then times out or hangs up (see CUT_REPLIES)
STAND_IN = r"""
import os, runpy, socket, struct, sys
answers = dict(item.split("=") for item in os.environ["STAND_IN_TV"].split(","))
COMMANDS = {"adb-cnxn": 0x4E584E43, "adb-auth": 0x48545541, "adb-stls": 0x534C5453}

AUTH = struct.pack("<6I", 0x48545541, 1, 0, 0, 0, 0x48545541 ^ 0xFFFFFFFF)
CNXN = struct.pack("<6I", 0x4E584E43, 1, 0, 0, 0, 0x4E584E43 ^ 0xFFFFFFFF)
CUT_REPLIES = {"cut-auth8-timeout": (AUTH[:8], "timeout"), "cut-cnxn4-eof": (CNXN[:4], "eof"),
               "cut-auth3-timeout": (AUTH[:3], "timeout"), "cut-http-timeout": (b"HTTP/1.1 ", "timeout")}

class Connection:
    def __init__(self, answer):
        self.answer = answer
        self.ends = "eof"
        if answer in CUT_REPLIES:
            self.reply, self.ends = CUT_REPLIES[answer]
        elif answer in COMMANDS:
            command = COMMANDS[answer]
            self.reply = struct.pack("<6I", command, 1, 0, 0, 0, command ^ 0xFFFFFFFF)
        elif answer == "http":
            self.reply = b"HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n"
        else:
            self.reply = b""
    def getpeername(self):
        return ("192.0.2.1", 0)
    def settimeout(self, timeout):
        pass
    def sendall(self, data):
        sys.stderr.write("[stand-in] sent %d bytes, command %08x\n" % (len(data), struct.unpack("<I", data[:4])[0]))
    def recv(self, size):
        if self.answer == "quiet":
            raise socket.timeout("timed out")
        chunk, self.reply = self.reply[:size], self.reply[size:]
        if not chunk and self.ends == "timeout":
            raise socket.timeout("timed out")
        return chunk
    def close(self):
        sys.stderr.write("[stand-in] closed\n")

def stand_in(address, timeout=None, *args, **kwargs):
    answer = answers.get(str(address[1]), "refused")
    if answer == "refused":
        raise ConnectionRefusedError(61, "Connection refused")
    return Connection(answer)

socket.create_connection = stand_in
sys.argv = sys.argv[1:]
runpy.run_path(sys.argv[0], run_name="__main__")
"""


def check_tv(*args, tv):
    env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT")}
    env["STAND_IN_TV"] = tv
    return subprocess.run([sys.executable, "-I", "-c", STAND_IN, str(SCRIPT), *args], capture_output=True,
                          text=True, timeout=30, env=env)


class AnAdbAnswerIsNetworkAdbTest(unittest.TestCase):
    def test_every_adb_answer_on_5555_is_network_adb(self):
        for answer, name in (("adb-cnxn", "CNXN"), ("adb-auth", "AUTH"), ("adb-stls", "STLS")):
            with self.subTest(answer=answer):
                result = check_tv(TV, tv=f"5555={answer}")
                self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
                self.assertIn("it is network ADB", result.stderr)
                self.assertIn(name, result.stderr)

    def test_an_adb_answer_on_another_port_is_network_adb_too(self):
        # The Wireless debugging port: a number the tool cannot know, so only the answer can tell.
        result = check_tv(TV, "43217", tv="43217=adb-stls,5555=refused")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn(f"{TV}:43217 is still open, and it is network ADB", result.stderr)

    def test_the_greeting_is_a_cnxn_and_the_connection_is_closed(self):
        result = check_tv(TV, tv="5555=adb-auth")
        self.assertIn("[stand-in] sent 31 bytes, command 4e584e43", result.stderr)
        self.assertIn("[stand-in] closed", result.stderr)


class AnAnswerThatIsNotAdbTest(unittest.TestCase):
    def test_not_adb_still_exits_as_an_open_port(self):
        # Only the TV's refusal is "closed" (PROMPT.md's closing step): a port that is open but not ADB is
        # named for what it is, and the exit code stays the open port's.
        for answer in ("http", "hangup"):
            with self.subTest(answer=answer):
                result = check_tv(TV, "1925", tv=f"1925={answer},5555=refused")
                self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
                self.assertNotIn("is closed", result.stdout + result.stderr)

    def test_a_web_api_on_its_own_port_is_not_adb(self):
        # #3890: the Philips JointSpace API on 1925 answers whatever the debugging toggles say.
        result = check_tv(TV, "1925", tv="1925=http,5555=refused")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn(f"{TV}:1925 is still open, but it is not ADB", result.stderr)
        self.assertIn(f"tools/adb-closed.py {TV} 5555", result.stderr)
        self.assertNotIn("network ADB is on", result.stdout + result.stderr)

    def test_a_hangup_is_not_adb(self):
        result = check_tv(TV, "8008", tv="8008=hangup")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("it hung up on ADB's greeting", result.stderr)

    def test_5555_answering_as_something_else_is_not_adb_and_names_no_second_check(self):
        result = check_tv(TV, tv="5555=http")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn(f"{TV}:5555 is still open, but it is not ADB", result.stderr)
        self.assertNotIn("check it with", result.stderr)


class ACutOffReplyIsUnknownTest(unittest.TestCase):
    def test_a_cut_off_adb_answer_is_unknown_not_not_adb(self):
        result = check_tv(TV, tv="5555=cut-auth8-timeout")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertNotIn("Network ADB is not on this port", result.stderr)
        self.assertIn(f"{TV}:5555 is still open: network ADB is on", result.stderr)

    def test_ask_adb_says_unknown_for_a_cut_off_adb_answer(self):
        for answer in ("cut-auth8-timeout", "cut-cnxn4-eof"):
            with self.subTest(answer=answer):
                result = check_tv(TV, "1925", tv=f"1925={answer},5555=refused")
                self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
                self.assertIn(f"{TV}:1925 is still open: something answers there", result.stderr)
                self.assertNotIn("it is not ADB", result.stderr)

    def test_fewer_than_four_bytes_or_a_non_adb_word_is_still_not_adb(self):
        for answer in ("cut-auth3-timeout", "cut-http-timeout"):
            with self.subTest(answer=answer):
                result = check_tv(TV, "5555", tv=f"5555={answer}")
                self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
                self.assertIn("it is not ADB", result.stderr)

    def test_short_reply_names_the_command_and_the_bytes(self):
        verdict, why = ADB_CLOSED.short_reply(struct.pack("<I", 0x4E584E43))
        self.assertEqual(verdict, "unknown")
        self.assertIn("CNXN", why)
        self.assertIn("4 of 24", why)


class SilenceProvesNothingTest(unittest.TestCase):
    def test_a_quiet_5555_is_still_judged_as_network_adb(self):
        result = check_tv(TV, tv="5555=quiet")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn(f"{TV}:5555 is still open: network ADB is on", result.stderr)

    def test_a_quiet_other_port_keeps_the_may_be_message(self):
        result = check_tv(TV, "1925", tv="1925=quiet,5555=refused")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn(f"{TV}:1925 is still open: something answers there", result.stderr)
        self.assertNotIn("network ADB is on", result.stderr)


class TheGreetingItselfTest(unittest.TestCase):
    def test_the_greeting_is_a_well_formed_cnxn(self):
        data = ADB_CLOSED.greeting()
        command, version, max_payload, length, checksum, magic = struct.unpack("<6I", data[:24])
        payload = data[24:]
        self.assertEqual(command, 0x4E584E43)
        self.assertEqual(magic, command ^ 0xFFFFFFFF)
        self.assertEqual(version, 0x01000001)
        self.assertEqual(max_payload, 256 * 1024)
        self.assertEqual(payload, b"host::\0")
        self.assertEqual(length, len(payload))
        self.assertEqual(checksum, sum(payload))

    def test_only_a_header_with_its_magic_is_adb(self):
        good = struct.pack("<6I", 0x48545541, 1, 0, 0, 0, 0x48545541 ^ 0xFFFFFFFF)
        bad_magic = struct.pack("<6I", 0x48545541, 1, 0, 0, 0, 0)
        other = struct.pack("<6I", 0x45534C43, 0, 0, 0, 0, 0x45534C43 ^ 0xFFFFFFFF)   # CLSE, not an answer
        self.assertEqual(ADB_CLOSED.adb_answer(good), "AUTH")
        self.assertIsNone(ADB_CLOSED.adb_answer(bad_magic))
        self.assertIsNone(ADB_CLOSED.adb_answer(other))
        self.assertIsNone(ADB_CLOSED.adb_answer(b"HTTP/1.1 400 Bad Request"))
        self.assertIsNone(ADB_CLOSED.adb_answer(good[:20]))


if __name__ == "__main__":
    unittest.main()
