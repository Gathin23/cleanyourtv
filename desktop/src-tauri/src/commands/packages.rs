//! Package listing and the guarded enable/disable paths.

use super::{current_user, on_tv, session, setupwraith_owns_wifi, timestamp, Res};
use super::guards::{self, Guards};
use crate::adb::Adb;
use crate::rules::{self, Kind, Verdict};
use crate::state::AppState;
use serde::{Deserialize, Serialize};
use std::time::Duration;
use tauri::{Emitter, State};

#[derive(Serialize, Clone)]
pub struct Package {
    pub name: String,
    pub apk: String,
    pub system: bool,
    pub disabled: bool,
    pub kind: Kind,
    pub verdict: Verdict,
    pub reason: String,
}

#[derive(Serialize)]
pub struct PackageList {
    pub packages: Vec<Package>,
    pub disabled: usize,
    pub total: usize,
    pub launcher_installed: bool,
}

fn names(adb: &Adb, target: &str, args: &str) -> Vec<String> {
    adb.shell_text(target, &format!("pm list packages {args}"), Duration::from_secs(30))
        .map(|text| {
            text.lines()
                .filter_map(|l| l.trim().strip_prefix("package:"))
                .map(|s| s.to_string())
                .collect()
        })
        .unwrap_or_default()
}

#[tauri::command]
pub async fn packages_list(state: State<'_, AppState>) -> Res<PackageList> {
    let (adb, target) = session(&state)?;
    on_tv(adb, target, |adb, target| {
        let text = adb.shell_text(target, "pm list packages -f", Duration::from_secs(45))?;
        let system = names(adb, target, "-s");
        let disabled = names(adb, target, "-d");
        let guards = guards::collect(adb, target);
        let mut packages = Vec::new();
        for line in text.lines() {
            let Some(body) = line.trim().strip_prefix("package:") else { continue };
            let (apk, name) = match body.split_once('=') {
                Some((apk, name)) => (apk.to_string(), name.to_string()),
                None => (String::new(), body.to_string()),
            };
            if !crate::adb::is_package_name(&name) {
                continue;
            }
            let mut assessment = rules::assess(&name);
            // The app that opens Settings or the HDMI inputs is not "recommended" on a rule about
            // telemetry: it has to be confirmed one by one, the way tools/disable.sh asks.
            if assessment.verdict != Verdict::Refused {
                if let Some(why) = guards.is_handler(&name) {
                    assessment.verdict = Verdict::AskFirst;
                    assessment.reason = why.to_string();
                }
            }
            packages.push(Package {
                disabled: disabled.iter().any(|d| d == &name),
                system: system.iter().any(|s| s == &name),
                name,
                apk,
                kind: assessment.kind,
                verdict: assessment.verdict,
                reason: assessment.reason,
            });
        }
        packages.sort_by(|a, b| a.name.cmp(&b.name));
        Ok(PackageList {
            disabled: packages.iter().filter(|p| p.disabled).count(),
            total: packages.len(),
            launcher_installed: packages.iter().any(|p| p.name == rules::OUR_LAUNCHER),
            packages,
        })
    })
    .await
}

#[tauri::command]
pub async fn package_details(state: State<'_, AppState>, name: String) -> Res<String> {
    if !crate::adb::is_package_name(&name) {
        return Err("Not a package name.".into());
    }
    let (adb, target) = session(&state)?;
    on_tv(adb, target, move |adb, target| {
        adb.shell_text(
            target,
            &format!("dumpsys package {name}"),
            Duration::from_secs(30),
        )
    })
    .await
}

#[derive(Serialize)]
pub struct ToggleResult {
    pub name: String,
    pub disabled: bool,
    pub verified: bool,
    pub output: String,
    pub warning: Option<String>,
}

/// Why a disable would be refused right now, before anything is sent.
///
/// `guards` is the snapshot taken before the run: Home's owner, the launchers that claim it, and the
/// apps that open the inputs and Settings. `disabling` is the whole batch, so a launcher going in the
/// same run does not count as a way back.
fn dynamic_refusal(
    adb: &Adb,
    target: &str,
    guards: &Guards,
    name: &str,
    disabling: &[String],
    allow_danger: bool,
    allow_setupwraith: bool,
) -> Option<String> {
    if name == rules::SETUPWRAITH {
        if !allow_setupwraith {
            return Some(
                "The setup wizard needs an explicit confirmation. The app checks whether it owns \
                 the saved Wi-Fi network first; if it does, disabling it takes the TV off Wi-Fi."
                    .into(),
            );
        }
        return match setupwraith_owns_wifi(adb, target) {
            Some(true) => Some(
                "The saved Wi-Fi network belongs to the setup wizard. Disabling it now would \
                 delete that network and take the TV off Wi-Fi and ADB. Forget the network on the \
                 TV and join it again in Settings first, or connect over Ethernet."
                    .into(),
            ),
            None => Some(
                "The app could not read which app owns the saved Wi-Fi networks, so it will not \
                 disable the setup wizard. Try again, or use Ethernet."
                    .into(),
            ),
            // Disabling the setup wizard is the recommended fix when it answers Home ahead of a
            // launcher (#1573), so the Home guard does not apply to it.
            Some(false) => None,
        };
    }

    if name.starts_with("com.cvte.tv.launcher") {
        // CVTE boards have a recovery path that hangs where the stock launcher is gone (#741).
        return Some(
            "This is a CVTE board's stock launcher. On those sets, disabling it has hang the TV on \
             its boot animation with ADB gone. Set the clean launcher as Home, reboot, and confirm \
             Home and ADB come back before doing this."
                .into(),
        );
    }

    guards.refusal(name, disabling, allow_danger)
}

fn verify_disabled(adb: &Adb, target: &str, name: &str) -> bool {
    names(adb, target, "-d").iter().any(|n| n == name)
}

fn command_ok(text: &str) -> bool {
    let t = text.to_ascii_lowercase();
    !(t.contains("error") || t.contains("exception") || t.contains("failure") || t.contains("not found"))
}

#[tauri::command]
pub async fn package_set_enabled(
    state: State<'_, AppState>,
    name: String,
    enabled: bool,
    allow_danger: bool,
    allow_setupwraith: bool,
) -> Res<ToggleResult> {
    if !crate::adb::is_package_name(&name) {
        return Err("Not a package name.".into());
    }
    let (adb, target) = session(&state)?;
    on_tv(adb, target, move |adb, target| {
        if enabled {
            let out = adb.shell_text(
                target,
                &format!("pm enable --user {} {name}", current_user(adb, target)),
                Duration::from_secs(25),
            )?;
            let disabled = verify_disabled(adb, target, &name);
            return Ok(ToggleResult {
                name,
                disabled,
                verified: !disabled,
                output: out,
                warning: None,
            });
        }

        let assessment = rules::assess(&name);
        if assessment.verdict == Verdict::Refused {
            return Err(assessment.reason);
        }
        let guards = guards::collect(adb, target);
        let batch = [name.clone()];
        if let Some(reason) =
            dynamic_refusal(adb, target, &guards, &name, &batch, allow_danger, allow_setupwraith)
        {
            return Err(reason);
        }

        let out = adb.shell_text(
            target,
            &format!("pm disable-user --user 0 {name}"),
            Duration::from_secs(25),
        )?;
        let lowered = out.to_ascii_lowercase();
        if lowered.contains("securityexception") || lowered.contains("not allowed") {
            return Err(format!(
                "This firmware refused the disable: {}. Nothing was changed. Some sets (Fire OS, \
                 for one) do not allow debloating this way.",
                out.trim()
            ));
        }
        if !command_ok(&out) {
            return Err(format!("The TV refused it: {}", out.trim()));
        }
        let disabled = verify_disabled(adb, target, &name);
        Ok(ToggleResult {
            name,
            disabled,
            verified: disabled,
            output: out,
            warning: if !disabled {
                Some("The command was accepted but the package is not on the disabled list.".into())
            } else {
                None
            },
        })
    })
    .await
}

#[tauri::command]
pub async fn package_launch(state: State<'_, AppState>, name: String) -> Res<String> {
    if !crate::adb::is_package_name(&name) {
        return Err("Not a package name.".into());
    }
    if rules::is_factory(&name) {
        return Err(
            "This is a factory or burn-in test app. The app never starts one: on some boards it \
             flips a factory-mode flag the shell cannot undo."
                .into(),
        );
    }
    let (adb, target) = session(&state)?;
    on_tv(adb, target, move |adb, target| {
        let user = current_user(adb, target);
        let leaf = adb
            .shell_text(
                target,
                &format!(
                    "cmd package resolve-activity --brief --user {user} -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER {name}"
                ),
                Duration::from_secs(15),
            )
            .unwrap_or_default();
        let component = leaf.lines().last().unwrap_or("").trim().to_string();
        if component.contains('/') {
            adb.shell_text(target, &format!("am start --user {user} -n {component}"), Duration::from_secs(20))
        } else {
            adb.shell_text(target, &format!("monkey -p {name} -c android.intent.category.LEANBACK_LAUNCHER 1"), Duration::from_secs(20))
        }
    })
    .await
}

#[tauri::command]
pub async fn package_force_stop(state: State<'_, AppState>, name: String) -> Res<String> {
    if !crate::adb::is_package_name(&name) {
        return Err("Not a package name.".into());
    }
    let (adb, target) = session(&state)?;
    on_tv(adb, target, move |adb, target| {
        adb.shell_text(target, &format!("am force-stop {name}"), Duration::from_secs(15))
    })
    .await
}

#[derive(Deserialize, Default)]
pub struct BatchOptions {
    /// Packages the person explicitly confirmed although the rule says ask first.
    pub allow: Vec<String>,
    pub allow_setupwraith: bool,
    pub screenshot: bool,
}

#[derive(Serialize, Clone)]
pub struct BatchItem {
    pub name: String,
    pub ok: bool,
    pub output: String,
    pub reason: Option<String>,
}

#[derive(Serialize)]
pub struct BatchReport {
    pub results: Vec<BatchItem>,
    pub skipped: Vec<BatchItem>,
    pub aborted: Option<String>,
    pub disabled_now: usize,
    pub still_enabled: Vec<String>,
}

/// Emitted for every package, so the UI can show a live list instead of a spinner.
#[derive(Serialize, Clone)]
pub struct Progress {
    pub index: usize,
    pub total: usize,
    pub name: String,
    pub phase: String,
    pub ok: bool,
    pub detail: String,
}

#[tauri::command]
pub async fn batch_disable(
    app: tauri::AppHandle,
    state: State<'_, AppState>,
    packages: Vec<String>,
    options: BatchOptions,
) -> Res<BatchReport> {
    let (adb, target) = session(&state)?;
    {
        let inner = state.0.lock().unwrap();
        if !inner.backed_up {
            return Err(
                "Take a backup first. It is one click on the Debloat screen, and it is what the \
                 restore button uses."
                    .into(),
            );
        }
    }
    on_tv(adb, target, move |adb, target| {
        let total = packages.len();
        let mut results = Vec::new();
        let mut skipped = Vec::new();
        let mut aborted = None;
        let mut disabled_now = 0usize;

        // One snapshot for the whole plan: who owns Home, who else claims it, and which apps the
        // inputs and Settings need. Asking per package would be slow and could answer differently
        // halfway through the batch.
        let guards = guards::collect(adb, target);

        for (index, name) in packages.iter().enumerate() {
            let emit = |phase: &str, ok: bool, detail: &str| {
                let _ = app.emit(
                    "debloat://progress",
                    Progress {
                        index: index + 1,
                        total,
                        name: name.clone(),
                        phase: phase.into(),
                        ok,
                        detail: detail.to_string(),
                    },
                );
            };
            let assessment = rules::assess(name);
            if assessment.verdict == Verdict::Refused {
                skipped.push(BatchItem {
                    name: name.clone(),
                    ok: false,
                    output: String::new(),
                    reason: Some(assessment.reason),
                });
                emit("skipped", false, "blocked by the safety rules");
                continue;
            }
            let needs_allow = matches!(assessment.verdict, Verdict::AskFirst | Verdict::Danger);
            let allowed = options.allow.iter().any(|a| a == name);
            if needs_allow && !allowed {
                skipped.push(BatchItem {
                    name: name.clone(),
                    ok: false,
                    output: String::new(),
                    reason: Some(format!("Needs confirmation: {}", assessment.reason)),
                });
                emit("skipped", false, "needs confirmation");
                continue;
            }
            if name == &rules::SETUPWRAITH && !options.allow_setupwraith {
                skipped.push(BatchItem {
                    name: name.clone(),
                    ok: false,
                    output: String::new(),
                    reason: Some("The setup wizard needs its own confirmation.".into()),
                });
                emit("skipped", false, "needs confirmation");
                continue;
            }
            if let Some(reason) = dynamic_refusal(
                adb,
                target,
                &guards,
                name,
                &packages,
                allowed,
                options.allow_setupwraith,
            ) {
                skipped.push(BatchItem {
                    name: name.clone(),
                    ok: false,
                    output: String::new(),
                    reason: Some(reason),
                });
                emit("skipped", false, "refused right now");
                continue;
            }

            emit("running", true, "sending pm disable-user");
            match adb.shell_text(
                target,
                &format!("pm disable-user --user 0 {name}"),
                Duration::from_secs(25),
            ) {
                Ok(out) => {
                    let lowered = out.to_ascii_lowercase();
                    if lowered.contains("securityexception") || lowered.contains("not allowed") {
                        results.push(BatchItem {
                            name: name.clone(),
                            ok: false,
                            output: out.clone(),
                            reason: Some("this firmware refuses pm disable-user".into()),
                        });
                        emit("failed", false, &out);
                        aborted = Some(
                            "This firmware refuses pm disable-user (SecurityException). There is \
                             nothing to debloat on this set this way; nothing else was tried."
                                .to_string(),
                        );
                        break;
                    }
                    let ok = command_ok(&out);
                    if ok {
                        disabled_now += 1;
                    }
                    emit(if ok { "done" } else { "failed" }, ok, out.trim());
                    results.push(BatchItem {
                        name: name.clone(),
                        ok,
                        output: out,
                        reason: None,
                    });
                }
                Err(e) => {
                    emit("failed", false, &e);
                    results.push(BatchItem {
                        name: name.clone(),
                        ok: false,
                        output: String::new(),
                        reason: Some(e),
                    });
                }
            }
            std::thread::sleep(Duration::from_millis(120));
        }

        // Android writes package state to disk about ten seconds after a change; sync now so a
        // reboot right after the batch cannot boot with the old state.
        let _ = adb.shell_text(target, "sync", Duration::from_secs(30));
        let disabled_now_list = names(adb, target, "-d");
        let still_enabled: Vec<String> = packages
            .iter()
            .filter(|p| !disabled_now_list.iter().any(|d| d == *p))
            .cloned()
            .collect();

        Ok(BatchReport {
            results,
            skipped,
            aborted,
            disabled_now,
            still_enabled,
        })
    })
    .await
}

#[derive(Serialize)]
pub struct ExtractedApk {
    pub local: String,
    pub size: u64,
}

/// `pm path` + pull: keep a copy of an app before disabling it.
#[tauri::command]
pub async fn package_pull_apk(
    state: State<'_, AppState>,
    name: String,
    directory: String,
) -> Res<ExtractedApk> {
    if !crate::adb::is_package_name(&name) {
        return Err("Not a package name.".into());
    }
    let (adb, target) = session(&state)?;
    on_tv(adb, target, move |adb, target| {
        let path = adb
            .shell_text(target, &format!("pm path {name}"), Duration::from_secs(15))?
            .lines()
            .find_map(|l| l.trim().strip_prefix("package:").map(str::to_string))
            .ok_or("The TV does not know that package.")?;
        let dir = std::path::PathBuf::from(directory);
        std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
        let local = dir.join(format!("{name}-{}.apk", timestamp()));
        let _out = adb.pull(target, &path, &local.display().to_string(), Duration::from_secs(300))?;
        let size = std::fs::metadata(&local).map(|m| m.len()).unwrap_or(0);
        Ok(ExtractedApk { local: local.display().to_string(), size })
    })
    .await
}
