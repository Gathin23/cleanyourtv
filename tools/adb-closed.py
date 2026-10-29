#!/usr/bin/env python3
"""Check that the TV's network ADB port is closed, after turning debugging off (no nc needed).

    adb-closed.py <tv ip[:port]> [port]   port defaults to TV_PORT or 5555; the IP to TV_HOST

Exits 0 when the port refuses the connection or does not answer, 1 when it is still open, and 2
when the check itself could not run (a name that does not resolve, no route to the TV).
"""
import os
import socket
import sys

TIMEOUT_S = 3.0


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


def main():
    args = sys.argv[1:]
    if len(args) > 2 or args[:1] in (["-h"], ["--help"]):
        sys.stderr.write(__doc__)
        return 2
    raw_host = args[0] if args else os.environ.get("TV_HOST", "")
    if not raw_host:
        sys.stderr.write("usage: adb-closed.py <tv ip> [port]\n")
        return 2
    host, endpoint_port = split_host_port(raw_host)
    if len(args) == 2:
        port_str = args[1]
    elif len(args) == 1 and endpoint_port is not None:
        port_str = endpoint_port
    else:
        port_str = endpoint_port or os.environ.get("TV_PORT") or "5555"

    try:
        port = int(port_str)
        if not (1 <= port <= 65535):
            raise ValueError
    except ValueError:
        sys.stderr.write(f"not a port number: {port_str!r}\n")
        return 2

    try:
        with socket.create_connection((host, port), timeout=TIMEOUT_S):
            pass
    except ConnectionRefusedError:
        print(f"{host}:{port} is closed: the TV refused the connection.")
        return 0
    except socket.timeout:
        # Some firmware drops instead of refusing, but so does a TV that is off or at another address.
        print(f"{host}:{port} did not answer in {TIMEOUT_S:.0f} s: closed, unless the TV is off or not at "
              "this address.")
        return 0
    except OSError as e:
        sys.stderr.write(f"could not check {host}:{port} ({e}). Is the TV on and on this network?\n")
        return 2
    sys.stderr.write(f"{host}:{port} is still open: network ADB is on. On the TV, turn USB debugging and\n"
                     "Network/ADB debugging off, reboot it, and check again.\n")
    return 1


if __name__ == "__main__":
    sys.exit(main())
