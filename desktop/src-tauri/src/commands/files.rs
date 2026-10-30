//! File browser over ADB: list, read, edit, pull, push, delete, and install an APK from disk.

use super::{human_time, on_tv, session, shell_quote, timestamp, Res};
use crate::state::AppState;
use regex::Regex;
use serde::Serialize;
use std::path::PathBuf;
use std::time::Duration;
use tauri::State;

#[derive(Serialize, Clone)]
pub struct Entry {
    pub name: String,
    pub path: String,
    pub dir: bool,
    pub size: u64,
    pub perms: String,
    pub modified: String,
    pub link: String,
}

#[derive(Serialize)]
pub struct Listing {
    pub path: String,
    pub entries: Vec<Entry>,
    pub free: String,
    pub raw_empty: bool,
}

fn join(base: &str, name: &str) -> String {
    if base == "/" {
        format!("/{name}")
    } else {
        format!("{}/{}", base.trim_end_matches('/'), name)
    }
}

fn parse_ls(out: &str, base: &str) -> Vec<Entry> {
    let month = Regex::new(r"^[A-Z][a-z]{2}$").unwrap();
    let iso = Regex::new(r"^\d{4}-\d{2}-\d{2}$").unwrap();
    let mut entries = Vec::new();
    for line in out.lines() {
        let line = line.trim_end();
        if line.is_empty() || line.starts_with("total ") {
            continue;
        }
        let tokens: Vec<&str> = line.split_whitespace().collect();
        if tokens.len() < 8 {
            continue;
        }
        let perm = tokens[0];
        if perm.len() < 10 || !matches!(perm.chars().next(), Some('d' | '-' | 'l' | 'c' | 'b' | 'p' | 's'))
        {
            continue;
        }
        let size: u64 = tokens[4].parse().unwrap_or(0);
        // toybox prints either 2024-05-01 12:00 or May 01 12:00 / May 01 2024.
        let name_at = if iso.is_match(tokens[5]) {
            7
        } else if month.is_match(tokens[5]) {
            8
        } else {
            7
        };
        if tokens.len() <= name_at {
            continue;
        }
        let rest = tokens[name_at..].join(" ");
        let (name, link) = match rest.split_once(" -> ") {
            Some((n, l)) => (n.to_string(), l.to_string()),
            None => (rest, String::new()),
        };
        if name == "." || name == ".." {
            continue;
        }
        entries.push(Entry {
            path: join(base, &name),
            name,
            dir: perm.starts_with('d'),
            size,
            perms: perm.to_string(),
            modified: tokens[5..name_at].join(" "),
            link,
        });
    }
    entries.sort_by(|a, b| b.dir.cmp(&a.dir).then_with(|| a.name.to_lowercase().cmp(&b.name.to_lowercase())));
    entries
}

#[tauri::command]
pub async fn fs_list(state: State<'_, AppState>, path: String) -> Res<Listing> {
    let path = normalize(&path)?;
    let (adb, target) = session(&state)?;
    on_tv(adb, target, move |adb, target| {
        let out = adb.shell_text(
            target,
            &format!("ls -la {}", shell_quote(&path)),
            Duration::from_secs(25),
        )?;
        if crate::commands::is_failure(&out) && !out.contains("total") && out.trim().is_empty() {
            return Err(format!("Cannot read {path}: {}", out.trim()));
        }
        let entries = parse_ls(&out, &path);
        let free = adb
            .shell_text(target, "df -h /data", Duration::from_secs(10))
            .ok()
            .and_then(|t| t.lines().last().map(|l| l.split_whitespace().nth(3).unwrap_or("").to_string()))
            .unwrap_or_default();
        Ok(Listing { raw_empty: entries.is_empty(), entries, path, free })
    })
    .await
}

fn normalize(path: &str) -> Res<String> {
    let p = path.trim();
    if p.is_empty() {
        return Ok("/sdcard".into());
    }
    if !p.starts_with('/') {
        return Err("Use an absolute path, such as /sdcard.".into());
    }
    if p.contains('\0') || p.contains('\n') {
        return Err("That path has a control character in it.".into());
    }
    let mut out: Vec<&str> = Vec::new();
    for part in p.split('/') {
        match part {
            "" | "." => {}
            ".." => {
                out.pop();
            }
            other => out.push(other),
        }
    }
    Ok(format!("/{}", out.join("/")))
}

const MAX_TEXT: u64 = 512 * 1024;

#[derive(Serialize)]
pub struct FileText {
    pub path: String,
    pub text: String,
    pub size: u64,
    pub truncated: bool,
    pub binary: bool,
}

#[tauri::command]
pub async fn fs_read(state: State<'_, AppState>, path: String) -> Res<FileText> {
    let path = normalize(&path)?;
    let (adb, target) = session(&state)?;
    on_tv(adb, target, move |adb, target| {
        let stat = adb
            .shell_text(target, &format!("stat -c %s {}", shell_quote(&path)), Duration::from_secs(10))
            .unwrap_or_default();
        let size: u64 = stat.trim().parse().unwrap_or(0);
        if size > MAX_TEXT {
            return Err(format!(
                "That file is {:.1} MB. The editor stops at 512 KB; pull it to this computer instead.",
                size as f64 / 1024.0 / 1024.0
            ));
        }
        let text = adb.shell_text(target, &format!("cat {}", shell_quote(&path)), Duration::from_secs(25))?;
        let binary = text.contains('\0') || text.chars().take(2048).any(|c| c == '\u{fffd}');
        Ok(FileText { path, text, size, truncated: false, binary })
    })
    .await
}

#[derive(Serialize)]
pub struct WriteResult {
    pub path: String,
    pub size: u64,
    pub detail: String,
}

#[tauri::command]
pub async fn fs_write(state: State<'_, AppState>, path: String, text: String) -> Res<WriteResult> {
    let path = normalize(&path)?;
    let (adb, target) = session(&state)?;
    let tmp = std::env::temp_dir().join(format!("cyt-edit-{}.txt", timestamp()));
    std::fs::write(&tmp, text.as_bytes()).map_err(|e| e.to_string())?;
    let local = tmp.display().to_string();
    let result = on_tv(adb, target, move |adb, target| {
        let out = adb.push(target, &local, &path, Duration::from_secs(60))?;
        Ok(WriteResult {
            path,
            size: text.len() as u64,
            detail: out.trim().to_string(),
        })
    })
    .await;
    let _ = std::fs::remove_file(&tmp);
    result
}

#[derive(Serialize)]
pub struct LocalFile {
    pub path: String,
    pub size: u64,
    pub md5: String,
    pub detail: String,
}

#[tauri::command]
pub async fn fs_pull(
    state: State<'_, AppState>,
    remote: String,
    local: String,
) -> Res<LocalFile> {
    let remote = normalize(&remote)?;
    let (adb, target) = session(&state)?;
    on_tv(adb, target, move |adb, target| {
        let out = adb.pull(target, &remote, &local, Duration::from_secs(900))?;
        let path = PathBuf::from(&local);
        let size = std::fs::metadata(&path).map(|m| m.len()).unwrap_or(0);
        let md5 = crate::adb::md5_file(&path).unwrap_or_default();
        Ok(LocalFile { path: local, size, md5, detail: out })
    })
    .await
}

#[derive(Serialize)]
pub struct PushResult {
    pub path: String,
    pub size: u64,
    pub md5: String,
    pub verified: bool,
    pub detail: String,
}

#[tauri::command]
pub async fn fs_push(
    state: State<'_, AppState>,
    local: String,
    remote: String,
    verify: bool,
) -> Res<PushResult> {
    let remote = normalize(&remote)?;
    let (adb, target) = session(&state)?;
    on_tv(adb, target, move |adb, target| {
        let host_md5 = crate::adb::md5_file(PathBuf::from(&local).as_path())?;
        let out = adb.push(target, &local, &remote, Duration::from_secs(900))?;
        let remote_md5 = adb
            .shell_text(target, &format!("md5sum {}", shell_quote(&remote)), Duration::from_secs(60))
            .unwrap_or_default();
        let verified = !verify || remote_md5.contains(&host_md5);
        let size = adb
            .shell_text(target, &format!("stat -c %s {}", shell_quote(&remote)), Duration::from_secs(15))
            .ok()
            .and_then(|t| t.trim().parse().ok())
            .unwrap_or(0);
        Ok(PushResult {
            path: remote,
            size,
            md5: host_md5,
            verified,
            detail: out.trim().to_string(),
        })
    })
    .await
}

#[tauri::command]
pub async fn fs_mkdir(state: State<'_, AppState>, path: String) -> Res<String> {
    let path = normalize(&path)?;
    let (adb, target) = session(&state)?;
    on_tv(adb, target, move |adb, target| {
        adb.shell_text(target, &format!("mkdir -p {}", shell_quote(&path)), Duration::from_secs(15))
    })
    .await
}

#[tauri::command]
pub async fn fs_delete(state: State<'_, AppState>, path: String) -> Res<String> {
    let path = normalize(&path)?;
    if matches!(
        path.as_str(),
        "/" | "/sdcard" | "/storage" | "/storage/emulated" | "/storage/emulated/0" | "/data" | "/system"
    ) {
        return Err("The app will not delete a top-level folder.".into());
    }
    let (adb, target) = session(&state)?;
    on_tv(adb, target, move |adb, target| {
        adb.shell_text(target, &format!("rm -rf -- {}", shell_quote(&path)), Duration::from_secs(60))
            .map(|_| format!("Deleted {path}"))
    })
    .await
}

#[derive(Serialize)]
pub struct Root {
    pub path: String,
    pub label: String,
    pub readable: bool,
}

#[tauri::command]
pub async fn fs_roots(state: State<'_, AppState>) -> Res<Vec<Root>> {
    let (adb, target) = session(&state)?;
    on_tv(adb, target, |adb, target| {
        let wanted = [
            ("/sdcard", "Internal storage"),
            ("/sdcard/Download", "Downloads"),
            ("/sdcard/Android/data", "App data"),
            ("/data/local/tmp", "Shell staging"),
            ("/data/app", "Installed apps"),
            ("/system/app", "System apps"),
            ("/data/system", "System settings"),
        ];
        let mut out = Vec::new();
        for (path, label) in wanted {
            let readable = adb
                .shell_text(target, &format!("ls -d {path} 2>/dev/null"), Duration::from_secs(8))
                .map(|t| t.contains(path))
                .unwrap_or(false);
            out.push(Root { path: path.into(), label: label.into(), readable });
        }
        Ok(out)
    })
    .await
}

#[derive(Serialize)]
pub struct InstallResult {
    pub ok: bool,
    pub output: String,
    pub fallback: bool,
    pub sdk: u32,
}

#[tauri::command]
pub async fn apk_install(state: State<'_, AppState>, local: String) -> Res<InstallResult> {
    let path = PathBuf::from(&local);
    if !path.is_file() {
        return Err("That APK is not on this computer any more.".into());
    }
    let (adb, target) = session(&state)?;
    on_tv(adb, target, move |adb, target| {
        let sdk = adb
            .shell_text(target, "getprop ro.build.version.sdk", Duration::from_secs(10))
            .ok()
            .and_then(|t| t.trim().parse::<u32>().ok())
            .unwrap_or(0);
        let out = adb.install(target, &local, Duration::from_secs(240));
        match out {
            Ok(text) if text.contains("Success") => Ok(InstallResult {
                ok: true,
                output: text,
                fallback: false,
                sdk,
            }),
            first => {
                // Android 14 can leave `adb install` waiting for an on-screen confirmation. Push
                // the APK and run pm install over the shell instead, as PROMPT.md says.
                let tmp = format!("/data/local/tmp/cyt-{}.apk", timestamp());
                adb.push(target, &local, &tmp, Duration::from_secs(600))?;
                let text = adb
                    .shell_text(
                        target,
                        &format!("pm install -r -d {}", shell_quote(&tmp)),
                        Duration::from_secs(240),
                    )
                    .unwrap_or_default();
                let _ = adb.shell_text(target, &format!("rm -f {}", shell_quote(&tmp)), Duration::from_secs(15));
                let ok = text.contains("Success");
                let mut output = text;
                if let Err(e) = first {
                    output = format!("{output}\n(adb install: {e})");
                }
                Ok(InstallResult { ok, output, fallback: true, sdk })
            }
        }
    })
    .await
}

#[derive(Serialize)]
pub struct ExtractResult {
    pub local: String,
    pub size: u64,
}

/// Pull any file from the TV to a folder this computer can show.
#[tauri::command]
pub async fn fs_pull_to(
    state: State<'_, AppState>,
    remote: String,
    folder: String,
) -> Res<ExtractResult> {
    let remote = normalize(&remote)?;
    let name = remote.rsplit('/').next().unwrap_or("file").to_string();
    let local = PathBuf::from(folder).join(name);
    let (adb, target) = session(&state)?;
    on_tv(adb, target, move |adb, target| {
        adb.pull(target, &remote, &local.display().to_string(), Duration::from_secs(900))?;
        let size = std::fs::metadata(&local).map(|m| m.len()).unwrap_or(0);
        Ok(ExtractResult { local: local.display().to_string(), size })
    })
    .await
}

#[derive(Serialize)]
pub struct SavedShot {
    pub path: String,
    pub when: String,
}

#[tauri::command]
pub fn shots_list(state: State<'_, AppState>) -> Vec<SavedShot> {
    let dir = super::output_root(&state).join("shots");
    let mut out = Vec::new();
    if let Ok(entries) = std::fs::read_dir(&dir) {
        for entry in entries.flatten() {
            if entry.path().extension().map(|e| e == "png").unwrap_or(false) {
                let meta = entry.metadata().ok();
                out.push(SavedShot {
                    path: entry.path().display().to_string(),
                    when: meta
                        .and_then(|m| m.modified().ok())
                        .map(|t| {
                            let dt: chrono::DateTime<chrono::Local> = t.into();
                            dt.format("%Y-%m-%d %H:%M").to_string()
                        })
                        .unwrap_or_default(),
                });
            }
        }
    }
    out.sort_by(|a, b| b.path.cmp(&a.path));
    out.truncate(40);
    out
}

#[derive(Serialize)]
pub struct PathInfo {
    pub path: String,
    pub exists: bool,
    pub size: u64,
}

#[tauri::command]
pub async fn fs_stat(state: State<'_, AppState>, path: String) -> Res<PathInfo> {
    let path = normalize(&path)?;
    let (adb, target) = session(&state)?;
    on_tv(adb, target, move |adb, target| {
        match adb.shell_text(
            target,
            &format!("if [ -e {p} ]; then stat -c %s {p}; else echo MISSING; fi", p = shell_quote(&path)),
            Duration::from_secs(10),
        ) {
            Ok(text) if text.contains("MISSING") => Ok(PathInfo { path, exists: false, size: 0 }),
            Ok(text) => Ok(PathInfo {
                path,
                exists: true,
                size: text.trim().parse().unwrap_or(0),
            }),
            Err(_) => Ok(PathInfo { path, exists: false, size: 0 }),
        }
    })
    .await
}

#[tauri::command]
pub fn local_now() -> String {
    human_time()
}
