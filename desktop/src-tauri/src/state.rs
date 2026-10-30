use crate::adb::Adb;
use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use std::sync::Mutex;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct Settings {
    pub adb_path: Option<String>,
    pub repo_path: Option<String>,
    pub backup_dir: Option<String>,
    pub last_host: Option<String>,
    pub last_port: u16,
    pub ui_locale: Option<String>,
    /// `system`, `dark` or `light`; unset follows the operating system.
    pub ui_theme: Option<String>,
    pub confirm_before_reboot: bool,
}

impl Default for Settings {
    fn default() -> Self {
        Settings {
            adb_path: None,
            repo_path: None,
            backup_dir: None,
            last_host: None,
            last_port: 5555,
            ui_locale: None,
            ui_theme: None,
            confirm_before_reboot: true,
        }
    }
}

pub struct AppPaths {
    pub config_dir: PathBuf,
    pub home_dir: PathBuf,
}

pub struct Inner {
    pub adb: Option<Adb>,
    pub target: Option<String>,
    pub host: Option<String>,
    pub port: u16,
    pub settings: Settings,
    pub paths: AppPaths,
    /// Set once a backup has been written in this session, so the debloat screen can prove it.
    pub backed_up: bool,
}

pub struct AppState(pub Mutex<Inner>);

impl AppState {
    pub fn new(paths: AppPaths) -> Self {
        let settings = load_settings(&paths.config_dir);
        let adb = Adb::locate(settings.adb_path.as_deref());
        let (host, port) = (settings.last_host.clone(), settings.last_port);
        AppState(Mutex::new(Inner {
            adb,
            target: None,
            host,
            port,
            settings,
            paths,
            backed_up: false,
        }))
    }
}

pub fn settings_file(config_dir: &PathBuf) -> PathBuf {
    config_dir.join("settings.json")
}

pub fn load_settings(config_dir: &PathBuf) -> Settings {
    let path = settings_file(config_dir);
    std::fs::read_to_string(path)
        .ok()
        .and_then(|text| serde_json::from_str(&text).ok())
        .unwrap_or_default()
}

pub fn save_settings(inner: &Inner) -> Result<(), String> {
    let path = settings_file(&inner.paths.config_dir);
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
    }
    let text = serde_json::to_string_pretty(&inner.settings).map_err(|e| e.to_string())?;
    std::fs::write(&path, text).map_err(|e| format!("{}: {e}", path.display()))
}
