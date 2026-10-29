# Contributing

Thanks for helping more TVs lose their ads. Three kinds of contributions are especially welcome.

Working with a coding agent? Point it at [`AGENTS.md`](AGENTS.md): the same rules, written for agents.

## Where does my idea go?

The launcher stays small on purpose, so pick the smallest home that fits:

| Your idea | Where it goes |
|---|---|
| A TV model, a fix, a translation, a small switch that is off by default | A pull request here (sections 1 to 3 below); a new switch with a screenshot in [`docs/switches.md`](docs/switches.md) |
| A new look: colours, wallpaper, dock, columns | [`themes/`](themes/README.md), with a screenshot from a real TV |
| A widget or tile that can be its own small app | [`addons/`](addons/README.md), as a module card with a screenshot from a real TV; it never touches `launcher/` |
| A whole new launcher, a heavy restyle, a port to another platform | Your own repo, plus one row in [`SHOWCASE.md`](SHOWCASE.md) (section 4) |

Everything merged into `themes/` and `addons/` shows up on [cleanyour.tv/addons](https://cleanyour.tv/addons).

## 1. Add your TV model

Every brand ships different bloat. If you ran the prompt (or did it by hand) on a TV that isn't in
`examples/`, add it. The same steps for a coding agent are in
[`docs/workflows/tv-list.md`](docs/workflows/tv-list.md).

1. Create `examples/<brand>-<model>/debloat.txt`. Copy the format from
   `examples/xiaomi-mi-tv-4a/debloat.txt`: one package per line, grouped with a comment that says
   what the group is, and a trailing comment on anything non-obvious ("re-enable for firmware
   updates", "needed for HDMI-CEC", etc.).
2. Put the exact model name, Android version and firmware build in the header so people can tell
   whether it matches theirs. `tools/tv.py --probe` prints all three.
3. Only list packages you have actually disabled and lived with for a few days. Note anything that
   broke and what you re-enabled.
4. Add a row to `examples/README.md` and one to the TV table in `README.md`: the tests compare them,
   so a list with a row in only one of the two fails.
5. If your TV's Live TV app or stock launcher has a different package name, say so in the header. Where
   a script needs the stock launcher, `tools/restore.sh` takes it as its second argument
   (`tools/restore.sh <list> <package>`) and `launcher/build-and-install.sh` takes it in the
   environment, as `STOCK_LAUNCHER=<package>` (`-StockLauncher` on the PowerShell twin, and `none`
   keeps it enabled) - not as an argument. The launcher shows your Live TV app in its Inputs menu from
   `live_tv_package`, in `config.xml` or a theme.

Never list packages to *uninstall*. Everything in this repo disables with `pm disable-user`, which
is reversible from the TV's own settings if the tools are gone.

Android's own packages are not bloat either. Never list the permission controller, the package
installer, ext services or ext shared, module metadata, a `com.google.android.overlay.modules*`
overlay, systemui, shell, phone, settings, tv.settings, `android`, or a `com.android.providers.*`
package other than calendar, contacts, user dictionary and blocked numbers. A TV that loses one of
them may never boot again ([#884](https://github.com/Gathin23/cleanyourtv/issues/884), and
[what to do if it already happened](docs/TROUBLESHOOTING.md#working-on-the-tv)), so
`tools/disable.sh`, `disable.ps1` and `check.py --fix` refuse such a list outright, with no switch to
override it, and a pull request that names them comes back for a rework.

## 2. Improve the launcher

The launcher lives in `launcher/` and is deliberately small: plain Java, AndroidX RecyclerView and
DynamicAnimation, no Kotlin, no Compose, no network. It targets TVs with 1 GB of RAM. A new switch follows
[`docs/workflows/launcher-switch.md`](docs/workflows/launcher-switch.md), a new language
[`docs/workflows/translation.md`](docs/workflows/translation.md), and a speed-up or slow-down is measured with
[`docs/workflows/perf-ab.md`](docs/workflows/perf-ab.md). In every change:

- No blur, no video backgrounds, no polling, no background services.
- Anything that depends on a specific TV goes in `app/src/main/res/values/config.xml`, not in code.
- Keep D-pad navigation deterministic. Test Up/Down/Left/Right from every edge.
- Verify on a real TV, not only the emulator. The easiest way is remote key injection plus a
  screenshot: `tools/tv.py --screenshot shots/after.png DPAD_DOWN` presses the key, waits a second
  (`--wait` changes that), captures on the TV and pulls the PNG, creating the folder if it is missing.
  Include a screenshot in your pull request.
- Long presses (hold OK on Settings or on a tile) can't always be injected: `input keyevent --longpress`
  arrives as a short press on some firmware (Android TV 8.0 on a Philips opens the app instead). Writing
  to the remote's own input device works, because Android then repeats the held key itself. Find the
  device and the OK key with `tools/tv.py 'getevent -pl'` (a remote that lists `KEY_OK` is scancode 352),
  then hold it for a second:
  `tools/tv.py 'e=/dev/input/event1; sendevent $e 1 352 1; sendevent $e 0 0 0; sleep 1.2; sendevent $e 1 352 0; sendevent $e 0 0 0'`.
  This needs the shell user in the `input` group (`tools/tv.py id`).
  SELinux can refuse it even then: on a TCL 55C728 (Google TV 11, enforcing) the shell is in `input`,
  yet `sendevent` fails with `Permission denied` and `logcat` shows
  `avc: denied { write } ... scontext=u:r:shell:s0 tcontext=u:object_r:input_device:s0`, while
  `--longpress` arrives as a short press too. On a set like that, test long presses with the remote.

### Automated navigation and lifecycle checks

- Use D-pad key events throughout focus tests, including entering and leaving Reorder.
  Injected touch input can enter Android's system-wide [touch mode](https://developer.android.com/develop/ui/views/touch-and-input/input-events#TouchMode),
  where ordinary buttons may not take focus. If a test uses touch, send a directional key
  and verify the focused tile before continuing with D-pad assertions.
- After Home, an app transition or a reboot, wait with a timeout for the launcher UI and
  focused window before sending navigation or capturing a hierarchy. A fixed sleep or
  a boot-completion property alone does not establish that Home is ready. Android's
  [UI Automator guidance](https://developer.android.com/training/testing/other-components/ui-automator-legacy#wait)
  demonstrates waiting for the launcher and for specific UI conditions.
- For synthetic memory checks, record the process state, requested trim level and command
  result. ActivityManager [can reject background trim levels for foreground processes](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/am/ActivityManagerService.java).
  To exercise hidden Home, put an opaque app in front and verify Home's process state;
  use a trim level supported by the target Android version. Treat a rejected command as
  a test setup failure. A delivered trim callback tests recovery behavior, not performance
  under real memory pressure.

Build with `TV_HOST=<ip> launcher/build-and-install.sh` (JDK 17, Android SDK platform 34 and
build-tools 34.0.0; on macOS `brew install openjdk@17 android-commandlinetools` then
`sdkmanager "platforms;android-34" "build-tools;34.0.0"`; with the newer command-line tools the form is
`android sdk install platforms/android-34 build-tools/34.0.0`).

Ideas that fit the direction: an idle screensaver with clock and ambient colour, per-TV icon packs
for apps without banners, a settings screen to edit the dock from the TV, localisation.
Ideas that don't: recommendation rows, content feeds, ads, telemetry.

A new theme goes in `themes/<name>/` with its `config.json` and a required `screenshot.jpg` of Home on a
real TV with that theme; see [Adding a theme](themes/README.md#adding-a-theme).

## 3. Improve the prompt and tools

`PROMPT.md` is what most people will actually use. If your assistant got stuck somewhere, the fix
usually belongs in the prompt as one extra sentence, not in a script. Keep it brand-neutral; put
brand-specific knowledge in `examples/`.

`tools/tv.py` and `tools/b64push.py` exist because the stock `adb` is blocked on recent macOS. If
you find a cleaner workaround, open an issue first.

## 4. Show what you built

Built your own launcher, a port to another platform, or an add-on that is too big or too personal
for the shared launcher? Put it in its own repository and add one row to
[`SHOWCASE.md`](SHOWCASE.md). That page is the place for alternative launchers, forks with a
different look, and tools around the launcher; this repo keeps one small reference launcher and
points at everything else. The rules for a row are on that page.

Things that stay in this repo: debloat lists, fixes and small, opt-in features for the reference
launcher, prompt and tool improvements. A feature that can be its own small app goes in [`addons/`](addons/README.md)
as a module card. Things that go in the showcase: a second launcher, a
restyle, a platform port, anything with its own background services or network access.

## Pull requests

- One topic per PR. Small is fine.
- Say which TV you tested on.
- No new dependencies in the launcher without discussing it in an issue.
- Be kind in reviews. Most contributors here are doing this for their parents.

By contributing you agree your work is released under the repo's MIT licence.
