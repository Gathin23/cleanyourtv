#!/usr/bin/env python3
"""Forward a port on this computer to the TV's ADB port, for assistants whose sandbox can't reach the network.

Some coding assistants run their commands in a sandbox that may talk to this computer but not to other devices
on the local network (Claude Code on macOS, where Local Network privacy blocks it). Run this in an ordinary
terminal, then point the tools at it:

    tools/venv/bin/python -I tools/relay.py 192.168.1.50          # leave it running
    TV_HOST=127.0.0.1 TV_PORT=15555 tools/venv/bin/python -I tools/tv.py --probe

Usage: relay.py [TV_HOST] [--tv-port 5555] [--port 15555]
It listens on 127.0.0.1 only, never on the network. Read tools/relay.md first.
"""
import argparse
import os
import socket
import sys
import threading

LISTEN = "127.0.0.1"   # loopback only: the relay must never put the TV's ADB port on the network


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


def main():
    p = argparse.ArgumentParser(description="Forward 127.0.0.1:PORT to the TV's ADB port. See tools/relay.md.")
    p.add_argument("tv_host", nargs="?", default=os.environ.get("TV_HOST"),
                   help="the TV's IP address (default: $TV_HOST)")
    p.add_argument("--tv-port", type=int, default=5555, help="the TV's ADB port (default 5555)")
    p.add_argument("--port", type=int, default=15555, help="local port to listen on (default 15555; 0 picks one)")
    args = p.parse_args()
    if not args.tv_host:
        p.error("give the TV's IP address, e.g.  tools/relay.py 192.168.1.50")
    if args.tv_host in ("127.0.0.1", "localhost") and args.tv_port == args.port:
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
