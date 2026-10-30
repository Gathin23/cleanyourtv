//! Backups: the package list, the disabled list, getprop, settings and a restore script, written to
//! disk before the first change. The debloat command refuses to run until one exists.

use super::{human_time, on_tv, output_root, session, timestamp, Res};
use crate::state::AppState;
use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use std::time::Duration;
use tauri::State;

#[derive(Serialize, Deserialize, Clone)]
pub struct Manifest {
    pub created: String,
    pub host: String,
    pub model: String,
    pub android: String,
    pub build: String,
    pub packages: usize,
    pub disabled: Vec<String>,
    pub files: Vec<String>,
}

#[derive(Serialize)]
pub struct BackupResult {
    pub path: String,
    pub disabled: Vec<String>,
    pub packages: usize,
    pub files: Vec<String>,
}

fn restore_script(disabled: &[String]) -> (String, String) {
    let mut sh = String::from(
        "#!/usr/bin/env sh\n# CleanYourTV Desktop restore script.\n# Re-enables everything this session disabled. Nothing is uninstalled.\nset -e\n: \"${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 sh restore.sh}\"\n",
    );
    let mut ps1 = String::from(
        "# CleanYourTV Desktop restore script.\n# Re-enables everything this session disabled. Nothing is uninstalled.\nparam([Parameter(Mandatory=$true)][string]$TvHost)\n$ErrorActionPreference = 'Stop'\n",
    );
    for pkg in disabled {
        sh.push_str(&format!("adb -s \"$TV_HOST:5555\" shell pm enable --user 0 {pkg}\n"));
        ps1.push_str(&format!("adb -s \"$($TvHost):5555\" shell pm enable --user 0 {pkg}\n"));
    }
    // Android writes package state to disk about ten seconds later; sync so a reboot cannot boot
    // with the old state.
    sh.push_str("adb -s \"$TV_HOST:5555\" shell sync\n");
    ps1.push_str("adb -s \"$($TvHost):5555\" shell sync\n");
    (sh, ps1)
}

#[tauri::command]
pub async fn backup_create(state: State<'_, AppState>) -> Res<BackupResult> {
    let (adb, target) = session(&state)?;
    let host = state.0.lock().unwrap().host.clone().unwrap_or_else(|| "tv".into());
    let root = output_root(&state);
    on_tv(adb, target, move |adb, target| {
        let safe_host: String = host
            .chars()
            .map(|c| if c.is_ascii_alphanumeric() || c == '.' || c == '-' { c } else { '_' })
            .collect();
        let dir = root
            .join("backups")
            .join(format!("{safe_host}-{}", timestamp()));
        std::fs::create_dir_all(&dir).map_err(|e| format!("{}: {e}", dir.display()))?;

        let mut files = Vec::new();
        fn write(dir: &Path, files: &mut Vec<String>, name: &str, text: &str) -> Res<()> {
            let path = dir.join(name);
            std::fs::write(&path, text).map_err(|e| format!("{}: {e}", path.display()))?;
            files.push(name.to_string());
            Ok(())
        }

        write(&dir, &mut files, "pkglist.txt", &adb.shell_text(target, "pm list packages", Duration::from_secs(40))?)?;
        let disabled_text = adb
            .shell_text(target, "pm list packages -d", Duration::from_secs(40))
            .unwrap_or_default();
        write(&dir, &mut files, "disabled.txt", &disabled_text)?;
        write(&dir, &mut files, "getprop.txt", &adb.shell_text(target, "getprop", Duration::from_secs(20))?)?;
        write(
            &dir,
            &mut files,
            "settings_global.txt",
            &adb.shell_text(target, "settings list global", Duration::from_secs(20)).unwrap_or_default(),
        )?;
        write(
            &dir,
            &mut files,
            "settings_secure.txt",
            &adb.shell_text(target, "settings list secure", Duration::from_secs(20)).unwrap_or_default(),
        )?;
        write(
            &dir,
            &mut files,
            "settings_system.txt",
            &adb.shell_text(target, "settings list system", Duration::from_secs(20)).unwrap_or_default(),
        )?;
        write(
            &dir,
            &mut files,
            "wifi.txt",
            &adb.shell_text(target, "dumpsys wifi", Duration::from_secs(25)).unwrap_or_default(),
        )?;

        let disabled: Vec<String> = disabled_text
            .lines()
            .filter_map(|l| l.trim().strip_prefix("package:"))
            .map(|s| s.to_string())
            .collect();
        let (sh, ps1) = restore_script(&disabled);
        write(&dir, &mut files, "restore.sh", &sh)?;
        write(&dir, &mut files, "restore.ps1", &ps1)?;

        let getprop = std::fs::read_to_string(dir.join("getprop.txt")).unwrap_or_default();
        let value = |key: &str| {
            getprop
                .lines()
                .find(|l| l.starts_with(&format!("[{key}]: [")))
                .and_then(|l| l.split("]: [").nth(1))
                .map(|v| v.trim_end_matches(']').to_string())
                .unwrap_or_default()
        };
        let manifest = Manifest {
            created: human_time(),
            host: host.clone(),
            model: value("ro.product.model"),
            android: value("ro.build.version.release"),
            build: value("ro.build.display.id"),
            packages: std::fs::read_to_string(dir.join("pkglist.txt"))
                .map(|t| t.lines().filter(|l| l.starts_with("package:")).count())
                .unwrap_or(0),
            disabled: disabled.clone(),
            files: files.clone(),
        };
        write(
            &dir,
            &mut files,
            "manifest.json",
            &serde_json::to_string_pretty(&manifest).map_err(|e| e.to_string())?,
        )?;

        Ok((BackupResult {
            path: dir.display().to_string(),
            disabled,
            packages: manifest.packages,
            files,
        }, dir))
    })
    .await
    .map(|(result, _dir)| {
        if let Ok(mut inner) = state.0.lock() {
            inner.backed_up = true;
        }
        result
    })
}

#[derive(Serialize)]
pub struct BackupEntry {
    pub path: String,
    pub created: String,
    pub host: String,
    pub model: String,
    pub android: String,
    pub disabled: usize,
    pub packages: usize,
}

fn read_manifest(dir: &Path) -> Option<Manifest> {
    let text = std::fs::read_to_string(dir.join("manifest.json")).ok()?;
    serde_json::from_str(&text).ok()
}

/// Whether this session has already written a backup (the batch command requires it).
#[tauri::command]
pub fn backup_state(state: State<'_, AppState>) -> bool {
    state.0.lock().map(|inner| inner.backed_up).unwrap_or(false)
}

#[tauri::command]
pub fn backup_list(state: State<'_, AppState>) -> Vec<BackupEntry> {
    let root = output_root(&state).join("backups");
    let mut out = Vec::new();
    if let Ok(entries) = std::fs::read_dir(&root) {
        for entry in entries.flatten() {
            let path = entry.path();
            if !path.is_dir() {
                continue;
            }
            if let Some(m) = read_manifest(&path) {
                out.push(BackupEntry {
                    path: path.display().to_string(),
                    created: m.created,
                    host: m.host,
                    model: m.model,
                    android: m.android,
                    disabled: m.disabled.len(),
                    packages: m.packages,
                });
            }
        }
    }
    out.sort_by(|a, b| b.created.cmp(&a.created));
    out.truncate(60);
    out
}

#[derive(Serialize)]
pub struct RestoreItem {
    pub name: String,
    pub ok: bool,
    pub output: String,
}

#[derive(Serialize)]
pub struct RestoreReport {
    pub results: Vec<RestoreItem>,
    pub still_disabled: Vec<String>,
}

#[tauri::command]
pub async fn backup_restore(
    state: State<'_, AppState>,
    path: String,
    packages: Option<Vec<String>>,
) -> Res<RestoreReport> {
    let (adb, target) = session(&state)?;
    let dir = PathBuf::from(path);
    let manifest =
        read_manifest(&dir).ok_or("That folder has no manifest.json, so it is not a backup.")?;
    let wanted = packages.unwrap_or(manifest.disabled);
    on_tv(adb, target, move |adb, target| {
        let mut results = Vec::new();
        for name in &wanted {
            if !crate::adb::is_package_name(name) {
                continue;
            }
            let out = adb.shell_text(
                target,
                &format!("pm enable --user 0 {name}"),
                Duration::from_secs(20),
            );
            results.push(match out {
                Ok(text) => RestoreItem {
                    name: name.clone(),
                    ok: !text.to_ascii_lowercase().contains("error"),
                    output: text,
                },
                Err(e) => RestoreItem { name: name.clone(), ok: false, output: e },
            });
            std::thread::sleep(Duration::from_millis(80));
        }
        let _ = adb.shell_text(target, "sync", Duration::from_secs(30));
        let disabled = adb
            .shell_text(target, "pm list packages -d", Duration::from_secs(30))
            .unwrap_or_default();
        // Compare whole package names: `contains("package:com.foo")` would also match com.foo.bar.
        let still: Vec<&str> = disabled
            .lines()
            .filter_map(|l| l.trim().strip_prefix("package:"))
            .collect();
        let still_disabled = wanted
            .iter()
            .filter(|p| still.iter().any(|d| *d == p.as_str()))
            .cloned()
            .collect();
        Ok(RestoreReport { results, still_disabled })
    })
    .await
}
