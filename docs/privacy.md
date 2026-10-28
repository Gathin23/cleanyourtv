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
candidates in three groups, each with:

- its evidence — named by the firmware's own `voice_interaction_service`, a known component from
  [`examples/`](../examples/), or nothing but a matching name;
- whether the package declares `RECORD_AUDIO`, and whether it shares the system UID;
- whether the returned AppOps history contains microphone access. A configured mode or a rejected
  attempt alone is not evidence of access.

`--write privacy.txt` turns the identified candidates into a normal debloat list, so applying it and
undoing it are the commands you already use (`tools/disable.sh`, `tools/restore.sh`). Packages that
only matched by name stay out unless you pass `--include-low`; they are the ones to check with
`dumpsys package <name>` first.

What the audit cannot do is prove intent. `appops` shows an op was touched, not what happened to the
stream afterwards, and no scan of package names proves a TV is or is not recording. Disabling an
assistant is a trade you make with a specific loss (the remote's mic button, voice search inside
apps), which is why the report names that loss per package instead of calling it a win.
