# tools/relay.py

Forwards a port on this computer to the TV's ADB port. Use it when your coding assistant runs its commands in
a sandbox that can reach this computer but not other devices on your network. Claude Code on macOS is the
usual case: Local Network privacy blocks its sandbox, so `tools/tv.py` times out or says "No route to host"
even though the TV answers from an ordinary terminal.

```bash
# In an ordinary terminal (Terminal, iTerm, VS Code), and leave it running:
tools/venv/bin/python -I tools/relay.py 192.168.1.50

# Then, wherever the assistant runs the tools:
export TV_HOST=127.0.0.1 TV_PORT=15555
tools/venv/bin/python -I tools/tv.py --probe
```

- **Options:** `--tv-port` if the TV's ADB port isn't 5555; `--port` for another local port (run one relay
  per TV, each on its own port). The TV's address defaults to `$TV_HOST`.
- **It prints the `TV_HOST`/`TV_PORT` to use**, and one line each time it can't reach the TV.
- **Stop it with Ctrl-C** when you're done.

## Safety

- **It listens on `127.0.0.1` only**, never on your network, and has no option to change that. Other
  devices can't use it to reach your TV.
- **Programs on this computer can reach the TV's ADB port through it while it runs.** ADB still needs a key
  the TV has accepted ("Allow USB debugging?"), but stop the relay when you're done, as you'd close network
  ADB itself.

## When it fails

- **"cannot reach 192.168.1.50:5555"**, printed by the relay: the TV is off or in standby (some TVs drop off
  the network), on another network, or network debugging was switched off. The tools then see the connection
  reset by peer.
- **"cannot listen on 127.0.0.1:15555"**: a relay is already running on that port. Use it, or pick another
  `--port`.
- **"that is the relay itself"**: the TV's address was given as `127.0.0.1` with the relay's own port. Give
  the TV's IP address.
