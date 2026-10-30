//! ADB transport for the desktop app.
//!
//! One `adb` binary is used for everything (connect, shell, push/pull, install, screenshots). The
//! binary is found in this order: an explicit path from Settings, `CLEANYOURTV_ADB`, the `PATH`, the
//! usual Android SDK locations. Every call runs with a timeout so a wedged TV cannot freeze the UI.

use std::ffi::OsStr;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::time::Duration;
use wait_timeout::ChildExt;

#[derive(Debug, Clone)]
pub struct Adb {
    pub path: PathBuf,
    pub source: &'static str,
    pub version: String,
}

#[derive(Debug, Clone)]
pub struct RunOutput {
    pub ok: bool,
    pub stdout: String,
    pub stderr: String,
    pub code: Option<i32>,
}

impl RunOutput {
    /// Everything adb printed, the way the shell scripts see it.
    pub fn all(&self) -> String {
        match (self.stdout.trim(), self.stderr.trim()) {
            ("", e) => e.to_string(),
            (o, "") => o.to_string(),
            (o, e) => format!("{o}\n{e}"),
        }
    }

    pub fn failed_with(&self, needle: &str) -> bool {
        self.all().to_ascii_lowercase().contains(&needle.to_ascii_lowercase())
    }
}

fn binary_name() -> &'static str {
    if cfg!(windows) {
        "adb.exe"
    } else {
        "adb"
    }
}

fn looks_like_adb(path: &Path) -> bool {
    path.is_file()
}

fn sdk_candidates() -> Vec<PathBuf> {
    let mut out = Vec::new();
    let mut push = |base: PathBuf| {
        out.push(base.join("platform-tools").join(binary_name()));
    };
    if let Ok(home) = std::env::var("HOME") {
        let home = PathBuf::from(home);
        push(home.join("Android/Sdk"));
        push(home.join("Library/Android/sdk"));
        push(home.join("android-sdk"));
    }
    if let Ok(local) = std::env::var("LOCALAPPDATA") {
        push(PathBuf::from(local).join("Android/Sdk"));
    }
    if let Ok(sdk) = std::env::var("ANDROID_SDK_ROOT") {
        out.push(PathBuf::from(sdk).join("platform-tools").join(binary_name()));
    }
    if let Ok(sdk) = std::env::var("ANDROID_HOME") {
        out.push(PathBuf::from(sdk).join("platform-tools").join(binary_name()));
    }
    out.push(PathBuf::from("/usr/lib/android-sdk/platform-tools/adb"));
    out.push(PathBuf::from("/opt/android-sdk/platform-tools/adb"));
    out.push(PathBuf::from("/usr/local/bin/adb"));
    out.push(PathBuf::from("/opt/homebrew/bin/adb"));
    out
}

pub fn search_paths() -> Vec<PathBuf> {
    let mut out = Vec::new();
    if let Ok(explicit) = std::env::var("CLEANYOURTV_ADB") {
        out.push(PathBuf::from(explicit));
    }
    if let Some(path) = std::env::var_os("PATH") {
        for dir in std::env::split_paths(&path) {
            out.push(dir.join(binary_name()));
        }
    }
    out.extend(sdk_candidates());
    out
}

impl Adb {
    pub fn locate(explicit: Option<&str>) -> Option<Adb> {
        if let Some(path) = explicit.map(str::trim).filter(|p| !p.is_empty()) {
            let path = PathBuf::from(path);
            if looks_like_adb(&path) {
                return Adb::load(path, "settings");
            }
        }
        for path in search_paths() {
            if looks_like_adb(&path) {
                if let Some(adb) = Adb::load(path, "search") {
                    return Some(adb);
                }
            }
        }
        None
    }

    fn load(path: PathBuf, source: &'static str) -> Option<Adb> {
        let mut adb = Adb { path, source, version: String::new() };
        let out = adb.run(&["version"], Duration::from_secs(8)).ok()?;
        if !out.ok {
            return None;
        }
        let first = out.stdout.lines().next().unwrap_or("").trim().to_string();
        adb.version = first;
        Some(adb)
    }

    pub fn run(&self, args: &[&str], timeout: Duration) -> Result<RunOutput, String> {
        let mut cmd = Command::new(&self.path);
        cmd.args(args)
            .stdin(Stdio::null())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped());
        hide_console(&mut cmd);
        let mut child = cmd
            .spawn()
            .map_err(|e| format!("could not start {}: {e}", self.path.display()))?;
        let status = match child.wait_timeout(timeout) {
            Ok(Some(status)) => status,
            Ok(None) => {
                let _ = child.kill();
                let _ = child.wait();
                return Err(format!(
                    "adb did not answer within {} s; the TV may be busy or asleep",
                    timeout.as_secs()
                ));
            }
            Err(e) => return Err(format!("could not wait for adb: {e}")),
        };
        let mut stdout = Vec::new();
        let mut stderr = Vec::new();
        if let Some(mut s) = child.stdout.take() {
            let _ = std::io::Read::read_to_end(&mut s, &mut stdout);
        }
        if let Some(mut s) = child.stderr.take() {
            let _ = std::io::Read::read_to_end(&mut s, &mut stderr);
        }
        Ok(RunOutput {
            ok: status.success(),
            stdout: String::from_utf8_lossy(&stdout).into_owned(),
            stderr: String::from_utf8_lossy(&stderr).into_owned(),
            code: status.code(),
        })
    }

    /// Run a command with raw bytes out (screencap, local file reads).
    ///
    /// stdout is drained on its own thread while the child runs: a screenshot is much bigger than
    /// the pipe buffer, so waiting for the exit first leaves adb blocked on a write and the call
    /// times out on a TV that was answering all along.
    pub fn run_bytes(&self, args: &[&str], timeout: Duration) -> Result<Vec<u8>, String> {
        let mut cmd = Command::new(&self.path);
        cmd.args(args)
            .stdin(Stdio::null())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped());
        hide_console(&mut cmd);
        let mut child = cmd.spawn().map_err(|e| e.to_string())?;
        let reader = child.stdout.take().map(|mut s| {
            std::thread::spawn(move || {
                let mut buf = Vec::new();
                let _ = std::io::Read::read_to_end(&mut s, &mut buf);
                buf
            })
        });
        let status = match child.wait_timeout(timeout) {
            Ok(Some(status)) => status,
            Ok(None) => {
                let _ = child.kill();
                let _ = child.wait();
                return Err(format!(
                    "adb did not answer within {} s; the TV may be busy or asleep",
                    timeout.as_secs()
                ));
            }
            Err(e) => {
                let _ = child.kill();
                let _ = child.wait();
                return Err(format!("could not wait for adb: {e}"));
            }
        };
        let mut err = Vec::new();
        if let Some(mut s) = child.stderr.take() {
            let _ = std::io::Read::read_to_end(&mut s, &mut err);
        }
        let out = reader.and_then(|h| h.join().ok()).unwrap_or_default();
        if !status.success() && out.is_empty() {
            let text = String::from_utf8_lossy(&err).trim().to_string();
            return Err(if text.is_empty() {
                format!("adb exited with {:?}", status.code())
            } else {
                text
            });
        }
        Ok(out)
    }

    pub fn shell(&self, serial: &str, command: &str, timeout: Duration) -> Result<RunOutput, String> {
        self.run(&["-s", serial, "shell", command], timeout)
    }

    /// Shell output as text, with the usual auth/connection failures turned into advice.
    pub fn shell_text(&self, serial: &str, command: &str, timeout: Duration) -> Result<String, String> {
        let out = self.shell(serial, command, timeout)?;
        let text = out.all();
        let lowered = text.to_ascii_lowercase();
        if lowered.contains("device unauthorized")
            || lowered.contains("unauthorized")
            || lowered.contains("error: device still authorizing")
        {
            return Err(
                "The TV has not authorised this computer yet. Look at the TV for the \"Allow USB \
                 debugging?\" dialog, tick \"Always allow\" and press Allow, then try again."
                    .into(),
            );
        }
        if lowered.contains("device offline") || lowered.contains("no devices/emulators found") {
            return Err("The TV is no longer connected. Reconnect and try again.".into());
        }
        if lowered.contains("device not found") {
            return Err("ADB cannot see the TV any more. Reconnect and try again.".into());
        }
        if !out.ok && text.trim().is_empty() {
            return Err(format!("adb exited with {:?}", out.code));
        }
        Ok(text)
    }

    pub fn connect(&self, host: &str, port: u16) -> Result<String, String> {
        let endpoint = format!("{host}:{port}");
        let out = self.run(&["connect", &endpoint], Duration::from_secs(25))?;
        let text = out.all();
        let lowered = text.to_ascii_lowercase();
        if lowered.contains("failed to connect")
            || lowered.contains("cannot connect")
            || lowered.contains("connection refused")
            || lowered.contains("unable to connect")
            || lowered.contains("no route to host")
        {
            return Err(text.trim().to_string());
        }
        Ok(text.trim().to_string())
    }

    pub fn pair(&self, host: &str, port: u16, code: &str) -> Result<String, String> {
        let endpoint = format!("{host}:{port}");
        let out = self.run(&["pair", &endpoint, code.trim()], Duration::from_secs(40))?;
        let text = out.all();
        if !out.ok || text.to_ascii_lowercase().contains("failed") {
            return Err(text.trim().to_string());
        }
        Ok(text.trim().to_string())
    }

    pub fn disconnect(&self, target: Option<&str>) -> Result<(), String> {
        if let Some(target) = target {
            self.run(&["disconnect", target], Duration::from_secs(10))?;
        }
        Ok(())
    }

    pub fn devices(&self) -> Result<Vec<DeviceLine>, String> {
        let out = self.run(&["devices", "-l"], Duration::from_secs(10))?;
        Ok(parse_devices(&out.stdout))
    }

    pub fn mdns_services(&self) -> Result<Vec<MdnsService>, String> {
        let out = self.run(&["mdns", "services"], Duration::from_secs(8))?;
        Ok(parse_mdns(&out.stdout))
    }

    pub fn push(&self, serial: &str, local: &str, remote: &str, timeout: Duration) -> Result<String, String> {
        let out = self.run(&["-s", serial, "push", local, remote], timeout)?;
        let text = out.all();
        if !out.ok {
            return Err(text);
        }
        Ok(text)
    }

    pub fn pull(&self, serial: &str, remote: &str, local: &str, timeout: Duration) -> Result<String, String> {
        let out = self.run(&["-s", serial, "pull", remote, local], timeout)?;
        let text = out.all();
        if !out.ok {
            return Err(text);
        }
        Ok(text)
    }

    pub fn install(&self, serial: &str, apk: &str, timeout: Duration) -> Result<String, String> {
        let out = self.run(&["-s", serial, "install", "-r", "-d", apk], timeout)?;
        Ok(out.all())
    }

    /// `exec-out screencap -p`, falling back to a file on the TV when a firmware does not stream it.
    ///
    /// Some vendor firmwares print a banner on stdout before the image ("Init wrapper sys mutex
    /// successfully. Pid:21431"), so the PNG is found and sliced out rather than assumed to start
    /// the stream; otherwise every screenshot looks empty on those sets.
    pub fn screenshot(&self, serial: &str) -> Result<Vec<u8>, String> {
        let first = self.run_bytes(&["-s", serial, "exec-out", "screencap", "-p"], Duration::from_secs(25));
        if let Ok(raw) = &first {
            if let Some(png) = png_slice(raw) {
                return Ok(png.to_vec());
            }
        }
        // Some firmwares garble `exec-out`, so write the PNG on the TV and pull it into a local
        // file. `adb pull <remote> -` is not a thing: adb needs a real destination.
        let path = "/data/local/tmp/cyt-shot.png";
        let local = std::env::temp_dir().join(format!("cyt-shot-{}.png", std::process::id()));
        let local_arg = local.display().to_string();
        let pulled = self
            .shell_text(serial, &format!("screencap -p {path}"), Duration::from_secs(20))
            .and_then(|_| self.pull(serial, path, &local_arg, Duration::from_secs(40)));
        let _ = self.shell_text(serial, &format!("rm -f {path}"), Duration::from_secs(10));
        let bytes = match pulled {
            Ok(_) => std::fs::read(&local).unwrap_or_default(),
            Err(e) => {
                let _ = std::fs::remove_file(&local);
                let reason = match &first {
                    Err(why) => why.clone(),
                    Ok(_) => e,
                };
                let front = self
                    .shell_text(serial, "dumpsys window | grep mCurrentFocus", Duration::from_secs(10))
                    .ok()
                    .and_then(|t| {
                        t.split_whitespace()
                            .find(|w| w.contains('/') && w.contains('.'))
                            .map(|w| w.trim_end_matches('}').to_string())
                    });
                // Naming the app in front is what tells a Live TV refusal apart from a dead TV.
                let front = match front {
                    Some(app) => format!(" while {app} is in front"),
                    None => String::new(),
                };
                return Err(format!(
                    "the TV refused a screenshot{front}: {reason}. Live TV, HDMI and some players \
                     protect their picture, and that is the TV, not this app"
                ));
            }
        };
        let _ = std::fs::remove_file(&local);
        match png_slice(&bytes) {
            Some(png) => Ok(png.to_vec()),
            None => {
                Err("the screenshot came back empty; Live TV, HDMI and some players protect their picture".into())
            }
        }
    }
}

const PNG_SIG: [u8; 8] = [0x89, b'P', b'N', b'G', 0x0d, 0x0a, 0x1a, 0x0a];

/// The PNG inside a screencap stream, past any banner the TV printed first. A real screenshot is
/// far bigger than a stray signature in a log line, so a short tail is not one.
fn png_slice(bytes: &[u8]) -> Option<&[u8]> {
    let at = bytes.windows(PNG_SIG.len()).position(|w| w == PNG_SIG)?;
    let png = &bytes[at..];
    if png.len() > 1024 {
        Some(png)
    } else {
        None
    }
}

#[derive(Debug, Clone, serde::Serialize)]
pub struct DeviceLine {
    pub serial: String,
    pub state: String,
    pub model: String,
    pub product: String,
    pub connection: String,
}

#[derive(Debug, Clone, serde::Serialize)]
pub struct MdnsService {
    pub name: String,
    pub kind: String,
    pub address: String,
}

pub fn parse_devices(text: &str) -> Vec<DeviceLine> {
    let mut out = Vec::new();
    for line in text.lines().skip(1) {
        let line = line.trim();
        if line.is_empty() || line.starts_with('*') {
            continue;
        }
        let mut parts = line.split_whitespace();
        let Some(serial) = parts.next() else { continue };
        let Some(state) = parts.next() else { continue };
        let mut model = String::new();
        let mut product = String::new();
        for part in parts {
            if let Some(v) = part.strip_prefix("model:") {
                model = v.replace('_', " ");
            } else if let Some(v) = part.strip_prefix("product:") {
                product = v.to_string();
            }
        }
        out.push(DeviceLine {
            serial: serial.to_string(),
            state: state.to_string(),
            model,
            product,
            connection: if serial.contains(':') { "network".into() } else { "usb".into() },
        });
    }
    out
}

pub fn parse_mdns(text: &str) -> Vec<MdnsService> {
    let mut out = Vec::new();
    for line in text.lines() {
        let cols: Vec<&str> = line.split_whitespace().collect();
        if cols.len() < 3 {
            continue;
        }
        // adb-XXXX\t_adb-tls-connect._tcp\t192.168.1.20:37421
        if !cols[1].starts_with("_adb") {
            continue;
        }
        out.push(MdnsService {
            name: cols[0].to_string(),
            kind: cols[1].trim_end_matches('.').to_string(),
            address: cols[2].to_string(),
        });
    }
    out
}

/// Write bytes to a local file, creating parents.
pub fn write_file(path: &Path, bytes: &[u8]) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).map_err(|e| format!("{}: {e}", parent.display()))?;
    }
    let mut f = std::fs::File::create(path).map_err(|e| format!("{}: {e}", path.display()))?;
    f.write_all(bytes).map_err(|e| e.to_string())
}

pub fn md5_hex(bytes: &[u8]) -> String {
    use md5::{Digest, Md5};
    let mut hasher = Md5::new();
    hasher.update(bytes);
    format!("{:x}", hasher.finalize())
}

pub fn md5_file(path: &Path) -> Result<String, String> {
    let bytes = std::fs::read(path).map_err(|e| format!("{}: {e}", path.display()))?;
    Ok(md5_hex(&bytes))
}

fn hide_console(cmd: &mut Command) {
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }
    #[cfg(not(windows))]
    let _ = cmd;
}

/// True when the string is a valid Android package name (the same shape disable.sh accepts).
pub fn is_package_name(name: &str) -> bool {
    let mut chars = name.chars();
    match chars.next() {
        Some(c) if c.is_ascii_alphabetic() || c == '_' => {}
        _ => return false,
    }
    if !name.contains('.') {
        return false;
    }
    name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_' || c == '.')
        && !name.contains("..")
        && !name.ends_with('.')
}

pub fn os_str(s: &str) -> &OsStr {
    OsStr::new(s)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn package_names() {
        assert!(is_package_name("com.google.android.youtube.tv"));
        assert!(is_package_name("com.mediatek.tv.service"));
        assert!(!is_package_name("android"));
        assert!(!is_package_name("com..broken"));
        assert!(!is_package_name("com.space name"));
        assert!(!is_package_name("1com.example"));
    }

    #[test]
    fn devices_parse() {
        let text = "List of devices attached\n192.168.1.20:5555\tdevice product:foo model:Android_TV device:bar\n";
        let d = parse_devices(text);
        assert_eq!(d.len(), 1);
        assert_eq!(d[0].model, "Android TV");
        assert_eq!(d[0].connection, "network");
    }

    #[test]
    fn mdns_parse() {
        let text = "adb-123\t_adb-tls-connect._tcp\t192.168.1.20:37123\n";
        let m = parse_mdns(text);
        assert_eq!(m.len(), 1);
        assert_eq!(m[0].address, "192.168.1.20:37123");
    }
}

/// Screenshots against a fake TV: a script standing in for `adb`.
#[cfg(all(test, unix))]
mod shot_tests {
    use super::*;
    use std::fs;
    use std::os::unix::fs::PermissionsExt;
    use std::sync::atomic::{AtomicU32, Ordering};

    static NEXT: AtomicU32 = AtomicU32::new(0);

    /// Far bigger than a pipe buffer, which is the size that used to wedge `run_bytes`.
    fn png() -> Vec<u8> {
        let mut bytes = vec![0x89, b'P', b'N', b'G', 0x0d, 0x0a, 0x1a, 0x0a];
        bytes.resize(200_000, 0x2a);
        bytes
    }

    /// `junk` makes `exec-out` answer with something that is not a PNG, to exercise the fallback.
    fn fake_adb(junk: bool) -> (PathBuf, Adb) {
        let dir = std::env::temp_dir().join(format!(
            "cyt-shot-{}-{}",
            std::process::id(),
            NEXT.fetch_add(1, Ordering::SeqCst)
        ));
        fs::create_dir_all(&dir).expect("temp dir");
        fs::write(dir.join("shot.png"), png()).unwrap();
        if junk {
            fs::write(dir.join("junk"), b"x").unwrap();
        }
        let script = format!(
            r#"#!/bin/sh
DIR="{dir}"
if [ "$1" = "version" ]; then echo "Android Debug Bridge version 1.0.41"; exit 0; fi
if [ "$3" = "exec-out" ]; then
  if [ -f "$DIR/junk" ]; then printf 'not a png at all\n'; exit 0; fi
  printf 'Init wrapper sys mutex successfully. Pid:21431\n'
  exec cat "$DIR/shot.png"
fi
if [ "$3" = "pull" ]; then cp "$DIR/shot.png" "$5"; echo "1 file pulled"; exit 0; fi
exit 0
"#,
            dir = dir.display()
        );
        let path = dir.join("adb");
        fs::write(&path, script).unwrap();
        let mut perms = fs::metadata(&path).unwrap().permissions();
        perms.set_mode(0o755);
        fs::set_permissions(&path, perms).unwrap();
        let adb = Adb::locate(Some(path.to_str().unwrap())).expect("the fake adb answers `version`");
        (dir, adb)
    }

    #[test]
    fn a_screenshot_bigger_than_the_pipe_buffer_arrives() {
        let (dir, adb) = fake_adb(false);
        let shot = adb.screenshot("192.168.1.20:5555").expect("a screenshot");
        assert_eq!(shot.len(), png().len());
        assert!(shot.starts_with(&PNG_SIG), "the vendor banner is sliced off");
        let _ = fs::remove_dir_all(dir);
    }

    #[test]
    fn a_banner_before_the_png_does_not_hide_it() {
        let mut stream = b"Init wrapper sys mutex successfully. Pid:21431\n".to_vec();
        stream.extend(png());
        let found = png_slice(&stream).expect("the PNG behind the banner");
        assert_eq!(found.len(), png().len());
        assert!(png_slice(b"no image here at all").is_none());    }

    /// A TV that refuses the capture the way a MediaTek board does on Live TV: `screencap` exits 1
    /// with nothing on stdout, and only `dumpsys window` still answers.
    fn refusing_adb() -> (PathBuf, Adb) {
        let dir = std::env::temp_dir().join(format!(
            "cyt-refuse-{}-{}",
            std::process::id(),
            NEXT.fetch_add(1, Ordering::SeqCst)
        ));
        fs::create_dir_all(&dir).expect("temp dir");
        let script = r#"#!/bin/sh
if [ "$1" = "version" ]; then echo "Android Debug Bridge version 1.0.41"; exit 0; fi
case "$3" in
  shell)
    case "$4" in
      *dumpsys*)
        echo "  mCurrentFocus=Window{1 u0 com.mediatek.wwtv.tvcenter/com.mediatek.wwtv.tvcenter.nav.TurnkeyUiMainActivity}"
        exit 0 ;;
      *) exit 1 ;;
    esac ;;
  *) exit 1 ;;
esac
"#;
        let path = dir.join("adb");
        fs::write(&path, script).unwrap();
        let mut perms = fs::metadata(&path).unwrap().permissions();
        perms.set_mode(0o755);
        fs::set_permissions(&path, perms).unwrap();
        let adb = Adb::locate(Some(path.to_str().unwrap())).expect("the fake adb answers `version`");
        (dir, adb)
    }

    #[test]
    fn a_refused_screenshot_names_the_app_in_front() {
        let (dir, adb) = refusing_adb();
        let error = adb
            .screenshot("192.168.1.20:5555")
            .expect_err("a protected surface cannot be captured");
        assert!(
            error.contains("refused a screenshot"),
            "the message should say the TV refused it, not show an exit code: {error}"
        );
        assert!(
            error.contains("com.mediatek.wwtv.tvcenter"),
            "the app in front is what explains the refusal: {error}"
        );
        let _ = fs::remove_dir_all(dir);
    }

    #[test]
    fn a_firmware_that_breaks_exec_out_falls_back_to_a_pull() {
        let (dir, adb) = fake_adb(true);
        let shot = adb.screenshot("192.168.1.20:5555").expect("the pulled screenshot");
        assert_eq!(shot.len(), png().len());
        let _ = fs::remove_dir_all(dir);
    }
}
