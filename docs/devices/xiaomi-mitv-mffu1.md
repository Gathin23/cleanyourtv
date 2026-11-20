# Xiaomi MiTV-MFFU1 on HyperOS 3 (`OS3.0.120.0.UFFMAMN`, Android 14): Home redirect loss and allowlisted ID

From @Fantasy-liudd's report and follow-up in [#3313](https://github.com/Gathin23/cleanyourtv/issues/3313). On this
`MiTV-MFFU1` (`beekeeper`) running HyperOS 3, keeping Xiaomi Home enabled and redirecting with `HomeKeyService` did not
hold across a reboot, whereas building the launcher under the allowlisted `com.ts.launcher` identity and disabling
`com.mitv.tvhome` passed physical Home, standby/wake and a full reboot.

| Field | Observed value |
|---|---|
| System model / codename | `MiTV-MFFU1` / `beekeeper` |
| Android / API | Android 14 / 34 |
| Build | `OS3.0.120.0.UFFMAMN` (HyperOS 3, Wi-Fi) |
| Stock Home | `com.mitv.tvhome/.MainActivityUserMode` |
| Tested launcher | Launcher 3.3 (`versionCode` 12, commit `04aec4b7c7`), locally built as `com.ts.launcher` |

Local changes in the tested build were limited to `config.xml`, `AndroidManifest.xml` and `AppAdapter.java` (dock
entries plus package-visibility and exported-activity fallback for Mi Home and the media player); `HomeKeyService.java`
was unmodified. There was no separate clean-upstream A/B run.

## What failed with stock Home kept enabled

### 1. `stock_home_redirect` + `HomeKeyService`

The first attempt kept `com.mitv.tvhome` enabled, set `"stock_home_redirect": "com.mitv.tvhome"` with
`"key_filter": false`, and enabled `com.ts.launcher/com.gathin.tvhome.HomeKeyService` alongside Xiaomi's existing
`com.xiaomi.voicecontrol/.VoiceAccessibilityService` (`restore_key_services` and `WRITE_SECURE_SETTINGS` were not
used):

- **Physical Home before reboot:** an injected `KEYCODE_HOME` reached `com.ts.launcher/com.gathin.tvhome.MainActivity`,
  but pressing the physical Home button showed the new launcher briefly and then resumed
  `com.xiaomi.mitv.tvplayer/.ExternalSourceActivity` about 3.6 seconds later (`wm_set_resumed_activity`). The caller
  that launched `ExternalSourceActivity` was not established.
- **Helper entry gone after reboot:** after a user-initiated reboot the TV opened Xiaomi's stock Home. A snapshot taken
  before rollback showed only `com.xiaomi.voicecontrol/.VoiceAccessibilityService` in
  `enabled_accessibility_services`; `HomeKeyService` was gone while `com.ts.launcher` remained enabled and the four
  packages disabled earlier in that session (`com.miui.tv.analytics`, `com.miui.misightservice`, `com.xiaomi.tvqs`,
  `com.mitv.shoplugin`) were still disabled. HOME resolved to `android/com.android.internal.app.ResolverActivity`.

### 2. Setting preferred Home alone

With `com.mitv.tvhome` still enabled and all four trial packages re-enabled, `cmd package set-home-activity` returned
`Success`, yet HOME resolved to `android/com.android.internal.app.ResolverActivity` and an injected Home press opened
Xiaomi Home with no chooser.

Read-only inspection of this firmware's `services.jar` showed why: `ResolveIntentHelper` admits third-party HOME
candidates on Xiaomi's allowlist (see [`xiaomi-mitv-aetp0-dofus.md`](xiaomi-mitv-aetp0-dofus.md) and
[`xiaomi-mitv-mffu0`](../../examples/xiaomi-mitv-mffu0/debloat.txt)), but `PreferredActivityHelper.addPreferredActivity`
checks `isSystem()` on a HOME target, logs `addPreferredActivity: Don't set 3-app default home` for a non-system
package, and returns without saving the preference (`updateDefaultHomeNotLocked` applies a matching check). Passing the
candidate filter therefore allows the package to resolve HOME once it is the sole launcher, without allowing a saved
default while `com.mitv.tvhome` stays enabled.

## What worked: allowlisted `com.ts.launcher` + `com.mitv.tvhome` disabled

With all four cleanup packages re-enabled so no debloat changes remained active, the follow-up test used a helper-free
configuration:

```json
{
  "stock_home_redirect": "",
  "key_filter": false,
  "restore_key_services": false,
  "power_on_home": false,
  "power_on_home_always": false,
  "boot_to_live_tv": false
}
```

1. Confirm `com.ts.launcher` is not already installed (`pm path com.ts.launcher` must print nothing), build with
   `-PlauncherApplicationId=com.ts.launcher`, install, and verify `am start -W -n com.ts.launcher/com.gathin.tvhome.MainActivity`
   comes up in the foreground **before** touching the stock launcher (see
   [`xiaomi-mitv-aetp0-dofus.md`](xiaomi-mitv-aetp0-dofus.md) for the build and install commands).
2. Disable the stock launcher (`pm disable-user --user 0 com.mitv.tvhome`) with no launcher accessibility service and
   no `WRITE_SECURE_SETTINGS` grant. Native Xiaomi Settings (`com.xiaomi.mitv.settings`) stayed enabled and reachable.

`resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME` then answered:

```text
priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
com.ts.launcher/com.gathin.tvhome.MainActivity
```

### Verified checks on the TV

- **Physical Home from an app:** stayed on `com.ts.launcher` without returning to `ExternalSourceActivity`.
- **Standby / wake (physical power key):** returned to `com.ts.launcher`; boot ID, boot count and uptime confirmed
  suspend/resume rather than a cold boot. Existing `mitv.short.power.action=2` and
  `com.xiaomi.voicecontrol/.VoiceAccessibilityService` stayed intact. Do not carry this result over to
  [`xiaomi-mitv-mffu0`](../../examples/xiaomi-mitv-mffu0/debloat.txt), which documents a different power-key path on
  `finch`.
- **Full system reboot (`tools/tv.py --reboot`):** started straight into `com.ts.launcher`; read-back confirmed a new
  boot ID, boot count incremented by one, HOME still resolving to `com.ts.launcher/com.gathin.tvhome.MainActivity`, and
  `com.mitv.tvhome` still disabled. After the cold boot Xiaomi's ADB switch still showed on while connections were
  refused until ADB debugging was toggled off and on under Account & Security.

### Recovery

Re-enabling `com.mitv.tvhome` restores stock Home:

```bash
PY=tools/venv/bin/python
"$PY" -I tools/tv.py 'pm enable --user 0 com.mitv.tvhome'
"$PY" -I tools/tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
```

On the tested unit, stock Home, package states and launcher configuration were restored and read back, the TV returned
to the normal Xiaomi interface after a reboot, and `tools/adb-closed.py` confirmed the debug port refused connections.

## Scope and limits

- Single-unit test rolled back at the owner's request, not a multi-day installation or a tested debloat list.
- This route bypasses the `HomeKeyService` setup; it does not identify what removed `HomeKeyService` from
  `enabled_accessibility_services` at reboot or what opened `ExternalSourceActivity` when `com.mitv.tvhome` was active.
- Input/source keys, held-volume repeat, live XiaoAI voice commands, phone casting and multi-day standby were not
  exhaustively retested under this configuration.
