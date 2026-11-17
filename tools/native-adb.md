# Use a paired Google ADB connection

The Python client remains the default. If the TV offers encrypted Wireless debugging,
`adb_shell` cannot speak its TLS protocol. The optional Google ADB backend lets the existing
tools use that connection without opening a plaintext ADB port.

Install Google's platform-tools and the usual `tools/requirements.txt` dependencies first.
On the TV, enable Wireless debugging. Pair with the address, port and code from **Pair device
with pairing code**, then connect to the separate address and port on the main Wireless
debugging screen:

```bash
adb pair <tv-ip>:<pairing-port>
adb connect <tv-ip>:<connection-port>
export TV_ADB_BINARY=/absolute/path/to/adb
export TV_HOST=<tv-ip>:<connection-port>
tools/venv/bin/python -I tools/tv.py --probe
```

Google ADB manages pairing and authentication; the tools neither read its private key nor
implement TLS. Keep the main screen's current connection port: it can change when debugging
is toggled or the TV restarts. `TV_HOST` and `TV_PORT` have their usual meaning. IPv6 endpoints
use `[address]:port`. The executable may also be a name on PATH, such as `adb`.

The backend requires an already connected, authorized transport. Every operation selects
that exact endpoint with `adb -s`; it never falls back to another TV or runs `adb tcpip`
to change the TV's debugging mode. Its `close()` leaves the shared Google ADB connection
and server running, so other tools using them keep working.

The setting is read by `tools/tv.py`, so every tool that reaches the TV through it uses the
backend: the scripts `tools/disable.sh`, `tools/restore.sh`, `tools/theme.sh` and
`launcher/build-and-install.sh`, and the Python tools, `tools/check.py` (the check after a
firmware update), `tools/privacy.py`, `tools/bench.py`, `tools/trace.py`,
`tools/theme_restart.py`, `tools/b64push.py`, `tools/recovery.py` and `tools/manage.py`.
Their existing safety checks still run, and on a set whose only network ADB is Wireless
debugging this is how the check after an update runs without opening the plaintext port with
`adb tcpip 5555`. Back up first and follow `PROMPT.md` before changing a TV. For example:

```bash
tools/venv/bin/python -I tools/tv.py --screenshot shots/before.png
```

When finished, switch Wireless debugging and any other enabled debugging modes off on the
TV and revoke the authorizations. Check the noted connection port with
`python3 tools/adb-closed.py <tv-ip> <connection-port>` before rebooting, then check that
debugging remains off after the reboot. Pairing codes, keys and screenshots stay private.

## macOS: a stock `adb` that cannot reach the LAN

On macOS 15 (Sequoia) the `adb` command starts its own server daemon (`adb fork-server server`) and
leaves the work to it. Local Network privacy grants the permission to the app that ran the command -
Terminal, iTerm2, VS Code - and that daemon outlives the command, which the report reads as the
grant not reaching it: `adb connect <tv-ip>:5555` answers `failed to connect to '<tv-ip>:5555': No
route to host (65)` while `tools/tv.py` in the same window reaches the TV. Reported on macOS Sequoia
in [#3336](https://github.com/Gathin23/cleanyourtv/issues/3336), which names no TV model.

The Python client needs nothing here, since it opens its own socket from the window that holds the
permission. The stock binary is what `TV_ADB_BINARY` runs for a Wireless debugging pairing, and what
`adb install` and `adb push` use. The relay below runs in that window instead: it listens on
loopback, which the Local Network check does not cover, and forwards to the TV from the process that
does hold the permission. Save it as `relay.py`:

```python
#!/usr/bin/env python3
"""Forward 127.0.0.1:<local port> to <tv ip>:<tv port>, for a stock adb blocked on macOS."""
import socket
import sys
import threading


def pipe(src, dst):
    try:
        while data := src.recv(65536):
            dst.sendall(data)
    except OSError:
        pass
    finally:
        for sock in (src, dst):
            sock.close()


def main():
    tv_host, tv_port, local_port = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
    server = socket.socket()
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("127.0.0.1", local_port))
    server.listen(8)
    print(f"relaying 127.0.0.1:{local_port} -> {tv_host}:{tv_port}", flush=True)
    while True:
        client, _ = server.accept()
        try:
            upstream = socket.create_connection((tv_host, tv_port), timeout=10)
            upstream.settimeout(None)  # the connect timeout must not cut an idle session off later
        except OSError as e:
            print(f"cannot reach {tv_host}:{tv_port}: {e} (is the TV on, and network debugging still on?)",
                  flush=True)
            client.close()
            continue
        threading.Thread(target=pipe, args=(client, upstream), daemon=True).start()
        threading.Thread(target=pipe, args=(upstream, client), daemon=True).start()


main()
```

An attempt that cannot reach the TV, refused or timed out, is printed and the relay goes back to
waiting for the next client, the same boundary `tools/relay.py` keeps around its connect. Without it
the error ends the program, so a TV that was in standby for one `adb` command leaves every later
command refused on the loopback port until the relay is started again.

Start it for the port `adb` has to reach, and give both `adb` and the tools the loopback address:

```bash
python3 relay.py <tv-ip> 5555 15555
adb connect 127.0.0.1:15555
export TV_ADB_BINARY=adb
export TV_HOST=127.0.0.1:15555
tools/venv/bin/python -I tools/tv.py --probe
```

A pairing goes through a second copy on the pairing port while the first one carries the connection,
for example `python3 relay.py <tv-ip> 37123 37123` and then `adb pair 127.0.0.1:37123`, since the two
ports are different. Two things to keep in mind, as it bridges rather than fixes the denial:

- It has to stay up for as long as `adb` is used. Stop it and the connection it carries reads as a
  TV that went away; `adb kill-server` reconnects through it only while it listens.
- What it forwards is the plaintext port, though only from `127.0.0.1`, so nothing else on the network
  reaches the TV through it. The Python client needs no relay at all, and a window that is itself
  denied by Local Network privacy still fails: that is the macOS bullet in
  [docs/TROUBLESHOOTING.md](../docs/TROUBLESHOOTING.md#connecting), not this one.
