# The microphone, and what can be said about it

[Back to the README](../README.md#the-microphone-and-what-can-be-said-about-it)

"Which of these packages is listening?" has no honest one-line answer, and a permission scan makes
it worse:

- On many sets most packages run as `android.uid.system`, so fifty of them show
  `RECORD_AUDIO: granted=true` and none of them record anything.
- On others the microphone is in the remote, not the panel, and a package with no audio permission at
  all still sees everything on screen — ACR clients such as `tv.inscape.tvclient.inscapeacr` identify
  content from the picture and need only `INTERNET`.

`tools/privacy.py` reports what the TV actually says instead of guessing:
`TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/privacy.py` prints voice, ACR and telemetry
candidates in up to four groups — the three kinds it can name a job for, and a fourth headed
"Matched by name only, nothing else known" — each with:

- its evidence — named by the firmware's own `voice_interaction_service`, a known component from
  [`examples/`](../examples/), or nothing but a matching name;
- whether the package declares `RECORD_AUDIO`, and whether it shares the system UID;
- whether the returned AppOps history contains microphone access. A configured mode or a rejected
  attempt alone is not evidence of access.

`--write privacy.txt` turns the identified candidates into a normal debloat list, so applying it
and undoing it are the commands you already use (`tools/disable.sh`, `tools/restore.sh`). Packages
in that fourth group, the ones that only matched by name, stay out unless you pass `--include-low`;
they are the ones to check with `dumpsys package <name>` first.

The audit reads `dumpsys package` for at most 25 packages, so on a TV with a longer list of name
matches the report shows the first 25 and says how many it left out, and `--write` writes only
those: check the rest by hand.

What the audit cannot do is prove intent. `appops` shows an op was touched, not what happened to the
stream afterwards, and no scan of package names proves a TV is or is not recording. Disabling an
assistant is a trade you make with a specific loss (the remote's mic button, voice search inside
apps), which is why the report names that loss per package instead of calling it a win.

## ACR, and blocking its servers

An ACR client does not always have a name that says so. On TCL's Google TV sets the Samba TV frame
server lives inside `com.tcl.ttvs`, next to the firmware updater, so a search of package names alone
misses it. `dumpsys package r | grep -i -E 'samba|acr'` lists the components that carry it, whatever
package they are in, and `pm list features | grep -i acr` shows whether the firmware switches the
feature on. The audit names the ACR packages known from [`examples/`](../examples/), and disabling
them is the fix: a client that is off sends nothing.

A DNS block is a second layer, for a client you decide to keep or one that comes back with an
update. Android TV has no hosts file you can edit without root, so the block goes on the network: a
Pi-hole or AdGuard Home that the router hands out as DNS, or a Private DNS resolver that takes your
own rules (a personal AdGuard DNS or NextDNS profile). The public AdGuard DNS servers from the
[FAQ](faq.md#can-it-block-ads-inside-apps-too) did not block Samba TV's `cid.samba.tv` hosts when
checked in November 2026. The second-level domains of the
[Watching the Watchers blocklist](https://github.com/IlgarNEU/Watching-the-Watchers/tree/main/Blocklist)
(Northeastern University, 2026), with the ACR service each one belongs to:

| Block | ACR service |
|---|---|
| `samba.tv` | Samba TV (Sony, Philips and TCL Google TV sets) |
| `alphonso.tv` | Alphonso (LG webOS, some Android TV boxes) |
| `samsungacr.com`, `acr-us-prd.samsungcloud.tv` | Samsung |
| `tvinteractive.tv` | Inscape (Vizio) |
| `acr.unruly.co` | Unruly (VIDAA) |
| `scribe.logs.roku.com` | Roku |

The same list says Samba TV also connects to a fixed address, `216.183.117.80`, which only a router
firewall rule stops. A TV that resolves names over its own DNS over HTTPS skips the DNS block
entirely, and vendors rename hosts, so check the resolver's log for new names rather than trusting the
list to stay complete.
