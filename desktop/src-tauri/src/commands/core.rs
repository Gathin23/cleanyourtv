//! Device commands: find adb, connect, pair, read the set, screenshots, keys, reboot, close ADB.

use super::{adb_only, current_home, focused_activity, home_candidates, on_tv, session, timestamp, Res};
use crate::adb::{Adb, MdnsService};
use crate::state::{save_settings, AppState, Settings};
use base64::{engine::general_purpose::STANDARD as B64, Engine};
use serde::Serialize;
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};
use tauri::{Emitter, State};

#[derive(Serialize)]
pub struct AdbStatus {
    pub found: bool,
    pub path: String,
    pub version: String,
    pub source: String,
    pub candidates: Vec<String>,
}

fn status_of(adb: Option<&Adb>) -> AdbStatus {
    AdbStatus {
        found: adb.is_some(),
        path: adb.map(|a| a.path.display().to_string()).unwrap_or_default(),
        version: adb.map(|a| a.version.clone()).unwrap_or_default(),
        source: adb.map(|a| a.source.to_string()).unwrap_or_default(),
        candidates: crate::adb::search_paths()
            .into_iter()
            .filter(|p| p.is_file())
            .map(|p| p.display().to_string())
            .collect(),
    }
}

#[tauri::command]
pub fn adb_status(state: State<'_, AppState>) -> AdbStatus {
    let inner = state.0.lock().unwrap();
    status_of(inner.adb.as_ref())
}

#[tauri::command]
pub fn adb_set_path(state: State<'_, AppState>, path: String) -> Res<AdbStatus> {
    let found = Adb::locate(Some(&path))
        .ok_or("That file is not a working adb binary. Point at adb (adb.exe on Windows).")?;
    let status = status_of(Some(&found));
    let mut inner = state.0.lock().unwrap();
    inner.adb = Some(found);
    inner.settings.adb_path = Some(path);
    save_settings(&inner)?;
    Ok(status)
}

#[derive(Serialize)]
pub struct ScanResult {
    pub mdns: Vec<MdnsService>,
    pub devices: Vec<crate::adb::DeviceLine>,
    pub note: String,
}

#[tauri::command]
pub async fn device_discover(state: State<'_, AppState>) -> Res<ScanResult> {
    let adb = adb_only(&state)?;
    on_tv(adb, String::new(), |adb, _| {
        let mdns = adb.mdns_services().unwrap_or_default();
        let devices = adb.devices().unwrap_or_default();
        let note = if mdns.is_empty() {
            "No Wireless debugging service answered. On the TV, open Developer options and turn on \
             Wireless debugging, then scan again. A TV connected over USB shows up here too."
                .to_string()
        } else {
            format!("{} Wireless debugging service(s) found.", mdns.len())
        };
        Ok(ScanResult { mdns, devices, note })
    })
    .await
}

#[derive(Serialize)]
pub struct Connection {
    pub connected: bool,
    pub target: String,
    pub host: String,
    pub port: u16,
}

fn connection_of(inner: &crate::state::Inner) -> Connection {
    Connection {
        connected: inner.target.is_some(),
        target: inner.target.clone().unwrap_or_default(),
        host: inner.host.clone().unwrap_or_default(),
        port: inner.port,
    }
}

#[tauri::command]
pub fn device_connection(state: State<'_, AppState>) -> Connection {
    connection_of(&state.0.lock().unwrap())
}

#[tauri::command]
pub async fn device_connect(
    state: State<'_, AppState>,
    host: String,
    port: u16,
) -> Res<Connection> {
    let host = host.trim().to_string();
    if host.is_empty() {
        return Err("Type the TV's IP address first.".into());
    }
    let adb = adb_only(&state)?;
    let (h, p) = (host.clone(), port);
    let message = on_tv(adb.clone(), String::new(), move |adb, _| adb.connect(&h, p)).await?;
    let target = format!("{host}:{port}");

    // A connect can say "connected" while the TV still shows the authorisation dialog. Prove the
    // link with a real shell call before the app calls itself connected.
    let probe = adb.shell_text(&target, "getprop ro.build.version.sdk", Duration::from_secs(15));
    match probe {
        Ok(_) => {}
        Err(e) => return Err(format!("{message}\n{e}")),
    }
    let mut inner = state.0.lock().unwrap();
    inner.target = Some(target.clone());
    inner.host = Some(host.clone());
    inner.port = port;
    inner.settings.last_host = Some(host);
    inner.settings.last_port = port;
    save_settings(&inner)?;
    Ok(connection_of(&inner))
}

#[tauri::command]
pub async fn device_pair(
    state: State<'_, AppState>,
    host: String,
    port: u16,
    code: String,
) -> Res<String> {
    let adb = adb_only(&state)?;
    let (h, c) = (host.trim().to_string(), code.trim().to_string());
    if h.is_empty() || c.is_empty() {
        return Err("The pairing needs the TV's IP, the pairing port and the six-digit code.".into());
    }
    on_tv(adb, String::new(), move |adb, _| adb.pair(&h, port, &c)).await
}

#[tauri::command]
pub fn device_disconnect(state: State<'_, AppState>) -> Res<()> {
    let target = {
        let mut inner = state.0.lock().unwrap();
        let target = inner.target.take();
        inner.host = None;
        target
    };
    if let Some(target) = target {
        if let Ok(adb) = adb_only(&state) {
            let _ = adb.disconnect(Some(&target));
        }
    }
    Ok(())
}

#[derive(Serialize, Default)]
pub struct DeviceInfo {
    pub model: String,
    pub manufacturer: String,
    pub brand: String,
    pub device: String,
    pub board: String,
    pub android: String,
    pub sdk: u32,
    pub build_id: String,
    pub fingerprint: String,
    pub serial: String,
    pub abi: String,
    pub uptime: String,
    pub storage_free: String,
    pub storage_total: String,
    pub screen: String,
    pub density: String,
    pub adb_secure: bool,
    pub adb_enabled: bool,
    pub wireless_debugging: Option<bool>,
    pub developer_options: bool,
    pub home: String,
    pub home_candidates: Vec<String>,
    pub focused: String,
    pub launcher_installed: bool,
    pub launcher_version: String,
    pub transports: Vec<String>,
    pub usb_host_connected: Option<bool>,
    pub private_dns_mode: String,
    pub private_dns_specifier: String,
}

fn props(adb: &Adb, target: &str) -> HashMap<String, String> {
    let mut map = HashMap::new();
    if let Ok(text) = adb.shell_text(target, "getprop", Duration::from_secs(15)) {
        for line in text.lines() {
            let line = line.trim();
            let Some(rest) = line.strip_prefix('[') else { continue };
            let Some((key, value)) = rest.split_once("]: [") else { continue };
            let value = value.trim_end_matches(']');
            map.insert(key.to_string(), value.to_string());
        }
    }
    map
}

fn prop<'a>(map: &'a HashMap<String, String>, key: &str) -> &'a str {
    map.get(key).map(String::as_str).unwrap_or("")
}

fn setting(adb: &Adb, target: &str, namespace: &str, key: &str) -> String {
    adb.shell_text(
        target,
        &format!("settings get {namespace} {key}"),
        Duration::from_secs(10),
    )
    .map(|t| t.trim().to_string())
    .unwrap_or_default()
}

#[tauri::command]
pub async fn device_info(state: State<'_, AppState>) -> Res<DeviceInfo> {
    let (adb, target) = session(&state)?;
    on_tv(adb, target, |adb, target| {
        let p = props(adb, target);
        let sdk = prop(&p, "ro.build.version.sdk").parse().unwrap_or(0);
        let mut info = DeviceInfo {
            model: prop(&p, "ro.product.model").to_string(),
            manufacturer: prop(&p, "ro.product.manufacturer").to_string(),
            brand: prop(&p, "ro.product.brand").to_string(),
            device: prop(&p, "ro.product.device").to_string(),
            board: prop(&p, "ro.product.board").to_string(),
            android: prop(&p, "ro.build.version.release").to_string(),
            sdk,
            build_id: prop(&p, "ro.build.display.id").to_string(),
            fingerprint: prop(&p, "ro.build.fingerprint").to_string(),
            serial: prop(&p, "ro.serialno").to_string(),
            abi: prop(&p, "ro.product.cpu.abi").to_string(),
            adb_secure: prop(&p, "ro.adb.secure") != "0" && !prop(&p, "ro.adb.secure").is_empty(),
            ..Default::default()
        };
        if info.model.is_empty() {
            info.model = format!("{} {}", info.brand, info.device).trim().to_string();
        }

        info.uptime = adb
            .shell_text(target, "cat /proc/uptime", Duration::from_secs(8))
            .ok()
            .and_then(|t| t.split_whitespace().next().map(str::to_string))
            .and_then(|s| s.parse::<f64>().ok())
            .map(|s| {
                let d = (s / 86400.0) as u64;
                let h = ((s % 86400.0) / 3600.0) as u64;
                let m = ((s % 3600.0) / 60.0) as u64;
                if d > 0 {
                    format!("{d}d {h}h")
                } else {
                    format!("{h}h {m}m")
                }
            })
            .unwrap_or_default();

        if let Ok(df) = adb.shell_text(target, "df -k /data", Duration::from_secs(10)) {
            if let Some(line) = df.lines().last() {
                let cols: Vec<&str> = line.split_whitespace().collect();
                if cols.len() >= 4 {
                    let total: u64 = cols[1].parse().unwrap_or(0);
                    let free: u64 = cols[3].parse().unwrap_or(0);
                    let gb = |k: u64| format!("{:.1} GB", k as f64 / 1024.0 / 1024.0);
                    info.storage_total = gb(total);
                    info.storage_free = gb(free);
                }
            }
        }

        info.screen = adb
            .shell_text(target, "wm size", Duration::from_secs(8))
            .unwrap_or_default()
            .replace('\n', ", ")
            .trim_end_matches(", ")
            .to_string();
        info.density = adb
            .shell_text(target, "wm density", Duration::from_secs(8))
            .unwrap_or_default()
            .replace('\n', ", ")
            .trim_end_matches(", ")
            .to_string();

        if sdk >= 24 {
            info.home = current_home(adb, target).unwrap_or_default();
            info.home_candidates = home_candidates(adb, target).unwrap_or_default();
        }
        info.focused = focused_activity(adb, target);

        if let Ok(pkgs) = adb.shell_text(target, "pm list packages com.gathin.tvhome", Duration::from_secs(12)) {
            info.launcher_installed = pkgs.contains("com.gathin.tvhome");
        }
        if info.launcher_installed {
            if let Ok(dump) = adb.shell_text(
                target,
                "dumpsys package com.gathin.tvhome | grep versionName",
                Duration::from_secs(12),
            ) {
                info.launcher_version = dump
                    .split_whitespace()
                    .last()
                    .unwrap_or("")
                    .to_string();
            }
        }

        if let Ok(text) = adb.shell_text(
            target,
            "dumpsys connectivity | grep -o 'Transports: [A-Z]*'",
            Duration::from_secs(12),
        ) {
            for line in text.lines() {
                if let Some(t) = line.trim().strip_prefix("Transports: ") {
                    if !t.is_empty() && !info.transports.iter().any(|x| x == t) {
                        info.transports.push(t.to_string());
                    }
                }
            }
        }

        info.usb_host_connected = adb
            .shell_text(target, "dumpsys usb | grep host_connected", Duration::from_secs(12))
            .ok()
            .and_then(|t| {
                if t.contains("host_connected=true") {
                    Some(true)
                } else if t.contains("host_connected=false") {
                    Some(false)
                } else {
                    None
                }
            });

        let wireless = setting(adb, target, "global", "adb_wifi_enabled");
        info.wireless_debugging = match wireless.as_str() {
            "1" => Some(true),
            "0" => Some(false),
            _ => None,
        };
        info.adb_enabled = setting(adb, target, "global", "adb_enabled") != "0";
        info.developer_options = setting(adb, target, "global", "development_settings_enabled") == "1";
        info.private_dns_mode = setting(adb, target, "global", "private_dns_mode");
        info.private_dns_specifier = setting(adb, target, "global", "private_dns_specifier");
        Ok(info)
    })
    .await
}

#[derive(Serialize)]
pub struct Shot {
    pub png: String,
    pub path: String,
    pub width: u32,
    pub height: u32,
}

fn png_size(png: &[u8]) -> (u32, u32) {
    if png.len() >= 24 && png.starts_with(&[0x89, b'P', b'N', b'G']) {
        let w = u32::from_be_bytes([png[16], png[17], png[18], png[19]]);
        let h = u32::from_be_bytes([png[20], png[21], png[22], png[23]]);
        return (w, h);
    }
    (0, 0)
}

#[tauri::command]
pub async fn device_screenshot(state: State<'_, AppState>, save: bool) -> Res<Shot> {
    let (adb, target) = session(&state)?;
    let dir = super::output_root(&state).join("shots");
    on_tv(adb, target, move |adb, target| {
        let png = adb.screenshot(target)?;
        let (width, height) = png_size(&png);
        let mut path = String::new();
        if save {
            let file = dir.join(format!("{}.png", timestamp()));
            crate::adb::write_file(&file, &png)?;
            path = file.display().to_string();
        }
        Ok(Shot {
            png: B64.encode(&png),
            path,
            width,
            height,
        })
    })
    .await
}

const KEYS: &[&str] = &[
    "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "DPAD_CENTER", "BACK", "HOME", "MENU",
    "VOLUME_UP", "VOLUME_DOWN", "POWER", "MEDIA_PLAY_PAUSE", "MEDIA_NEXT", "MEDIA_PREVIOUS", "TV_INPUT",
];

#[tauri::command]
pub async fn device_key(state: State<'_, AppState>, key: String) -> Res<String> {
    if !KEYS.contains(&key.as_str()) {
        return Err(format!("{key} is not a key this app sends."));
    }
    let (adb, target) = session(&state)?;
    on_tv(adb, target, move |adb, target| {
        adb.shell_text(target, &format!("input keyevent {key}"), Duration::from_secs(12))
    })
    .await
}

#[tauri::command]
pub async fn device_text(state: State<'_, AppState>, text: String) -> Res<String> {
    if text.len() > 200 {
        return Err("Keep injected text under 200 characters.".into());
    }
    let (adb, target) = session(&state)?;
    on_tv(adb, target, move |adb, target| {
        // Quoted, so spaces stay spaces and `&`, `;` and quotes are text, not shell syntax.
        adb.shell_text(
            target,
            &format!("input text {}", super::shell_quote(&text)),
            Duration::from_secs(12),
        )
    })
    .await
}

/// The display size `input tap` counts in: the override size when one is set, otherwise the panel's.
/// It is not always the screenshot's size, which is why taps are sent as fractions of the screen.
fn display_size(adb: &Adb, target: &str) -> Option<(u32, u32)> {
    let text = adb
        .shell_text(target, "wm size", Duration::from_secs(10))
        .ok()?;
    let parse = |line: &str| -> Option<(u32, u32)> {
        let (w, h) = line.split_once('x')?;
        Some((w.trim().parse().ok()?, h.trim().parse().ok()?))
    };
    let mut physical = None;
    for line in text.lines() {
        let line = line.trim();
        if let Some(rest) = line.strip_prefix("Override size:") {
            // Android prints the override line after the physical one, and it wins.
            if let Some(size) = parse(rest) {
                return Some(size);
            }
        }
        if let Some(rest) = line.strip_prefix("Physical size:") {
            physical = parse(rest);
        }
    }
    physical
}

/// A 0..1 point on the screen turned into an integer pixel `input tap` accepts.
fn map_point(u: f64, v: f64, width: u32, height: u32) -> (u32, u32) {
    let x = (u.clamp(0.0, 1.0) * width as f64).round() as i64;
    let y = (v.clamp(0.0, 1.0) * height as f64).round() as i64;
    (
        x.clamp(0, width.saturating_sub(1) as i64) as u32,
        y.clamp(0, height.saturating_sub(1) as i64) as u32,
    )
}

/// A tap on the TV screen image, as fractions of the screen so the app never has to guess the panel
/// size. `tap` is a normal click; `long` is the same point held for 650 ms.
#[tauri::command]
pub async fn device_tap(
    state: State<'_, AppState>,
    u: f64,
    v: f64,
    mode: String,
) -> Res<String> {
    if !(0.0..=1.0).contains(&u) || !(0.0..=1.0).contains(&v) {
        return Err("A tap has to be a point on the screen.".into());
    }
    let long = match mode.as_str() {
        "tap" => false,
        "long" => true,
        other => return Err(format!("{other} is not a tap this app sends.")),
    };
    let (adb, target) = session(&state)?;
    on_tv(adb, target, move |adb, target| {
        let (width, height) = display_size(adb, target).unwrap_or((1920, 1080));
        let (x, y) = map_point(u, v, width, height);
        let command = if long {
            format!("input swipe {x} {y} {x} {y} 650")
        } else {
            format!("input tap {x} {y}")
        };
        adb.shell_text(target, &command, Duration::from_secs(15))?;
        Ok(format!("{width}×{height} · {x},{y}"))
    })
    .await
}

#[tauri::command]
pub async fn device_sync(state: State<'_, AppState>) -> Res<String> {
    let (adb, target) = session(&state)?;
    on_tv(adb, target, |adb, target| {
        adb.shell_text(target, "sync", Duration::from_secs(20))
    })
    .await
}

/// Reboot, after the write delay the project asks for (Android writes package state about ten seconds
/// after a change; PROMPT.md wants about 15 s so the next boot does not read the old state).
#[tauri::command]
pub async fn device_reboot(
    app: tauri::AppHandle,
    state: State<'_, AppState>,
    wait_for_writes: bool,
) -> Res<String> {
    let (adb, target) = session(&state)?;
    let adb2 = adb.clone();
    let out = on_tv(adb, target, move |adb, target| {
        let _ = adb.shell_text(target, "sync", Duration::from_secs(20));
        if wait_for_writes {
            for left in (1..=12).rev() {
                let _ = app.emit("reboot://countdown", left);
                std::thread::sleep(Duration::from_secs(1));
            }
        }
        adb.shell_text(target, "reboot", Duration::from_secs(10)).or_else(|_| {
            // adb drops the transport as the TV goes down; that is the reboot, not a failure.
            adb.run(&["-s", target, "reboot"], Duration::from_secs(10)).map(|_| String::new())
        })
    })
    .await;
    let _ = adb2.disconnect(None);
    out.map(|_| "Reboot requested. The TV comes back in about a minute.".to_string())
}

#[derive(Serialize)]
pub struct PortState {
    pub open: bool,
    pub detail: String,
}

#[tauri::command]
pub fn port_check(host: String, port: u16) -> PortState {
    use std::net::{TcpStream, ToSocketAddrs};
    let addr = format!("{host}:{port}");
    let Ok(mut addrs) = addr.to_socket_addrs() else {
        return PortState { open: false, detail: "Could not resolve the address.".into() };
    };
    let Some(addr) = addrs.next() else {
        return PortState { open: false, detail: "Could not resolve the address.".into() };
    };
    let start = Instant::now();
    match TcpStream::connect_timeout(&addr, Duration::from_secs(3)) {
        Ok(_) => PortState {
            open: true,
            detail: format!("Port {port} answers ({} ms).", start.elapsed().as_millis()),
        },
        Err(e) => PortState {
            open: false,
            detail: format!("Port {port} is closed: {e}"),
        },
    }
}

#[derive(Serialize)]
pub struct Step {
    pub label: String,
    pub ok: bool,
    pub detail: String,
}

#[derive(Serialize)]
pub struct CloseReport {
    pub steps: Vec<Step>,
    pub port_open: bool,
    pub port_detail: String,
    pub wireless_off: Option<bool>,
    pub usb_off: Option<bool>,
}

/// Turn network and USB debugging off, then prove the port is closed from this computer.
#[tauri::command]
pub async fn adb_close(
    state: State<'_, AppState>,
    host: String,
    port: u16,
    wireless: bool,
    usb: bool,
    port_to_check: Option<u16>,
) -> Res<CloseReport> {
    let (adb, target) = session(&state)?;
    let report = {
        let host = host.clone();
        let check = port_to_check.unwrap_or(port);
        on_tv(adb, target, move |adb, target| {
            let mut steps = Vec::new();
            let mut wireless_off = None;
            let mut usb_off = None;

            if wireless {
                let out = adb.shell_text(
                    target,
                    "settings put global adb_wifi_enabled 0",
                    Duration::from_secs(10),
                );
                let ok = out.is_ok();
                steps.push(Step {
                    label: "Wireless debugging off".into(),
                    ok,
                    detail: out.unwrap_or_else(|e| e),
                });
                wireless_off = Some(ok);
            }
            if usb {
                // This one usually drops the very transport the answer would travel on, so a
                // missing answer is not a failure: the port check below is what decides.
                let out =
                    adb.shell_text(target, "settings put global adb_enabled 0", Duration::from_secs(8));
                let ok = match &out {
                    Ok(text) => !text.to_ascii_lowercase().contains("error"),
                    Err(_) => true,
                };
                steps.push(Step {
                    label: "USB debugging off".into(),
                    ok,
                    detail: match out {
                        Ok(text) if !text.trim().is_empty() => text,
                        Ok(_) => "accepted".into(),
                        Err(e) => format!(
                            "the connection dropped while the command ran ({e}); this is expected"
                        ),
                    },
                });
                usb_off = Some(ok);
            }

            let _ = adb.disconnect(Some(target));
            // Give adbd a moment to drop the listener before the port is probed.
            std::thread::sleep(Duration::from_millis(1500));

            let now = port_check(host, check);
            steps.push(Step {
                label: format!("Port {check}"),
                ok: !now.open,
                detail: now.detail.clone(),
            });
            Ok(CloseReport {
                steps,
                port_open: now.open,
                port_detail: now.detail,
                wireless_off,
                usb_off,
            })
        })
        .await?
    };

    if let Ok(mut inner) = state.0.lock() {
        inner.target = None;
    }
    Ok(report)
}

#[derive(Serialize)]
pub struct DnsState {
    pub mode: String,
    pub specifier: String,
}

#[tauri::command]
pub async fn private_dns_get(state: State<'_, AppState>) -> Res<DnsState> {
    let (adb, target) = session(&state)?;
    on_tv(adb, target, |adb, target| {
        let get = |key: &str| {
            adb.shell_text(target, &format!("settings get global {key}"), Duration::from_secs(8))
                .map(|t| t.trim().to_string())
                .unwrap_or_default()
        };
        Ok(DnsState {
            mode: get("private_dns_mode"),
            specifier: get("private_dns_specifier"),
        })
    })
    .await
}

fn valid_hostname(host: &str) -> bool {
    let re = regex::Regex::new(
        r"^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$",
    )
    .unwrap();
    re.is_match(host) && host.len() <= 253
}

#[tauri::command]
pub async fn private_dns_set(
    state: State<'_, AppState>,
    mode: String,
    specifier: String,
) -> Res<Vec<Step>> {
    if !matches!(mode.as_str(), "off" | "opportunistic" | "hostname") {
        return Err("Private DNS mode must be off, opportunistic or hostname.".into());
    }
    if mode == "hostname" && !valid_hostname(&specifier) {
        return Err("That does not look like a DNS hostname.".into());
    }
    let (adb, target) = session(&state)?;
    on_tv(adb, target, move |adb, target| {
        let mut steps = Vec::new();
        if mode == "hostname" {
            let out = adb.shell_text(
                target,
                &format!("settings put global private_dns_specifier {specifier}"),
                Duration::from_secs(8),
            )?;
            steps.push(Step {
                label: format!("Resolver set to {specifier}"),
                ok: true,
                detail: out,
            });
        }
        let out = adb.shell_text(
            target,
            &format!("settings put global private_dns_mode {mode}"),
            Duration::from_secs(8),
        )?;
        steps.push(Step { label: format!("Mode {mode}"), ok: true, detail: out });
        Ok(steps)
    })
    .await
}

#[tauri::command]
pub fn settings_get(state: State<'_, AppState>) -> Settings {
    state.0.lock().unwrap().settings.clone()
}

#[tauri::command]
pub fn settings_save(state: State<'_, AppState>, settings: Settings) -> Res<Settings> {
    let mut inner = state.0.lock().unwrap();
    let old_path = inner.settings.adb_path.clone();
    inner.settings = settings;
    if inner.settings.adb_path != old_path {
        inner.adb = Adb::locate(inner.settings.adb_path.as_deref());
    }
    inner.port = inner.settings.last_port;
    save_settings(&inner)?;
    Ok(inner.settings.clone())
}

#[derive(Serialize)]
pub struct RepoInfo {
    pub path: Option<String>,
    pub launcher: bool,
    pub examples: usize,
}

fn repo_root(start: &Path) -> Option<PathBuf> {
    start
        .ancestors()
        .find(|dir| {
            dir.join("launcher/app/build.gradle").is_file() && dir.join("examples").is_dir()
        })
        .map(Path::to_path_buf)
}

#[tauri::command]
pub fn repo_detect(state: State<'_, AppState>, path: Option<String>) -> RepoInfo {
    let mut candidates: Vec<PathBuf> = Vec::new();
    if let Some(p) = path.filter(|p| !p.trim().is_empty()) {
        candidates.push(PathBuf::from(p));
    }
    if let Ok(p) = std::env::current_dir() {
        candidates.push(p);
    }
    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            candidates.push(dir.to_path_buf());
        }
    }
    {
        let inner = state.0.lock().unwrap();
        if let Some(p) = inner.settings.repo_path.clone() {
            candidates.insert(0, PathBuf::from(p));
        }
    }
    for start in candidates {
        if let Some(root) = repo_root(&start) {
            let examples = std::fs::read_dir(root.join("examples"))
                .map(|it| it.filter_map(Result::ok).filter(|e| e.path().is_dir()).count())
                .unwrap_or(0);
            return RepoInfo {
                path: Some(root.display().to_string()),
                launcher: root.join("launcher/app/src/main/res/values/config.xml").is_file(),
                examples,
            };
        }
    }
    RepoInfo { path: None, launcher: false, examples: 0 }
}

#[derive(Serialize)]
pub struct ExampleList {
    pub folder: String,
    pub tv: String,
    pub os: String,
    pub brand: String,
    pub notes: String,
    pub path: String,
    pub packages: Vec<String>,
    pub raw: String,
}

fn parse_debloat(text: &str) -> Vec<String> {
    let mut out = Vec::new();
    for line in text.lines() {
        let line = line.trim_start_matches('\u{feff}').trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let body = match line.find(" #") {
            Some(i) => &line[..i],
            None => line,
        };
        let Some(name) = body.split_whitespace().next() else { continue };
        if crate::adb::is_package_name(name) && !out.contains(&name.to_string()) {
            out.push(name.to_string());
        }
    }
    out
}

#[tauri::command]
pub fn examples_list(state: State<'_, AppState>, repo: String) -> Res<Vec<ExampleList>> {
    let root = if repo.trim().is_empty() {
        let detected = repo_detect(state, None);
        detected.path.map(PathBuf::from).ok_or(
            "The CleanYourTV checkout was not found. Set its folder in Settings, or import a list \
             file with the button on the Debloat screen.",
        )?
    } else {
        PathBuf::from(repo)
    };
    let dir = root.join("examples");
    let mut out = Vec::new();
    let entries = std::fs::read_dir(&dir).map_err(|e| format!("{}: {e}", dir.display()))?;
    for entry in entries.flatten() {
        let folder = entry.path();
        if !folder.is_dir() {
            continue;
        }
        let list = folder.join("debloat.txt");
        if !list.is_file() {
            continue;
        }
        let raw = std::fs::read_to_string(&list).unwrap_or_default();
        let tv_json: serde_json::Value = std::fs::read_to_string(folder.join("tv.json"))
            .ok()
            .and_then(|t| serde_json::from_str(&t).ok())
            .unwrap_or(serde_json::Value::Null);
        let field = |k: &str| {
            tv_json
                .get(k)
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string()
        };
        out.push(ExampleList {
            folder: folder.file_name().unwrap_or_default().to_string_lossy().to_string(),
            tv: field("tv"),
            os: field("os"),
            brand: field("brand"),
            notes: field("notes"),
            path: list.display().to_string(),
            packages: parse_debloat(&raw),
            raw,
        });
    }
    out.sort_by(|a, b| a.folder.cmp(&b.folder));
    Ok(out)
}

#[derive(Serialize)]
pub struct RestoreScripts {
    pub sh: String,
    pub ps1: String,
}

/// A restore script for a list of packages, ready to save anywhere the person likes.
#[tauri::command]
pub fn restore_scripts(packages: Vec<String>, host: String) -> Res<RestoreScripts> {
    let mut sh = String::from("#!/usr/bin/env sh\n# Re-enable the packages CleanYourTV Desktop disabled.\nset -e\nTV=\"${TV_HOST:?set TV_HOST to the TV IP address}\"\n");
    let mut ps1 = String::from("# Re-enable the packages CleanYourTV Desktop disabled.\n$ErrorActionPreference = 'Stop'\nparam([Parameter(Mandatory=$true)][string]$TvHost)\n");
    let _ = host;
    for pkg in &packages {
        if !crate::adb::is_package_name(pkg) {
            continue;
        }
        sh.push_str(&format!("adb -s \"$TV:5555\" shell pm enable {pkg}\n"));
        ps1.push_str(&format!("adb -s \"$($TvHost):5555\" shell pm enable {pkg}\n"));
    }
    Ok(RestoreScripts { sh, ps1 })
}

#[derive(Serialize)]
pub struct ImportedList {
    pub name: String,
    pub raw: String,
}

#[tauri::command]
pub fn import_list(file: String) -> Res<ImportedList> {
    let path = PathBuf::from(&file);
    let raw = std::fs::read_to_string(&path).map_err(|e| format!("{}: {e}", path.display()))?;
    Ok(ImportedList {
        name: path
            .file_name()
            .unwrap_or_default()
            .to_string_lossy()
            .to_string(),
        raw,
    })
}

/// Write a text file the person picked in a save dialog (restore scripts, config.json exports).
#[tauri::command]
pub fn save_text(path: String, text: String) -> Res<String> {
    let file = PathBuf::from(&path);
    if let Some(parent) = file.parent() {
        if !parent.as_os_str().is_empty() {
            std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
        }
    }
    std::fs::write(&file, text).map_err(|e| format!("{}: {e}", file.display()))?;
    Ok(file.display().to_string())
}

#[cfg(test)]
mod tap_tests {
    use super::map_point;

    #[test]
    fn the_middle_of_the_screen_is_the_middle() {
        assert_eq!(map_point(0.5, 0.5, 1920, 1080), (960, 540));
    }

    #[test]
    fn edges_stay_inside_the_panel() {
        assert_eq!(map_point(0.0, 0.0, 1920, 1080), (0, 0));
        assert_eq!(map_point(1.0, 1.0, 1920, 1080), (1919, 1079));
        // A jittered click, or a rounded layout, cannot send a coordinate off the screen.
        assert_eq!(map_point(-0.2, 1.7, 3840, 2160), (0, 2159));
    }

    #[test]
    fn a_4k_panel_scales_from_the_same_fraction() {
        assert_eq!(map_point(0.25, 0.75, 3840, 2160), (960, 1620));
    }
}
