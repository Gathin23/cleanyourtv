# After a firmware update

[Back to the README](../README.md#after-a-firmware-update)

An update installs over the cleaned-up system: the listed packages come back, Home goes to the
stock launcher, and new preloads appear that no list mentions. Nothing else here notices, so
compare the TV with the list you applied.

## Open ADB again, then check

The check and `--fix` reach the TV over network ADB. If you closed ADB as
[Isn't ADB access a security hole?](faq.md#isnt-adb-access-a-security-hole) describes, open it again first: USB
debugging, and Network/ADB debugging if the TV has it, back on as in [Quick start](../README.md#quick-start)
step 1. On a TV whose port 5555 only opens through Wireless debugging, switch that on as well, pair
it again with the stock `adb` and run `adb tcpip 5555`, as in [Doing it by hand](../README.md#doing-it-by-hand).
Closing revoked this computer's authorisation, so accept "Allow USB debugging?" on the TV when the
check connects. While ADB is closed the check can only report "could not reach the TV", even with
the TV on and `TV_HOST` right. Close ADB the same way once you are done. With it open:

```bash
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py examples/<your tv>/debloat.txt
```

On Windows the same command is `tools\venv\Scripts\python.exe -I tools\check.py <list>`.

## Exit codes, and a weekly job

It exits with `0` when the TV still matches the list, `1` when something drifted, and `2` when it
could not check at all: the TV did not answer, nobody accepted the "Allow USB debugging?" prompt in
time, the list path is not there, the list disables Android's core packages, or, under `--fix`, the
recovery record for this TV and list cannot be read or updated (nothing is changed on the TV then).
Only 1 is drift, so
a job that mails on 1 alone stays quiet while the check never ran; mail on anything but 0, or on 2 as
well, and a weekly job on a home server or a Pi is enough to hear about it:

```bash
0 9 * * 1 cd ~/cleanyourtv && TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py examples/<your tv>/debloat.txt
```

`TV_HOST` is read before any of that: a run that leaves it unset stops in `tools/tv.py` with its own
message and exits 2, the code for a check that never ran, so set it in the job line, as above, where
cron does not inherit it from your shell. A job on a computer where the tools' venv is gone answers 2
the same way, naming the line that builds it.

The job only gets an answer from a TV that keeps network ADB listening between runs and has
accepted "Allow USB debugging?" for the computer that runs it with "Always allow from this computer"
ticked: without the tick the TV asks again at every run, and with nobody there to accept, the run
exits 2. A port left listening is the one [Isn't ADB access a security hole?](faq.md#isnt-adb-access-a-security-hole)
says to close, since anything on the network can reach it. With ADB closed as described there,
every run exits 2: run the check by hand after an update instead, with ADB opened again as above.

## Putting it back with `--fix`

`--fix` reapplies the list to what drifted: it switches Home to the launcher the list does not
disable (pass a package as the second argument if you run a fork), then disables the listed packages
that came back, and reads the TV back to confirm both. It never writes a recovery record of its own,
because only the first run's record holds the state from before cleanup. When there is one, `--fix`
first adds to it what a second `tools/disable.sh` run would (names the list gained, and a package that
was not installed then and is installed and enabled now), so `tools/restore.sh` still returns the TV
to stock afterwards. Home is fixed first; if no launcher is left to take it, `--fix` changes nothing
and says so.

It runs the same safety checks as `tools/disable.sh` before it disables anything, since an update is
the moment they matter again: it leaves setupwraith on while the saved Wi-Fi network belongs to it
(`FORCE_SETUPWRAITH=1` on Ethernet), leaves the packages under the HDMI/AV inputs on
(`TVINPUT_ALLOW=1` if you really want them off), leaves on the apps the TV opens its inputs and
Settings with (`HANDLERS_ALLOW=1`), and never takes Home to a vendor `FallbackHome` or a factory-test
app.
Each package it leaves on comes with the reason and exits 1, because the TV is not back to the list
until you deal with that one by hand.

## When the stock launcher stays Home on purpose

With `stock_home_redirect` the stock launcher can stay Home on purpose: the list leaves it out, the
install script ran with `STOCK_LAUNCHER=none`, Android keeps it as Home, and `HomeKeyService` sends
its home screen on to this launcher. On the Chromecast with Google TV 4K that setup matches the
folder's `debloat-keep-cast.txt`, not its `debloat.txt`, which disables setupwraith and the
screensaver pair the redirect setup keeps. On the Acer AR55AR2851UDPRO it is the folder's
`debloat.txt`. Check those lists with the stock launcher as the second argument. Without it the
check expects `com.gathin.tvhome` on Home, so every run reports Home as drift, and `--fix` tries to
move Home to it, which those TVs do not keep:

```bash
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py examples/google-chromecast-gtv-4k/debloat-keep-cast.txt com.google.android.apps.tv.launcherx
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py examples/acer-ar55ar2851udpro/debloat.txt com.google.android.tvlauncher
```

Leave the argument out where the stock launcher is meant to be off. Where the install script
disables it, as with the Chromecast's `debloat.txt` and the XGIMI MoGo 3 Pro list (there the
redirect only covers for firmware that turns it back on), the stock launcher on Home is drift, and
naming it as the second argument would hide that drift. Where the list disables it itself, as the
XGIMI MoGo 4's does, `check.py` refuses that argument with exit 2 and checks nothing: a launcher the
list disables cannot be the one Home stays on.

## Lists that disable the updater

Lists that also disable the updater get no updates at all. To install one, re-enable the updater on
the TV, let it update, run `check.py --fix`, and disable the updater again.
