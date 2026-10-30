//! Shared helpers for the command layer: session lookup, Home/Wi-Fi guards and output checks.

pub mod backup;
pub mod core;
pub mod files;
pub mod guards;
pub mod launcher;
pub mod packages;
pub mod tv;

use crate::adb::Adb;
use crate::state::AppState;
use regex::Regex;
use std::path::PathBuf;
use std::sync::OnceLock;
use tauri::State;

pub type Res<T> = Result<T, String>;

pub fn session(state: &State<'_, AppState>) -> Res<(Adb, String)> {
    let inner = state.0.lock().map_err(|_| "the app state is locked".to_string())?;
    let adb = inner.adb.clone().ok_or_else(|| {
        "ADB was not found. Install Android platform-tools, or set the path to adb in Settings."
            .to_string()
    })?;
    let target = inner
        .target
        .clone()
        .ok_or_else(|| "No TV is connected yet. Open Connect and connect to one first.".to_string())?;
    Ok((adb, target))
}

pub fn adb_only(state: &State<'_, AppState>) -> Res<Adb> {
    let inner = state.0.lock().map_err(|_| "the app state is locked".to_string())?;
    inner.adb.clone().ok_or_else(|| {
        "ADB was not found. Install Android platform-tools, or set the path to adb in Settings."
            .to_string()
    })
}

pub fn output_root(state: &State<'_, AppState>) -> PathBuf {
    let inner = match state.0.lock() {
        Ok(i) => i,
        Err(_) => return PathBuf::from("."),
    };
    match &inner.settings.backup_dir {
        Some(dir) if !dir.trim().is_empty() => PathBuf::from(dir),
        _ => inner.paths.home_dir.join("CleanYourTV"),
    }
}

pub async fn on_tv<T, F>(adb: Adb, target: String, f: F) -> Res<T>
where
    F: FnOnce(&Adb, &str) -> Res<T> + Send + 'static,
    T: Send + 'static,
{
    tauri::async_runtime::spawn_blocking(move || f(&adb, &target))
        .await
        .map_err(|e| format!("background task failed: {e}"))?
}

pub fn current_user(adb: &Adb, target: &str) -> u32 {
    adb.shell_text(target, "am get-current-user", std::time::Duration::from_secs(8))
        .ok()
        .and_then(|t| t.trim().parse().ok())
        .unwrap_or(0)
}

fn activity_re() -> &'static Regex {
    static RE: OnceLock<Regex> = OnceLock::new();
    RE.get_or_init(|| Regex::new(r"([A-Za-z0-9_.]+/[A-Za-z0-9_.$]+)").expect("activity regex"))
}

/// Every app that can answer Home right now, as `package/activity`.
pub fn home_candidates(adb: &Adb, target: &str) -> Res<Vec<String>> {
    let user = current_user(adb, target);
    let out = adb.shell_text(
        target,
        &format!(
            "cmd package query-activities --brief --user {user} -a android.intent.action.MAIN -c android.intent.category.HOME"
        ),
        std::time::Duration::from_secs(20),
    )?;
    let mut seen = Vec::new();
    for capture in activity_re().captures_iter(&out) {
        let name = capture[1].to_string();
        let pkg = name.split('/').next().unwrap_or("").to_string();
        // Fallback home screens are not launchers: without them a set still has a Home.
        if pkg.ends_with(".settings") || pkg == "com.android.tv.settings" {
            continue;
        }
        if !seen.contains(&name) {
            seen.push(name);
        }
    }
    Ok(seen)
}

/// The app Android would start for Home, as `package/activity`.
pub fn current_home(adb: &Adb, target: &str) -> Res<String> {
    let user = current_user(adb, target);
    let out = adb.shell_text(
        target,
        &format!(
            "cmd package resolve-activity --brief --user {user} -a android.intent.action.MAIN -c android.intent.category.HOME"
        ),
        std::time::Duration::from_secs(15),
    )?;
    Ok(out
        .lines()
        .last()
        .unwrap_or("")
        .trim()
        .to_string())
}

/// The window with focus, as `package/activity`.
pub fn focused_activity(adb: &Adb, target: &str) -> String {
    let out = adb
        .shell_text(
            target,
            "dumpsys activity activities | grep mFocusedApp",
            std::time::Duration::from_secs(15),
        )
        .unwrap_or_default();
    activity_re()
        .captures(&out)
        .map(|c| c[1].to_string())
        .unwrap_or_default()
}

/// Whether the setup wizard owns the saved Wi-Fi network.
///
/// `None` means the TV could not be asked: the caller must treat that as "do not disable it",
/// exactly as `tools/disable.sh` does.
pub fn setupwraith_owns_wifi(adb: &Adb, target: &str) -> Option<bool> {
    let out = adb
        .shell_text(target, "dumpsys wifi", std::time::Duration::from_secs(20))
        .ok()?;
    let owners: Vec<&str> = out
        .split_whitespace()
        .filter_map(|w| w.strip_prefix("cname="))
        .collect();
    if owners.is_empty() {
        return None;
    }
    Some(owners.iter().any(|o| o.contains("setupwraith")))
}

/// Shell output that raised an Android-side error before it finished.
pub fn is_failure(text: &str) -> bool {
    let t = text.to_ascii_lowercase();
    t.contains("error:")
        || t.contains("exception")
        || t.contains("permission denial")
        || t.contains("securityexception")
        || t.contains("failure")
        || t.contains("not found")
        || t.contains("unknown package")
}

/// Quote a value for the TV's shell: a single-quoted string, with embedded quotes escaped the
/// POSIX way. Every path or text that reaches `sh` goes through here.
pub fn shell_quote(value: &str) -> String {
    format!("'{}'", value.replace('\'', r"'\''"))
}

pub fn timestamp() -> String {
    chrono::Local::now().format("%Y%m%d-%H%M%S").to_string()
}

pub fn human_time() -> String {
    chrono::Local::now().format("%Y-%m-%d %H:%M:%S").to_string()
}

#[cfg(test)]
mod tests {
    use super::shell_quote;

    #[test]
    fn quoting_survives_quotes_and_shell_metacharacters() {
        // The same rule as the POSIX shell: close, escape, reopen.
        assert_eq!(shell_quote("plain"), "'plain'");
        assert_eq!(shell_quote("Merhaba KRALIM"), "'Merhaba KRALIM'");
        assert_eq!(shell_quote("Kral'ım; rm -rf /"), r"'Kral'\''ım; rm -rf /'");
        assert_eq!(shell_quote("$(reboot) & `id`"), "'$(reboot) & `id`'");
    }
}
