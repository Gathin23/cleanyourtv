#!/usr/bin/env python3
"""Forward a port on this computer to the TV's ADB port, for assistants whose sandbox can't reach the network.

Some coding assistants run their commands in a sandbox that may talk to this computer but not to other devices
on the local network (Claude Code on macOS, where Local Network privacy blocks it). Run this in an ordinary
terminal, then point the tools at it:

    tools/venv/bin/python -I tools/relay.py 192.168.1.50          # leave it running
    TV_HOST=127.0.0.1 TV_PORT=15555 tools/venv/bin/python -I tools/tv.py --probe

Usage: relay.py [TV_HOST[:PORT]] [--tv-port 5555] [--port 15555]
The TV's port is --tv-port, else the one in the address, else TV_PORT, else 5555.
It listens on 127.0.0.1 only, never on the network. Read tools/relay.md first.
"""
import argparse
import os
import socket
import sys
import threading

LISTEN = "127.0.0.1"   # loopback only: the relay must never put the TV's ADB port on the network


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


def pipe(src, dst):
    """Copy one direction until either side closes, then close both directions."""
    try:
        while True:
            data = src.recv(65536)
            if not data:
                break
            dst.sendall(data)
    except OSError:
        pass
    finally:
        for s in (src, dst):
            try:
                s.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass


def serve(srv, tv_host, tv_port):
    while True:
        client, _ = srv.accept()
        try:
            tv = socket.create_connection((tv_host, tv_port), timeout=10)
            tv.settimeout(None)
        except OSError as e:
            print(f"cannot reach {tv_host}:{tv_port}: {e} (is the TV on, and network debugging still on?)",
                  flush=True)
            client.close()
            continue
        for a, b in ((client, tv), (tv, client)):
            threading.Thread(target=pipe, args=(a, b), daemon=True).start()


def checked_port(p, flag, port, lowest):
    """A port this script can bind or connect to, or the parser's refusal: --port may be 0, the TV's port may not."""
    if port is not None and not lowest <= port <= 65535:
        hint = " (0 picks one)" if lowest == 0 else ""
        p.error(f"{flag}: not a port number: {port!r}{hint}")
    return port


def main():
    p = argparse.ArgumentParser(description="Forward 127.0.0.1:PORT to the TV's ADB port. See tools/relay.md.")
    p.add_argument("tv_host", nargs="?", default=os.environ.get("TV_HOST"),
                   help="the TV's IP address, or IP:port (default: $TV_HOST)")
    p.add_argument("--tv-port", type=int,
                   help="the TV's ADB port (default: the port in the address, else $TV_PORT, else 5555)")
    p.add_argument("--port", type=int, default=15555, help="local port to listen on (default 15555; 0 picks one)")
    args = p.parse_args()
    if not args.tv_host:
        p.error("give the TV's IP address, e.g.  tools/venv/bin/python -I tools/relay.py 192.168.1.50")
    # Both options are ports, and neither was checked: --port went to bind() as typed, and bind() refuses a
    # port above 65535 (or a negative one) with an OverflowError - not an OSError, so the "cannot listen"
    # message below never saw it - while --tv-port skipped the 1..65535 check the address and TV_PORT get a
    # few lines further down. socket.create_connection cuts a servname to 16 bits, so "--tv-port 70000" printed
    # "-> <tv>:70000" and forwarded to <tv>:4464, whatever service is there.
    args.port = checked_port(p, "--port", args.port, 0)
    args.tv_port = checked_port(p, "--tv-port", args.tv_port, 1)
    # The same order as tv.py and adb-closed.py (#997). With 5555 fixed, a TV whose adbd has a port of its
    # own (5877 on the Wanbo T6R Max) was relayed to 5555 whatever TV_HOST and TV_PORT said.
    args.tv_host, endpoint_port = split_host_port(args.tv_host)
    port_from_env = args.tv_port is None and not endpoint_port and bool(os.environ.get("TV_PORT"))
    if args.tv_port is None:
        port_str = endpoint_port or os.environ.get("TV_PORT") or "5555"
        try:
            args.tv_port = int(port_str)
            if not (1 <= args.tv_port <= 65535):
                raise ValueError
        except ValueError:
            p.error(f"not a port number: {port_str!r}")
    # TV_HOST=127.0.0.1 TV_PORT=15555 exported for the tools, then the relay started in that same terminal: that
    # TV_PORT is a relay's (this one's, or the one TV_HOST points at), not the TV's, and with the TV's IP given
    # the relay would forward to IP:15555.
    loopback = ("127.0.0.1", "localhost")
    env_host = split_host_port(os.environ.get("TV_HOST", ""))[0]
    if port_from_env and (args.tv_port == args.port or (env_host in loopback and args.tv_host not in loopback)):
        p.error(f"TV_PORT={args.tv_port} is a relay's port, set for the tools: unset TV_HOST TV_PORT in this "
                "terminal, or give the TV's port, e.g.  tools/venv/bin/python -I tools/relay.py 192.168.1.50:5555")
    if args.tv_host in loopback and args.tv_port == args.port:
        p.error("that is the relay itself: give the TV's IP address")

    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        srv.bind((LISTEN, args.port))
    except OSError as e:
        sys.exit(f"cannot listen on {LISTEN}:{args.port}: {e}. Is a relay already running? Pick another --port.")
    srv.listen(8)
    port = srv.getsockname()[1]
    print(f"relay {LISTEN}:{port} -> {args.tv_host}:{args.tv_port}", flush=True)
    print(f"use  TV_HOST={LISTEN} TV_PORT={port}  with tools/tv.py and the other tools; Ctrl-C stops it", flush=True)
    try:
        serve(srv, args.tv_host, args.tv_port)
    except KeyboardInterrupt:
        print("relay stopped", flush=True)
    finally:
        srv.close()


if __name__ == "__main__":
    main()
