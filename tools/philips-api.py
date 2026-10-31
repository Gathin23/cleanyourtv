#!/usr/bin/env python3
"""Check that a Philips TV's JointSpace control API still answers, before and after a debloat.

    philips-api.py [tv ip]      the IP defaults to TV_HOST

Exits 0 when the API answers; 1 when a port answered and it is not the JointSpace API (the package to
check is named); and 2 when this run could not tell - nothing answered on either port, so the TV may be
off, asleep, at another address or out of reach from here - or the command line was not one address.

A port that refuses the connection is the TV answering: nothing listens there, which is what a disabled
org.droidtv.xtv looks like. A port that times out, answers "No route to host", or a name that does not
resolve is the network between here and the TV, and says nothing about the package.

Philips Android TVs are driven from Home Assistant (the built-in philips_js integration), openHAB and
the like over the JointSpace API. It is served by org.droidtv.xtv, which is in the DO NOT DISABLE block
of examples/philips-50pus8517; a TV that loses it drops out of Home Assistant, and nothing on the TV's
screen says why. Run this once before the first batch and again after the last.

Two things trip people up, and this handles both:

  * On Android TV sets the API is on port 1926 over HTTPS, not the 1925 that older guides name. 1925 may
    be open and answer nothing (a 77OLED806/12 on Android TV 11 does).
  * The certificate is self-signed, so it is not verified here, as Home Assistant does not verify it
    either. This only reads from a TV on your own network.

/6/system answers without pairing, so this needs no credentials and changes nothing on the TV. The other
endpoints answer 401 until a client has done the digest-auth pairing, and for this check a 401 is the good
answer: the endpoint is there.
"""
import errno
import json
import os
import socket
import ssl
import sys
import urllib.error
import urllib.request

PORTS = ((1926, "https"), (1925, "http"))
PROBE = ("ambilight/topology", "ambilight/power", "ambilight/mode", "ambilight/currentconfiguration",
         "powerstate")
# errno values that mean this computer never reached the TV: a timeout, a network that is not the
# set's, a host that is gone. What the port said in those cases is about the route, not the TV.
UNREACHED = (errno.EHOSTUNREACH, errno.ENETUNREACH, errno.EHOSTDOWN, errno.ENETDOWN, errno.ETIMEDOUT,
             errno.ECONNABORTED)


def answered(failure):
    """True when the TV answered at the TCP level, so what the port said is about the TV.

    A connection the set refuses is its own reset: nothing is listening on that port, which is what a
    disabled org.droidtv.xtv looks like. A timeout, a "No route to host" or a name that does not
    resolve is this computer's network, or a set that is off, asleep or at another address, and it
    says nothing either way about the package: on a TV that is asleep every port looks like this.
    """
    if isinstance(failure, urllib.error.URLError):    # urlopen wraps what the socket raised
        failure = failure.reason
    if isinstance(failure, (socket.gaierror, TimeoutError)):
        return False
    if isinstance(failure, OSError):
        return failure.errno not in UNREACHED
    return False


def get(url, timeout=8):
    """(status, body, failure); status is None when nothing answered and body then says why."""
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    try:
        with urllib.request.urlopen(url, timeout=timeout, context=ctx) as r:
            return r.status, r.read().decode("utf-8", "replace"), None
    except urllib.error.HTTPError as e:
        return e.code, "", None
    except Exception as failure:
        return None, str(failure), failure


def system_info(body):
    """The decoded /6/system object, or None when the body is not one (an HTML page, broken JSON, {})."""
    try:
        info = json.loads(body)
    except ValueError:
        return None
    if isinstance(info, dict) and isinstance(info.get("api_version"), dict):
        return info
    return None


def section(parent, key):
    """parent[key] when it is an object, else {}: optional metadata of the wrong type counts as unknown."""
    value = parent.get(key)
    return value if isinstance(value, dict) else {}


def find_api(host):
    """(base URL, decoded /6/system, whether any port answered at all), or (None, None, reached).

    Prints each port it tried. `reached` is what tells "the TV is there and the API is not" (the
    package to check) from "this computer never reached the TV" (nothing was learned).
    """
    reached = False
    for port, scheme in PORTS:
        base = f"{scheme}://{host}:{port}"
        status, body, failure = get(f"{base}/6/system")
        if status is not None or answered(failure):
            reached = True
        info = system_info(body) if status == 200 else None
        if info is not None:
            return base, info, True
        why = status if status is not None else body
        print(f"  {base}/6/system -> {why}{' (not the API)' if status == 200 else ''}")
    return None, None, reached


def main(argv):
    args = argv[1:]
    host = args[0] if args else os.environ.get("TV_HOST", "")
    if len(args) > 1 or not host or host.startswith("-"):
        print(__doc__.split("\n\n")[1], file=sys.stderr)
        return 2

    base, info, reached = find_api(host)
    if not base:
        if not reached:
            # The run used to end here with the advice below and exit 1, which is what "the TV is
            # there and org.droidtv.xtv is off" looks like: a set that is asleep, on another VLAN or
            # behind a VPN's firewall sent the owner hunting for a package nothing had touched.
            ports = " and ".join(str(port) for port, _ in PORTS)
            print(f"could not check {host}: nothing answered on {ports}, and that says nothing about "
                  "the TV.\nNothing is known about org.droidtv.xtv: the set may be off, asleep or still "
                  "starting, at\nanother address, or out of reach from here (a VPN, another VLAN, an "
                  "assistant's sandbox).\nRun this from an ordinary terminal on the same network, check "
                  "the address, and run it again.", file=sys.stderr)
            return 2
        print(f"\nNo JointSpace API. If this TV used to work with Home Assistant, check that\n"
              f"org.droidtv.xtv is still enabled:\n"
              f"  TV_HOST={host} tools/tv.py 'pm list packages -d' | grep droidtv")
        return 1

    print(f"API on {base}")
    api = info["api_version"]
    feat = section(info, "featuring")
    ambilight = section(feat, "jsonfeatures").get("ambilight")
    print(f"  name           {info.get('name')}")
    print(f"  api_version    {api.get('Major')}.{api.get('Minor')}.{api.get('Patch')}")
    print(f"  pairing        {section(feat, 'systemfeatures').get('pairing_type')}")
    print(f"  ambilight      {ambilight}")
    if isinstance(ambilight, list) and "LoungeLight" in ambilight:
        print("                 (LoungeLight is Ambilight with the panel off. For it to answer while\n"
              "                  the TV is off, turn on Eco settings > Quick start.)")

    print("\nendpoints (401 = there, waiting for pairing):")
    for path in PROBE:
        status, _, _ = get(f"{base}/6/{path}")
        verdict = {200: "open", 401: "there", 404: "MISSING"}.get(status, str(status))
        print(f"  {path:32s} {verdict}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
