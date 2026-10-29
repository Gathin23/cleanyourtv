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

The setting is inherited by `tools/disable.sh`, `tools/restore.sh`, `tools/privacy.py`,
`tools/theme.sh` and `launcher/build-and-install.sh`; their existing safety checks still
run. Back up first and follow `PROMPT.md` before changing a TV. For example:

```bash
tools/venv/bin/python -I tools/tv.py --screenshot shots/before.png
```

When finished, switch Wireless debugging and any other enabled debugging modes off on the
TV and revoke the authorizations. Check the noted connection port with
`python3 tools/adb-closed.py <tv-ip> <connection-port>` before rebooting, then check that
debugging remains off after the reboot. Pairing codes, keys and screenshots stay private.
