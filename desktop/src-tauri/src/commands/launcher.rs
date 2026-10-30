//! Launcher work: read and edit the built-in `config.xml` and `strings.xml`, push a theme overlay,
//! set Home, build the APK and install it.
//!
//! Two different things live here on purpose:
//!
//! * `config.xml` and `strings.xml` are compiled into the APK. Editing them changes the source, and
//!   the change reaches the TV only after a build and install.
//! * `config.json` is the launcher's runtime overlay. Pushing it changes the look immediately, with
//!   no build, through the launcher's ThemeProvider (the same path `tools/theme.sh` uses).

use super::{
    current_home, current_user, focused_activity, home_candidates, on_tv, session, timestamp, Res,
};
use crate::adb::Adb;
use crate::rules;
use crate::state::AppState;
use quick_xml::events::Event;
use quick_xml::Reader;
use serde::Serialize;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::Duration;
use tauri::State;

pub const OUR_LAUNCHER: &str = rules::OUR_LAUNCHER;
pub const MIN_SDK: u32 = 26;
const THEME_DIR: &str = "/sdcard/Android/data/com.gathin.tvhome/files";
const THEME_URI: &str = "content://com.gathin.tvhome.theme";

#[derive(Serialize)]
pub struct LauncherStatus {
    pub installed: bool,
    pub version: String,
    pub sdk: u32,
    pub min_sdk: u32,
    pub supported: bool,
    pub home: String,
    pub candidates: Vec<String>,
    pub focused: String,
    pub is_home: bool,
    pub overlay: Option<String>,
    pub overlay_exists: bool,
    pub wireless: Option<bool>,
    pub adb_secure: bool,
}

#[tauri::command]
pub async fn launcher_status(state: State<'_, AppState>) -> Res<LauncherStatus> {
    let (adb, target) = session(&state)?;
    on_tv(adb, target, |adb, target| {
        let sdk = adb
            .shell_text(target, "getprop ro.build.version.sdk", Duration::from_secs(10))
            .ok()
            .and_then(|t| t.trim().parse::<u32>().ok())
            .unwrap_or(0);
        let installed = adb
            .shell_text(target, "pm list packages com.gathin.tvhome", Duration::from_secs(10))
            .map(|t| t.contains(OUR_LAUNCHER))
            .unwrap_or(false);
        let version = if installed {
            adb.shell_text(
                target,
                "dumpsys package com.gathin.tvhome | grep versionName",
                Duration::from_secs(10),
            )
            .map(|t| t.split_whitespace().last().unwrap_or("").to_string())
            .unwrap_or_default()
        } else {
            String::new()
        };
        let home = current_home(adb, target).unwrap_or_default();
        let candidates = home_candidates(adb, target).unwrap_or_default();
        let overlay = read_overlay(adb, target).ok().flatten();
        Ok(LauncherStatus {
            installed,
            version,
            sdk,
            min_sdk: MIN_SDK,
            supported: sdk >= MIN_SDK,
            is_home: home.split('/').next() == Some(OUR_LAUNCHER),
            home,
            candidates,
            focused: focused_activity(adb, target),
            overlay_exists: overlay.is_some(),
            overlay,
            wireless: adb
                .shell_text(target, "settings get global adb_wifi_enabled", Duration::from_secs(8))
                .ok()
                .and_then(|t| match t.trim() {
                    "1" => Some(true),
                    "0" => Some(false),
                    _ => None,
                }),
            adb_secure: adb
                .shell_text(target, "getprop ro.adb.secure", Duration::from_secs(8))
                .map(|t| !t.trim().is_empty() && t.trim() != "0")
                .unwrap_or(false),
        })
    })
    .await
}

/// `config.json` on the TV, if the launcher has one.
fn read_overlay(adb: &Adb, target: &str) -> Res<Option<String>> {
    for command in [
        format!("content read --uri {THEME_URI}/config.json 2>/dev/null"),
        format!("cat {THEME_DIR}/config.json 2>/dev/null"),
    ] {
        if let Ok(text) = adb.shell_text(target, &command, Duration::from_secs(15)) {
            let trimmed = text.trim();
            if trimmed.starts_with('{') {
                return Ok(Some(trimmed.to_string()));
            }
        }
    }
    Ok(None)
}

#[derive(Serialize)]
pub struct Step {
    pub label: String,
    pub ok: bool,
    pub detail: String,
}

fn step(label: impl Into<String>, ok: bool, detail: impl Into<String>) -> Step {
    Step { label: label.into(), ok, detail: detail.into() }
}

fn restart_home(adb: &Adb, target: &str) -> Res<String> {
    let user = current_user(adb, target);
    let home = current_home(adb, target).unwrap_or_default();
    adb.shell_text(
        target,
        &format!("am force-stop --user {user} {OUR_LAUNCHER}"),
        Duration::from_secs(15),
    )?;
    std::thread::sleep(Duration::from_millis(400));
    if home.starts_with(OUR_LAUNCHER) {
        adb.shell_text(
            target,
            &format!("am start --user {user} -a android.intent.action.MAIN -c android.intent.category.HOME"),
            Duration::from_secs(20),
        )?;
    } else {
        adb.shell_text(
            target,
            &format!("am start --user {user} -n {OUR_LAUNCHER}/.MainActivity"),
            Duration::from_secs(20),
        )?;
    }
    std::thread::sleep(Duration::from_millis(900));
    Ok(focused_activity(adb, target))
}

/// Write one file through the launcher's ThemeProvider, falling back to cp for firmware whose
/// `content write` is missing (Android 8/9 print "Unsupported operation" and exit 0).
fn put_theme_file(adb: &Adb, target: &str, remote_rel: &str, local: &str) -> Res<Step> {
    let tmp = format!("/data/local/tmp/cyt-theme-{}", remote_rel.replace('/', "-"));
    adb.push(target, local, &tmp, Duration::from_secs(300))?;
    let parent = remote_rel
        .rsplit_once('/')
        .map(|(p, _)| format!("{THEME_DIR}/{p}"))
        .unwrap_or_else(|| THEME_DIR.to_string());
    let _ = adb.shell_text(
        target,
        &format!(
            "mkdir -p {parent} 2>/dev/null; rm -f {THEME_DIR}/{remote_rel} 2>/dev/null"
        ),
        Duration::from_secs(10),
    );
    let out = adb.shell_text(
        target,
        &format!(
            "o=$(content write --uri {THEME_URI}/{remote_rel} < {tmp} 2>&1) && \
             case \"$o\" in *Unsupported*) echo __UNSUPPORTED__ ;; *) echo \"$o\" ;; esac",
        ),
        Duration::from_secs(40),
    );
    let detail;
    let mut ok = true;
    match out {
        Ok(text) if text.contains("__UNSUPPORTED__") => {
            let cp = adb.shell_text(
                target,
                &format!("cp {tmp} {THEME_DIR}/{remote_rel}"),
                Duration::from_secs(30),
            )?;
            detail = format!("written with cp (the provider has no write on this Android): {cp}");
            if cp.to_ascii_lowercase().contains("denied") {
                ok = false;
            }
        }
        Ok(text) => detail = text.trim().to_string(),
        Err(e) => {
            ok = false;
            detail = e;
        }
    }
    let _ = adb.shell_text(target, &format!("rm -f {tmp}"), Duration::from_secs(10));
    Ok(step(format!("Write {remote_rel}"), ok, detail))
}

#[derive(Serialize)]
pub struct ThemeReport {
    pub steps: Vec<Step>,
    pub focused: String,
    pub overlay_exists: bool,
}

fn safe_name(name: &str) -> bool {
    !name.is_empty()
        && !name.contains("..")
        && name
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '.' || c == '_' || c == '-')
}

/// Push a `config.json` overlay and restart Home: the no-build path.
#[tauri::command]
pub async fn theme_push(
    state: State<'_, AppState>,
    config: String,
    wallpaper: Option<String>,
    wallpapers: Vec<String>,
) -> Res<ThemeReport> {
    let parsed: serde_json::Value = serde_json::from_str(&config)
        .map_err(|e| format!("That is not valid JSON, so the launcher would ignore it: {e}"))?;
    if !parsed.is_object() {
        return Err(
            "The overlay has to be a JSON object of theme keys. An array or a plain value would \
             make the launcher drop the overlay and go back to its built-in config."
                .into(),
        );
    }
    if config.len() > 512 * 1024 {
        return Err("That overlay is far too large; themes are a few kilobytes.".into());
    }
    let (adb, target) = session(&state)?;
    let temp_dir = state.0.lock().unwrap().paths.config_dir.join("tmp");
    std::fs::create_dir_all(&temp_dir).map_err(|e| e.to_string())?;
    let local = temp_dir.join(format!("config-{}.json", timestamp()));
    std::fs::write(&local, format!("{}\n", config.trim())).map_err(|e| e.to_string())?;
    let local_str = local.display().to_string();

    on_tv(adb, target, move |adb, target| {
        let mut steps = Vec::new();
        let installed = adb
            .shell_text(target, "pm list packages com.gathin.tvhome", Duration::from_secs(10))
            .map(|t| t.contains(OUR_LAUNCHER))
            .unwrap_or(false);
        if !installed {
            return Err("The clean launcher is not installed on the TV, so there is nothing to push a theme to.".into());
        }
        steps.push(put_theme_file(adb, target, "config.json", &local_str)?);

        // A revision marker makes the launcher drop on-TV layout/motion choices even when the JSON
        // bytes are identical to the last push.
        let revision = format!(
            "{}-{}",
            timestamp(),
            std::process::id()
        );
        let rev_local = local_str.replace("config-", "rev-");
        std::fs::write(&rev_local, &revision).map_err(|e| e.to_string())?;
        let rev_step = put_theme_file(adb, target, "theme_revision", &rev_local);
        if let Ok(s) = rev_step {
            steps.push(s);
        }
        let _ = std::fs::remove_file(&rev_local);

        if let Some(path) = wallpaper.as_ref().filter(|p| Path::new(p).is_file()) {
            steps.push(put_theme_file(adb, target, "wallpaper.jpg", path)?);
        }
        for path in wallpapers.iter().filter(|p| Path::new(p).is_file()) {
            let name = Path::new(path)
                .file_name()
                .map(|n| n.to_string_lossy().to_string())
                .unwrap_or_default();
            if !safe_name(&name) {
                steps.push(step(
                    format!("Wallpaper {name}"),
                    false,
                    "unsupported filename: use letters, digits, dot, dash and underscore",
                ));
                continue;
            }
            steps.push(put_theme_file(adb, target, &format!("wallpapers/{name}"), path)?);
        }

        let focused = restart_home(adb, target)?;
        let overlay_exists = read_overlay(adb, target).ok().flatten().is_some();
        steps.push(step(
            "Home restarted",
            focused.starts_with(OUR_LAUNCHER),
            format!("focused now: {focused}"),
        ));
        Ok(ThemeReport { steps, focused, overlay_exists })
    })
    .await
}

/// Remove the overlay: back to the built-in `config.xml`, also with no build.
#[tauri::command]
pub async fn theme_reset(state: State<'_, AppState>) -> Res<ThemeReport> {
    let (adb, target) = session(&state)?;
    on_tv(adb, target, |adb, target| {
        let mut steps = Vec::new();
        let out = adb.shell_text(
            target,
            &format!(
                "rm -f {THEME_DIR}/config.json {THEME_DIR}/wallpaper.jpg 2>/dev/null; \
                 content delete --uri {THEME_URI}/config.json 2>/dev/null; \
                 content delete --uri {THEME_URI}/wallpapers 2>/dev/null; \
                 rm -f {THEME_DIR}/wallpapers/* 2>/dev/null; \
                 rm -f {THEME_DIR}/theme_revision 2>/dev/null; echo done"
            ),
            Duration::from_secs(40),
        )?;
        steps.push(step("Overlay removed", out.contains("done"), out.trim().to_string()));
        let focused = restart_home(adb, target)?;
        let overlay_exists = read_overlay(adb, target).ok().flatten().is_some();
        steps.push(step(
            "Home restarted",
            focused.starts_with(OUR_LAUNCHER),
            format!("focused now: {focused}"),
        ));
        Ok(ThemeReport { steps, focused, overlay_exists })
    })
    .await
}

#[derive(Serialize)]
pub struct HomeReport {
    pub steps: Vec<Step>,
    pub home: String,
    pub focused: String,
    pub ok: bool,
    pub cvte: bool,
}

fn cvte_present(adb: &Adb, target: &str) -> bool {
    adb.shell_text(target, "pm list packages com.cvte.", Duration::from_secs(10))
        .map(|t| t.contains("com.cvte."))
        .unwrap_or(false)
}

/// Make the clean launcher Home and prove it took.
#[tauri::command]
pub async fn launcher_set_home(state: State<'_, AppState>) -> Res<HomeReport> {
    let (adb, target) = session(&state)?;
    on_tv(adb, target, |adb, target| {
        let mut steps = Vec::new();
        let installed = adb
            .shell_text(target, "pm list packages com.gathin.tvhome", Duration::from_secs(10))
            .map(|t| t.contains(OUR_LAUNCHER))
            .unwrap_or(false);
        if !installed {
            return Err("The clean launcher is not installed on the TV yet.".into());
        }
        let candidates = home_candidates(adb, target).unwrap_or_default();
        let component = candidates
            .iter()
            .find(|c| c.starts_with(&format!("{OUR_LAUNCHER}/")))
            .cloned()
            .ok_or(
                "The clean launcher does not answer Home on this TV, so it cannot take the Home key.",
            )?;
        let out = adb.shell_text(
            target,
            &format!("cmd package set-home-activity {component}"),
            Duration::from_secs(20),
        )?;
        steps.push(step("set-home-activity", out.to_ascii_lowercase().contains("success"), out.trim().to_string()));
        std::thread::sleep(Duration::from_millis(800));

        let home = current_home(adb, target).unwrap_or_default();
        let focused = focused_activity(adb, target);
        // Some Google TV builds print Success and keep the old Home. Say so instead of pretending.
        let took = home.starts_with(OUR_LAUNCHER);
        steps.push(step(
            "Home resolves to the clean launcher",
            took,
            if took { home.clone() } else { format!("still {home}") },
        ));
        steps.push(step(
            "Window in front",
            focused.starts_with(OUR_LAUNCHER),
            if focused.is_empty() { "the TV did not name a focused window".to_string() } else { focused.clone() },
        ));
        let cvte = cvte_present(adb, target);
        if cvte {
            steps.push(step(
                "CVTE board",
                true,
                "Reboot once and confirm Home and ADB come back before disabling the stock launcher \
                 or enabling a remote-key helper (#741).",
            ));
        }
        Ok(HomeReport { steps, home, focused, ok: took, cvte })
    })
    .await
}

#[derive(Serialize)]
pub struct LocaleDir {
    pub dir: String,
    pub label: String,
    pub config: bool,
    pub strings: bool,
}

#[derive(Serialize)]
pub struct RepoLayout {
    pub repo: String,
    pub res: String,
    pub locales: Vec<LocaleDir>,
}

fn res_dir(repo: &str) -> PathBuf {
    PathBuf::from(repo).join("launcher/app/src/main/res")
}

#[tauri::command]
pub fn config_layout(repo: String) -> Res<RepoLayout> {
    let res = res_dir(&repo);
    if !res.is_dir() {
        return Err(format!(
            "{} is not a CleanYourTV checkout: launcher/app/src/main/res is missing.",
            repo
        ));
    }
    let mut locales = Vec::new();
    let entries = std::fs::read_dir(&res).map_err(|e| e.to_string())?;
    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().to_string();
        if !name.starts_with("values") {
            continue;
        }
        let config = entry.path().join("config.xml").is_file();
        let strings = entry.path().join("strings.xml").is_file();
        if !config && !strings {
            continue;
        }
        let label = if name == "values" {
            "Default (English)".to_string()
        } else {
            name.trim_start_matches("values-").to_string()
        };
        locales.push(LocaleDir { dir: name, label, config, strings });
    }
    locales.sort_by(|a, b| a.dir.cmp(&b.dir));
    Ok(RepoLayout { repo: repo.to_string(), res: res.display().to_string(), locales })
}

#[derive(Serialize, Clone)]
pub struct ResEntry {
    pub name: String,
    pub kind: String,
    pub value: String,
    pub items: Vec<String>,
    pub translatable: Option<bool>,
}

fn xml_unescape(text: &str) -> String {
    text.replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}

fn xml_escape(text: &str) -> String {
    text.replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
}

/// Android string resources need their own escaping on top of XML's.
fn android_escape(text: &str) -> String {
    let mut out = String::with_capacity(text.len());
    for (i, c) in text.chars().enumerate() {
        match c {
            '\\' => out.push_str("\\\\"),
            '\'' => out.push_str("\\'"),
            '"' => out.push_str("\\\""),
            '@' | '?' if i == 0 => {
                out.push('\\');
                out.push(c);
            }
            '\n' => out.push_str("\\n"),
            _ => out.push(c),
        }
    }
    xml_escape(&out)
}

fn android_unescape(text: &str) -> String {
    xml_unescape(text)
        .replace("\\'", "'")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
}

fn find_element(xml: &str, kind: &str, name: &str) -> Option<(usize, usize, usize)> {
    // (start tag offset, content start, content end)
    let needle = format!("<{kind}");
    let name_attr = format!("name=\"{name}\"");
    let mut from = 0;
    while let Some(rel) = xml[from..].find(&needle) {
        let start = from + rel;
        let after = start + needle.len();
        let boundary = xml[after..].chars().next();
        if !matches!(boundary, Some(' ' | '\t' | '\r' | '\n' | '>')) {
            from = start + 1;
            continue;
        }
        let Some(tag_end_rel) = xml[after..].find('>') else { break };
        let tag_end = after + tag_end_rel;
        let attrs = &xml[after..tag_end];
        if !attrs.contains(&name_attr) {
            from = tag_end + 1;
            continue;
        }
        let close = format!("</{kind}>");
        let Some(close_rel) = xml[tag_end..].find(&close) else { return None };
        let content_start = tag_end + 1;
        let content_end = tag_end + close_rel;
        return Some((start, content_start, content_end));
    }
    None
}

fn line_indent(xml: &str, offset: usize) -> String {
    let line_start = xml[..offset].rfind('\n').map(|i| i + 1).unwrap_or(0);
    xml[line_start..offset]
        .chars()
        .take_while(|c| *c == ' ' || *c == '\t')
        .collect()
}

fn replace_element(xml: &str, kind: &str, name: &str, items: &[String], scalar: Option<&str>) -> Res<String> {
    let indent = "    ";
    if let Some((start, content_start, content_end)) = find_element(xml, kind, name) {
        let body = if kind.ends_with("-array") {
            let inner = line_indent(xml, start) + "    ";
            let mut text = String::from("\n");
            for item in items {
                text.push_str(&format!("{inner}<item>{}</item>\n", android_escape(item)));
            }
            text.push_str(&line_indent(xml, start));
            text
        } else {
            android_escape(scalar.unwrap_or(""))
        };
        let mut out = String::with_capacity(xml.len() + body.len());
        out.push_str(&xml[..content_start]);
        out.push_str(&body);
        out.push_str(&xml[content_end..]);
        return Ok(out);
    }

    // Not there yet: add it before </resources>.
    let body = if kind.ends_with("-array") {
        let mut text = format!("\n{indent}<{kind} name=\"{name}\" translatable=\"false\">\n");
        for item in items {
            text.push_str(&format!("{indent}    <item>{}</item>\n", android_escape(item)));
        }
        text.push_str(&format!("{indent}</{kind}>\n"));
        text
    } else {
        format!("\n{indent}<{kind} name=\"{name}\">{}</{kind}>\n", android_escape(scalar.unwrap_or("")))
    };
    let Some(close) = xml.rfind("</resources>") else {
        return Err("That file has no </resources>; it does not look like an Android resource file.".into());
    };
    let mut out = String::with_capacity(xml.len() + body.len());
    out.push_str(&xml[..close]);
    out.push_str(&body);
    out.push_str(&xml[close..]);
    Ok(out)
}

fn validate_xml(xml: &str) -> Res<()> {
    let mut reader = Reader::from_str(xml);
    reader.config_mut().trim_text(true);
    let mut root = String::new();
    loop {
        match reader.read_event() {
            Ok(Event::Start(e)) => {
                if root.is_empty() {
                    root = String::from_utf8_lossy(e.name().as_ref()).to_string();
                }
            }
            Ok(Event::Eof) => break,
            Err(e) => return Err(format!("The edit would leave invalid XML: {e}")),
            _ => {}
        }
    }
    if root != "resources" {
        return Err("The file's root element is not <resources>.".into());
    }
    Ok(())
}

fn parse_entries(xml: &str) -> Vec<ResEntry> {
    let mut out = Vec::new();
    for kind in ["bool", "string", "integer", "dimen", "color"] {
        let re = regex::Regex::new(&format!(
            r#"(?s)<{kind}\b([^>]*?)name="([^"]+)"([^>]*)>(.*?)</{kind}>"#
        ))
        .unwrap();
        for cap in re.captures_iter(xml) {
            let attrs = format!("{}{}", &cap[1], &cap[3]);
            out.push(ResEntry {
                name: cap[2].to_string(),
                kind: kind.to_string(),
                value: android_unescape(cap[4].trim()),
                items: Vec::new(),
                translatable: attrs.contains("translatable=\"false\"").then_some(false),
            });
        }
    }
    for kind in ["string-array", "integer-array"] {
        let re = regex::Regex::new(&format!(
            r#"(?s)<{kind}\b([^>]*?)name="([^"]+)"([^>]*)>(.*?)</{kind}>"#
        ))
        .unwrap();
        let item_re = regex::Regex::new(r"(?s)<item>(.*?)</item>").unwrap();
        for cap in re.captures_iter(xml) {
            let attrs = format!("{}{}", &cap[1], &cap[3]);
            let items = item_re
                .captures_iter(&cap[4])
                .map(|c| android_unescape(c[1].trim()))
                .collect();
            out.push(ResEntry {
                name: cap[2].to_string(),
                kind: kind.to_string(),
                value: String::new(),
                items,
                translatable: attrs.contains("translatable=\"false\"").then_some(false),
            });
        }
    }
    out.sort_by(|a, b| a.name.cmp(&b.name));
    out
}

#[derive(Serialize)]
pub struct ConfigDoc {
    pub path: String,
    pub xml: String,
    pub entries: Vec<ResEntry>,
    pub backup: String,
}

fn backup_file(repo: &str, path: &Path, config_dir: &Path) -> Res<String> {
    let _ = repo;
    let dir = config_dir.join("repo-edits");
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    let rel = path
        .components()
        .rev()
        .take(2)
        .collect::<Vec<_>>()
        .into_iter()
        .rev()
        .map(|c| c.as_os_str().to_string_lossy().to_string())
        .collect::<Vec<_>>()
        .join("-");
    let dest = dir.join(format!("{}-{rel}", timestamp()));
    std::fs::copy(path, &dest).map_err(|e| format!("{}: {e}", dest.display()))?;
    Ok(dest.display().to_string())
}

#[tauri::command]
pub fn config_read(
    _state: State<'_, AppState>,
    repo: String,
    locale: String,
    file: String,
) -> Res<ConfigDoc> {
    let res = res_dir(&repo);
    let dir = if locale.is_empty() { "values".to_string() } else { locale };
    let path = res.join(&dir).join(&file);
    let path = if path.is_file() {
        path
    } else {
        res.join("values").join(&file)
    };
    let xml = std::fs::read_to_string(&path).map_err(|e| format!("{}: {e}", path.display()))?;
    validate_xml(&xml)?;
    let entries = parse_entries(&xml);
    Ok(ConfigDoc {
        path: path.display().to_string(),
        xml,
        entries,
        backup: String::new(),
    })
}

#[tauri::command]
pub fn config_write(
    state: State<'_, AppState>,
    repo: String,
    path: String,
    name: String,
    kind: String,
    value: String,
    items: Vec<String>,
) -> Res<ConfigDoc> {
    if !["bool", "string", "integer", "dimen", "color", "string-array", "integer-array"]
        .contains(&kind.as_str())
    {
        return Err("That resource type cannot be written from here.".into());
    }
    let file = PathBuf::from(&path);
    let xml = std::fs::read_to_string(&file).map_err(|e| format!("{}: {e}", file.display()))?;
    validate_xml(&xml)?;
    let scalar = if kind.ends_with("-array") { None } else { Some(value.as_str()) };
    let updated = replace_element(&xml, &kind, &name, &items, scalar)?;
    validate_xml(&updated)?;
    let backup = {
        let inner = state.0.lock().unwrap();
        backup_file(&repo, &file, &inner.paths.config_dir)?
    };
    std::fs::write(&file, &updated).map_err(|e| format!("{}: {e}", file.display()))?;
    Ok(ConfigDoc {
        path: file.display().to_string(),
        xml: updated.clone(),
        entries: parse_entries(&updated),
        backup,
    })
}

#[tauri::command]
pub fn config_write_raw(
    state: State<'_, AppState>,
    repo: String,
    path: String,
    xml: String,
) -> Res<ConfigDoc> {
    validate_xml(&xml)?;
    let file = PathBuf::from(&path);
    let backup = {
        let inner = state.0.lock().unwrap();
        backup_file(&repo, &file, &inner.paths.config_dir)?
    };
    std::fs::write(&file, &xml).map_err(|e| format!("{}: {e}", file.display()))?;
    Ok(ConfigDoc {
        path: file.display().to_string(),
        entries: parse_entries(&xml),
        xml,
        backup,
    })
}

#[derive(Serialize)]
pub struct StringsDoc {
    pub path: String,
    pub entries: Vec<ResEntry>,
}

#[tauri::command]
pub fn strings_read(repo: String, locale: String) -> Res<StringsDoc> {
    let dir = if locale.is_empty() { "values".to_string() } else { locale };
    let path = res_dir(&repo).join(&dir).join("strings.xml");
    let xml = std::fs::read_to_string(&path).map_err(|e| format!("{}: {e}", path.display()))?;
    validate_xml(&xml)?;
    let entries = parse_entries(&xml)
        .into_iter()
        .filter(|e| e.kind == "string")
        .collect();
    Ok(StringsDoc { path: path.display().to_string(), entries })
}

/// Change one translation string, e.g. the greeting (`good_morning`) the Home screen shows.
#[tauri::command]
pub fn strings_write(
    state: State<'_, AppState>,
    repo: String,
    locale: String,
    name: String,
    value: String,
) -> Res<ConfigDoc> {
    if !crate::adb::is_package_name(&format!("x.{name}")) || name.contains('/') {
        return Err("Not a resource name.".into());
    }
    let dir = if locale.is_empty() { "values".to_string() } else { locale };
    let path = res_dir(&repo).join(&dir).join("strings.xml");
    let xml = std::fs::read_to_string(&path).map_err(|e| format!("{}: {e}", path.display()))?;
    validate_xml(&xml)?;
    let updated = replace_element(&xml, "string", &name, &[], Some(&value))?;
    validate_xml(&updated)?;
    let backup = {
        let inner = state.0.lock().unwrap();
        backup_file(&repo, &path, &inner.paths.config_dir)?
    };
    std::fs::write(&path, &updated).map_err(|e| format!("{}: {e}", path.display()))?;
    Ok(ConfigDoc {
        path: path.display().to_string(),
        entries: parse_entries(&updated),
        xml: updated,
        backup,
    })
}

#[derive(Serialize)]
pub struct BuildResult {
    pub ok: bool,
    pub apk: String,
    pub size: u64,
    pub log: String,
    pub java: String,
    pub duration: u64,
}

fn gradle_cmd(repo: &Path) -> PathBuf {
    let launcher = repo.join("launcher");
    if cfg!(windows) {
        launcher.join("gradlew.bat")
    } else {
        launcher.join("gradlew")
    }
}

fn java_version() -> String {
    Command::new("java")
        .arg("-version")
        .output()
        .map(|o| {
            let text = format!(
                "{}{}",
                String::from_utf8_lossy(&o.stderr),
                String::from_utf8_lossy(&o.stdout)
            );
            text.lines().next().unwrap_or("").trim().to_string()
        })
        .unwrap_or_default()
}

/// A JDK Android's Gradle plugin can use. Lint crashes on JDK 25 (and anything newer than the
/// plugin knows), so a 17 to 21 build is preferred over whatever `java` happens to be on PATH.
struct Java {
    /// `JAVA_HOME` to hand to Gradle; `None` means "whatever PATH has".
    home: Option<PathBuf>,
    version: String,
    supported: bool,
}

fn jdk_major(home: &Path) -> Option<u32> {
    let java = home.join("bin").join(if cfg!(windows) { "java.exe" } else { "java" });
    if !java.is_file() {
        return None;
    }
    let out = Command::new(&java).arg("-version").output().ok()?;
    let text = format!(
        "{}{}",
        String::from_utf8_lossy(&out.stderr),
        String::from_utf8_lossy(&out.stdout)
    );
    let first = text.lines().next()?;
    let quoted = first.split('"').nth(1)?;
    let major = quoted.split(['.', '-']).next()?.parse::<u32>().ok()?;
    Some(if major == 1 { 8 } else { major })
}

/// Where a JDK usually hides, for people who never set `JAVA_HOME`: the one Gradle downloaded, the
/// distro's, JetBrains' and Android Studio's bundled runtime, and the usual macOS and Windows paths.
fn java_homes() -> Vec<PathBuf> {
    let mut homes = Vec::new();
    for var in ["CLEANYOURTV_JAVA_HOME", "JAVA_HOME"] {
        if let Some(path) = std::env::var_os(var) {
            homes.push(PathBuf::from(path));
        }
    }
    let push_children = |homes: &mut Vec<PathBuf>, base: PathBuf| {
        if let Ok(entries) = std::fs::read_dir(&base) {
            let mut found: Vec<PathBuf> = entries.filter_map(|e| e.ok()).map(|e| e.path()).collect();
            found.sort();
            for path in found {
                homes.push(path.clone());
                // macOS bundles wrap the real home in Contents/Home; Android Studio's jbr is plain.
                homes.push(path.join("Contents/Home"));
            }
        }
    };
    if let Some(home) = std::env::var_os("HOME") {
        let home = PathBuf::from(home);
        push_children(&mut homes, home.join(".gradle/jdks"));
        push_children(&mut homes, home.join(".jdks"));
        homes.push(home.join("android-studio/jbr"));
        push_children(&mut homes, home.join("Library/Java/JavaVirtualMachines"));
        homes.push(PathBuf::from("/Applications/Android Studio.app/Contents/jbr/Contents/Home"));
    }
    push_children(&mut homes, PathBuf::from("/usr/lib/jvm"));
    push_children(&mut homes, PathBuf::from("/usr/java"));
    homes.push(PathBuf::from("/opt/android-studio/jbr"));
    if let Some(local) = std::env::var_os("LOCALAPPDATA") {
        homes.push(PathBuf::from(local).join("Programs/Android Studio/jbr"));
    }
    homes.push(PathBuf::from(r"C:\Program Files\Android\Android Studio\jbr"));
    homes
}

fn find_java() -> Java {
    let mut fallback: Option<(PathBuf, u32)> = None;
    for home in java_homes() {
        let Some(major) = jdk_major(&home) else { continue };
        if (17..=21).contains(&major) {
            let version = java_version_of(&home).unwrap_or_else(|| format!("Java {major}"));
            return Java { home: Some(home), version, supported: true };
        }
        if fallback.is_none() {
            fallback = Some((home, major));
        }
    }
    let on_path = java_version();
    match fallback {
        Some((home, major)) => Java {
            home: Some(home),
            version: if on_path.is_empty() {
                format!("Java {major}")
            } else {
                on_path
            },
            supported: false,
        },
        None => Java { home: None, version: on_path, supported: false },
    }
}

fn java_version_of(home: &Path) -> Option<String> {
    let java = home.join("bin").join(if cfg!(windows) { "java.exe" } else { "java" });
    let out = Command::new(java).arg("-version").output().ok()?;
    let text = format!(
        "{}{}",
        String::from_utf8_lossy(&out.stderr),
        String::from_utf8_lossy(&out.stdout)
    );
    text.lines().next().map(|l| l.trim().to_string())
}

/// Build the launcher APK from a checkout. Needs JDK 17 and Android SDK platform 34 on this computer.
#[tauri::command]
pub async fn launcher_build(_state: State<'_, AppState>, repo: String) -> Res<BuildResult> {
    let repo_path = PathBuf::from(&repo);
    let gradlew = gradle_cmd(&repo_path);
    if !gradlew.is_file() {
        return Err(format!(
            "{} is missing: this folder is not a CleanYourTV checkout.",
            gradlew.display()
        ));
    }
    let java = find_java();
    if java.version.is_empty() && java.home.is_none() {
        return Err(
            "Java was not found. Install JDK 17 (Android Studio bundles one) or set JAVA_HOME, then try again."
                .into(),
        );
    }
    let repo_clone = repo_path.clone();
    tauri::async_runtime::spawn_blocking(move || {
        let started = std::time::Instant::now();
        let mut cmd = Command::new(&gradlew);
        cmd.current_dir(repo_clone.join("launcher"))
            .arg("--no-daemon");
        if let Some(home) = &java.home {
            cmd.env("JAVA_HOME", home);
        }
        if java.supported {
            cmd.arg("assembleRelease");
        } else {
            // Lint (lintVital) needs JDK 17 to 21; on a newer JDK it crashes the build instead of
            // reporting anything. The APK is still built and installed, and the repo's own CI runs
            // the lint gate on the pull request.
            cmd.args(["-x", "lintVitalRelease", "-x", "lintVitalAnalyzeRelease"]);
            cmd.arg("assembleRelease");
        }
        cmd.env("CI", "true");
        #[cfg(windows)]
        {
            use std::os::windows::process::CommandExt;
            cmd.creation_flags(0x0800_0000);
        }
        let out = cmd
            .output()
            .map_err(|e| format!("could not run gradle: {e}"))?;
        let mut log = format!(
            "{}{}",
            String::from_utf8_lossy(&out.stdout),
            String::from_utf8_lossy(&out.stderr)
        );
        if !java.supported {
            log = format!(
                "note: no JDK 17 to 21 was found, so lint was skipped; the APK itself is complete.\n{log}"
            );
        }
        // Keep the tail: gradle's first lines are the daemon handshake and the rest is noise here.
        if log.lines().count() > 120 {
            let lines: Vec<&str> = log.lines().rev().take(120).collect();
            log = lines.into_iter().rev().collect::<Vec<_>>().join("\n");
        }
        let apk = repo_clone.join("launcher/app/build/outputs/apk/release/app-release.apk");
        let size = std::fs::metadata(&apk).map(|m| m.len()).unwrap_or(0);
        Ok(BuildResult {
            ok: out.status.success() && size > 0,
            apk: apk.display().to_string(),
            size,
            log,
            java: match &java.home {
                Some(home) => format!("{} · {}", java.version, home.display()),
                None => java.version.clone(),
            },
            duration: started.elapsed().as_secs(),
        })
    })
    .await
    .map_err(|e| e.to_string())?
}

#[tauri::command]
pub async fn launcher_install(
    state: State<'_, AppState>,
    apk: String,
    set_home: bool,
) -> Res<HomeReport> {
    let path = PathBuf::from(&apk);
    if !path.is_file() {
        return Err(format!("{} is not there any more. Build it again.", path.display()));
    }
    let (adb, target) = session(&state)?;
    on_tv(adb, target, move |adb, target| {
        let sdk = adb
            .shell_text(target, "getprop ro.build.version.sdk", Duration::from_secs(10))
            .ok()
            .and_then(|t| t.trim().parse::<u32>().ok())
            .unwrap_or(0);
        if sdk < MIN_SDK {
            return Err(format!(
                "This TV runs SDK {sdk}; the launcher needs {MIN_SDK} (Android 8.0). The debloat \
                 half still applies, the launcher half does not."
            ));
        }
        let mut steps = Vec::new();
        let out = adb.install(target, &apk, Duration::from_secs(240));
        let text = match out {
            Ok(t) => t,
            Err(e) => {
                // The Android 14 confirmation dialog hangs `adb install`; pm install over the shell
                // is the documented way round it.
                let tmp = format!("/data/local/tmp/cyt-launcher-{}.apk", timestamp());
                adb.push(target, &apk, &tmp, Duration::from_secs(600))?;
                let t = adb
                    .shell_text(target, &format!("pm install -r -d {tmp}"), Duration::from_secs(240))
                    .unwrap_or_else(|e2| format!("{e}\n{e2}"));
                let _ = adb.shell_text(target, &format!("rm -f {tmp}"), Duration::from_secs(10));
                t
            }
        };
        let ok = text.contains("Success");
        steps.push(step("Install the APK", ok, text.trim().to_string()));
        if !ok {
            return Ok(HomeReport {
                steps,
                home: String::new(),
                focused: String::new(),
                ok: false,
                cvte: false,
            });
        }

        let mut home = String::new();
        let mut focused = String::new();
        let mut took = false;
        if set_home {
            std::thread::sleep(Duration::from_millis(600));
            let candidates = home_candidates(adb, target).unwrap_or_default();
            if let Some(component) = candidates
                .iter()
                .find(|c| c.starts_with(&format!("{OUR_LAUNCHER}/")))
            {
                let out = adb.shell_text(
                    target,
                    &format!("cmd package set-home-activity {component}"),
                    Duration::from_secs(20),
                )?;
                steps.push(step(
                    "set-home-activity",
                    out.to_ascii_lowercase().contains("success"),
                    out.trim().to_string(),
                ));
            } else {
                steps.push(step(
                    "set-home-activity",
                    false,
                    "the new launcher does not answer Home; leave the stock launcher on",
                ));
            }
            std::thread::sleep(Duration::from_millis(700));
            home = current_home(adb, target).unwrap_or_default();
            focused = focused_activity(adb, target);
            took = home.starts_with(OUR_LAUNCHER);
            steps.push(step(
                "Home resolves to the clean launcher",
                took,
                if took { home.clone() } else { format!("still {home}") },
            ));
            steps.push(step("Window in front", focused.starts_with(OUR_LAUNCHER), focused.clone()));
        }
        let cvte = cvte_present(adb, target);
        if cvte {
            steps.push(step(
                "CVTE board",
                true,
                "Reboot once and confirm Home and ADB come back before disabling the stock launcher.",
            ));
        }
        Ok(HomeReport { steps, home, focused, ok: took || !set_home, cvte })
    })
    .await
}

/// One theme key, for the friendly form. Kept in step with themes/README.md and the readers in
/// `launcher/app/src/main/java/com/gathin/tvhome/Config.java`.
///
/// `only` marks a key the overlay cannot carry: it lives in `config.xml`, inside the APK, and needs a
/// build. The form greys those out instead of silently sending a key the launcher would ignore.
macro_rules! k {
    ($key:literal, $kind:literal, $group:literal, $label:literal, $help:literal
     $(, default: $default:expr)? $(, min: $min:expr)? $(, max: $max:expr)?
     $(, options: $options:expr)? $(, only: $only:literal)?) => {
        serde_json::json!({
            "key": $key,
            "type": $kind,
            "group": $group,
            "label": $label,
            "help": $help,
            $( "default": $default, )?
            $( "min": $min, )?
            $( "max": $max, )?
            $( "options": $options, )?
            $( "only": $only, )?
        })
    };
}

/// Every key a theme overlay understands, plus the `config.xml`-only ones, so the form can show the whole
/// surface: what changes instantly, and what needs a build.
#[tauri::command]
pub fn theme_keys() -> serde_json::Value {
    use serde_json::Value;
    let keys = vec![
        // ------------------------------------------------------------------ Düzen
        k!("dock_apps", "packages", "Düzen", "Dock uygulamaları",
           "Dock'taki uygulamalar, sırayla. Kurulu olmayan uygulama atlanır.", only: ""),
        k!("fill_dock", "bool", "Düzen", "Boş dock gözlerini doldur",
           "Dock'ta boş yer kalırsa ilk ızgara uygulamalarıyla doldurulur.", default: true),
        k!("columns", "int", "Düzen", "Satır başına kare",
           "Izgaradaki kare sayısı. 5'ten sonra kareler küçülür.", default: 4, min: 1, max: 8),
        k!("rows_below_dock", "int", "Düzen", "Dock altında görünen satır",
           "Dock'un altında kalan ızgara satırı sayısı; sığmazsa kareler küçülür.", default: 0, min: 0, max: 3),
        k!("dock_row", "bool", "Düzen", "Dock ayrı bir satır",
           "Kapalıyken dock uygulamaları tek düz ızgaranın ilk kareleri olur.", default: true),
        k!("dock_title", "bool", "Düzen", "Dock'ta büyük uygulama adı",
           "Dock üstünde odaklanan uygulamanın adı. Kapalıyken yalnızca parlama kalır.", default: true),
        k!("dock_glass", "bool", "Düzen", "Buzlu cam dock",
           "Dock bandı üst barın hapları gibi açık buzlu cam olur.", default: false),
        k!("dock_band", "bool", "Düzen", "Dock bandı ve kenarı",
           "Kapalıyken kareler duvar kağıdının üstünde, bantsız durur.", default: true),
        k!("dock_band_all", "bool", "Düzen", "Tek satırda bant hepsini kaplasın",
           "Tek satır modunda bant yalnızca dock uygulamalarını değil tüm satırı kaplar.", default: false),
        k!("dock_even_padding", "bool", "Düzen", "Dock boşlukları eşit",
           "Bant boşluğu dört yandan kare aralığına eşitlenir.", default: false),
        k!("single_row", "bool", "Düzen", "Tek satır, yana kaydırmalı",
           "Tüm uygulamalar tek satırda; aşağıda ızgara olmaz. dock_row açık olmalı.", default: false),
        k!("top_menu", "bool", "Düzen", "Üstte tek dişli menü",
           "Girişler, Wi-Fi ve Ayarlar tek yuvarlak dişlide toplanır.", default: false),
        k!("launcher_settings_button", "bool", "Düzen", "Ayarlar düğmesi başlatıcı ayarlarını açsın",
           "Üst bardaki ayarlar düğmesi TV ayarları yerine başlatıcının kendi ayarlarını açar.", default: false),
        k!("reorder_button", "bool", "Düzen", "Sırala düğmesi",
           "Üst bara iki oklu sıralama düğmesi ekler; zaten ayarların uzun basış menüsünde var.", default: false),
        k!("icon_only_buttons", "bool", "Düzen", "Yalnız ikon düğmeler",
           "Wi-Fi ve Ayarlar yuvarlak ikon olur; Girişler etiketini korur.", default: false),
        k!("icon_only_inputs", "bool", "Düzen", "Girişler de yalnız ikon",
           "Girişler hapı da yuvarlak ikon düğme olur.", default: false),
        k!("always_show_names", "bool", "Düzen", "Her karede ad",
           "Odaklanmayan adlar soluk görünür; kapalıyken yalnız odaklananın adı yazar.", default: false),
        k!("uniform_tiles", "bool", "Düzen", "Tek tip kareler",
           "Kareler uygulamanın kendi bandı yerine ikonundan ve renginden çizilir.", default: false),
        k!("hidden_apps", "packages", "Düzen", "Home'da gizli uygulamalar",
           "Bu paketler kare olarak hiç gösterilmez."),
        k!("top_bar_apps", "packages", "Düzen", "Üst barda uygulama düğmeleri",
           "Üst barda yuvarlak logo düğmesi olarak durur ve ızgaradan çıkar."),
        k!("fade_rows", "bool", "Düzen", "Satırlar yumuşak kırpılsın",
           "Izgara saatin altına kayarken sert bir çizgi yerine solarak kaybolur.", default: false),
        k!("show_home_settings_shortcut", "bool", "Düzen", "Varsayılan Home karesi",
           "TV'de Home seçici varsa isteğe bağlı kareyi görünür tutar.", default: false),

        // ------------------------------------------------------------------ Üst bar
        k!("show_network_pill", "bool", "Üst bar", "Ağ hapı",
           "Wi-Fi veya Ethernet hapı. Kapalıyken gizlenir.", default: true),
        k!("show_inputs_pill", "bool", "Üst bar", "Girişler hapı",
           "TV'de giriş varsa görünen Girişler hapı. Kapalıyken kumandanın Giriş tuşu değişmez.", default: true),
        k!("wifi_status_color", "bool", "Üst bar", "Wi-Fi ikonu renkli",
           "Çevrimiçiyken yeşil, değilken beyaz ikon.", default: false),
        k!("wifi_status_warnings", "bool", "Üst bar", "Wi-Fi uyarı renkleri",
           "İnternet yokken turuncu, çevrimdışıyken kırmızı ikon.", default: false),
        k!("wifi_label_width", "int", "Üst bar", "Ağ adı genişliği",
           "Yalnız ikon düğmelerde ağ adını bu genişliğe kadar tutar; 0 yalnız ikon bırakır.", default: 0, min: 0, max: 300),
        k!("library_button", "bool", "Üst bar", "Uygulama kitaplığı düğmesi",
           "Kitaplığı üst barda yuvarlak düğme olarak açar; app_library de açık olmalı.", default: false),
        k!("show_library_pill", "bool", "Üst bar", "Kitaplık hapı",
           "Son satırdan sonra Kitaplık hapı. Kapalıyken üst bar düğmesi tek yol kalır.", default: true),
        k!("vpn_pill", "bool", "Üst bar", "VPN hapı",
           "VPN ayrı hap olur: açıkken yeşil kilit, kapalıyken kırmızı “VPN kapalı”.", default: false),
        k!("vpn_app", "string", "Üst bar", "VPN uygulaması",
           "Ağ hapının açtığı VPN uygulamasının paketi, ör. com.example.vpn."),
        k!("module_cards", "bool", "Üst bar", "Modül kartları",
           "Kart duyuran eklentiler için “Göster” sorusu sorulur; kapalıyken hiç aranmaz.", default: true),

        // ------------------------------------------------------------------ Saat ve metin
        k!("greeting_name", "string", "Saat ve metin", "Selamlamadan sonra isim",
           "Ör. “Kralım” yazarsanız GÜNAYDIN, KRALIM olur. Boş bırakılırsa yalnız selamlama."),
        k!("show_greeting", "bool", "Saat ve metin", "Selamlama görünsün",
           "Saatin üstündeki Günaydın/İyi akşamlar satırı.", default: true),
        k!("locale", "string", "Saat ve metin", "Dil",
           "tr, de, pt-BR gibi bir dil etiketi; boş bırakılırsa TV'nin dili izlenir."),
        k!("date_below_clock", "bool", "Saat ve metin", "Tarih saatin altında",
           "Tarih (Şemsi ve ay takvimi satırlarıyla) saatin yanı yerine altına iner.", default: false),
        k!("header_white", "bool", "Saat ve metin", "Tarih tam beyaz",
           "Tarih ve çizgi saatin tam beyazında, soluk yerine.", default: false),
        k!("shamsi_date", "bool", "Saat ve metin", "Hicri-Şemsi tarih",
           "İran ve Afganistan'da kullanılan tarih, ör. 27 Mehr 1405.", default: false),
        k!("lunar_date", "bool", "Saat ve metin", "Ay-güneş takvimi",
           "Vietnam, Çin ve Kore takvimi; tarihin altında görünür.", default: false),
        k!("lunar_utc_offset", "int", "Saat ve metin", "Ay takvimi saat dilimi",
           "UTC'nin kaç saat doğusu: Vietnam 7, Çin 8, Kore 9. Boş bırakılırsa dile göre seçilir.", min: 0, max: 14),
        k!("show_stats", "bool", "Saat ve metin", "Sistem satırı",
           "Saatin altında CPU, boş bellek ve boş depolama.", default: false),
        k!("home_idle_clock_minutes", "int", "Saat ve metin", "Saat ekran koruyucu (dakika)",
           "Home bu kadar dakika boş kalırsa saat ekran koruyucusu başlar; 0 kapalı.", default: 0, min: 0, max: 1440),
        k!("clock_wallpaper", "bool", "Saat ve metin", "Saat duvar kağıdı üstünde",
           "Ekran koruyucu siyah yerine Home'un duvar kağıdını gösterir.", default: false),
        k!("clock_wallpaper_dim", "int", "Saat ve metin", "Saat duvar kağıdı karartması",
           "Duvar kağıdının saat arkasında ne kadar kararacağı, yüzde.", default: 50, min: 0, max: 90),
        k!("clock_glow", "bool", "Saat ve metin", "Saat parlaması",
           "Ekran koruyucudaki renkli yavaş parlama; OLED için kapatılabilir.", default: true),

        // ------------------------------------------------------------------ Görünüm
        k!("background_color", "color", "Görünüm", "Arka plan rengi",
           "Her şeyin arkasındaki renk.", default: "#000000"),
        k!("wallpaper", "bool", "Görünüm", "Duvar kağıdı",
           "Temanın resmini göster; kapalıyken ne tek resim ne koleksiyon çizilir.", default: true),
        k!("wallpaper_dim", "int", "Görünüm", "Duvar kağıdı karartması",
           "Duvar kağıdının üstüne siyah katman, yüzde.", default: 0, min: 0, max: 90),
        k!("wallpaper_rotation_minutes", "int", "Görünüm", "Duvar kağıdı döngüsü (dakika)",
           "Temanın wallpapers/ klasörünü bu aralıkla değiştirir; 0 tek resim demek.", default: 0, min: 0, max: 1440),
        k!("wallpaper_order", "select", "Görünüm", "Duvar kağıdı sırası",
           "Koleksiyonun nasıl ilerleyeceği.", default: "shuffle", options: ["shuffle", "sequential"]),
        k!("wallpaper_captions", "bool", "Görünüm", "Duvar kağıdı adları",
           "Dönen koleksiyonda resmin adını üst sağdaki düğmelerin altında gösterir.", default: false),
        k!("wallpaper_true_color", "bool", "Görünüm", "Tam renk duvar kağıdı",
           "32 bit çözümleme (1080p'de 8 MB); koyu geçişlerde bantlanmayı önler, 2 GB ve üstü TV'ler için.", default: false),
        k!("wallpaper_moods", "json", "Görünüm", "Günün saatine göre duvar kağıtları",
           "JSON nesnesi: morning, evening ve night dizileri. Boş bırakılırsa hepsi döner."),
        k!("dock_opacity", "int", "Görünüm", "Dock saydamlığı",
           "Dock bandının opaklığı, yüzde; duvar kağıdı altından görünür.", default: 100, min: 30, max: 100),
        k!("sky_intensity", "int", "Görünüm", "Gün ışığı fonu",
           "Saate göre koyu bir gradyan; gün doğumu ve batımında sıcak bir ufuk. 0 kapalı.", default: 0, min: 0, max: 100),
        k!("ambient_glow", "bool", "Görünüm", "Dock arkası parlama",
           "Dock'un arkasında odaklanan uygulamanın renginde parlama.", default: true),
        k!("app_glow", "bool", "Görünüm", "Her karede uygulama rengi",
           "Odak vurgusu her karede uygulamanın renginde parlar.", default: false),
        k!("glow_style", "select", "Görünüm", "Parlama biçimi",
           "radial yumuşak bir parlama, horizon üst kenardan aşağı yayılan renk.", default: "radial", options: ["radial", "horizon"]),
        k!("glass_style", "select", "Görünüm", "Odak vurgusu",
           "glass buzlu levha, rim yalnız ince çerçeve, halo çerçeve ve dış parlama.", default: "glass", options: ["glass", "rim", "halo"]),
        k!("focus_overshoot", "bool", "Görünüm", "Odak yaylanması",
           "Vurgu hedefini aşıp yerine oturur; kapalıyken düz kayar.", default: true),
        k!("reduced_motion", "bool", "Görünüm", "Azaltılmış hareket",
           "Zayıf GPU'lar için sakin hareket, 90 ms'lik animasyonlar.", default: false),
        k!("animations", "bool", "Görünüm", "Temel animasyonlar",
           "Odak, cam ve kare giriş hareketleri.", default: true),
        k!("advanced_effects", "bool", "Görünüm", "Gelişmiş efektler",
           "Kalkan kare, ikon yakınlaşması, uygulama açılışı.", default: false),
        k!("cinematic_home", "bool", "Görünüm", "Sinematik üst raf",
           "Odaklanan dock uygulamasının görseli ekranın üstünü kaplar.", default: false),
        k!("cinematic_dots", "bool", "Görünüm", "Sinematik noktalar",
           "Sinematik raf açıkken dock konum noktaları.", default: true),
        k!("cinematic_white_focus", "bool", "Görünüm", "Dock'ta beyaz odak",
           "Sinematik rafta dock vurgusu uygulama rengi yerine beyaz kalır.", default: false),
        k!("cinematic_scrim", "int", "Görünüm", "Sinematik gölge",
           "Raf adının arkasındaki koyu gölgenin gücü, yüzde.", default: 100, min: 0, max: 100),
        k!("cinematic_art", "int", "Görünüm", "Sinematik görsel opaklığı",
           "Odaklanan uygulamanın yumuşatılmış görselinin opaklığı, yüzde.", default: 50, min: 0, max: 100),

        // ------------------------------------------------------------------ Davranış
        k!("boot_to_live_tv", "bool", "Davranış", "Açılışta canlı TV",
           "TV açıldıktan sonraki ilk Home'da canlı TV uygulamasını açar; her açılışta bir kez.", default: false),
        k!("live_tv_package", "string", "Davranış", "Canlı TV paketi",
           "TV'nin canlı yayın uygulaması, ör. com.mediatek.wwtv.tvcenter."),
        k!("live_tv_action", "string", "Davranış", "Canlı TV action",
           "Uygulamanın leanback girişi yoksa gönderilecek action."),
        k!("live_tv_input_id", "string", "Davranış", "Canlı TV tuner kimliği",
           "Girişler menüsündeki canlı TV satırı için tam tuner kimliği; boşsa otomatik seçilir."),
        k!("open_on_start_choice", "bool", "Davranış", "Açılışta uygulama seçimi",
           "Başlatıcı ayarlarına “Açılışta aç” satırı ekler: her açılışta seçilen uygulama açılır.", default: false),
        k!("open_on_start_network_wait", "int", "Davranış", "Ağ bekleme (saniye)",
           "Açılışta uygulamayı açmadan önce internet bağlantısını bu kadar saniye bekler.", default: 0, min: 0, max: 600),
        k!("power_on_home", "bool", "Davranış", "Açılışta Home'a dön",
           "Firmware kendi kendine canlı TV açıyorsa, ilk tuş basışına kadar onu Home'a geri gönderir.", default: false),
        k!("power_on_home_always", "bool", "Davranış", "Her açılışta Home",
           "Firmware ne açarsa açsın (son HDMI, canlı TV, uygulama) Home gelir.", default: false),
        k!("home_long_press_home", "bool", "Davranış", "Uzun Home basışı da Home",
           "Kumandanın Home tuşu basılı tutulsa da Home açar; key_filter açık olmalı.", default: false),
        k!("app_library", "bool", "Davranış", "Uygulama kitaplığı",
           "Son satırdan sonra tüm kurulu uygulamaları listeleyen kitaplık.", default: false),
        k!("app_library_selection", "bool", "Davranış", "Kitaplıkta seçim",
           "Kitaplıkta OK Home işaretini değiştirir; işaretliler önce gelir.", default: false),
        k!("library_ok_opens", "bool", "Davranış", "Kitaplıkta OK açar",
           "Kitaplıkta OK her uygulamayı açar, yalnız Home'dakileri değil.", default: false),
        k!("library_apps", "packages", "Davranış", "Kitaplığa önceden eklenenler",
           "Home'da eklenmiş gibi görünen, kitaplıktan çıkarılabilen paketler."),
        k!("library_excluded_apps", "packages", "Davranış", "Kitaplıkta hiç önerilmeyenler",
           "Kitaplık kataloğunun asla önermeyeceği paketler."),
        k!("tile_menu", "bool", "Davranış", "Kare menüsü",
           "Karede OK'a basılı tutunca Taşı / Home'dan kaldır / Uygulama bilgisi menüsü.", default: false),
        k!("handle_view_inputs", "bool", "Davranış", "Sistem giriş isteğine cevap",
           "Kumandanın Giriş tuşu VIEW_INPUTS gönderdiğinde Girişler menüsünü açar.", default: false),
        k!("inputs_connected_only", "bool", "Davranış", "Yalnız bağlı girişler",
           "Boş HDMI/AV girişleri “Diğer girişler” satırının arkasına katlanır.", default: false),
        k!("launch_actions", "list", "Davranış", "Kare action'ları",
           "\"paket=action\" satırları: kare uygulamanın başlangıcı yerine o ekranı açar."),
        k!("app_labels", "list", "Davranış", "Kare adları",
           "\"paket=Ad\" satırları: karenin ve dock üstünün adını değiştirir."),
        k!("app_banners", "list", "Davranış", "Kare görselleri",
           "\"paket=çizim\" satırları: launcher içindeki banner_* çizimini kullanır."),
        k!("input_action", "string", "Davranış", "Giriş action'ı",
           "Kanal URI'sini hiçbir şey açmazsa gönderilecek action, ör. com.xgimi.action.hdmiPlayer."),
        k!("input_uri_scheme", "string", "Davranış", "Giriş URI şeması",
           "content:// yerine kullanılacak şema, ör. YaOS için live-tv."),
        k!("input_sources", "list", "Davranış", "Satıcı giriş numaraları",
           "\"HW<n>=<numara>\" satırları; \"HW<n>=-\" olmayan portu menüden çıkarır."),
        k!("coocaa_sources", "bool", "Davranış", "Coocaa kaynakları",
           "Coocaa OS (Skyworth, Çin) kaynaklarını com.skyworth.tv üzerinden listeler.", default: false),
        k!("source_panel_service", "string", "Davranış", "Kaynak paneli servisi",
           "TvInputManager olmayan TV'lerde giriş menüsünün açacağı panel bileşeni."),
        k!("source_panel_action", "string", "Davranış", "Kaynak paneli action",
           "Aynı panelin action biçimi."),
        k!("source_panel_extra", "string", "Davranış", "Kaynak paneli ekstrası",
           "\"ad=değer\" biçiminde tek int ekstra, ör. Coocaa: control_center_command=100."),
        k!("stock_home_redirect", "string", "Davranış", "Stok başlatıcı yönlendirmesi",
           "Firmware stok başlatıcıyı geri açıyorsa (XGIMI) Home'a döner; paket adı yazılır."),

        // ------------------------------------------------------------------ Kumanda tuşları
        k!("key_filter", "bool", "Kumanda tuşları", "Tuş süzgeci",
           "Tuş yardımcıları tuşları süzsün. Kapalıyken aşağıdaki kumanda tuşları çalışmaz.", default: true),
        k!("gamepad_passthrough", "bool", "Kumanda tuşları", "Oyun kolu geçişi",
           "Oyun kolu bağlıyken süzgeç durur, oyunlar tuşları görür.", default: false),
        k!("consume_volume_keys", "bool", "Kumanda tuşları", "Sesi yardımcı yönetsin",
           "Ses tuşlarını yardımcı yutar ve kendi tekrarlar; çift adım sorununda kullanılır.", default: false),
        k!("app_keys", "list", "Kumanda tuşları", "Kumanda tuş → uygulama",
           "\"190:paket\", \"scan:490:paket\", \"hold:...\" gibi satırlar; action sözcükleri de olur (mute, home)."),
        k!("settings_key_keycodes", "ints", "Kumanda tuşları", "Ayarlar tuş kodları",
           "Ayarlar'ı açan kumanda tuşlarının Android tuş kodları, ör. TCL: 83."),
        k!("settings_key_panel", "bool", "Kumanda tuşları", "Ayarlar hızlı paneli",
           "O tuşlar TV ayarları yerine küçük bir hızlı ayarlar paneli açar.", default: false),
        k!("input_key_keycodes", "ints", "Kumanda tuşları", "Giriş tuş kodları",
           "Giriş değiştiriciyi açan ek tuş kodları."),
        k!("restore_key_services", "bool", "Kumanda tuşları", "Tuş servislerini geri getir",
           "Başlatıcı zorla durdurulduktan sonra Home, tuş yardımcılarını yeniden etkinleştirir.", default: false),

        // ------------------------------------------------------------------ Sezon ve ekstralar
        k!("festival", "select", "Sezon ve ekstralar", "Festival görünümü",
           "dussehra veya diwali: ağ hapı diya, Girişler çanı, Ayarlar kadayıfı olur.", options: ["", "dussehra", "diwali"]),
        k!("festival_until", "string", "Sezon ve ekstralar", "Festival bitiş tarihi",
           "YYYY-AA-GG; bu tarihten sonra festival kendiliğinden kapanır."),
        k!("greeting", "string", "Sezon ve ekstralar", "Festival selamlaması",
           "Festival sırasında günün selamlamasının yerine geçer. Kalıcı metin için Selamlama sekmesi."),
        k!("accent_color", "color", "Sezon ve ekstralar", "Festival vurgu rengi",
           "Festival sırasında odak parlamasının rengi; boşsa diya altını."),
        k!("race_calendar", "list", "Sezon ve ekstralar", "F1 takvimi",
           "Yarış hafta sonu paneli; tools/race_calendar.py ile yazılır, başlatıcı internete çıkmaz."),
        k!("game_day", "string", "Sezon ve ekstralar", "Maç günü eklentisi",
           "F1 panelinin yerine takımın sonraki maçını yayınlayan eklentinin paketi."),

        // ------------------------------------------------------------------ Yalnızca config.xml
        k!("icon_tiles", "packages", "Yalnızca config.xml", "İkonla çizilen kareler",
           "Bandı kötü kırpılan uygulamalar için ikon + ad. APK'ya derlenir.", only: "config.xml"),
        k!("settings_fallback_components", "list", "Yalnızca config.xml", "Yedek ayarlar ekranları",
           "Standart Ayarlar action'ını kimse açmazsa sırayla denenen package/activity'ler.", only: "config.xml"),
        k!("settings_key_scancodes", "ints", "Yalnızca config.xml", "Ayarlar tarama kodları",
           "Ayarlar tuşunun Linux tarama kodları. APK'ya derlenir.", only: "config.xml"),
        k!("latitude", "string", "Yalnızca config.xml", "Enlem",
           "Gün ışığı fonunun gerçek güneşe uyması için ondalık derece, ör. 41.01.", only: "config.xml"),
        k!("longitude", "string", "Yalnızca config.xml", "Boylam",
           "Aynı amaçla boylam, ör. 28.98.", only: "config.xml"),
    ];
    Value::Array(keys)
}

#[cfg(test)]
mod schema_tests {
    use super::*;

    fn keys() -> Vec<serde_json::Value> {
        match theme_keys() {
            serde_json::Value::Array(items) => items,
            other => panic!("theme_keys is not an array: {other}"),
        }
    }

    #[test]
    fn every_key_is_usable() {
        let keys = keys();
        assert!(keys.len() > 80, "the form should cover the whole surface");
        let allowed = ["bool", "int", "string", "color", "select", "packages", "list", "ints", "json"];
        let mut seen = std::collections::HashSet::new();
        for key in &keys {
            let name = key["key"].as_str().expect("a key name");
            assert!(seen.insert(name), "{name} is listed twice");
            let kind = key["type"].as_str().expect("a type");
            assert!(allowed.contains(&kind), "{name} has an unknown type {kind}");
            assert!(!key["group"].as_str().unwrap_or("").is_empty(), "{name} has no group");
            assert!(!key["label"].as_str().unwrap_or("").is_empty(), "{name} has no label");
            assert!(!key["help"].as_str().unwrap_or("").is_empty(), "{name} has no help");
            if let (Some(min), Some(max)) = (key["min"].as_i64(), key["max"].as_i64()) {
                assert!(min <= max, "{name} has min above max");
            }
            if kind == "select" {
                assert!(key["options"].as_array().map(|o| !o.is_empty()).unwrap_or(false), "{name} has no options");
            }
            if let Some(default) = key["default"].as_str() {
                if kind == "select" {
                    let options = key["options"].as_array().unwrap();
                    assert!(
                        options.iter().any(|o| o.as_str() == Some(default)),
                        "{name} defaults to a value it does not offer"
                    );
                }
            }
        }
    }

    #[test]
    fn the_keys_we_use_are_there() {
        let keys = keys();
        let find = |name: &str| keys.iter().find(|k| k["key"] == name).cloned();
        assert_eq!(find("dock_apps").unwrap()["type"], "packages");
        assert_eq!(find("greeting_name").unwrap()["type"], "string");
        assert_eq!(find("columns").unwrap()["default"], 4);
        assert_eq!(find("icon_tiles").unwrap()["only"], "config.xml");
    }
}
