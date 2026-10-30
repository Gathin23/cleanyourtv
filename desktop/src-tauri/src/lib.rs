//! CleanYourTV Desktop: the app shell, state wiring and the command surface.
#![recursion_limit = "256"]

pub mod adb;
pub mod commands;
pub mod rules;
pub mod state;

use state::{AppPaths, AppState};
use tauri::Manager;

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            let config_dir = app
                .path()
                .app_config_dir()
                .map_err(|e| format!("could not find the app config folder: {e}"))?;
            let home_dir = app
                .path()
                .home_dir()
                .map_err(|e| format!("could not find the home folder: {e}"))?;
            std::fs::create_dir_all(&config_dir).ok();
            app.manage(AppState::new(AppPaths { config_dir, home_dir }));
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::core::adb_status,
            commands::core::adb_set_path,
            commands::core::device_discover,
            commands::core::device_connection,
            commands::core::device_connect,
            commands::core::device_pair,
            commands::core::device_disconnect,
            commands::core::device_info,
            commands::core::device_screenshot,
            commands::core::device_key,
            commands::core::device_tap,
            commands::core::device_text,
            commands::core::device_sync,
            commands::core::device_reboot,
            commands::core::port_check,
            commands::core::adb_close,
            commands::core::private_dns_get,
            commands::core::private_dns_set,
            commands::core::settings_get,
            commands::core::settings_save,
            commands::core::repo_detect,
            commands::core::examples_list,
            commands::core::restore_scripts,
            commands::core::import_list,
            commands::core::save_text,
            commands::packages::packages_list,
            commands::packages::package_details,
            commands::packages::package_set_enabled,
            commands::packages::package_launch,
            commands::packages::package_force_stop,
            commands::packages::batch_disable,
            commands::packages::package_pull_apk,
            commands::backup::backup_create,
            commands::backup::backup_state,
            commands::backup::backup_list,
            commands::backup::backup_restore,
            commands::files::fs_list,
            commands::files::fs_read,
            commands::files::fs_write,
            commands::files::fs_pull,
            commands::files::fs_push,
            commands::files::fs_mkdir,
            commands::files::fs_delete,
            commands::files::fs_roots,
            commands::files::apk_install,
            commands::files::fs_pull_to,
            commands::files::shots_list,
            commands::files::fs_stat,
            commands::files::local_now,
            commands::launcher::launcher_status,
            commands::launcher::theme_push,
            commands::launcher::theme_reset,
            commands::launcher::theme_keys,
            commands::tv::tv_settings_specs,
            commands::tv::tv_settings_read,
            commands::tv::tv_setting_write,
            commands::launcher::launcher_set_home,
            commands::launcher::config_layout,
            commands::launcher::config_read,
            commands::launcher::config_write,
            commands::launcher::config_write_raw,
            commands::launcher::strings_read,
            commands::launcher::strings_write,
            commands::launcher::launcher_build,
            commands::launcher::launcher_install,
        ])
        .run(tauri::generate_context!())
        .expect("CleanYourTV Desktop could not start");
}
