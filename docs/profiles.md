# Profiles: one setup, several TVs

A profile is the list of what a household's TVs should look like: the apps the master unit has, and
the packages it has disabled. Capture it once from the unit that is already set up, then check or
converge any other unit of the same model against it. Two Chromecast with Google TV 4K in one house
end up with the same apps and the same cleanup, without doing the work twice.

**The master is the unit you already set up.** Whatever its third-party apps and disabled packages
are, they become the profile.

## Capture the master

```bash
TV_HOST=<master ip> tools/venv/bin/python -I tools/profile.py capture profiles/sabrina
```

That writes three files:

| File | What it holds |
|---|---|
| `apps.txt` | The master's third-party packages, one per line: what the other unit should have. |
| `debloat.txt` | The master's disabled packages, in the format [`tools/disable.sh`](../tools/disable.sh) and [`tools/restore.sh`](../tools/restore.sh) already take. |
| `profile.json` | Model, device, SDK and capture date, so a profile for the wrong set is obvious. |

Capture sends no command that changes the TV.

## Check any unit against it

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/profile.py check profiles/sabrina
```

Exit `0` when the unit matches, `1` when it drifted, `2` when the run could not check at all, the
same three codes [`tools/check.py`](../tools/check.py) uses. A drifted report names the packages:
`missing` (the profile has it, this unit does not), `not in the profile` (this unit has an app the
master does not), `should be disabled`, and `disabled but the profile installs it`. A check changes
nothing.

## Converge a second unit to the master

```bash
TV_HOST=<target ip> tools/venv/bin/python -I tools/profile.py sync profiles/sabrina --source <master ip>
```

`TV_HOST` is the unit that changes, `--source` the master, as with every other tool in `tools/`.
Without `--apply` the run prints the plan and exits `1`; with it, the run:

- installs the profile's apps the target is missing, pulled from the master with `pm path` and
  installed with `pm install -r`, or for a split APK in one `pm install-create` / `install-write` /
  `install-commit` session. A package whose APK the master's shell cannot read, or whose install
  fails, is listed for a manual install and its files are left in a temporary folder;
- disables the packages the profile disables, and enables the ones the master has enabled;
- prints the undo commands for everything it disabled;
- reports the apps that are only on the target and leaves them alone.

Nothing is uninstalled, and no package the profile does not name is touched.

## What a profile does not carry

App data, accounts, DRM state and Wi-Fi credentials stay on each unit; this is a package list, not a
backup. Launcher colours, dock and layout live in a [theme](../themes/README.md), which
[`tools/theme.sh`](../tools/theme.sh) pushes to any unit; a profile and a theme pushed together are
the same home screen on both TVs.

**Paid and account-bound apps do not belong in a profile.** An APK copied from another unit carries no
licence: Play checks the purchase against the Google account signed in on *that* unit, so a paid app
arrives and then refuses to run, as tvQuick Actions Pro does. Install those from the Play Store on each
unit with the account that bought them, and keep them out of `apps.txt` — a line commented out there is
read as "not part of this profile" and is never installed. The same applies to a patched or re-signed
package built on one unit: `app.revanced.android.gms` is left out of `profiles/master/` for that reason.

## After a firmware update

A firmware update re-enables packages and can hand Home back to the stock launcher. Run `check` to
see it, then `sync --source` from the master, or `tools/venv/bin/python -I tools/check.py --fix` on a single
unit that holds its own list.
