# Samsung Tizen: AU8000 reconnaissance and DU7000 custom-app testing

> **TL;DR: Don't spend time on this repo expecting to debloat a Samsung Tizen TV or replace
> Samsung Home. That workflow does not work here.** A separate custom Tizen app was tested, but
> the Home button still opened Samsung Home and a long-press restart returned to HDMI.
> Standby resume worked in one sequence; it is not a system launcher replacement.
> No safe system-app removal or full restorable backup was established.

One owner's reconnaissance from a Mac on the same network, not a port and not a tested list. It was
reported in [#441](https://github.com/Gathin23/cleanyourtv/issues/441) on 2026-10-20 by
[@csmith1133](https://github.com/csmith1133), with nothing changed on the TV. Nothing in `tools/`
speaks Tizen: `tools/tv.py` drives `adb`, and this set has no `adb` to connect to.

| Field | Observed value |
|---|---|
| Retail model | UN50AU8000FXZA, 50" AU8000, 2021 |
| Model string (`/api/v2/`) | `21_KANTSU2E_UHD_VOICE` |
| System | Tizen, US firmware |
| Network | Wi-Fi |

## AU8000: what answers without touching the TV

- SSDP replies as `Samsung-Linux/4.1, UPnP/1.0, Samsung_UPnP_SDK/1.0`, with a DMR on `9197` and
  `nservice` on `7678`.
- `GET http://<tv>:8001/api/v2/` returns the model, the power state, `TokenAuthSupport: true` and
  `developerMode: 0`.

| Port | State | What it is |
|---|---|---|
| 8001 | open | HTTP API and the remote-control websocket |
| 8002 | open | the same remote over TLS |
| 8080 | open | open in the report, purpose not identified |
| 9080 | open | open in the report, purpose not identified |
| 9197 | open | UPnP DMR (media rendering) |
| 7678 | open | `nservice` |
| 5555 | closed | no ADB on retail firmware |
| 26101 | closed | no `sdb` while Developer Mode is off |

## AU8000: what that rules out

- **No shell, so no `pm disable-user`.** Samsung's own apps cannot be disabled from outside on
  retail firmware, which is what every list in `examples/` is built on.
- **No screenshot.** `screencap` is part of that missing shell, and there is no capture API on 8001
  or 8002, so a script cannot see the TV to decide what the next key press should be.
- **No Home takeover.** No third-party app can take the Home button, so `launcher/` has nowhere to
  go here even if it could be installed.

## What a Tizen cleanup could still be

The remote on 8002 accepts key presses once the "Allow" prompt is accepted on the TV, and
`/api/v2/applications/<appId>` can launch an app, so a script can walk the settings menus the owner
named in the report: interest-based ads, viewing information services, autorun Smart Hub. That is a
key sequence with a person watching the screen, not a package list. Combined with DNS blocking at
the router, it is the realistic shape of a Tizen version of this repo: settings by remote keys plus
DNS, rather than debloat plus a launcher.

A port like that belongs in its own repository, with a row in [SHOWCASE.md](../../SHOWCASE.md), the
same as the [webOS launcher](https://github.com/OmkarK97/webos-vibehack).

## What the AU8000 report does not establish

- One set, one firmware, one region, one session. Tizen versions differ between years and markets,
  and none of this was checked on a second TV.
- Developer Mode was off, so the path through `sdb` on 26101 was not tested. A developer-mode set
  would still not have `adb`, and no `pm`-style package disabling was claimed.
- Whether the remote socket asks for the "Allow" prompt again after every TV reboot, and how long
  that grant lasts, is unmeasured.
- The app-launch endpoint was reported to work; which Smart Hub apps answer it, and whether it can
  reach a settings activity rather than only an app, is unmeasured.

If you have one of these sets and want to extend this page, a second unit on a different year or
region is the useful data point.

## DU7000: Developer Mode and a custom dashboard, tested on 2026-10-23

A second owner's session used a Samsung UA43DU7000UXEG, model string `24_KSUE_UB_T09`,
over Wi-Fi from an Apple Silicon Mac. SDB reported Tizen 9.0; the HTTP metadata API returned
`Unknown` for the firmware version, so the exact firmware build was not established.
This is a capability report, not a tested debloat list or a port of this repo's launcher.
No existing applications were removed, and no root or system-Home modification was attempted.

**The practical result:** a separately signed Tizen Web application could display a custom
full-screen interface, discover applications, launch selected applications and display a connected
HDMI source. It was not a replacement for Samsung Home. Distinguish these two goals before spending
time on the design.

### Network and developer access

These results are for the DU7000 with Developer Mode enabled and the Mac's address entered in the
Host PC IP field. A reachable port alone does not identify the service behind it.

| Port | DU7000 result | Verification | Limits |
|---|---|---|---|
| 8001 | open | HTTP `GET /api/v2/` returned model metadata | Remote-control websocket not tested |
| 8002 | open | HTTPS `GET /api/v2/` returned model metadata | Self-signed TLS certificate verification was bypassed for this local read only |
| 8080 | open | TCP connection | Service not identified |
| 9080 | closed or unreachable | TCP connection failed | Not distinguished further |
| 9197 | open | TCP connection | Service not identified independently |
| 7678 | open | TCP connection | Service not identified independently |
| 5555 | closed or unreachable | TCP connection failed | No ADB connection established |
| 26101 | open | SDB connected successfully | Developer Mode is not unrestricted shell or root access |

`sdb capability` reported `intershell_support:disabled`, `sdbd_rootperm:disabled`,
`rootonoff_support:disabled` and `filesync_support:pushpull`. An ordinary shell command did not
produce the expected output, and pulling `/etc/os-release` was refused with
`You cannot pull files from this path`. File-transfer support therefore did **not** establish
access to the TV's filesystem or a full backup.

The developer tooling could nevertheless install and debug a signed application. The absence of an
ordinary shell does not mean that the supported application-development path is unavailable.

### Mac setup and installation pitfalls

- Use Samsung's SDB, not Android's ADB. The tested SDB was version 4.2.36 from the official
  [Tizen Web CLI installer](https://download.tizen.org/sdk/Installer/tizen-sdk_10.0/).
  Its executable was Intel-only and ran through the Mac's existing Rosetta installation.
- The CLI installer downloaded about 326 MB. The installed CLI occupied about 478 MB, plus
  about 216 MB for its bootstrapped Java runtime, before additional SDK extensions.
  SDB itself was about 1 MB; that is not the size of the SDK installation.
- The first installer run failed while launching its bundled archive extractor, leaving no usable
  Java runtime. Extracting the installer's bundled JDK with macOS's `unzip` and rerunning the official
  installer using that runtime succeeded. No third-party SDB binary was used.
- Install the Samsung Certificate Extension and the TV deployment tools through the official
  Package Manager. The emulator was not needed. The TV-tools installation also brought in its
  resources dependency, including simulator resources.
- Do not run the graphical and command-line Package Managers concurrently. During an overlapping
  session a download failed because a partial file was missing; retrying after closing the graphical
  manager succeeded. The overlap was observed, not proven to be the cause.
- Follow Samsung's [certificate setup](https://developer.samsung.com/smarttv/develop/getting-started/setting-up-sdk/creating-certificates.html):
  sign in yourself, select TV and Public privileges, and include the target TV's DUID in the
  distributor certificate. Keep certificate files, passwords and signing profiles private.
- In Device Manager, use **Permit to install applications** for the connected TV.
- **Use a package filename without spaces.** Both CLI and Device Manager attempts with a
  space-containing filename failed without an actionable error. The owner successfully installed
  after using a space-free filename. Earlier failures did not establish a TV-side signature rejection.
- Running the CLI from the package's directory, with no trailing `-- <directory>`, worked for the
  owner. Use a separate application identity so the test cannot overwrite an existing application.

For example, from the directory containing your own signed package:

```sh
sdb connect <tv ip>:26101
tizen install -n DashboardTest.wgt -s <tv ip>:26101
```

This is a developer-app workflow, not permission to remove system components or bypass verification.
Keep the source and signing materials backed up locally before updating the test.

### What the custom application actually established

| Capability | Observed result |
|---|---|
| Full-screen Web application and physical D-pad navigation | Worked |
| `tizen.application.getAppsInfo()` | Returned 486 API-visible entries, including system and helper entries |
| Launching the three owner-selected streaming applications | Owner confirmed the manual tests worked |
| Source discovery with `tizen.systeminfo.getPropertyValue("VIDEOSOURCE")` | Returned connected and disconnected sources |
| Connected HDMI 1 via `tizen.tvwindow` | Owner confirmed the manual picture test worked |
| Disconnected HDMI 3 | Diagnostic deliberately disabled its test button; switching this port was not tested |
| Physical Home button while the diagnostic was open | Opened Samsung Home |

Samsung documents source selection in the [TV Window API](https://developer.samsung.com/smarttv/develop/api-references/tizen-web-device-api-references/tvwindow-api.html)
as a **public** capability requiring `http://tizen.org/privilege/tv.window`.
Do not assume that HDMI control requires root. This test displayed HDMI inside the application's TV
window; it did not establish every aspect of a system-wide source handoff.

The [Application API](https://developer.samsung.com/smarttv/develop/api-references/tizen-web-device-api-references/application-api.html)
allowed inventory and selected-app launch tests. An entry being visible is not evidence that it is
user-installed, removable, safe to remove, or running in the background.

### Startup: standby resume is not a Home replacement

On this Tizen 9 interface, the menu was under **All Settings > Advanced features > Start Screen
Options**, rather than the older General & Privacy location. Before testing, the owner photographed
the original values: **Start with Smart Hub Home on**, **Autorun Last App off**. Those are this unit's
undo values, not recommended defaults for every TV.

The owner then tested with Smart Hub startup off and last-app autorun on:

| Test | Observed result |
|---|---|
| Normal short-press standby off/on after an HDMI test | Returned to HDMI 1, despite the diagnostic interface having been visible |
| Normal standby off/on after freshly opening the diagnostic without an HDMI test | Reopened the diagnostic |
| Long-press Power restart | Returned to HDMI 1 |
| Physical Home button | Opened Samsung Home |

This establishes **standby resume in one tested sequence**, not guaranteed startup or Home takeover.
Samsung [warns that not every application supports last-app autorun](https://www.samsung.com/ae/support/tv-audio-video/use-autorun-start-screen-options-on-your-samsung-tv/).
Power removal/reconnection, repeated restart trials and startup after using other applications were
not tested. No supported way to replace the physical Home button's destination was established.

### Inventory export, backup limits and removal

The installed diagnostic was opened in debug mode through SDB. Its Web Inspector exposed a local
debugging endpoint; evaluating `tizen.application.getAppsInfo()` within that application allowed the
inventory to be saved on the Mac as JSON and CSV. Both exports contained 486 unique application IDs.
The API did **not** expose a removable flag in this session; the export recorded it as unknown.
This avoided installing an inventory collector or publishing the owner's application list.

The metadata, capability results, signing materials and original startup-settings photo were
preserved privately on the Mac. **None is a full, restorable TV backup.** App data, accounts,
system settings and firmware were not backed up, and system-package reinstallation was not verified.

No uninstall experiment was performed. In particular:

- An ad-related name does not establish that a component is safe to remove or that other TV features
  do not depend on it.
- Removing a TV advertising component would not establish removal of advertisements delivered inside
  streaming applications.
- A large inventory count does not measure background resource use. No speed, memory, storage or
  ad-reduction improvement was measured.

Do not treat this report as an uninstall list. A custom dashboard can limit its own visible choices
without removing applications or risking HDMI, remote-control or streaming functionality.
The custom Tizen application belongs in a separate repository; this note does not add Tizen support
to `tools/` or make the Android launcher installable on Samsung firmware.
