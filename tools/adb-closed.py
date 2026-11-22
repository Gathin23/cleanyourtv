#!/usr/bin/env python3
"""Check that the TV's network ADB port is closed, after turning debugging off (no nc needed).

    adb-closed.py <tv ip[:port]> [port]   port defaults to TV_PORT or 5555; the IP to TV_HOST

Exits 0 when the TV refuses the connection, 1 when the port is still open, and 2 when the check
could not tell: nothing answered (the TV off, asleep or rebooting, at another address, or out of reach
from here), nothing listened on 127.0.0.1, or the check itself could not run (a name that does not
resolve or is not a hostname, no route to the TV, an address that is a URL or a path rather than an
IP, or a word starting with a dash, which is a flag this check does not have). Only the TV's own
refusal shows the port closed.

On macOS a "No route to host" (`EHOSTUNREACH`) is also what Local Network privacy looks like: the
permission belongs to the app that runs this check, not to python or adb, so a terminal that was never
granted it is denied the subnet and this tool cannot tell an open port from a closed one. The message
says so and gives the way to grant it (tools/tv.py names the same permission at connect time).

A TV_PORT exported with TV_HOST=127.0.0.1, or set to relay.py's 15555, is a relay's (tools/relay.md), so
it is not used for a TV's own IP. 127.0.0.1 is this computer: a relay answers there for itself, not for
the TV, so a refusal there is not the TV's.

An open port other than 5555 is reported as the port it is, not as ADB. Port 5555 is the ADB port, and
another port may be the Wireless debugging port (`adb mdns services` lists it as
`_adb-tls-connect._tcp`) or one this firmware runs adbd on (5877 on the Wanbo T6R Max) - but a TV also
keeps services of its own open whatever the debugging toggles say, the JointSpace API on a Philips
(1926, or 1925 on older sets), casting and a web page among them. For those an open port says nothing
about ADB, so the message names the port it checked and, to finish the step, the command that checks
5555 itself.

An open port is then asked whether it is ADB: the check sends ADB's opening CNXN message and reads the
reply's header. adbd always answers one, with CNXN (this computer is already allowed), AUTH (it asks for a
key) or STLS (the Wireless debugging port, before its TLS handshake), so any of those is network ADB on
whatever port it was. A port that answers with anything else, or hangs up, is open but not ADB, and the
message says so and names the check for 5555; it still exits 1, as any open port does, because only the TV's
own refusal shows a port closed. A port that says nothing within the time, that the greeting cannot reach, or
whose ADB answer stops part way, is judged by its number as before, since that proves nothing either way.
The check sends only the greeting, never a key, so it cannot bring up the TV's "Allow USB debugging?" prompt (#3890).
"""
import errno
import ipaddress
import os
import socket
import struct
import sys

TIMEOUT_S = 3.0
RELAY_PORT = 15555   # relay.py's default --port, the TV_PORT it tells the tools to use
ADB_PORT = 5555      # where adbd listens by default: an open one of these is network ADB
HANDSHAKE_S = 3.0    # how long an open port has to answer ADB's greeting

# ADB's wire format: a 24-byte little-endian header (command, arg0, arg1, payload length, payload checksum,
# command ^ 0xffffffff) and the payload. adbd answers a CNXN with one of these three, and only adbd does.
A_CNXN, A_AUTH, A_STLS = 0x4E584E43, 0x48545541, 0x534C5453
ADB_ANSWERS = {A_CNXN: "CNXN", A_AUTH: "AUTH", A_STLS: "STLS"}


def greeting():
    """The CNXN a host sends first: protocol version 1.0.0.1, 256 KiB max payload, banner host::."""
    payload = b"host::\0"
    return struct.pack("<6I", A_CNXN, 0x01000001, 256 * 1024, len(payload), sum(payload) & 0xFFFFFFFF,
                       A_CNXN ^ 0xFFFFFFFF) + payload


def adb_answer(header):
    """The ADB command a 24-byte header carries (CNXN, AUTH or STLS), or None when it is not ADB's."""
    if len(header) != 24:
        return None
    command, _, _, _, _, magic = struct.unpack("<6I", header)
    if command in ADB_ANSWERS and magic == command ^ 0xFFFFFFFF:
        return ADB_ANSWERS[command]
    return None


def short_reply(header):
    """A non-empty reply that is not a complete ADB header: unknown when it begins an ADB answer, else not ADB."""
    for command, name in ADB_ANSWERS.items():
        if 4 <= len(header) < 24 and header[:4] == struct.pack("<I", command):
            return "unknown", f"it began an ADB answer ({name}) and stopped after {len(header)} of 24 bytes"
    return "not adb", "it answered with something that is not ADB"


def ask_adb(sock):
    """Greet an open port: ("adb", command), ("not adb", why) or ("unknown", why).

    Silence is "unknown", not "not adb": a port that waits for more than this greeting could still be
    anything, and a stand-in or a half-open socket that cannot send says nothing about the TV either.
    """
    header = b""
    try:
        sock.getpeername()   # a socket with no peer (a test's stand-in) has nothing to greet
    except OSError as e:
        return "unknown", f"the greeting could not be sent ({e})"
    try:
        sock.settimeout(HANDSHAKE_S)
        sock.sendall(greeting())
        while len(header) < 24:
            chunk = sock.recv(24 - len(header))
            if not chunk:
                break
            header += chunk
    except socket.timeout:
        return short_reply(header) if header else ("unknown", f"it said nothing within {HANDSHAKE_S:.0f} s")
    except OSError as e:
        return "unknown", f"the greeting could not be sent ({e})"
    command = adb_answer(header)
    if command:
        return "adb", command
    if not header:
        return "not adb", "it hung up on ADB's greeting"
    return short_reply(header)


def this_computer(host):
    """127.0.0.1, localhost or another loopback address: where a relay listens, never the TV.

    Every way the socket API takes to write it counts: localhost with the root's trailing dot, the short
    IPv4 forms (127.1, 2130706433, 0x7f000001), ::1 and an IPv4-mapped ::ffff:127.0.0.1, and 0.0.0.0 or ::,
    which connect to this computer as well.
    """
    name = host.lower()
    if name.endswith("."):
        name = name[:-1]
    if name == "localhost" or name.endswith(".localhost"):
        return True
    try:
        address = ipaddress.ip_address(name.split("%", 1)[0])
    except ValueError:
        try:
            address = ipaddress.IPv4Address(socket.inet_aton(name))
        except (OSError, ValueError):
            return False
    # Before Python 3.13 an IPv4-mapped loopback is not is_loopback.
    if getattr(address, "ipv4_mapped", None):
        address = address.ipv4_mapped
    return address.is_loopback or address.is_unspecified


def split_host_port(endpoint):
    """Split host:port or [ipv6]:port into (host, port_or_none)."""
    if endpoint.startswith("["):
        end = endpoint.find("]")
        if end != -1:
            host = endpoint[1:end]
            rest = endpoint[end + 1:]
            if rest.startswith(":"):
                return host, rest[1:]
            return host, None
    elif endpoint.count(":") == 1:
        host, port = endpoint.split(":", 1)
        return host, port
    return endpoint, None


def address_problem(word):
    """Why this first word cannot be the TV's address, or None.

    A URL pasted from a browser was read as an address: `http://<tv ip>` split into the host "http" and
    the port "//<tv ip>", which answered "not a port number: '//<tv ip>'" - a sentence about a port
    fragment for a word that is an address of another kind - and `http://<tv ip>:5555` became a host
    literally named "http://<tv ip>:5555", whose lookup failed into the OSError branch's question about
    whether the TV is on, for a word this check cannot use. A page at the address (`<tv ip>:5555/`,
    `<tv ip>/6/system`) read the same way, and so did a flag from another tool (`--port 5555`, `-p 5555`),
    which named no host at all. All of them exit 2 already; naming the word is what tells the owner which
    of them to fix.
    """
    word = word.strip()
    # A flag copied over from another tool (tools/restore.sh and tools/theme.sh refuse a dash-led word for
    # the same reason): `--port 5555` and `-p 5555` went into getaddrinfo as a host named "--port", whose
    # lookup failed into the OSError branch's question about whether the TV is on and on this network - a
    # question about the TV's power for a word that names no host at all, at the step whose only answer
    # counts. No hostname starts with a dash, so the word is the mistake, and naming it costs one sentence.
    if word.startswith("-"):
        return ("that is a flag, not the TV's address: this check takes the IP (or IP:port) as its first "
                "word and the port as a second one, e.g.  tools/adb-closed.py <tv ip> 5555  (-h or --help "
                "on its own prints this text)")
    if "://" in word:
        return ("that is a URL, not the TV's address: this check asks the TV's own ADB port, so give the "
                "IP (or IP:port) alone, e.g.  tools/adb-closed.py <tv ip>")
    if "/" in word:
        return ("that is a path, not the TV's address: give the IP (or IP:port) alone, without a / or the "
                "page it opens, e.g.  tools/adb-closed.py <tv ip>")
    return None


def open_port_message(host, port, relay):
    """What an open port means: ADB for 5555 (and a relay's for a relay), the port itself for any other."""
    if port == ADB_PORT:
        return (f"{host}:{port} is still open: network ADB is on. On the TV, turn USB debugging,\n"
                "Network/ADB debugging and Wireless debugging off and check again, then reboot it\n"
                "and check once more.\n" + relay)
    return (f"{host}:{port} is still open: something answers there. 5555 is the ADB port, so an open one\n"
            "of those is network ADB. The port you gave may be the Wireless debugging port, which\n"
            "`adb mdns services` lists as `_adb-tls-connect._tcp`, or one this firmware runs adbd on.\n"
            "For those, turn USB debugging, Network/ADB debugging and Wireless debugging off, check\n"
            "again, then check 5555 after the reboot. A TV also keeps services of its own open whatever\n"
            "those toggles say - the JointSpace API on a Philips (1926, or 1925 on older sets),\n"
            "casting, a web page - and an open port like that says nothing about ADB. Check the ADB\n"
            "port with  tools/adb-closed.py " + host + " 5555\n" + relay)


def main():
    args = sys.argv[1:]
    if len(args) > 2 or args[:1] in (["-h"], ["--help"]):
        sys.stderr.write(__doc__)
        return 2
    raw_host = args[0] if args else os.environ.get("TV_HOST", "")
    if not raw_host:
        sys.stderr.write("usage: adb-closed.py <tv ip> [port]\n")
        return 2
    problem = address_problem(raw_host)
    if problem:
        sys.stderr.write(problem + "\n")
        return 2
    host, endpoint_port = split_host_port(raw_host)
    port_from_env = False
    if len(args) == 2:
        port_str = args[1]
    elif len(args) == 1 and endpoint_port is not None:
        port_str = endpoint_port
    else:
        port_from_env = not endpoint_port and bool(os.environ.get("TV_PORT"))
        port_str = endpoint_port or os.environ.get("TV_PORT") or "5555"

    try:
        port = int(port_str)
        if not (1 <= port <= 65535):
            raise ValueError
    except ValueError:
        sys.stderr.write(f"not a port number: {port_str!r}\n")
        return 2

    # relay.py's rule: a TV_PORT exported beside TV_HOST=127.0.0.1, or the relay's own 15555, is a relay's,
    # not the TV's. With the TV's IP typed at the closing step this would check IP:15555, which the TV
    # refuses, and say "closed" while 5555 is open. A TV_PORT exported beside the TV's own IP (5877 on the
    # Wanbo T6R Max) still counts.
    env_host = split_host_port(os.environ.get("TV_HOST", ""))[0]
    local = this_computer(host)
    if port_from_env and not local and (port == RELAY_PORT or this_computer(env_host)):
        sys.stderr.write(f"TV_PORT={port} is a relay's port, set for the tools: unset TV_HOST TV_PORT in this "
                         f"terminal, or give the TV's port, e.g.  tools/adb-closed.py {host} 5555\n")
        return 2
    # Through a relay the answer is the relay's: "still open" while it runs, refused once it stops,
    # whatever the TV's own port is doing. So a refusal on this computer is never the TV's.
    relay = (f"{host} is this computer: a relay answers for itself, not for the TV. Check the TV's own IP\n"
             "from an ordinary terminal:  tools/adb-closed.py <tv ip>\n" if local else "")

    connect_host = host
    try:
        connect_host = socket.inet_ntoa(socket.inet_aton(host))
    except (OSError, ValueError):
        pass

    sock = None
    try:
        sock = socket.create_connection((connect_host, port), timeout=TIMEOUT_S)
    except ConnectionRefusedError:
        if local:
            sys.stderr.write(f"could not check {host}:{port}: nothing listens there, which says nothing about "
                             "the TV.\n" + relay)
            return 2
        print(f"{host}:{port} is closed: the TV refused the connection.")
        return 0
    except socket.timeout:
        # No answer is not a refusal, but it was exit 0 here. Port 5555 times out with ADB still on when
        # the TV is off, asleep or rebooting, at a new address, on another VLAN, behind a VPN's firewall or
        # out of an assistant's sandbox (docs/TROUBLESHOOTING.md, tools/relay.md), so the closing step would
        # be told "closed" for a TV this never reached.
        sys.stderr.write(f"could not check {host}:{port}: it did not answer in {TIMEOUT_S:.0f} s, and that "
                         "proves nothing. The TV\nmay be off, asleep or still rebooting, at another address, "
                         "or out of reach from here (a VPN,\nanother VLAN, an assistant's sandbox: run this "
                         "from an ordinary terminal). Only a refused\nconnection shows the port closed.\n"
                         + relay)
        return 2
    except UnicodeError:
        # getaddrinfo runs the name through the idna codec, which raises UnicodeError on an empty or
        # over-long label (192.168.1..50, a 70-character label) instead of the gaierror an unresolvable
        # name gives. Left uncaught it printed a traceback and exited 1 - the code this tool reserves for
        # "the port is still open" - so a typo in the address read as an open port at the closing step.
        sys.stderr.write(f"could not check {host}:{port}: not a hostname. Check the address.\n")
        return 2
    except OSError as e:
        # macOS Local Network privacy denies a process the local subnet and the kernel reports that as
        # EHOSTUNREACH, the same errno as a TV that is switched off, so "is the TV on?" sends people
        # rebuilding a network that was never broken. tools/tv.py says so at connect time since #1982; this
        # is the other client, and the one the closing step depends on (PROMPT.md, AGENTS.md and the README
        # accept only its "closed"), so an assistant on a Mac whose terminal was never granted the
        # permission was told the TV was off or on another network while the port it was asked about may
        # be open or closed: nothing was learned about it either way. Exit 2 already means that.
        note = ""
        if sys.platform == "darwin" and e.errno == errno.EHOSTUNREACH:
            note = ("On macOS this is also what Local Network privacy looks like. Allow the app that runs "
                    "this check (Terminal, iTerm2, VS Code) under System Settings > Privacy & Security > "
                    "Local Network, then quit and reopen it; granting it to python or adb does nothing. To "
                    "tell the two apart, try the default gateway: if it answers while every other address "
                    "on the subnet gives this error, it is the permission and not the TV. Nothing was "
                    "learned about the port.\n")
        sys.stderr.write(f"could not check {host}:{port} ({e}). Is the TV on and on this network?\n" + note)
        return 2
    # An open port is judged by which port it is. 5555 is where adbd listens by default, so an open one of
    # those is network ADB; a port handed in is the owner's choice - Wireless debugging's own (which stays
    # open when USB debugging goes off, so the advice names it too, and the check comes before the reboot
    # because a reboot leaves it off or on a new port), or one this firmware runs adbd on. Any other port
    # belongs to a service the toggles do not touch: a Philips keeps its JointSpace API open on 1926 and
    # 1925 whatever they say (tools/philips-api.py reads it there), so saying "network ADB is on" for it
    # sent the owner of a 58PUS7304 hunting a setting that was already off (#3890).
    # Then ask it. On this computer a relay answers for itself, so the answer is not the TV's: keep the
    # relay's message there. Elsewhere an ADB reply settles "network ADB is on" for any port, and a reply
    # that is not ADB says the port belongs to something else, so an owner whose TV keeps a service open is
    # told so instead of hunting a setting that is already off (#3890). The exit code stays the open port's:
    # only a refusal shows a port closed.
    verdict, detail = ("unknown", "") if local else ask_adb(sock)
    sock.close()
    if verdict == "adb":
        sys.stderr.write(f"{host}:{port} is still open, and it is network ADB: it answered ADB's greeting with "
                         f"{detail}.\nOn the TV, turn USB debugging, Network/ADB debugging and Wireless debugging "
                         "off and check again,\nthen reboot it and check 5555 once more.\n")
        return 1
    if verdict == "not adb":
        sys.stderr.write(f"{host}:{port} is still open, but it is not ADB: {detail}. Network ADB is not on this "
                         "port.\n")
        if port != ADB_PORT:
            sys.stderr.write(f"5555 is the ADB port: check it with  tools/adb-closed.py {host} 5555\n")
        return 1
    sys.stderr.write(open_port_message(host, port, relay))
    return 1


if __name__ == "__main__":
    sys.exit(main())
